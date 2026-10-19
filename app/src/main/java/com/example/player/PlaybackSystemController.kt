package com.example.player

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.support.v4.media.session.MediaSessionCompat
import android.telephony.TelephonyManager
import android.util.Log
import android.view.KeyEvent
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*

internal object PlaybackSystemController {

    fun acquirePlaybackWakeLock() {
        with(AudioPlayerManager) {
            try {
                val ctx = appContext ?: return
                if (playbackWakeLock == null) {
                    val pm = ctx.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
                    playbackWakeLock = pm?.newWakeLock(
                        android.os.PowerManager.PARTIAL_WAKE_LOCK,
                        "Hearmark:AudioPlaybackWakeLock"
                    )?.apply {
                        setReferenceCounted(false)
                    }
                }
                if (playbackWakeLock?.isHeld == false) {
                    playbackWakeLock?.acquire(6 * 60 * 60 * 1000L) // max 6 hours safety timeout
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to acquire playback wake lock: ${e.message}")
            }
        }
    }

    fun releasePlaybackWakeLock() {
        with(AudioPlayerManager) {
            try {
                if (playbackWakeLock?.isHeld == true) {
                    playbackWakeLock?.release()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to release playback wake lock: ${e.message}")
            }
        }
    }

    var isPausedForCall = false
        internal set

    private var callMonitoringJob: Job? = null
    internal var modeChangedListener: Any? = null

    fun onCallStarted() {
        with(AudioPlayerManager) {
            if (_isPlaying.value) {
                Log.i(TAG, "Incoming/Active phone call started. Pausing playback immediately.")
                isPausedForCall = true
                pause(abandonFocus = false)
            }
            callMonitoringJob?.cancel()
            callMonitoringJob = coroutineScope.launch(Dispatchers.Default) {
                while (isPausedForCall) {
                    delay(1000)
                    val mode = audioManager?.mode ?: AudioManager.MODE_NORMAL
                    if (mode == AudioManager.MODE_NORMAL) {
                        withContext(Dispatchers.Main) {
                            onCallEnded()
                        }
                        break
                    }
                }
            }
        }
    }

    fun onCallEnded() {
        with(AudioPlayerManager) {
            callMonitoringJob?.cancel()
            callMonitoringJob = null
            if (isPausedForCall) {
                isPausedForCall = false
                Log.i(TAG, "Phone call ended. Resuming playback.")
                resume()
            }
        }
    }

    val focusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        with(AudioPlayerManager) {
            when (focusChange) {
                AudioManager.AUDIOFOCUS_LOSS -> {
                    resumeOnFocusGain = false
                    isPausedForCall = false
                    pause(abandonFocus = true)
                }
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                    val wasPlaying = _isPlaying.value
                    pause(abandonFocus = false)
                    resumeOnFocusGain = wasPlaying
                }
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                    // Educational/speech audio must pause rather than duck so user never misses words or calls
                    val wasPlaying = _isPlaying.value
                    pause(abandonFocus = false)
                    resumeOnFocusGain = wasPlaying
                }
                AudioManager.AUDIOFOCUS_GAIN -> {
                    mediaPlayer?.setVolume(1.0f, 1.0f)
                    if (resumeOnFocusGain && !isPausedForCall) {
                        resumeOnFocusGain = false
                        resume()
                    }
                }
            }
        }
    }

    fun requestAudioFocus(): Boolean {
        with(AudioPlayerManager) {
            if (audioManager == null) return true
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val playbackAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
                audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(playbackAttributes)
                    .setAcceptsDelayedFocusGain(true)
                    .setWillPauseWhenDucked(true)
                    .setOnAudioFocusChangeListener(focusChangeListener)
                    .build()
                audioManager?.requestAudioFocus(audioFocusRequest!!) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            } else {
                @Suppress("DEPRECATION")
                audioManager?.requestAudioFocus(
                    focusChangeListener,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN
                ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            }
        }
    }

    fun abandonAudioFocus() {
        with(AudioPlayerManager) {
            if (audioManager == null) return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { audioManager?.abandonAudioFocusRequest(it) }
            } else {
                @Suppress("DEPRECATION")
                audioManager?.abandonAudioFocus(focusChangeListener)
            }
        }
    }

    val headsetClickRunnable = Runnable {
        with(AudioPlayerManager) {
            val count = headsetClickCount
            headsetClickCount = 0
            if (!isHeadsetControlsEnabled.value) return@Runnable

            when (count) {
                1 -> {
                    if (_isPlaying.value) pause() else resume()
                }
                2 -> {
                    if (headsetMultiClickAction.value == "SKIP_SECONDS") {
                        skipForward()
                    } else {
                        playNextTrack()
                    }
                }
                3 -> {
                    if (headsetMultiClickAction.value == "SKIP_SECONDS") {
                        skipBackward()
                    } else {
                        playPreviousTrack()
                    }
                }
            }
        }
    }

    val mediaSessionCallback = object : MediaSessionCompat.Callback() {
        override fun onPlay() {
            if (AudioPlayerManager.isHeadsetControlsEnabled.value) {
                AudioPlayerManager.resume()
            }
        }

        override fun onPause() {
            if (AudioPlayerManager.isHeadsetControlsEnabled.value) {
                AudioPlayerManager.pause()
            }
        }

        override fun onSkipToNext() {
            if (AudioPlayerManager.isHeadsetControlsEnabled.value) {
                AudioPlayerManager.playNextTrack()
            }
        }

        override fun onSkipToPrevious() {
            if (AudioPlayerManager.isHeadsetControlsEnabled.value) {
                AudioPlayerManager.playPreviousTrack()
            }
        }

        override fun onFastForward() {
            if (AudioPlayerManager.isHeadsetControlsEnabled.value) {
                AudioPlayerManager.skipForward()
            }
        }

        override fun onRewind() {
            if (AudioPlayerManager.isHeadsetControlsEnabled.value) {
                AudioPlayerManager.skipBackward()
            }
        }

        override fun onSeekTo(pos: Long) {
            AudioPlayerManager.seekTo(pos)
        }

        override fun onStop() {
            AudioPlayerManager.stop()
        }

        override fun onMediaButtonEvent(mediaButtonEvent: Intent?): Boolean {
            if (!AudioPlayerManager.isHeadsetControlsEnabled.value) return false
            val keyEvent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                mediaButtonEvent?.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
            } else {
                @Suppress("DEPRECATION")
                mediaButtonEvent?.getParcelableExtra(Intent.EXTRA_KEY_EVENT)
            } ?: return super.onMediaButtonEvent(mediaButtonEvent)

            return AudioPlayerManager.handleMediaKeyEvent(keyEvent) || super.onMediaButtonEvent(mediaButtonEvent)
        }
    }

    val becomingNoisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                AudioPlayerManager.pause()
            }
        }
    }

    val notificationControlReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            try {
                when (intent?.action) {
                    "com.example.ACTION_PLAY_PAUSE" -> {
                        if (AudioPlayerManager._isPlaying.value) AudioPlayerManager.pause() else AudioPlayerManager.resume()
                    }
                    "com.example.ACTION_SKIP_FORWARD" -> {
                        AudioPlayerManager.skipForward()
                    }
                    "com.example.ACTION_SKIP_BACKWARD" -> {
                        AudioPlayerManager.skipBackward()
                    }
                }
            } catch (e: Exception) {
                Log.e("AudioPlayerManager", "Notification control error: ${e.message}")
            }
        }
    }

    val phoneStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == TelephonyManager.ACTION_PHONE_STATE_CHANGED ||
                intent?.action == "android.intent.action.PHONE_STATE") {
                val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
                Log.d("AudioPlayerManager", "Phone state broadcast: state=$state")
                when (state) {
                    TelephonyManager.EXTRA_STATE_RINGING,
                    TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                        onCallStarted()
                    }
                    TelephonyManager.EXTRA_STATE_IDLE -> {
                        onCallEnded()
                    }
                }
            }
        }
    }

    fun registerSystemReceiversAndSession(context: Context) {
        with(AudioPlayerManager) {
            // Initialize MediaSessionCompat for headset buttons & system media control
            try {
                val mediaButtonReceiverComponent = ComponentName(context, androidx.media.session.MediaButtonReceiver::class.java)
                mediaSession = MediaSessionCompat(context, "HearmarkMediaSession", mediaButtonReceiverComponent, null).apply {
                    @Suppress("DEPRECATION")
                    setFlags(
                        MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                        MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
                    )
                    val mediaButtonIntent = Intent(Intent.ACTION_MEDIA_BUTTON).apply {
                        setClass(context, androidx.media.session.MediaButtonReceiver::class.java)
                    }
                    val mediaButtonPendingIntent = PendingIntent.getBroadcast(
                        context,
                        0,
                        mediaButtonIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    setMediaButtonReceiver(mediaButtonPendingIntent)
                    setCallback(mediaSessionCallback)
                    isActive = true
                }
                updateMediaSessionPlaybackState()
            } catch (e: Exception) {
                Log.e(TAG, "Error creating MediaSession: ${e.message}")
            }

            // Register Headphone Unplug Broadcast Receiver across all supported API levels (API 24+)
            try {
                val filter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
                context.applicationContext.registerReceiver(becomingNoisyReceiver, filter)
            } catch (e: Exception) {
                Log.e(TAG, "Error registering noisy receiver: ${e.message}")
            }

            // Register Notification Playback Controls Broadcast Receiver
            try {
                val controlFilter = IntentFilter().apply {
                    addAction("com.example.ACTION_PLAY_PAUSE")
                    addAction("com.example.ACTION_SKIP_FORWARD")
                    addAction("com.example.ACTION_SKIP_BACKWARD")
                }
                ContextCompat.registerReceiver(
                    context.applicationContext,
                    notificationControlReceiver,
                    controlFilter,
                    ContextCompat.RECEIVER_NOT_EXPORTED
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error registering control receiver: ${e.message}")
            }

            // Register Phone State Broadcast Receiver for incoming and active calls
            try {
                val phoneFilter = IntentFilter().apply {
                    addAction(TelephonyManager.ACTION_PHONE_STATE_CHANGED)
                    addAction("android.intent.action.PHONE_STATE")
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    ContextCompat.registerReceiver(
                        context.applicationContext,
                        phoneStateReceiver,
                        phoneFilter,
                        ContextCompat.RECEIVER_EXPORTED
                    )
                } else {
                    context.applicationContext.registerReceiver(phoneStateReceiver, phoneFilter)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error registering phoneStateReceiver: ${e.message}")
            }

            // Register OnModeChangedListener on Android 12+ (API 31+) to catch calls in silent/vibrate mode
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                try {
                    val listener = AudioManager.OnModeChangedListener { mode ->
                        Log.d(TAG, "AudioManager mode changed: mode=$mode")
                        when (mode) {
                            AudioManager.MODE_RINGTONE,
                            AudioManager.MODE_IN_CALL,
                            AudioManager.MODE_IN_COMMUNICATION -> {
                                onCallStarted()
                            }
                            AudioManager.MODE_NORMAL -> {
                                onCallEnded()
                            }
                        }
                    }
                    audioManager?.addOnModeChangedListener(ContextCompat.getMainExecutor(context), listener)
                    modeChangedListener = listener
                } catch (e: Exception) {
                    Log.w(TAG, "Error registering OnModeChangedListener: ${e.message}")
                }
            }
        }
    }
}
