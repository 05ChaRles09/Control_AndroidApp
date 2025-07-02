package com.example.location; // Please ensure this package name matches your project

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.media.AudioManager;
import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.BroadcastReceiver;
import android.content.pm.PackageManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.app.ActivityManager;
import android.provider.Settings;
import android.text.format.Formatter;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.LinearLayout; // Ensure this is imported if used in custom_toast.xml
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import java.io.File;

public class MainActivity extends AppCompatActivity {

    // --- UI Components ---
    private SeekBar mediaVolumeSeekBar, ringVolumeSeekBar, notificationVolumeSeekBar, alarmVolumeSeekBar, callVolumeSeekBar;
    private Button btnMute;
    private SeekBar brightnessSeekBar;
    private Button btnWifi, btnBluetooth, btnBrightness, btnAirplane, btnMobileData, btnPowerSaver, btnFlash, btnDnd;
    private Switch switchTheme;
    private TextView tvBatteryStatus, tvStorageStatus, tvRamUsage;
    private Button btnPlayPause, btnPrevious, btnNext;
    private MediaSessionCompat mediaSession;
    // --- System Services ---
    private AudioManager audioManager;
    private CameraManager cameraManager;
    private String cameraId;
    private boolean hasFlashlight = false;
    private BluetoothAdapter bluetoothAdapter;
    private boolean isFlashlightOn = false; // Used to track flashlight state

    // --- Broadcast Receivers ---
    private BroadcastReceiver batteryReceiver; // Battery status broadcast receiver
    private VolumeChangeReceiver volumeChangeReceiver; // Volume change broadcast receiver

    // --- Permission Request Codes (only for onRequestPermissionsResult, ActivityResultLauncher is preferred) ---
    private static final int PERMISSION_CODE_CAMERA = 2; // Still used for ActivityCompat.requestPermissions

    // --- ActivityResultLaunchers for Permissions and Settings Redirects ---
    private ActivityResultLauncher<Intent> requestWriteSettingsLauncher; // Request WRITE_SETTINGS permission
    private ActivityResultLauncher<String> requestBluetoothConnectPermissionLauncher; // Request BLUETOOTH_CONNECT (Android 12+)
    private ActivityResultLauncher<String> requestNotificationPermissionLauncher; // Request POST_NOTIFICATIONS (Android 13+)


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // --- Initialize UI Components ---
        mediaVolumeSeekBar = findViewById(R.id.volumeSeekBar); // Media volume
        btnMute = findViewById(R.id.btnMute);
        brightnessSeekBar = findViewById(R.id.brightnessSeekBar);
        btnWifi = findViewById(R.id.btnWifi);
        btnBluetooth = findViewById(R.id.btnBluetooth);
        btnBrightness = findViewById(R.id.btnBrightness);
        btnAirplane = findViewById(R.id.btnAirplane);
        btnMobileData = findViewById(R.id.btnMobileData);
        btnPowerSaver = findViewById(R.id.btnPowerSaver);
        btnFlash = findViewById(R.id.btnFlash);
        btnDnd = findViewById(R.id.btnDnd);
        switchTheme = findViewById(R.id.switchTheme);

        ringVolumeSeekBar = findViewById(R.id.ringVolumeSeekBar);
        notificationVolumeSeekBar = findViewById(R.id.notificationVolumeSeekBar);
        alarmVolumeSeekBar = findViewById(R.id.alarmVolumeSeekBar);
        callVolumeSeekBar = findViewById(R.id.callVolumeSeekBar);

        tvBatteryStatus = findViewById(R.id.tvBatteryStatus);
        tvStorageStatus = findViewById(R.id.tvStorageStatus);
        tvRamUsage = findViewById(R.id.tvRamUsage);

