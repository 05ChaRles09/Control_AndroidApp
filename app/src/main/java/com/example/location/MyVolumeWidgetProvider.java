package com.example.location;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color; // 用於設定文字顏色
import android.media.AudioManager;
import android.net.Uri; // 用於跳轉設定頁面
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Build;
import android.provider.Settings; // 用於系統設定相關操作
import android.util.Log;
import android.widget.RemoteViews;
import android.widget.Toast; // 用於顯示短暫提示

public class MyVolumeWidgetProvider extends AppWidgetProvider {

    // --- 定義所有 Widget 操作的常量 ---
    public static final String ACTION_VOLUME_UP = "com.example.location.ACTION_VOLUME_UP";
    public static final String ACTION_VOLUME_DOWN = "com.example.location.ACTION_VOLUME_DOWN";
    public static final String ACTION_MUTE_TOGGLE = "com.example.location.ACTION_MUTE_TOGGLE";

    public static final String ACTION_TOGGLE_WIFI = "com.example.location.ACTION_TOGGLE_WIFI";
    public static final String ACTION_TOGGLE_BLUETOOTH = "com.example.location.ACTION_TOGGLE_BLUETOOTH";
    public static final String ACTION_TOGGLE_MOBILE_DATA_SETTINGS = "com.example.location.ACTION_TOGGLE_MOBILE_DATA_SETTINGS"; // 跳轉到行動數據設定

    public static final String ACTION_LAUNCH_APP = "com.example.location.ACTION_LAUNCH_APP";

    public static final String ACTION_BRIGHTNESS_UP = "com.example.location.ACTION_BRIGHTNESS_UP";
    public static final String ACTION_BRIGHTNESS_DOWN = "com.example.location.ACTION_BRIGHTNESS_DOWN";

