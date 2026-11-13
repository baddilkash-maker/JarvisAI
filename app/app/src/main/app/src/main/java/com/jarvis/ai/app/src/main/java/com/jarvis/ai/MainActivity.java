package com.jarvis.ai;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_CODE = 1001;
    private TextView statusText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER);
        layout.setBackgroundColor(Color.parseColor("#0A0F1E"));
        layout.setPadding(60, 60, 60, 60);

        TextView title = new TextView(this);
        title.setText("J.A.R.V.I.S");
        title.setTextSize(34);
        title.setTextColor(Color.parseColor("#00E5FF"));
        title.setGravity(Gravity.CENTER);
        layout.addView(title);

        statusText = new TextView(this);
        statusText.setText(JarvisService.currentStatus);
        statusText.setTextSize(17);
        statusText.setTextColor(Color.WHITE);
        statusText.setGravity(Gravity.CENTER);
        statusText.setPadding(0, 40, 0, 70);
        layout.addView(statusText);

        Button startBtn = new Button(this);
        startBtn.setText("START JARVIS");
        startBtn.setTextSize(16);
        startBtn.setBackgroundColor(Color.parseColor("#00B8D4"));
        startBtn.setTextColor(Color.BLACK);
        startBtn.setPadding(40, 35, 40, 35);
        startBtn.setOnClickListener(v -> checkPermissionsAndStart());
        layout.addView(startBtn);

        LinearLayout.LayoutParams spaceParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 30);

        TextView spacer1 = new TextView(this);
        spacer1.setLayoutParams(spaceParams);
        layout.addView(spacer1);

        Button stopBtn = new Button(this);
        stopBtn.setText("STOP JARVIS");
        stopBtn.setTextSize(16);
        stopBtn.setBackgroundColor(Color.parseColor("#D32F2F"));
        stopBtn.setTextColor(Color.WHITE);
        stopBtn.setPadding(40, 35, 40, 35);
        stopBtn.setOnClickListener(v -> stopService(new Intent(this, JarvisService.class)));
        layout.addView(stopBtn);

        TextView spacer2 = new TextView(this);
        spacer2.setLayoutParams(spaceParams);
        layout.addView(spacer2);

        Button batteryBtn = new Button(this);
        batteryBtn.setText("ALLOW LOCK-SCREEN BACKGROUND");
        batteryBtn.setTextSize(14);
        batteryBtn.setBackgroundColor(Color.parseColor("#1E293B"));
        batteryBtn.setTextColor(Color.parseColor("#00E5FF"));
        batteryBtn.setPadding(30, 30, 30, 30);
        batteryBtn.setOnClickListener(v -> requestBatteryUnrestricted());
        layout.addView(batteryBtn);

        setContentView(layout);

        JarvisService.setStatusListener(status -> statusText.setText(status));
    }

    private void checkPermissionsAndStart() {
        List<String> needed = new ArrayList<>();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.RECORD_AUDIO);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.POST_NOTIFICATIONS);
        }

        if (!needed.isEmpty()) {
            ActivityCompat.requestPermissions(this,
                    needed.toArray(new String[0]), PERMISSION_REQUEST_CODE);
        } else {
            startJarvisService();
        }
    }

    private void startJarvisService() {
        Intent intent = new Intent(this, JarvisService.class);
        ContextCompat.startForegroundService(this, intent);
    }

    private void requestBatteryUnrestricted() {
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        if (pm != null && pm.isIgnoringBatteryOptimizations(getPackageName())) {
            Toast.makeText(this, "Background mode already allowed!", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            intent.setData(Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        } catch (Exception e) {
            startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                    == PackageManager.PERMISSION_GRANTED) {
                startJarvisService();
            } else {
                Toast.makeText(this, "Microphone permission is required for Jarvis!", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onDestroy() {
        JarvisService.setStatusListener(null);
        super.onDestroy();
    }
}
