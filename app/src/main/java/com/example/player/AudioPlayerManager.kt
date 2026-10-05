package com.example.player

import android.content.Context
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.support.v4.media.session.MediaSessionCompat
import android.util.Log
import android.view.KeyEvent
import com.example.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

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
    internal var sleepTimerOption = 0 // 0=off, 15, 30, 35, 60, -1=end of file

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

    val isSubtitlesEnabled = MutableStateFlow(true)
    val subtitleOffsetMs = MutableStateFlow(0L)
    val subtitleFontSize = MutableStateFlow(16f)
    val showTimestampsInSubtitles = MutableStateFlow(true)

    // Video Playback Surface State
    internal var activeSurface: android.view.Surface? = null
    internal var activeSurfaceHolder: android.view.SurfaceHolder? = null
    private val _videoDimensions = MutableStateFlow<Pair<Int, Int>?>(null)
    val videoDimensions: StateFlow<Pair<Int, Int>?> = _videoDimensions.asStateFlow()

    private val _isVideoTrack = MutableStateFlow(false)
    val isVideoTrack: StateFlow<Boolean> = _isVideoTrack.asStateFlow()

    internal val _isInPipMode = MutableStateFlow(false)
    val isInPipMode: StateFlow<Boolean> = _isInPipMode.asStateFlow()

    // Headset & Media Buttons Control State
    internal var mediaSession: MediaSessionCompat? = null
    val mediaSessionToken: MediaSessionCompat.Token?
        get() = mediaSession?.sessionToken

    fun getMediaSession(): MediaSessionCompat? = mediaSession

    val isHeadsetControlsEnabled = MutableStateFlow(true)
    val headsetMultiClickAction = MutableStateFlow("NEXT_PREV") // "NEXT_PREV" or "SKIP_SECONDS"

    internal var headsetClickCount = 0
    internal val headsetClickHandler = Handler(Looper.getMainLooper())
    internal val headsetClickRunnable: Runnable
        get() = PlaybackSystemController.headsetClickRunnable

    // Preferences & Settings loaded
    internal var completionThreshold = 90 // default 90%
    internal var skipTimeSeconds = 10 // default 10 seconds
    internal var isThresholdTriggeredForCurrentSession = false
    internal var initialSessionProgressPercent = 0
    internal val completedTaskIdsForCurrentSession = java.util.Collections.synchronizedSet(mutableSetOf<Long>())

    @Volatile
    internal var isSeeking = false
    @Volatile
    internal var lastSeekTimestamp = 0L

    // Active listening stopwatch (measures exact wall-clock listening duration while isPlaying and in practice pauses)
    internal var sessionActualListeningMs: Long = 0L
    internal var lastActivePlayTimestamp: Long = 0L
    internal var lastPracticePauseTimestamp: Long = 0L
    internal var currentSessionHistoryId: Long? = null
    // Thread-safe map tracking accumulated listening time (ms) for the current play in progress of each track across sessions
    internal val trackAccumulatedListeningMsMap = java.util.concurrent.ConcurrentHashMap<Long, Long>()

    // In-memory cached bitset of listened segments for the active track to eliminate string parsing/splitting on playback ticks
    internal var activeTrackSegmentTrackId: Long? = null
    internal val activeTrackSegmentsBitSet = java.util.BitSet(100)
    internal var playbackWakeLock: android.os.PowerManager.WakeLock? = null

    internal val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    internal var progressTrackingJob: Job? = null

    internal var appContext: Context? = null
    internal var repository: AppRepository? = null

    // Audio Focus and Noisy broadcast
    internal var audioManager: AudioManager? = null
    internal var audioFocusRequest: AudioFocusRequest? = null
    @Volatile
    internal var resumeOnFocusGain: Boolean = false
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
            PlaybackSystemController.registerSystemReceiversAndSession(context)
        }

        // Only restore previous track from preferences if nothing is currently playing and no track is active
        if (!_isPlaying.value && mediaPlayer == null && currentTrackValue == null && _currentTrack.value == null) {
            restoreQueueAndTrack()
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

    fun getCompletionThreshold() = completionThreshold
    fun getSkipTimeSeconds() = skipTimeSeconds

    // =========================================================================
    // @LOCKED: Track Transition Lifecycle & Virtual Scene Initialization - STRICT FREEZE
    // DO NOT MODIFY OR REFACTOR THIS BLOCK WITHOUT EXPLICIT PERMISSION IN PROMPT
    // =========================================================================
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

            // Stop background progress tracking immediately so old ticks cannot race with new track setup
            stopProgressTracking()

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
            setLastTrackedPosition(null)

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
            _currentPosition.value = initialTrack.lastPosition.coerceAtLeast(0L)
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

                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "MediaPlayer error (what=$what, extra=$extra) for track: ${track.filePath}")
                    coroutineScope.launch(Dispatchers.Main) {
                        stop()
                        appContext?.let { ctx ->
                            android.widget.Toast.makeText(
                                ctx,
                                com.example.ui.Loc.getText("playback_error_toast"),
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                    true
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
            val baseTrack = currentTrackValue?.takeIf { it.id == initialTrack.id } ?: initialTrack
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
    // =========================================================================
    // @END_LOCKED: Track Transition Lifecycle & Virtual Scene Initialization
    // =========================================================================

    fun resume() {
        resumeOnFocusGain = false
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

    fun pause(abandonFocus: Boolean = true) {
        if (abandonFocus) {
            resumeOnFocusGain = false
        }
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
        if (abandonFocus) {
            abandonAudioFocus()
        }
        updateMediaSessionPlaybackState()
        showNotification()

        // Persist progress position and active listening time inside database instantly on pause
        persistCurrentPlayListeningTime()
        saveCurrentPositionProgress()
        syncCurrentSessionHistory(isFinishing = false)
    }

    fun stop() {
        resumeOnFocusGain = false
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

    // =========================================================================
    // @LOCKED: Virtual Scene & Physical Seek Math - STRICT FREEZE
    // DO NOT MODIFY OR REFACTOR THIS BLOCK WITHOUT EXPLICIT PERMISSION IN PROMPT
    // =========================================================================
    internal fun performSeek(mp: MediaPlayer, targetMs: Long) {
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
    // =========================================================================
    // @END_LOCKED: Virtual Scene & Physical Seek Math
    // =========================================================================

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

    internal val trackUpdateMutex = Mutex()
    @Volatile
    internal var lastTrackedPositionMs: Long? = null
    @Volatile
    internal var lastTrackedWallClockMs: Long = 0L

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

    // --- Delegated Video Surface & Mode Controller Operations ---
    fun toggleFocusMode(): Boolean = VideoSurfaceController.toggleFocusMode()
    fun toggleVideoFocusMode(): Boolean = VideoSurfaceController.toggleFocusMode()
    fun setFocusMode(enabled: Boolean) = VideoSurfaceController.setFocusMode(enabled)
    fun setVideoFocusMode(enabled: Boolean) = VideoSurfaceController.setFocusMode(enabled)
    fun toggleVideoFullWidth(): Boolean = VideoSurfaceController.toggleVideoFullWidth()
    fun setVideoFullWidth(enabled: Boolean) = VideoSurfaceController.setVideoFullWidth(enabled)
    fun toggleVideoSubtitleMode(): VideoSubtitleMode = VideoSurfaceController.toggleVideoSubtitleMode()
    fun setInPipMode(inPip: Boolean) = VideoSurfaceController.setInPipMode(inPip)
    fun attachSurface(surface: android.view.Surface?) = VideoSurfaceController.attachSurface(surface)
    fun attachSurfaceHolder(holder: android.view.SurfaceHolder?) = VideoSurfaceController.attachSurfaceHolder(holder)
    fun setPracticeSettings(source: String, multiplier: Float) = VideoSurfaceController.setPracticeSettings(source, multiplier)
    fun setSilenceSettings(sensitivity: String, minDurationMs: Long, paddingMs: Long) = VideoSurfaceController.setSilenceSettings(sensitivity, minDurationMs, paddingMs)

    // --- Delegated System Integration (AudioFocus & WakeLock) Operations ---
    internal fun acquirePlaybackWakeLock() = PlaybackSystemController.acquirePlaybackWakeLock()
    internal fun releasePlaybackWakeLock() = PlaybackSystemController.releasePlaybackWakeLock()
    internal fun requestAudioFocus(): Boolean = PlaybackSystemController.requestAudioFocus()
    internal fun abandonAudioFocus() = PlaybackSystemController.abandonAudioFocus()

    // --- Delegated Progress Tracking & @LOCKED Lifecycle Operations ---
    internal fun parseSegmentsIntoBitSet(rawSegments: String, targetBitSet: java.util.BitSet) = PlaybackProgressEngine.parseSegmentsIntoBitSet(rawSegments, targetBitSet)
    internal fun getOrInitActiveBitSet(trackId: Long, rawSegments: String): java.util.BitSet = PlaybackProgressEngine.getOrInitActiveBitSet(trackId, rawSegments)
    internal fun serializeBitSet(bitSet: java.util.BitSet): String = PlaybackProgressEngine.serializeBitSet(bitSet)
    internal fun accumulateActiveListeningTime() = PlaybackProgressEngine.accumulateActiveListeningTime()
    internal fun accumulatePracticePauseTime() = PlaybackProgressEngine.accumulatePracticePauseTime()
    fun persistCurrentPlayListeningTime() = PlaybackProgressEngine.persistCurrentPlayListeningTime()
    internal fun setLastTrackedPosition(posMs: Long?) = PlaybackProgressEngine.setLastTrackedPosition(posMs)
    internal fun computeMaxContinuousDeltaMs(nowWallMs: Long = android.os.SystemClock.elapsedRealtime()): Long = PlaybackProgressEngine.computeMaxContinuousDeltaMs(nowWallMs)
    internal fun flushContinuousSegmentsOnEvent() = PlaybackProgressEngine.flushContinuousSegmentsOnEvent()
    internal fun flushContinuousSegmentsBeforeCompletion() = PlaybackProgressEngine.flushContinuousSegmentsBeforeCompletion()
    internal suspend fun updateTrackState(transform: (AudioTrack) -> AudioTrack) = PlaybackProgressEngine.updateTrackState(transform)
    internal fun startProgressTracking() = PlaybackProgressEngine.startProgressTracking()
    internal fun stopProgressTracking() = PlaybackProgressEngine.stopProgressTracking()
    fun saveCurrentPositionProgress() = PlaybackProgressEngine.saveCurrentPositionProgress()
    internal suspend fun performFullProgressReset(track: AudioTrack, resetPosition: Boolean = true) = PlaybackProgressEngine.performFullProgressReset(track, resetPosition)
    internal fun handleThresholdReached() = PlaybackProgressEngine.handleThresholdReached()
    internal fun handlePhysicalEndOfTrack() = PlaybackProgressEngine.handlePhysicalEndOfTrack()

    // --- Delegated Task & Session History Controller Operations ---
    internal fun syncCurrentSessionHistory(isFinishing: Boolean = false) = PlaybackTaskHistoryController.syncCurrentSessionHistory(isFinishing)
    internal fun checkAndTriggerTaskSpecificProgress(track: AudioTrack, progressPercent: Int) = PlaybackTaskHistoryController.checkAndTriggerTaskSpecificProgress(track, progressPercent)
    internal suspend fun updateAssociatedTasks(trackId: Long) = PlaybackTaskHistoryController.updateAssociatedTasks(trackId)

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
