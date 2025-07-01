package com.example.location;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Build;
import android.widget.RemoteViews;

public class VolumeWidget extends AppWidgetProvider {

    public static final String ACTION_MUTE = "com.example.location.ACTION_MUTE";
    public static final String ACTION_VOLUME_UP = "com.example.location.ACTION_VOLUME_UP";
    public static final String ACTION_VOLUME_DOWN = "com.example.location.ACTION_VOLUME_DOWN";

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId);
        }
    }

    // 更新 Widget UI
    public static void updateWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);

        int maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        int currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);

        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_volume);

        // 設定音量百分比文字
        int percent = (int) (currentVolume * 100f / maxVolume);
        views.setTextViewText(R.id.textVolumePercent, "音量：" + percent + "%");

        // 設定音量進度條
        views.setProgressBar(R.id.progressBarVolume, 100, percent, false);

        // 靜音按鈕 PendingIntent
        Intent muteIntent = new Intent(context, VolumeWidget.class);
        muteIntent.setAction(ACTION_MUTE);
        PendingIntent mutePendingIntent = PendingIntent.getBroadcast(context, 0, muteIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.buttonMute, mutePendingIntent);

        // 減音按鈕 PendingIntent
        Intent volDownIntent = new Intent(context, VolumeWidget.class);
        volDownIntent.setAction(ACTION_VOLUME_DOWN);
        PendingIntent volDownPendingIntent = PendingIntent.getBroadcast(context, 1, volDownIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.buttonVolumeDown, volDownPendingIntent);

        // 加音按鈕 PendingIntent
        Intent volUpIntent = new Intent(context, VolumeWidget.class);
        volUpIntent.setAction(ACTION_VOLUME_UP);
        PendingIntent volUpPendingIntent = PendingIntent.getBroadcast(context, 2, volUpIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.buttonVolumeUp, volUpPendingIntent);

        appWidgetManager.updateAppWidget(appWidgetId, views);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);

        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);

        if (intent != null && intent.getAction() != null) {
            switch (intent.getAction()) {
                case ACTION_MUTE:
                    boolean isMuted = false;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        isMuted = audioManager.isStreamMute(AudioManager.STREAM_MUSIC);
                    }
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                        audioManager.adjustStreamVolume(
                                AudioManager.STREAM_MUSIC,
                                isMuted ? AudioManager.ADJUST_UNMUTE : AudioManager.ADJUST_MUTE,
                                AudioManager.FLAG_SHOW_UI
                        );
                    }
                    break;

                case ACTION_VOLUME_UP:
                    audioManager.adjustStreamVolume(
                            AudioManager.STREAM_MUSIC,
                            AudioManager.ADJUST_RAISE,
                            AudioManager.FLAG_SHOW_UI
                    );
                    break;

                case ACTION_VOLUME_DOWN:
                    audioManager.adjustStreamVolume(
                            AudioManager.STREAM_MUSIC,
                            AudioManager.ADJUST_LOWER,
                            AudioManager.FLAG_SHOW_UI
                    );
                    break;
            }
        }

        // 更新所有 Widget UI（假設只有一個 widget）
        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
        int[] appWidgetIds = appWidgetManager.getAppWidgetIds(
                new android.content.ComponentName(context, VolumeWidget.class));
        for (int appWidgetId : appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId);
        }
    }
}
