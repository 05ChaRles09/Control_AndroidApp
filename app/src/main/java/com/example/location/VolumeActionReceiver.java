package com.example.location;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;

public class VolumeActionReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            boolean isMuted = audioManager.isStreamMute(AudioManager.STREAM_MUSIC);
            audioManager.adjustStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    isMuted ? AudioManager.ADJUST_UNMUTE : AudioManager.ADJUST_MUTE,
                    AudioManager.FLAG_SHOW_UI
            );
        }
    }
}