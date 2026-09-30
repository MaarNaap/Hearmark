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
    internal const val TAG = "AudioPlayerManager"

    internal var mediaPlayer: MediaPlayer? = null
    internal var currentTrackValue: AudioTrack? = null
    private val _currentQueueFlow = MutableStateFlow<List<AudioTrack>>(emptyList())
    val currentQueueFlow: StateFlow<List<AudioTrack>> = _currentQueueFlow.asStateFlow()

    internal var currentQueue: List<AudioTrack> = emptyList()
        set(value) {
            field = value
            _currentQueueFlow.value = value
            saveQueueToPreferences(value)
        }

    internal val _currentTrack = MutableStateFlow<AudioTrack?>(null)
    val currentTrack: StateFlow<AudioTrack?> = _currentTrack.asStateFlow()

    val isAutoPlayEnabled = MutableStateFlow(true)

    internal val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    internal val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    internal val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    internal val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    // Sleep Timer
    internal var sleepTimerJob: Job? = null
    internal val _sleepTimeRemaining = MutableStateFlow(0) // seconds remaining, 0 for off
    val sleepTimeRemaining: StateFlow<Int> = _sleepTimeRemaining.asStateFlow()
    internal var sleepTimerOption = 0 // 0=off, 15, 30, 45, 60, -1=end of file

    // Practice Mode (Silence Detection & Imitation Pauses)
    internal val _isPracticeMode = MutableStateFlow(false)
    val isPracticeMode: StateFlow<Boolean> = _isPracticeMode.asStateFlow()

    internal val _isPracticeAnalyzing = MutableStateFlow(false)
    val isPracticeAnalyzing: StateFlow<Boolean> = _isPracticeAnalyzing.asStateFlow()

    internal val _isPracticePausing = MutableStateFlow(false)
    val isPracticePausing: StateFlow<Boolean> = _isPracticePausing.asStateFlow()

    internal val _practicePauseRemainingSeconds = MutableStateFlow(0f)
    val practicePauseRemainingSeconds: StateFlow<Float> = _practicePauseRemainingSeconds.asStateFlow()

    internal val _practicePauseTotalSeconds = MutableStateFlow(0f)
    val practicePauseTotalSeconds: StateFlow<Float> = _practicePauseTotalSeconds.asStateFlow()

    internal val _practicePauseMultiplierFlow = MutableStateFlow(1.0f)
    val practicePauseMultiplierFlow: StateFlow<Float> = _practicePauseMultiplierFlow.asStateFlow()

    internal val _currentPracticeSegments = MutableStateFlow<List<Long>>(emptyList())
    val currentPracticeSegments: StateFlow<List<Long>> = _currentPracticeSegments.asStateFlow()

    internal var practicePauseJob: Job? = null
    internal var practiceLastSegmentStartMs = 0L
    internal var practiceNextBoundaryIndex = 0

    // Subtitles & Lyrics State
    internal val _subtitlesCues = MutableStateFlow<List<SubtitleCue>>(emptyList())
    val subtitlesCues: StateFlow<List<SubtitleCue>> = _subtitlesCues.asStateFlow()

    internal val _activeSubtitleCue = MutableStateFlow<SubtitleCue?>(null)
    val activeSubtitleCue: StateFlow<SubtitleCue?> = _activeSubtitleCue.asStateFlow()

    enum class VideoSubtitleMode {
        SHOW,
        HIDE,
        BLACK
    }
    val videoSubtitleMode = MutableStateFlow(VideoSubtitleMode.SHOW)

    val isVideoFullWidth = MutableStateFlow(true)

    val isFocusMode = MutableStateFlow(false)
    val isVideoFocusMode: MutableStateFlow<Boolean> get() = isFocusMode

    fun toggleFocusMode(): Boolean {
        val next = !isFocusMode.value
        setFocusMode(next)
        return next
    }

    fun toggleVideoFocusMode(): Boolean = toggleFocusMode()

    fun setFocusMode(enabled: Boolean) {
        isFocusMode.value = enabled
        if (!enabled && _isPracticeMode.value) {
            stopPracticeMode()
        }
    }

    fun setVideoFocusMode(enabled: Boolean) {
        setFocusMode(enabled)
    }

    fun toggleVideoFullWidth(): Boolean {
        isVideoFullWidth.value = true
        return true
    }

    fun setVideoFullWidth(enabled: Boolean) {
        isVideoFullWidth.value = true
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
    internal var mediaSession: MediaSessionCompat? = null
    val mediaSessionToken: MediaSessionCompat.Token?
        get() = mediaSession?.sessionToken

    fun getMediaSession(): MediaSessionCompat? = mediaSession

    val isHeadsetControlsEnabled = MutableStateFlow(true)
    val headsetMultiClickAction = MutableStateFlow("NEXT_PREV") // "NEXT_PREV" or "SKIP_SECONDS"

    internal var headsetClickCount = 0
    internal val headsetClickHandler = Handler(Looper.getMainLooper())
    internal val headsetClickRunnable = Runnable {
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
    private var initialSessionProgressPercent = 0
    private val completedTaskIdsForCurrentSession = java.util.Collections.synchronizedSet(mutableSetOf<Long>())

    @Volatile
    private var isSeeking = false
    @Volatile
    private var lastSeekTimestamp = 0L

    // Active listening stopwatch (measures exact wall-clock listening duration while isPlaying and in practice pauses)
    private var sessionActualListeningMs: Long = 0L
    internal var lastActivePlayTimestamp: Long = 0L
    internal var lastPracticePauseTimestamp: Long = 0L
    private var currentSessionHistoryId: Long? = null
    // Thread-safe map tracking accumulated listening time (ms) for the current play in progress of each track across sessions
    internal val trackAccumulatedListeningMsMap = java.util.concurrent.ConcurrentHashMap<Long, Long>()

    // In-memory cached bitset of listened segments for the active track to eliminate string parsing/splitting on playback ticks
    private var activeTrackSegmentTrackId: Long? = null
    private val activeTrackSegmentsBitSet = java.util.BitSet(100)
    private var playbackWakeLock: android.os.PowerManager.WakeLock? = null

    private fun acquirePlaybackWakeLock() {
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

    private fun releasePlaybackWakeLock() {
        try {
            if (playbackWakeLock?.isHeld == true) {
                playbackWakeLock?.release()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to release playback wake lock: ${e.message}")
        }
    }

    private fun parseSegmentsIntoBitSet(rawSegments: String, targetBitSet: java.util.BitSet) {
        if (rawSegments.isEmpty()) return
        var current = 0
        var hasDigits = false
        for (i in 0 until rawSegments.length) {
            val ch = rawSegments[i]
            if (ch in '0'..'9') {
                current = current * 10 + (ch - '0')
                hasDigits = true
            } else if (ch == ',') {
                if (hasDigits && current in 0..999) {
                    targetBitSet.set(current)
                }
                current = 0
                hasDigits = false
            }
        }
        if (hasDigits && current in 0..999) {
            targetBitSet.set(current)
        }
    }

    @Synchronized
    private fun getOrInitActiveBitSet(trackId: Long, rawSegments: String): java.util.BitSet {
        if (activeTrackSegmentTrackId != trackId) {
            activeTrackSegmentTrackId = trackId
            activeTrackSegmentsBitSet.clear()
            parseSegmentsIntoBitSet(rawSegments, activeTrackSegmentsBitSet)
        } else if (rawSegments.isNotEmpty()) {
            // Merge any segments present in rawSegments so we never lose segments across async updates
            parseSegmentsIntoBitSet(rawSegments, activeTrackSegmentsBitSet)
        }
        return activeTrackSegmentsBitSet
    }

    @Synchronized
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
    internal fun accumulateActiveListeningTime() {
        if (lastActivePlayTimestamp > 0L) {
            val now = System.currentTimeMillis()
            val delta = now - lastActivePlayTimestamp
            if (delta > 0L) {
                sessionActualListeningMs += minOf(delta, 10000L)
            }
            lastActivePlayTimestamp = now
        }
    }

    @Synchronized
    internal fun accumulatePracticePauseTime() {
        if (lastPracticePauseTimestamp > 0L) {
            val now = System.currentTimeMillis()
            val delta = now - lastPracticePauseTimestamp
            if (delta > 0L) {
                sessionActualListeningMs += minOf(delta, 10000L)
            }
            lastPracticePauseTimestamp = now
        }
    }

    @Synchronized
    fun persistCurrentPlayListeningTime() {
        accumulateActiveListeningTime()
        accumulatePracticePauseTime()
        val delta = sessionActualListeningMs
        val track = currentTrackValue ?: return
        if (delta <= 0L) return
        sessionActualListeningMs = 0L
        val prevTotal = trackAccumulatedListeningMsMap[track.id] ?: track.currentPlayActualListeningMs
        val newTotal = prevTotal + delta
        trackAccumulatedListeningMsMap[track.id] = newTotal

        val latestSegments = if (activeTrackSegmentTrackId == track.id && !activeTrackSegmentsBitSet.isEmpty) {
            serializeBitSet(activeTrackSegmentsBitSet)
        } else {
            track.listenedSegments
        }

        val updatedTrack = track.copy(
            currentPlayActualListeningMs = newTotal,
            listenedSegments = latestSegments
        )
        currentTrackValue = updatedTrack
        _currentTrack.value = updatedTrack

        coroutineScope.launch(Dispatchers.IO) {
            updateTrackState { t ->
                if (t.id == track.id) t.copy(currentPlayActualListeningMs = newTotal) else t
            }
        }
    }

    private fun syncCurrentSessionHistory(isFinishing: Boolean = false) {
        val track = currentTrackValue ?: return
        accumulateActiveListeningTime()
        accumulatePracticePauseTime()

        // Total actual listening duration for THIS PLAY of the track across all sessions
        val accumulatedSoFar = trackAccumulatedListeningMsMap[track.id] ?: track.currentPlayActualListeningMs
        val totalActualMs = accumulatedSoFar + sessionActualListeningMs
        val shouldRecord = isThresholdTriggeredForCurrentSession
        if (!shouldRecord) {
            if (isFinishing) {
                // When finishing an uncompleted session (e.g. paused at 20% or 80%),
                // flush session listening time into the track so it is retained for future sessions!
                persistCurrentPlayListeningTime()
                sessionActualListeningMs = 0L
                currentSessionHistoryId = null
            }
            return
        }

        val historyId = currentSessionHistoryId ?: 0L
        val recordedActual = if (totalActualMs > 0L) totalActualMs else track.duration
        val speed = _playbackSpeed.value

        coroutineScope.launch(Dispatchers.IO) {
            try {
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
                    id = historyId,
                    trackId = track.id,
                    trackName = track.fileName,
                    completedAt = System.currentTimeMillis(),
                    durationMs = track.duration,
                    playbackSpeed = speed,
                    actualListenedMs = recordedActual,
                    activeTasks = activeTasksJson
                )
                val newId = repository?.insertPlaybackHistory(history) ?: 0L
                if (currentSessionHistoryId == null && newId > 0L) {
                    currentSessionHistoryId = newId
                }
            } catch (e: Exception) {
                Log.e(TAG, "syncCurrentSessionHistory error: ${e.message}")
            } finally {
                if (isFinishing) {
                    currentSessionHistoryId = null
                    sessionActualListeningMs = 0L
                }
            }
        }
    }

    internal val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var progressTrackingJob: Job? = null

    internal var appContext: Context? = null
    internal var repository: AppRepository? = null

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
        
        // Load playback speed, headset, and subtitle preferences
        try {
            val sharedPref = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            val savedSpeed = sharedPref.getFloat("playback_speed", 1.0f)
            _playbackSpeed.value = savedSpeed
            isAutoPlayEnabled.value = sharedPref.getBoolean("autoplay_enabled", true)
            isHeadsetControlsEnabled.value = sharedPref.getBoolean("headset_controls_enabled", true)
            headsetMultiClickAction.value = sharedPref.getString("headset_multiclick_action", "NEXT_PREV") ?: "NEXT_PREV"
            val savedFontSize = sharedPref.getFloat("subtitle_font_size", 16f)
            subtitleFontSize.value = savedFontSize
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
        internal set

    // Multiplier for automatic pause duration after each segment
    var practicePauseMultiplier: Float = 1.0f
        internal set

    // Silence detection tuning
    var silenceSensitivity: String = "MEDIUM"
        internal set
    var silenceMinDurationMs: Long = 500L
        internal set
    var silencePaddingMs: Long = 200L
        internal set

    fun setPracticeSettings(source: String, multiplier: Float) {
        val oldSource = segmentSource
        segmentSource = when (source.uppercase()) {
            "SUBTITLES" -> "SUBTITLES"
            "MANUAL" -> "MANUAL"
            else -> "SILENCE"
        }
        val coerced = multiplier.coerceIn(0.25f, 4.0f)
        practicePauseMultiplier = coerced
        _practicePauseMultiplierFlow.value = coerced

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

            // 1. Sync & finish previous track session if one was active
            if (currentTrackValue != null) {
                persistCurrentPlayListeningTime()
                saveCurrentPositionProgress()
                syncCurrentSessionHistory(isFinishing = true)
            }

            // Stop current playback
            mediaPlayer?.release()
            mediaPlayer = null

            // Reset stopwatch and tracking for the new track
            sessionActualListeningMs = 0L
            currentSessionHistoryId = null
            lastActivePlayTimestamp = System.currentTimeMillis()
            lastPracticePauseTimestamp = 0L
            activeTrackSegmentTrackId = null
            activeTrackSegmentsBitSet.clear()

            val initialAccumulatedMs = maxOf(
                trackAccumulatedListeningMsMap[track.id] ?: 0L,
                currentTrackValue?.takeIf { it.id == track.id }?.currentPlayActualListeningMs ?: 0L,
                track.currentPlayActualListeningMs
            )
            trackAccumulatedListeningMsMap[track.id] = initialAccumulatedMs

            val initialTrack = track.copy(currentPlayActualListeningMs = initialAccumulatedMs)

            currentTrackValue = initialTrack
            _currentTrack.value = initialTrack
            _duration.value = initialTrack.duration
            initialSessionProgressPercent = initialTrack.getProgressPercent()
            isThresholdTriggeredForCurrentSession = false
            completedTaskIdsForCurrentSession.clear()

            // Keep in-memory queue in sync with authoritative track
            val updatedQueue = currentQueue.toMutableList()
            val qIdx = updatedQueue.indexOfFirst { it.id == initialTrack.id }
            if (qIdx != -1) {
                updatedQueue[qIdx] = initialTrack
                currentQueue = updatedQueue
            }
            
            // Fetch authoritative track from Room DB to sync playCount and segments asynchronously
            repository?.let { repo ->
                coroutineScope.launch(Dispatchers.IO) {
                    try {
                        val dbTrack = repo.getTrackById(track.id)
                        if (dbTrack != null) {
                            val dbAccumulated = dbTrack.currentPlayActualListeningMs
                            val currentAcc = trackAccumulatedListeningMsMap[track.id] ?: 0L
                            val finalAcc = maxOf(dbAccumulated, currentAcc)
                            trackAccumulatedListeningMsMap[track.id] = finalAcc
                            updateTrackState { current ->
                                if (current.id == dbTrack.id) {
                                    val bitSet = getOrInitActiveBitSet(dbTrack.id, dbTrack.listenedSegments)
                                    parseSegmentsIntoBitSet(current.listenedSegments, bitSet)
                                    val mergedSegments = serializeBitSet(bitSet)
                                    val effDur = if (current.duration > 0) current.duration else dbTrack.duration
                                    dbTrack.copy(
                                        duration = effDur,
                                        lastPosition = current.lastPosition,
                                        listenedSegments = mergedSegments,
                                        currentPlayActualListeningMs = finalAcc
                                    )
                                } else {
                                    current
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
                appContext?.let { ctx ->
                    try {
                        setWakeMode(ctx.applicationContext, android.os.PowerManager.PARTIAL_WAKE_LOCK)
                    } catch (e: Exception) {
                        Log.w(TAG, "setWakeMode failed: ${e.message}")
                    }
                }
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
                    if (initialPhysicalSeek > 0L) {
                        seekTo(initialPhysicalSeek.toInt())
                    }
                    _currentPosition.value = track.lastPosition.coerceIn(0L, virtualDuration)
                } else {
                    if (realDuration > 0) {
                        _duration.value = realDuration
                    }
                    // Seek to the last saved position immediately if non-zero
                    if (track.lastPosition > 0L) {
                        seekTo(track.lastPosition.toInt())
                    }
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
                
                setOnSeekCompleteListener {
                    isSeeking = false
                    val actual = try { currentPosition.toLong() } catch (e: Exception) { null }
                    if (actual != null) {
                        setLastTrackedPosition(actual)
                    }
                }

                setOnCompletionListener {
                    flushContinuousSegmentsBeforeCompletion()
                    // Normal play next or cycle completion (which handles sleep at end of file elegantly)
                    handlePhysicalEndOfTrack()
                }
            }

            acquirePlaybackWakeLock()
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
            lastActivePlayTimestamp = System.currentTimeMillis()
            val effectiveDuration = if (track.isVirtualScene) {
                _duration.value
            } else {
                if (_duration.value > 0) _duration.value else track.duration
            }
            val baseTrack = currentTrackValue ?: initialTrack
            val activeTrack = baseTrack.copy(duration = effectiveDuration)
            currentTrackValue = activeTrack
            _currentTrack.value = activeTrack
            getOrInitActiveBitSet(activeTrack.id, activeTrack.listenedSegments)
            setLastTrackedPosition(if (track.isVirtualScene) (track.startOffsetMs + track.lastPosition) else track.lastPosition)

            if (track.duration != effectiveDuration && effectiveDuration > 0) {
                coroutineScope.launch(Dispatchers.IO) {
                    updateTrackState { t ->
                        if (t.id == activeTrack.id) t.copy(duration = effectiveDuration) else t
                    }
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

    fun resume() {
        if (mediaPlayer == null) {
            currentTrackValue?.let { playTrack(it) }
            return
        }
        if (!requestAudioFocus()) return
        NoteAudioPlayer.stop()
        lastActivePlayTimestamp = System.currentTimeMillis()
        try {
            acquirePlaybackWakeLock()
            mediaPlayer?.let { mp ->
                try {
                    setLastTrackedPosition(mp.currentPosition.toLong())
                } catch (e: Exception) {
                    setLastTrackedPosition(null)
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
        accumulatePracticePauseTime()
        lastPracticePauseTimestamp = 0L
        practicePauseJob?.cancel()
        _isPracticePausing.value = false
        _practicePauseRemainingSeconds.value = 0f
        accumulateActiveListeningTime()
        lastActivePlayTimestamp = 0L
        flushContinuousSegmentsOnEvent()
        try {
            mediaPlayer?.let {
                if (try { it.isPlaying } catch (e: Exception) { false }) {
                    it.pause()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Pause error: ${e.message}")
        }
        isSeeking = false
        setLastTrackedPosition(null)
        _isPlaying.value = false
        stopProgressTracking()
        releasePlaybackWakeLock()
        abandonAudioFocus()
        updateMediaSessionPlaybackState()
        showNotification()
        
        // Persist progress position and active listening time inside database instantly on pause
        persistCurrentPlayListeningTime()
        saveCurrentPositionProgress()
        syncCurrentSessionHistory(isFinishing = false)
    }

    fun stop() {
        accumulatePracticePauseTime()
        lastPracticePauseTimestamp = 0L
        practicePauseJob?.cancel()
        _isPracticePausing.value = false
        _practicePauseRemainingSeconds.value = 0f
        accumulateActiveListeningTime()
        lastActivePlayTimestamp = 0L
        flushContinuousSegmentsOnEvent()
        persistCurrentPlayListeningTime()
        saveCurrentPositionProgress()
        syncCurrentSessionHistory(isFinishing = true)
        isSeeking = false
        setLastTrackedPosition(null)
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
        releasePlaybackWakeLock()
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

    private fun performSeek(mp: MediaPlayer, targetMs: Long) {
        val safeTarget = targetMs.coerceAtLeast(0L)
        try {
            mp.setOnSeekCompleteListener {
                isSeeking = false
                val actual = try { mp.currentPosition.toLong() } catch (e: Exception) { safeTarget }
                setLastTrackedPosition(actual)
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                mp.seekTo(safeTarget, MediaPlayer.SEEK_CLOSEST)
            } else {
                mp.seekTo(safeTarget.toInt())
            }
        } catch (e: Exception) {
            isSeeking = false
            Log.e(TAG, "performSeek error: ${e.message}")
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
                    isSeeking = true
                    lastSeekTimestamp = System.currentTimeMillis()
                    _currentPosition.value = virtualPos
                    setLastTrackedPosition(physicalTarget)
                    performSeek(mp, physicalTarget)
                    updateActiveSubtitleCue(physicalTarget)
                } else {
                    val maxDur = track?.duration ?: (try { mp.duration.toLong() } catch (e: Exception) { Long.MAX_VALUE })
                    val validTarget = position.coerceIn(0L, if (maxDur > 0) maxDur else Long.MAX_VALUE)
                    isSeeking = true
                    lastSeekTimestamp = System.currentTimeMillis()
                    _currentPosition.value = validTarget
                    setLastTrackedPosition(validTarget)
                    performSeek(mp, validTarget)
                    updateActiveSubtitleCue(validTarget)
                }
                onSeekInPracticeMode(_currentPosition.value)
                updateMediaSessionPlaybackState()
                saveCurrentPositionProgress()
            }
        } catch (e: Exception) {
            isSeeking = false
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
    @Volatile
    private var lastTrackedPositionMs: Long? = null
    @Volatile
    private var lastTrackedWallClockMs: Long = 0L

    private fun setLastTrackedPosition(posMs: Long?) {
        lastTrackedPositionMs = posMs
        lastTrackedWallClockMs = if (posMs != null) android.os.SystemClock.elapsedRealtime() else 0L
    }

    private fun computeMaxContinuousDeltaMs(nowWallMs: Long = android.os.SystemClock.elapsedRealtime()): Long {
        val prevWall = lastTrackedWallClockMs
        val elapsedWall = if (prevWall > 0L) (nowWallMs - prevWall).coerceAtLeast(0L) else 250L
        val speed = _playbackSpeed.value.coerceAtLeast(1.0f)
        return maxOf(2500L, (elapsedWall * speed * 1.5f).toLong() + 2000L)
    }

    internal fun flushContinuousSegmentsOnEvent() {
        if (isSeeking) return
        val mp = mediaPlayer ?: return
        val track = currentTrackValue ?: return
        val physicalPos = try {
            if (mp.isPlaying) mp.currentPosition.toLong() else -1L
        } catch (e: Exception) {
            -1L
        }
        if (physicalPos < 0L) return

        val effectiveDuration = if (track.isVirtualScene) {
            track.duration
        } else {
            val mpDur = try { mp.duration.toLong() } catch (e: Exception) { 0L }
            if (mpDur > 0) mpDur else track.duration
        }
        if (effectiveDuration <= 0L) return

        val numSegments = track.getAdaptiveNumSegments()
        if (numSegments <= 0) return

        val prevPhys = lastTrackedPositionMs ?: physicalPos
        val nowWall = android.os.SystemClock.elapsedRealtime()
        val maxContinuousDelta = computeMaxContinuousDeltaMs(nowWall)

        val currentVirtualOrPhys = if (track.isVirtualScene) {
            (physicalPos - track.startOffsetMs).coerceIn(0L, effectiveDuration)
        } else {
            physicalPos.coerceIn(0L, effectiveDuration)
        }
        val prevVirtualOrPhys = if (track.isVirtualScene) {
            (prevPhys - track.startOffsetMs).coerceIn(0L, effectiveDuration)
        } else {
            prevPhys.coerceIn(0L, effectiveDuration)
        }

        val deltaPos = currentVirtualOrPhys - prevVirtualOrPhys
        val bitSet = getOrInitActiveBitSet(track.id, track.listenedSegments)
        var hadNewSegments = false
        if (deltaPos in 0L..maxContinuousDelta) {
            val fromSeg = ((prevVirtualOrPhys * numSegments) / effectiveDuration).toInt().coerceIn(0, numSegments - 1)
            val toSeg = ((currentVirtualOrPhys * numSegments) / effectiveDuration).toInt().coerceIn(0, numSegments - 1)
            for (s in fromSeg..toSeg) {
                if (!bitSet.get(s)) {
                    bitSet.set(s)
                    hadNewSegments = true
                }
            }
        }
        setLastTrackedPosition(physicalPos)
        _currentPosition.value = currentVirtualOrPhys
        if (hadNewSegments) {
            val serialized = serializeBitSet(bitSet)
            val updated = track.copy(lastPosition = currentVirtualOrPhys, listenedSegments = serialized)
            currentTrackValue = updated
            _currentTrack.value = updated
            coroutineScope.launch(Dispatchers.IO) {
                updateTrackState { t ->
                    if (t.id == track.id) t.copy(lastPosition = currentVirtualOrPhys, listenedSegments = serialized) else t
                }
            }
        }
    }

    private fun flushContinuousSegmentsBeforeCompletion() {
        if (isSeeking) return
        val track = currentTrackValue ?: return
        val effectiveDuration = if (track.isVirtualScene) {
            track.duration
        } else {
            val mpDur = try { mediaPlayer?.duration?.toLong() ?: 0L } catch (e: Exception) { 0L }
            if (mpDur > 0) mpDur else (track.duration.takeIf { it > 0 } ?: _duration.value)
        }
        if (effectiveDuration <= 0L) return

        val numSegments = track.getAdaptiveNumSegments()
        if (numSegments <= 0) return

        val lastPos = lastTrackedPositionMs ?: return
        val prevEffectivePos = if (track.isVirtualScene) {
            (lastPos - track.startOffsetMs).coerceIn(0L, effectiveDuration)
        } else {
            lastPos.coerceIn(0L, effectiveDuration)
        }

        val nowWall = android.os.SystemClock.elapsedRealtime()
        val maxContinuousDelta = computeMaxContinuousDeltaMs(nowWall)
        val remainingToEnd = effectiveDuration - prevEffectivePos

        if (remainingToEnd in 0L..maxContinuousDelta) {
            val bitSet = getOrInitActiveBitSet(track.id, track.listenedSegments)
            val fromSeg = ((prevEffectivePos * numSegments) / effectiveDuration).toInt().coerceIn(0, numSegments - 1)
            val finalSeg = numSegments - 1
            var hadNewSegments = false
            for (s in fromSeg..finalSeg) {
                if (!bitSet.get(s)) {
                    bitSet.set(s)
                    hadNewSegments = true
                }
            }
            val endPhysicalPos = if (track.isVirtualScene) (track.startOffsetMs + effectiveDuration) else effectiveDuration
            setLastTrackedPosition(endPhysicalPos)
            if (hadNewSegments) {
                val serialized = serializeBitSet(bitSet)
                val updated = track.copy(listenedSegments = serialized)
                currentTrackValue = updated
                _currentTrack.value = updated
            }
        }
    }

    internal suspend fun updateTrackState(transform: (AudioTrack) -> AudioTrack) {
        trackUpdateMutex.withLock {
            val track = currentTrackValue ?: return@withLock
            var updated = transform(track)
            if (activeTrackSegmentTrackId == updated.id && !activeTrackSegmentsBitSet.isEmpty && updated.listenedSegments.isNotEmpty()) {
                val mergedBitSet = getOrInitActiveBitSet(updated.id, updated.listenedSegments)
                val mergedSerialized = serializeBitSet(mergedBitSet)
                if (updated.listenedSegments != mergedSerialized) {
                    updated = updated.copy(listenedSegments = mergedSerialized)
                }
            }
            if (updated != track) {
                repository?.updateTrack(updated)
                currentTrackValue = updated
                _currentTrack.value = updated
                val index = currentQueue.indexOfFirst { it.id == updated.id }
                if (index != -1 && currentQueue[index] != updated) {
                    withContext(Dispatchers.Main) {
                        val qIdx = currentQueue.indexOfFirst { it.id == updated.id }
                        if (qIdx != -1) {
                            val updatedQueue = currentQueue.toMutableList()
                            updatedQueue[qIdx] = updated
                            currentQueue = updatedQueue
                        }
                    }
                }
            }
        }
    }

    // Dynamic continuous segments tracker maintaining unbroken, high-fidelity updates
    internal fun startProgressTracking() {
        progressTrackingJob?.cancel()
        try {
            mediaPlayer?.let { mp ->
                if (try { mp.isPlaying } catch (e: Exception) { false }) {
                    val curPos = try { mp.currentPosition.toLong() } catch (e: Exception) { null }
                    if (curPos != null) {
                        setLastTrackedPosition(curPos)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "startProgressTracking initial pos error: ${e.message}")
        }
        if (lastActivePlayTimestamp <= 0L) {
            lastActivePlayTimestamp = System.currentTimeMillis()
        }
        progressTrackingJob = coroutineScope.launch(Dispatchers.IO) {
            while (isActive) {
                try {
                    val mp = mediaPlayer
                    val isPlaying = try { mp?.isPlaying == true } catch (e: Exception) { false }
                    if (mp != null && isPlaying) {
                        accumulateActiveListeningTime()
                        if (sessionActualListeningMs >= 3000L) {
                            persistCurrentPlayListeningTime()
                        }
                        val physicalPos = try { mp.currentPosition.toLong() } catch (e: Exception) { -1L }
                        if (physicalPos < 0L) {
                            delay(200)
                            continue
                        }

                        if (isSeeking) {
                            if (System.currentTimeMillis() - lastSeekTimestamp > 1200L) {
                                isSeeking = false
                            } else {
                                delay(100)
                                continue
                            }
                        }

                        val track = currentTrackValue
                        val nowWall = android.os.SystemClock.elapsedRealtime()
                        val maxContinuousDelta = computeMaxContinuousDeltaMs(nowWall)

                        if (track != null && track.isVirtualScene) {
                            val virtualPos = (physicalPos - track.startOffsetMs).coerceIn(0L, track.duration)
                            _currentPosition.value = virtualPos
                            _duration.value = track.duration
                            updateActiveSubtitleCue(physicalPos)

                            // Process segment logic for virtual scene BEFORE checking end boundary or practice pause
                            val effectiveDuration = track.duration
                            if (effectiveDuration > 0) {
                                val numSegments = track.getAdaptiveNumSegments()
                                val prevPhysical = lastTrackedPositionMs ?: physicalPos
                                val prevVirtual = (prevPhysical - track.startOffsetMs).coerceIn(0L, effectiveDuration)

                                val currentSeg = ((virtualPos * numSegments) / effectiveDuration).toInt().coerceIn(0, numSegments - 1)
                                val bitSet = getOrInitActiveBitSet(track.id, track.listenedSegments)
                                var hadNewSegments = false

                                val deltaPos = virtualPos - prevVirtual
                                if (!isSeeking && deltaPos in 0L..maxContinuousDelta) {
                                    val fromSeg = ((prevVirtual * numSegments) / effectiveDuration).toInt().coerceIn(0, numSegments - 1)
                                    for (s in fromSeg..currentSeg) {
                                        if (!bitSet.get(s)) {
                                            bitSet.set(s)
                                            hadNewSegments = true
                                        }
                                    }
                                }
                                setLastTrackedPosition(physicalPos)

                                if (hadNewSegments || Math.abs(track.lastPosition - virtualPos) >= 2000L) {
                                    val serialized = serializeBitSet(bitSet)
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
                                    val shouldTriggerThreshold = when {
                                        initialSessionProgressPercent >= 100 -> false
                                        initialSessionProgressPercent >= completionThreshold -> progressPercent >= 100
                                        else -> progressPercent >= completionThreshold
                                    }
                                    if (shouldTriggerThreshold) {
                                        handleThresholdReached()
                                    }
                                    checkAndTriggerTaskSpecificProgress(currentTrack, progressPercent)
                                }
                            }

                            if (_isPracticeMode.value && !_isPracticePausing.value && isPlaying) {
                                checkPracticeSegmentBoundary(virtualPos)
                            }
                            val endBoundary = track.endOffsetMs ?: (track.startOffsetMs + track.duration)
                            if (physicalPos >= endBoundary - 120L) {
                                flushContinuousSegmentsBeforeCompletion()
                                withContext(Dispatchers.Main) {
                                    try {
                                        mediaPlayer?.pause()
                                    } catch (e: Exception) {}
                                    handlePhysicalEndOfTrack()
                                }
                                return@launch
                            }
                        } else {
                            _currentPosition.value = physicalPos
                            updateActiveSubtitleCue(physicalPos)
                            
                            // Process segment logic
                            val effectiveDuration = if (mp.duration > 0) mp.duration.toLong() else track?.duration ?: 0L
                            if (track != null && effectiveDuration > 0) {
                                val numSegments = track.getAdaptiveNumSegments()
                                val prevPos = lastTrackedPositionMs ?: physicalPos
                                
                                val currentSeg = ((physicalPos * numSegments) / effectiveDuration).toInt().coerceIn(0, numSegments - 1)
                                val bitSet = getOrInitActiveBitSet(track.id, track.listenedSegments)
                                var hadNewSegments = false
                                
                                val deltaPos = physicalPos - prevPos
                                if (!isSeeking && deltaPos in 0L..maxContinuousDelta) {
                                    val fromSeg = ((prevPos * numSegments) / effectiveDuration).toInt().coerceIn(0, numSegments - 1)
                                    for (s in fromSeg..currentSeg) {
                                        if (!bitSet.get(s)) {
                                            bitSet.set(s)
                                            hadNewSegments = true
                                        }
                                    }
                                }
                                setLastTrackedPosition(physicalPos)
                                
                                // Atomically update track state with new segments and lastPosition directly on IO
                                if (hadNewSegments || Math.abs(track.lastPosition - physicalPos) >= 2000L) {
                                    val serialized = serializeBitSet(bitSet)
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
                                    val shouldTriggerThreshold = when {
                                        initialSessionProgressPercent >= 100 -> false
                                        initialSessionProgressPercent >= completionThreshold -> progressPercent >= 100
                                        else -> progressPercent >= completionThreshold
                                    }
                                    if (shouldTriggerThreshold) {
                                        handleThresholdReached()
                                    }
                                    checkAndTriggerTaskSpecificProgress(currentTrack, progressPercent)
                                }
                            }

                            if (_isPracticeMode.value && !_isPracticePausing.value && isPlaying) {
                                checkPracticeSegmentBoundary(physicalPos)
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
        persistCurrentPlayListeningTime()
        val pos = _currentPosition.value
        coroutineScope.launch(Dispatchers.IO) {
            updateTrackState { t -> t.copy(lastPosition = pos) }
        }
    }

    private suspend fun performFullProgressReset(track: AudioTrack, resetPosition: Boolean = true) {
        activeTrackSegmentTrackId = null
        activeTrackSegmentsBitSet.clear()
        trackAccumulatedListeningMsMap[track.id] = 0L
        initialSessionProgressPercent = 0
        isThresholdTriggeredForCurrentSession = false

        updateTrackState { t ->
            if (t.id == track.id) {
                if (resetPosition) {
                    t.copy(
                        lastPosition = 0L,
                        listenedSegments = "",
                        currentPlayActualListeningMs = 0L
                    )
                } else {
                    t.copy(
                        listenedSegments = "",
                        currentPlayActualListeningMs = 0L
                    )
                }
            } else {
                t
            }
        }
    }

    // =========================================================================
    // @LOCKED: Play Count Increment & Threshold Handling - STRICT FREEZE
    // DO NOT MODIFY OR REFACTOR THIS BLOCK WITHOUT EXPLICIT PERMISSION IN PROMPT
    // =========================================================================
    private fun handleThresholdReached() {
        if (isThresholdTriggeredForCurrentSession) return
        isThresholdTriggeredForCurrentSession = true

        coroutineScope.launch(Dispatchers.IO) {
            // 1. Increment playCount safely
            updateTrackState { t -> t.copy(playCount = t.playCount + 1) }
            
            val track = currentTrackValue ?: return@launch

            // 2. Sync / save into History with active tasks snapshot and exact actual listening & speaking time
            syncCurrentSessionHistory(isFinishing = false)

            // 3. Update all active tasks containing this file automatically
            updateAssociatedTasks(track.id)
        }
    }
    // =========================================================================
    // @END_LOCKED: Play Count Increment & Threshold Handling
    // =========================================================================

    // =========================================================================
    // @LOCKED: End-of-File Lifecycle & Progress Reset - STRICT FREEZE
    // DO NOT MODIFY OR REFACTOR THIS BLOCK WITHOUT EXPLICIT PERMISSION IN PROMPT
    // =========================================================================
    private fun handlePhysicalEndOfTrack() {
        val track = currentTrackValue ?: return
        stopProgressTracking()
        coroutineScope.launch(Dispatchers.IO) {
            // First check if there is a sleep at end of file timer set
            val isSleepAtEnd = (sleepTimerOption == -1)
            if (isSleepAtEnd) {
                sleepTimerOption = 0
                _sleepTimeRemaining.value = 0
            }

            accumulateActiveListeningTime()
            persistCurrentPlayListeningTime()

            // If track was playing continuously to the physical end, mark ONLY the final segments (never a range across seeks)
            val effectiveDuration = if (track.isVirtualScene) track.duration else (track.duration.takeIf { it > 0 } ?: _duration.value)
            var currentTrack = currentTrackValue ?: track
            val numSegments = currentTrack.getAdaptiveNumSegments()
            val bitSet = getOrInitActiveBitSet(track.id, currentTrack.listenedSegments)

            if (effectiveDuration > 0 && numSegments > 0) {
                val finalSeg = numSegments - 1
                val lastPos = lastTrackedPositionMs
                val effectiveLastPos = if (lastPos != null) {
                    if (track.isVirtualScene) (lastPos - track.startOffsetMs).coerceIn(0L, effectiveDuration) else lastPos.coerceIn(0L, effectiveDuration)
                } else null

                // Only mark the final segment(s) if the player was playing continuously near the physical end (within 2000ms of end)
                if (effectiveLastPos != null && (effectiveDuration - effectiveLastPos) in 0L..2000L) {
                    val fromSeg = ((effectiveLastPos * numSegments) / effectiveDuration).toInt().coerceIn(0, finalSeg)
                    var hadNewEndSegments = false
                    for (s in fromSeg..finalSeg) {
                        if (!bitSet.get(s)) {
                            bitSet.set(s)
                            hadNewEndSegments = true
                        }
                    }
                    if (hadNewEndSegments) {
                        val serialized = serializeBitSet(bitSet)
                        val updated = currentTrack.copy(listenedSegments = serialized)
                        currentTrack = updated
                        updateTrackState { t ->
                            if (t.id == track.id) updated else t
                        }
                    }
                }
            }

            // Compute progress BEFORE clearing activeTrackSegmentsBitSet (since bitSet references activeTrackSegmentsBitSet)
            val progressPercent = if (numSegments > 0) {
                ((bitSet.cardinality() * 100) / numSegments).coerceIn(0, 100)
            } else {
                currentTrack.getProgressPercent()
            }

            activeTrackSegmentTrackId = null
            activeTrackSegmentsBitSet.clear()

            // Recheck criteria: MUST reach completion threshold OR 100% progress!
            // Reaching the physical end of the file ALONE does NOT record history or trigger completion.
            val criteriaMet = (progressPercent >= completionThreshold) || (progressPercent >= 100)

            if (criteriaMet) {
                val shouldTrigger = when {
                    initialSessionProgressPercent >= 100 -> false
                    initialSessionProgressPercent >= completionThreshold -> progressPercent >= 100
                    else -> progressPercent >= completionThreshold
                }
                if (shouldTrigger && !isThresholdTriggeredForCurrentSession) {
                    isThresholdTriggeredForCurrentSession = true
                    updateTrackState { t -> t.copy(playCount = t.playCount + 1) }
                    updateAssociatedTasks(currentTrack.id)
                }
                syncCurrentSessionHistory(isFinishing = true)
            } else {
                // Criteria NOT met: do NOT call handleThresholdReached(), do NOT log into PlaybackHistory, do NOT increment playCount
                syncCurrentSessionHistory(isFinishing = true)
            }

            // Reset progress ONLY when BOTH milestones are met: (1) 100% progress AND (2) physical end of the file
            val isFullyListened = progressPercent >= 100
            if (isFullyListened) {
                performFullProgressReset(currentTrack, resetPosition = true)
            } else {
                // Keep partial progress intact, only rewind playhead to beginning
                initialSessionProgressPercent = progressPercent
                isThresholdTriggeredForCurrentSession = false
                updateTrackState { t ->
                    if (t.id == track.id) t.copy(lastPosition = 0L) else t
                }
            }
            // =========================================================================
            // @END_LOCKED: End-of-File Lifecycle & Progress Reset
            // =========================================================================

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
                    releasePlaybackWakeLock()

                    // Reset seek position cleanly to start so future play restarts from beginning
                    val seekReset = if (track.isVirtualScene) track.startOffsetMs else 0L
                    try {
                        mediaPlayer?.let { performSeek(it, seekReset) }
                    } catch (e: Exception) {}
                    setLastTrackedPosition(if (track.isVirtualScene) track.startOffsetMs else 0L)
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
                val customThreshold = task.customThreshold ?: continue
                if (progressPercent >= customThreshold) {
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

    // Notifications and Services
    internal const val NOTIFICATION_ID = 404

    // --- PRACTICE MODE (Silence Detection & Imitation Pauses) ---

    data class PracticeTrackSourcesInfo(
        val hasManualCuts: Boolean,
        val manualCutsCount: Int,
        val hasSubtitles: Boolean,
        val subtitlesCount: Int,
        val activeSource: String,
        val activeSegmentsCount: Int
    )

    internal var practiceLastPlayedSegmentStartMs = 0L
    internal var practiceRepeatSegmentStartMs = 0L

    // --- Delegated Subtitle Controller Operations ---
    internal fun loadSubtitlesForTrack(track: AudioTrack) = SubtitleController.loadSubtitlesForTrack(track)
    internal fun updateActiveSubtitleCue(positionMs: Long) = SubtitleController.updateActiveSubtitleCue(positionMs)
    fun getCurrentSubtitlesRawText(): String = SubtitleController.getCurrentSubtitlesRawText()
    fun setSubtitleContentForCurrentTrack(content: String) = SubtitleController.setSubtitleContentForCurrentTrack(content)
    fun setSubtitleContentForTrack(targetTrackId: Long, content: String) = SubtitleController.setSubtitleContentForTrack(targetTrackId, content)
    fun setSubtitleFileForCurrentTrack(filePath: String) = SubtitleController.setSubtitleFileForCurrentTrack(filePath)
    fun clearSubtitlesForCurrentTrack() = SubtitleController.clearSubtitlesForCurrentTrack()
    fun adjustSubtitleOffset(deltaMs: Long) = SubtitleController.adjustSubtitleOffset(deltaMs)
    fun resetSubtitleOffset() = SubtitleController.resetSubtitleOffset()
    fun setSubtitleFontSize(size: Float) = SubtitleController.setSubtitleFontSize(size)
    fun hasAvailableSubtitles(track: AudioTrack): Boolean = SubtitleController.hasAvailableSubtitles(track)
    fun getOrParseCuesForTrack(track: AudioTrack): List<SubtitleCue> = SubtitleController.getOrParseCuesForTrack(track)

    // --- Delegated Notification & MediaSession Controller Operations ---
    fun handleMediaKeyEvent(keyEvent: KeyEvent): Boolean = PlaybackNotificationController.handleMediaKeyEvent(keyEvent)
    fun setHeadsetSettings(enabled: Boolean, action: String) = PlaybackNotificationController.setHeadsetSettings(enabled, action)
    internal fun updateMediaSessionPlaybackState() = PlaybackNotificationController.updateMediaSessionPlaybackState()
    internal fun updateMediaSessionMetadata(track: AudioTrack?) = PlaybackNotificationController.updateMediaSessionMetadata(track)
    internal fun startPlaybackService() = PlaybackNotificationController.startPlaybackService()
    internal fun stopPlaybackService() = PlaybackNotificationController.stopPlaybackService()
    internal fun isDarkThemeActive(): Boolean = PlaybackNotificationController.isDarkThemeActive()
    fun buildNotification(context: Context? = appContext): android.app.Notification? = PlaybackNotificationController.buildNotification(context)
    internal fun showNotification() = PlaybackNotificationController.showNotification()
    fun updateNotification() = PlaybackNotificationController.updateNotification()
    internal fun cancelNotification() = PlaybackNotificationController.cancelNotification()
    internal fun sendTaskCompletionNotification(task: Task) = PlaybackNotificationController.sendTaskCompletionNotification(task)

    // --- Delegated Playback Queue Controller Operations ---
    fun removeTracksFromQueue(indices: List<Int>) = PlaybackQueueController.removeTracksFromQueue(indices)
    fun removeTracksByIds(trackIds: Set<Long>) = PlaybackQueueController.removeTracksByIds(trackIds)
    fun clearQueue() = PlaybackQueueController.clearQueue()
    fun playNextTrack() = PlaybackQueueController.playNextTrack()
    fun playPreviousTrack() = PlaybackQueueController.playPreviousTrack()
    fun addTrackToQueueNext(track: AudioTrack) = PlaybackQueueController.addTrackToQueueNext(track)
    fun addTracksToQueueNext(tracks: List<AudioTrack>) = PlaybackQueueController.addTracksToQueueNext(tracks)
    internal fun saveQueueToPreferences(queue: List<AudioTrack>) = PlaybackQueueController.saveQueueToPreferences(queue)
    internal fun saveCurrentTrackToPreferences(trackId: Long?) = PlaybackQueueController.saveCurrentTrackToPreferences(trackId)
    internal fun restoreQueueAndTrack() = PlaybackQueueController.restoreQueueAndTrack()
}
