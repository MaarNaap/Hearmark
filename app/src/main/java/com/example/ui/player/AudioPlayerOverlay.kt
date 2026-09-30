package com.example.ui

import android.app.Activity
import android.content.pm.ActivityInfo
import android.graphics.SurfaceTexture
import android.view.Surface
import android.view.TextureView
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.*
import com.example.player.*
import com.example.ui.theme.*
import kotlinx.coroutines.delay

// Reusable Video Player View backed by TextureView with seamless Surface lifecycle
@Composable
fun VideoPlayerSurface(
    modifier: Modifier = Modifier
) {
    val isPlayingState by AudioPlayerManager.isPlaying.collectAsStateWithLifecycle()
    AndroidView(
        factory = { ctx ->
            TextureView(ctx).apply {
                keepScreenOn = isPlayingState
                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                        AudioPlayerManager.attachSurface(Surface(surface))
                    }
                    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}
                    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                        AudioPlayerManager.attachSurface(null)
                        return true
                    }
                    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
                }
                if (isAvailable) {
                    surfaceTexture?.let { AudioPlayerManager.attachSurface(Surface(it)) }
                }
            }
        },
        update = { view ->
            view.keepScreenOn = isPlayingState
            if (view.isAvailable) {
                view.surfaceTexture?.let { AudioPlayerManager.attachSurface(Surface(it)) }
            }
        },
        modifier = modifier
    )
}

