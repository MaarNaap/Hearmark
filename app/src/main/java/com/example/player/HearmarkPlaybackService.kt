package com.example.player

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log

class HearmarkPlaybackService : Service() {

    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun getService(): HearmarkPlaybackService = this@HearmarkPlaybackService
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onCreate() {
        super.onCreate()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null) {
            AudioPlayerManager.getMediaSession()?.let { mediaSession ->
                androidx.media.session.MediaButtonReceiver.handleIntent(mediaSession, intent)
            }
        }
        try {
            val notification = AudioPlayerManager.buildNotification(this)
            if (notification != null && (AudioPlayerManager.isPlaying.value || AudioPlayerManager.currentTrackValue != null)) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(
                        AudioPlayerManager.NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                    )
                } else {
                    startForeground(AudioPlayerManager.NOTIFICATION_ID, notification)
                }
            }
        } catch (e: Exception) {
            Log.w("HearmarkPlaybackService", "startForeground failed: ${e.message}")
        }
        return START_STICKY
    }

    override fun onDestroy() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
        } catch (e: Exception) {
            // Ignore
        }
        super.onDestroy()
    }
}