        // --- Initialize System Services ---
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);

        BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        if (bluetoothManager != null) {
            bluetoothAdapter = bluetoothManager.getAdapter();
        }

        // --- Initialize ActivityResultLaunchers ---
        requestWriteSettingsLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        if (Settings.System.canWrite(this)) {
                            showCustomToast(this, "寫入系統設定權限已授予！", Toast.LENGTH_SHORT);
                            // Re-initialize brightness control after permission granted to ensure SeekBar is enabled
                            initBrightnessControl();
                        } else {
                            showCustomToast(this, "寫入系統設定權限被拒絕，亮度無法控制。", Toast.LENGTH_LONG);
                            brightnessSeekBar.setEnabled(false);
                        }
                    }
                }
        );

        requestBluetoothConnectPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    if (isGranted) {
                        showCustomToast(this, "BLUETOOTH_CONNECT 權限已授予！", Toast.LENGTH_SHORT);
                        // After permission granted, update the button state
                        updateBluetoothButtonState();
                    } else {
                        showCustomToast(this, "BLUETOOTH_CONNECT 權限被拒絕。藍牙開關可能無法正常運作。", Toast.LENGTH_LONG);
                        btnBluetooth.setEnabled(false); // Disable the Bluetooth button
                    }
                });

        requestNotificationPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    if (isGranted) {
                        showNotification("通知權限已授予", "現在可以發送通知了。");
                    } else {
                        showCustomToast(this, "通知權限被拒絕，無法發送通知。", Toast.LENGTH_SHORT);
                    }
                }
        );

        // --- Setup Volume Controls ---
        initVolumeControls();

        // --- Setup Brightness Control (may not have WRITE_SETTINGS permission yet, will recheck in onResume) ---
        initBrightnessControl();

        // --- Setup System Control Buttons ---
        setupSystemControlButtons();

        // --- Setup Theme Mode Switch ---
        setupThemeSwitch();

        // --- Check Permissions (results handled by onRequestPermissionsResult or ActivityResultLauncher) ---
        checkWriteSettingsPermission();
        checkCameraPermission(); // This will trigger onRequestPermissionsResult
        checkBluetoothPermissions(); // This will trigger requestBluetoothConnectPermissionLauncher

        // Initialize camera ID and flashlight status. This needs to be done after camera permission check.
        // It's called here, but also re-evaluated in onRequestPermissionsResult if permission is granted.
        // If permission is denied, btnFlash will be disabled later.
        initCameraIdAndFlashlightStatus();


        // --- Initialize System Info Display ---
        displaySystemInfo();

        // --- Register Battery Status Broadcast Receiver ---
        registerBatteryReceiver();

        // --- Register Volume Change Broadcast Receiver ---
        volumeChangeReceiver = new VolumeChangeReceiver();
        IntentFilter volumeFilter = new IntentFilter("android.media.VOLUME_CHANGED_ACTION");
        registerReceiver(volumeChangeReceiver, volumeFilter);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // When the app returns to the foreground, update UI status
        updateBrightnessSeekBar(); // Ensure brightness SeekBar is up-to-date (especially after returning from settings)
        updateAllVolumeSeekBars(); // Ensure all volume SeekBars are up-to-date
        displaySystemInfo(); // Update system information
        updateBluetoothButtonState(); // Recheck Bluetooth button state in case permission changed in settings
        updateFlashlightButtonState(); // Recheck flashlight button state in case permission changed in settings
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Unregister all broadcast receivers to prevent memory leaks
        if (batteryReceiver != null) {
            unregisterReceiver(batteryReceiver);
        }
        if (volumeChangeReceiver != null) {
            unregisterReceiver(volumeChangeReceiver);
        }
    }

    /**
     * Shows a custom-styled Toast message.
     * @param context The context.
     * @param message The text to display.
     * @param duration Toast.LENGTH_SHORT or Toast.LENGTH_LONG.
     */
    private void showCustomToast(Context context, String message, int duration) {
        LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View layout = inflater.inflate(R.layout.custom_toast, null);

        TextView text = layout.findViewById(R.id.text);
        text.setText(message);

        Toast toast = new Toast(context.getApplicationContext());
        toast.setDuration(duration);
        toast.setView(layout);
        toast.show();
    }

    // --- All Volume Control Logic ---
    private void initVolumeControls() {
        setupVolumeSeekBar(mediaVolumeSeekBar, AudioManager.STREAM_MUSIC);
        setupVolumeSeekBar(ringVolumeSeekBar, AudioManager.STREAM_RING);
        setupVolumeSeekBar(notificationVolumeSeekBar, AudioManager.STREAM_NOTIFICATION);
        setupVolumeSeekBar(alarmVolumeSeekBar, AudioManager.STREAM_ALARM);
        setupVolumeSeekBar(callVolumeSeekBar, AudioManager.STREAM_VOICE_CALL);

        // Mute toggle button (for media volume)
        btnMute.setOnClickListener(v -> {
            int current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
            int max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            if (current == 0) {
                // If currently muted, restore to half of max volume
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC,
                        max / 2,
                        AudioManager.FLAG_PLAY_SOUND | AudioManager.FLAG_SHOW_UI);
            } else {
                // If not muted, set to 0
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC,
                        0, AudioManager.FLAG_PLAY_SOUND | AudioManager.FLAG_SHOW_UI);
            }
            // Update media volume SeekBar
            mediaVolumeSeekBar.setProgress(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC));
        });
    }

    // General Volume SeekBar Setup Method
    private void setupVolumeSeekBar(SeekBar seekBar, int streamType) {
        int maxVolume = audioManager.getStreamMaxVolume(streamType);
        int currentVolume = audioManager.getStreamVolume(streamType);

        seekBar.setMax(maxVolume);
        seekBar.setProgress(currentVolume);

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                // Only respond if the user is dragging the SeekBar
                if (fromUser) {
                    audioManager.setStreamVolume(streamType,
                            progress, AudioManager.FLAG_PLAY_SOUND | AudioManager.FLAG_SHOW_UI);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                // Do nothing
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                // Do nothing
            }
        });
    }

    // Updates the state of all volume SeekBars
    private void updateAllVolumeSeekBars() {
        mediaVolumeSeekBar.setProgress(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC));
        ringVolumeSeekBar.setProgress(audioManager.getStreamVolume(AudioManager.STREAM_RING));
        notificationVolumeSeekBar.setProgress(audioManager.getStreamVolume(AudioManager.STREAM_NOTIFICATION));
        alarmVolumeSeekBar.setProgress(audioManager.getStreamVolume(AudioManager.STREAM_ALARM));
        callVolumeSeekBar.setProgress(audioManager.getStreamVolume(AudioManager.STREAM_VOICE_CALL));
    }

    // --- Brightness Control Logic ---
    private void initBrightnessControl() {
        brightnessSeekBar.setMax(255); // Brightness range 0-255

        // Check for WRITE_SETTINGS permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.System.canWrite(this)) {
            brightnessSeekBar.setEnabled(false);
            // No need to repeat Toast here, it's handled when requesting permission
        } else {
            brightnessSeekBar.setEnabled(true);
            updateBrightnessSeekBar(); // Permission exists, update SeekBar to show current brightness
        }

        brightnessSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser) return; // Only respond if the user is dragging

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    if (!Settings.System.canWrite(MainActivity.this)) {
                        showCustomToast(MainActivity.this, "無寫入系統設定權限，無法調整亮度。", Toast.LENGTH_SHORT);
                        return;
                    }
                } else {
                    // For Android < 6.0, no permission needed, but still use try-catch for robustness
                }

                if (progress < 1) { // Prevent brightness from being 0, which would make the screen black
                    progress = 1;
                }

                try {
                    // Set brightness mode to manual, otherwise auto-brightness will override your setting
                    Settings.System.putInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
                    // Set the new screen brightness
                    Settings.System.putInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, progress);

                    // Synchronously update the current Activity's brightness (optional, but recommended for immediate feedback)
                    WindowManager.LayoutParams layoutParams = getWindow().getAttributes();
                    layoutParams.screenBrightness = progress / 255.0f; // Value between 0.0f and 1.0f
                    getWindow().setAttributes(layoutParams);
                } catch (SecurityException e) {
                    Log.e("Brightness", "Failed to write brightness settings, permission likely denied: " + e.getMessage());
                    showCustomToast(MainActivity.this, "無法調整亮度，請檢查權限。", Toast.LENGTH_SHORT);
                } catch (Exception e) {
                    Log.e("Brightness", "An unknown error occurred while adjusting brightness: " + e.getMessage());
                    showCustomToast(MainActivity.this, "調整亮度時發生錯誤。", Toast.LENGTH_SHORT);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                // Do nothing
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                // Do nothing
            }
        });
    }

    // Updates the brightness SeekBar's progress to the current system brightness
    private void updateBrightnessSeekBar() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.System.canWrite(this)) {
            try {
                int brightness = Settings.System.getInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS);
                brightnessSeekBar.setProgress(brightness);
            } catch (Settings.SettingNotFoundException e) {
                Log.e("Brightness", "Could not read screen brightness setting", e);
                brightnessSeekBar.setProgress(0); // Set to 0 if reading fails
            }
        } else {
            // If no permission, disable and set to 0
            brightnessSeekBar.setProgress(0);
            brightnessSeekBar.setEnabled(false);
        }
    }

    // --- WRITE_SETTINGS Permission Request (using ActivityResultLauncher) ---
    private void checkWriteSettingsPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.System.canWrite(this)) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:" + getPackageName()));
                requestWriteSettingsLauncher.launch(intent); // Launch with the Launcher
            }
        }
    }

    // --- Camera Permission Request (for Flashlight) ---
    // Still using ActivityCompat.requestPermissions here for simplicity
    private void checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, PERMISSION_CODE_CAMERA);
        } else {
            // If permission is already granted, immediately initialize flashlight related features
            initCameraIdAndFlashlightStatus();
        }
    }

    // --- Bluetooth Permission Request (Android 12+, using ActivityResultLauncher) ---
    private void checkBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                requestBluetoothConnectPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT);
            }
        }
    }

    // onRequestPermissionsResult handles results from ActivityCompat.requestPermissions
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_CODE_CAMERA) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                showCustomToast(this, "相機權限已授予！", Toast.LENGTH_SHORT);
                initCameraIdAndFlashlightStatus(); // Initialize flashlight after permission is granted
            } else {
                showCustomToast(this, "相機權限被拒絕，手電筒無法使用。", Toast.LENGTH_LONG);
                btnFlash.setEnabled(false); // Disable flashlight button
                hasFlashlight = false; // Even if hardware exists, it's unusable without permission
            }
        }
        // BLUETOOTH_CONNECT results are handled automatically by requestBluetoothConnectPermissionLauncher, no need to duplicate here.
        // You would add more cases here for other runtime permissions if you use ActivityCompat.requestPermissions for them.
    }

    // --- Camera ID and Flashlight Status Initialization (Improved error handling) ---
    private void initCameraIdAndFlashlightStatus() {
        if (cameraManager == null) {
            showCustomToast(this, "無法獲取相機服務。", Toast.LENGTH_SHORT);
            btnFlash.setEnabled(false);
            return;
        }

        try {
            String[] cameraIds = cameraManager.getCameraIdList();
            if (cameraIds.length > 0) {
                cameraId = cameraIds[0]; // Typically the first ID is the rear camera

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraId);
                    Boolean isFlashAvailable = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                    if (isFlashAvailable != null && isFlashAvailable) {
                        hasFlashlight = true;
                        // Ensure flashlight is off on startup
                        try {
                            cameraManager.setTorchMode(cameraId, false);
                            isFlashlightOn = false;
                        } catch (CameraAccessException e) {
                            Log.e("Flashlight", "Failed to turn off flashlight on startup: " + e.getMessage());
                            // No Toast for this error, as it's not user-actionable
                        }
                    } else {
                        showCustomToast(this, "此設備沒有閃光燈功能。", Toast.LENGTH_SHORT);
                        btnFlash.setEnabled(false);
                        hasFlashlight = false;
                    }
                } else { // For API 23 (Android 6.0) and below
                    hasFlashlight = false;
                    btnFlash.setEnabled(false);
                    showCustomToast(this, "設備版本過低，不支持閃光燈控制。", Toast.LENGTH_SHORT);
                }
            } else {
                showCustomToast(this, "此設備沒有可用的攝像頭。", Toast.LENGTH_SHORT);
                btnFlash.setEnabled(false);
                hasFlashlight = false;
            }
        } catch (CameraAccessException e) {
            Log.e("Flashlight", "Failed to access camera service: " + e.getMessage());
            showCustomToast(this, "無法訪問相機服務，手電筒功能可能無法使用。", Toast.LENGTH_SHORT);
            btnFlash.setEnabled(false);
            hasFlashlight = false;
        }

        // Update button state based on final hasFlashlight status and camera permission
        updateFlashlightButtonState();
    }

    // --- Update Flashlight Button State ---
    private void updateFlashlightButtonState() {
        btnFlash.setEnabled(hasFlashlight && ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED);
    }


    // --- System Control Button Logic ---
    private void setupSystemControlButtons() {
        btnWifi.setOnClickListener(v -> {
            Intent intent;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) { // Android 10+
                intent = new Intent(Settings.Panel.ACTION_WIFI); // Recommended to use Panel
            } else {
                intent = new Intent(Settings.ACTION_WIFI_SETTINGS);
            }
            startActivity(intent);
        });

        btnBluetooth.setOnClickListener(v -> {
            if (bluetoothAdapter == null) {
                showCustomToast(this, "此設備不支持藍牙", Toast.LENGTH_SHORT);
                return;
            }

            // Android 12 (API 31) and above require BLUETOOTH_CONNECT permission
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    showCustomToast(this, "請授予藍牙連接權限以控制藍牙。", Toast.LENGTH_SHORT);
                    requestBluetoothConnectPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT); // Use Launcher to request
                    return;
                }
                // For Android 12+, even with permission, you can't directly toggle Bluetooth. You must redirect to settings.
                showCustomToast(this, "Android 12+ 請前往系統藍牙設定切換。", Toast.LENGTH_LONG);
                Intent settingsIntent = new Intent(Settings.ACTION_BLUETOOTH_SETTINGS);
                startActivity(settingsIntent);

            } else { // Android 11 (API 30) and below can directly toggle Bluetooth
                // These methods are deprecated in API 33 but still usable on older versions
                if (bluetoothAdapter.isEnabled()) {
                    bluetoothAdapter.disable();
                    showCustomToast(this, "藍牙已關閉", Toast.LENGTH_SHORT);
                } else {
                    bluetoothAdapter.enable();
                    showCustomToast(this, "藍牙已開啟", Toast.LENGTH_SHORT);
                }
            }
            // Update button state after attempting to toggle Bluetooth
            updateBluetoothButtonState();
        });


        btnBrightness.setOnClickListener(v -> {
            Intent intent = new Intent(Settings.ACTION_DISPLAY_SETTINGS);
            startActivity(intent);
        });

        btnAirplane.setOnClickListener(v -> {
            Intent intent = new Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS);
            startActivity(intent);
        });

        btnMobileData.setOnClickListener(v -> {
            Intent intent;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) { // Android 10+
                intent = new Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY); // Recommended to use Panel
            } else {
                intent = new Intent(Settings.ACTION_WIRELESS_SETTINGS); // Jumps to Wireless & Network settings
            }
            startActivity(intent);
        });

        btnPowerSaver.setOnClickListener(v -> {
            Intent intent = new Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS);
            startActivity(intent);
        });

        btnFlash.setOnClickListener(v -> {
            if (!hasFlashlight) {
                showCustomToast(this, "設備無閃光燈硬體或初始化失敗。", Toast.LENGTH_SHORT);
                return;
            }

            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                showCustomToast(this, "請授予相機權限以使用手電筒。", Toast.LENGTH_SHORT);
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, PERMISSION_CODE_CAMERA);
                return;
            }

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    // Toggle flashlight based on current state
                    cameraManager.setTorchMode(cameraId, !isFlashlightOn);
                    isFlashlightOn = !isFlashlightOn; // Update state
                    showCustomToast(this, isFlashlightOn ? "手電筒已開啟" : "手電筒已關閉", Toast.LENGTH_SHORT);
                } else {
                    showCustomToast(this, "此設備版本不支持手電筒切換，或功能被限制。", Toast.LENGTH_SHORT);
                }

            } catch (CameraAccessException e) {
                Log.e("Flashlight", "Flashlight operation failed: " + e.getMessage());
                showCustomToast(this, "手電筒操作失敗: " + e.getMessage(), Toast.LENGTH_SHORT);
            }
        });

        btnDnd.setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Intent intent = new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS);
                startActivity(intent);
            } else {
                showCustomToast(this, "此功能需要 Android 6.0 或更高版本", Toast.LENGTH_SHORT);
            }
        });
    }

    // --- Update Bluetooth Button State ---
    // Moved outside setupSystemControlButtons()
    private void updateBluetoothButtonState() {
        if (bluetoothAdapter != null) {
            boolean isBluetoothEnabled = bluetoothAdapter.isEnabled();
            btnBluetooth.setText(isBluetoothEnabled ? "藍牙 ON" : "藍牙 OFF");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // For Android 12+, button is enabled only if BLUETOOTH_CONNECT permission is granted
                btnBluetooth.setEnabled(ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED);
            } else {
                // For older versions, button is enabled if Bluetooth adapter exists
                btnBluetooth.setEnabled(true);
            }
        } else {
            // If device doesn't support Bluetooth, disable button and show N/A
            btnBluetooth.setText("藍牙 N/A");
            btnBluetooth.setEnabled(false);
        }
    }


    // --- Theme Mode Switch Logic ---
    private void setupThemeSwitch() {
        boolean isNightMode = (AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_YES);
        switchTheme.setChecked(isNightMode);

        switchTheme.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
            } else {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
            }
            recreate(); // Recreate Activity to apply new theme
        });
    }

    // --- Notification Functionality ---
    private void showNotification(String title, String content) {
        String channelId = "system_control_notification_channel";
        int notificationId = 1001;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) { // Android 8.0 Oreo (API 26)
            CharSequence name = "系統控制通知";
            String description = "用於顯示系統相關的通知";
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            NotificationChannel channel = new NotificationChannel(channelId, name, importance);
            channel.setDescription(description);
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, channelId)
                .setSmallIcon(android.R.drawable.ic_dialog_info) // Use built-in icon as example
                .setContentTitle(title)
                .setContentText(content)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true);

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { // Android 13 (API 33)
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                notificationManager.notify(notificationId, builder.build());
            } else {
                // Request notification permission
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            }
        } else {
            // No POST_NOTIFICATIONS permission needed below Android 13
            notificationManager.notify(notificationId, builder.build());
        }
    }

    // --- System Information Display ---
    private void displaySystemInfo() {
        // Battery status: Register receiver in onCreate to listen for changes, here only call to refresh
        // Ensure batteryReceiver is registered in onCreate, otherwise first call might be ineffective
        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = registerReceiver(null, ifilter); // Get current battery status
        if (batteryStatus != null) {
            updateBatteryStatusUI(batteryStatus);
        }

        // Storage space
        long internalTotal = getTotalInternalStorage();
        long internalAvailable = getAvailableInternalStorage();
        if (internalTotal > 0) {
            String total = Formatter.formatFileSize(this, internalTotal);
            String available = Formatter.formatFileSize(this, internalAvailable);
            tvStorageStatus.setText(String.format("儲存空間: %s 可用 / %s 總共", available, total));
        } else {
            tvStorageStatus.setText("儲存空間: 無法獲取");
        }

        // RAM Usage
        ActivityManager activityManager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        if (activityManager != null) {
            ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
            activityManager.getMemoryInfo(memoryInfo);

            long totalRam = memoryInfo.totalMem;
            long availableRam = memoryInfo.availMem;
            long usedRam = totalRam - availableRam;

            String totalRamFormatted = Formatter.formatFileSize(this, totalRam);
            String usedRamFormatted = Formatter.formatFileSize(this, usedRam);

            tvRamUsage.setText(String.format("RAM 使用率: %s / %s", usedRamFormatted, totalRamFormatted));
        } else {
            tvRamUsage.setText("RAM 使用率: 無法獲取");
        }
    }

    // Get total internal storage
    private long getTotalInternalStorage() {
        File path = Environment.getDataDirectory();
        android.os.StatFs stat = new android.os.StatFs(path.getPath());
        long blockSize = stat.getBlockSizeLong();
        long totalBlocks = stat.getBlockCountLong();
        return totalBlocks * blockSize;
    }

    // Get available internal storage
    private long getAvailableInternalStorage() {
        File path = Environment.getDataDirectory();
        android.os.StatFs stat = new android.os.StatFs(path.getPath());
        long blockSize = stat.getBlockSizeLong();
        long availableBlocks = stat.getAvailableBlocksLong();
        return availableBlocks * blockSize;
    }


    // --- Battery Status Broadcast Receiver and UI Update Method ---
    private void registerBatteryReceiver() {
        batteryReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (Intent.ACTION_BATTERY_CHANGED.equals(intent.getAction())) {
                    updateBatteryStatusUI(intent);
                }
            }
        };
        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        registerReceiver(batteryReceiver, ifilter);
    }

    private void updateBatteryStatusUI(Intent batteryStatusIntent) {
        int level = batteryStatusIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = batteryStatusIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        float batteryPct = level / (float) scale * 100;

        int status = batteryStatusIntent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        String statusString;
        switch (status) {
            case BatteryManager.BATTERY_STATUS_CHARGING:
                statusString = "充電中";
                break;
            case BatteryManager.BATTERY_STATUS_DISCHARGING:
                statusString = "放電中";
                break;
            case BatteryManager.BATTERY_STATUS_FULL:
                statusString = "已充滿";
                break;
            case BatteryManager.BATTERY_STATUS_NOT_CHARGING:
                statusString = "未充電";
                break;
            case BatteryManager.BATTERY_STATUS_UNKNOWN:
            default:
                statusString = "未知狀態";
                break;
        }

        int health = batteryStatusIntent.getIntExtra(BatteryManager.EXTRA_HEALTH, -1);
        String healthString = "";
        switch (health) {
            case BatteryManager.BATTERY_HEALTH_GOOD:
                healthString = "良好";
                break;
            case BatteryManager.BATTERY_HEALTH_COLD:
                healthString = "過冷";
                break;
            case BatteryManager.BATTERY_HEALTH_DEAD:
                healthString = "損壞";
                break;
            case BatteryManager.BATTERY_HEALTH_OVERHEAT:
                healthString = "過熱";
                break;
            case BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE:
                healthString = "電壓過高";
                break;
            case BatteryManager.BATTERY_HEALTH_UNKNOWN:
            default:
                healthString = "未知";
                break;
        }

        String batteryInfo = String.format("電量: %.0f%% (%s, 健康: %s)", batteryPct, statusString, healthString);
        tvBatteryStatus.setText(batteryInfo);
    }

    // --- Volume Change Broadcast Receiver (for updating Widget) ---
    private class VolumeChangeReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction() != null && intent.getAction().equals("android.media.VOLUME_CHANGED_ACTION")) {
                Log.d("VolumeWidget", "音量改變，正在更新 Widget...");
                // Trigger MyVolumeWidgetProvider to update all widget instances
                AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
                ComponentName thisAppWidget = new ComponentName(context.getPackageName(), MyVolumeWidgetProvider.class.getName());
                int[] appWidgetIds = appWidgetManager.getAppWidgetIds(thisAppWidget);
                // Iterate and update all widget instances
                for (int appWidgetId : appWidgetIds) {
                    MyVolumeWidgetProvider.updateAppWidget(context, appWidgetManager, appWidgetId);
                }
            }
        }
    }
}