// --- SUB-SCREEN 4: AUDIO PLAYER FULL OVERLAY SCREEN ---
@Composable
@OptIn(ExperimentalFoundationApi::class)
fun AudioPlayerOverlay(
    track: AudioTrack,
    viewModel: AppViewModel,
    dismiss: () -> Unit,
    onQuickAddTaskPrompt: (Long) -> Unit,
    onNavigateToFolder: (Long) -> Unit,
    onNavigateToPlaylist: (Long) -> Unit,
    onNavigateToTask: (com.example.data.Task) -> Unit,
    onQuickAddNotePrompt: (Long, Long, Long, String) -> Unit = { _, _, _, _ -> },
    onOpenNotes: (List<Note>) -> Unit = {}
) {
    var isFullScreenVideo by remember { mutableStateOf(false) }

    val durationState by AudioPlayerManager.duration.collectAsStateWithLifecycle()
    val isPlayingState by AudioPlayerManager.isPlaying.collectAsStateWithLifecycle()
    val playPositionState by AudioPlayerManager.currentPosition.collectAsStateWithLifecycle()
    val speedState by AudioPlayerManager.playbackSpeed.collectAsStateWithLifecycle()
    val sleepTimerState by AudioPlayerManager.sleepTimeRemaining.collectAsStateWithLifecycle()

    val isPracticeMode by AudioPlayerManager.isPracticeMode.collectAsStateWithLifecycle()
    val isPracticeAnalyzing by AudioPlayerManager.isPracticeAnalyzing.collectAsStateWithLifecycle()
    val isPracticePausing by AudioPlayerManager.isPracticePausing.collectAsStateWithLifecycle()
    val practicePauseRemaining by AudioPlayerManager.practicePauseRemainingSeconds.collectAsStateWithLifecycle()
    val practiceSegments by AudioPlayerManager.currentPracticeSegments.collectAsStateWithLifecycle()

    val subtitlesCuesState by AudioPlayerManager.subtitlesCues.collectAsStateWithLifecycle()
    val activeSubtitleCueState by AudioPlayerManager.activeSubtitleCue.collectAsStateWithLifecycle()
    val isSubtitlesEnabledState by AudioPlayerManager.isSubtitlesEnabled.collectAsStateWithLifecycle()
    val videoSubtitleModeState by AudioPlayerManager.videoSubtitleMode.collectAsStateWithLifecycle()
    val subtitleOffsetMsState by AudioPlayerManager.subtitleOffsetMs.collectAsStateWithLifecycle()
    val subtitleFontSizeState by AudioPlayerManager.subtitleFontSize.collectAsStateWithLifecycle()
    val showTimestampsInSubtitlesState by AudioPlayerManager.showTimestampsInSubtitles.collectAsStateWithLifecycle()

    var showSubtitlePasteDialog by remember { mutableStateOf(false) }
    var showLiveSyncDialog by remember { mutableStateOf(false) }
    var showDeleteSubtitleConfirmDialog by remember { mutableStateOf(false) }
    var isEditingExistingSubtitles by remember { mutableStateOf(false) }
    var subtitlePasteText by remember { mutableStateOf("") }
    var showWaveformEditorDialog by remember { mutableStateOf(false) }
    var showPracticeSetupSheet by remember { mutableStateOf(false) }

    val isVideoTrackState by AudioPlayerManager.isVideoTrack.collectAsStateWithLifecycle()
    val isTrackVideo = remember(track.filePath, isVideoTrackState) {
        SubtitleParser.isVideoFile(track.filePath) || isVideoTrackState
    }
    val isFocusModeState by AudioPlayerManager.isFocusMode.collectAsStateWithLifecycle()
    val isDistractionFree = isFocusModeState

    BackHandler(enabled = true) {
        if (isFullScreenVideo) {
            isFullScreenVideo = false
        } else if (isDistractionFree) {
            AudioPlayerManager.setFocusMode(false)
        } else {
            dismiss()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            AudioPlayerManager.setFocusMode(false)
        }
    }
    val isInPipModeState by AudioPlayerManager.isInPipMode.collectAsStateWithLifecycle()

    var areVideoControlsVisible by remember { mutableStateOf(true) }
    var lastVideoControlsInteractionTime by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(areVideoControlsVisible, isPlayingState, lastVideoControlsInteractionTime) {
        if (areVideoControlsVisible && isPlayingState && !isPracticeMode && isTrackVideo) {
            delay(3500L)
            areVideoControlsVisible = false
        }
    }

    val videoDims by AudioPlayerManager.videoDimensions.collectAsStateWithLifecycle()
    val videoAspectRatio = remember(videoDims) {
        if (videoDims != null && videoDims!!.second > 0 && videoDims!!.first > 0) {
            videoDims!!.first.toFloat() / videoDims!!.second.toFloat()
        } else {
            16f / 9f
        }
    }
    val isHorizontalVideo = remember(videoDims) {
        if (videoDims != null && videoDims!!.first > 0 && videoDims!!.second > 0) {
            videoDims!!.first > videoDims!!.second
        } else {
            true
        }
    }

    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp.toFloat()
    val fullWidthVideoHeight = remember(screenWidthDp, videoAspectRatio) {
        (screenWidthDp / videoAspectRatio.coerceIn(1.0f, 2.4f)).dp
    }

    val context = LocalContext.current
    val pagerState = rememberPagerState(pageCount = { 2 })

    // Keep screen ON only while video is active, playing, and visible on screen (both normal and fullscreen mode)
    DisposableEffect(isTrackVideo, isPlayingState) {
        val activity = context as? Activity
        val window = activity?.window
        if (isTrackVideo && isPlayingState) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // Auto rotate screen and enable full immersive mode (hide system bars and navigation bar) on full screen
    DisposableEffect(isFullScreenVideo, isHorizontalVideo, isTrackVideo) {
        val activity = context as? Activity
        val window = activity?.window
        val insetsController = if (window != null) {
            androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
        } else {
            null
        }

        if (isInPipModeState || (isFullScreenVideo && isTrackVideo)) {
            if (isHorizontalVideo) {
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            } else {
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }

            // Hide status bar and navigation buttons for 100% immersive video view
            insetsController?.apply {
                hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            // Restore status bar and navigation buttons
            insetsController?.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            insetsController?.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        }
    }

    var areFullScreenControlsVisible by remember { mutableStateOf(true) }
    LaunchedEffect(isFullScreenVideo, areFullScreenControlsVisible, isPlayingState) {
        if (isFullScreenVideo && areFullScreenControlsVisible && isPlayingState) {
            kotlinx.coroutines.delay(3500L)
            areFullScreenControlsVisible = false
        }
    }

    val subtitleFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { selectedUri ->
            try {
                context.contentResolver.openInputStream(selectedUri)?.use { stream ->
                    val content = stream.bufferedReader().use { it.readText() }
                    AudioPlayerManager.setSubtitleContentForCurrentTrack(content)
                    Toast.makeText(context, Loc.getText("subtitles"), Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Observe all tracks to ensure single source of truth with the library
    val allTracksList by viewModel.tracks.collectAsStateWithLifecycle()
    val liveTrack = allTracksList.find { it.id == track.id } ?: AudioPlayerManager.currentTrack.collectAsStateWithLifecycle().value ?: track

    // Display total listened segments percentage
    val completedSegmentsCount = liveTrack.getListenedCount()
    val maxListenedPercent = liveTrack.getProgressPercent()

    var isDraggingState by remember { mutableStateOf(false) }
    var dragPercentState by remember { mutableStateOf(0f) }

    var relatedTasks by remember(track.id) { mutableStateOf<List<com.example.data.Task>>(emptyList()) }
    val allActiveTasks by viewModel.activeTasks.collectAsStateWithLifecycle()

    val folders by viewModel.folders.collectAsStateWithLifecycle()
    val folderOfTrack = remember(folders, track.parentFolderId, track.filePath) {
        folders.firstOrNull { it.id == track.parentFolderId }
            ?: folders.firstOrNull { track.filePath.startsWith(it.folderPath) }
    }

    LaunchedEffect(track.id, folderOfTrack, allActiveTasks) {
        val directTasks = viewModel.getActiveTasksForTrack(track.id)
        val folderTasks = folderOfTrack?.let { folder ->
            allActiveTasks.filter { it.sourceType == "FOLDER" && it.sourceId == folder.id && it.status == "ACTIVE" }
        } ?: emptyList()
        relatedTasks = (directTasks + folderTasks).distinctBy { it.id }
    }

    var playlistsOfTrack by remember(track.id) { mutableStateOf<List<com.example.data.Playlist>>(emptyList()) }
    LaunchedEffect(track.id) {
        playlistsOfTrack = viewModel.getPlaylistsForTrack(track.id)
    }

    val allNotesList by viewModel.notes.collectAsStateWithLifecycle()
    val trackNotes = remember(allNotesList, allTracksList, track.id, track.isVirtualScene, track.parentTrackId, track.startOffsetMs, track.duration) {
        if (track.isVirtualScene) {
            val sceneStart = track.startOffsetMs
            val sceneEnd = track.endOffsetMs ?: (track.startOffsetMs + track.duration)
            val parentId = track.parentTrackId ?: allTracksList.find { it.filePath == track.filePath && !it.isVirtualScene }?.id
            val parentTrack = allTracksList.find { it.id == parentId }
            allNotesList.filter { note ->
                if (note.trackId == track.id) {
                    true
                } else if (parentId != null && note.trackId == parentId) {
                    val s = note.originStartMs ?: note.startTimestampMs
                    val e = if (note.endTimestampMs > s) note.endTimestampMs else s
                    (s <= sceneEnd && e >= sceneStart) || (s in 0L..track.duration)
                } else if (note.trackName != null && (note.trackName == track.fileName || (parentTrack != null && note.trackName == parentTrack.fileName))) {
                    val s = note.originStartMs ?: note.startTimestampMs
                    val e = if (note.endTimestampMs > s) note.endTimestampMs else s
                    (s <= sceneEnd && e >= sceneStart) || (s in 0L..track.duration)
                } else {
                    false
                }
            }
        } else {
            // Parent or regular track (audio or video)
            val childSceneIds = allTracksList.filter {
                (it.isVirtualScene && it.parentTrackId == track.id) ||
                (it.isVirtualScene && it.filePath == track.filePath)
            }.map { it.id }.toSet()

            allNotesList.filter { note ->
                note.trackId == track.id ||
                (note.trackId != null && childSceneIds.contains(note.trackId)) ||
                (note.trackName != null && (note.trackName == track.fileName || note.trackName == track.filePath))
            }
        }
    }

    val effectivePhysPos = if (track.isVirtualScene) (track.startOffsetMs + playPositionState) else playPositionState

    val activeNotesForTime = remember(trackNotes, subtitlesCuesState, playPositionState, activeSubtitleCueState, effectivePhysPos) {
        val currentActiveCue = activeSubtitleCueState ?: if (subtitlesCuesState.isNotEmpty()) {
            subtitlesCuesState.find { cue ->
                val cueEnd = if (cue.endMs > cue.startMs) cue.endMs else cue.startMs + 4000L
                effectivePhysPos in cue.startMs..cueEnd
            }
        } else null

        trackNotes.filter { note ->
            val cueMatch = if (currentActiveCue != null && subtitlesCuesState.isNotEmpty()) {
                SubtitleParser.findDedicatedCueForNote(note, subtitlesCuesState) == currentActiveCue
            } else false

            val rawS = note.originStartMs ?: note.startTimestampMs
            val rawE = if (note.endTimestampMs > rawS) note.endTimestampMs else rawS + 4000L

            val timeMatch = if (track.isVirtualScene) {
                (effectivePhysPos in rawS..rawE) ||
                (playPositionState in rawS..rawE) ||
                (rawS >= track.startOffsetMs && playPositionState in (rawS - track.startOffsetMs)..(rawE - track.startOffsetMs))
            } else {
                (effectivePhysPos in rawS..rawE) || (playPositionState in rawS..rawE)
            }

            cueMatch || timeMatch
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .testTag("full_player_layout"),
        color = MaterialTheme.colorScheme.surface
    ) {
        if (isFullScreenVideo && isTrackVideo) {
            FullScreenVideoOverlay(
                track = track,
                videoAspectRatio = videoAspectRatio,
                isHorizontalVideo = isHorizontalVideo,
                videoDims = videoDims,
                videoSubtitleModeState = videoSubtitleModeState,
                activeSubtitleCueState = activeSubtitleCueState,
                subtitleFontSizeState = subtitleFontSizeState,
                areFullScreenControlsVisible = areFullScreenControlsVisible,
                isInPipModeState = isInPipModeState,
                activeNotesForTime = activeNotesForTime,
                trackNotes = trackNotes,
                subtitlesCuesState = subtitlesCuesState,
                effectivePhysPos = effectivePhysPos,
                durationState = durationState,
                playPositionState = playPositionState,
                isPlayingState = isPlayingState,
                onToggleControlsVisibility = { areFullScreenControlsVisible = !areFullScreenControlsVisible },
                onExitFullScreen = { isFullScreenVideo = false },
                onOpenNotes = onOpenNotes,
                onQuickAddNotePrompt = onQuickAddNotePrompt
            )
        } else {
            val bgGradientTop by animateColorAsState(
                targetValue = if (isDistractionFree) Color.Black else MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                animationSpec = tween(durationMillis = 300),
                label = "bgGradientTop"
            )
            val bgGradientBottom by animateColorAsState(
                targetValue = if (isDistractionFree) Color.Black else MaterialTheme.colorScheme.surface,
                animationSpec = tween(durationMillis = 300),
                label = "bgGradientBottom"
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(bgGradientTop, bgGradientBottom)
                        )
                    )
                    .navigationBarsPadding()
            ) {
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    PlayerTopBarAndFocusHeader(
                        isDistractionFree = isDistractionFree,
                        isPracticeMode = isPracticeMode,
                        track = track,
                        viewModel = viewModel,
                        pagerState = pagerState,
                        subtitlesCuesState = subtitlesCuesState,
                        activeSubtitleCueState = activeSubtitleCueState,
                        playPositionState = playPositionState,
                        durationState = durationState,
                        relatedTasks = relatedTasks,
                        dismiss = dismiss,
                        onQuickAddNotePrompt = onQuickAddNotePrompt,
                        onQuickAddTaskPrompt = onQuickAddTaskPrompt
                    )

                    // MIDDLE SWIPEABLE TAB CONTENT (HorizontalPager)
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) { page ->
                        if (page == 0) {
                            // PAGE 0: MAIN PLAYER VIEW
                            PlayerMainTabContent(
                                track = track,
                                liveTrack = liveTrack,
                                isDistractionFree = isDistractionFree,
                                isTrackVideo = isTrackVideo,
                                isPlayingState = isPlayingState,
                                isFocusModeState = isFocusModeState,
                                fullWidthVideoHeight = fullWidthVideoHeight,
                                videoAspectRatio = videoAspectRatio,
                                videoDims = videoDims,
                                videoSubtitleModeState = videoSubtitleModeState,
                                activeSubtitleCueState = activeSubtitleCueState,
                                subtitleFontSizeState = subtitleFontSizeState,
                                areVideoControlsVisible = areVideoControlsVisible,
                                activeNotesForTime = activeNotesForTime,
                                folderOfTrack = folderOfTrack,
                                playlistsOfTrack = playlistsOfTrack,
                                relatedTasks = relatedTasks,
                                dismiss = dismiss,
                                onNavigateToFolder = onNavigateToFolder,
                                onNavigateToPlaylist = onNavigateToPlaylist,
                                onNavigateToTask = onNavigateToTask,
                                onOpenNotes = onOpenNotes,
                                onToggleVideoControls = { areVideoControlsVisible = !areVideoControlsVisible },
                                onUpdateVideoInteractionTime = { lastVideoControlsInteractionTime = System.currentTimeMillis() },
                                onEnterFullScreen = { isFullScreenVideo = true }
                            )
                        } else {
                            // PAGE 1: SUBTITLES / LYRICS VIEW (Huawei Music Style Dedicated Tab)
                            SubtitlesPageContent(
                                cues = subtitlesCuesState,
                                activeCue = activeSubtitleCueState,
                                currentPositionMs = playPositionState,
                                fontSize = subtitleFontSizeState,
                                offsetMs = subtitleOffsetMsState,
                                showTimestamps = showTimestampsInSubtitlesState,
                                isInFocusOrPracticeMode = isDistractionFree || isPracticeMode,
                                onToggleTimestamps = {
                                    AudioPlayerManager.showTimestampsInSubtitles.value = !AudioPlayerManager.showTimestampsInSubtitles.value
                                },
                                onCopyAllSubtitlesClick = {
                                    val fullText = AudioPlayerManager.getCurrentSubtitlesRawText()
                                    if (fullText.isNotBlank()) {
                                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                                        val clip = android.content.ClipData.newPlainText("Subtitles", fullText)
                                        clipboard?.setPrimaryClip(clip)
                                        Toast.makeText(context, Loc.getText("subtitles_copied"), Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, Loc.getText("no_subtitles_found"), Toast.LENGTH_SHORT).show()
                                    }
                                },
                                onEditSubtitlesClick = {
                                    subtitlePasteText = AudioPlayerManager.getCurrentSubtitlesRawText()
                                    isEditingExistingSubtitles = true
                                    showSubtitlePasteDialog = true
                                },
                                onImportSrtClick = { subtitleFilePicker.launch(arrayOf("*/*")) },
                                onPasteLyricsClick = {
                                    subtitlePasteText = ""
                                    isEditingExistingSubtitles = false
                                    showSubtitlePasteDialog = true
                                },
                                onTapToSyncClick = { showLiveSyncDialog = true },
                                onDeleteSubtitlesClick = { showDeleteSubtitleConfirmDialog = true },
                                onSeekTo = { AudioPlayerManager.seekTo(it, isPhysicalTimestamp = true) },
                                onFontSizeChange = { AudioPlayerManager.setSubtitleFontSize(it) },
                                onAdjustOffset = { AudioPlayerManager.adjustSubtitleOffset(it) },
                                onResetOffset = { AudioPlayerManager.resetSubtitleOffset() },
                                onAddNoteFromCue = { cueText, startMs, endMs ->
                                    onQuickAddNotePrompt(track.id, startMs, endMs, cueText)
                                },
                                onAskAiAboutCue = { cueText, startMs, endMs ->
                                    val dur = durationState
                                    val formattedPos = if (dur > 0) "${formatDuration(startMs)} / ${formatDuration(dur)}" else formatDuration(startMs)
                                    viewModel.openChatWithContext(
                                        com.example.ai.AudioContextSummary(
                                            trackTitle = track.getDisplayTitle(),
                                            trackArtist = null,
                                            currentPositionMs = startMs,
                                            formattedPosition = formattedPos,
                                            activeSubtitleLine = cueText,
                                            activeTaskTitle = relatedTasks.firstOrNull()?.getDisplayTitle()
                                        )
                                    )
                                },
                                notes = trackNotes,
                                onOpenNotes = onOpenNotes
                            )
                        }
                    }

                    // FIXED BOTTOM CONTROLLER & UTILITIES AREA (Always visible)
                    val arePlaybackButtonsActive = !isDistractionFree || areVideoControlsVisible || isPracticeMode || !isTrackVideo
                    val bottomPlaybackAlpha by animateFloatAsState(
                        targetValue = if (arePlaybackButtonsActive) 1f else 0f,
                        animationSpec = tween(durationMillis = 300),
                        label = "bottomPlaybackAlpha"
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .graphicsLayer { alpha = bottomPlaybackAlpha }
                        ) {
                            // Practice Mode Status Row in Focus Mode (Single row with Repeat & Skip Pause)
                            if (isPracticeMode) {
                                PlayerPracticeStatusPill(
                                    track = track,
                                    isPracticePausing = isPracticePausing,
                                    practicePauseRemaining = practicePauseRemaining,
                                    onOpenPracticeSetup = { showPracticeSetupSheet = true }
                                )
                            }

                            // PROGRESS SLIDER & TIMESTAMPS
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(
                                        text = Loc.getText("max_listened_progress"),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 12.sp
                                    )
                                    Text(
                                        text = "$maxListenedPercent%",
                                        color = ColorSuccess,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 12.sp
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))

                                val currentNeedlePercent = if (isDraggingState) dragPercentState else (if (durationState > 0) playPositionState.toFloat() / durationState.toFloat() else 0f)

                                val numSegments = liveTrack.getAdaptiveNumSegments()
                                val listenedRanges = remember(liveTrack.listenedSegments, numSegments) {
                                    val bitSet = liveTrack.getListenedBitSet(numSegments)
                                    val ranges = mutableListOf<IntRange>()
                                    var start = -1
                                    var prev = -1
                                    var i = bitSet.nextSetBit(0)
                                    while (i in 0 until numSegments) {
                                        if (start == -1) {
                                            start = i
                                            prev = i
                                        } else if (i == prev + 1) {
                                            prev = i
                                        } else {
                                            ranges.add(start..prev)
                                            start = i
                                            prev = i
                                        }
                                        i = bitSet.nextSetBit(i + 1)
                                    }
                                    if (start != -1) {
                                        ranges.add(start..prev)
                                    }
                                    ranges
                                }

                                BoxWithConstraints(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(12.dp),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(5.dp)
                                            .clip(RoundedCornerShape(2.5.dp))
                                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                                    ) {
                                        val segmentsColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                                        val noteMarkerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                                        Canvas(modifier = Modifier.fillMaxSize()) {
                                            if (numSegments > 0) {
                                                val segmentWidth = size.width / numSegments
                                                for (range in listenedRanges) {
                                                    val startX = range.first * segmentWidth
                                                    val rangeWidth = (range.last - range.first + 1) * segmentWidth
                                                    drawRect(
                                                        color = segmentsColor,
                                                        topLeft = androidx.compose.ui.geometry.Offset(x = startX, y = 0f),
                                                        size = androidx.compose.ui.geometry.Size(width = rangeWidth, height = size.height)
                                                    )
                                                }
                                            }
                                            if (durationState > 0 && trackNotes.isNotEmpty()) {
                                                val markerWidth = 2.dp.toPx()
                                                for (note in trackNotes) {
                                                    val noteTargetMs = note.originStartMs
                                                        ?: SubtitleParser.findDedicatedCueForNote(note, subtitlesCuesState)?.startMs
                                                        ?: note.startTimestampMs
                                                    val relTargetMs = if (track.isVirtualScene) {
                                                        if (noteTargetMs >= track.startOffsetMs) noteTargetMs - track.startOffsetMs else noteTargetMs
                                                    } else {
                                                        noteTargetMs
                                                    }
                                                    if (relTargetMs in 0L..durationState) {
                                                        val frac = (relTargetMs.toFloat() / durationState.toFloat()).coerceIn(0f, 1f)
                                                        val noteX = frac * size.width
                                                        drawRect(
                                                            color = noteMarkerColor,
                                                            topLeft = androidx.compose.ui.geometry.Offset(x = noteX - (markerWidth / 2f), y = 0f),
                                                            size = androidx.compose.ui.geometry.Size(width = markerWidth, height = size.height)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    val thumbSize = 10.dp
                                    val safeNeedlePercent = currentNeedlePercent.coerceIn(0f, 1f)
                                    val thumbOffset = ((maxWidth * safeNeedlePercent) - 5.dp).coerceIn(0.dp, maxWidth - 10.dp)
                                    Box(
                                        modifier = Modifier
                                            .size(thumbSize)
                                            .offset(x = thumbOffset)
                                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                                    )

                                    Slider(
                                        value = safeNeedlePercent,
                                        enabled = arePlaybackButtonsActive,
                                        onValueChange = { percent ->
                                            isDraggingState = true
                                            dragPercentState = percent
                                        },
                                        onValueChangeFinished = {
                                            val target = (dragPercentState * durationState).toLong()
                                            AudioPlayerManager.seekTo(target, isPhysicalTimestamp = false)
                                            isDraggingState = false
                                        },
                                        colors = SliderDefaults.colors(
                                            thumbColor = Color.Transparent,
                                            activeTrackColor = Color.Transparent,
                                            inactiveTrackColor = Color.Transparent,
                                            activeTickColor = Color.Transparent,
                                            inactiveTickColor = Color.Transparent,
                                            disabledThumbColor = Color.Transparent,
                                            disabledActiveTrackColor = Color.Transparent,
                                            disabledInactiveTrackColor = Color.Transparent
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }

                                Spacer(modifier = Modifier.height(2.dp))

                                val displayPlayPosition = if (isDraggingState) (dragPercentState * durationState).toLong() else playPositionState
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(formatDuration(displayPlayPosition), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                                    Text(formatDuration(durationState), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                                }
                            }

                            // CONTROLLER BUTTONS
                            PlayerTransportControls(
                                isPlayingState = isPlayingState,
                                arePlaybackButtonsActive = arePlaybackButtonsActive,
                                skipSecondsSetting = viewModel.skipSecondsSetting
                            )
                        }

                        // GLASSMORPHIC FOOTER UTILITIES CONTAINER
                        PlayerFooterUtilitiesCard(
                            track = track,
                            viewModel = viewModel,
                            isDistractionFree = isDistractionFree,
                            speedState = speedState,
                            isPracticeMode = isPracticeMode,
                            isPracticeAnalyzing = isPracticeAnalyzing,
                            sleepTimerState = sleepTimerState,
                            onOpenPracticeSetup = { showPracticeSetupSheet = true }
                        )
                    }

                    if (showWaveformEditorDialog) {
                        WaveformSegmentEditorDialog(
                            track = track,
                            viewModel = viewModel,
                            onDismiss = { showWaveformEditorDialog = false }
                        )
                    }

                    if (showPracticeSetupSheet) {
                        PracticeModeSetupSheet(
                            track = track,
                            viewModel = viewModel,
                            onDismiss = { showPracticeSetupSheet = false },
                            onOpenWaveformEditor = {
                                showPracticeSetupSheet = false
                                showWaveformEditorDialog = true
                            }
                        )
                    }

                    if (showSubtitlePasteDialog) {
                        SubtitlePasteEditDialog(
                            initialText = subtitlePasteText,
                            isEditingExistingSubtitles = isEditingExistingSubtitles,
                            playPositionState = playPositionState,
                            isPlayingState = isPlayingState,
                            onDismiss = { showSubtitlePasteDialog = false },
                            onOpenLiveSync = {
                                showSubtitlePasteDialog = false
                                showLiveSyncDialog = true
                            },
                            onSaveCleared = {
                                showSubtitlePasteDialog = false
                                subtitlePasteText = ""
                            }
                        )
                    }

                    if (showLiveSyncDialog) {
                        LiveSubtitleSyncDialog(
                            initialCues = subtitlesCuesState,
                            onDismiss = { showLiveSyncDialog = false },
                            onSave = { AudioPlayerManager.setSubtitleContentForCurrentTrack(it) }
                        )
                    }

                    if (showDeleteSubtitleConfirmDialog) {
                        DeleteSubtitleConfirmDialog(
                            onDismiss = { showDeleteSubtitleConfirmDialog = false },
                            onConfirmDelete = {
                                AudioPlayerManager.clearSubtitlesForCurrentTrack()
                                showDeleteSubtitleConfirmDialog = false
                            }
                        )
                    }
                }

                // When in No-Distraction Mode and controls are hidden, tapping ANY part of the screen reveals controls
                if (isDistractionFree && !areVideoControlsVisible) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onDoubleTap = {
                                        lastVideoControlsInteractionTime = System.currentTimeMillis()
                                        val isNowFocus = AudioPlayerManager.toggleVideoFocusMode()
                                        val msg = if (isNowFocus) Loc.getText("video_focus_mode_on") else Loc.getText("video_focus_mode_off")
                                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                    },
                                    onTap = {
                                        areVideoControlsVisible = true
                                        lastVideoControlsInteractionTime = System.currentTimeMillis()
                                    }
                                )
                            }
                    )
                }
            }
        }
    }
}
