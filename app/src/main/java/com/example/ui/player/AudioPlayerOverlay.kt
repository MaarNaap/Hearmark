package com.example.ui

import android.app.TimePickerDialog
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.roundToInt
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import kotlinx.coroutines.delay
import androidx.activity.result.contract.ActivityResultContracts
import android.app.Activity
import android.app.PictureInPictureParams
import android.os.Build
import android.util.Rational
import android.content.pm.ActivityInfo
import android.graphics.SurfaceTexture
import android.view.Surface
import android.view.WindowManager
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.TextureView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.graphics.RectangleShape
import com.example.data.*
import com.example.player.*
import com.example.ui.theme.*
import com.example.R
import com.example.util.AudioMetadataExtractor
import com.example.util.TrackMetadata
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.launch

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
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
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
    val coroutineScope = rememberCoroutineScope()
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
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        areFullScreenControlsVisible = !areFullScreenControlsVisible
                    }
            ) {
                // Video Viewport respecting original video dimensions & aspect ratio
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .aspectRatio(videoAspectRatio, matchHeightConstraintsFirst = isHorizontalVideo),
                        contentAlignment = Alignment.Center
                    ) {
                        VideoPlayerSurface(modifier = Modifier.fillMaxSize())
                    }
                }

                // Subtitle / Black Mask Overlay
                when (videoSubtitleModeState) {
                    AudioPlayerManager.VideoSubtitleMode.SHOW -> {
                        if (activeSubtitleCueState != null && activeSubtitleCueState!!.text.isNotBlank()) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = if (areFullScreenControlsVisible && !isInPipModeState) 100.dp else 16.dp, start = 16.dp, end = 16.dp)
                                    .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = activeSubtitleCueState!!.text,
                                    color = Color.White,
                                    fontSize = (if (isInPipModeState) subtitleFontSizeState * 0.75f else subtitleFontSizeState * 1.25f).sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                    AudioPlayerManager.VideoSubtitleMode.HIDE -> {
                        // Hidden
                    }
                    AudioPlayerManager.VideoSubtitleMode.BLACK -> {
                        // Solid black box without text to hide built-in video subtitles
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = if (areFullScreenControlsVisible && !isInPipModeState) 100.dp else 20.dp, start = 16.dp, end = 16.dp)
                                .fillMaxWidth(0.92f)
                                .height(if (isInPipModeState) 32.dp else 52.dp)
                                .background(Color.Black, RoundedCornerShape(6.dp))
                        )
                    }
                }

                // Floating Registered Note Badge directly on Fullscreen Video
                if (activeNotesForTime.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.92f),
                        shadowElevation = 4.dp,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .statusBarsPadding()
                            .padding(top = if (areFullScreenControlsVisible) 64.dp else 16.dp, start = 16.dp)
                            .clickable { onOpenNotes(activeNotesForTime) }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.EditNote,
                                contentDescription = Loc.getText("view_note"),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (activeNotesForTime.size > 1) "${Loc.getText("notes")} (${activeNotesForTime.size})" else Loc.getText("notes"),
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                if (!isInPipModeState) {
                // Top Bar Overlay
                AnimatedVisibility(
                    visible = areFullScreenControlsVisible,
                    enter = fadeIn(animationSpec = tween(250)),
                    exit = fadeOut(animationSpec = tween(250)),
                    modifier = Modifier.align(Alignment.TopCenter)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.8f), Color.Transparent)))
                            .statusBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { isFullScreenVideo = false }) {
                            Icon(Icons.Filled.FullscreenExit, contentDescription = "Exit Fullscreen", tint = Color.White)
                        }
                        Text(
                            text = track.getDisplayTitle(),
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 12.dp)
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (activeNotesForTime.isNotEmpty()) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .clickable { onOpenNotes(activeNotesForTime) }
                                        .padding(horizontal = 4.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.EditNote,
                                            contentDescription = Loc.getText("view_note"),
                                            tint = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = if (activeNotesForTime.size > 1) "${Loc.getText("notes")} (${activeNotesForTime.size})" else Loc.getText("notes"),
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            IconButton(onClick = {
                                val currentCue = activeSubtitleCueState
                                val currentCueText = currentCue?.text ?: ""
                                val startMs = currentCue?.startMs ?: effectivePhysPos
                                val maxEndLimit = if (track.isVirtualScene) {
                                    track.endOffsetMs ?: (track.startOffsetMs + track.duration)
                                } else {
                                    durationState
                                }
                                val endMs = currentCue?.let { if (it.endMs > it.startMs) it.endMs else (it.startMs + 5000L) } ?: (startMs + 5000L).coerceAtMost(maxEndLimit)
                                onQuickAddNotePrompt(track.id, startMs, endMs, currentCueText)
                            }) {
                                Icon(
                                    imageVector = Icons.Filled.EditNote,
                                    contentDescription = Loc.getText("add_note"),
                                    tint = Color.White
                                )
                            }
                            IconButton(onClick = {
                                val next = AudioPlayerManager.toggleVideoSubtitleMode()
                                val msg = when (next) {
                                    AudioPlayerManager.VideoSubtitleMode.SHOW -> Loc.getText("video_subtitles_show_toast")
                                    AudioPlayerManager.VideoSubtitleMode.HIDE -> Loc.getText("video_subtitles_hide_toast")
                                    AudioPlayerManager.VideoSubtitleMode.BLACK -> Loc.getText("video_subtitles_black_toast")
                                }
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            }) {
                                when (videoSubtitleModeState) {
                                    AudioPlayerManager.VideoSubtitleMode.SHOW -> {
                                        Icon(
                                            imageVector = Icons.Filled.Subtitles,
                                            contentDescription = Loc.getText("video_subtitles_show_toast"),
                                            tint = Color.White
                                        )
                                    }
                                    AudioPlayerManager.VideoSubtitleMode.HIDE -> {
                                        Icon(
                                            imageVector = Icons.Filled.SubtitlesOff,
                                            contentDescription = Loc.getText("video_subtitles_hide_toast"),
                                            tint = Color.White.copy(alpha = 0.45f)
                                        )
                                    }
                                    AudioPlayerManager.VideoSubtitleMode.BLACK -> {
                                        Icon(
                                            imageVector = Icons.Filled.Crop169,
                                            contentDescription = Loc.getText("video_subtitles_black_toast"),
                                            tint = Color(0xFFFFB74D)
                                        )
                                    }
                                }
                            }
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                IconButton(onClick = {
                                    val activity = context as? Activity
                                    if (activity != null) {
                                        try {
                                            AudioPlayerManager.setInPipMode(true)
                                            val ratW = if (videoDims != null && videoDims!!.first > 0) videoDims!!.first.coerceIn(1, 10000) else 16
                                            val ratH = if (videoDims != null && videoDims!!.second > 0) videoDims!!.second.coerceIn(1, 10000) else 9
                                            val params = PictureInPictureParams.Builder()
                                                .setAspectRatio(Rational(ratW, ratH))
                                                .build()
                                            activity.enterPictureInPictureMode(params)
                                        } catch (e: Exception) {
                                            activity.enterPictureInPictureMode()
                                        }
                                    }
                                }) {
                                    Icon(Icons.Filled.PictureInPictureAlt, contentDescription = "PiP", tint = Color.White)
                                }
                            }
                        }
                    }
                }

                // Bottom Controls Overlay
                AnimatedVisibility(
                    visible = areFullScreenControlsVisible,
                    enter = fadeIn(animationSpec = tween(250)),
                    exit = fadeOut(animationSpec = tween(250)),
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.88f))))
                            .navigationBarsPadding()
                            .padding(horizontal = 20.dp, vertical = 14.dp)
                    ) {
                        Box(modifier = Modifier.fillMaxWidth()) {
                            if (durationState > 0 && trackNotes.isNotEmpty()) {
                                Canvas(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(4.dp)
                                        .align(Alignment.Center)
                                        .padding(horizontal = 8.dp)
                                ) {
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
                                                color = Color.White.copy(alpha = 0.85f),
                                                topLeft = androidx.compose.ui.geometry.Offset(x = noteX - (markerWidth / 2f), y = 0f),
                                                size = androidx.compose.ui.geometry.Size(width = markerWidth, height = size.height)
                                            )
                                        }
                                    }
                                }
                            }
                            Slider(
                                value = if (durationState > 0) (playPositionState.toFloat() / durationState).coerceIn(0f, 1f) else 0f,
                                onValueChange = { frac ->
                                    val targetMs = (frac * durationState).toLong()
                                    AudioPlayerManager.seekTo(targetMs, isPhysicalTimestamp = false)
                                },
                                colors = SliderDefaults.colors(
                                    thumbColor = Color.White,
                                    activeTrackColor = MaterialTheme.colorScheme.primary,
                                    inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                                )
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(formatDuration(playPositionState), color = Color.White, fontSize = 12.sp)

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(20.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(onClick = { AudioPlayerManager.skipBackward() }) {
                                    Icon(Icons.Filled.Replay10, contentDescription = "Rewind", tint = Color.White)
                                }
                                IconButton(onClick = {
                                    if (isPlayingState) AudioPlayerManager.pause() else AudioPlayerManager.resume()
                                }) {
                                    Icon(
                                        imageVector = if (isPlayingState) Icons.Filled.PauseCircleFilled else Icons.Filled.PlayCircleFilled,
                                        contentDescription = "Play/Pause",
                                        tint = Color.White,
                                        modifier = Modifier.size(46.dp)
                                    )
                                }
                                IconButton(onClick = { AudioPlayerManager.skipForward() }) {
                                    Icon(Icons.Filled.Forward10, contentDescription = "Forward", tint = Color.White)
                                }
                            }

                            Text(formatDuration(durationState), color = Color.White, fontSize = 12.sp)
                        }
                    }
                }
            }
                }
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
            androidx.compose.animation.AnimatedVisibility(
                visible = isDistractionFree,
                enter = fadeIn(tween(250)) + expandVertically(tween(250)),
                exit = fadeOut(tween(250)) + shrinkVertically(tween(250))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(
                        onClick = {
                            AudioPlayerManager.setFocusMode(false)
                        },
                        modifier = Modifier
                            .size(38.dp)
                            .testTag("btn_exit_focus_mode")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = Loc.getText("exit_focus_mode"),
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    if (isPracticeMode) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)),
                            modifier = Modifier.clickable {
                                AudioPlayerManager.setFocusMode(false)
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary)
                                )
                                Text(
                                    text = Loc.getText("practice_mode"),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = Loc.getText("exit_focus_mode"),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }

                    Box(modifier = Modifier.size(38.dp))
                }
            }

            androidx.compose.animation.AnimatedVisibility(
                visible = !isDistractionFree,
                enter = fadeIn(tween(250)) + expandVertically(tween(250)),
                exit = fadeOut(tween(250)) + shrinkVertically(tween(250))
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // iOS drag handle that detects swiping down to minimize
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onVerticalDrag = { _, dragAmount ->
                                if (dragAmount > 25f) {
                                    dismiss()
                                }
                            }
                        )
                    }
                    .padding(top = 10.dp, bottom = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(42.dp)
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                )
            }

            // Top Header row with Huawei Music style swipable tab headers
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onVerticalDrag = { _, dragAmount ->
                                if (dragAmount > 25f) {
                                    dismiss()
                                }
                            }
                        )
                    }, 
                horizontalArrangement = Arrangement.SpaceBetween, 
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = dismiss) {
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowDown, 
                        contentDescription = "Close", 
                        tint = MaterialTheme.colorScheme.onSurface, 
                        modifier = Modifier.size(36.dp)
                    )
                }

                // Small tiny circle navigation indicator
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    (0 until 2).forEach { index ->
                        val selected = pagerState.currentPage == index
                        Box(
                            modifier = Modifier
                                .size(if (selected) 8.dp else 6.dp)
                                .clip(CircleShape)
                                .background(
                                    if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                                )
                                .clickable {
                                    coroutineScope.launch { pagerState.animateScrollToPage(index) }
                                }
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(onClick = {
                        val fullSubtitles = AudioPlayerManager.getCurrentSubtitlesRawText()
                        val fullTranscript = if (fullSubtitles.isNotBlank()) {
                            fullSubtitles
                        } else {
                            subtitlesCuesState.joinToString("\n") { cue ->
                                val timeStr = if (cue.isTimed && cue.startMs >= 0) "[${formatDuration(cue.startMs)}] " else ""
                                "$timeStr${cue.text}"
                            }
                        }
                        val dur = durationState
                        val formattedPos = if (dur > 0) "${formatDuration(playPositionState)} / ${formatDuration(dur)}" else formatDuration(playPositionState)
                        viewModel.openChatWithContext(
                            com.example.ai.AudioContextSummary(
                                trackTitle = track.getDisplayTitle(),
                                trackArtist = null,
                                currentPositionMs = playPositionState,
                                formattedPosition = formattedPos,
                                activeSubtitleLine = null,
                                activeTaskTitle = relatedTasks.firstOrNull()?.getDisplayTitle(),
                                fullSubtitlesText = fullTranscript.ifBlank { null },
                                isFullSubtitlesContext = true,
                                audioFilePath = track.filePath,
                                trackId = track.id,
                                totalDurationMs = dur
                            )
                        )
                    }) {
                        Icon(
                            imageVector = Icons.Filled.AutoAwesome,
                            contentDescription = Loc.getText("gemini_ai_assistant"),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    IconButton(
                        onClick = { viewModel.openQuizForTrack(track) },
                        modifier = Modifier.testTag("player_ai_quiz_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Quiz,
                            contentDescription = Loc.getText("ai_quiz_action"),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    IconButton(onClick = {
                        val currentCue = activeSubtitleCueState
                        val currentCueText = currentCue?.text ?: ""
                        val effectivePhysPos = if (track.isVirtualScene) (track.startOffsetMs + playPositionState) else playPositionState
                        val startMs = currentCue?.startMs ?: effectivePhysPos
                        val maxEndLimit = if (track.isVirtualScene) {
                            track.endOffsetMs ?: (track.startOffsetMs + track.duration)
                        } else {
                            durationState
                        }
                        val endMs = currentCue?.let { if (it.endMs > it.startMs) it.endMs else (it.startMs + 5000L) } ?: (startMs + 5000L).coerceAtMost(maxEndLimit)
                        onQuickAddNotePrompt(track.id, startMs, endMs, currentCueText)
                    }) {
                        Icon(
                            imageVector = Icons.Filled.EditNote,
                            contentDescription = Loc.getText("add_note"),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    IconButton(onClick = {
                        onQuickAddTaskPrompt(track.id)
                    }) {
                        Icon(
                            imageVector = Icons.Filled.Bookmark, 
                            contentDescription = "Bookmark", 
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

                    Spacer(modifier = Modifier.height(6.dp))
                }
            }

            // MIDDLE SWIPEABLE TAB CONTENT (HorizontalPager)
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) { page ->
                if (page == 0) {
                    // PAGE 0: MAIN PLAYER VIEW
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = if (isDistractionFree) Arrangement.Center else Arrangement.spacedBy(16.dp)
                    ) {
                        androidx.compose.animation.AnimatedVisibility(
                            visible = !isDistractionFree,
                            enter = fadeIn(tween(250)) + expandVertically(tween(250)),
                            exit = fadeOut(tween(250)) + shrinkVertically(tween(250))
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                // Title of active track
                                SmartFileNameText(
                            text = track.getDisplayTitle(),
                            isActive = true,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                textAlign = TextAlign.Center
                            ),
                            modifier = Modifier.padding(horizontal = 24.dp),
                            maxLength = 32
                        )

                        // Play count & Registered Note Indicator (Steady layout without shifts)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(26.dp)
                                .padding(horizontal = 24.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Left balancing space to ensure play count is always rock-steady centered
                            Spacer(modifier = Modifier.weight(1f))

                            // Center Play Count Indicator - perfectly stationary
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Headphones,
                                    contentDescription = null,
                                    modifier = Modifier.size(13.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "${liveTrack.playCount}",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            // Right space for Note Indicator - appears next to play count without shifting anything
                            val isNoteActive = activeNotesForTime.isNotEmpty()
                            val noteAlpha by animateFloatAsState(
                                targetValue = if (isNoteActive) 1f else 0f,
                                animationSpec = tween(220),
                                label = "noteAlpha"
                            )
                            Box(
                                modifier = Modifier.weight(1f),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                if (isNoteActive || noteAlpha > 0.01f) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .padding(start = 6.dp)
                                            .graphicsLayer { alpha = noteAlpha }
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f * noteAlpha))
                                            .clickable(enabled = isNoteActive) { onOpenNotes(activeNotesForTime) }
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.EditNote,
                                            contentDescription = Loc.getText("view_note"),
                                            modifier = Modifier.size(14.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text(
                                            text = if (activeNotesForTime.size > 1) "${Loc.getText("notes")} (${activeNotesForTime.size})" else Loc.getText("notes"),
                                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }

                        // Unified Metadata & Origin Layout
                        if (folderOfTrack != null || playlistsOfTrack.isNotEmpty() || relatedTasks.isNotEmpty()) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp)
                            ) {
                                if (folderOfTrack != null || playlistsOfTrack.isNotEmpty()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (folderOfTrack != null) {
                                            Row(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .clickable {
                                                        dismiss()
                                                        onNavigateToFolder(folderOfTrack.id)
                                                    }
                                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.Folder,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(12.dp),
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                                )
                                                Text(
                                                    text = folderOfTrack.folderName,
                                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }
                                        }
                                        if (folderOfTrack != null && playlistsOfTrack.isNotEmpty()) {
                                            Text(
                                                " • ",
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp)
                                            )
                                        }
                                        playlistsOfTrack.forEachIndexed { index, playlist ->
                                            Row(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .clickable {
                                                        dismiss()
                                                        onNavigateToPlaylist(playlist.id)
                                                    }
                                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.PlaylistPlay,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(12.dp),
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                                )
                                                Text(
                                                    text = playlist.name,
                                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }
                                            if (index < playlistsOfTrack.lastIndex) {
                                                Text(
                                                    " • ",
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp)
                                                )
                                            }
                                        }
                                    }
                                }

                                if (relatedTasks.isNotEmpty()) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Bookmark,
                                            contentDescription = null,
                                            modifier = Modifier.size(12.dp),
                                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        relatedTasks.forEachIndexed { index, task ->
                                            Text(
                                                text = task.getDisplayTitle(),
                                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .clickable { onNavigateToTask(task) }
                                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                                            )
                                            if (index < relatedTasks.lastIndex) {
                                                Text(", ", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                            }
                        }

                        // Video Player Screen or Audio Artwork Representation
                        if (isTrackVideo) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(fullWidthVideoHeight),
                                shape = RoundedCornerShape(0.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.Black),
                                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                            ) {
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
                                                    areVideoControlsVisible = !areVideoControlsVisible
                                                    lastVideoControlsInteractionTime = System.currentTimeMillis()
                                                }
                                            )
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .aspectRatio(videoAspectRatio, matchHeightConstraintsFirst = false),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        VideoPlayerSurface(modifier = Modifier.fillMaxSize())
                                    }

                                    // Subtitle / Black Mask Overlay directly on video
                                    when (videoSubtitleModeState) {
                                        AudioPlayerManager.VideoSubtitleMode.SHOW -> {
                                            if (activeSubtitleCueState != null && activeSubtitleCueState!!.text.isNotBlank()) {
                                                Box(
                                                    modifier = Modifier
                                                        .align(Alignment.BottomCenter)
                                                        .padding(bottom = 8.dp, start = 12.dp, end = 12.dp)
                                                        .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(6.dp))
                                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                                ) {
                                                    Text(
                                                        text = activeSubtitleCueState!!.text,
                                                        color = Color.White,
                                                        fontSize = (subtitleFontSizeState * 0.9f).sp,
                                                        fontWeight = FontWeight.Bold,
                                                        textAlign = TextAlign.Center
                                                    )
                                                }
                                            }
                                        }
                                        AudioPlayerManager.VideoSubtitleMode.HIDE -> {
                                            // Hidden
                                        }
                                        AudioPlayerManager.VideoSubtitleMode.BLACK -> {
                                            // Solid black box without text to hide built-in video subtitles
                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.BottomCenter)
                                                    .padding(bottom = 8.dp, start = 12.dp, end = 12.dp)
                                                    .fillMaxWidth(0.9f)
                                                    .height(36.dp)
                                                    .background(Color.Black, RoundedCornerShape(6.dp))
                                            )
                                        }
                                    }

                                    // Registered Note Floating Indicator on Video Card
                                    if (activeNotesForTime.isNotEmpty()) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.92f),
                                            shadowElevation = 4.dp,
                                            modifier = Modifier
                                                .align(Alignment.TopStart)
                                                .padding(8.dp)
                                                .clickable { onOpenNotes(activeNotesForTime) }
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.EditNote,
                                                    contentDescription = Loc.getText("view_note"),
                                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                    modifier = Modifier.size(15.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = if (activeNotesForTime.size > 1) "${Loc.getText("notes")} (${activeNotesForTime.size})" else Loc.getText("notes"),
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }

                                    // Quick Floating Actions (Subtitle Toggle, PiP & Fullscreen)
                                    androidx.compose.animation.AnimatedVisibility(
                                        visible = areVideoControlsVisible,
                                        enter = fadeIn(animationSpec = tween(250)),
                                        exit = fadeOut(animationSpec = tween(250)),
                                        modifier = Modifier.align(Alignment.TopEnd)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(6.dp),
                                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            IconButton(
                                                onClick = {
                                                    lastVideoControlsInteractionTime = System.currentTimeMillis()
                                                    val next = AudioPlayerManager.toggleVideoSubtitleMode()
                                                    val msg = when (next) {
                                                        AudioPlayerManager.VideoSubtitleMode.SHOW -> Loc.getText("video_subtitles_show_toast")
                                                        AudioPlayerManager.VideoSubtitleMode.HIDE -> Loc.getText("video_subtitles_hide_toast")
                                                        AudioPlayerManager.VideoSubtitleMode.BLACK -> Loc.getText("video_subtitles_black_toast")
                                                    }
                                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                                },
                                                modifier = Modifier.size(36.dp)
                                            ) {
                                                when (videoSubtitleModeState) {
                                                    AudioPlayerManager.VideoSubtitleMode.SHOW -> {
                                                        Icon(
                                                            imageVector = Icons.Filled.Subtitles,
                                                            contentDescription = Loc.getText("video_subtitles_show_toast"),
                                                            tint = Color.White,
                                                            modifier = Modifier.size(20.dp)
                                                        )
                                                    }
                                                    AudioPlayerManager.VideoSubtitleMode.HIDE -> {
                                                        Icon(
                                                            imageVector = Icons.Filled.SubtitlesOff,
                                                            contentDescription = Loc.getText("video_subtitles_hide_toast"),
                                                            tint = Color.White.copy(alpha = 0.5f),
                                                            modifier = Modifier.size(20.dp)
                                                        )
                                                    }
                                                    AudioPlayerManager.VideoSubtitleMode.BLACK -> {
                                                        Icon(
                                                            imageVector = Icons.Filled.Crop169,
                                                            contentDescription = Loc.getText("video_subtitles_black_toast"),
                                                            tint = Color(0xFFFFB74D),
                                                            modifier = Modifier.size(20.dp)
                                                        )
                                                    }
                                                }
                                            }

                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                                IconButton(
                                                    onClick = {
                                                        lastVideoControlsInteractionTime = System.currentTimeMillis()
                                                        val activity = context as? Activity
                                                        if (activity != null) {
                                                            try {
                                                                AudioPlayerManager.setInPipMode(true)
                                                                val ratW = if (videoDims != null && videoDims!!.first > 0) videoDims!!.first.coerceIn(1, 10000) else 16
                                                                val ratH = if (videoDims != null && videoDims!!.second > 0) videoDims!!.second.coerceIn(1, 10000) else 9
                                                                val params = PictureInPictureParams.Builder()
                                                                    .setAspectRatio(Rational(ratW, ratH))
                                                                    .build()
                                                                activity.enterPictureInPictureMode(params)
                                                            } catch (e: Exception) {
                                                                activity.enterPictureInPictureMode()
                                                            }
                                                        }
                                                    },
                                                    modifier = Modifier.size(36.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Filled.PictureInPictureAlt,
                                                        contentDescription = "PiP",
                                                        tint = Color.White,
                                                        modifier = Modifier.size(19.dp)
                                                    )
                                                }
                                            }
                                            IconButton(
                                                onClick = {
                                                    lastVideoControlsInteractionTime = System.currentTimeMillis()
                                                    val isNowFocus = AudioPlayerManager.toggleVideoFocusMode()
                                                    val msg = if (isNowFocus) Loc.getText("video_focus_mode_on") else Loc.getText("video_focus_mode_off")
                                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                                },
                                                modifier = Modifier.size(36.dp).testTag("video_focus_mode_button")
                                            ) {
                                                Icon(
                                                    imageVector = if (isFocusModeState) Icons.Filled.CenterFocusStrong else Icons.Filled.FilterCenterFocus,
                                                    contentDescription = Loc.getText("video_focus_mode_title"),
                                                    tint = if (isFocusModeState) MaterialTheme.colorScheme.primary else Color.White,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                            IconButton(
                                                onClick = {
                                                    lastVideoControlsInteractionTime = System.currentTimeMillis()
                                                    isFullScreenVideo = true
                                                },
                                                modifier = Modifier.size(36.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.Fullscreen,
                                                    contentDescription = "Fullscreen",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(22.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            // Artwork & Radar Waves Representation
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(horizontal = 24.dp)
                            ) {
                                val primaryRadarColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                Box(
                                    modifier = Modifier
                                        .size(110.dp)
                                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f), RoundedCornerShape(24.dp))
                                        .drawBehind {
                                            drawCircle(
                                                color = primaryRadarColor,
                                                radius = size.minDimension / 1.4f,
                                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f)
                                            )
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = getTrackFileIcon(track),
                                        contentDescription = null,
                                        modifier = Modifier.size(64.dp),
                                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                    )

                                    Row(
                                        modifier = Modifier
                                            .height(32.dp)
                                            .padding(vertical = 2.dp),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        val barCount = 13
                                        val infiniteTransition = rememberInfiniteTransition(label = "waveform")
                                        val heights = (0 until barCount).map { index ->
                                            if (isPlayingState) {
                                                val duration = 350 + (index % 4) * 120
                                                val target = 0.35f + (index % 3) * 0.25f
                                                infiniteTransition.animateFloat(
                                                    initialValue = 0.15f,
                                                    targetValue = target.coerceIn(0.22f, 1f),
                                                    animationSpec = infiniteRepeatable(
                                                        animation = tween(durationMillis = duration, easing = FastOutSlowInEasing),
                                                        repeatMode = RepeatMode.Reverse
                                                    ),
                                                    label = "bar_$index"
                                                )
                                            } else {
                                                remember { mutableStateOf(0.18f) }
                                            }
                                        }
                                        for (i in 0 until barCount) {
                                            Box(
                                                modifier = Modifier
                                                    .width(3.dp)
                                                    .fillMaxHeight(heights[i].value)
                                                    .clip(RoundedCornerShape(1.5.dp))
                                                    .background(MaterialTheme.colorScheme.primary)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
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
                        val practiceSegmentsList by AudioPlayerManager.currentPracticeSegments.collectAsStateWithLifecycle()
                        val practiceMultiplierState by AudioPlayerManager.practicePauseMultiplierFlow.collectAsStateWithLifecycle()
                        val currentPlayingTrack by AudioPlayerManager.currentTrack.collectAsStateWithLifecycle()
                        val activeTrack = currentPlayingTrack ?: track
                        val activePracticeSource = AudioPlayerManager.getActivePracticeSourceForTrack(activeTrack)
                        val effectiveSegments = if (practiceSegmentsList.isNotEmpty()) practiceSegmentsList else activeTrack.getPracticeSegmentsList()
                        val cutsCount = effectiveSegments.size

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.60f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp)
                                .testTag("pill_active_practice_status")
                        ) {
                            if (isPracticePausing) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(20.dp)
                                                .background(MaterialTheme.colorScheme.primary, CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.RecordVoiceOver,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onPrimary,
                                                modifier = Modifier.size(13.dp)
                                            )
                                        }
                                        Text(
                                            text = Loc.getText("practice_your_turn"),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            lineHeight = 14.sp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.20f),
                                            border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.50f))
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.Timer,
                                                    contentDescription = "Stopwatch",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(12.dp)
                                                )
                                                Text(
                                                    text = "${String.format(java.util.Locale.US, "%.1f", practicePauseRemaining)}s",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    lineHeight = 14.sp,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                    }

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        // Repeat button
                                        Row(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
                                                .clickable { AudioPlayerManager.repeatPracticeSegment() }
                                                .padding(horizontal = 6.dp, vertical = 3.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Replay,
                                                contentDescription = Loc.getText("practice_repeat_segment"),
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(13.dp)
                                            )
                                            Text(
                                                text = Loc.getText("practice_repeat_segment"),
                                                fontSize = 10.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                lineHeight = 13.sp,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }

                                        // Skip Pause button
                                        Row(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
                                                .clickable { AudioPlayerManager.skipPracticePause() }
                                                .padding(horizontal = 6.dp, vertical = 3.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            Text(
                                                text = Loc.getText("practice_skip_pause"),
                                                fontSize = 10.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                lineHeight = 13.sp,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Icon(
                                                imageVector = Icons.Filled.SkipNext,
                                                contentDescription = Loc.getText("practice_skip_pause"),
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }
                            } else {
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .weight(1f, fill = false)
                                            .clickable { showPracticeSetupSheet = true },
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(20.dp)
                                                .background(MaterialTheme.colorScheme.primary, CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = when (activePracticeSource) {
                                                    "MANUAL" -> Icons.Filled.GraphicEq
                                                    "SUBTITLES" -> Icons.Filled.Subtitles
                                                    else -> Icons.Filled.RecordVoiceOver
                                                },
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onPrimary,
                                                modifier = Modifier.size(13.dp)
                                            )
                                        }
                                        Text(
                                            text = when (activePracticeSource) {
                                                "MANUAL" -> Loc.getText("practice_source_manual_short")
                                                "SUBTITLES" -> Loc.getText("practice_source_subtitles_short")
                                                else -> Loc.getText("practice_source_silence_short")
                                            },
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            lineHeight = 14.sp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.20f),
                                            border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.50f))
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.ContentCut,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(12.dp)
                                                )
                                                Text(
                                                    text = "$cutsCount ${Loc.getText("cuts_label")} • ${String.format(java.util.Locale.US, "%.2f", practiceMultiplierState)}x",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    lineHeight = 14.sp,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                    }

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        // Customize button styled exactly like Repeat/Skip buttons
                                        Row(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
                                                .clickable { showPracticeSetupSheet = true }
                                                .padding(horizontal = 6.dp, vertical = 3.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Tune,
                                                contentDescription = Loc.getText("tap_to_change_source"),
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(13.dp)
                                            )
                                            Text(
                                                text = Loc.getText("tap_to_change_source"),
                                                fontSize = 10.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                lineHeight = 13.sp,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }

                                        // Exit button
                                        Row(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
                                                .clickable { AudioPlayerManager.setFocusMode(false) }
                                                .padding(horizontal = 5.dp, vertical = 3.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Close,
                                                contentDescription = "Exit Practice Mode",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(13.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { AudioPlayerManager.playPreviousTrack() },
                        enabled = arePlaybackButtonsActive,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.SkipPrevious,
                            contentDescription = "Previous Track",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Box(contentAlignment = Alignment.Center) {
                        IconButton(
                            onClick = { AudioPlayerManager.skipBackward() },
                            enabled = arePlaybackButtonsActive
                        ) {
                            Icon(
                                imageVector = Icons.Filled.FastRewind,
                                contentDescription = "Rewind",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        Text(
                            text = "${viewModel.skipSecondsSetting}",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.offset(y = 12.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            if (isPlayingState) AudioPlayerManager.pause() else AudioPlayerManager.resume()
                        },
                        enabled = arePlaybackButtonsActive,
                        modifier = Modifier
                            .size(64.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                    ) {
                        Icon(
                            imageVector = if (isPlayingState) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = "Play pause overlay",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    Box(contentAlignment = Alignment.Center) {
                        IconButton(
                            onClick = { AudioPlayerManager.skipForward() },
                            enabled = arePlaybackButtonsActive
                        ) {
                            Icon(
                                imageVector = Icons.Filled.FastForward,
                                contentDescription = "ForwardFast",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        Text(
                            text = "${viewModel.skipSecondsSetting}",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.offset(y = 12.dp)
                        )
                    }

                    IconButton(
                        onClick = { AudioPlayerManager.playNextTrack() },
                        enabled = arePlaybackButtonsActive,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.SkipNext,
                            contentDescription = "Next Track",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
                }

                // GLASSMORPHIC FOOTER UTILITIES CONTAINER
                var showQueueSheet by remember { mutableStateOf(false) }
                var selectedQueueIndices by remember { mutableStateOf(setOf<Int>()) }
                val currentQueueState by AudioPlayerManager.currentQueueFlow.collectAsStateWithLifecycle()

                androidx.compose.animation.AnimatedVisibility(
                    visible = !isDistractionFree,
                    enter = fadeIn(tween(250)) + expandVertically(tween(250)),
                    exit = fadeOut(tween(250)) + shrinkVertically(tween(250))
                ) {
                    Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Secondary Tools Row (Speed, Autoplay, Practice Mode, Queue, Sleep Timer)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceAround,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 1. Speed Button
                            var showSpeedDropdown by remember { mutableStateOf(false) }
                            val speedActive = Math.abs(speedState - 1.0f) > 0.01f
                            val speedColor = if (speedActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            Box {
                                TextButton(
                                    onClick = { showSpeedDropdown = true },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.textButtonColors(contentColor = speedColor),
                                    modifier = Modifier.height(44.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Speed,
                                            contentDescription = "Speed",
                                            modifier = Modifier.size(18.dp),
                                            tint = speedColor
                                        )
                                        Text(
                                            text = formatPlaybackSpeed(speedState),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                DropdownMenu(
                                    expanded = showSpeedDropdown,
                                    onDismissRequest = { showSpeedDropdown = false }
                                ) {
                                    // + Button to fine tune speed by +0.1x per click
                                    DropdownMenuItem(
                                        text = {
                                            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                                Text(
                                                    text = "+0.1x",
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    fontSize = 13.sp
                                                )
                                            }
                                        },
                                        onClick = {
                                            val next = ((Math.round((speedState + 0.1f) * 10f)) / 10f).coerceIn(0.25f, 3.0f)
                                            AudioPlayerManager.setSpeed(next)
                                        }
                                    )

                                    // Fixed speed options in descending order: 2x, 1.5x, 1x, 0.75x, .50x
                                    val fixedSpeeds = listOf(2.0f, 1.5f, 1.0f, 0.75f, 0.5f)
                                    fixedSpeeds.forEach { speed ->
                                        val isSelected = Math.abs(speedState - speed) < 0.01f
                                        val label = when (speed) {
                                            2.0f -> "2x"
                                            1.5f -> "1.5x"
                                            1.0f -> "1x"
                                            0.75f -> "0.75x"
                                            0.5f -> ".50x"
                                            else -> formatPlaybackSpeed(speed)
                                        }
                                        DropdownMenuItem(
                                            text = {
                                                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                                                    Text(
                                                        text = label,
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                                    )
                                                }
                                            },
                                            onClick = {
                                                AudioPlayerManager.setSpeed(speed)
                                                showSpeedDropdown = false
                                            }
                                        )
                                    }

                                    // - Button to fine tune speed by -0.1x per click
                                    DropdownMenuItem(
                                        text = {
                                            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                                Text(
                                                    text = "-0.1x",
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    fontSize = 13.sp
                                                )
                                            }
                                        },
                                        onClick = {
                                            val next = ((Math.round((speedState - 0.1f) * 10f)) / 10f).coerceIn(0.25f, 3.0f)
                                            AudioPlayerManager.setSpeed(next)
                                        }
                                    )
                                }
                            }

                            // 2. Autoplay Toggle (Icon only, no background change, no text, no toast)
                            val isAutoPlay by AudioPlayerManager.isAutoPlayEnabled.collectAsStateWithLifecycle()
                            IconButton(
                                onClick = {
                                    AudioPlayerManager.toggleAutoPlay()
                                },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = if (isAutoPlay) Icons.Filled.PlaylistPlay else Icons.Filled.PlaylistRemove,
                                    contentDescription = "Autoplay",
                                    tint = if (isAutoPlay) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            // Practice Mode (Listen & Imitate with Silence Detection)
                            val practiceColor = if (isPracticeMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (isPracticeMode) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent
                                    )
                                    .combinedClickable(
                                        onClick = {
                                            if (isPracticeMode) {
                                                AudioPlayerManager.togglePracticeMode(context, viewModel.repository)
                                                Toast.makeText(context, Loc.getText("practice_mode_off"), Toast.LENGTH_SHORT).show()
                                            } else {
                                                if (track.practiceSegments.isNullOrBlank()) {
                                                    // Present clear setup sheet so user can choose source and pause multiplier
                                                    showPracticeSetupSheet = true
                                                } else {
                                                    val repo = viewModel.repository
                                                    val willAnalyze = AudioPlayerManager.needsReanalysis(track, context)
                                                    AudioPlayerManager.togglePracticeMode(context, repo)
                                                    if (willAnalyze) {
                                                        Toast.makeText(context, Loc.getText("practice_mode_analyzing"), Toast.LENGTH_SHORT).show()
                                                    } else {
                                                        val src = track.getPracticeSegmentsSource() ?: AudioPlayerManager.segmentSource
                                                        val srcName = when (src) {
                                                            "MANUAL" -> Loc.getText("practice_source_manual_short")
                                                            "SUBTITLES" -> Loc.getText("practice_source_subtitles_short")
                                                            else -> Loc.getText("practice_source_silence_short")
                                                        }
                                                        Toast.makeText(context, "${Loc.getText("practice_mode_on")}: $srcName", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            }
                                        },
                                        onLongClick = {
                                            showPracticeSetupSheet = true
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isPracticeAnalyzing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Filled.RecordVoiceOver,
                                        contentDescription = Loc.getText("practice_mode"),
                                        tint = practiceColor,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }

                            // 3. Queue Button
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { showQueueSheet = !showQueueSheet }
                                    .height(38.dp)
                                    .background(
                                        if (showQueueSheet) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent
                                    )
                                    .padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.QueueMusic,
                                    contentDescription = "Queue Icon",
                                    tint = if (showQueueSheet) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "${currentQueueState.size}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (showQueueSheet) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            }

                            // 5. Sleep Timer Button
                            var showSleepDropdown by remember { mutableStateOf(false) }
                            Box {
                                val isTimerRunning = sleepTimerState > 0 || sleepTimerState == -1
                                val timerColor = if (isTimerRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { showSleepDropdown = true }
                                        .height(44.dp)
                                        .padding(horizontal = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Timer,
                                        contentDescription = "Sleep Timer",
                                        tint = timerColor,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    if (isTimerRunning) {
                                        Text(
                                            text = if (sleepTimerState == -1) "EOF" else "${(sleepTimerState + 59) / 60}m",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = timerColor
                                        )
                                    }
                                }
                                DropdownMenu(
                                    expanded = showSleepDropdown,
                                    onDismissRequest = { showSleepDropdown = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Off") },
                                        onClick = {
                                            AudioPlayerManager.cancelSleepTimer()
                                            showSleepDropdown = false
                                        }
                                    )
                                    listOf(5, 10, 15, 30, 45, 60).forEach { min ->
                                        DropdownMenuItem(
                                            text = { Text("${min}m") },
                                            onClick = {
                                                AudioPlayerManager.startSleepTimer(min)
                                                showSleepDropdown = false
                                            }
                                        )
                                    }
                                    DropdownMenuItem(
                                        text = { Text(Loc.getText("ends_at")) },
                                        onClick = {
                                            AudioPlayerManager.setSleepAtEnd()
                                            showSleepDropdown = false
                                        }
                                    )
                                }
                            }
                        }



                        // Playback Queue sheet inside the glassmorphic card itself
                        if (showQueueSheet) {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                                modifier = Modifier.padding(vertical = 4.dp)
                            )

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                // Header bar with title and clear button
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 4.dp, vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.QueueMusic,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = "${Loc.getText("queue")} (${currentQueueState.size})",
                                            style = MaterialTheme.typography.titleSmall.copy(
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp
                                            ),
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    if (currentQueueState.size > 1) {
                                        TextButton(
                                            onClick = { AudioPlayerManager.clearQueue() },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                            modifier = Modifier.height(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.ClearAll,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(3.dp))
                                            Text(
                                                text = Loc.getText("clear_queue"),
                                                fontSize = 10.5.sp,
                                                color = MaterialTheme.colorScheme.error,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                }

                                // Action bar for selected queue tracks
                                if (selectedQueueIndices.isNotEmpty()) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 6.dp, top = 2.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "${selectedQueueIndices.size} ${Loc.getText("selected")}",
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            TextButton(onClick = { selectedQueueIndices = emptySet() }) {
                                                Text(Loc.getText("cancel"), fontSize = 11.sp)
                                            }
                                            IconButton(
                                                onClick = {
                                                    AudioPlayerManager.removeTracksFromQueue(selectedQueueIndices.toList())
                                                    selectedQueueIndices = emptySet()
                                                }
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.Delete,
                                                    contentDescription = "Remove chosen files from queue",
                                                    tint = MaterialTheme.colorScheme.error,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }
                                    }
                                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                                    Spacer(modifier = Modifier.height(4.dp))
                                }

                                if (currentQueueState.isEmpty()) {
                                    Text(
                                        text = Loc.getText("empty_queue"),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 12.sp,
                                        modifier = Modifier.padding(8.dp)
                                    )
                                } else {
                                    // Scrollable container bounded to comfortable height so all 20+ items are scrollable & visible
                                    val queueScrollState = rememberScrollState()
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(max = 240.dp)
                                            .verticalScroll(queueScrollState)
                                    ) {
                                        currentQueueState.forEachIndexed { idx, qTrack ->
                                            val isRunning = qTrack.id == track.id
                                            val isChosen = selectedQueueIndices.contains(idx)

                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .background(
                                                        if (isChosen) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                                        else if (isRunning) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                                                        else Color.Transparent,
                                                        RoundedCornerShape(8.dp)
                                                    )
                                                    .combinedClickable(
                                                        onClick = {
                                                            if (selectedQueueIndices.isNotEmpty()) {
                                                                selectedQueueIndices = if (isChosen) {
                                                                    selectedQueueIndices - idx
                                                                } else {
                                                                    selectedQueueIndices + idx
                                                                }
                                                            } else {
                                                                AudioPlayerManager.playTrack(qTrack, currentQueueState)
                                                            }
                                                        },
                                                        onLongClick = {
                                                            selectedQueueIndices = selectedQueueIndices + idx
                                                        }
                                                    )
                                                    .padding(vertical = 8.dp, horizontal = 6.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(
                                                    modifier = Modifier.weight(1f),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = "${idx + 1}.",
                                                        fontSize = 12.sp,
                                                        color = if (isRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                        fontWeight = if (isRunning) FontWeight.Bold else FontWeight.Normal,
                                                        modifier = Modifier.width(24.dp)
                                                    )
                                                    Text(
                                                        text = if (isRunning) qTrack.getDisplayTitle() else middleEllipse(qTrack.getDisplayTitle(), 26),
                                                        modifier = if (isRunning) Modifier.basicMarquee() else Modifier,
                                                        fontSize = 13.sp,
                                                        fontWeight = if (isRunning) FontWeight.Bold else FontWeight.Normal,
                                                        color = if (isRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    if (isChosen) {
                                                        Icon(
                                                            imageVector = Icons.Filled.Check,
                                                            contentDescription = "Selected indicator",
                                                            tint = MaterialTheme.colorScheme.primary,
                                                            modifier = Modifier.size(16.dp).padding(end = 4.dp)
                                                        )
                                                    }
                                                    Text(
                                                        text = formatDuration(qTrack.duration),
                                                        fontSize = 12.sp,
                                                        color = if (isRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(
                                                        text = "•",
                                                        fontSize = 12.sp,
                                                        color = if (isRunning) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(
                                                        text = "${qTrack.getProgressPercent()}%",
                                                        fontSize = 12.sp,
                                                        color = if (isRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                            if (idx < currentQueueState.size - 1) {
                                                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                }
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
                val dialogTitle = if (isEditingExistingSubtitles) Loc.getText("edit_subtitles") else Loc.getText("paste_lyrics_title")
                val confirmText = if (isEditingExistingSubtitles) Loc.getText("save_changes") else Loc.getText("save_subtitles")

                var textFieldValue by remember {
                    mutableStateOf(TextFieldValue(subtitlePasteText, TextRange(subtitlePasteText.length)))
                }

                androidx.compose.ui.window.Dialog(
                    onDismissRequest = { showSubtitlePasteDialog = false },
                    properties = androidx.compose.ui.window.DialogProperties(
                        usePlatformDefaultWidth = false,
                        decorFitsSystemWindows = false
                    )
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth(0.96f)
                            .fillMaxHeight(0.95f)
                            .imePadding(),
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 6.dp,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp, vertical = 14.dp)
                        ) {
                            // Compact Header Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = if (isEditingExistingSubtitles) Icons.Filled.Edit else Icons.Filled.EditNote,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        text = dialogTitle,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 17.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                IconButton(
                                    onClick = { showSubtitlePasteDialog = false },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Close,
                                        contentDescription = Loc.getText("cancel"),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Sleek Single-Row Toolbar (Timestamps & Controls)
                            Surface(
                                tonalElevation = 1.dp,
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Insert current timestamp at cursor button
                                    FilledTonalButton(
                                        onClick = {
                                            val currentMs = AudioPlayerManager.currentPosition.value
                                            val tag = SubtitleParser.formatShortTimeTag(currentMs)
                                            val fullText = textFieldValue.text
                                            val start = textFieldValue.selection.min
                                            val end = textFieldValue.selection.max
                                            val insertion = "$tag "
                                            val updatedText = fullText.substring(0, start) + insertion + fullText.substring(end)
                                            val newPos = start + insertion.length
                                            textFieldValue = TextFieldValue(updatedText, TextRange(newPos))
                                        },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                        modifier = Modifier.height(32.dp),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Icon(Icons.Filled.Timer, contentDescription = null, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            "${Loc.getText("insert_current_timestamp")} ${SubtitleParser.formatShortTimeTag(playPositionState)}",
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    // Mini Audio Controls & Tap-to-Sync shortcut
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        IconButton(
                                            onClick = { AudioPlayerManager.skipBackward() },
                                            modifier = Modifier.size(30.dp)
                                        ) {
                                            Icon(Icons.Filled.FastRewind, contentDescription = "Rewind", modifier = Modifier.size(16.dp))
                                        }

                                        FilledIconButton(
                                            onClick = {
                                                if (isPlayingState) AudioPlayerManager.pause() else AudioPlayerManager.resume()
                                            },
                                            modifier = Modifier.size(30.dp),
                                            colors = IconButtonDefaults.filledIconButtonColors(
                                                containerColor = MaterialTheme.colorScheme.primary,
                                                contentColor = MaterialTheme.colorScheme.onPrimary
                                            )
                                        ) {
                                            Icon(
                                                imageVector = if (isPlayingState) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                                contentDescription = "Play/Pause",
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }

                                        IconButton(
                                            onClick = { AudioPlayerManager.skipForward() },
                                            modifier = Modifier.size(30.dp)
                                        ) {
                                            Icon(Icons.Filled.FastForward, contentDescription = "Forward", modifier = Modifier.size(16.dp))
                                        }

                                        IconButton(
                                            onClick = {
                                                if (textFieldValue.text.isNotBlank()) {
                                                    AudioPlayerManager.setSubtitleContentForCurrentTrack(textFieldValue.text)
                                                }
                                                showSubtitlePasteDialog = false
                                                showLiveSyncDialog = true
                                            },
                                            modifier = Modifier.size(30.dp)
                                        ) {
                                            Icon(
                                                Icons.Filled.TouchApp,
                                                contentDescription = Loc.getText("open_tap_to_sync"),
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(17.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Large Spacious Subtitle Editing Box taking full remaining space
                            OutlinedTextField(
                                value = textFieldValue,
                                onValueChange = {
                                    textFieldValue = it
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                placeholder = {
                                    Text(
                                        Loc.getText("subtitle_edit_placeholder"),
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                    )
                                },
                                shape = RoundedCornerShape(14.dp),
                                textStyle = LocalTextStyle.current.copy(
                                    fontSize = 15.sp,
                                    lineHeight = 24.sp,
                                    fontFamily = FontFamily.Monospace
                                ),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f),
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.08f)
                                )
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            // Footer Statistics & Action Buttons
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val linesCount = remember(textFieldValue.text) {
                                    if (textFieldValue.text.isBlank()) 0 else textFieldValue.text.lines().size
                                }
                                val linesUnit = if (linesCount == 1) Loc.getText("lines_count_singular") else Loc.getText("lines_count_plural")
                                Text(
                                    text = "$linesCount $linesUnit",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TextButton(
                                        onClick = { showSubtitlePasteDialog = false },
                                        modifier = Modifier.height(40.dp)
                                    ) {
                                        Text(Loc.getText("cancel"), fontSize = 13.5.sp)
                                    }
                                    Button(
                                        onClick = {
                                            val textToSave = textFieldValue.text
                                            if (textToSave.isNotBlank()) {
                                                AudioPlayerManager.setSubtitleContentForCurrentTrack(textToSave)
                                            } else {
                                                AudioPlayerManager.clearSubtitlesForCurrentTrack()
                                            }
                                            showSubtitlePasteDialog = false
                                            subtitlePasteText = ""
                                        },
                                        modifier = Modifier.height(40.dp),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text(confirmText, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (showLiveSyncDialog) {
                LiveSubtitleSyncDialog(
                    initialCues = subtitlesCuesState,
                    onDismiss = { showLiveSyncDialog = false },
                    onSave = { AudioPlayerManager.setSubtitleContentForCurrentTrack(it) }
                )
            }

            if (showDeleteSubtitleConfirmDialog) {
                AlertDialog(
                    onDismissRequest = { showDeleteSubtitleConfirmDialog = false },
                    icon = {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                    },
                    title = {
                        Text(
                            text = Loc.getText("confirm_delete_subtitles_title"),
                            fontWeight = FontWeight.Bold
                        )
                    },
                    text = {
                        Text(
                            text = Loc.getText("confirm_delete_subtitles_desc"),
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                AudioPlayerManager.clearSubtitlesForCurrentTrack()
                                showDeleteSubtitleConfirmDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text(Loc.getText("delete_confirm"))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDeleteSubtitleConfirmDialog = false }) {
                            Text(Loc.getText("cancel"))
                        }
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
