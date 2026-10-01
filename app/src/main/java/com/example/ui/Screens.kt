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

fun getTrackFileIcon(track: AudioTrack?): ImageVector {
    if (track == null) return Icons.Filled.Audiotrack
    return when {
        track.isVirtualScene -> Icons.Filled.MovieCreation
        SubtitleParser.isVideoFile(track.filePath) -> Icons.Filled.Videocam
        else -> Icons.Filled.Audiotrack
    }
}

fun getTrackFileIcon(filePath: String, isVirtualScene: Boolean = false): ImageVector {
    return when {
        isVirtualScene -> Icons.Filled.MovieCreation
        SubtitleParser.isVideoFile(filePath) -> Icons.Filled.Videocam
        else -> Icons.Filled.Audiotrack
    }
}

@Composable
fun AppNavigationContainer(viewModel: AppViewModel) {
    val context = LocalContext.current
    var currentScreen by remember { mutableStateOf("home") } // home, library, tasks, notebook, stats, settings
    var activeFolderIdForDetails by remember { mutableStateOf<Long?>(null) }
    var activePlaylistIdForDetails by remember { mutableStateOf<Long?>(null) }
    var activeTaskForDetails by remember { mutableStateOf<Task?>(null) }
    var editingTaskTarget by remember { mutableStateOf<Task?>(null) }
    var taskCreationSourceInfo by remember { mutableStateOf<Pair<String, Long?>?>(null) } // type, id (e.g. from Library click)
    var taskCreationPreselectedTrackIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var isCreatingTask by remember { mutableStateOf(false) }
    var showAssociatedTasksFor by remember { mutableStateOf<Triple<String, Long, String>?>(null) }

    // Notebook Note Modal State
    var editingNoteTarget by remember { mutableStateOf<Note?>(null) }
    var viewingCueNotesTarget by remember { mutableStateOf<List<Note>?>(null) }
    var isAddingNoteModalOpen by remember { mutableStateOf(false) }
    var notePrefilledText by remember { mutableStateOf("") }
    var notePrefilledComment by remember { mutableStateOf("") }
    var notePrefilledTrackId by remember { mutableStateOf<Long?>(null) }
    var notePrefilledStartMs by remember { mutableLongStateOf(0L) }
    var notePrefilledEndMs by remember { mutableLongStateOf(0L) }

    // Floating Player State (Full Screen overlay)
    var isFullPlayerExpanded by remember { mutableStateOf(false) }

    val allTasksList by viewModel.allTasks.collectAsStateWithLifecycle()
    val allTracksList by viewModel.tracks.collectAsStateWithLifecycle()
    val noteQuestionCounts by viewModel.noteQuestionCounts.collectAsStateWithLifecycle()
    val playingSnippetNoteId by com.example.player.NoteAudioPlayer.playingNoteId.collectAsStateWithLifecycle()
    val isSnippetPlaying by com.example.player.NoteAudioPlayer.isPlaying.collectAsStateWithLifecycle()
    val snippetPosition by com.example.player.NoteAudioPlayer.currentPosition.collectAsStateWithLifecycle()
    val pendingOpenTaskId by viewModel.pendingOpenTaskId.collectAsStateWithLifecycle()

    LaunchedEffect(pendingOpenTaskId, allTasksList) {
        val targetTaskId = pendingOpenTaskId
        if (targetTaskId != null && targetTaskId > 0) {
            val matchingTask = allTasksList.find { it.id == targetTaskId }
            if (matchingTask != null) {
                activeTaskForDetails = matchingTask
                isCreatingTask = false
                editingTaskTarget = null
                activeFolderIdForDetails = null
                activePlaylistIdForDetails = null
                showAssociatedTasksFor = null
                viewModel.consumePendingOpenTaskId()
            }
        }
    }

    val currentTrackState by AudioPlayerManager.currentTrack.collectAsStateWithLifecycle()
    val isPlayingState by AudioPlayerManager.isPlaying.collectAsStateWithLifecycle()
    val isInPipModeState by AudioPlayerManager.isInPipMode.collectAsStateWithLifecycle()
    val isVideoTrackGlobalState by AudioPlayerManager.isVideoTrack.collectAsStateWithLifecycle()
    val playbackSpeedState by AudioPlayerManager.playbackSpeed.collectAsStateWithLifecycle()
    val isTrackVideoState = currentTrackState?.let { SubtitleParser.isVideoFile(it.filePath) } == true || isVideoTrackGlobalState

    // Enforce RTL layout if language is Arabic
    val layoutDirection = Loc.layoutDirection
    val isDark = when (viewModel.selectedTheme) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }
    val backgroundGradient = if (isDark) {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xFF0F0D16),
                Color(0xFF13101C),
                Color(0xFF0A090F)
            )
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xFFFDFBFE),
                Color(0xFFF6F0FA),
                Color(0xFFECE3F4)
            )
        )
    }

    if (isInPipModeState && currentTrackState != null && isTrackVideoState) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            VideoPlayerSurface(modifier = Modifier.fillMaxSize())
        }
    } else {
        CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .testTag("app_scaffold"),
            bottomBar = {
                if (!isFullPlayerExpanded) {
                    Column {
                        // PERSISTENT MINI-PLAYER BAR (Spotify style)
                        if (currentTrackState != null) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { isFullPlayerExpanded = true }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                .testTag("mini_player_bar"),
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            tonalElevation = 8.dp
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(horizontal = 16.dp, vertical = 10.dp)
                                    .fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val percent = currentTrackState!!.let { track ->
                                    val durationS = track.duration / 1000
                                    val numSegments = minOf(durationS.toInt(), 100).coerceAtLeast(10)
                                    track.getProgressPercent(numSegments)
                                }

                                Icon(
                                    imageVector = getTrackFileIcon(currentTrackState),
                                    contentDescription = "Playing",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(28.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (isPlayingState) currentTrackState!!.getDisplayTitle() else middleEllipse(currentTrackState!!.getDisplayTitle(), 38), modifier = if (isPlayingState) Modifier.basicMarquee() else Modifier,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Filled.Headphones,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                                                modifier = Modifier.size(12.dp)
                                            )
                                            Spacer(modifier = Modifier.width(3.dp))
                                            val miniPlayerLiveTrack = allTracksList.find { it.id == currentTrackState?.id } ?: currentTrackState
                                            Text(
                                                text = "${miniPlayerLiveTrack?.playCount ?: 0}",
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Filled.Speed,
                                                contentDescription = "Speed",
                                                tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                                                modifier = Modifier.size(12.dp)
                                            )
                                            Spacer(modifier = Modifier.width(3.dp))
                                            Text(
                                                text = formatPlaybackSpeed(playbackSpeedState),
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Filled.TrendingUp,
                                                contentDescription = "Max Progress",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(13.dp)
                                            )
                                            Spacer(modifier = Modifier.width(3.dp))
                                            Text(
                                                text = "$percent%",
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.ExtraBold
                                            )
                                        }
                                    }
                                }
                                
                                // Controls
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = {
                                            if (isPlayingState) AudioPlayerManager.pause() else AudioPlayerManager.resume()
                                        },
                                        modifier = Modifier.testTag("mini_play_pause_btn")
                                    ) {
                                        Icon(
                                            imageVector = if (isPlayingState) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                            contentDescription = "PlayPause"
                                        )
                                    }
                                    IconButton(
                                        onClick = { AudioPlayerManager.stop() },
                                        modifier = Modifier.testTag("mini_stop_btn")
                                    ) {
                                        Icon(imageVector = Icons.Filled.Close, contentDescription = "Stop")
                                    }
                                }
                            }
                        }
                    }

                    // BOTTOM NAVIGATION BAR
                    NavigationBar(
                        modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars),
                        tonalElevation = 12.dp
                    ) {
                        NavigationBarItem(
                            selected = currentScreen == "home" && !isCreatingTask && activeFolderIdForDetails == null && activePlaylistIdForDetails == null && activeTaskForDetails == null && editingTaskTarget == null,
                            onClick = {
                                isFullPlayerExpanded = false
                                currentScreen = "home"
                                activeFolderIdForDetails = null
                                activePlaylistIdForDetails = null
                                activeTaskForDetails = null
                                editingTaskTarget = null
                                isCreatingTask = false
                            },
                            icon = { Icon(Icons.Filled.Home, "Home") },
                            label = { Text(Loc.getText("home"), fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                            modifier = Modifier.testTag("nav_home")
                        )
                        NavigationBarItem(
                            selected = currentScreen == "library" || activeFolderIdForDetails != null || activePlaylistIdForDetails != null,
                            onClick = {
                                isFullPlayerExpanded = false
                                currentScreen = "library"
                                activeFolderIdForDetails = null
                                activePlaylistIdForDetails = null
                                activeTaskForDetails = null
                                editingTaskTarget = null
                                isCreatingTask = false
                            },
                            icon = { Icon(Icons.Filled.LibraryMusic, "Library") },
                            label = { Text(Loc.getText("library"), fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                            modifier = Modifier.testTag("nav_library")
                        )
                        NavigationBarItem(
                            selected = (currentScreen == "tasks" || isCreatingTask || activeTaskForDetails != null || editingTaskTarget != null) && activeFolderIdForDetails == null && activePlaylistIdForDetails == null,
                            onClick = {
                                isFullPlayerExpanded = false
                                currentScreen = "tasks"
                                activeFolderIdForDetails = null
                                activePlaylistIdForDetails = null
                                activeTaskForDetails = null
                                editingTaskTarget = null
                                isCreatingTask = false
                            },
                            icon = { Icon(Icons.Filled.Bookmark, "Tasks") },
                            label = { Text(Loc.getText("tasks"), fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                            modifier = Modifier.testTag("nav_tasks")
                        )
                        NavigationBarItem(
                            selected = currentScreen == "notebook" && !isCreatingTask && activeFolderIdForDetails == null && activePlaylistIdForDetails == null && activeTaskForDetails == null && editingTaskTarget == null,
                            onClick = {
                                isFullPlayerExpanded = false
                                currentScreen = "notebook"
                                activeFolderIdForDetails = null
                                activePlaylistIdForDetails = null
                                activeTaskForDetails = null
                                editingTaskTarget = null
                                isCreatingTask = false
                            },
                            icon = { Icon(Icons.Filled.MenuBook, "Notebook") },
                            label = { Text(Loc.getText("notebook"), fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                            modifier = Modifier.testTag("nav_notebook")
                        )
                        NavigationBarItem(
                            selected = currentScreen == "stats",
                            onClick = {
                                isFullPlayerExpanded = false
                                currentScreen = "stats"
                                activeFolderIdForDetails = null
                                activePlaylistIdForDetails = null
                                activeTaskForDetails = null
                                editingTaskTarget = null
                                isCreatingTask = false
                            },
                            icon = { Icon(Icons.Filled.BarChart, "Stats") },
                            label = { Text(Loc.getText("stats"), fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                            modifier = Modifier.testTag("nav_stats")
                        )

                    }
                }
                }
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(backgroundGradient)
                    .padding(paddingValues)
                    .windowInsetsPadding(WindowInsets.statusBars)
            ) {
                // Back handlers for hierarchical screen/detail navigation
                if (isFullPlayerExpanded) {
                    BackHandler { isFullPlayerExpanded = false }
                } else if (editingTaskTarget != null) {
                    BackHandler { editingTaskTarget = null }
                } else if (isCreatingTask) {
                    BackHandler {
                        isCreatingTask = false
                        taskCreationSourceInfo = null
                        taskCreationPreselectedTrackIds = emptySet()
                    }
                } else if (activeTaskForDetails != null) {
                    BackHandler { activeTaskForDetails = null }
                } else if (activePlaylistIdForDetails != null) {
                    BackHandler { activePlaylistIdForDetails = null }
                } else if (activeFolderIdForDetails != null) {
                    BackHandler { activeFolderIdForDetails = null }
                } else if (currentScreen != "home") {
                    BackHandler { currentScreen = "home" }
                }

                // SCREEN ROUTING
                AnimatedContent(
                    targetState = Pair(currentScreen, isCreatingTask || activeFolderIdForDetails != null || activePlaylistIdForDetails != null || activeTaskForDetails != null || editingTaskTarget != null),
                    transitionSpec = {
                        fadeIn(animationSpec = spring()) togetherWith fadeOut(animationSpec = spring())
                    },
                    label = "main_screens"
                ) { (screen, details) ->
                    when {
                        isCreatingTask -> {
                            CreateTaskScreen(
                                viewModel = viewModel,
                                predefinedSource = taskCreationSourceInfo,
                                taskCreationPreselectedTrackIds = taskCreationPreselectedTrackIds,
                                onDismiss = {
                                    isCreatingTask = false
                                    taskCreationSourceInfo = null
                                    taskCreationPreselectedTrackIds = emptySet()
                                }
                            )
                        }
                        editingTaskTarget != null -> {
                            CreateTaskScreen(
                                viewModel = viewModel,
                                editingTask = editingTaskTarget,
                                onDismiss = { editingTaskTarget = null }
                            )
                        }
                        activeTaskForDetails != null -> {
                            TaskDetailsView(
                                task = activeTaskForDetails!!,
                                viewModel = viewModel,
                                onBack = { activeTaskForDetails = null },
                                onEdit = { task -> editingTaskTarget = task },
                                onShowAssociatedTasks = { type, id, name ->
                                    showAssociatedTasksFor = Triple(type, id, name)
                                }
                            )
                        }
                        activeFolderIdForDetails != null -> {
                            FolderDetailsView(
                                folderId = activeFolderIdForDetails!!,
                                viewModel = viewModel,
                                backPressed = { activeFolderIdForDetails = null },
                                onCreateTaskForFolder = { type, id ->
                                    taskCreationSourceInfo = Pair(type, id)
                                    isCreatingTask = true
                                },
                                onShowAssociatedTasks = { type, id, name ->
                                    showAssociatedTasksFor = Triple(type, id, name)
                                }
                            )
                        }
                        activePlaylistIdForDetails != null -> {
                            PlaylistDetailsView(
                                playlistId = activePlaylistIdForDetails!!,
                                viewModel = viewModel,
                                backPressed = { activePlaylistIdForDetails = null },
                                onCreateTaskForPlaylist = { type, id ->
                                    taskCreationSourceInfo = Pair(type, id)
                                    isCreatingTask = true
                                },
                                onShowAssociatedTasks = { type, id, name ->
                                    showAssociatedTasksFor = Triple(type, id, name)
                                }
                            )
                        }
                        screen == "home" -> {
                            HomeView(
                                viewModel = viewModel,
                                onLibraryShortcutClicked = { currentScreen = "library" },
                                onCreateTask = { isCreatingTask = true },
                                onTaskDetailsRequested = { activeTaskForDetails = it },
                                onSettingsClicked = { currentScreen = "settings" },
                                onVocabularyReviewClicked = { currentScreen = "vocab_review" }
                            )
                        }
                        screen == "library" -> {
                            LibraryView(
                                viewModel = viewModel,
                                onFolderDetailsClicked = { folderId -> activeFolderIdForDetails = folderId },
                                onPlaylistDetailsClicked = { playlistId -> activePlaylistIdForDetails = playlistId },
                                onCreateTaskForSource = { type, id ->
                                    taskCreationSourceInfo = Pair(type, id)
                                    isCreatingTask = true
                                },
                                onShowAssociatedTasks = { type, id, name ->
                                    showAssociatedTasksFor = Triple(type, id, name)
                                }
                            )
                        }
                        screen == "tasks" -> {
                            TasksView(
                                viewModel = viewModel,
                                onAddTaskClicked = { isCreatingTask = true },
                                onTaskSelected = { activeTaskForDetails = it },
                                onEdit = { task -> editingTaskTarget = task }
                            )
                        }
                        screen == "notebook" -> {
                            NotebookScreen(
                                viewModel = viewModel,
                                onAddNoteClicked = {
                                    editingNoteTarget = null
                                    notePrefilledText = ""
                                    notePrefilledComment = ""
                                    notePrefilledTrackId = null
                                    notePrefilledStartMs = 0L
                                    notePrefilledEndMs = 0L
                                    isAddingNoteModalOpen = true
                                },
                                onEditNoteClicked = { note ->
                                    editingNoteTarget = note
                                    isAddingNoteModalOpen = true
                                },
                                onNavigateToFolder = { folderId ->
                                    activeFolderIdForDetails = folderId
                                    currentScreen = "library"
                                },
                                onNavigateToTrack = { track ->
                                    if (track.parentFolderId != null) {
                                        activeFolderIdForDetails = track.parentFolderId
                                    }
                                    currentScreen = "library"
                                },
                                onPlayTrackInMainPlayer = { track, startMs ->
                                    viewModel.selectAndPlay(track)
                                    if (startMs > 0) {
                                        AudioPlayerManager.seekTo(startMs, isPhysicalTimestamp = true)
                                    }
                                },
                                onOpenVocabularyReview = { currentScreen = "vocab_review" }
                            )
                        }
                        screen == "stats" -> {
                            StatsView(viewModel = viewModel)
                        }
                        screen == "settings" -> {
                            SettingsView(
                                viewModel = viewModel,
                                onBack = { currentScreen = "home" }
                            )
                        }
                        screen == "vocab_review" -> {
                            VocabularyReviewScreen(
                                viewModel = viewModel,
                                onBack = { currentScreen = "home" },
                                onNavigateToLibrary = { currentScreen = "library" }
                            )
                        }
                    }
                }

                // AUDIO PLAYER OVERLAY (Expanded view)
                if (isFullPlayerExpanded && currentTrackState != null) {
                    AudioPlayerOverlay(
                        track = currentTrackState!!,
                        viewModel = viewModel,
                        dismiss = { isFullPlayerExpanded = false },
                        onQuickAddTaskPrompt = { trackId ->
                            taskCreationSourceInfo = Pair("TRACKS", trackId)
                            isCreatingTask = true
                            isFullPlayerExpanded = false
                        },
                        onNavigateToFolder = { id ->
                            activeFolderIdForDetails = id
                            currentScreen = "library"
                            isFullPlayerExpanded = false
                        },
                        onNavigateToPlaylist = { id ->
                            activePlaylistIdForDetails = id
                            currentScreen = "library"
                            isFullPlayerExpanded = false
                        },
                        onNavigateToTask = { task ->
                            activeTaskForDetails = task
                            isFullPlayerExpanded = false
                        },
                        onQuickAddNotePrompt = { trackId, startMs, endMs, quoteText ->
                            editingNoteTarget = null
                            notePrefilledText = quoteText
                            notePrefilledComment = ""
                            notePrefilledTrackId = trackId
                            notePrefilledStartMs = startMs
                            notePrefilledEndMs = endMs
                            isAddingNoteModalOpen = true
                        },
                        onOpenNotes = { notesList ->
                            viewingCueNotesTarget = notesList
                        }
                    )
                }

                // VIEW CUE NOTES BOTTOM SHEET (Playback / Subtitles context)
                if (viewingCueNotesTarget != null) {
                    PlaybackCueNotesBottomSheet(
                        notes = viewingCueNotesTarget!!,
                        noteQuestionCounts = noteQuestionCounts,
                        onDismiss = { viewingCueNotesTarget = null },
                        onEditNote = { note ->
                            viewingCueNotesTarget = null
                            editingNoteTarget = note
                        },
                        onDeleteNote = { note ->
                            viewModel.deleteNote(note)
                            val remaining = viewingCueNotesTarget?.filter { it.id != note.id } ?: emptyList()
                            if (remaining.isEmpty()) {
                                viewingCueNotesTarget = null
                            } else {
                                viewingCueNotesTarget = remaining
                            }
                        },
                        onToggleFavorite = { note ->
                            viewModel.toggleNoteFavorite(note)
                        },
                        onCopyNote = { note ->
                            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                            val clipText = if (note.comment.isNotBlank()) note.comment else note.text
                            val clip = android.content.ClipData.newPlainText("Hearmark Note", clipText)
                            clipboard?.setPrimaryClip(clip)
                            android.widget.Toast.makeText(context, Loc.getText("note_copied"), android.widget.Toast.LENGTH_SHORT).show()
                        }
                    )
                }

                // NOTEBOOK ADD/EDIT MODAL
                if (isAddingNoteModalOpen || editingNoteTarget != null) {
                    AddEditNoteModal(
                        initialNote = editingNoteTarget,
                        prefilledText = notePrefilledText,
                        prefilledComment = notePrefilledComment,
                        prefilledTrackId = notePrefilledTrackId,
                        prefilledStartMs = notePrefilledStartMs,
                        prefilledEndMs = notePrefilledEndMs,
                        viewModel = viewModel,
                        onDismiss = {
                            isAddingNoteModalOpen = false
                            editingNoteTarget = null
                            notePrefilledText = ""
                            notePrefilledComment = ""
                            notePrefilledTrackId = null
                            notePrefilledStartMs = 0L
                            notePrefilledEndMs = 0L
                        }
                    )
                }

                // ASSOCIATED ACTIVE TASKS DIALOG
                if (showAssociatedTasksFor != null) {
                    val associated = showAssociatedTasksFor!!
                    AssociatedTasksDialog(
                        itemType = associated.first,
                        itemId = associated.second,
                        itemName = associated.third,
                        viewModel = viewModel,
                        onDismiss = { showAssociatedTasksFor = null },
                        onViewTaskDetails = { task ->
                            activeTaskForDetails = task
                            isFullPlayerExpanded = false
                        },
                        onCreateTask = {
                            taskCreationSourceInfo = Pair(associated.first, associated.second)
                            isCreatingTask = true
                        }
                    )
                }

                // FLOATING AI ACTION BUTTON (Context-Aware Assistant - Unified AI Hub)
                if (!isFullPlayerExpanded) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(end = 16.dp, bottom = if (currentTrackState != null) 140.dp else 80.dp),
                        contentAlignment = Alignment.BottomEnd
                    ) {
                        FloatingAiButton(
                            onClick = {
                                viewModel.openAiHub(
                                    function = AiFunctionType.CHAT,
                                    track = currentTrackState,
                                    task = activeTaskForDetails
                                )
                            }
                        )
                    }
                }

                // UNIFIED AI HUB SHEET
                val isAiHubOpen by viewModel.isAiHubOpen.collectAsStateWithLifecycle()
                val aiHubInitialFunction by viewModel.aiHubInitialFunction.collectAsStateWithLifecycle()
                val aiHubInitialTrack by viewModel.aiHubInitialTrack.collectAsStateWithLifecycle()
                val aiHubInitialNotes by viewModel.aiHubInitialNotes.collectAsStateWithLifecycle()
                val aiHubInitialTask by viewModel.aiHubInitialTask.collectAsStateWithLifecycle()

                if (isAiHubOpen) {
                    UnifiedAiHubSheet(
                        viewModel = viewModel,
                        onDismiss = { viewModel.closeAiHub() },
                        initialFunction = aiHubInitialFunction,
                        initialTrack = aiHubInitialTrack,
                        initialNotes = aiHubInitialNotes,
                        initialTask = aiHubInitialTask
                    )
                }

                // GEMINI AI CHAT SHEET
                val isChatDialogOpen by viewModel.isChatDialogOpen.collectAsStateWithLifecycle()
                if (isChatDialogOpen) {
                    GeminiChatSheet(
                        viewModel = viewModel,
                        onDismiss = { viewModel.closeChatDialog() },
                        onSaveToNotebookRequested = { quoteText, explanationText, trackId, startMs, endMs ->
                            viewModel.closeChatDialog()
                            editingNoteTarget = null
                            notePrefilledText = quoteText
                            notePrefilledComment = explanationText
                            notePrefilledTrackId = trackId
                            notePrefilledStartMs = startMs ?: 0L
                            notePrefilledEndMs = endMs ?: 0L
                            isAddingNoteModalOpen = true
                        }
                    )
                }

                // AI COMPREHENSION QUIZ SHEET
                val isQuizSheetOpen by viewModel.isQuizSheetOpen.collectAsStateWithLifecycle()
                if (isQuizSheetOpen) {
                    val allNotes by viewModel.notes.collectAsStateWithLifecycle()
                    val allTracks by viewModel.tracks.collectAsStateWithLifecycle()
                    UnifiedQuizSheet(
                        quizViewModel = viewModel.quizViewModel,
                        allNotes = allNotes,
                        allTracks = allTracks,
                        onDismiss = { viewModel.closeQuizSheet() },
                        onOpenVocabularyReview = {
                            viewModel.closeQuizSheet()
                            currentScreen = "vocab_review"
                        },
                        onPlayTrackInMainPlayer = { track, ts ->
                            AudioPlayerManager.playTrack(track)
                            AudioPlayerManager.seekTo(ts)
                            AudioPlayerManager.resume()
                        }
                    )
                }

                // AI operations run asynchronously in background via AiBackgroundStatusBar without blocking application interactions or audio playback
            }
        }
    }
    }
}
