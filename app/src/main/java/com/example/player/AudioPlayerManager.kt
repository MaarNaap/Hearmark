package com.example.player

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import android.view.KeyEvent
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.ui.Loc
import com.example.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

object AudioPlayerManager {
    private const val TAG = "AudioPlayerManager"

    private var mediaPlayer: MediaPlayer? = null
    private var currentTrackValue: AudioTrack? = null
    private val _currentQueueFlow = MutableStateFlow<List<AudioTrack>>(emptyList())
    val currentQueueFlow: StateFlow<List<AudioTrack>> = _currentQueueFlow.asStateFlow()

    private var currentQueue: List<AudioTrack> = emptyList()
        set(value) {
            field = value
            _currentQueueFlow.value = value
            saveQueueToPreferences(value)
        }

    private val _currentTrack = MutableStateFlow<AudioTrack?>(null)
    val currentTrack: StateFlow<AudioTrack?> = _currentTrack.asStateFlow()

    val isAutoPlayEnabled = MutableStateFlow(true)

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    // Sleep Timer
    private var sleepTimerJob: Job? = null
    private val _sleepTimeRemaining = MutableStateFlow(0) // seconds remaining, 0 for off
    val sleepTimeRemaining: StateFlow<Int> = _sleepTimeRemaining.asStateFlow()
    private var sleepTimerOption = 0 // 0=off, 15, 30, 45, 60, -1=end of file

    // Practice Mode (Silence Detection & Imitation Pauses)
    private val _isPracticeMode = MutableStateFlow(false)
    val isPracticeMode: StateFlow<Boolean> = _isPracticeMode.asStateFlow()

    private val _isPracticeAnalyzing = MutableStateFlow(false)
    val isPracticeAnalyzing: StateFlow<Boolean> = _isPracticeAnalyzing.asStateFlow()

    private val _isPracticePausing = MutableStateFlow(false)
    val isPracticePausing: StateFlow<Boolean> = _isPracticePausing.asStateFlow()

    private val _practicePauseRemainingSeconds = MutableStateFlow(0f)
    val practicePauseRemainingSeconds: StateFlow<Float> = _practicePauseRemainingSeconds.asStateFlow()

    private val _practicePauseTotalSeconds = MutableStateFlow(0f)
    val practicePauseTotalSeconds: StateFlow<Float> = _practicePauseTotalSeconds.asStateFlow()

    private val _currentPracticeSegments = MutableStateFlow<List<Long>>(emptyList())
    val currentPracticeSegments: StateFlow<List<Long>> = _currentPracticeSegments.asStateFlow()

    private var practicePauseJob: Job? = null
    private var practiceLastSegmentStartMs = 0L
    private var practiceNextBoundaryIndex = 0

    // Subtitles & Lyrics State
    private val _subtitlesCues = MutableStateFlow<List<SubtitleCue>>(emptyList())
    val subtitlesCues: StateFlow<List<SubtitleCue>> = _subtitlesCues.asStateFlow()

    private val _activeSubtitleCue = MutableStateFlow<SubtitleCue?>(null)
    val activeSubtitleCue: StateFlow<SubtitleCue?> = _activeSubtitleCue.asStateFlow()

    enum class VideoSubtitleMode {
        SHOW,
        HIDE,
        BLACK
    }
    val videoSubtitleMode = MutableStateFlow(VideoSubtitleMode.SHOW)

    val isVideoFullWidth = MutableStateFlow(false)

    fun toggleVideoFullWidth(): Boolean {
        val next = !isVideoFullWidth.value
        isVideoFullWidth.value = next
        return next
    }

    fun setVideoFullWidth(enabled: Boolean) {
        isVideoFullWidth.value = enabled
    }

    fun toggleVideoSubtitleMode(): VideoSubtitleMode {
        val next = when (videoSubtitleMode.value) {
            VideoSubtitleMode.SHOW -> VideoSubtitleMode.HIDE
            VideoSubtitleMode.HIDE -> VideoSubtitleMode.BLACK
            VideoSubtitleMode.BLACK -> VideoSubtitleMode.SHOW
        }
        videoSubtitleMode.value = next
        return next
    }

    val isSubtitlesEnabled = MutableStateFlow(true)
    val subtitleOffsetMs = MutableStateFlow(0L)
    val subtitleFontSize = MutableStateFlow(16f)
    val showTimestampsInSubtitles = MutableStateFlow(true)

    // Video Playback Surface State
    private var activeSurface: android.view.Surface? = null
    private var activeSurfaceHolder: android.view.SurfaceHolder? = null
    private val _videoDimensions = MutableStateFlow<Pair<Int, Int>?>(null)
    val videoDimensions: StateFlow<Pair<Int, Int>?> = _videoDimensions.asStateFlow()

    private val _isVideoTrack = MutableStateFlow(false)
    val isVideoTrack: StateFlow<Boolean> = _isVideoTrack.asStateFlow()

    private val _isInPipMode = MutableStateFlow(false)
    val isInPipMode: StateFlow<Boolean> = _isInPipMode.asStateFlow()

    fun setInPipMode(inPip: Boolean) {
        _isInPipMode.value = inPip
    }

