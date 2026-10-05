package com.example.player

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import android.view.KeyEvent
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.data.*
import com.example.ui.Loc

internal object PlaybackNotificationController {
    private const val CHANNEL_ID = "smart_audio_player_channel"

    fun handleMediaKeyEvent(keyEvent: KeyEvent): Boolean = with(AudioPlayerManager) {
        if (!isHeadsetControlsEnabled.value) return false

        val keyCode = keyEvent.keyCode
        val action = keyEvent.action

        if (action != KeyEvent.ACTION_UP) {
            return when (keyCode) {
                KeyEvent.KEYCODE_HEADSETHOOK,
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                KeyEvent.KEYCODE_MEDIA_PLAY,
                KeyEvent.KEYCODE_MEDIA_PAUSE,
                KeyEvent.KEYCODE_MEDIA_NEXT,
                KeyEvent.KEYCODE_MEDIA_PREVIOUS,
                KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
                KeyEvent.KEYCODE_MEDIA_REWIND,
                KeyEvent.KEYCODE_MEDIA_STEP_FORWARD,
                KeyEvent.KEYCODE_MEDIA_STEP_BACKWARD,
                KeyEvent.KEYCODE_MEDIA_STOP -> true
                else -> false
            }
        }

        when (keyCode) {
            KeyEvent.KEYCODE_HEADSETHOOK,
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                headsetClickHandler.removeCallbacks(headsetClickRunnable)
                headsetClickCount++
                headsetClickHandler.postDelayed(headsetClickRunnable, 350L)
                return true
            }
            KeyEvent.KEYCODE_MEDIA_PLAY -> {
                resume()
                return true
            }
            KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                pause()
                return true
            }
            KeyEvent.KEYCODE_MEDIA_NEXT,
            KeyEvent.KEYCODE_MEDIA_STEP_FORWARD -> {
                playNextTrack()
                return true
            }
            KeyEvent.KEYCODE_MEDIA_PREVIOUS,
            KeyEvent.KEYCODE_MEDIA_STEP_BACKWARD -> {
                playPreviousTrack()
                return true
            }
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                skipForward()
                return true
            }
            KeyEvent.KEYCODE_MEDIA_REWIND -> {
                skipBackward()
                return true
            }
            KeyEvent.KEYCODE_MEDIA_STOP -> {
                pause()
                return true
            }
        }
        return false
    }

    fun setHeadsetSettings(enabled: Boolean, action: String) {
        with(AudioPlayerManager) {
            isHeadsetControlsEnabled.value = enabled
            headsetMultiClickAction.value = action
            appContext?.let { ctx ->
                try {
                    val sharedPref = ctx.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
                    sharedPref.edit()
                        .putBoolean("headset_controls_enabled", enabled)
                        .putString("headset_multiclick_action", action)
                        .apply()
                } catch (e: Exception) {
                    Log.e(TAG, "Error saving headset settings: ${e.message}")
                }
            }
        }
    }

    fun updateMediaSessionPlaybackState() {
        with(AudioPlayerManager) {
            val session = mediaSession ?: return
            try {
                val state = if (_isPlaying.value) {
                    PlaybackStateCompat.STATE_PLAYING
                } else if (currentTrackValue != null) {
                    PlaybackStateCompat.STATE_PAUSED
                } else {
                    PlaybackStateCompat.STATE_STOPPED
                }

                val actions = PlaybackStateCompat.ACTION_PLAY or
                        PlaybackStateCompat.ACTION_PAUSE or
                        PlaybackStateCompat.ACTION_PLAY_PAUSE or
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                        PlaybackStateCompat.ACTION_FAST_FORWARD or
                        PlaybackStateCompat.ACTION_REWIND or
                        PlaybackStateCompat.ACTION_SEEK_TO or
                        PlaybackStateCompat.ACTION_STOP

                val currentPos = try {
                    mediaPlayer?.currentPosition?.toLong()?.coerceAtLeast(0L) ?: _currentPosition.value
                } catch (e: Exception) {
                    _currentPosition.value
                }

                val playbackSpeed = if (_isPlaying.value) _playbackSpeed.value else 0f
                val stateBuilder = PlaybackStateCompat.Builder()
                    .setActions(actions)
                    .setState(state, currentPos, playbackSpeed, android.os.SystemClock.elapsedRealtime())

                session.setPlaybackState(stateBuilder.build())
            } catch (e: Exception) {
                Log.e(TAG, "Error updating playback state: ${e.message}")
            }
        }
    }

    fun updateMediaSessionMetadata(track: AudioTrack?) {
        with(AudioPlayerManager) {
            val session = mediaSession ?: return
            if (track == null) {
                session.setMetadata(null)
                return
            }
            try {
                val cleanTitle = track.getDisplayTitle()
                val context = appContext
                val isDark = isDarkThemeActive()
                val artworkBitmap = context?.let { getLargeIconBitmap(it, isDark) }

                val trackDuration = if (track.duration > 0) {
                    track.duration
                } else {
                    try {
                        mediaPlayer?.duration?.toLong()?.coerceAtLeast(0L) ?: 0L
                    } catch (e: Exception) {
                        0L
                    }
                }

                val metadataBuilder = MediaMetadataCompat.Builder()
                    .putString(MediaMetadataCompat.METADATA_KEY_TITLE, cleanTitle)
                    .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "Hearmark")
                    .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, "Audio Library")
                    .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, trackDuration)
                    .putLong(MediaMetadataCompat.METADATA_KEY_TRACK_NUMBER, 1L)

                if (artworkBitmap != null) {
                    metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, artworkBitmap)
                    metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ART, artworkBitmap)
                    metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON, artworkBitmap)
                }

                session.setMetadata(metadataBuilder.build())
            } catch (e: Exception) {
                Log.e(TAG, "Error updating metadata: ${e.message}")
            }
        }
    }

    fun startPlaybackService() {
        with(AudioPlayerManager) {
            appContext?.let { ctx ->
                try {
                    val intent = Intent(ctx, HearmarkPlaybackService::class.java)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && _isPlaying.value) {
                        ContextCompat.startForegroundService(ctx, intent)
                    } else {
                        ctx.startService(intent)
                    }
                } catch (e: Exception) {
                    try {
                        ctx.startService(Intent(ctx, HearmarkPlaybackService::class.java))
                    } catch (e2: Exception) {
                        Log.e(TAG, "Failed to start HearmarkPlaybackService: ${e2.message}")
                    }
                }
            }
        }
    }

    fun stopPlaybackService() {
        with(AudioPlayerManager) {
            appContext?.let { ctx ->
                try {
                    ctx.stopService(Intent(ctx, HearmarkPlaybackService::class.java))
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to stop HearmarkPlaybackService: ${e.message}")
                }
            }
        }
    }

    fun isDarkThemeActive(): Boolean = with(AudioPlayerManager) {
        val context = appContext ?: return true
        return try {
            val sharedPref = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            val themeSetting = sharedPref.getString("theme", "dark") ?: "dark"
            when (themeSetting) {
                "light" -> false
                "dark" -> true
                else -> {
                    val nightModeFlags = context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
                    nightModeFlags == android.content.res.Configuration.UI_MODE_NIGHT_YES
                }
            }
        } catch (e: Exception) {
            true
        }
    }

    fun buildNotification(context: Context? = AudioPlayerManager.appContext): android.app.Notification? = with(AudioPlayerManager) {
        val ctx = context ?: appContext ?: return null
        val track = currentTrackValue ?: return null
        return try {
            val notificationManager = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Hearmark Playback Control",
                    NotificationManager.IMPORTANCE_LOW
                )
                notificationManager?.createNotificationChannel(channel)
            }

            // Intents for controls
            val intentOpen = Intent(ctx, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntentOpen = PendingIntent.getActivity(
                ctx, 0, intentOpen,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val playIcon = if (_isPlaying.value) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
            val playActionTitle = if (_isPlaying.value) Loc.getText("pause") else Loc.getText("play")

            val playIntent = Intent("com.example.ACTION_PLAY_PAUSE").apply {
                `package` = ctx.packageName
            }
            val playPendingIntent = PendingIntent.getBroadcast(
                ctx, 10, playIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val skipForwardIntent = Intent("com.example.ACTION_SKIP_FORWARD").apply {
                `package` = ctx.packageName
            }
            val skipForwardPendingIntent = PendingIntent.getBroadcast(
                ctx, 20, skipForwardIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val skipBackwardIntent = Intent("com.example.ACTION_SKIP_BACKWARD").apply {
                `package` = ctx.packageName
            }
            val skipBackwardPendingIntent = PendingIntent.getBroadcast(
                ctx, 30, skipBackwardIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val isDark = isDarkThemeActive()
            val largeIcon = getLargeIconBitmap(ctx, isDark)
            val cleanTitle = track.getDisplayTitle()
            val statusText = if (_isPlaying.value) {
                "Hearmark • ${Loc.getText("listening")}"
            } else {
                "Hearmark • ${Loc.getText("paused")}"
            }

            val mediaStyle = androidx.media.app.NotificationCompat.MediaStyle()
                .setShowActionsInCompactView(0, 1, 2)
                .setMediaSession(mediaSession?.sessionToken)

            val notificationBgColor = if (isDark) {
                0xFF1E1A22.toInt()
            } else {
                0xFFF4EEF8.toInt()
            }

            val durationMs = if (_duration.value > 0) _duration.value else track.duration
            val currentPosMs = _currentPosition.value

            NotificationCompat.Builder(ctx, CHANNEL_ID)
                .setSmallIcon(com.example.R.drawable.ic_logo)
                .setContentTitle(cleanTitle)
                .setContentText(statusText)
                .setContentIntent(pendingIntentOpen)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOngoing(_isPlaying.value)
                .setColor(notificationBgColor)
                .setColorized(true)
                .setStyle(mediaStyle)
                .apply {
                    if (durationMs > 0) {
                        setProgress(durationMs.toInt(), currentPosMs.toInt(), false)
                    }
                    if (largeIcon != null) {
                        setLargeIcon(largeIcon)
                    }
                }
                .addAction(android.R.drawable.ic_media_rew, "⏪", skipBackwardPendingIntent)
                .addAction(playIcon, playActionTitle, playPendingIntent)
                .addAction(android.R.drawable.ic_media_ff, "⏩", skipForwardPendingIntent)
                .build()
        } catch (e: Exception) {
            Log.w(TAG, "buildNotification failed: ${e.message}")
            null
        }
    }

    fun buildFallbackNotification(context: Context): android.app.Notification {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Hearmark Playback Control",
                NotificationManager.IMPORTANCE_LOW
            )
            notificationManager?.createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(com.example.R.drawable.ic_logo)
            .setContentTitle("Hearmark")
            .setContentText(Loc.getText("paused"))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    fun showNotification() {
        with(AudioPlayerManager) {
            val context = appContext ?: return
            try {
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
                val notification = buildNotification(context) ?: return
                notificationManager.notify(NOTIFICATION_ID, notification)
            } catch (e: Exception) {
                Log.w(TAG, "showNotification failed: ${e.message}")
            }
        }
    }

    fun updateNotification() {
        with(AudioPlayerManager) {
            if (_isPlaying.value || currentTrackValue != null) {
                showNotification()
            }
        }
    }

    private fun getLargeIconBitmap(context: Context, isDark: Boolean = isDarkThemeActive()): android.graphics.Bitmap? {
        return try {
            val size = (64 * context.resources.displayMetrics.density).toInt()
            val bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)

            // Draw a themed circular background plate for clear contrast
            val bgPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = if (isDark) 0xFF352C42.toInt() else 0xFFEADBFC.toInt()
                style = android.graphics.Paint.Style.FILL
            }
            val radius = size / 2f
            canvas.drawCircle(radius, radius, radius, bgPaint)

            val drawable = ContextCompat.getDrawable(context, com.example.R.drawable.ic_logo)?.mutate() ?: return bitmap
            val tintColor = if (isDark) 0xFFD3C2FF.toInt() else 0xFF6750A4.toInt()
            androidx.core.graphics.drawable.DrawableCompat.setTint(drawable, tintColor)

            val padding = (size * 0.16f).toInt()
            drawable.setBounds(padding, padding, size - padding, size - padding)
            drawable.draw(canvas)
            bitmap
        } catch (e: Exception) {
            null
        }
    }

    fun cancelNotification() = with(AudioPlayerManager) {
        try {
            val notificationManager = appContext?.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            notificationManager?.cancel(NOTIFICATION_ID)
        } catch (e: Exception) {
            Log.e(TAG, "Notification cancel error: ${e.message}")
        }
    }

    fun sendTaskCompletionNotification(task: Task) {
        // Disabled per user request: "I do not want to receive notifications for completed tasks."
    }
}
