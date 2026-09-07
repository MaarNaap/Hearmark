package com.example.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.ui.Loc

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
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    companion object {
        private const val NOTIFICATION_ID = 808
    }
}