    fun attachSurface(surface: android.view.Surface?) {
        activeSurface = surface
        try {
            mediaPlayer?.let { mp ->
                mp.setSurface(surface)
                if (surface != null && surface.isValid) {
                    try {
                        val currentPos = mp.currentPosition
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            mp.seekTo(currentPos.toLong(), MediaPlayer.SEEK_CLOSEST)
                        } else {
                            mp.seekTo(currentPos)
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Frame resync after surface attach: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error attaching surface: ${e.message}")
        }
    }

    fun attachSurfaceHolder(holder: android.view.SurfaceHolder?) {
        activeSurfaceHolder = holder
        try {
            mediaPlayer?.setDisplay(holder)
        } catch (e: Exception) {
            Log.e(TAG, "Error attaching surface holder: ${e.message}")
        }
    }

    // Headset & Media Buttons Control State
    private var mediaSession: MediaSessionCompat? = null
    val mediaSessionToken: MediaSessionCompat.Token?
        get() = mediaSession?.sessionToken

    fun getMediaSession(): MediaSessionCompat? = mediaSession

    val isHeadsetControlsEnabled = MutableStateFlow(true)
    val headsetMultiClickAction = MutableStateFlow("NEXT_PREV") // "NEXT_PREV" or "SKIP_SECONDS"

    private var headsetClickCount = 0
    private val headsetClickHandler = Handler(Looper.getMainLooper())
    private val headsetClickRunnable = Runnable {
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

    // Preferences & Settings loaded
    private var completionThreshold = 90 // default 90%
    private var skipTimeSeconds = 10 // default 10 seconds
    private var isThresholdTriggeredForCurrentSession = false
    private val completedTaskIdsForCurrentSession = java.util.Collections.synchronizedSet(mutableSetOf<Long>())

    // Active listening stopwatch (measures exact wall-clock listening duration while isPlaying)
    private var sessionActualListeningMs: Long = 0L
    private var lastActivePlayTimestamp: Long = 0L

    // In-memory cached bitset of listened segments for the active track to eliminate string parsing/splitting on playback ticks
    private var activeTrackSegmentTrackId: Long? = null
    private val activeTrackSegmentsBitSet = java.util.BitSet(100)

    @Synchronized
    private fun getOrInitActiveBitSet(trackId: Long, rawSegments: String): java.util.BitSet {
        if (activeTrackSegmentTrackId != trackId) {
            activeTrackSegmentTrackId = trackId
            activeTrackSegmentsBitSet.clear()
            if (rawSegments.isNotEmpty()) {
                var current = 0
                var hasDigits = false
                for (i in 0 until rawSegments.length) {
                    val ch = rawSegments[i]
                    if (ch in '0'..'9') {
                        current = current * 10 + (ch - '0')
                        hasDigits = true
                    } else if (ch == ',') {
                        if (hasDigits && current in 0..999) {
                            activeTrackSegmentsBitSet.set(current)
                        }
                        current = 0
                        hasDigits = false
                    }
                }
                if (hasDigits && current in 0..999) {
                    activeTrackSegmentsBitSet.set(current)
                }
            }
        }
        return activeTrackSegmentsBitSet
    }

    private fun serializeBitSet(bitSet: java.util.BitSet): String {
        if (bitSet.isEmpty) return ""
        val sb = StringBuilder(bitSet.cardinality() * 4)
        var i = bitSet.nextSetBit(0)
        var first = true
        while (i >= 0) {
            if (!first) sb.append(',')
            sb.append(i)
            first = false
            i = bitSet.nextSetBit(i + 1)
        }
        return sb.toString()
    }

    @Synchronized
    private fun accumulateActiveListeningTime() {
        if (lastActivePlayTimestamp > 0L) {
            val now = System.currentTimeMillis()
            val delta = now - lastActivePlayTimestamp
            if (delta in 1..4000) {
                sessionActualListeningMs += delta
            }
            lastActivePlayTimestamp = now
        }
    }

    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var progressTrackingJob: Job? = null

    private var appContext: Context? = null
    private var repository: AppRepository? = null

    // Audio Focus and Noisy broadcast
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var isManagerInitialized = false

    fun init(context: Context, repo: AppRepository) {
        appContext = context.applicationContext
        repository = repo
        if (audioManager == null) {
            audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        }
        
        // Load playback speed and headset preferences
        try {
            val sharedPref = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            val savedSpeed = sharedPref.getFloat("playback_speed", 1.0f)
            _playbackSpeed.value = savedSpeed
            isAutoPlayEnabled.value = sharedPref.getBoolean("autoplay_enabled", true)
            isHeadsetControlsEnabled.value = sharedPref.getBoolean("headset_controls_enabled", true)
            headsetMultiClickAction.value = sharedPref.getString("headset_multiclick_action", "NEXT_PREV") ?: "NEXT_PREV"
        } catch (e: Exception) {
            Log.e(TAG, "Error loading saved settings: ${e.message}")
        }
        
        if (!isManagerInitialized) {
            isManagerInitialized = true
            // Initialize MediaSessionCompat for headset buttons & system media control
            try {
                val mediaButtonReceiverComponent = ComponentName(context, androidx.media.session.MediaButtonReceiver::class.java)
                mediaSession = MediaSessionCompat(context, "HearmarkMediaSession", mediaButtonReceiverComponent, null).apply {
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

            // Register Headphone Unplug Broadcast Receiver
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val filter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
                    context.applicationContext.registerReceiver(becomingNoisyReceiver, filter)
                }
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
        }

        // Only restore previous track from preferences if nothing is currently playing and no track is active
        if (!_isPlaying.value && mediaPlayer == null && currentTrackValue == null && _currentTrack.value == null) {
            restoreQueueAndTrack()
        }
    }

    private val mediaSessionCallback = object : MediaSessionCompat.Callback() {
        override fun onPlay() {
            if (isHeadsetControlsEnabled.value) {
                resume()
            }
        }

        override fun onPause() {
            if (isHeadsetControlsEnabled.value) {
                pause()
            }
        }

        override fun onSkipToNext() {
            if (isHeadsetControlsEnabled.value) {
                playNextTrack()
            }
        }

        override fun onSkipToPrevious() {
            if (isHeadsetControlsEnabled.value) {
                playPreviousTrack()
            }
        }

        override fun onFastForward() {
            if (isHeadsetControlsEnabled.value) {
                skipForward()
            }
        }

        override fun onRewind() {
            if (isHeadsetControlsEnabled.value) {
                skipBackward()
            }
        }

        override fun onSeekTo(pos: Long) {
            seekTo(pos)
        }

        override fun onStop() {
            stop()
        }

        override fun onMediaButtonEvent(mediaButtonEvent: Intent?): Boolean {
            if (!isHeadsetControlsEnabled.value) return false
            val keyEvent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                mediaButtonEvent?.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
            } else {
                @Suppress("DEPRECATION")
                mediaButtonEvent?.getParcelableExtra(Intent.EXTRA_KEY_EVENT)
            } ?: return super.onMediaButtonEvent(mediaButtonEvent)

            return handleMediaKeyEvent(keyEvent) || super.onMediaButtonEvent(mediaButtonEvent)
        }
    }

    fun handleMediaKeyEvent(keyEvent: KeyEvent): Boolean {
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

    private fun updateMediaSessionPlaybackState() {
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

    private fun updateMediaSessionMetadata(track: AudioTrack?) {
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

    private val becomingNoisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                pause()
            }
        }
    }

    private val notificationControlReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            try {
                when (intent?.action) {
                    "com.example.ACTION_PLAY_PAUSE" -> {
                        if (_isPlaying.value) pause() else resume()
                    }
                    "com.example.ACTION_SKIP_FORWARD" -> {
                        skipForward()
                    }
                    "com.example.ACTION_SKIP_BACKWARD" -> {
                        skipBackward()
                    }
                }
            } catch (e: Exception) {
                Log.e("AudioPlayerManager", "Notification control error: ${e.message}")
            }
        }
    }

    fun setSettings(threshold: Int, skipTime: Int) {
        completionThreshold = threshold
        skipTimeSeconds = skipTime
    }

    // Global practice segment source: "SILENCE" (Silence Analysis) or "SUBTITLES" (Subtitles/Lyrics)
    var segmentSource: String = "SILENCE"
        private set

    // Multiplier for automatic pause duration after each segment
    var practicePauseMultiplier: Float = 1.0f
        private set

    // Silence detection tuning
    var silenceSensitivity: String = "MEDIUM"
        private set
    var silenceMinDurationMs: Long = 500L
        private set
    var silencePaddingMs: Long = 200L
        private set

    fun setPracticeSettings(source: String, multiplier: Float) {
        val oldSource = segmentSource
        segmentSource = when (source.uppercase()) {
            "SUBTITLES" -> "SUBTITLES"
            "MANUAL" -> "MANUAL"
            else -> "SILENCE"
        }
        practicePauseMultiplier = multiplier.coerceIn(0.25f, 4.0f)

        if (!oldSource.equals(segmentSource, ignoreCase = true)) {
            val track = currentTrackValue
            val repo = repository
            val ctx = appContext
            if (track != null && repo != null && ctx != null) {
                if (_isPracticeMode.value || !track.practiceSegments.isNullOrBlank()) {
                    coroutineScope.launch(Dispatchers.IO) {
                        reanalyzePracticeSegmentsInternal(track, ctx, repo, silent = true)
                    }
                }
            }
        }
    }

    fun setSilenceSettings(sensitivity: String, minDurationMs: Long, paddingMs: Long) {
        val changed = (silenceSensitivity != sensitivity) || (silenceMinDurationMs != minDurationMs) || (silencePaddingMs != paddingMs)
        silenceSensitivity = sensitivity
        silenceMinDurationMs = minDurationMs
        silencePaddingMs = paddingMs

        if (changed && segmentSource == "SILENCE") {
            val track = currentTrackValue
            val repo = repository
            val ctx = appContext
            if (track != null && repo != null && ctx != null) {
                if (_isPracticeMode.value || !track.practiceSegments.isNullOrBlank()) {
                    coroutineScope.launch(Dispatchers.IO) {
                        reanalyzePracticeSegmentsInternal(track, ctx, repo, silent = true)
                    }
                }
            }
        }
    }

    fun getCompletionThreshold() = completionThreshold
    fun getSkipTimeSeconds() = skipTimeSeconds

    // Playback Focus listener
    private val focusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                pause()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                mediaPlayer?.setVolume(0.2f, 0.2f)
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                mediaPlayer?.setVolume(1.0f, 1.0f)
            }
        }
    }

    private fun requestAudioFocus(): Boolean {
        if (audioManager == null) return true
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val playbackAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(playbackAttributes)
                .setAcceptsDelayedFocusGain(true)
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

    private fun abandonAudioFocus() {
        if (audioManager == null) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager?.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager?.abandonAudioFocus(focusChangeListener)
        }
    }

    fun playTrack(track: AudioTrack, playlistTracks: List<AudioTrack> = emptyList()) {
        if (playlistTracks.isNotEmpty()) {
            currentQueue = playlistTracks
        } else if (currentQueue.isEmpty() || !currentQueue.any { it.id == track.id }) {
            currentQueue = listOf(track)
        }

        try {
            NoteAudioPlayer.stop()
            if (!requestAudioFocus()) {
                Log.e(TAG, "Audio focus denied")
                return
            }

            // Stop current playback
            mediaPlayer?.release()
            mediaPlayer = null

            val initialTrack = if (track.getProgressPercent() >= 100) {
                val reset = track.copy(listenedSegments = "", lastPosition = 0L)
                coroutineScope.launch(Dispatchers.IO) {
                    repository?.updateTrack(reset)
                }
                reset
            } else {
                track
            }

            currentTrackValue = initialTrack
            _currentTrack.value = initialTrack
            _duration.value = initialTrack.duration
            isThresholdTriggeredForCurrentSession = initialTrack.getProgressPercent() >= completionThreshold
            completedTaskIdsForCurrentSession.clear()
            
            // Reset active listening stopwatch for this track session
            sessionActualListeningMs = 0L
            lastActivePlayTimestamp = System.currentTimeMillis()
            
            // Pre-populate completedTaskIdsForCurrentSession with tasks for which the threshold has already been met
            repository?.let { repo ->
                coroutineScope.launch(Dispatchers.IO) {
                    try {
                        val dbTrack = repo.getTrackById(track.id)
                        if (dbTrack != null && dbTrack != track) {
                            withContext(Dispatchers.Main) {
                                if (currentTrackValue?.id == dbTrack.id) {
                                    currentTrackValue = dbTrack
                                    _currentTrack.value = dbTrack
                                }
                            }
                        }
                        val activeTasksList = repo.getAllTasksDirect()
                        val currentProgress = (dbTrack ?: track).getProgressPercent()
                        for (task in activeTasksList) {
                            val effectiveThreshold = task.customThreshold ?: completionThreshold
                            if (currentProgress >= effectiveThreshold) {
                                completedTaskIdsForCurrentSession.add(task.id)
                                Log.d(TAG, "Task ${task.id} threshold of $effectiveThreshold% already met with progress $currentProgress%. Adding to session ignore list.")
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error pre-populating completed task IDs: ${e.message}")
                    }
                }
            }

            saveCurrentTrackToPreferences(track.id)

            val file = File(track.filePath)
            if (!file.exists()) {
                // Mark track as missing
                coroutineScope.launch(Dispatchers.IO) {
                    val updated = track.copy(isMissing = true)
                    repository?.updateTrack(updated)
                    // If missing, try to locate inside the demo zone
                    withContext(Dispatchers.Main) {
                        _currentTrack.value = updated
                    }
                }
                Log.e(TAG, "Audio file does not exist: ${track.filePath}")
                return
            }

            val isVideo = SubtitleParser.isVideoFile(track.filePath)
            _isVideoTrack.value = isVideo
            _videoDimensions.value = null

            if (isVideo) {
                try {
                    val retriever = android.media.MediaMetadataRetriever()
                    retriever.setDataSource(track.filePath)
                    val wStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                    val hStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                    val rotStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                    val rotation = rotStr?.toIntOrNull() ?: 0
                    var width = wStr?.toIntOrNull() ?: 0
                    var height = hStr?.toIntOrNull() ?: 0
                    if (rotation == 90 || rotation == 270) {
                        val temp = width
                        width = height
                        height = temp
                    }
                    if (width > 0 && height > 0) {
                        _videoDimensions.value = Pair(width, height)
                    }
                    retriever.release()
                } catch (e: Exception) {
                    Log.w(TAG, "Video metadata extraction failed: ${e.message}")
                }
            }

            mediaPlayer = MediaPlayer().apply {
                if (activeSurface != null && activeSurface!!.isValid) {
                    try {
                        setSurface(activeSurface)
                    } catch (e: Exception) {
                        Log.e(TAG, "setSurface error: ${e.message}")
                    }
                } else if (activeSurfaceHolder != null) {
                    try {
                        setDisplay(activeSurfaceHolder)
                    } catch (e: Exception) {
                        Log.e(TAG, "setDisplay error: ${e.message}")
                    }
                }
                setDataSource(track.filePath)
                setOnVideoSizeChangedListener { _, width, height ->
                    if (width > 0 && height > 0) {
                        _videoDimensions.value = Pair(width, height)
                    }
                }
                prepare()

                val realDuration = try { duration.toLong() } catch (e: Exception) { 0L }
                if (track.isVirtualScene) {
                    val virtualDuration = if (track.endOffsetMs != null && track.endOffsetMs > track.startOffsetMs) {
                        track.endOffsetMs - track.startOffsetMs
                    } else if (track.duration > 0) {
                        track.duration
                    } else {
                        (realDuration - track.startOffsetMs).coerceAtLeast(1000L)
                    }
                    _duration.value = virtualDuration
                    val initialPhysicalSeek = (track.startOffsetMs + track.lastPosition).coerceIn(track.startOffsetMs, (track.endOffsetMs ?: realDuration).coerceAtLeast(track.startOffsetMs))
                    seekTo(initialPhysicalSeek.toInt())
                    _currentPosition.value = track.lastPosition.coerceIn(0L, virtualDuration)
                } else {
                    if (realDuration > 0) {
                        _duration.value = realDuration
                    }
                    // Seek to the last saved position immediately (never start from zero if we can resume)
                    seekTo(track.lastPosition.toInt())
                    _currentPosition.value = track.lastPosition
                }
                
                // Adjust speed based on speed settings
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    try {
                        playbackParams = playbackParams.setSpeed(_playbackSpeed.value)
                    } catch (e: Exception) {
                        Log.e(TAG, "Setting speed failed: ${e.message}")
                    }
                }
                
                setOnCompletionListener {
                    // Normal play next or cycle completion (which handles sleep at end of file elegantly)
                    handlePhysicalEndOfTrack()
                }
            }

            _isPlaying.value = true
            loadSubtitlesForTrack(track)
            practicePauseJob?.cancel()
            _isPracticePausing.value = false
            _practicePauseRemainingSeconds.value = 0f
            val practiceSegs = track.getPracticeSegmentsList()
            _currentPracticeSegments.value = practiceSegs
            if (_isPracticeMode.value) {
                val ctx = appContext
                val repo = repository
                if (ctx != null && repo != null && needsReanalysis(track, ctx)) {
                    togglePracticeMode(ctx, repo, forceReanalyze = true)
                } else {
                    resetPracticeSegmentTracking(track.lastPosition)
                }
            }
            mediaPlayer?.start()
            val effectiveDuration = if (track.isVirtualScene) {
                _duration.value
            } else {
                if (_duration.value > 0) _duration.value else track.duration
            }
            val activeTrack = track.copy(duration = effectiveDuration)
            currentTrackValue = activeTrack
            _currentTrack.value = activeTrack
            lastTrackedPositionMs = if (track.isVirtualScene) (track.startOffsetMs + track.lastPosition) else track.lastPosition

            if (track.duration != effectiveDuration && effectiveDuration > 0) {
                coroutineScope.launch(Dispatchers.IO) {
                    repository?.updateTrack(activeTrack)
                }
            }

            startProgressTracking()
            updateMediaSessionMetadata(activeTrack)
            updateMediaSessionPlaybackState()
            showNotification()
            startPlaybackService()

        } catch (e: Exception) {
            Log.e(TAG, "Error playing audio file: ${e.message}")
            e.printStackTrace()
        }
    }

    fun removeTracksFromQueue(indices: List<Int>) {
        val newList = currentQueue.filterIndexed { index, _ -> index !in indices }
        currentQueue = newList
    }

    fun clearQueue() {
        val current = currentTrackValue
        currentQueue = if (current != null) listOf(current) else emptyList()
    }

    fun playNextTrack() {
        val current = currentTrackValue ?: return
        val currentIndex = currentQueue.indexOfFirst { it.id == current.id }
        if (currentIndex != -1 && currentIndex < currentQueue.size - 1) {
            playTrack(currentQueue[currentIndex + 1], currentQueue)
        }
    }

    fun playPreviousTrack() {
        val current = currentTrackValue ?: return
        val currentIndex = currentQueue.indexOfFirst { it.id == current.id }
        if (currentIndex > 0) {
            playTrack(currentQueue[currentIndex - 1], currentQueue)
        }
    }

    fun addTrackToQueueNext(track: AudioTrack) {
        val current = currentTrackValue
        if (current == null) {
            playTrack(track)
            return
        }
        val withoutTarget = currentQueue.filter { it.id != track.id }
        val currentIndex = withoutTarget.indexOfFirst { it.id == current.id }
        val newList = if (currentIndex != -1) {
            val left = withoutTarget.subList(0, currentIndex + 1)
            val right = withoutTarget.subList(currentIndex + 1, withoutTarget.size)
            left + track + right
        } else {
            withoutTarget + track
        }
        currentQueue = newList
    }

    fun addTracksToQueueNext(tracks: List<AudioTrack>) {
        if (tracks.isEmpty()) return
        val current = currentTrackValue
        if (current == null) {
            playTrack(tracks.first(), tracks)
            return
        }
        val targetIds = tracks.map { it.id }.toSet()
        val withoutTarget = currentQueue.filter { it.id !in targetIds }
        val currentIndex = withoutTarget.indexOfFirst { it.id == current.id }
        val newList = if (currentIndex != -1) {
            val left = withoutTarget.subList(0, currentIndex + 1)
            val right = withoutTarget.subList(currentIndex + 1, withoutTarget.size)
            left + tracks + right
        } else {
            withoutTarget + tracks
        }
        currentQueue = newList
    }

    fun resume() {
        if (mediaPlayer == null) {
            currentTrackValue?.let { playTrack(it) }
            return
        }
        if (!requestAudioFocus()) return
        NoteAudioPlayer.stop()
        lastActivePlayTimestamp = System.currentTimeMillis()
        try {
            mediaPlayer?.let { mp ->
                try {
                    lastTrackedPositionMs = mp.currentPosition.toLong()
                } catch (e: Exception) {
                    lastTrackedPositionMs = null
                }
                mp.start()
            }
            _isPlaying.value = true
            startProgressTracking()
            updateMediaSessionPlaybackState()
            showNotification()
            startPlaybackService()
        } catch (e: Exception) {
            Log.e(TAG, "Resume failed, attempting to replay track cleanly: ${e.message}")
            currentTrackValue?.let { playTrack(it) }
        }
    }

    fun pause() {
        practicePauseJob?.cancel()
        _isPracticePausing.value = false
        _practicePauseRemainingSeconds.value = 0f
        accumulateActiveListeningTime()
        lastActivePlayTimestamp = 0L
        try {
            mediaPlayer?.let {
                if (try { it.isPlaying } catch (e: Exception) { false }) {
                    it.pause()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Pause error: ${e.message}")
        }
        lastTrackedPositionMs = null
        _isPlaying.value = false
        stopProgressTracking()
        abandonAudioFocus()
        updateMediaSessionPlaybackState()
        showNotification()
        
        // Persist progress position inside database instantly on pause
        saveCurrentPositionProgress()
    }

    fun stop() {
        practicePauseJob?.cancel()
        _isPracticePausing.value = false
        _practicePauseRemainingSeconds.value = 0f
        accumulateActiveListeningTime()
        lastActivePlayTimestamp = 0L
        sessionActualListeningMs = 0L
        lastTrackedPositionMs = null
        try {
            mediaPlayer?.let {
                if (try { it.isPlaying } catch (e: Exception) { false }) {
                    it.stop()
                }
                it.reset()
                it.release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Stop/release error: ${e.message}")
        }
        mediaPlayer = null
        _isPlaying.value = false
        stopProgressTracking()
        abandonAudioFocus()
        currentTrackValue = null
        _currentTrack.value = null
        saveCurrentTrackToPreferences(null)
        _currentPosition.value = 0L
        _duration.value = 0L
        cancelSleepTimer()
        updateMediaSessionMetadata(null)
        updateMediaSessionPlaybackState()
        cancelNotification()
        stopPlaybackService()
    }

    private fun startPlaybackService() {
        appContext?.let { ctx ->
            try {
                val intent = Intent(ctx, HearmarkPlaybackService::class.java)
                ctx.startService(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start HearmarkPlaybackService: ${e.message}")
            }
        }
    }

    private fun stopPlaybackService() {
        appContext?.let { ctx ->
            try {
                ctx.stopService(Intent(ctx, HearmarkPlaybackService::class.java))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to stop HearmarkPlaybackService: ${e.message}")
            }
        }
    }

    private fun performSeek(mp: MediaPlayer, targetMs: Long) {
        val safeTarget = targetMs.coerceAtLeast(0L)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            mp.seekTo(safeTarget, MediaPlayer.SEEK_CLOSEST)
        } else {
            mp.seekTo(safeTarget.toInt())
        }
    }

    fun seekTo(position: Long, isPhysicalTimestamp: Boolean? = null) {
        val track = currentTrackValue
        try {
            mediaPlayer?.let { mp ->
                if (track != null && track.isVirtualScene) {
                    val maxEnd = track.endOffsetMs ?: (track.startOffsetMs + track.duration)
                    val physicalTarget = when (isPhysicalTimestamp) {
                        true -> position.coerceIn(track.startOffsetMs, maxEnd)
                        false -> (track.startOffsetMs + position.coerceIn(0L, track.duration)).coerceIn(track.startOffsetMs, maxEnd)
                        null -> {
                            if (position > track.duration && position in track.startOffsetMs..(maxEnd + 1000L)) {
                                position.coerceIn(track.startOffsetMs, maxEnd)
                            } else if (position in track.startOffsetMs..(maxEnd + 1000L) && track.startOffsetMs > track.duration) {
                                position.coerceIn(track.startOffsetMs, maxEnd)
                            } else {
                                (track.startOffsetMs + position.coerceIn(0L, track.duration)).coerceIn(track.startOffsetMs, maxEnd)
                            }
                        }
                    }
                    val virtualPos = (physicalTarget - track.startOffsetMs).coerceIn(0L, track.duration)
                    performSeek(mp, physicalTarget)
                    _currentPosition.value = virtualPos
                    lastTrackedPositionMs = physicalTarget
                    updateActiveSubtitleCue(physicalTarget)
                } else {
                    val maxDur = track?.duration ?: (try { mp.duration.toLong() } catch (e: Exception) { Long.MAX_VALUE })
                    val validTarget = position.coerceIn(0L, if (maxDur > 0) maxDur else Long.MAX_VALUE)
                    performSeek(mp, validTarget)
                    _currentPosition.value = validTarget
                    lastTrackedPositionMs = validTarget
                    updateActiveSubtitleCue(validTarget)
                }
                onSeekInPracticeMode(_currentPosition.value)
                updateMediaSessionPlaybackState()
                saveCurrentPositionProgress()
            }
        } catch (e: Exception) {
            Log.e(TAG, "seekTo error: ${e.message}")
        }
    }

    fun skipForward() {
        val track = currentTrackValue
        try {
            mediaPlayer?.let { mp ->
                if (track != null && track.isVirtualScene) {
                    val currentVirtual = _currentPosition.value
                    val targetVirtual = (currentVirtual + (skipTimeSeconds * 1000)).coerceAtMost(track.duration)
                    seekTo(targetVirtual, isPhysicalTimestamp = false)
                } else {
                    val cur = try { mp.currentPosition } catch (e: Exception) { _currentPosition.value.toInt() }
                    val dur = try { if (mp.duration > 0) mp.duration else (track?.duration?.toInt() ?: 0) } catch (e: Exception) { (track?.duration?.toInt() ?: 0) }
                    val target = (cur + (skipTimeSeconds * 1000)).coerceAtMost(if (dur > 0) dur else Int.MAX_VALUE)
                    seekTo(target.toLong(), isPhysicalTimestamp = false)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "skipForward error: ${e.message}")
        }
    }

    fun skipBackward() {
        val track = currentTrackValue
        try {
            mediaPlayer?.let { mp ->
                if (track != null && track.isVirtualScene) {
                    val currentVirtual = _currentPosition.value
                    val targetVirtual = (currentVirtual - (skipTimeSeconds * 1000)).coerceAtLeast(0L)
                    seekTo(targetVirtual, isPhysicalTimestamp = false)
                } else {
                    val cur = try { mp.currentPosition } catch (e: Exception) { _currentPosition.value.toInt() }
                    val target = (cur - (skipTimeSeconds * 1000)).coerceAtLeast(0)
                    seekTo(target.toLong(), isPhysicalTimestamp = false)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "skipBackward error: ${e.message}")
        }
    }

    fun setSpeed(speed: Float) {
        _playbackSpeed.value = speed
        appContext?.let { ctx ->
            try {
                val sharedPref = ctx.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
                sharedPref.edit().putFloat("playback_speed", speed).apply()
            } catch (e: Exception) {
                Log.e(TAG, "Error saving playback speed: ${e.message}")
            }
        }
        mediaPlayer?.let {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                try {
                    val wasPlaying = it.isPlaying
                    it.playbackParams = it.playbackParams.setSpeed(speed)
                    if (!wasPlaying) {
                        it.pause() // PlaybackParams automatically starts player sometimes on Marshmallow+
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed setSpeed method: ${e.message}")
                }
            }
        }
        updateMediaSessionPlaybackState()
    }

    fun setAutoPlay(enabled: Boolean) {
        isAutoPlayEnabled.value = enabled
        appContext?.let { ctx ->
            try {
                val sharedPref = ctx.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
                sharedPref.edit().putBoolean("autoplay_enabled", enabled).apply()
            } catch (e: Exception) {
                Log.e(TAG, "Error saving autoplay preference: ${e.message}")
            }
        }
    }

    fun toggleAutoPlay(): Boolean {
        val next = !isAutoPlayEnabled.value
        setAutoPlay(next)
        return next
    }

    private val trackUpdateMutex = Mutex()
    private var lastTrackedPositionMs: Long? = null

    private suspend fun updateTrackState(transform: (AudioTrack) -> AudioTrack) {
        trackUpdateMutex.withLock {
            val track = currentTrackValue ?: return@withLock
            val updated = transform(track)
            if (updated != track) {
                repository?.updateTrack(updated)
                currentTrackValue = updated
                withContext(Dispatchers.Main) {
                    _currentTrack.value = updated
                    val index = currentQueue.indexOfFirst { it.id == updated.id }
                    if (index != -1) {
                        val updatedQueue = currentQueue.toMutableList()
                        updatedQueue[index] = updated
                        currentQueue = updatedQueue
                    }
                }
            }
        }
    }

    // Dynamic continuous segments tracker maintaining unbroken, high-fidelity updates
    private fun startProgressTracking() {
        progressTrackingJob?.cancel()
        try {
            mediaPlayer?.let { mp ->
                if (try { mp.isPlaying } catch (e: Exception) { false }) {
                    lastTrackedPositionMs = try { mp.currentPosition.toLong() } catch (e: Exception) { null }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "startProgressTracking initial pos error: ${e.message}")
        }
        progressTrackingJob = coroutineScope.launch(Dispatchers.IO) {
            while (isActive) {
                try {
                    val mp = mediaPlayer
                    val isPlaying = try { mp?.isPlaying == true } catch (e: Exception) { false }
                    if (mp != null && isPlaying) {
                        accumulateActiveListeningTime()
                        val physicalPos = try { mp.currentPosition.toLong() } catch (e: Exception) { -1L }
                        if (physicalPos < 0L) {
                            delay(200)
                            continue
                        }
                        val track = currentTrackValue

                        if (track != null && track.isVirtualScene) {
                            val virtualPos = (physicalPos - track.startOffsetMs).coerceIn(0L, track.duration)
                            withContext(Dispatchers.Main) {
                                _currentPosition.value = virtualPos
                                _duration.value = track.duration
                            }
                            updateActiveSubtitleCue(physicalPos)
                            if (_isPracticeMode.value && !_isPracticePausing.value && isPlaying) {
                                checkPracticeSegmentBoundary(virtualPos)
                            }
                            val endBoundary = track.endOffsetMs ?: (track.startOffsetMs + track.duration)
                            if (physicalPos >= endBoundary - 120L) {
                                withContext(Dispatchers.Main) {
                                    try {
                                        mediaPlayer?.pause()
                                    } catch (e: Exception) {}
                                    handlePhysicalEndOfTrack()
                                }
                                return@launch
                            }

                            // Process segment logic for virtual scene
                            val effectiveDuration = track.duration
                            if (effectiveDuration > 0) {
                                val numSegments = track.getAdaptiveNumSegments()
                                val prevPhysical = lastTrackedPositionMs ?: physicalPos
                                val prevVirtual = (prevPhysical - track.startOffsetMs).coerceIn(0L, effectiveDuration)

                                val currentSeg = ((virtualPos * numSegments) / effectiveDuration).toInt().coerceIn(0, numSegments - 1)
                                val bitSet = getOrInitActiveBitSet(track.id, track.listenedSegments)
                                var hadNewSegments = false

                                if (virtualPos >= prevVirtual) {
                                    val fromSeg = ((prevVirtual * numSegments) / effectiveDuration).toInt().coerceIn(0, numSegments - 1)
                                    for (s in fromSeg..currentSeg) {
                                        if (!bitSet.get(s)) {
                                            bitSet.set(s)
                                            hadNewSegments = true
                                        }
                                    }
                                } else {
                                    if (!bitSet.get(currentSeg)) {
                                        bitSet.set(currentSeg)
                                        hadNewSegments = true
                                    }
                                }
                                lastTrackedPositionMs = physicalPos

                                if (hadNewSegments || Math.abs(track.lastPosition - virtualPos) >= 1000L) {
                                    val serialized = if (hadNewSegments) serializeBitSet(bitSet) else track.listenedSegments
                                    updateTrackState { t ->
                                        t.copy(
                                            lastPosition = virtualPos,
                                            listenedSegments = serialized
                                        )
                                    }
                                }

                                val currentTrack = currentTrackValue
                                if (currentTrack != null) {
                                    val progressPercent = currentTrack.getProgressPercent(numSegments)
                                    if (progressPercent >= completionThreshold) {
                                        handleThresholdReached()
                                    }
                                    checkAndTriggerTaskSpecificProgress(currentTrack, progressPercent)
                                }
                            }
                        } else {
                            withContext(Dispatchers.Main) {
                                _currentPosition.value = physicalPos
                            }
                            updateActiveSubtitleCue(physicalPos)
                            if (_isPracticeMode.value && !_isPracticePausing.value && isPlaying) {
                                checkPracticeSegmentBoundary(physicalPos)
                            }
                            
                            // Process segment logic
                            val effectiveDuration = if (mp.duration > 0) mp.duration.toLong() else track?.duration ?: 0L
                            if (track != null && effectiveDuration > 0) {
                                val numSegments = track.getAdaptiveNumSegments()
                                val prevPos = lastTrackedPositionMs ?: physicalPos
                                
                                val currentSeg = ((physicalPos * numSegments) / effectiveDuration).toInt().coerceIn(0, numSegments - 1)
                                val bitSet = getOrInitActiveBitSet(track.id, track.listenedSegments)
                                var hadNewSegments = false
                                
                                // Since media is playing continuously, every segment elapsed from prevPos to pos is recorded
                                if (physicalPos >= prevPos) {
                                    val fromSeg = ((prevPos * numSegments) / effectiveDuration).toInt().coerceIn(0, numSegments - 1)
                                    for (s in fromSeg..currentSeg) {
                                        if (!bitSet.get(s)) {
                                            bitSet.set(s)
                                            hadNewSegments = true
                                        }
                                    }
                                } else {
                                    if (!bitSet.get(currentSeg)) {
                                        bitSet.set(currentSeg)
                                        hadNewSegments = true
                                    }
                                }
                                lastTrackedPositionMs = physicalPos
                                
                                // Atomically update track state with new segments and lastPosition directly on IO
                                if (hadNewSegments || Math.abs(track.lastPosition - physicalPos) >= 1000L) {
                                    val serialized = if (hadNewSegments) serializeBitSet(bitSet) else track.listenedSegments
                                    updateTrackState { t ->
                                        t.copy(
                                            lastPosition = physicalPos,
                                            listenedSegments = serialized
                                        )
                                    }
                                }
                                
                                val currentTrack = currentTrackValue
                                if (currentTrack != null) {
                                    val progressPercent = currentTrack.getProgressPercent(numSegments)
                                    if (progressPercent >= completionThreshold) {
                                        handleThresholdReached()
                                    }
                                    checkAndTriggerTaskSpecificProgress(currentTrack, progressPercent)
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Progress tracking tick error: ${e.message}")
                }
                delay(200)
            }
        }
    }

    private fun stopProgressTracking() {
        progressTrackingJob?.cancel()
        progressTrackingJob = null
    }

    fun saveCurrentPositionProgress() {
        val pos = _currentPosition.value
        coroutineScope.launch(Dispatchers.IO) {
            updateTrackState { t -> t.copy(lastPosition = pos) }
        }
    }

    private fun handleThresholdReached() {
        if (isThresholdTriggeredForCurrentSession) return
        isThresholdTriggeredForCurrentSession = true

        coroutineScope.launch(Dispatchers.IO) {
            accumulateActiveListeningTime()

            // 1. Increment playCount safely
            updateTrackState { t -> t.copy(playCount = t.playCount + 1) }
            
            val track = currentTrackValue ?: return@launch

            // Use exact actual wall-clock listening duration, falling back to full duration only if stopwatch was uninitialized
            val recordedActualMs = if (sessionActualListeningMs > 0L) {
                sessionActualListeningMs
            } else {
                track.duration
            }
            sessionActualListeningMs = 0L // Reset for next iteration/repeat

            // 2. Save into History with active tasks snapshot at this exact moment
            val activeTasksAtThatTime = repository?.getActiveTasksForTrackDirect(track) ?: emptyList()
            val activeTasksJson = if (activeTasksAtThatTime.isNotEmpty()) {
                val arr = org.json.JSONArray()
                activeTasksAtThatTime.forEach { t ->
                    val obj = org.json.JSONObject()
                    obj.put("id", t.id)
                    obj.put("title", t.getDisplayTitle())
                    arr.put(obj)
                }
                arr.toString()
            } else {
                ""
            }

            val history = PlaybackHistory(
                trackId = track.id,
                trackName = track.fileName,
                completedAt = System.currentTimeMillis(),
                durationMs = track.duration,
                playbackSpeed = playbackSpeed.value,
                actualListenedMs = recordedActualMs,
                activeTasks = activeTasksJson
            )
            repository?.insertPlaybackHistory(history)

            // 3. Update all active tasks containing this file automatically
            updateAssociatedTasks(track.id)
        }
    }

    private fun handlePhysicalEndOfTrack() {
        val track = currentTrackValue ?: return
        coroutineScope.launch(Dispatchers.IO) {
            // First check if there is a sleep at end of file timer set
            val isSleepAtEnd = (sleepTimerOption == -1)
            if (isSleepAtEnd) {
                sleepTimerOption = 0
                _sleepTimeRemaining.value = 0
            }

            sessionActualListeningMs = 0L
            activeTrackSegmentTrackId = null
            activeTrackSegmentsBitSet.clear()

            val isFullyListened = (currentTrackValue?.getProgressPercent() ?: 0) >= 100
            if (isFullyListened) {
                // Reset segments and position back to 0% after reaching 100% maximum listening progress
                updateTrackState { t ->
                    t.copy(
                        lastPosition = 0L,
                        listenedSegments = ""
                    )
                }
                isThresholdTriggeredForCurrentSession = false
                completedTaskIdsForCurrentSession.clear()
            } else {
                // Keep partial progress intact, only rewind playhead to beginning
                updateTrackState { t ->
                    t.copy(lastPosition = 0L)
                }
            }

            // Look for next track in the queue to auto play
            var nextTrackPlayed = false
            if (isAutoPlayEnabled.value && !isSleepAtEnd) {
                val currentIndex = currentQueue.indexOfFirst { it.id == track.id }
                if (currentIndex != -1 && currentIndex < currentQueue.size - 1) {
                    val nextTrackForPlayback = currentQueue[currentIndex + 1]
                    nextTrackPlayed = true
                    withContext(Dispatchers.Main) {
                        playTrack(nextTrackForPlayback, currentQueue)
                    }
                }
            }

            if (!nextTrackPlayed) {
                withContext(Dispatchers.Main) {
                    // STOP and PAUSE playback immediately so track does NOT auto-replay
                    try {
                        mediaPlayer?.pause()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error pausing player on end of track: ${e.message}")
                    }
                    _isPlaying.value = false
                    stopProgressTracking()

                    // Reset seek position cleanly to start so future play restarts from beginning
                    val seekReset = if (track.isVirtualScene) track.startOffsetMs else 0L
                    try {
                        mediaPlayer?.let { performSeek(it, seekReset) }
                    } catch (e: Exception) {}
                    lastTrackedPositionMs = if (track.isVirtualScene) track.startOffsetMs else 0L
                    _currentPosition.value = 0L
                    updateActiveSubtitleCue(if (track.isVirtualScene) track.startOffsetMs else 0L)

                    abandonAudioFocus()
                    updateMediaSessionPlaybackState()
                    showNotification()
                }
            }
        }
    }

    private fun checkAndTriggerTaskSpecificProgress(track: AudioTrack, progressPercent: Int) {
        val repo = repository ?: return
        coroutineScope.launch(Dispatchers.IO) {
            val activeTasksList = repo.getAllTasksDirect()
            val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

            for (task in activeTasksList) {
                if (task.isCompleted) continue
                val effectiveThreshold = task.customThreshold ?: completionThreshold
                if (progressPercent >= effectiveThreshold) {
                    if (completedTaskIdsForCurrentSession.contains(task.id)) continue
                    
                    val taskProgresses = repo.getProgressForTask(task.id)
                    val matchingProgress = taskProgresses.find { it.trackId == track.id }
                    if (matchingProgress != null) {
                        completedTaskIdsForCurrentSession.add(task.id)
                        repo.incrementDailyPlayCount(task.id, todayStr)
                        
                        var updatedProgress = matchingProgress
                        if (task.targetType == "PLAY_COUNT") {
                            val newCount = matchingProgress.completedPlayCount + 1
                            val isTrackDone = newCount >= task.targetValue
                            updatedProgress = matchingProgress.copy(
                                completedPlayCount = newCount,
                                isTrackCompleted = isTrackDone
                            )
                        } else if (task.targetType == "DAYS_COUNT") {
                            val newCount = matchingProgress.completedPlayCount + 1
                            val days = matchingProgress.getDaysList().toMutableSet()
                            days.add(todayStr)
                            val newDaysStr = days.joinToString(",")
                            val isTrackDone = days.size >= task.targetValue
                            updatedProgress = matchingProgress.copy(
                                completedPlayCount = newCount,
                                completedDays = newDaysStr,
                                isTrackCompleted = isTrackDone
                            )
                        }

                        repo.insertTaskProgress(updatedProgress)

                        val refreshedProgresses = repo.getProgressForTask(task.id)
                        val allTracksCompleted = refreshedProgresses.isNotEmpty() && refreshedProgresses.all { it.isTrackCompleted }

                        if (allTracksCompleted && !task.isCompleted) {
                            repo.updateTask(task.copy(isCompleted = true, status = "COMPLETED"))
                            appContext?.let { com.example.receiver.AlarmReceiver.cancelAlarm(it, task.id) }
                            sendTaskCompletionNotification(task)
                        }
                    }
                }
            }
        }
    }

    private suspend fun updateAssociatedTasks(trackId: Long) {
        val repo = repository ?: return
        val track = currentTrackValue
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

        val activeTasksList = repo.getAllTasksDirect()

        for (task in activeTasksList) {
            if (task.isCompleted) continue
            if (completedTaskIdsForCurrentSession.contains(task.id)) continue

            if (task.customThreshold != null) {
                val currentProgress = track?.getProgressPercent() ?: 0
                if (currentProgress < task.customThreshold) {
                    continue
                }
            }

            val taskProgresses = repo.getProgressForTask(task.id)
            val matchingProgress = taskProgresses.find { it.trackId == trackId }
            
            if (matchingProgress != null) {
                completedTaskIdsForCurrentSession.add(task.id)
                repo.incrementDailyPlayCount(task.id, todayStr)
                var updatedProgress = matchingProgress
                if (task.targetType == "PLAY_COUNT") {
                    val newCount = matchingProgress.completedPlayCount + 1
                    val isTrackDone = newCount >= task.targetValue
                    updatedProgress = matchingProgress.copy(
                        completedPlayCount = newCount,
                        isTrackCompleted = isTrackDone
                    )
                } else if (task.targetType == "DAYS_COUNT") {
                    val days = matchingProgress.getDaysList().toMutableSet()
                    days.add(todayStr)
                    val newDaysStr = days.joinToString(",")
                    val isTrackDone = days.size >= task.targetValue
                    updatedProgress = matchingProgress.copy(
                        completedDays = newDaysStr,
                        isTrackCompleted = isTrackDone
                    )
                }

                repo.insertTaskProgress(updatedProgress)

                val refreshedProgresses = repo.getProgressForTask(task.id)
                val allTracksCompleted = refreshedProgresses.isNotEmpty() && refreshedProgresses.all { it.isTrackCompleted }

                if (allTracksCompleted && !task.isCompleted) {
                    repo.updateTask(task.copy(isCompleted = true, status = "COMPLETED"))
                    appContext?.let { com.example.receiver.AlarmReceiver.cancelAlarm(it, task.id) }
                    sendTaskCompletionNotification(task)
                }
            }
        }
    }

    // Sleep Timer management
    fun startSleepTimer(minutes: Int) {
        cancelSleepTimer()
        sleepTimerOption = minutes
        if (minutes <= 0) {
            _sleepTimeRemaining.value = 0
            return
        }

        _sleepTimeRemaining.value = minutes * 60
        sleepTimerJob = coroutineScope.launch {
            while (_sleepTimeRemaining.value > 0) {
                delay(1000)
                _sleepTimeRemaining.value -= 1
            }
            // Timer finished, pause playback
            pause()
            sleepTimerOption = 0
        }
    }

    fun setSleepAtEnd() {
        cancelSleepTimer()
        sleepTimerOption = -1 // end of file code
        _sleepTimeRemaining.value = -1
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        sleepTimerOption = 0
        _sleepTimeRemaining.value = 0
    }

    // Notifications and Services
    private const val CHANNEL_ID = "smart_audio_player_channel"
    private const val NOTIFICATION_ID = 404

    fun isDarkThemeActive(): Boolean {
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

    fun showNotification() {
        val context = appContext ?: return
        val track = currentTrackValue ?: return
        
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Hearmark Playback Control",
                NotificationManager.IMPORTANCE_LOW
            )
            notificationManager.createNotificationChannel(channel)
        }

        // Intents for controls
        val intentOpen = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntentOpen = PendingIntent.getActivity(
            context, 0, intentOpen,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val playIcon = if (_isPlaying.value) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        val playActionTitle = if (_isPlaying.value) Loc.getText("pause") else Loc.getText("play")

        val playIntent = Intent("com.example.ACTION_PLAY_PAUSE").apply {
            `package` = context.packageName
        }
        val playPendingIntent = PendingIntent.getBroadcast(
            context, 10, playIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val skipForwardIntent = Intent("com.example.ACTION_SKIP_FORWARD").apply {
            `package` = context.packageName
        }
        val skipForwardPendingIntent = PendingIntent.getBroadcast(
            context, 20, skipForwardIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val skipBackwardIntent = Intent("com.example.ACTION_SKIP_BACKWARD").apply {
            `package` = context.packageName
        }
        val skipBackwardPendingIntent = PendingIntent.getBroadcast(
            context, 30, skipBackwardIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val isDark = isDarkThemeActive()
        val largeIcon = getLargeIconBitmap(context, isDark)
        val cleanTitle = track.getDisplayTitle()
        val statusText = if (_isPlaying.value) {
            "Hearmark • ${Loc.getText("listening")}"
        } else {
            "Hearmark • ${Loc.getText("paused")}"
        }

        val mediaStyle = androidx.media.app.NotificationCompat.MediaStyle()
            .setShowActionsInCompactView(0, 1, 2)
            .setMediaSession(mediaSession?.sessionToken)

        // Light background color for light theme, dark background color for dark theme
        // Maintaining clarity, high contrast, and harmony with the respective theme palette
        val notificationBgColor = if (isDark) {
            0xFF1E1A22.toInt() // Dark charcoal/purple theme background
        } else {
            0xFFF4EEF8.toInt() // Clean light lavender background
        }

        val durationMs = if (_duration.value > 0) _duration.value else track.duration
        val currentPosMs = _currentPosition.value

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
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

        notificationManager.notify(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Log.w(TAG, "showNotification failed: ${e.message}")
        }
    }

    fun updateNotification() {
        if (_isPlaying.value || currentTrackValue != null) {
            showNotification()
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

            val drawable = androidx.core.content.ContextCompat.getDrawable(context, com.example.R.drawable.ic_logo)?.mutate() ?: return bitmap
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

    private fun cancelNotification() {
        try {
            val notificationManager = appContext?.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            notificationManager?.cancel(NOTIFICATION_ID)
        } catch (e: Exception) {
            Log.e(TAG, "Notification cancel error: ${e.message}")
        }
    }

    private fun sendTaskCompletionNotification(task: Task) {
        // Disabled per user request: "I do not want to receive notifications for completed tasks."
    }

    private fun saveQueueToPreferences(queue: List<AudioTrack>) {
        val context = appContext ?: return
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val sharedPref = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
                val idsStr = queue.map { it.id }.joinToString(",")
                sharedPref.edit().putString("current_queue_ids", idsStr).apply()
            } catch (e: Exception) {
                Log.e(TAG, "Error saving queue to preferences: ${e.message}")
            }
        }
    }

    private fun saveCurrentTrackToPreferences(trackId: Long?) {
        val context = appContext ?: return
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val sharedPref = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
                sharedPref.edit().putLong("current_track_id", trackId ?: -1L).apply()
            } catch (e: Exception) {
                Log.e(TAG, "Error saving track to preferences: ${e.message}")
            }
        }
    }

    private fun restoreQueueAndTrack() {
        val context = appContext ?: return
        val repo = repository ?: return
        coroutineScope.launch(Dispatchers.IO) {
            try {
                if (_isPlaying.value || mediaPlayer != null || currentTrackValue != null || _currentTrack.value != null) {
                    Log.d(TAG, "Playback is already active or track is loaded; skipping restore.")
                    return@launch
                }
                val sharedPref = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
                val currentTrackId = sharedPref.getLong("current_track_id", -1L)
                val queueIdsStr = sharedPref.getString("current_queue_ids", "") ?: ""
                
                if (queueIdsStr.isNotEmpty()) {
                    val idList = queueIdsStr.split(",").mapNotNull { it.toLongOrNull() }
                    if (idList.isNotEmpty()) {
                        val fetchedTracks = mutableListOf<AudioTrack>()
                        for (id in idList) {
                            val track = repo.getTrackById(id)
                            if (track != null) {
                                fetchedTracks.add(track)
                            }
                        }
                        
                        withContext(Dispatchers.Main) {
                            if (!_isPlaying.value && mediaPlayer == null && currentTrackValue == null && _currentTrack.value == null) {
                                currentQueue = fetchedTracks
                            }
                        }
                    }
                }
                
                if (currentTrackId != -1L) {
                    val track = repo.getTrackById(currentTrackId)
                    if (track != null) {
                        // Let's make sure the track isn't missing
                        val file = File(track.filePath)
                        if (file.exists()) {
                            withContext(Dispatchers.Main) {
                                if (!_isPlaying.value && mediaPlayer == null && currentTrackValue == null && _currentTrack.value == null) {
                                    currentTrackValue = track
                                    _currentTrack.value = track
                                    _duration.value = track.duration
                                    _currentPosition.value = track.lastPosition
                                    loadSubtitlesForTrack(track)
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error restoring queue/track state: ${e.message}")
            }
        }
    }

    // --- SUBTITLE & LYRICS MANAGEMENT ---

    fun loadSubtitlesForTrack(track: AudioTrack) {
        coroutineScope.launch(Dispatchers.IO) {
            val rawCues = when {
                !track.subtitleContent.isNullOrBlank() -> {
                    SubtitleParser.parseContent(track.subtitleContent, track.subtitleOffsetMs)
                }
                !track.subtitlePath.isNullOrBlank() -> {
                    val file = File(track.subtitlePath)
                    SubtitleParser.parseFile(file, track.subtitleOffsetMs)
                }
                else -> {
                    val autoFile = SubtitleParser.findMatchingSubtitleFile(track.filePath)
                    if (autoFile != null) {
                        val updatedTrack = track.copy(subtitlePath = autoFile.absolutePath)
                        repository?.updateTrack(updatedTrack)
                        updateTrackState { updatedTrack }
                        SubtitleParser.parseFile(autoFile, track.subtitleOffsetMs)
                    } else {
                        emptyList()
                    }
                }
            }
            val cues = if (track.isVirtualScene) {
                val sceneStart = track.startOffsetMs
                val sceneEnd = track.endOffsetMs ?: (track.startOffsetMs + track.duration)
                rawCues.filter { cue ->
                    if (!cue.isTimed || cue.startMs < 0) true
                    else {
                        val cueEnd = if (cue.endMs > cue.startMs) cue.endMs else cue.startMs + 5000L
                        cueEnd >= sceneStart && cue.startMs <= sceneEnd
                    }
                }
            } else {
                rawCues
            }
            withContext(Dispatchers.Main) {
                _subtitlesCues.value = cues
                subtitleOffsetMs.value = track.subtitleOffsetMs
                val currentPhys = if (track.isVirtualScene) track.startOffsetMs + _currentPosition.value else _currentPosition.value
                updateActiveSubtitleCue(currentPhys)
            }
        }
    }

    fun updateActiveSubtitleCue(positionMs: Long) {
        if (!isSubtitlesEnabled.value) {
            if (_activeSubtitleCue.value != null) _activeSubtitleCue.value = null
            return
        }
        val cues = _subtitlesCues.value
        if (cues.isEmpty()) {
            if (_activeSubtitleCue.value != null) _activeSubtitleCue.value = null
            return
        }
        val track = currentTrackValue
        val effectivePos = if (track != null && track.isVirtualScene && positionMs < track.startOffsetMs) {
            track.startOffsetMs + positionMs
        } else {
            positionMs
        }
        val active = cues.firstOrNull { it.isTimed && it.startMs >= 0 && effectivePos >= it.startMs && effectivePos <= it.endMs }
        _activeSubtitleCue.value = active
    }

    fun getCurrentSubtitlesRawText(): String {
        val track = currentTrackValue ?: return ""
        if (!track.subtitleContent.isNullOrBlank()) {
            return SubtitleParser.formatForEditor(track.subtitleContent)
        }
        if (!track.subtitlePath.isNullOrBlank()) {
            try {
                val file = File(track.subtitlePath)
                if (file.exists() && file.canRead()) {
                    return SubtitleParser.formatForEditor(file.readText())
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        val cues = _subtitlesCues.value
        if (cues.isNotEmpty()) {
            val hasTimings = cues.any { it.isTimed && it.startMs >= 0 }
            return if (hasTimings) {
                val isSrtLike = cues.any { it.endMs > it.startMs + 1000L }
                if (isSrtLike) {
                    cues.joinToString("\n\n") { cue ->
                        val start = formatSubtitleTimestamp(cue.startMs)
                        val end = formatSubtitleTimestamp(cue.endMs)
                        "${cue.id}\n$start --> $end\n${cue.text}"
                    }
                } else {
                    cues.joinToString("\n\n") { cue ->
                        "${SubtitleParser.formatTimestampTag(cue.startMs)} ${cue.text}"
                    }
                }
            } else {
                cues.joinToString("\n\n") { it.text }
            }
        }
        return ""
    }

    private fun formatSubtitleTimestamp(ms: Long): String {
        val safeMs = ms.coerceAtLeast(0L)
        val hours = safeMs / 3600_000
        val rem = safeMs % 3600_000
        val mins = rem / 60_000
        val secs = (rem % 60_000) / 1000
        val millis = rem % 1000
        return String.format(java.util.Locale.US, "%02d:%02d:%02d,%03d", hours, mins, secs, millis)
    }

    fun setSubtitleContentForCurrentTrack(content: String) {
        val track = currentTrackValue ?: return
        coroutineScope.launch(Dispatchers.IO) {
            val updated = track.copy(subtitleContent = content)
            repository?.updateTrack(updated)
            updateTrackState { updated }
            loadSubtitlesForTrack(updated)
            if (segmentSource == "SUBTITLES" || _isPracticeMode.value) {
                val repo = repository
                val ctx = appContext
                if (repo != null && ctx != null) {
                    reanalyzePracticeSegmentsInternal(updated, ctx, repo, silent = false)
                }
            }
        }
    }

    fun setSubtitleFileForCurrentTrack(filePath: String) {
        val track = currentTrackValue ?: return
        coroutineScope.launch(Dispatchers.IO) {
            val updated = track.copy(subtitlePath = filePath)
            repository?.updateTrack(updated)
            updateTrackState { updated }
            loadSubtitlesForTrack(updated)
            if (segmentSource == "SUBTITLES" || _isPracticeMode.value) {
                val repo = repository
                val ctx = appContext
                if (repo != null && ctx != null) {
                    reanalyzePracticeSegmentsInternal(updated, ctx, repo, silent = false)
                }
            }
        }
    }

    fun clearSubtitlesForCurrentTrack() {
        val track = currentTrackValue ?: return
        coroutineScope.launch(Dispatchers.IO) {
            val updated = track.copy(subtitleContent = null, subtitlePath = null)
            repository?.updateTrack(updated)
            updateTrackState { updated }
            withContext(Dispatchers.Main) {
                _subtitlesCues.value = emptyList()
                _activeSubtitleCue.value = null
            }
        }
    }

    fun adjustSubtitleOffset(deltaMs: Long) {
        val track = currentTrackValue ?: return
        val newOffset = track.subtitleOffsetMs + deltaMs
        val updated = track.copy(subtitleOffsetMs = newOffset)
        subtitleOffsetMs.value = newOffset
        coroutineScope.launch(Dispatchers.IO) {
            repository?.updateTrack(updated)
            updateTrackState { updated }
            loadSubtitlesForTrack(updated)
        }
    }

    fun resetSubtitleOffset() {
        val track = currentTrackValue ?: return
        val updated = track.copy(subtitleOffsetMs = 0L)
        subtitleOffsetMs.value = 0L
        coroutineScope.launch(Dispatchers.IO) {
            repository?.updateTrack(updated)
            updateTrackState { updated }
            loadSubtitlesForTrack(updated)
        }
    }

    // --- PRACTICE MODE (Silence Detection & Imitation Pauses) ---

    fun hasAvailableSubtitles(track: AudioTrack): Boolean {
        if (!track.subtitleContent.isNullOrBlank()) return true
        if (!track.subtitlePath.isNullOrBlank()) {
            val f = File(track.subtitlePath)
            if (f.exists() && f.length() > 0) return true
        }
        val autoFile = SubtitleParser.findMatchingSubtitleFile(track.filePath)
        if (autoFile != null && autoFile.length() > 0) return true
        if (_subtitlesCues.value.isNotEmpty() && currentTrackValue?.id == track.id) return true
        return false
    }

    fun getOrParseCuesForTrack(track: AudioTrack): List<SubtitleCue> {
        if (!track.subtitleContent.isNullOrBlank()) {
            val parsed = SubtitleParser.parseContent(track.subtitleContent, track.subtitleOffsetMs)
            if (parsed.isNotEmpty()) return parsed
        }
        if (!track.subtitlePath.isNullOrBlank()) {
            val file = File(track.subtitlePath)
            if (file.exists() && file.canRead()) {
                val parsed = SubtitleParser.parseFile(file, track.subtitleOffsetMs)
                if (parsed.isNotEmpty()) return parsed
            }
        }
        val autoFile = SubtitleParser.findMatchingSubtitleFile(track.filePath)
        if (autoFile != null && autoFile.canRead()) {
            val parsed = SubtitleParser.parseFile(autoFile, track.subtitleOffsetMs)
            if (parsed.isNotEmpty()) return parsed
        }
        if (currentTrackValue?.id == track.id && _subtitlesCues.value.isNotEmpty()) {
            return _subtitlesCues.value
        }
        return emptyList()
    }

    fun needsReanalysis(track: AudioTrack, context: Context? = null): Boolean {
        val existing = track.getPracticeSegmentsList()
        if (existing.isEmpty()) return true

        val storedSource = track.getPracticeSegmentsSource()
        if (storedSource == "MANUAL") {
            return false
        }
        // If stored source is known and differs from active source:
        if (storedSource != null && segmentSource != "MANUAL" && storedSource != segmentSource) {
            return true
        }

        // If legacy stored segments (no prefix):
        if (storedSource == null) {
            if (segmentSource == "SUBTITLES" && hasAvailableSubtitles(track)) {
                return true
            }
        }

        if (segmentSource == "SUBTITLES") {
            val cues = getOrParseCuesForTrack(track)
            if (cues.isNotEmpty() && Math.abs(cues.size - existing.size) > 1) {
                return true
            }
            return false
        }

        val effectiveContext = context ?: appContext
        val effectiveDuration = if (track.duration > 0) {
            track.duration
        } else if (_duration.value > 0) {
            _duration.value
        } else {
            val mpDur = try { mediaPlayer?.duration?.toLong() ?: 0L } catch (e: Exception) { 0L }
            if (mpDur > 0) mpDur else (effectiveContext?.let { SilenceDetector.getAudioDuration(it, track.filePath) } ?: 0L)
        }

        if (effectiveDuration > 15000L) {
            val hasPrematureCutoffGap = existing.zipWithNext().any { (a, b) -> (b - a) > 12000L }
            if (hasPrematureCutoffGap) return true
            if (existing.first() > 12000L) return true
            if (existing.last() < (effectiveDuration - 4000L)) return true
            if (effectiveDuration > 25000L && existing.size < 3) return true
        }

        return false
    }

    private suspend fun extractSubtitleBoundaries(
        track: AudioTrack,
        effectiveDuration: Long
    ): List<Long> {
        val rawCues = getOrParseCuesForTrack(track)
        if (rawCues.isEmpty()) return emptyList()

        if (_subtitlesCues.value.isEmpty() && currentTrackValue?.id == track.id) {
            withContext(Dispatchers.Main) {
                _subtitlesCues.value = rawCues
            }
        }

        val rawBoundaries: List<Long>
        if (track.isVirtualScene) {
            val sceneStart = track.startOffsetMs
            val sceneEnd = track.endOffsetMs ?: (track.startOffsetMs + track.duration)
            val sceneDuration = track.duration.coerceAtLeast(1000L)
            val timedCues = rawCues.filter { it.isTimed && it.startMs >= 0L }
            val sceneCues = timedCues.filter { it.endMs > sceneStart && it.startMs < sceneEnd }

            rawBoundaries = if (sceneCues.isNotEmpty()) {
                sceneCues.map { cue ->
                    val cueEnd = if (cue.endMs > cue.startMs) cue.endMs else (cue.startMs + 3000L)
                    (cueEnd - sceneStart).coerceIn(400L, sceneDuration)
                }
            } else {
                val lines = rawCues.take(minOf(rawCues.size, 10))
                val lineDur = sceneDuration / lines.size.coerceAtLeast(1)
                lines.indices.map { (it + 1) * lineDur }
            }
        } else {
            val timedCues = rawCues.filter { it.isTimed && it.startMs >= 0L }.sortedBy { it.startMs }
            rawBoundaries = if (timedCues.isNotEmpty()) {
                timedCues.map { cue ->
                    val cueEnd = if (cue.endMs > cue.startMs) cue.endMs else (cue.startMs + 3000L)
                    if (effectiveDuration > 0L) {
                        cueEnd.coerceIn(400L, effectiveDuration)
                    } else {
                        cueEnd.coerceAtLeast(400L)
                    }
                }
            } else if (effectiveDuration > 0L) {
                val nonBlank = rawCues.filter { it.text.isNotBlank() }
                if (nonBlank.isNotEmpty()) {
                    val lineDur = effectiveDuration / nonBlank.size
                    nonBlank.indices.map { (it + 1) * lineDur }
                } else {
                    emptyList()
                }
            } else {
                emptyList()
            }
        }

        val boundaries = mutableListOf<Long>()
        val maxLimit = if (effectiveDuration > 0L) (effectiveDuration - 350L) else Long.MAX_VALUE
        val sorted = rawBoundaries
            .filter { it > 300L && it <= maxLimit }
            .distinct()
            .sorted()

        for (b in sorted) {
            if (boundaries.isEmpty() || b - boundaries.last() >= 500L) {
                boundaries.add(b)
            }
        }
        return boundaries
    }

    suspend fun reanalyzePracticeSegmentsInternal(
        track: AudioTrack,
        context: Context,
        targetRepo: AppRepository,
        silent: Boolean = false,
        onComplete: ((Int) -> Unit)? = null
    ) {
        _isPracticeAnalyzing.value = true
        try {
            val effectiveDuration = if (track.duration > 0) {
                track.duration
            } else if (_duration.value > 0) {
                _duration.value
            } else {
                val mpDur = try { mediaPlayer?.duration?.toLong() ?: 0L } catch (e: Exception) { 0L }
                if (mpDur > 0) mpDur else SilenceDetector.getAudioDuration(context, track.filePath)
            }

            val boundaries: List<Long>
            val isSubtitleSource = (segmentSource == "SUBTITLES")
            val isManualSource = (segmentSource == "MANUAL")
            var usedPrefix = "SIL:"

            if (isManualSource) {
                if (track.practiceSegments?.startsWith("MAN:") == true) {
                    val existing = track.getPracticeSegmentsList()
                    if (existing.isNotEmpty()) {
                        withContext(Dispatchers.Main) {
                            _currentPracticeSegments.value = existing
                            _isPracticeAnalyzing.value = false
                            resetPracticeSegmentTracking(_currentPosition.value)
                            onComplete?.invoke(existing.size)
                        }
                        return
                    }
                }
                // When MANUAL is selected but no manual cuts exist for this track,
                // fallback gracefully to Subtitles if available, otherwise Silence detection.
                // We MUST NEVER save SilenceDetector results as "MAN:".
                if (hasAvailableSubtitles(track)) {
                    val subBoundaries = extractSubtitleBoundaries(track, effectiveDuration)
                    if (subBoundaries.isNotEmpty()) {
                        boundaries = subBoundaries
                        usedPrefix = "SUB:"
                        if (!silent) {
                            withContext(Dispatchers.Main) {
                                val msg = String.format(java.util.Locale.US, Loc.getText("segments_created_from_subtitles_count"), subBoundaries.size)
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            }
                        }
                    } else {
                        boundaries = SilenceDetector.detectBoundaries(
                            context = context,
                            filePath = track.filePath,
                            totalDurationMs = effectiveDuration,
                            sensitivity = silenceSensitivity,
                            minSilenceMs = silenceMinDurationMs,
                            tailPaddingMs = silencePaddingMs
                        )
                        usedPrefix = "SIL:"
                        if (!silent) {
                            withContext(Dispatchers.Main) {
                                val msg = String.format(java.util.Locale.US, Loc.getText("segments_created_from_silence_count"), boundaries.size)
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                } else {
                    boundaries = SilenceDetector.detectBoundaries(
                        context = context,
                        filePath = track.filePath,
                        totalDurationMs = effectiveDuration,
                        sensitivity = silenceSensitivity,
                        minSilenceMs = silenceMinDurationMs,
                        tailPaddingMs = silencePaddingMs
                    )
                    usedPrefix = "SIL:"
                    if (!silent) {
                        withContext(Dispatchers.Main) {
                            val msg = String.format(java.util.Locale.US, Loc.getText("segments_created_from_silence_count"), boundaries.size)
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } else if (isSubtitleSource) {
                val subBoundaries = extractSubtitleBoundaries(track, effectiveDuration)
                if (subBoundaries.isNotEmpty()) {
                    boundaries = subBoundaries
                    usedPrefix = "SUB:"
                    if (!silent) {
                        withContext(Dispatchers.Main) {
                            val msg = String.format(java.util.Locale.US, Loc.getText("segments_created_from_subtitles_count"), subBoundaries.size)
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    if (!silent) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, Loc.getText("no_subtitles_for_segments"), Toast.LENGTH_LONG).show()
                        }
                    }
                    boundaries = SilenceDetector.detectBoundaries(
                        context = context,
                        filePath = track.filePath,
                        totalDurationMs = effectiveDuration,
                        sensitivity = silenceSensitivity,
                        minSilenceMs = silenceMinDurationMs,
                        tailPaddingMs = silencePaddingMs
                    )
                    usedPrefix = "SIL:"
                }
            } else {
                boundaries = SilenceDetector.detectBoundaries(
                    context = context,
                    filePath = track.filePath,
                    totalDurationMs = effectiveDuration,
                    sensitivity = silenceSensitivity,
                    minSilenceMs = silenceMinDurationMs,
                    tailPaddingMs = silencePaddingMs
                )
                usedPrefix = "SIL:"
                if (!silent) {
                    withContext(Dispatchers.Main) {
                        val msg = String.format(java.util.Locale.US, Loc.getText("segments_created_from_silence_count"), boundaries.size)
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    }
                }
            }

            val segmentsStr = usedPrefix + boundaries.joinToString(",")
            targetRepo.updateTrackPracticeSegments(track.id, segmentsStr)
            updateTrackState { it.copy(practiceSegments = segmentsStr) }

            withContext(Dispatchers.Main) {
                _currentPracticeSegments.value = boundaries
                _isPracticeAnalyzing.value = false
                resetPracticeSegmentTracking(_currentPosition.value)
                onComplete?.invoke(boundaries.size)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Practice segmentation error: ${e.message}")
            withContext(Dispatchers.Main) {
                _isPracticeAnalyzing.value = false
            }
        }
    }

    fun reanalyzePracticeSegments(
        context: Context,
        repository: AppRepository,
        silent: Boolean = false,
        onComplete: ((Int) -> Unit)? = null
    ) {
        val track = currentTrackValue ?: return
        coroutineScope.launch(Dispatchers.IO) {
            reanalyzePracticeSegmentsInternal(track, context, repository, silent, onComplete)
        }
    }

    data class PracticeTrackSourcesInfo(
        val hasManualCuts: Boolean,
        val manualCutsCount: Int,
        val hasSubtitles: Boolean,
        val subtitlesCount: Int,
        val activeSource: String,
        val activeSegmentsCount: Int
    )

    fun getActivePracticeSourceForTrack(track: AudioTrack): String {
        val stored = track.getPracticeSegmentsSource()
        if (stored != null) return stored
        val hasManual = track.practiceSegments?.startsWith("MAN:") == true
        val hasSub = hasAvailableSubtitles(track)
        return when (segmentSource) {
            "MANUAL" -> if (hasManual) "MANUAL" else if (hasSub) "SUBTITLES" else "SILENCE"
            "SUBTITLES" -> if (hasSub) "SUBTITLES" else "SILENCE"
            else -> "SILENCE"
        }
    }

    fun getPracticeTrackSourcesInfo(track: AudioTrack): PracticeTrackSourcesInfo {
        val manualCuts = if (track.practiceSegments?.startsWith("MAN:") == true) {
            track.getPracticeSegmentsList()
        } else emptyList()

        val cues = getOrParseCuesForTrack(track)
        val activeSource = getActivePracticeSourceForTrack(track)
        val count = if (_isPracticeMode.value && _currentPracticeSegments.value.isNotEmpty()) {
            _currentPracticeSegments.value.size
        } else {
            track.getPracticeSegmentsList().size
        }
        return PracticeTrackSourcesInfo(
            hasManualCuts = manualCuts.isNotEmpty(),
            manualCutsCount = manualCuts.size,
            hasSubtitles = cues.isNotEmpty(),
            subtitlesCount = cues.size,
            activeSource = activeSource,
            activeSegmentsCount = count
        )
    }

    fun applyPracticeSettingsAndStart(
        track: AudioTrack,
        source: String,
        multiplier: Float,
        context: Context,
        repository: AppRepository
    ) {
        val normalizedSource = when (source.uppercase()) {
            "MANUAL" -> "MANUAL"
            "SUBTITLES" -> "SUBTITLES"
            else -> "SILENCE"
        }
        segmentSource = normalizedSource
        practicePauseMultiplier = multiplier.coerceIn(0.25f, 4.0f)

        // If manual and cuts exist, directly apply without reanalysis
        if (normalizedSource == "MANUAL" && track.practiceSegments?.startsWith("MAN:") == true) {
            val list = track.getPracticeSegmentsList()
            if (list.isNotEmpty()) {
                _currentPracticeSegments.value = list
                _isPracticeMode.value = true
                resetPracticeSegmentTracking(_currentPosition.value)
                if (!_isPlaying.value) {
                    resume()
                }
                Toast.makeText(
                    context,
                    "${Loc.getText("practice_source_manual_short")} (${list.size} ${Loc.getText("cuts_label")})",
                    Toast.LENGTH_SHORT
                ).show()
                return
            }
        }

        coroutineScope.launch(Dispatchers.IO) {
            reanalyzePracticeSegmentsInternal(track, context, repository, silent = false) { count ->
                _isPracticeMode.value = true
                if (!_isPlaying.value) {
                    resume()
                }
            }
        }
    }

    fun saveManualPracticeSegments(context: Context, track: AudioTrack, boundaries: List<Long>, autoEnable: Boolean = true) {
        val sorted = boundaries.filter { it > 0 }.distinct().sorted()
        val segmentsStr = "MAN:" + sorted.joinToString(",")
        segmentSource = "MANUAL"
        val repo = repository
        coroutineScope.launch(Dispatchers.IO) {
            repo?.updateTrackPracticeSegments(track.id, segmentsStr)
            updateTrackState { it.copy(practiceSegments = segmentsStr) }
            withContext(Dispatchers.Main) {
                _currentPracticeSegments.value = sorted
                resetPracticeSegmentTracking(_currentPosition.value)
                if (autoEnable) {
                    _isPracticeMode.value = true
                    if (!_isPlaying.value) {
                        resume()
                    }
                }
                Toast.makeText(
                    context,
                    if (autoEnable) Loc.getText("manual_cuts_applied_and_active")
                    else String.format(java.util.Locale.US, Loc.getText("manual_cuts_saved"), sorted.size),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    fun clearPracticeSegmentsForCurrentTrack() {
        practicePauseJob?.cancel()
        _isPracticePausing.value = false
        _practicePauseRemainingSeconds.value = 0f
        _currentPracticeSegments.value = emptyList()
        practiceNextBoundaryIndex = 0
        practiceLastSegmentStartMs = 0L
        val track = currentTrackValue
        if (track != null) {
            coroutineScope.launch(Dispatchers.IO) {
                repository?.updateTrackPracticeSegments(track.id, null)
                updateTrackState { it.copy(practiceSegments = null) }
            }
        }
    }

    fun togglePracticeMode(context: Context? = null, repository: AppRepository? = null, forceReanalyze: Boolean = false) {
        if (_isPracticeMode.value && !forceReanalyze) {
            _isPracticeMode.value = false
            practicePauseJob?.cancel()
            val wasPausing = _isPracticePausing.value
            _isPracticePausing.value = false
            _practicePauseRemainingSeconds.value = 0f
            if (wasPausing) {
                resume()
            }
        } else {
            val targetContext = context ?: appContext ?: return
            val targetRepo = repository ?: this.repository ?: return
            val track = currentTrackValue ?: return
            val shouldReanalyze = forceReanalyze || needsReanalysis(track, targetContext)

            if (!shouldReanalyze) {
                _currentPracticeSegments.value = track.getPracticeSegmentsList()
                _isPracticeMode.value = true
                resetPracticeSegmentTracking(_currentPosition.value)
                if (!_isPlaying.value) {
                    resume()
                }
            } else {
                coroutineScope.launch(Dispatchers.IO) {
                    reanalyzePracticeSegmentsInternal(track, targetContext, targetRepo, silent = false) {
                        _isPracticeMode.value = true
                        if (!_isPlaying.value) {
                            resume()
                        }
                    }
                }
            }
        }
    }

    private fun resetPracticeSegmentTracking(pos: Long) {
        val segments = _currentPracticeSegments.value
        val nextIdx = segments.indexOfFirst { it > pos }
        practiceNextBoundaryIndex = if (nextIdx == -1) segments.size else nextIdx
        practiceLastSegmentStartMs = if (practiceNextBoundaryIndex > 0) segments[practiceNextBoundaryIndex - 1] else 0L
    }

    fun onSeekInPracticeMode(targetMs: Long) {
        if (!_isPracticeMode.value) return
        val wasPausing = _isPracticePausing.value
        practicePauseJob?.cancel()
        _isPracticePausing.value = false
        _practicePauseRemainingSeconds.value = 0f
        resetPracticeSegmentTracking(targetMs)
        if (wasPausing) {
            resume()
        }
    }

    private fun checkPracticeSegmentBoundary(currentPosMs: Long) {
        val segments = _currentPracticeSegments.value
        if (segments.isEmpty()) return

        // Auto re-sync when position jumped backwards (rewind) or jumped forward past next boundary window
        val currentExpectedBoundary = if (practiceNextBoundaryIndex in segments.indices) segments[practiceNextBoundaryIndex] else -1L
        if (practiceNextBoundaryIndex >= segments.size || currentPosMs < (practiceLastSegmentStartMs - 500L) || (currentExpectedBoundary != -1L && currentPosMs > currentExpectedBoundary + 1500L)) {
            resetPracticeSegmentTracking(currentPosMs)
        }

        if (practiceNextBoundaryIndex >= segments.size) return

        val boundary = segments[practiceNextBoundaryIndex]
        if (currentPosMs >= boundary && currentPosMs <= boundary + 1500L) {
            val segmentLengthMs = if (segmentSource == "SUBTITLES") {
                val cues = _subtitlesCues.value
                val matchingCue = cues.find { cue ->
                    val cueEnd = if (currentTrackValue?.isVirtualScene == true) {
                        cue.endMs - (currentTrackValue?.startOffsetMs ?: 0L)
                    } else {
                        cue.endMs
                    }
                    Math.abs(cueEnd - boundary) <= 500L
                }
                if (matchingCue != null && matchingCue.endMs > matchingCue.startMs) {
                    (matchingCue.endMs - matchingCue.startMs).coerceIn(1200L, 25000L)
                } else {
                    (boundary - practiceLastSegmentStartMs).coerceIn(1200L, 25000L)
                }
            } else {
                (boundary - practiceLastSegmentStartMs).coerceAtLeast(1200L)
            }
            practiceLastSegmentStartMs = boundary
            practiceNextBoundaryIndex++
            triggerPracticePause(segmentLengthMs)
        }
    }

    private fun triggerPracticePause(segmentLengthMs: Long) {
        val multiplier = practicePauseMultiplier.coerceIn(0.25f, 4.0f)
        val pauseDurationMs = (segmentLengthMs * multiplier).toLong().coerceIn(1000L, 30000L)
        _isPracticePausing.value = true
        val totalSec = pauseDurationMs / 1000f
        _practicePauseTotalSeconds.value = totalSec
        _practicePauseRemainingSeconds.value = totalSec

        coroutineScope.launch(Dispatchers.Main) {
            try {
                mediaPlayer?.pause()
                _isPlaying.value = false
            } catch (e: Exception) {
                Log.w(TAG, "Practice pause error: ${e.message}")
            }
        }

        practicePauseJob?.cancel()
        practicePauseJob = coroutineScope.launch {
            val startTime = System.currentTimeMillis()
            val endTime = startTime + pauseDurationMs
            while (System.currentTimeMillis() < endTime && _isPracticeMode.value && _isPracticePausing.value) {
                val remaining = (endTime - System.currentTimeMillis()).coerceAtLeast(0L)
                _practicePauseRemainingSeconds.value = remaining / 1000f
                delay(100L)
            }
            if (_isPracticeMode.value && _isPracticePausing.value) {
                resumeFromPracticePause()
            }
        }
    }

    fun resumeFromPracticePause() {
        practicePauseJob?.cancel()
        _isPracticePausing.value = false
        _practicePauseRemainingSeconds.value = 0f
        coroutineScope.launch(Dispatchers.Main) {
            try {
                mediaPlayer?.start()
                _isPlaying.value = true
                startProgressTracking()
            } catch (e: Exception) {
                Log.e(TAG, "Resume from practice pause error: ${e.message}")
            }
        }
    }

    fun skipPracticePause() {
        resumeFromPracticePause()
    }
}