    /**
     * 當 Widget 被添加到桌面或在更新週期到達時調用。
     * 遍歷所有已存在的 Widget 實例並更新它們的 UI。
     */
    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        // 遍歷所有 Widget ID
        for (int appWidgetId : appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId);
        }
    }

    /**
     * 實際更新 Widget UI 和設定點擊事件的方法。
     * 這是 Widget 的核心更新邏輯。
     */
    public static void updateAppWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        // 創建一個 RemoteViews 物件，用於操作 Widget 的佈局
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_volume);

        // --- 1. 更新音量狀態顯示 ---
        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (audioManager != null) {
            int currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
            int maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            String volumeText = String.format("媒體音量: %d/%d", currentVolume, maxVolume);
            views.setTextViewText(R.id.tvVolumeStatus, volumeText); // 更新音量 TextView

            // 根據音量狀態更新靜音按鈕文字和顏色
            if (currentVolume == 0) {
                views.setTextViewText(R.id.btnMuteToggle, "取消靜音");
                views.setTextColor(R.id.btnMuteToggle, Color.RED); // 靜音時文字為紅色
            } else {
                views.setTextViewText(R.id.btnMuteToggle, "靜音");
                views.setTextColor(R.id.btnMuteToggle, Color.BLACK); // 非靜音時文字為黑色
            }
        }

        // --- 2. 更新電池和亮度狀態顯示 ---
        // 獲取電池狀態 (首次更新時獲取，後續通過 ACTION_BATTERY_CHANGED 觸發)
        Intent batteryStatus = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        int level = batteryStatus != null ? batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) : -1;
        int scale = batteryStatus != null ? batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1) : -1;
        float batteryPct = (scale > 0) ? (level / (float) scale * 100) : 0; // 防止除以零
        String batteryText = String.format("電量: %.0f%%", batteryPct);

        // 獲取亮度狀態
        String brightnessText = "亮度: --";
        // 僅當有 WRITE_SETTINGS 權限時才嘗試讀取亮度
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.System.canWrite(context)) {
            try {
                int brightness = Settings.System.getInt(context.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS);
                brightnessText = String.format("亮度: %d", brightness);
            } catch (Settings.SettingNotFoundException e) {
                Log.e("Widget", "無法讀取亮度設定: " + e.getMessage());
            }
        }
        views.setTextViewText(R.id.tvBatteryBrightnessStatus, batteryText + " | " + brightnessText);


        // --- 3. 更新 Wi-Fi 按鈕狀態並設定點擊事件 ---
        WifiManager wifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wifiManager != null) {
            boolean isWifiEnabled = wifiManager.isWifiEnabled();
            views.setTextViewText(R.id.btnToggleWifi, isWifiEnabled ? "Wi-Fi ON" : "Wi-Fi OFF");
            views.setTextColor(R.id.btnToggleWifi, isWifiEnabled ? Color.BLUE : Color.BLACK); // 開啟藍色，關閉黑色

            Intent toggleWifiIntent = new Intent(context, MyVolumeWidgetProvider.class);
            toggleWifiIntent.setAction(ACTION_TOGGLE_WIFI);
            // PendingIntent.FLAG_IMMUTABLE 是必須的 (API 31+)
            PendingIntent pendingToggleWifiIntent = PendingIntent.getBroadcast(context, 3, toggleWifiIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            views.setOnClickPendingIntent(R.id.btnToggleWifi, pendingToggleWifiIntent);
        } else {
            views.setTextViewText(R.id.btnToggleWifi, "Wi-Fi N/A");
            views.setTextColor(R.id.btnToggleWifi, Color.GRAY); // 不可用時顯示灰色
        }

        // --- 4. 更新藍牙按鈕狀態並設定點擊事件 ---
        BluetoothManager bluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter bluetoothAdapter = bluetoothManager != null ? bluetoothManager.getAdapter() : null;
        if (bluetoothAdapter != null) {
            boolean isBluetoothEnabled = bluetoothAdapter.isEnabled();
            views.setTextViewText(R.id.btnToggleBluetooth, isBluetoothEnabled ? "藍牙 ON" : "藍牙 OFF");
            views.setTextColor(R.id.btnToggleBluetooth, isBluetoothEnabled ? Color.BLUE : Color.BLACK); // 開啟藍色，關閉黑色

            Intent toggleBluetoothIntent = new Intent(context, MyVolumeWidgetProvider.class);
            toggleBluetoothIntent.setAction(ACTION_TOGGLE_BLUETOOTH);
            PendingIntent pendingToggleBluetoothIntent = PendingIntent.getBroadcast(context, 4, toggleBluetoothIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            views.setOnClickPendingIntent(R.id.btnToggleBluetooth, pendingToggleBluetoothIntent);
        } else {
            views.setTextViewText(R.id.btnToggleBluetooth, "藍牙 N/A");
            views.setTextColor(R.id.btnToggleBluetooth, Color.GRAY); // 不可用時顯示灰色
        }

        // --- 5. 設定行動數據按鈕 (跳轉設定) ---
        views.setTextViewText(R.id.btnToggleMobileData, "數據設定");
        views.setTextColor(R.id.btnToggleMobileData, Color.BLACK);
        Intent mobileDataIntent = new Intent(context, MyVolumeWidgetProvider.class);
        mobileDataIntent.setAction(ACTION_TOGGLE_MOBILE_DATA_SETTINGS);
        PendingIntent pendingMobileDataIntent = PendingIntent.getBroadcast(context, 5, mobileDataIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.btnToggleMobileData, pendingMobileDataIntent);

        // --- 6. 設定啟動應用程式按鈕 ---
        views.setTextViewText(R.id.btnLaunchApp, "開應用APP");
        views.setTextColor(R.id.btnLaunchApp, Color.BLACK);
        Intent launchAppIntent = new Intent(context, MainActivity.class);
        // PendingIntent.getActivity 用於啟動 Activity
        PendingIntent pendingLaunchAppIntent = PendingIntent.getActivity(context, 6, launchAppIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.btnLaunchApp, pendingLaunchAppIntent);

        // --- 7. 設定亮度調整按鈕 ---
        // 文字已在 XML 中設定 ("亮", "暗")，這裡只需設定顏色
        views.setTextColor(R.id.btnBrightnessUp, Color.BLACK);
        views.setTextColor(R.id.btnBrightnessDown, Color.BLACK);

        Intent brightnessUpIntent = new Intent(context, MyVolumeWidgetProvider.class);
        brightnessUpIntent.setAction(ACTION_BRIGHTNESS_UP);
        PendingIntent pendingBrightnessUpIntent = PendingIntent.getBroadcast(context, 7, brightnessUpIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.btnBrightnessUp, pendingBrightnessUpIntent);

        Intent brightnessDownIntent = new Intent(context, MyVolumeWidgetProvider.class);
        brightnessDownIntent.setAction(ACTION_BRIGHTNESS_DOWN);
        PendingIntent pendingBrightnessDownIntent = PendingIntent.getBroadcast(context, 8, brightnessDownIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.btnBrightnessDown, pendingBrightnessDownIntent);


        // --- 8. 設定音量控制按鈕 (已在 XML 中設定文字 "＋", "－") ---
        views.setTextColor(R.id.btnVolumeUp, Color.BLACK);
        views.setTextColor(R.id.btnVolumeDown, Color.BLACK);

        Intent volumeUpIntent = new Intent(context, MyVolumeWidgetProvider.class);
        volumeUpIntent.setAction(ACTION_VOLUME_UP);
        PendingIntent pendingVolumeUpIntent = PendingIntent.getBroadcast(context, 0, volumeUpIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.btnVolumeUp, pendingVolumeUpIntent);

        Intent volumeDownIntent = new Intent(context, MyVolumeWidgetProvider.class);
        volumeDownIntent.setAction(ACTION_VOLUME_DOWN);
        PendingIntent pendingVolumeDownIntent = PendingIntent.getBroadcast(context, 1, volumeDownIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.btnVolumeDown, pendingVolumeDownIntent);

        // --- 最後，將更新應用到 Widget 上 ---
        appWidgetManager.updateAppWidget(appWidgetId, views);
    }

    /**
     * 接收來自系統或 Widget 點擊事件的廣播。
     * 這是處理所有 Widget 互動的入口點。
     */
    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent); // 必須呼叫父類的 onReceive

        // 如果 Intent 或其 Action 為空，則直接返回
        if (intent == null || intent.getAction() == null) {
            return;
        }

        Log.d("MyVolumeWidgetProvider", "Received action: " + intent.getAction());

        // 獲取必要的系統服務
        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        WifiManager wifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        BluetoothManager bluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter bluetoothAdapter = bluetoothManager != null ? bluetoothManager.getAdapter() : null;

        String action = intent.getAction(); // 獲取當前接收到的 Action

        // --- 根據不同的 Action 執行對應的邏輯 ---
        switch (action) {
            // 音量控制
            case ACTION_VOLUME_UP:
                if (audioManager != null) {
                    audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_PLAY_SOUND);
                }
                break;
            case ACTION_VOLUME_DOWN:
                if (audioManager != null) {
                    audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_PLAY_SOUND);
                }
                break;
            case ACTION_MUTE_TOGGLE:
                if (audioManager != null) {
                    int currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
                    if (currentVolume == 0) {
                        int maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, maxVolume / 2, AudioManager.FLAG_PLAY_SOUND);
                    } else {
                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, AudioManager.FLAG_PLAY_SOUND);
                    }
                }
                break;

            // Wi-Fi 切換 (適應 Android 10+ 限制)
            case ACTION_TOGGLE_WIFI:
                if (wifiManager != null) {
                    // 檢查是否具有更改 Wi-Fi 狀態的權限
                    if (context.checkSelfPermission(android.Manifest.permission.CHANGE_WIFI_STATE) == PackageManager.PERMISSION_GRANTED) {
                        // Android 10 (API 29) 及更高版本限制直接切換 Wi-Fi
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            Intent panelIntent = new Intent(Settings.Panel.ACTION_WIFI);
                            panelIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK); // 必須添加此 Flag
                            context.startActivity(panelIntent);
                            Toast.makeText(context, "前往 Wi-Fi 設定面板", Toast.LENGTH_SHORT).show();
                        } else { // 舊版 Android 直接切換
                            wifiManager.setWifiEnabled(!wifiManager.isWifiEnabled());
                            Toast.makeText(context, "Wi-Fi 已切換", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Toast.makeText(context, "無 Wi-Fi 權限，請手動在應用程式設定中開啟", Toast.LENGTH_LONG).show();
                        // 引導用戶到應用程式的詳細設定頁面授予權限
                        Intent appSettingsIntent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + context.getPackageName()));
                        appSettingsIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        context.startActivity(appSettingsIntent);
                    }
                }
                break;

            // 藍牙切換 (適應 Android 12+ 限制)
            case ACTION_TOGGLE_BLUETOOTH:
                if (bluetoothAdapter != null) {
                    // Android 12 (API 31) 及更高版本限制直接開啟/關閉藍牙
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        Toast.makeText(context, "請在系統藍牙設定中切換", Toast.LENGTH_LONG).show();
                        Intent bluetoothSettingsIntent = new Intent(Settings.ACTION_BLUETOOTH_SETTINGS);
                        bluetoothSettingsIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK); // 必須添加此 Flag
                        context.startActivity(bluetoothSettingsIntent);
                    } else { // 舊版 Android 直接切換 (會有 Deprecated 警告，但功能有效)
                        // 確保有 BLUETOOTH_ADMIN 權限
                        if (bluetoothAdapter.isEnabled()) {
                            bluetoothAdapter.disable();
                            Toast.makeText(context, "藍牙已關閉", Toast.LENGTH_SHORT).show();
                        } else {
                            bluetoothAdapter.enable();
                            Toast.makeText(context, "藍牙已開啟", Toast.LENGTH_SHORT).show();
                        }
                    }
                } else {
                    Toast.makeText(context, "設備不支持藍牙", Toast.LENGTH_SHORT).show();
                }
                break;

            // 跳轉到行動網路設定
            case ACTION_TOGGLE_MOBILE_DATA_SETTINGS:
                Intent settingsIntent = new Intent(Settings.ACTION_WIRELESS_SETTINGS);
                settingsIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK); // 必須添加此 Flag
                context.startActivity(settingsIntent);
                Toast.makeText(context, "前往行動網路設定", Toast.LENGTH_SHORT).show();
                break;

            // 啟動應用程式
            case ACTION_LAUNCH_APP:
                Intent launchAppIntent = new Intent(context, MainActivity.class);
                launchAppIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK); // 必須添加此 Flag
                context.startActivity(launchAppIntent);
                Toast.makeText(context, "開啟應用程式", Toast.LENGTH_SHORT).show();
                break;

            // 亮度調整
            case ACTION_BRIGHTNESS_UP:
                adjustBrightness(context, 10); // 增加亮度
                break;
            case ACTION_BRIGHTNESS_DOWN:
                adjustBrightness(context, -10); // 降低亮度
                break;

            // 處理系統廣播：這些廣播的目的是觸發 Widget UI 更新
            case AppWidgetManager.ACTION_APPWIDGET_UPDATE:
            case Intent.ACTION_SCREEN_ON:
            case Intent.ACTION_SCREEN_OFF:
            case Intent.ACTION_BATTERY_CHANGED:
                // 這些 Action 只是觸發 Widget 的 onUpdate，以便更新 UI 狀態
                // 實際的更新邏輯在 updateAppWidget 方法中處理
                break; // 這裡不執行特定邏輯，下方會統一更新 Widget

            default:
                Log.w("MyVolumeWidgetProvider", "Unhandled action: " + action);
                break;
        }

        // --- 無論處理何種 Action，最後都要更新所有 Widget 實例，以確保 UI 狀態是最新的 ---
        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
        ComponentName thisAppWidget = new ComponentName(context.getPackageName(), MyVolumeWidgetProvider.class.getName());
        int[] appWidgetIds = appWidgetManager.getAppWidgetIds(thisAppWidget);
        for (int appWidgetId : appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId);
        }
    }

    /**
     * 輔助方法：調整螢幕亮度。
     * 需要 WRITE_SETTINGS 權限，並且在 Android 6.0+ 上需要用戶手動授予。
     */
    private void adjustBrightness(Context context, int delta) {
        // 僅在 Android 6.0 (API 23) 及更高版本上支持此功能
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // 檢查是否具有寫入系統設定的權限
            if (!Settings.System.canWrite(context)) {
                Toast.makeText(context, "請授予寫入系統設定權限以調整亮度", Toast.LENGTH_LONG).show();
                // 引導用戶到應用程式的寫入設定權限頁面
                Intent intent = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:" + context.getPackageName()));
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK); // 必須添加此 Flag
                context.startActivity(intent);
                return; // 沒有權限則直接返回
            }

            try {
                // 獲取當前亮度值 (0-255)
                int currentBrightness = Settings.System.getInt(context.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS);
                int newBrightness = currentBrightness + delta;

                // 將亮度值限制在有效範圍內 (通常是 1 到 255，避免完全黑屏)
                if (newBrightness < 1) newBrightness = 1;
                if (newBrightness > 255) newBrightness = 255;

                // 設定亮度模式為手動，以防止自動亮度覆蓋
                Settings.System.putInt(context.getContentResolver(),
                        Settings.System.SCREEN_BRIGHTNESS_MODE,
                        Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);

                // 設定新的亮度值
                Settings.System.putInt(context.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, newBrightness);
                Toast.makeText(context, "亮度調整為: " + newBrightness, Toast.LENGTH_SHORT).show();
                Log.d("Brightness", "亮度調整為: " + newBrightness);
            } catch (Settings.SettingNotFoundException e) {
                Log.e("Brightness", "亮度設定未找到: " + e.getMessage());
                Toast.makeText(context, "亮度設定錯誤", Toast.LENGTH_SHORT).show();
            } catch (SecurityException e) {
                Log.e("Brightness", "無權限寫入亮度設定: " + e.getMessage());
                Toast.makeText(context, "無權限調整亮度", Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(context, "此功能需要 Android 6.0 或更高版本", Toast.LENGTH_SHORT).show();
        }
    }
}