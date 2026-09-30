package com.example.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.*
import com.example.player.*
import kotlinx.coroutines.delay

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

    val subtitlesCuesState by AudioPlayerManager.subtitlesCues.collectAsStateWithLifecycle()
    val activeSubtitleCueState by AudioPlayerManager.activeSubtitleCue.collectAsStateWithLifecycle()
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

    VideoScreenAndImmersiveEffects(
        isTrackVideo = isTrackVideo,
        isPlayingState = isPlayingState,
        isFullScreenVideo = isFullScreenVideo,
        isHorizontalVideo = isHorizontalVideo,
        isInPipModeState = isInPipModeState
    )

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
    val maxListenedPercent = liveTrack.getProgressPercent()

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
                                onAskAiAboutCue = { cueText, startMs, _ ->
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
                            PlayerSegmentedProgressBar(
                                track = track,
                                liveTrack = liveTrack,
                                durationState = durationState,
                                playPositionState = playPositionState,
                                maxListenedPercent = maxListenedPercent,
                                arePlaybackButtonsActive = arePlaybackButtonsActive,
                                trackNotes = trackNotes,
                                subtitlesCuesState = subtitlesCuesState
                            )

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
