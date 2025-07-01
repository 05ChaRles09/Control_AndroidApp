package com.example.location;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.camera2.CameraManager;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {

    private SeekBar volumeSeekBar, brightnessSeekBar;
    private AudioManager audioManager;
    private static final String CHANNEL_ID = "volume_channel";

    private boolean isFlashOn = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

		// 初始化音量控制
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        setupVolumeControl();

        // 初始化亮度控制
        setupBrightnessControl();

        requestNotificationPermission();
        createNotificationChannel();

        setupSystemControlButtons();

        setupThemeSwitch();
    }

    private void setupVolumeControl() {
        volumeSeekBar = findViewById(R.id.volumeSeekBar);
        int maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        volumeSeekBar.setMax(maxVolume);
        volumeSeekBar.setProgress(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC));

        volumeSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, progress, AudioManager.FLAG_SHOW_UI);
                showNotification(progress, maxVolume);
            }

            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }

    private void showNotification(int volume, int max) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_lock_silent_mode_off)
                .setContentTitle("音量通知")
                .setContentText("目前音量：" + (volume * 100 / max) + "%")
                .setPriority(NotificationCompat.PRIORITY_LOW);

        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        manager.notify(1, builder.build());
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "音量通知",
                    NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }
    }

    private void setupBrightnessControl() {
        brightnessSeekBar = findViewById(R.id.brightnessSeekBar);
        brightnessSeekBar.setMax(255);

        try {
            int curBrightness = Settings.System.getInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS);
            brightnessSeekBar.setProgress(curBrightness);
        } catch (Settings.SettingNotFoundException e) {
            e.printStackTrace();
            brightnessSeekBar.setProgress(128);
        }

        brightnessSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    if (Settings.System.canWrite(MainActivity.this)) {
                        Settings.System.putInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, progress);

                        // 同步更新螢幕亮度(立即生效)
                        WindowManager.LayoutParams layoutParams = getWindow().getAttributes();
                        layoutParams.screenBrightness = progress / 255f;
                        getWindow().setAttributes(layoutParams);
                    } else {
                        // 請求用戶授權
                        Intent intent = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS);
                        intent.setData(Uri.parse("package:" + getPackageName()));
                        startActivity(intent);
                        Toast.makeText(MainActivity.this, "請先授權寫入系統設定權限", Toast.LENGTH_SHORT).show();
                    }
                }
            }

            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }

    private void setupSystemControlButtons() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.BLUETOOTH_CONNECT}, 100);
                return;
            }
        }

        // 藍牙按鈕（導向系統設定，避免權限問題）
        findViewById(R.id.btnBluetooth).setOnClickListener(v -> {
            startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS));
        });

        findViewById(R.id.btnWifi).setOnClickListener(v -> {
            startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS));
        });

        findViewById(R.id.btnMobileData).setOnClickListener(v -> {
            startActivity(new Intent(Settings.ACTION_DATA_ROAMING_SETTINGS));
        });

        findViewById(R.id.btnAirplane).setOnClickListener(v -> {
            startActivity(new Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS));
        });

        findViewById(R.id.btnPowerSaver).setOnClickListener(v -> {
            startActivity(new Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS));
        });

        findViewById(R.id.btnDnd).setOnClickListener(v -> {
            Intent intent = new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS);
            startActivity(intent);
        });

        findViewById(R.id.btnFlash).setOnClickListener(v -> {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, 101);
                return;
            }

            try {
                CameraManager cm = (CameraManager) getSystemService(CAMERA_SERVICE);
                String camId = cm.getCameraIdList()[0];
                isFlashOn = !isFlashOn;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    cm.setTorchMode(camId, isFlashOn);
                    Toast.makeText(this, isFlashOn ? "手電筒已開啟" : "手電筒已關閉", Toast.LENGTH_SHORT).show();
                }
            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(this, "手電筒控制失敗", Toast.LENGTH_SHORT).show();
            }
        });

        findViewById(R.id.btnBrightness).setOnClickListener(v -> {
            // 點按按鈕跳到系統亮度設定頁面
            Intent intent = new Intent(Settings.ACTION_DISPLAY_SETTINGS);
            startActivity(intent);
        });
    }

    private void setupThemeSwitch() {
        Switch switchTheme = findViewById(R.id.switchTheme);

        // 初始化狀態（根據 AppCompatDelegate 現有模式）
        int currentMode = AppCompatDelegate.getDefaultNightMode();
        switchTheme.setChecked(currentMode == AppCompatDelegate.MODE_NIGHT_YES);

        switchTheme.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
            } else {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
            }
        });
    }
}
