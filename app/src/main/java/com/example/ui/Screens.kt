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
                                        text = if (isPlayingState) currentTrackState!!.getDisplayTitle() else middleEllipse(currentTrackState!!.getDisplayTitle(), 26), modifier = if (isPlayingState) Modifier.basicMarquee() else Modifier,
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
                                            Text(
                                                text = "${currentTrackState!!.playCount}",
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
                    QuizSheet(
                        viewModel = viewModel,
                        onDismiss = { viewModel.closeQuizSheet() },
                        onOpenVocabularyReview = {
                            viewModel.closeQuizSheet()
                            currentScreen = "vocab_review"
                        }
                    )
                }

                // AI operations run asynchronously in background via AiBackgroundStatusBar without blocking application interactions or audio playback
            }
        }
    }
    }
}

@Composable
fun HighlightedSubtitleText(
    text: String,
    query: String,
    isCurrentMatch: Boolean,
    fontSize: Float,
    lineHeightMultiplier: Double,
    fontWeight: FontWeight,
    textColor: Color,
    modifier: Modifier = Modifier
) {
    val cleanQuery = query.trim()
    if (cleanQuery.isBlank() || !text.contains(cleanQuery, ignoreCase = true)) {
        Text(
            text = text,
            fontSize = fontSize.sp,
            lineHeight = (fontSize * lineHeightMultiplier).sp,
            fontWeight = fontWeight,
            color = textColor,
            modifier = modifier
        )
    } else {
        val annotatedString = remember(text, cleanQuery, isCurrentMatch) {
            buildAnnotatedString {
                val lowerText = text.lowercase()
                val lowerQuery = cleanQuery.lowercase()
                var startIndex = 0
                while (startIndex < text.length) {
                    val index = lowerText.indexOf(lowerQuery, startIndex)
                    if (index == -1) {
                        append(text.substring(startIndex))
                        break
                    }
                    if (index > startIndex) {
                        append(text.substring(startIndex, index))
                    }
                    val endIndex = index + lowerQuery.length
                    pushStyle(
                        SpanStyle(
                            background = if (isCurrentMatch) Color(0xFFFFD54F) else Color(0x77FFE082),
                            color = Color(0xFF1A1A1A),
                            fontWeight = FontWeight.Bold
                        )
                    )
                    append(text.substring(index, endIndex))
                    pop()
                    startIndex = endIndex
                }
            }
        }
        Text(
            text = annotatedString,
            fontSize = fontSize.sp,
            lineHeight = (fontSize * lineHeightMultiplier).sp,
            fontWeight = fontWeight,
            color = textColor,
            modifier = modifier
        )
    }
}

@Composable
fun SubtitlesPageContent(
    cues: List<SubtitleCue>,
    activeCue: SubtitleCue?,
    currentPositionMs: Long,
    fontSize: Float,
    offsetMs: Long,
    showTimestamps: Boolean = true,
    isInFocusOrPracticeMode: Boolean = false,
    onToggleTimestamps: () -> Unit = {},
    onCopyAllSubtitlesClick: () -> Unit,
    onEditSubtitlesClick: () -> Unit,
    onImportSrtClick: () -> Unit,
    onPasteLyricsClick: () -> Unit,
    onTapToSyncClick: () -> Unit,
    onDeleteSubtitlesClick: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onFontSizeChange: (Float) -> Unit,
    onAdjustOffset: (Long) -> Unit,
    onResetOffset: () -> Unit,
    onAddNoteFromCue: ((cueText: String, startMs: Long, endMs: Long) -> Unit)? = null,
    onAskAiAboutCue: ((cueText: String, startMs: Long, endMs: Long) -> Unit)? = null,
    notes: List<Note> = emptyList(),
    onOpenNotes: ((List<Note>) -> Unit)? = null
) {
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    var showMenu by remember { mutableStateOf(false) }
    var isSearchExpanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var currentMatchIndex by remember { mutableIntStateOf(0) }
    val focusRequester = remember { FocusRequester() }

    val isDarkBg = isInFocusOrPracticeMode || isSystemInDarkTheme()
    val hasTimings = remember(cues) { cues.any { it.isTimed && it.startMs >= 0L } }

    val matchingCueIndices = remember(cues, searchQuery) {
        val q = searchQuery.trim().lowercase()
        if (q.isBlank()) {
            emptyList()
        } else {
            cues.mapIndexedNotNull { index, cue ->
                if (cue.text.lowercase().contains(q)) index else null
            }
        }
    }

    LaunchedEffect(isSearchExpanded) {
        if (isSearchExpanded) {
            focusRequester.requestFocus()
        }
    }

    LaunchedEffect(matchingCueIndices, currentMatchIndex) {
        if (matchingCueIndices.isNotEmpty() && currentMatchIndex in matchingCueIndices.indices) {
            val targetIdx = matchingCueIndices[currentMatchIndex]
            listState.animateScrollToItem(index = targetIdx, scrollOffset = 0)
        }
    }

    LaunchedEffect(activeCue?.startMs) {
        if (!isSearchExpanded && hasTimings && activeCue != null) {
            val index = cues.indexOfFirst { it.startMs == activeCue.startMs }
            if (index >= 0) {
                listState.animateScrollToItem(index = index, scrollOffset = 0)
            }
        }
    }

    val cueNotesMap = remember(cues, notes) {
        val map = mutableMapOf<SubtitleCue, MutableList<Note>>()
        if (notes.isNotEmpty() && cues.isNotEmpty()) {
            for (note in notes) {
                val dedicatedCue = SubtitleParser.findDedicatedCueForNote(note, cues)
                if (dedicatedCue != null) {
                    map.getOrPut(dedicatedCue) { mutableListOf() }.add(note)
                }
            }
        }
        map
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 4.dp)
    ) {
        // Top Action Header with Expandable Search and Three-Dotted Menu
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            AnimatedVisibility(
                visible = isSearchExpanded,
                enter = fadeIn() + expandHorizontally(),
                exit = fadeOut() + shrinkHorizontally(),
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = if (isDarkBg) Color(0xFF1E2430) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                    border = BorderStroke(1.dp, if (isDarkBg) Color(0xFF334155) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = null,
                            tint = if (isDarkBg) Color(0xFF93C5FD) else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        androidx.compose.foundation.text.BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(
                                color = if (isDarkBg) Color.White else MaterialTheme.colorScheme.onSurface,
                                fontSize = 13.sp
                            ),
                            cursorBrush = SolidColor(if (isDarkBg) Color.White else MaterialTheme.colorScheme.primary),
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(focusRequester),
                            decorationBox = { innerTextField ->
                                if (searchQuery.isEmpty()) {
                                    Text(
                                        text = Loc.getText("search_subtitles_hint"),
                                        color = if (isDarkBg) Color(0xFF94A3B8) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                        fontSize = 13.sp
                                    )
                                }
                                innerTextField()
                            }
                        )

                        if (searchQuery.isNotBlank()) {
                            if (matchingCueIndices.isNotEmpty()) {
                                Text(
                                    text = "${currentMatchIndex + 1}/${matchingCueIndices.size}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isDarkBg) Color(0xFF93C5FD) else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 4.dp)
                                )
                                IconButton(
                                    onClick = {
                                        if (matchingCueIndices.isNotEmpty()) {
                                            val prev = if (currentMatchIndex <= 0) matchingCueIndices.size - 1 else currentMatchIndex - 1
                                            currentMatchIndex = prev
                                            coroutineScope.launch {
                                                listState.animateScrollToItem((matchingCueIndices[prev] - 1).coerceAtLeast(0))
                                            }
                                        }
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.KeyboardArrowUp,
                                        contentDescription = "Previous Match",
                                        modifier = Modifier.size(18.dp),
                                        tint = if (isDarkBg) Color.White else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        if (matchingCueIndices.isNotEmpty()) {
                                            val next = (currentMatchIndex + 1) % matchingCueIndices.size
                                            currentMatchIndex = next
                                            coroutineScope.launch {
                                                listState.animateScrollToItem((matchingCueIndices[next] - 1).coerceAtLeast(0))
                                            }
                                        }
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.KeyboardArrowDown,
                                        contentDescription = "Next Match",
                                        modifier = Modifier.size(18.dp),
                                        tint = if (isDarkBg) Color.White else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            } else {
                                Text(
                                    text = Loc.getText("no_matches_found"),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(horizontal = 4.dp)
                                )
                            }
                        }

                        IconButton(
                            onClick = {
                                if (searchQuery.isNotEmpty()) {
                                    searchQuery = ""
                                } else {
                                    isSearchExpanded = false
                                }
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Close Search",
                                modifier = Modifier.size(18.dp),
                                tint = if (isDarkBg) Color(0xFF94A3B8) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            if (!isSearchExpanded) {
                Spacer(modifier = Modifier.weight(1f))
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (!isSearchExpanded) {
                    IconButton(
                        onClick = { isSearchExpanded = true },
                        modifier = Modifier.size(36.dp),
                        enabled = cues.isNotEmpty()
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = "Search Subtitles",
                            tint = if (isDarkBg) {
                                if (cues.isNotEmpty()) Color(0xFFE2E8F0) else Color(0xFF64748B)
                            } else {
                                if (cues.isNotEmpty()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            }
                        )
                    }
                }

                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = "Subtitle Controls",
                            tint = if (isDarkBg) Color(0xFFE2E8F0) else MaterialTheme.colorScheme.onSurface
                        )
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        modifier = Modifier.widthIn(min = 160.dp)
                    ) {
                        // 0. Tap to Sync (المزامنة الحية)
                        DropdownMenuItem(
                            text = { Text(Loc.getText("tap_to_sync"), fontSize = 13.sp, fontWeight = FontWeight.SemiBold) },
                            onClick = {
                                showMenu = false
                                onTapToSyncClick()
                            }
                        )

                        // 0.5 Toggle Timestamps Visibility (إظهار / إخفاء التوقيت)
                        if (hasTimings) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (showTimestamps) Loc.getText("hide_timestamps") else Loc.getText("show_timestamps"),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                },
                                onClick = {
                                    showMenu = false
                                    onToggleTimestamps()
                                }
                            )
                        }

                        // 1. Copy All Subtitles / Text
                        DropdownMenuItem(
                            text = { Text(Loc.getText("copy_full_subtitles"), fontSize = 13.sp) },
                            enabled = cues.isNotEmpty(),
                            onClick = {
                                showMenu = false
                                onCopyAllSubtitlesClick()
                            }
                        )

                        // 2. Edit Subtitles / Text
                        DropdownMenuItem(
                            text = { Text(Loc.getText("edit_subtitles"), fontSize = 13.sp) },
                            enabled = cues.isNotEmpty(),
                            onClick = {
                                showMenu = false
                                onEditSubtitlesClick()
                            }
                        )

                        // 3. Import Subtitles
                        DropdownMenuItem(
                            text = { Text(Loc.getText("load_subtitles"), fontSize = 13.sp) },
                            onClick = {
                                showMenu = false
                                onImportSrtClick()
                            }
                        )

                        // 4. Paste Subtitles / Lyrics
                        DropdownMenuItem(
                            text = { Text(Loc.getText("paste_subtitles"), fontSize = 13.sp) },
                            onClick = {
                                showMenu = false
                                onPasteLyricsClick()
                            }
                        )

                        // 5. Delete Subtitles
                        DropdownMenuItem(
                            text = { Text(Loc.getText("clear_subtitles"), fontSize = 13.sp, color = MaterialTheme.colorScheme.error) },
                            enabled = cues.isNotEmpty(),
                            onClick = {
                                showMenu = false
                                onDeleteSubtitlesClick()
                            }
                        )

                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                        // 6. Font Size Controls
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "${Loc.getText("subtitles_size")} (${fontSize.toInt()})",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedButton(
                                    onClick = { onFontSizeChange((fontSize - 2f).coerceAtLeast(10f)) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(32.dp),
                                    contentPadding = PaddingValues(0.dp),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("A-", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                                OutlinedButton(
                                    onClick = { onFontSizeChange((fontSize + 2f).coerceAtMost(32f)) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(32.dp),
                                    contentPadding = PaddingValues(0.dp),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("A+", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        if (hasTimings) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                            // 7. Sync Delay Controls (Open-ended adjustment with reset)
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = Loc.getText("subtitles_offset"),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    val offsetSec = offsetMs / 1000.0
                                    val formattedOffset = if (offsetMs == 0L) "0.0s" else String.format(Locale.US, "%+.1fs", offsetSec)
                                    Surface(
                                        color = if (offsetMs != 0L) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = formattedOffset,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (offsetMs != 0L) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))

                                // Fast Adjust (+/- 0.5s)
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedButton(
                                        onClick = { onAdjustOffset(-500L) },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(32.dp),
                                        contentPadding = PaddingValues(0.dp),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("-0.5s", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                    OutlinedButton(
                                        onClick = { onAdjustOffset(500L) },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(32.dp),
                                        contentPadding = PaddingValues(0.dp),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("+0.5s", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                // Fine Adjust (+/- 0.1s)
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedButton(
                                        onClick = { onAdjustOffset(-100L) },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(28.dp),
                                        contentPadding = PaddingValues(0.dp),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text("-0.1s", fontSize = 10.sp)
                                    }
                                    OutlinedButton(
                                        onClick = { onAdjustOffset(100L) },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(28.dp),
                                        contentPadding = PaddingValues(0.dp),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text("+0.1s", fontSize = 10.sp)
                                    }
                                }

                                if (offsetMs != 0L) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    FilledTonalButton(
                                        onClick = { onResetOffset() },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(30.dp),
                                        contentPadding = PaddingValues(0.dp),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(Loc.getText("reset_offset"), fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (cues.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = Loc.getText("no_subtitles_found"),
                        fontSize = 13.sp,
                        color = if (isDarkBg) Color(0xFF94A3B8) else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilledTonalIconButton(
                            onClick = onTapToSyncClick,
                            modifier = Modifier.size(56.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.TouchApp,
                                contentDescription = Loc.getText("tap_to_sync"),
                                modifier = Modifier.size(26.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        FilledTonalIconButton(
                            onClick = onImportSrtClick,
                            modifier = Modifier.size(56.dp),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.FileUpload,
                                contentDescription = Loc.getText("load_subtitles"),
                                modifier = Modifier.size(26.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        FilledTonalIconButton(
                            onClick = onPasteLyricsClick,
                            modifier = Modifier.size(56.dp),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.EditNote,
                                contentDescription = Loc.getText("paste_subtitles"),
                                modifier = Modifier.size(26.dp),
                                tint = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }
            }
        } else {
            SelectionContainer {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(if (hasTimings) 8.dp else 12.dp),
                    contentPadding = PaddingValues(top = 40.dp, bottom = 220.dp)
                ) {
                    itemsIndexed(
                        items = cues,
                        key = { index, cue -> if (cue.isTimed && cue.startMs >= 0L) "cue_${cue.id}_${cue.startMs}" else "cue_idx_$index" }
                    ) { index, cue ->
                        val isCurrentSearchMatch = matchingCueIndices.getOrNull(currentMatchIndex) == index
                        if (hasTimings) {
                            val isTimedCue = cue.isTimed && cue.startMs >= 0L
                            val isActive = isTimedCue && activeCue != null && cue.startMs == activeCue.startMs
                            val isMatched = matchingCueIndices.contains(index)
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .then(
                                        if (isTimedCue) Modifier.clickable { onSeekTo(cue.startMs) } else Modifier
                                    ),
                                color = when {
                                    isCurrentSearchMatch -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.85f)
                                    isActive -> if (isDarkBg) Color(0xFF1E293B) else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                                    isMatched -> if (isDarkBg) Color(0xFF242C38) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    else -> if (isDarkBg) Color(0xFF141820) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                                },
                                border = when {
                                    isCurrentSearchMatch -> BorderStroke(2.dp, MaterialTheme.colorScheme.secondary)
                                    isActive -> BorderStroke(1.2.dp, if (isDarkBg) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                                    isMatched -> BorderStroke(1.dp, if (isDarkBg) Color(0xFF475569) else MaterialTheme.colorScheme.outlineVariant)
                                    else -> if (isDarkBg) BorderStroke(0.6.dp, Color(0xFF334155).copy(alpha = 0.35f)) else null
                                },
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = if (showTimestamps && isTimedCue) 8.dp else 10.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (showTimestamps && isTimedCue) {
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = if (isDarkBg) {
                                                    if (isActive) Color.Black.copy(alpha = 0.50f) else Color.Black.copy(alpha = 0.35f)
                                                } else {
                                                    if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.40f)
                                                },
                                                border = BorderStroke(
                                                    0.5.dp,
                                                    if (isDarkBg) {
                                                        if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f) else Color(0xFF334155).copy(alpha = 0.30f)
                                                    } else {
                                                        if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                                                    }
                                                )
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                ) {
                                                    val timestampColor = if (isDarkBg) {
                                                        if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.70f) else Color(0xFF64748B)
                                                    } else {
                                                        if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.75f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.50f)
                                                    }
                                                    Icon(
                                                        imageVector = Icons.Filled.AccessTime,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(10.dp),
                                                        tint = timestampColor
                                                    )
                                                    Text(
                                                        text = formatDuration(cue.startMs),
                                                        fontSize = 10.sp,
                                                        fontWeight = if (isCurrentSearchMatch) FontWeight.Bold else FontWeight.Normal,
                                                        color = timestampColor
                                                    )
                                                }
                                            }
                                        } else {
                                            Spacer(modifier = Modifier.width(1.dp))
                                        }

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            if (notes.isNotEmpty()) {
                                                val matchingNotes = cueNotesMap[cue] ?: emptyList()
                                                if (matchingNotes.isNotEmpty()) {
                                                    val cueEnd = if (cue.endMs > cue.startMs) cue.endMs else (cue.startMs + 5000L)
                                                    IconButton(
                                                        onClick = {
                                                            if (onOpenNotes != null) {
                                                                onOpenNotes(matchingNotes)
                                                            } else if (onAddNoteFromCue != null) {
                                                                onAddNoteFromCue(cue.text, cue.startMs, cueEnd)
                                                            }
                                                        },
                                                        modifier = Modifier.size(24.dp)
                                                    ) {
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(1.dp)
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Filled.Note,
                                                                contentDescription = Loc.getText("view_note"),
                                                                modifier = Modifier.size(15.dp),
                                                                tint = MaterialTheme.colorScheme.primary
                                                            )
                                                            if (matchingNotes.size > 1) {
                                                                Text(
                                                                    text = "${matchingNotes.size}",
                                                                    fontSize = 10.sp,
                                                                    fontWeight = FontWeight.Bold,
                                                                    color = MaterialTheme.colorScheme.primary
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }

                                            if (onAskAiAboutCue != null) {
                                                IconButton(
                                                    onClick = {
                                                        onAskAiAboutCue(cue.text, cue.startMs, if (cue.endMs > cue.startMs) cue.endMs else (cue.startMs + 5000L))
                                                    },
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Filled.AutoAwesome,
                                                        contentDescription = Loc.getText("gemini_ai_assistant"),
                                                        modifier = Modifier.size(15.dp),
                                                        tint = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                            }

                                            if (onAddNoteFromCue != null) {
                                                IconButton(
                                                    onClick = {
                                                        onAddNoteFromCue(cue.text, cue.startMs, if (cue.endMs > cue.startMs) cue.endMs else (cue.startMs + 5000L))
                                                    },
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Filled.EditNote,
                                                        contentDescription = Loc.getText("add_note"),
                                                        modifier = Modifier.size(16.dp),
                                                        tint = if (isDarkBg) {
                                                            if (isActive) MaterialTheme.colorScheme.primary else Color(0xFF94A3B8)
                                                        } else {
                                                            if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    HighlightedSubtitleText(
                                        text = cue.text,
                                        query = searchQuery,
                                        isCurrentMatch = isCurrentSearchMatch,
                                        fontSize = fontSize,
                                        lineHeightMultiplier = 1.4,
                                        fontWeight = if (isActive || isCurrentSearchMatch) FontWeight.SemiBold else FontWeight.Normal,
                                        textColor = if (isDarkBg) {
                                            if (isActive) Color.White else Color(0xFFF1F5F9)
                                        } else {
                                            if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        } else {
                            // Non-timed continuous plain text reading mode
                            val isMatched = matchingCueIndices.contains(index)
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp)),
                                color = when {
                                    isCurrentSearchMatch -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                                    isMatched -> if (isDarkBg) Color(0xFF242C38) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                                    else -> if (isDarkBg) Color(0xFF141820) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                                },
                                border = when {
                                    isCurrentSearchMatch -> BorderStroke(1.5.dp, MaterialTheme.colorScheme.secondary)
                                    isMatched -> BorderStroke(1.dp, if (isDarkBg) Color(0xFF475569) else MaterialTheme.colorScheme.outlineVariant)
                                    else -> if (isDarkBg) BorderStroke(0.6.dp, Color(0xFF334155).copy(alpha = 0.35f)) else null
                                },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                HighlightedSubtitleText(
                                    text = cue.text,
                                    query = searchQuery,
                                    isCurrentMatch = isCurrentSearchMatch,
                                    fontSize = fontSize,
                                    lineHeightMultiplier = 1.5,
                                    fontWeight = FontWeight.Normal,
                                    textColor = if (isDarkBg) Color(0xFFF1F5F9) else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 12.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- SUB-SCREEN 1: HOME VIEW ---
@Composable
fun HomeView(
    viewModel: AppViewModel,
    onLibraryShortcutClicked: () -> Unit,
    onCreateTask: () -> Unit,
    onTaskDetailsRequested: (Task) -> Unit,
    onSettingsClicked: () -> Unit,
    onVocabularyReviewClicked: () -> Unit = {}
) {
    val tracks by viewModel.tracks.collectAsStateWithLifecycle()
    val activeTasks by viewModel.activeTasks.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val allTaskProgress by viewModel.allTaskProgress.collectAsStateWithLifecycle()
    val todayDailyProgressList by viewModel.todayDailyProgress.collectAsStateWithLifecycle()
    val allVocabQuestions by viewModel.allVocabularyQuestions.collectAsStateWithLifecycle()

    val dayOfWeekToday = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
    val todayStr = when (dayOfWeekToday) {
        Calendar.SUNDAY -> "SUNDAY"
        Calendar.MONDAY -> "MONDAY"
        Calendar.TUESDAY -> "TUESDAY"
        Calendar.WEDNESDAY -> "WEDNESDAY"
        Calendar.THURSDAY -> "THURSDAY"
        Calendar.FRIDAY -> "FRIDAY"
        Calendar.SATURDAY -> "SATURDAY"
        else -> ""
    }

    val todayTasks = remember(activeTasks, todayStr) {
        activeTasks.filter { it.scheduledDays.split(",").contains(todayStr) }
    }

    val sortedTodayTasks = remember(todayTasks, todayDailyProgressList) {
        todayTasks.sortedWith(
            compareBy<Task> { task ->
                val isDoneOverall = task.isCompleted
                val dailyTarget = task.dailyTargetValue
                val isDailyDone = if (dailyTarget != null && dailyTarget > 0) {
                    val currentPlays = todayDailyProgressList.find { it.taskId == task.id }?.completedPlayCount ?: 0
                    currentPlays >= dailyTarget
                } else false
                if (isDoneOverall || isDailyDone) 1 else 0
            }
        )
    }

    // Find continuation track based on custom adaptive logic:
    val latestResumableTrack = remember(tracks, activeTasks, allTaskProgress) {
        val dayOfWeekToday = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_WEEK)
        val todayStr = when (dayOfWeekToday) {
            java.util.Calendar.SUNDAY -> "SUNDAY"
            java.util.Calendar.MONDAY -> "MONDAY"
            java.util.Calendar.TUESDAY -> "TUESDAY"
            java.util.Calendar.WEDNESDAY -> "WEDNESDAY"
            java.util.Calendar.THURSDAY -> "THURSDAY"
            java.util.Calendar.FRIDAY -> "FRIDAY"
            java.util.Calendar.SATURDAY -> "SATURDAY"
            else -> ""
        }

        // Today's active tasks
        val todayActiveTasks = activeTasks.filter { task ->
            task.scheduledDays.split(",").any { it.trim() == todayStr }
        }
        val todayActiveTaskIds = todayActiveTasks.map { it.id }.toSet()

        // Incomplete tracks for today's active tasks
        val allProgressForTodayTasks = allTaskProgress.filter { it.taskId in todayActiveTaskIds && !it.isTrackCompleted }
        val todayTrackIds = allProgressForTodayTasks.map { it.trackId }.toSet()
        val todayCandidates = tracks.filter { it.id in todayTrackIds && it.lastPosition > 0 }
            .sortedByDescending { it.lastPosition }

        if (todayCandidates.isNotEmpty()) {
            todayCandidates.first()
        } else {
            // Fallback (No scheduled tasks today, or all today's task tracks are completed):
            // Find incomplete tracks associated with any other active task in the app
            val activeTaskIds = activeTasks.map { it.id }.toSet()
            val otherActiveTaskProgresses = allTaskProgress.filter { it.taskId in activeTaskIds && !it.isTrackCompleted }
            val otherActiveTaskTrackIds = otherActiveTaskProgresses.map { it.trackId }.toSet()
            val fallbackCandidates = tracks.filter { it.id in otherActiveTaskTrackIds && it.lastPosition > 0 }
                .sortedByDescending { it.lastPosition }

            if (fallbackCandidates.isNotEmpty()) {
                fallbackCandidates.first()
            } else {
                // Fallback (No active tasks exist at all, or all tracks associated with active tasks are fully completed):
                // Show incomplete tracks that are NOT associated with any active task
                val activeAssociatedTrackIds = allTaskProgress.filter { it.taskId in activeTaskIds }.map { it.trackId }.toSet()
                val unassociatedIncompleteCandidates = tracks.filter {
                    it.lastPosition > 0 &&
                    it.getProgressPercent() < 100 &&
                    it.id !in activeAssociatedTrackIds
                }.sortedByDescending { it.lastPosition }

                unassociatedIncompleteCandidates.firstOrNull()
            }
        }
    }

    val activeTaskTracks = remember(activeTasks, tracks) {
        tracks.take(4)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // App Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_logo),
                        contentDescription = "Hearmark Logo",
                        modifier = Modifier
                            .size(46.dp)
                            .padding(end = 10.dp)
                            .testTag("app_logo")
                    )
                    Column {
                        Text(
                            text = Loc.getText("app_name"),
                            fontSize = 24.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "🗓️ " + SimpleDateFormat("EEEE, dd MMM", Locale(Loc.currentLanguage)).format(Date()),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onVocabularyReviewClicked,
                        modifier = Modifier.testTag("btn_home_vocab_review")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Spellcheck,
                            contentDescription = Loc.getText("vocab_review_title"),
                            tint = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    IconButton(
                        onClick = onSettingsClicked,
                        modifier = Modifier.testTag("btn_home_settings")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
        }

        // CONTINUE LISTENING CARD
        if (latestResumableTrack != null) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("continue_card"),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = Loc.getText("continue_listening").uppercase(),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                letterSpacing = 0.8.sp
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .background(MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.12f), RoundedCornerShape(100.dp))
                                    .padding(horizontal = 7.dp, vertical = 2.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Headphones,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(11.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "${latestResumableTrack.playCount}",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(6.dp))
                        
                        Text(
                            text = middleEllipse(latestResumableTrack.getDisplayTitle(), 30),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        
                        Spacer(modifier = Modifier.height(10.dp))

                        val numSegments = latestResumableTrack.getAdaptiveNumSegments()
                        val listenedRanges = remember(latestResumableTrack.listenedSegments, numSegments) {
                            val bitSet = latestResumableTrack.getListenedBitSet(numSegments)
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

                        val percent = latestResumableTrack.getProgressPercent(numSegments)

                        // Display custom dual seek progress
                        val playbackFraction = latestResumableTrack.lastPosition.toFloat() / latestResumableTrack.duration.toFloat()
                        
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f))
                        ) {
                            // Reach coverage bar (max historical reach with custom Canvas segments)
                            val segmentsColor = MaterialTheme.colorScheme.primary
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                if (numSegments > 0) {
                                    val segmentWidth = size.width / numSegments
                                    for (range in listenedRanges) {
                                        val startX = range.first * segmentWidth
                                        val rangeWidth = (range.last - range.first + 1) * segmentWidth
                                        drawRect(
                                            color = segmentsColor.copy(alpha = 0.35f),
                                            topLeft = androidx.compose.ui.geometry.Offset(x = startX, y = 0f),
                                            size = androidx.compose.ui.geometry.Size(width = rangeWidth, height = size.height)
                                        )
                                    }
                                }
                            }
                            // Playback progress bar (current play cursor)
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(fraction = playbackFraction.coerceIn(0.01f, 1f))
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(5.dp))
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = formatDuration(latestResumableTrack.lastPosition),
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                            )
                            Text(
                                text = "$percent% ${Loc.getText("reach")}",
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = formatDuration(latestResumableTrack.duration),
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Skip backward / rewind (-10s)
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .background(MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.08f), CircleShape)
                                        .clickable {
                                            val newPos = (latestResumableTrack.lastPosition - 10000).coerceAtLeast(0L)
                                            viewModel.selectAndPlay(latestResumableTrack)
                                            AudioPlayerManager.seekTo(newPos, isPhysicalTimestamp = false)
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.FastRewind,
                                        contentDescription = "Rewind",
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                                
                                // Play / Resume button
                                val isPlayingState by AudioPlayerManager.isPlaying.collectAsStateWithLifecycle()
                                val currentTrackState by AudioPlayerManager.currentTrack.collectAsStateWithLifecycle()
                                val isCurrentResumable = currentTrackState?.id == latestResumableTrack.id
                                
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                                        .clickable {
                                            if (isCurrentResumable) {
                                                if (isPlayingState) AudioPlayerManager.pause() else AudioPlayerManager.resume()
                                            } else {
                                                viewModel.selectAndPlay(latestResumableTrack)
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (isCurrentResumable && isPlayingState) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                        contentDescription = "PlayResume",
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                                
                                // Skip forward (+30s)
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .background(MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.08f), CircleShape)
                                        .clickable {
                                            val newPos = (latestResumableTrack.lastPosition + 30000).coerceAtMost(latestResumableTrack.duration)
                                            viewModel.selectAndPlay(latestResumableTrack)
                                            AudioPlayerManager.seekTo(newPos, isPhysicalTimestamp = false)
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.FastForward,
                                        contentDescription = "Forward",
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }
                            
                            // Speed badge
                            val speedVal by AudioPlayerManager.playbackSpeed.collectAsStateWithLifecycle()
                            Box(
                                modifier = Modifier
                                    .background(MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.08f), RoundedCornerShape(6.dp))
                                    .clickable {
                                        val nextSpeed = when (speedVal) {
                                            1.0f -> 1.25f
                                            1.25f -> 1.5f
                                            1.5f -> 2.0f
                                            else -> 1.0f
                                        }
                                        AudioPlayerManager.setSpeed(nextSpeed)
                                    }
                                    .padding(horizontal = 7.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = formatPlaybackSpeed(speedVal),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }


        // TODAY'S SCHEDULED SMART TASKS
        item {
            Text(
                text = Loc.getText("todays_tasks"),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        if (sortedTodayTasks.isNotEmpty()) {
            items(sortedTodayTasks, key = { it.id }) { task ->
                val isDoneOverall = task.isCompleted
                val dailyTarget = task.dailyTargetValue
                val isDailyDone = if (dailyTarget != null && dailyTarget > 0) {
                    val currentPlays = todayDailyProgressList.find { it.taskId == task.id }?.completedPlayCount ?: 0
                    currentPlays >= dailyTarget
                } else false
                val isCompletedToday = isDoneOverall || isDailyDone

                val progressList by viewModel.repository.getProgressForTaskFlow(task.id).collectAsStateWithLifecycle(emptyList())
                val overallPercent = if (progressList.isNotEmpty()) {
                    var totalCompleted = 0
                    var totalRequired = 0
                    val targetVal = task.targetValue
                    if (task.targetType == "PLAY_COUNT") {
                        progressList.forEach { p ->
                            totalCompleted += minOf(p.completedPlayCount, targetVal)
                            totalRequired += targetVal
                        }
                    } else {
                        progressList.forEach { p ->
                            totalCompleted += minOf(p.getDaysList().size, targetVal)
                            totalRequired += targetVal
                        }
                    }
                    val isAllTracksDone = progressList.isNotEmpty() && progressList.all { it.isTrackCompleted }
                    if (isAllTracksDone) 1.0f else if (totalRequired > 0) totalCompleted.toFloat() / totalRequired.toFloat() else 0f
                } else {
                    0f
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onTaskDetailsRequested(task) }
                        .testTag("home_task_card_${task.id}"),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                ) {
                    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.widthIn(min = 32.dp)
                            ) {
                                Icon(
                                    imageVector = if (isCompletedToday) Icons.Filled.CheckCircle else Icons.Filled.Bookmark,
                                    contentDescription = "Task icon",
                                    tint = if (isCompletedToday) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "${(overallPercent * 100).toInt()}%",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isCompletedToday) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = task.getDisplayTitle(),
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(1.dp))
                                Text(
                                    text = task.reminderTime,
                                    fontSize = 10.5.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { overallPercent },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                                .clip(CircleShape),
                            color = if (isCompletedToday) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                        )
                    }
                }
            }
        } else {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = Loc.getText("no_tasks_today"),
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}

// --- SUB-SCREEN 2: LIBRARY VIEW (Folder / Independent / Playlists) ---
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryView(
    viewModel: AppViewModel,
    onFolderDetailsClicked: (Long) -> Unit,
    onPlaylistDetailsClicked: (Long) -> Unit,
    onCreateTaskForSource: (String, Long?) -> Unit,
    onShowAssociatedTasks: (String, Long, String) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    var activeTab by remember { mutableStateOf(0) } // 0=Folders, 1=Independent Tracks, 2=Playlists
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { 3 })
    LaunchedEffect(pagerState.currentPage) {
        activeTab = pagerState.currentPage
    }
    val folders by viewModel.folders.collectAsStateWithLifecycle()
    val independentTracks by viewModel.independentTracks.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val selectedTrackIdsSet by viewModel.selectedTrackIds.collectAsStateWithLifecycle()
    val lastImportSummary by viewModel.lastImportSummary.collectAsStateWithLifecycle()

    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var playlistInputName by remember { mutableStateOf("") }
    
    // Bulk select modes
    var isBulkSelectMode by remember { mutableStateOf(false) }
    var isFolderBulkSelectMode by remember { mutableStateOf(false) }
    var selectedFolderIds by remember { mutableStateOf(emptySet<Long>()) }

    var isPlaylistBulkSelectMode by remember { mutableStateOf(false) }
    var selectedPlaylistIds by remember { mutableStateOf(emptySet<Long>()) }

    // Global library search state
    var searchQuery by remember { mutableStateOf("") }
    var isSearchExpanded by remember { mutableStateOf(false) }

    val filteredFolders = remember(folders, searchQuery) {
        if (searchQuery.isBlank()) folders else folders.filter { it.folderName.contains(searchQuery, ignoreCase = true) }
    }
    var tracksSortBy by remember { mutableStateOf("name") }
    var tracksIsAscending by remember { mutableStateOf(true) }

    val filteredIndependentTracks = remember(independentTracks, searchQuery, tracksSortBy, tracksIsAscending) {
        val filtered = if (searchQuery.isBlank()) independentTracks else independentTracks.filter { 
            val name = it.getDisplayTitle()
            name.contains(searchQuery, ignoreCase = true) || it.fileName.contains(searchQuery, ignoreCase = true)
        }
        getSortedTracks(filtered, tracksSortBy, tracksIsAscending)
    }
    val filteredPlaylists = remember(playlists, searchQuery) {
        if (searchQuery.isBlank()) playlists else playlists.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    // Folder and tracks import control states
    var showImportFolderDialog by remember { mutableStateOf(false) }
    var importFolderNameInput by remember { mutableStateOf("") }
    var showAddToPlaylistDialogForTrack by remember { mutableStateOf<AudioTrack?>(null) }
    var queuedFoldersToImport by remember { mutableStateOf<List<Pair<String, Uri>>>(emptyList()) }

    val importTracksLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.importIndependentTracks(uris)
        }
    }

    val importFolderFilesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.importFolder(importFolderNameInput.ifBlank { "Imported Files" }, uris)
            importFolderNameInput = ""
            showImportFolderDialog = false
        }
    }

    val importFolderTreeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            val resolvedName = viewModel.getDirNameFromTreeUri(context, uri)
            queuedFoldersToImport = queuedFoldersToImport + Pair(resolvedName, uri)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = isSearchExpanded,
            label = "search_expansion_row",
            modifier = Modifier.fillMaxWidth()
        ) { expanded ->
            if (expanded) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    IconButton(onClick = {
                        isSearchExpanded = false
                        searchQuery = ""
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Collapse search",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text(Loc.getText("search_placeholder"), fontSize = 14.sp) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear", modifier = Modifier.size(20.dp))
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("global_library_search"),
                        singleLine = true,
                        shape = RoundedCornerShape(28.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                            unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
                        )
                    )
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        TabRow(
                            selectedTabIndex = activeTab,
                            containerColor = Color.Transparent,
                            contentColor = MaterialTheme.colorScheme.primary,
                            indicator = { tabPositions ->
                                if (activeTab < tabPositions.size) {
                                    TabRowDefaults.SecondaryIndicator(
                                        Modifier.tabIndicatorOffset(tabPositions[activeTab]),
                                        color = MaterialTheme.colorScheme.primary,
                                        height = 3.dp
                                    )
                                }
                            },
                            divider = {}
                        ) {
                            val tabs = listOf(
                                Pair("folders", 0),
                                Pair("independent_files", 1),
                                Pair("playlists", 2)
                            )
                            tabs.forEach { (key, index) ->
                                Tab(
                                    selected = activeTab == index,
                                    onClick = { coroutineScope.launch { pagerState.animateScrollToPage(index) } }
                                ) {
                                    Text(
                                        text = Loc.getText(key),
                                        modifier = Modifier.padding(vertical = 12.dp),
                                        fontWeight = if (activeTab == index) FontWeight.SemiBold else FontWeight.Normal,
                                        style = MaterialTheme.typography.bodyLarge.copy(letterSpacing = 0.15.sp),
                                        color = if (activeTab == index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(
                        onClick = { isSearchExpanded = true },
                        modifier = Modifier.testTag("search_icon")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))

        // TOOLBAR SYSTEM - TRACKS (Tab 1)
        if (isBulkSelectMode && activeTab == 1) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${Loc.getText("selected")}: ${selectedTrackIdsSet.size}",
                        fontWeight = FontWeight.Bold
                    )
                    Row {
                        val areAllTracksSelected = filteredIndependentTracks.isNotEmpty() && selectedTrackIdsSet.size == filteredIndependentTracks.size
                        IconButton(onClick = {
                            if (areAllTracksSelected) {
                                viewModel.clearTrackSelections()
                            } else {
                                viewModel.selectedTrackIds.value = filteredIndependentTracks.map { it.id }.toSet()
                            }
                        }) {
                            Icon(
                                imageVector = Icons.Filled.SelectAll,
                                contentDescription = Loc.getText("select_all"),
                                tint = if (areAllTracksSelected) MaterialTheme.colorScheme.primary else Color.Gray
                            )
                        }
                        IconButton(onClick = {
                            viewModel.deleteSelectedTracks()
                            isBulkSelectMode = false
                        }) {
                            Icon(Icons.Filled.Delete, "Delete selection", tint = MaterialTheme.colorScheme.error)
                        }
                        IconButton(onClick = {
                            if (selectedTrackIdsSet.isNotEmpty()) {
                                // Add unified task for selected tracks
                                val selectedTracksList = independentTracks.filter { selectedTrackIdsSet.contains(it.id) }
                                viewModel.quickAddTaskForTracks(selectedTracksList, Loc.getText("group_goal_task_title"), 3)
                                isBulkSelectMode = false
                                viewModel.clearTrackSelections()
                            }
                        }) {
                            Icon(Icons.Filled.Bookmark, "Add goal to selected", tint = MaterialTheme.colorScheme.primary)
                        }
                        IconButton(onClick = {
                            if (selectedTrackIdsSet.isNotEmpty()) {
                                val firstTrack = independentTracks.find { selectedTrackIdsSet.contains(it.id) }
                                if (firstTrack != null) {
                                    showAddToPlaylistDialogForTrack = firstTrack
                                }
                            }
                        }) {
                            Icon(Icons.Filled.AddToPhotos, "Add to Playlist", tint = MaterialTheme.colorScheme.primary)
                        }
                        IconButton(onClick = {
                            isBulkSelectMode = false
                            viewModel.clearTrackSelections()
                        }) {
                            Icon(Icons.Filled.Close, "Exit bulk mode")
                        }
                    }
                }
            }
        }

        // TOOLBAR SYSTEM - FOLDERS (Tab 0)
        if (isFolderBulkSelectMode && activeTab == 0) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${Loc.getText("selected")}: ${selectedFolderIds.size}",
                        fontWeight = FontWeight.Bold
                    )
                    Row {
                        val areAllFoldersSelected = filteredFolders.isNotEmpty() && selectedFolderIds.size == filteredFolders.size
                        IconButton(onClick = {
                            if (areAllFoldersSelected) {
                                selectedFolderIds = emptySet()
                            } else {
                                selectedFolderIds = filteredFolders.map { it.id }.toSet()
                            }
                        }) {
                            Icon(
                                imageVector = Icons.Filled.SelectAll,
                                contentDescription = Loc.getText("select_all"),
                                tint = if (areAllFoldersSelected) MaterialTheme.colorScheme.primary else Color.Gray
                            )
                        }
                        IconButton(onClick = {
                            viewModel.deleteMultipleFolders(selectedFolderIds)
                            isFolderBulkSelectMode = false
                            selectedFolderIds = emptySet()
                        }) {
                            Icon(Icons.Filled.Delete, "Delete folders", tint = MaterialTheme.colorScheme.error)
                        }
                        IconButton(onClick = {
                            if (selectedFolderIds.isNotEmpty()) {
                                viewModel.bulkAddFoldersToTasks(selectedFolderIds)
                                isFolderBulkSelectMode = false
                                selectedFolderIds = emptySet()
                                Toast.makeText(context, Loc.getText("tasks_created_for_folders"), Toast.LENGTH_SHORT).show()
                            }
                        }) {
                            Icon(Icons.Filled.Bookmark, "Create tasks", tint = MaterialTheme.colorScheme.primary)
                        }
                        IconButton(onClick = {
                            isFolderBulkSelectMode = false
                            selectedFolderIds = emptySet()
                        }) {
                            Icon(Icons.Filled.Close, "Exit folder bulk mode")
                        }
                    }
                }
            }
        }

        // TOOLBAR SYSTEM - PLAYLISTS (Tab 2)
        if (isPlaylistBulkSelectMode && activeTab == 2) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${Loc.getText("selected")}: ${selectedPlaylistIds.size}",
                        fontWeight = FontWeight.Bold
                    )
                    Row {
                        val areAllPlaylistsSelected = filteredPlaylists.isNotEmpty() && selectedPlaylistIds.size == filteredPlaylists.size
                        IconButton(onClick = {
                            if (areAllPlaylistsSelected) {
                                selectedPlaylistIds = emptySet()
                            } else {
                                selectedPlaylistIds = filteredPlaylists.map { it.id }.toSet()
                            }
                        }) {
                            Icon(
                                imageVector = Icons.Filled.SelectAll,
                                contentDescription = Loc.getText("select_all"),
                                tint = if (areAllPlaylistsSelected) MaterialTheme.colorScheme.primary else Color.Gray
                            )
                        }
                        IconButton(onClick = {
                            viewModel.deleteMultiplePlaylists(selectedPlaylistIds)
                            isPlaylistBulkSelectMode = false
                            selectedPlaylistIds = emptySet()
                        }) {
                            Icon(Icons.Filled.Delete, "Delete playlists", tint = MaterialTheme.colorScheme.error)
                        }
                        IconButton(onClick = {
                            if (selectedPlaylistIds.isNotEmpty()) {
                                viewModel.bulkAddPlaylistsToTasks(selectedPlaylistIds)
                                isPlaylistBulkSelectMode = false
                                selectedPlaylistIds = emptySet()
                                Toast.makeText(context, Loc.getText("tasks_created_for_playlists"), Toast.LENGTH_SHORT).show()
                            }
                        }) {
                            Icon(Icons.Filled.Bookmark, "Create tasks for playlists", tint = MaterialTheme.colorScheme.primary)
                        }
                        IconButton(onClick = {
                            isPlaylistBulkSelectMode = false
                            selectedPlaylistIds = emptySet()
                        }) {
                            Icon(Icons.Filled.Close, "Exit playlist bulk mode")
                        }
                    }
                }
            }
        }

        Box(modifier = Modifier.weight(1f)) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                when (page) {
                    // folders
                    0 -> {
                    var folderToRename by remember { mutableStateOf<com.example.data.Folder?>(null) }
                    
                    if (folderToRename != null) {
                        var tempName by remember(folderToRename) { mutableStateOf(folderToRename!!.folderName) }
                        AlertDialog(
                            onDismissRequest = { folderToRename = null },
                            title = { Text(Loc.getText("edit_folder_name")) },
                            text = {
                                OutlinedTextField(
                                    value = tempName,
                                    onValueChange = { tempName = it },
                                    label = { Text(Loc.getText("folder_name_label")) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            },
                            confirmButton = {
                                Button(
                                    onClick = {
                                        val toRename = folderToRename
                                        if (toRename != null && tempName.isNotBlank()) {
                                            viewModel.editFolderName(toRename.id, tempName)
                                        }
                                        folderToRename = null
                                    }
                                ) {
                                    Text(Loc.getText("save_settings"))
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { folderToRename = null }) {
                                    Text(Loc.getText("cancel"))
                                }
                            }
                        )
                    }

                    Column(modifier = Modifier.fillMaxSize()) {
                        val rootFolders by viewModel.rootFolders.collectAsStateWithLifecycle()
                        val displayFolders = if (searchQuery.isBlank()) rootFolders else filteredFolders

                        if (displayFolders.isEmpty()) {
                            Box(modifier = Modifier.weight(1f)) {
                                EmptyLibraryState("empty_folders_desc")
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(displayFolders, key = { it.id }) { folder ->
                                    FolderTreeNodeItem(
                                        folder = folder,
                                        depth = 0,
                                        viewModel = viewModel,
                                        isFolderBulkSelectMode = isFolderBulkSelectMode,
                                        selectedFolderIds = selectedFolderIds,
                                        onToggleSelectFolder = { id ->
                                            selectedFolderIds = if (selectedFolderIds.contains(id)) selectedFolderIds - id else selectedFolderIds + id
                                        },
                                        onFolderDetailsClicked = onFolderDetailsClicked,
                                        onLongClickFolder = { id ->
                                            isFolderBulkSelectMode = true
                                            selectedFolderIds = selectedFolderIds + id
                                        },
                                        onRenameFolder = { f -> folderToRename = f },
                                        onCreateTaskForSource = onCreateTaskForSource,
                                        onShowAssociatedTasks = onShowAssociatedTasks
                                    )
                                }
                            }
                        }
                    }
                }

                // independent tracks
                1 -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        if (filteredIndependentTracks.isEmpty()) {
                            Box(modifier = Modifier.weight(1f)) {
                                EmptyLibraryState("empty_tracks_desc")
                            }
                        } else {
                            TrackListSortHeader(
                                totalCount = filteredIndependentTracks.size,
                                sortBy = tracksSortBy,
                                onSortByChange = { tracksSortBy = it },
                                isAscending = tracksIsAscending,
                                onIsAscendingChange = { tracksIsAscending = it }
                            )
                            LazyColumn(
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(filteredIndependentTracks) { track ->
                                    val isSelected = selectedTrackIdsSet.contains(track.id)
                                    val isCurrentExecuting = AudioPlayerManager.currentTrack.collectAsStateWithLifecycle().value?.id == track.id
                                    val isPlaying = AudioPlayerManager.isPlaying.collectAsStateWithLifecycle().value

                                    UnifiedAudioTrackRow(
                                        track = track,
                                        isCurrentExecuting = isCurrentExecuting,
                                        isPlaying = isPlaying,
                                        viewModel = viewModel,
                                        onTrackPlaylistMenuClicked = { showAddToPlaylistDialogForTrack = it },
                                        onCreateTask = { type, id -> onCreateTaskForSource(type, id) },
                                        onShowAssociatedTasks = { type, id, name -> onShowAssociatedTasks(type, id, name) },
                                        playlistTracks = filteredIndependentTracks,
                                        isSelected = isSelected,
                                        isBulkSelectMode = isBulkSelectMode,
                                        onClick = {
                                            if (isBulkSelectMode) {
                                                viewModel.toggleTrackSelection(track.id)
                                            } else {
                                                viewModel.selectAndPlay(track, filteredIndependentTracks)
                                            }
                                        },
                                        onLongClick = {
                                            isBulkSelectMode = true
                                            viewModel.toggleTrackSelection(track.id)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // playlists
                2 -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        if (filteredPlaylists.isEmpty()) {
                            Box(modifier = Modifier.weight(1f)) {
                                EmptyLibraryState("empty_playlists_desc")
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(filteredPlaylists) { playlist ->
                                    val tracksInPl by viewModel.repository.getTracksForPlaylistFlow(playlist.id).collectAsStateWithLifecycle(emptyList())
                                    val isSelected = selectedPlaylistIds.contains(playlist.id)
                                    var showMenu by remember { mutableStateOf(false) }

                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .combinedClickable(
                                                onClick = {
                                                    if (isPlaylistBulkSelectMode) {
                                                        selectedPlaylistIds = if (isSelected) selectedPlaylistIds - playlist.id else selectedPlaylistIds + playlist.id
                                                    } else {
                                                        onPlaylistDetailsClicked(playlist.id)
                                                    }
                                                },
                                                onLongClick = {
                                                    isPlaylistBulkSelectMode = true
                                                    selectedPlaylistIds = selectedPlaylistIds + playlist.id
                                                }
                                            ),
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (isSelected) {
                                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.18f)
                                             } else {
                                                MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
                                             }
                                         ),
                                        shape = RoundedCornerShape(16.dp),
                                        border = BorderStroke(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)),
                                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(start = 14.dp, top = 10.dp, end = 4.dp, bottom = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            if (isPlaylistBulkSelectMode) {
                                                Checkbox(
                                                    checked = isSelected,
                                                    onCheckedChange = {
                                                        selectedPlaylistIds = if (isSelected) selectedPlaylistIds - playlist.id else selectedPlaylistIds + playlist.id
                                                    }
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                            } else {
                                                Box(
                                                    modifier = Modifier
                                                        .size(52.dp)
                                                        .background(
                                                            Brush.radialGradient(
                                                                listOf(
                                                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.04f)
                                                                )
                                                            ),
                                                            RoundedCornerShape(16.dp)
                                                        ),
                                                    contentAlignment = Alignment.Center
                                                 ) {
                                                     Icon(
                                                         imageVector = Icons.AutoMirrored.Filled.PlaylistPlay,
                                                         contentDescription = "Playlist",
                                                         tint = MaterialTheme.colorScheme.primary,
                                                         modifier = Modifier.size(26.dp)
                                                     )
                                                 }
                                                 Spacer(modifier = Modifier.width(16.dp))
                                            }
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                     text = playlist.name,
                                                     style = MaterialTheme.typography.bodyLarge.copy(
                                                         fontSize = 15.sp,
                                                         fontWeight = FontWeight.SemiBold,
                                                         fontFamily = FontFamily.SansSerif
                                                     ),
                                                     color = MaterialTheme.colorScheme.onSurface
                                                 )
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                     text = String.format(Loc.getText("files_count"), tracksInPl.size),
                                                     style = MaterialTheme.typography.bodySmall,
                                                     color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                                                 )
                                            }

                                            // Options menu
                                            if (!isPlaylistBulkSelectMode) {
                                                Box {
                                                    IconButton(
                                                        onClick = { showMenu = true },
                                                        modifier = Modifier.size(32.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Filled.MoreVert,
                                                            contentDescription = "options",
                                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                    }
                                                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                                        DropdownMenuItem(
                                                            text = { Text(Loc.getText("play_next")) },
                                                            onClick = {
                                                                showMenu = false
                                                                viewModel.addTracksToPlayNext(tracksInPl)
                                                                Toast.makeText(context, Loc.getText("added_to_queue"), Toast.LENGTH_SHORT).show()
                                                            }
                                                        )
                                                        DropdownMenuItem(
                                                            text = { Text(Loc.getText("create_task")) },
                                                            onClick = {
                                                                showMenu = false
                                                                onCreateTaskForSource("PLAYLIST", playlist.id)
                                                            }
                                                        )
                                                        DropdownMenuItem(
                                                            text = { Text(Loc.getText("show_associated_tasks")) },
                                                            onClick = {
                                                                showMenu = false
                                                                onShowAssociatedTasks("PLAYLIST", playlist.id, playlist.name)
                                                            }
                                                        )
                                                        DropdownMenuItem(
                                                            text = { Text(Loc.getText("delete_history"), color = MaterialTheme.colorScheme.error) },
                                                            onClick = {
                                                                showMenu = false
                                                                viewModel.deletePlaylist(playlist.id, false)
                                                            }
                                                        )
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
            }
            }

            // Bottom-right Floating Action Button, context-aware and elegant
            FloatingActionButton(
                onClick = {
                    when (activeTab) {
                        0 -> { showImportFolderDialog = true }
                        1 -> { importTracksLauncher.launch("audio/*") }
                        2 -> { showCreatePlaylistDialog = true }
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(24.dp)
                    .testTag(
                        when (activeTab) {
                            0 -> "btn_import_folder"
                            1 -> "btn_import_tracks"
                            else -> "btn_create_playlist"
                        }
                    ),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = CircleShape
            ) {
                Icon(
                    imageVector = when (activeTab) {
                        0 -> Icons.Filled.Folder
                        1 -> Icons.Filled.Audiotrack
                        else -> Icons.Filled.PlaylistAdd
                    },
                    contentDescription = when (activeTab) {
                        0 -> Loc.getText("add_folder")
                        1 -> Loc.getText("add_individual_track")
                        else -> Loc.getText("add_playlist")
                    },
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }

    // IMPORT FOLDER DIALOG (REPAIRED & FULLY NATIVE - SUPPORTING BULK MULTI-DIRECTORIES)
    if (showImportFolderDialog) {
        AlertDialog(
            onDismissRequest = { 
                showImportFolderDialog = false
                queuedFoldersToImport = emptyList()
            },
            title = { Text(Loc.getText("create_folder_dialog_title")) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = if (queuedFoldersToImport.isEmpty()) Loc.getText("create_folder_dialog_desc") 
                               else Loc.getText("folders_selected_for_bulk"),
                        fontSize = 13.sp,
                        color = Color.Gray
                    )
                    
                    if (queuedFoldersToImport.isNotEmpty()) {
                        queuedFoldersToImport.forEachIndexed { index, item ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = String.format(Loc.getText("folder_number"), index + 1),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        IconButton(
                                            modifier = Modifier.size(24.dp),
                                            onClick = {
                                                queuedFoldersToImport = queuedFoldersToImport.filterIndexed { i, _ -> i != index }
                                            }
                                        ) {
                                            Icon(Icons.Filled.Delete, "Remove", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    OutlinedTextField(
                                        value = item.first,
                                        onValueChange = { newName ->
                                            queuedFoldersToImport = queuedFoldersToImport.mapIndexed { i, old ->
                                                if (i == index) Pair(newName, old.second) else old
                                            }
                                        },
                                        label = { Text(Loc.getText("folder_name_label")) },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    } else {
                        OutlinedTextField(
                            value = importFolderNameInput,
                            onValueChange = { importFolderNameInput = it },
                            label = { Text(Loc.getText("folder_name_label") + " (" + Loc.getText("optional") + ")") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    
                    Button(
                        onClick = { importFolderTreeLauncher.launch(null) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)
                    ) {
                        Text(Loc.getText("add_another_dir"))
                    }
                }
            },
            confirmButton = {
                if (queuedFoldersToImport.isNotEmpty()) {
                    Button(onClick = {
                        for (item in queuedFoldersToImport) {
                            viewModel.importFolderFromTreeUri(item.first, item.second)
                        }
                        queuedFoldersToImport = emptyList()
                        showImportFolderDialog = false
                        Toast.makeText(context, Loc.getText("importing_folders_bg"), Toast.LENGTH_SHORT).show()
                    }) {
                        Text(String.format(Loc.getText("start_bulk_import"), queuedFoldersToImport.size))
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { 
                    showImportFolderDialog = false
                    queuedFoldersToImport = emptyList()
                }) {
                    Text(Loc.getText("cancel"))
                }
            }
        )
    }

    // IMPORT SUMMARY / LOG DIALOG
    lastImportSummary?.let { summary ->
        AlertDialog(
            onDismissRequest = { viewModel.clearImportSummary() },
            icon = {
                Icon(
                    imageVector = Icons.Filled.Info,
                    contentDescription = "Import Summary Icon",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(
                    text = Loc.getText("import_summary_title"),
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Folder name badge
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Folder,
                                contentDescription = "Folder Icon",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = summary.folderName,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Stat rows
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = String.format(Loc.getText("total_processed_files"), summary.totalProcessed),
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = String.format(Loc.getText("existing_files_preserved"), summary.existingCount),
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = String.format(Loc.getText("newly_indexed_files"), summary.newlyIndexedCount),
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Newly indexed files detail
                    if (summary.newlyIndexedCount > 0) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = Loc.getText("newly_indexed_heading"),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            
                            summary.newlyIndexedFiles.forEach { fileName ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Audiotrack,
                                        contentDescription = "New Audio File",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = fileName,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = "NEW",
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier
                                            .background(
                                                color = MaterialTheme.colorScheme.primaryContainer,
                                                shape = RoundedCornerShape(4.dp)
                                            )
                                            .padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                    } else {
                        // All are clean and up to date!
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = "Up-to-date Icon",
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = Loc.getText("no_newly_indexed_files"),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.clearImportSummary() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(Loc.getText("close_dialog"))
                }
            }
        )
    }

    // ADD TO PLAYLIST DIALOG
    if (showAddToPlaylistDialogForTrack != null) {
        AddToPlaylistDialog(
            track = showAddToPlaylistDialogForTrack!!,
            playlists = playlists,
            onDismiss = { showAddToPlaylistDialogForTrack = null },
            onPlaylistSelected = { playlist ->
                viewModel.addTrackToPlaylist(playlist.id, showAddToPlaylistDialogForTrack!!.id)
                Toast.makeText(context, String.format(Loc.getText("added_to_playlist_success"), playlist.name), Toast.LENGTH_SHORT).show()
            },
            onCreatePlaylistClicked = {
                showCreatePlaylistDialog = true
            }
        )
    }

    // CREATE PLAYLIST DIALOG
    if (showCreatePlaylistDialog) {
        AlertDialog(
            onDismissRequest = { showCreatePlaylistDialog = false },
            title = { Text(Loc.getText("add_playlist")) },
            text = {
                OutlinedTextField(
                    value = playlistInputName,
                    onValueChange = { playlistInputName = it },
                    label = { Text(Loc.getText("playlist_name_label")) },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (playlistInputName.isNotEmpty()) {
                        viewModel.createPlaylist(playlistInputName)
                        playlistInputName = ""
                        showCreatePlaylistDialog = false
                    }
                }) {
                    Text(Loc.getText("create"))
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreatePlaylistDialog = false }) { Text(Loc.getText("cancel")) }
            }
        )
    }
}

@Composable
fun EmptyLibraryState(messageKey: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_logo),
                contentDescription = "Empty Library Logo",
                modifier = Modifier
                    .size(80.dp)
                    .padding(bottom = 8.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = Loc.getText(messageKey),
                textAlign = TextAlign.Center,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// --- SUB-SCREEN 3: FOLDER DETAILS VIEW ---
@Composable
fun FolderDetailsView(
    folderId: Long,
    viewModel: AppViewModel,
    backPressed: () -> Unit,
    onCreateTaskForFolder: (String, Long) -> Unit,
    onShowAssociatedTasks: (String, Long, String) -> Unit
) {
    var currentFolderId by remember(folderId) { mutableStateOf(folderId) }
    var folderHistory by remember(folderId) { mutableStateOf(listOf(folderId)) }

    val handleBack = {
        if (folderHistory.size > 1) {
            val newHistory = folderHistory.dropLast(1)
            folderHistory = newHistory
            currentFolderId = newHistory.last()
        } else {
            backPressed()
        }
    }

    BackHandler(enabled = true) {
        handleBack()
    }

    var folderBreadcrumbs by remember { mutableStateOf<List<Folder>>(emptyList()) }
    var folderName by remember { mutableStateOf("") }

    LaunchedEffect(currentFolderId) {
        val list = mutableListOf<Folder>()
        var curr: Folder? = viewModel.repository.getFolderById(currentFolderId)
        while (curr != null) {
            list.add(0, curr)
            curr = curr.parentFolderId?.let { viewModel.repository.getFolderById(it) }
        }
        folderBreadcrumbs = list
        folderName = list.lastOrNull()?.folderName ?: ""
    }

    val subfoldersFlow = remember(currentFolderId) { viewModel.getSubfolders(currentFolderId) }
    val subfolders by subfoldersFlow.collectAsStateWithLifecycle(emptyList())

    val folderFlow = remember(currentFolderId) { viewModel.repository.getTracksForFolderFlow(currentFolderId) }
    val rawTracks by folderFlow.collectAsStateWithLifecycle(emptyList())

    var folderSortBy by remember { mutableStateOf("name") }
    var folderIsAscending by remember { mutableStateOf(true) }

    val tracks = remember(rawTracks, folderSortBy, folderIsAscending) {
        getSortedTracks(rawTracks, folderSortBy, folderIsAscending)
    }
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var showAddToPlaylistDialogForTrack by remember { mutableStateOf<AudioTrack?>(null) }

    var showMenu by remember { mutableStateOf(false) }
    var showRenameFolderDialog by remember { mutableStateOf(false) }
    var showDeleteFolderConfirmDialog by remember { mutableStateOf(false) }
    var folderToRename by remember { mutableStateOf<Folder?>(null) }

    var isBulkSelectMode by remember { mutableStateOf(false) }
    var selectedDetailTrackIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var showPlaylistSelectDialogForBulk by remember { mutableStateOf(false) }

    folderToRename?.let { targetFolder ->
        var tempName by remember(targetFolder) { mutableStateOf(targetFolder.folderName) }
        AlertDialog(
            onDismissRequest = { folderToRename = null },
            title = { Text(Loc.getText("edit_folder_name")) },
            text = {
                OutlinedTextField(
                    value = tempName,
                    onValueChange = { tempName = it },
                    label = { Text(Loc.getText("folder_name_label")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (tempName.isNotBlank()) {
                            viewModel.editFolderName(targetFolder.id, tempName)
                            if (targetFolder.id == currentFolderId) {
                                folderName = tempName
                            }
                        }
                        folderToRename = null
                    }
                ) {
                    Text(Loc.getText("save_settings"))
                }
            },
            dismissButton = {
                TextButton(onClick = { folderToRename = null }) {
                    Text(Loc.getText("cancel"))
                }
            }
        )
    }

    if (showRenameFolderDialog) {
        var tempName by remember { mutableStateOf(folderName) }
        AlertDialog(
            onDismissRequest = { showRenameFolderDialog = false },
            title = { Text(Loc.getText("edit_folder_name")) },
            text = {
                OutlinedTextField(
                    value = tempName,
                    onValueChange = { tempName = it },
                    label = { Text(Loc.getText("folder_name_label")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (tempName.isNotBlank()) {
                            viewModel.editFolderName(currentFolderId, tempName)
                            folderName = tempName
                        }
                        showRenameFolderDialog = false
                    }
                ) {
                    Text(Loc.getText("save_settings"))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameFolderDialog = false }) {
                    Text(Loc.getText("cancel"))
                }
            }
        )
    }

    if (showPlaylistSelectDialogForBulk) {
        AlertDialog(
            onDismissRequest = { showPlaylistSelectDialogForBulk = false },
            title = { Text(Loc.getText("choose_playlist_dialog_title")) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (playlists.isEmpty()) {
                        Text(
                            text = Loc.getText("empty_playlists_desc"),
                            fontSize = 14.sp,
                            color = Color.Gray
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.heightIn(max = 300.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(playlists) { playlist ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            viewModel.addTracksToPlaylist(playlist.id, selectedDetailTrackIds.toList())
                                            Toast.makeText(
                                                context,
                                                String.format(Loc.getText("tracks_added_to_playlist_success"), playlist.name),
                                                Toast.LENGTH_SHORT
                                            ).show()
                                            selectedDetailTrackIds = emptySet()
                                            isBulkSelectMode = false
                                            showPlaylistSelectDialogForBulk = false
                                        },
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Audiotrack,
                                            contentDescription = "Playlist",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = playlist.name,
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showPlaylistSelectDialogForBulk = false }) {
                    Text(Loc.getText("cancel"))
                }
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // App header bar
        if (isBulkSelectMode) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${Loc.getText("selected")}: ${selectedDetailTrackIds.size}",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val areAllTracksSelected = tracks.isNotEmpty() && selectedDetailTrackIds.size == tracks.size
                        IconButton(onClick = {
                            if (areAllTracksSelected) {
                                selectedDetailTrackIds = emptySet()
                                isBulkSelectMode = false
                            } else {
                                selectedDetailTrackIds = tracks.map { it.id }.toSet()
                            }
                        }) {
                            Icon(
                                imageVector = Icons.Filled.SelectAll,
                                contentDescription = Loc.getText("select_all"),
                                tint = if (areAllTracksSelected) MaterialTheme.colorScheme.primary else Color.Gray
                            )
                        }
                        IconButton(onClick = {
                            coroutineScope.launch {
                                for (id in selectedDetailTrackIds) {
                                    val t = viewModel.repository.getTrackById(id)
                                    if (t != null) {
                                        viewModel.repository.deleteTrack(t)
                                    }
                                }
                                selectedDetailTrackIds = emptySet()
                                isBulkSelectMode = false
                            }
                        }) {
                            Icon(
                                imageVector = Icons.Filled.Delete,
                                contentDescription = "Delete selection",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                        IconButton(onClick = {
                            if (selectedDetailTrackIds.isNotEmpty()) {
                                val selectedTracksList = tracks.filter { selectedDetailTrackIds.contains(it.id) }
                                viewModel.quickAddTaskForTracks(selectedTracksList, Loc.getText("group_goal_task_title"), 3)
                                selectedDetailTrackIds = emptySet()
                                isBulkSelectMode = false
                            }
                        }) {
                            Icon(
                                imageVector = Icons.Filled.Bookmark,
                                contentDescription = "Add goal to selected",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(onClick = {
                            if (selectedDetailTrackIds.isNotEmpty()) {
                                showPlaylistSelectDialogForBulk = true
                            }
                        }) {
                            Icon(
                                imageVector = Icons.Filled.AddToPhotos,
                                contentDescription = "Add to Playlist",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(onClick = {
                            isBulkSelectMode = false
                            selectedDetailTrackIds = emptySet()
                        }) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Exit bulk mode"
                            )
                        }
                    }
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = { handleBack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                }
                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = "More Options",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text(Loc.getText("play_next")) },
                            onClick = {
                                showMenu = false
                                viewModel.addTracksToPlayNext(tracks)
                                Toast.makeText(context, Loc.getText("added_to_queue"), Toast.LENGTH_SHORT).show()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(Loc.getText("edit_folder_name")) },
                            onClick = {
                                showMenu = false
                                showRenameFolderDialog = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(Loc.getText("create_task")) },
                            onClick = {
                                showMenu = false
                                onCreateTaskForFolder("FOLDER", currentFolderId)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(Loc.getText("show_associated_tasks")) },
                            onClick = {
                                showMenu = false
                                onShowAssociatedTasks("FOLDER", currentFolderId, folderName)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(Loc.getText("delete_history"), color = MaterialTheme.colorScheme.error) },
                            onClick = {
                                showMenu = false
                                showDeleteFolderConfirmDialog = true
                            }
                        )
                    }
                }
            }
        }

        if (showDeleteFolderConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteFolderConfirmDialog = false },
                title = { Text(Loc.getText("delete_folder_title")) },
                text = { Text(Loc.getText("delete_folder_confirm")) },
                confirmButton = {
                    Button(
                        onClick = {
                            showDeleteFolderConfirmDialog = false
                            coroutineScope.launch {
                                viewModel.repository.deleteFolder(currentFolderId)
                            }
                            handleBack()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text(Loc.getText("delete"))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteFolderConfirmDialog = false }) {
                        Text(Loc.getText("cancel"))
                    }
                }
            )
        }

        if (!isBulkSelectMode) {
            // Windows-Explorer style Breadcrumb Navigation Bar
            if (folderBreadcrumbs.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    itemsIndexed(folderBreadcrumbs) { index, bFolder ->
                        val isLast = index == folderBreadcrumbs.size - 1
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = bFolder.folderName,
                                fontSize = 14.sp,
                                fontWeight = if (isLast) FontWeight.Bold else FontWeight.Medium,
                                color = if (isLast) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.clickable(!isLast) {
                                    val targetIndex = folderHistory.indexOf(bFolder.id)
                                    if (targetIndex >= 0) {
                                        folderHistory = folderHistory.take(targetIndex + 1)
                                        currentFolderId = bFolder.id
                                    } else {
                                        folderHistory = folderHistory + bFolder.id
                                        currentFolderId = bFolder.id
                                    }
                                }
                            )
                            if (!isLast) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.size(18.dp).padding(horizontal = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        TrackListSortHeader(
            totalCount = tracks.size,
            sortBy = folderSortBy,
            onSortByChange = { folderSortBy = it },
            isAscending = folderIsAscending,
            onIsAscendingChange = { folderIsAscending = it },
            showCount = subfolders.isEmpty()
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 1. SUBFOLDERS SECTION
            if (subfolders.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Folder,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${Loc.getText("folders")} (${subfolders.size})",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            ),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                items(subfolders, key = { "folder_${it.id}" }) { sub ->
                    SubfolderDetailsItem(
                        sub = sub,
                        depth = 0,
                        viewModel = viewModel,
                        onNavigate = { targetId ->
                            folderHistory = folderHistory + targetId
                            currentFolderId = targetId
                        },
                        onRenameFolder = { targetFolder ->
                            folderToRename = targetFolder
                        },
                        onCreateTaskForSource = { source, id ->
                            onCreateTaskForFolder(source, id)
                        },
                        onShowAssociatedTasks = { source, id, name ->
                            onShowAssociatedTasks(source, id, name)
                        }
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }

            // 2. FILES SECTION HEADER (Only when subfolders exist to cleanly separate sections)
            if (tracks.isNotEmpty() && subfolders.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Audiotrack,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = Loc.getText("files"),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            ),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            } else if (tracks.isEmpty() && subfolders.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = Loc.getText("empty_folders_desc"),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            // 3. TRACKS LIST
            items(tracks) { track ->
                val isCurrentExecuting = AudioPlayerManager.currentTrack.collectAsStateWithLifecycle().value?.id == track.id
                val isPlaying = AudioPlayerManager.isPlaying.collectAsStateWithLifecycle().value

                UnifiedAudioTrackRow(
                    track = track,
                    isCurrentExecuting = isCurrentExecuting,
                    isPlaying = isPlaying,
                    viewModel = viewModel,
                    onTrackPlaylistMenuClicked = { showAddToPlaylistDialogForTrack = it },
                    onCreateTask = { type, id -> onCreateTaskForFolder(type, id) },
                    onShowAssociatedTasks = { type, id, name -> onShowAssociatedTasks(type, id, name) },
                    playlistTracks = tracks,
                    isSelected = selectedDetailTrackIds.contains(track.id),
                    isBulkSelectMode = isBulkSelectMode,
                    onClick = {
                        if (isBulkSelectMode) {
                            if (selectedDetailTrackIds.contains(track.id)) {
                                selectedDetailTrackIds = selectedDetailTrackIds - track.id
                                if (selectedDetailTrackIds.isEmpty()) {
                                    isBulkSelectMode = false
                                }
                            } else {
                                selectedDetailTrackIds = selectedDetailTrackIds + track.id
                            }
                        } else {
                            viewModel.selectAndPlay(track, tracks)
                        }
                    },
                    onLongClick = {
                        if (!isBulkSelectMode) {
                            isBulkSelectMode = true
                            selectedDetailTrackIds = setOf(track.id)
                        } else {
                            if (selectedDetailTrackIds.contains(track.id)) {
                                selectedDetailTrackIds = selectedDetailTrackIds - track.id
                                if (selectedDetailTrackIds.isEmpty()) {
                                    isBulkSelectMode = false
                                }
                            } else {
                                selectedDetailTrackIds = selectedDetailTrackIds + track.id
                            }
                        }
                    }
                )
            }
        }

        if (showAddToPlaylistDialogForTrack != null) {
            AddToPlaylistDialog(
                track = showAddToPlaylistDialogForTrack!!,
                playlists = playlists,
                onDismiss = { showAddToPlaylistDialogForTrack = null },
                onPlaylistSelected = { playlist ->
                    viewModel.addTrackToPlaylist(playlist.id, showAddToPlaylistDialogForTrack!!.id)
                    Toast.makeText(context, String.format(Loc.getText("added_to_playlist_success"), playlist.name), Toast.LENGTH_SHORT).show()
                },
                onCreatePlaylistClicked = {
                    // Done on main screen or prompt Toast
                }
            )
        }
    }
}

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

    // Display total listened segments percentage
    val completedSegmentsCount = track.getListenedCount()
    val maxListenedPercent = track.getProgressPercent()

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
    val allTracksList by viewModel.tracks.collectAsStateWithLifecycle()
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
                                text = if (isPracticeMode) Loc.getText("practice_mode") else Loc.getText("video_focus_mode_title"),
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
                                    text = "${track.playCount}",
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

                    val numSegments = track.getAdaptiveNumSegments()
                    val listenedRanges = remember(track.listenedSegments, numSegments) {
                        val bitSet = track.getListenedBitSet(numSegments)
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
                            val segmentsColor = MaterialTheme.colorScheme.primary
                            val noteMarkerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                if (numSegments > 0) {
                                    val segmentWidth = size.width / numSegments
                                    for (range in listenedRanges) {
                                        val startX = range.first * segmentWidth
                                        val rangeWidth = (range.last - range.first + 1) * segmentWidth
                                        drawRect(
                                            color = segmentsColor.copy(alpha = 0.22f),
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
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(currentNeedlePercent.coerceIn(0f, 1f))
                                    .background(MaterialTheme.colorScheme.primary)
                            )
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


// --- SUB-SCREEN 5: TASKS LIST VIEW (Active / Completed) ---
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TasksView(
    viewModel: AppViewModel,
    onAddTaskClicked: () -> Unit,
    onTaskSelected: (Task) -> Unit,
    onEdit: (Task) -> Unit
) {
    var activeTab by remember { mutableStateOf(0) } // 0=Active, 1=Completed
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { 2 })
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(pagerState.currentPage) {
        activeTab = pagerState.currentPage
    }

    val activeTasks by viewModel.activeTasks.collectAsStateWithLifecycle(emptyList())
    val completedTasks by viewModel.completedTasks.collectAsStateWithLifecycle(emptyList())
    val allTracks by viewModel.tracks.collectAsStateWithLifecycle(emptyList())
    val allTaskProgress by viewModel.allTaskProgress.collectAsStateWithLifecycle(emptyList())
    val trackMetadataCache by viewModel.trackMetadataCache.collectAsStateWithLifecycle(emptyMap())
    val taskLabelsList by viewModel.taskLabels.collectAsStateWithLifecycle(emptyList())
    val context = LocalContext.current

    var isTaskBulkSelectMode by remember { mutableStateOf(false) }
    var selectedTaskIds by remember { mutableStateOf(emptySet<Long>()) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedLabelFilter by remember { mutableStateOf<String?>(null) }
    var sortBy by remember { mutableStateOf("date") } // "date", "progress", "alphabetical"
    var showSortMenu by remember { mutableStateOf(false) }
    var showTagFilterMenu by remember { mutableStateOf(false) }
    var isSearchExpanded by remember { mutableStateOf(false) }

    val allUniqueTaskLabels = remember(activeTasks, completedTasks, taskLabelsList) {
        val fromTasks = (activeTasks + completedTasks).flatMap { it.getLabelsList() }
        val fromDb = taskLabelsList.map { it.name.trim() }
        (fromTasks + fromDb).filter { it.isNotEmpty() }.distinctBy { it.lowercase(Locale.getDefault()) }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (isTaskBulkSelectMode) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${Loc.getText("selected")}: ${selectedTaskIds.size}",
                        fontWeight = FontWeight.Bold
                    )
                    Row {
                        if (activeTab == 0) {
                            IconButton(onClick = {
                                selectedTaskIds.forEach { taskId ->
                                    viewModel.archiveTask(taskId)
                                }
                                isTaskBulkSelectMode = false
                                selectedTaskIds = emptySet()
                            }) {
                                Icon(Icons.Filled.Archive, "Archive tasks", tint = MaterialTheme.colorScheme.primary)
                            }
                        } else {
                            IconButton(onClick = {
                                selectedTaskIds.forEach { taskId ->
                                    viewModel.reactivateTask(taskId)
                                }
                                isTaskBulkSelectMode = false
                                selectedTaskIds = emptySet()
                            }) {
                                Icon(Icons.Filled.Refresh, "Reactivate tasks", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                        IconButton(onClick = {
                            selectedTaskIds.forEach { taskId ->
                                viewModel.deleteTask(taskId)
                            }
                            isTaskBulkSelectMode = false
                            selectedTaskIds = emptySet()
                        }) {
                            Icon(Icons.Filled.Delete, "Delete tasks", tint = MaterialTheme.colorScheme.error)
                        }
                        IconButton(onClick = {
                            isTaskBulkSelectMode = false
                            selectedTaskIds = emptySet()
                        }) {
                            Icon(Icons.Filled.Close, "Exit bulk mode")
                        }
                    }
                }
            }
        }

        AnimatedContent(
            targetState = isSearchExpanded,
            label = "search_expansion_row",
            modifier = Modifier.fillMaxWidth()
        ) { expanded ->
            if (expanded) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    IconButton(onClick = {
                        isSearchExpanded = false
                        searchQuery = ""
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Collapse search",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 56.dp)
                            .testTag("search_tasks_input"),
                        placeholder = {
                            Text(
                                text = Loc.getText("search_tasks_placeholder"),
                                fontSize = 14.sp
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(
                                        imageVector = Icons.Filled.Close,
                                        contentDescription = "Clear search",
                                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(28.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f),
                            focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                            unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                        ),
                        textStyle = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        TabRow(
                            selectedTabIndex = activeTab,
                            containerColor = Color.Transparent,
                            contentColor = MaterialTheme.colorScheme.primary,
                            indicator = { tabPositions ->
                                TabRowDefaults.SecondaryIndicator(
                                    modifier = Modifier.tabIndicatorOffset(tabPositions[activeTab]),
                                    color = MaterialTheme.colorScheme.primary,
                                    height = 3.dp
                                )
                            },
                            divider = {}
                        ) {
                            Tab(selected = activeTab == 0, onClick = { coroutineScope.launch { pagerState.animateScrollToPage(0) } }) {
                                Text(
                                    text = Loc.getText("active"),
                                    modifier = Modifier.padding(14.dp),
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = if (activeTab == 0) FontWeight.Bold else FontWeight.Medium,
                                        letterSpacing = 0.5.sp,
                                        fontFamily = FontFamily.SansSerif
                                    ),
                                    color = if (activeTab == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
                            }
                            Tab(selected = activeTab == 1, onClick = { coroutineScope.launch { pagerState.animateScrollToPage(1) } }) {
                                Text(
                                    text = Loc.getText("completed"),
                                    modifier = Modifier.padding(14.dp),
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = if (activeTab == 1) FontWeight.Bold else FontWeight.Medium,
                                        letterSpacing = 0.5.sp,
                                        fontFamily = FontFamily.SansSerif
                                    ),
                                    color = if (activeTab == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    // Sorting dropdown menu & sort icon
                    Box {
                        IconButton(
                            onClick = { showSortMenu = true },
                            modifier = Modifier.testTag("sort_tasks_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Sort,
                                contentDescription = "Sort tasks",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = Loc.getText("sort_date_created"),
                                        fontWeight = if (sortBy == "date") FontWeight.Bold else FontWeight.Normal,
                                        color = if (sortBy == "date") MaterialTheme.colorScheme.primary else Color.Unspecified
                                    )
                                },
                                onClick = {
                                    sortBy = "date"
                                    showSortMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = Loc.getText("sort_progress"),
                                        fontWeight = if (sortBy == "progress") FontWeight.Bold else FontWeight.Normal,
                                        color = if (sortBy == "progress") MaterialTheme.colorScheme.primary else Color.Unspecified
                                    )
                                },
                                onClick = {
                                    sortBy = "progress"
                                    showSortMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = Loc.getText("sort_alphabetical"),
                                        fontWeight = if (sortBy == "alphabetical") FontWeight.Bold else FontWeight.Normal,
                                        color = if (sortBy == "alphabetical") MaterialTheme.colorScheme.primary else Color.Unspecified
                                    )
                                },
                                onClick = {
                                    sortBy = "alphabetical"
                                    showSortMenu = false
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    // Tag filter dropdown menu & icon
                    Box {
                        IconButton(
                            onClick = { showTagFilterMenu = true },
                            modifier = Modifier.testTag("filter_tag_button")
                        ) {
                            BadgedBox(
                                badge = {
                                    if (selectedLabelFilter != null) {
                                        Badge(
                                            containerColor = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(6.dp)
                                        )
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Label,
                                    contentDescription = Loc.getText("filter_by_tag"),
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = showTagFilterMenu,
                            onDismissRequest = { showTagFilterMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = Loc.getText("all_labels_filter"),
                                        fontWeight = if (selectedLabelFilter == null) FontWeight.Bold else FontWeight.Normal,
                                        color = if (selectedLabelFilter == null) MaterialTheme.colorScheme.primary else Color.Unspecified
                                    )
                                },
                                trailingIcon = if (selectedLabelFilter == null) {
                                    {
                                        Icon(
                                            imageVector = Icons.Filled.Check,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                } else null,
                                onClick = {
                                    selectedLabelFilter = null
                                    showTagFilterMenu = false
                                }
                            )

                            if (allUniqueTaskLabels.isEmpty()) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = if (Loc.currentLanguage == "ar") "لا توجد وسوم" else "No tags available",
                                            color = Color.Gray,
                                            fontSize = 13.sp
                                        )
                                    },
                                    enabled = false,
                                    onClick = {}
                                )
                            } else {
                                allUniqueTaskLabels.forEach { label ->
                                    val isSelected = selectedLabelFilter.equals(label, ignoreCase = true)
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = label,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Unspecified
                                            )
                                        },
                                        trailingIcon = if (isSelected) {
                                            {
                                                Icon(
                                                    imageVector = Icons.Filled.Check,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        } else null,
                                        onClick = {
                                            selectedLabelFilter = if (isSelected) null else label
                                            showTagFilterMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    IconButton(
                        onClick = { isSearchExpanded = true },
                        modifier = Modifier.testTag("search_icon")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))

        // Active tag filter indicator
        if (selectedLabelFilter != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                InputChip(
                    selected = true,
                    onClick = { selectedLabelFilter = null },
                    label = { Text(selectedLabelFilter!!, fontSize = 12.sp) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Label,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                    },
                    trailingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Clear tag filter",
                            modifier = Modifier.size(14.dp)
                        )
                    }
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
        }

        Box(modifier = Modifier.weight(1f)) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                val pageTasks = if (page == 0) activeTasks else completedTasks
                
                val filteredAndSortedTasks = remember(pageTasks, allTaskProgress, searchQuery, selectedLabelFilter, sortBy, allTracks, trackMetadataCache) {
                    val trackMap = allTracks.associateBy { it.id }
                    
                    val taskWithDataList = pageTasks.map { task ->
                        val taskProgresses = allTaskProgress.filter { it.taskId == task.id }
                        
                        var totalCompleted = 0
                        var totalRequired = 0
                        val targetVal = task.targetValue
                        if (task.targetType == "PLAY_COUNT") {
                            taskProgresses.forEach { p ->
                                totalCompleted += minOf(p.completedPlayCount, targetVal)
                                totalRequired += targetVal
                            }
                        } else {
                            taskProgresses.forEach { p ->
                                totalCompleted += minOf(p.getDaysList().size, targetVal)
                                totalRequired += targetVal
                            }
                        }
                        val isAllTracksDone = taskProgresses.isNotEmpty() && taskProgresses.all { it.isTrackCompleted }
                        val percent = if (isAllTracksDone) 1.0f else if (totalRequired > 0) totalCompleted.toFloat() / totalRequired.toFloat() else 0f
                        val associatedTracks = taskProgresses.mapNotNull { trackMap[it.trackId] }
                        
                        Triple(task, percent, associatedTracks)
                    }

                    // Real-time filtering by task labels, title, associated track name, track title, or artist metadata
                    val filtered = taskWithDataList.filter { (task, _, tracks) ->
                        val matchesLabelFilter = if (selectedLabelFilter == null) {
                            true
                        } else {
                            task.getLabelsList().any { it.equals(selectedLabelFilter, ignoreCase = true) }
                        }
                        if (!matchesLabelFilter) return@filter false

                        if (searchQuery.trim().isEmpty()) {
                            true
                        } else {
                            val query = searchQuery.trim().lowercase(Locale.getDefault())
                            val matchesTaskTitle = task.getDisplayTitle().lowercase(Locale.getDefault()).contains(query)
                            val matchesLabels = task.labels.lowercase(Locale.getDefault()).contains(query)
                            val matchesTracks = tracks.any { track ->
                                val meta = trackMetadataCache[track.id]
                                val matchesFileName = track.fileName.lowercase(Locale.getDefault()).contains(query)
                                val matchesMetaTitle = meta?.title?.lowercase(Locale.getDefault())?.contains(query) ?: false
                                val matchesMetaArtist = meta?.artist?.lowercase(Locale.getDefault())?.contains(query) ?: false
                                matchesFileName || matchesMetaTitle || matchesMetaArtist
                            }
                            matchesTaskTitle || matchesLabels || matchesTracks
                        }
                    }

                    // Order by date created, progress percentage, or alphabetical
                    when (sortBy) {
                        "date" -> filtered.sortedByDescending { it.first.startDate }
                        "progress" -> filtered.sortedByDescending { it.second }
                        "alphabetical" -> filtered.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.first.getDisplayTitle() })
                        else -> filtered
                    }
                }

                if (filteredAndSortedTasks.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        val placeholderText = if (pageTasks.isEmpty()) {
                            Loc.getText("no_items")
                        } else {
                            if (Loc.currentLanguage == "ar") "لم يُعثر على مهام تطابق البحث" else "No matching tasks found"
                        }
                        Text(placeholderText, color = Color.Gray, fontSize = 14.sp)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(filteredAndSortedTasks) { (task, overallPercent, taskTracks) ->
                            val isSelected = selectedTaskIds.contains(task.id)

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .combinedClickable(
                                        onClick = {
                                            if (isTaskBulkSelectMode) {
                                                selectedTaskIds = if (isSelected) selectedTaskIds - task.id else selectedTaskIds + task.id
                                            } else {
                                                onTaskSelected(task)
                                            }
                                        },
                                        onLongClick = {
                                            isTaskBulkSelectMode = true
                                            selectedTaskIds = selectedTaskIds + task.id
                                        }
                                    )
                                    .testTag("task_card_${task.id}"),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected) {
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.18f)
                                    } else {
                                        MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
                                    }
                                ),
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
                                ),
                                shape = RoundedCornerShape(20.dp),
                                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                            ) {
                                Column(modifier = Modifier.padding(start = 14.dp, top = 12.dp, end = 6.dp, bottom = 12.dp)) {
                                    val isActuallyCompleted = overallPercent >= 0.999f
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {

                                        if (isTaskBulkSelectMode) {
                                            Checkbox(
                                                checked = isSelected,
                                                onCheckedChange = {
                                                    selectedTaskIds = if (isSelected) selectedTaskIds - task.id else selectedTaskIds + task.id
                                                }
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                        } else {
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.Center,
                                                modifier = Modifier.widthIn(min = 34.dp)
                                            ) {
                                                if (task.isCompleted) {
                                                    if (isActuallyCompleted) {
                                                        Icon(
                                                            imageVector = Icons.Filled.CheckCircle,
                                                            contentDescription = "Completed task icon",
                                                            tint = ColorSuccess,
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                    } else {
                                                        Text(
                                                            text = "${(overallPercent * 100).toInt()}%",
                                                            style = MaterialTheme.typography.labelSmall.copy(
                                                                fontSize = 11.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                fontFamily = FontFamily.SansSerif
                                                            ),
                                                            color = MaterialTheme.colorScheme.primary
                                                        )
                                                    }
                                                } else {
                                                    Icon(
                                                        imageVector = Icons.Filled.Bookmark,
                                                        contentDescription = "Task icon",
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                    Spacer(modifier = Modifier.height(2.dp))
                                                    Text(
                                                        text = "${(overallPercent * 100).toInt()}%",
                                                        style = MaterialTheme.typography.labelSmall.copy(
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            fontFamily = FontFamily.SansSerif
                                                        ),
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.width(10.dp))
                                        }
                                        Column(
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(end = 4.dp)
                                        ) {
                                            Text(
                                                text = task.getDisplayTitle(),
                                                style = MaterialTheme.typography.bodyMedium.copy(
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    fontFamily = FontFamily.SansSerif
                                                ),
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (!task.isCompleted || !isActuallyCompleted) {
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = formatScheduledDays(task.scheduledDays),
                                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                        if (!isTaskBulkSelectMode) {
                                            var showTaskMenu by remember { mutableStateOf(false) }
                                            Box {
                                                IconButton(
                                                    onClick = { showTaskMenu = true },
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Filled.MoreVert,
                                                        contentDescription = "options",
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                                DropdownMenu(
                                                    expanded = showTaskMenu,
                                                    onDismissRequest = { showTaskMenu = false }
                                                ) {
                                                    DropdownMenuItem(
                                                        text = { Text(Loc.getText("play_next")) },
                                                        onClick = {
                                                            showTaskMenu = false
                                                            viewModel.addTracksToPlayNext(taskTracks)
                                                            Toast.makeText(context, Loc.getText("added_to_queue"), Toast.LENGTH_SHORT).show()
                                                        }
                                                    )
                                                    DropdownMenuItem(
                                                        text = { Text(Loc.getText("edit_task")) },
                                                        onClick = {
                                                            showTaskMenu = false
                                                            onEdit(task)
                                                        }
                                                    )
                                                    DropdownMenuItem(
                                                        text = { Text(Loc.getText("duplicate_task")) },
                                                        onClick = {
                                                            showTaskMenu = false
                                                            viewModel.duplicateTask(task.id) {
                                                                Toast.makeText(context, Loc.getText("task_duplicated_msg"), Toast.LENGTH_SHORT).show()
                                                            }
                                                        }
                                                    )
                                                    if (!task.isCompleted) {
                                                        DropdownMenuItem(
                                                            text = { Text(Loc.getText("archive_action")) },
                                                            onClick = {
                                                                showTaskMenu = false
                                                                viewModel.archiveTask(task.id)
                                                                Toast.makeText(context, Loc.getText("task_archived_msg"), Toast.LENGTH_SHORT).show()
                                                            }
                                                        )
                                                    } else if (!isActuallyCompleted) {
                                                        DropdownMenuItem(
                                                            text = { Text(Loc.getText("reactivate")) },
                                                            onClick = {
                                                                showTaskMenu = false
                                                                viewModel.reactivateTask(task.id)
                                                            }
                                                        )
                                                    }
                                                    DropdownMenuItem(
                                                        text = { Text(Loc.getText("delete_history"), color = MaterialTheme.colorScheme.error) },
                                                        onClick = {
                                                            showTaskMenu = false
                                                            viewModel.deleteTask(task.id)
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    if (!task.isCompleted || !isActuallyCompleted) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        LinearProgressIndicator(
                                            progress = { overallPercent },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(4.dp)
                                                .clip(CircleShape),
                                            color = MaterialTheme.colorScheme.primary,
                                            trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Bottom-right floating button (FAB) center-aware, elegant, and matches library styles
            FloatingActionButton(
                onClick = onAddTaskClicked,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(24.dp)
                    .testTag("btn_add_task"),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = CircleShape
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = Loc.getText("add_task"),
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

// --- SUB-SCREEN 6: TASK DETAILS VIEW (Track items within task) ---
@Composable
fun TaskDetailsView(
    task: Task,
    viewModel: AppViewModel,
    onBack: () -> Unit,
    onEdit: (Task) -> Unit,
    onShowAssociatedTasks: (String, Long, String) -> Unit
) {
    val context = LocalContext.current
    val allTasksState by viewModel.allTasks.collectAsStateWithLifecycle()
    val currentTask = remember(task.id, allTasksState) {
        allTasksState.find { it.id == task.id } ?: task
    }

    val progressFlow = remember(currentTask.id) { viewModel.repository.getProgressForTaskFlow(currentTask.id) }
    val progressList by progressFlow.collectAsStateWithLifecycle(emptyList())

    val allTracks by viewModel.tracks.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    var showAddToPlaylistDialogForTrack by remember { mutableStateOf<AudioTrack?>(null) }
    var showTaskStatsDialog by remember { mutableStateOf(false) }
    val taskTracks = remember(progressList, allTracks) {
        val trackMap = allTracks.associateBy { it.id }
        progressList.mapNotNull { trackMap[it.trackId] }
    }

    var taskSortBy by remember { mutableStateOf("name") }
    var taskIsAscending by remember { mutableStateOf(true) }

    val progressWithTracks = remember(progressList, allTracks) {
        val trackMap = allTracks.associateBy { it.id }
        progressList.mapNotNull { p ->
            val track = trackMap[p.trackId]
            if (track != null) p to track else null
        }
    }

    val sortedProgressWithTracks = remember(progressWithTracks, taskSortBy, taskIsAscending) {
        val sorted = when (taskSortBy) {
            "name" -> progressWithTracks.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.second.fileName })
            "duration" -> progressWithTracks.sortedBy { it.second.duration }
            "date_added" -> progressWithTracks.sortedBy { it.second.id }
            "play_count" -> progressWithTracks.sortedBy { it.second.playCount }
            "progress" -> progressWithTracks.sortedBy { it.second.getProgressPercent() }
            else -> progressWithTracks
        }
        if (taskIsAscending) sorted else sorted.reversed()
    }

    val totalTracksCount = progressList.size
    val finishedTracksCount = progressList.count { it.isTrackCompleted }

    val overallPercent = if (progressList.isNotEmpty()) {
        var totalCompleted = 0
        var totalRequired = 0
        val targetVal = currentTask.targetValue
        if (currentTask.targetType == "PLAY_COUNT") {
            progressList.forEach { p ->
                totalCompleted += minOf(p.completedPlayCount, targetVal)
                totalRequired += targetVal
            }
        } else {
            progressList.forEach { p ->
                totalCompleted += minOf(p.getDaysList().size, targetVal)
                totalRequired += targetVal
            }
        }
        val isAllTracksDone = progressList.isNotEmpty() && progressList.all { it.isTrackCompleted }
        if (isAllTracksDone) 1.0f else if (totalRequired > 0) totalCompleted.toFloat() / totalRequired.toFloat() else 0f
    } else {
        0f
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = currentTask.getDisplayTitle(),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )

            // Settings 3-dots
            var expandedMenu by remember { mutableStateOf(false) }
            Box {
                IconButton(
                    onClick = { expandedMenu = true },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = "options",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        modifier = Modifier.size(20.dp)
                    )
                }
                DropdownMenu(expanded = expandedMenu, onDismissRequest = { expandedMenu = false }) {
                    DropdownMenuItem(
                        text = { Text(Loc.getText("play_next")) },
                        onClick = {
                            expandedMenu = false
                            viewModel.addTracksToPlayNext(taskTracks)
                            Toast.makeText(context, Loc.getText("added_to_queue"), Toast.LENGTH_SHORT).show()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(Loc.getText("edit_task")) },
                        onClick = {
                            expandedMenu = false
                            onEdit(currentTask)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(Loc.getText("duplicate_task")) },
                        onClick = {
                            expandedMenu = false
                            viewModel.duplicateTask(currentTask.id) {
                                Toast.makeText(context, Loc.getText("task_duplicated_msg"), Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                    if (!currentTask.isCompleted) {
                        DropdownMenuItem(
                            text = { Text(Loc.getText("archive_action")) },
                            onClick = {
                                expandedMenu = false
                                viewModel.archiveTask(currentTask.id)
                                Toast.makeText(context, Loc.getText("task_archived_msg"), Toast.LENGTH_SHORT).show()
                                onBack()
                            }
                        )
                    } else if (overallPercent < 1.0f) {
                        DropdownMenuItem(
                            text = { Text(Loc.getText("reactivate")) },
                            onClick = {
                                expandedMenu = false
                                viewModel.reactivateTask(currentTask.id)
                                Toast.makeText(context, Loc.getText("re_activated_msg"), Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(Loc.getText("task_stats_option")) },
                        onClick = {
                            expandedMenu = false
                            showTaskStatsDialog = true
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(Loc.getText("delete_history"), color = MaterialTheme.colorScheme.error) },
                        onClick = {
                            expandedMenu = false
                            viewModel.deleteTask(currentTask.id)
                            onBack()
                        }
                    )
                }
            }
        }

        // Summary box
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                LinearProgressIndicator(
                    progress = { overallPercent },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(CircleShape),
                    color = if (currentTask.isCompleted && overallPercent >= 1.0f) ColorSuccess else MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = Loc.getText("goal_target_prefix") + if (currentTask.targetType == "PLAY_COUNT") {
                        String.format(Loc.getText("goal_type_plays_desc"), currentTask.targetValue)
                    } else {
                        String.format(Loc.getText("goal_type_days_desc"), currentTask.targetValue)
                    },
                    fontSize = 14.sp
                )
                Text(
                    text = String.format(Loc.getText("schedule_alert_desc"), currentTask.reminderTime, formatScheduledDays(currentTask.scheduledDays)),
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
        }

        val sortedTaskTracks = remember(sortedProgressWithTracks) {
            sortedProgressWithTracks.map { it.second }
        }

        TrackListSortHeader(
            totalCount = sortedProgressWithTracks.size,
            sortBy = taskSortBy,
            onSortByChange = { taskSortBy = it },
            isAscending = taskIsAscending,
            onIsAscendingChange = { taskIsAscending = it }
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(sortedProgressWithTracks) { (progress, track) ->
                val isCurrentExecuting = AudioPlayerManager.currentTrack.collectAsStateWithLifecycle().value?.id == track.id
                val isPlaying = AudioPlayerManager.isPlaying.collectAsStateWithLifecycle().value

                val progressText = if (currentTask.targetType == "PLAY_COUNT") {
                    "🎧 ${progress.completedPlayCount} / ${currentTask.targetValue}"
                } else {
                    "📅 ${progress.getDaysList().size} / ${currentTask.targetValue}"
                }

                val startIcon = if (isCurrentExecuting && isPlaying) {
                    Icons.Filled.PlayArrow
                } else {
                    null
                }

                val startIconTint = if (isCurrentExecuting && isPlaying) {
                    MaterialTheme.colorScheme.primary
                } else if (progress.isTrackCompleted) {
                    ColorSuccess
                } else {
                    null
                }

                UnifiedAudioTrackRow(
                    track = track,
                    isCurrentExecuting = isCurrentExecuting,
                    isPlaying = isPlaying,
                    viewModel = viewModel,
                    taskId = currentTask.id,
                    onTrackPlaylistMenuClicked = { showAddToPlaylistDialogForTrack = it },
                    onCreateTask = { _, _ -> },
                    onShowAssociatedTasks = { type, id, name -> onShowAssociatedTasks(type, id, name) },
                    playlistTracks = sortedTaskTracks,
                    customStartIcon = startIcon,
                    customStartIconTint = startIconTint,
                    taskProgressText = progressText
                )
            }
        }

        // Reactivate button (Show only if non-completed in archive)
        if (currentTask.isCompleted && overallPercent < 1.0f) {
            Button(
                onClick = {
                    viewModel.reactivateTask(currentTask.id)
                    Toast.makeText(context, Loc.getText("re_activated_msg"), Toast.LENGTH_LONG).show()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .testTag("reactivate_btn_tag"),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text(Loc.getText("reactivate_task"), fontWeight = FontWeight.Bold)
            }
        }
    }

    if (showAddToPlaylistDialogForTrack != null) {
        AddToPlaylistDialog(
            track = showAddToPlaylistDialogForTrack!!,
            playlists = playlists,
            onDismiss = { showAddToPlaylistDialogForTrack = null },
            onPlaylistSelected = { playlist ->
                viewModel.addTrackToPlaylist(playlist.id, showAddToPlaylistDialogForTrack!!.id)
                Toast.makeText(context, String.format(Loc.getText("added_to_playlist_success"), playlist.name), Toast.LENGTH_SHORT).show()
            },
            onCreatePlaylistClicked = {}
        )
    }

    if (showTaskStatsDialog) {
        val playbackHistoryList by viewModel.playbackHistory.collectAsStateWithLifecycle()
        val taskTrackIds = remember(progressList) { progressList.map { it.trackId }.toSet() }
        
        val taskPlaybackHistory = remember(playbackHistoryList, taskTrackIds, currentTask.startDate, currentTask.id) {
            val taskTitle = currentTask.getDisplayTitle()
            playbackHistoryList.filter { item ->
                val matchesLoggedTask = item.getActiveTaskIds().contains(currentTask.id) || item.getActiveTasksList().contains(taskTitle)
                val matchesLegacy = item.activeTasks.isBlank() && item.trackId in taskTrackIds
                (matchesLoggedTask || matchesLegacy) && item.completedAt >= currentTask.startDate
            }
        }
        
        val firstPlayTimestamp = remember(taskPlaybackHistory) {
            taskPlaybackHistory.minOfOrNull { it.completedAt }
        }
        
        val completionTimestamp = remember(currentTask.isCompleted, taskPlaybackHistory) {
            if (currentTask.isCompleted) {
                taskPlaybackHistory.maxOfOrNull { it.completedAt }
            } else {
                null
            }
        }

        val totalTaskDurationMs = remember(taskTracks) {
            taskTracks.sumOf { it.duration }
        }

        val totalTimeListenedMs = remember(progressWithTracks, currentTask.targetType) {
            progressWithTracks.sumOf { (p, track) ->
                val effectivePlays = if (currentTask.targetType == "DAYS_COUNT") {
                    maxOf(p.completedPlayCount, p.getDaysList().size)
                } else {
                    p.completedPlayCount
                }
                val fullPlaysDuration = track.duration * effectivePlays
                val currentProgressDuration = (track.duration * (track.getProgressPercent() / 100.0)).toLong()
                fullPlaysDuration + currentProgressDuration
            }
        }

        val isAr = remember { Loc.currentLanguage == "ar" }

        val totalTaskDurationStr = remember(totalTaskDurationMs, isAr) {
            val totalSeconds = totalTaskDurationMs / 1000
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            val seconds = totalSeconds % 60
            if (isAr) {
                when {
                    hours > 0 -> "$hours ساعة و $minutes دقيقة و $seconds ثانية"
                    minutes > 0 -> "$minutes دقيقة و $seconds ثانية"
                    else -> "$seconds ثانية"
                }
            } else {
                when {
                    hours > 0 -> "${hours}h ${minutes}m ${seconds}s"
                    minutes > 0 -> "${minutes}m ${seconds}s"
                    else -> "${seconds}s"
                }
            }
        }

        val totalTimeListenedStr = remember(totalTimeListenedMs, isAr) {
            val totalSeconds = totalTimeListenedMs / 1000
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            val seconds = totalSeconds % 60
            if (isAr) {
                when {
                    hours > 0 -> "$hours ساعة و $minutes دقيقة و $seconds ثانية"
                    minutes > 0 -> "$minutes دقيقة و $seconds ثانية"
                    else -> "$seconds ثانية"
                }
            } else {
                when {
                    hours > 0 -> "${hours}h ${minutes}m ${seconds}s"
                    minutes > 0 -> "${minutes}m ${seconds}s"
                    else -> "${seconds}s"
                }
            }
        }

        val sdf = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
        val createdDateStr = remember(currentTask.startDate) {
            sdf.format(java.util.Date(currentTask.startDate))
        }
        val firstPlayDateStr = remember(firstPlayTimestamp) {
            firstPlayTimestamp?.let { sdf.format(java.util.Date(it)) } ?: Loc.getText("stat_no_plays_yet")
        }
        val completionDateStr = remember(currentTask.isCompleted, completionTimestamp) {
            if (currentTask.isCompleted) {
                completionTimestamp?.let { sdf.format(java.util.Date(it)) } ?: Loc.getText("status_completed")
            } else {
                Loc.getText("stat_in_progress")
            }
        }

        val timeSinceCreation = remember(currentTask.startDate, Loc.currentLanguage) {
            val diff = System.currentTimeMillis() - currentTask.startDate
            val mins = diff / (1000 * 60)
            val hours = mins / 60
            val days = hours / 24
            if (Loc.currentLanguage == "ar") {
                when {
                    days > 0 -> "منذ $days يوم"
                    hours > 0 -> "منذ $hours ساعة"
                    mins > 0 -> "منذ $mins دقيقة"
                    else -> "الآن"
                }
            } else {
                when {
                    days > 1 -> "$days days ago"
                    days == 1L -> "1 day ago"
                    hours > 1 -> "$hours hours ago"
                    hours == 1L -> "1 hour ago"
                    mins > 1 -> "$mins minutes ago"
                    mins == 1L -> "1 minute ago"
                    else -> "Just now"
                }
            }
        }

        AlertDialog(
            onDismissRequest = { showTaskStatsDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Filled.BarChart,
                    contentDescription = "Stats Icon",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(
                    text = Loc.getText("task_stats_title"),
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 1. Overall Progress Section
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = Loc.getText("stat_progress"),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "${(overallPercent * 100).toInt()}%",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            LinearProgressIndicator(
                                progress = { overallPercent },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                            )
                        }
                    }

                    // 2. Chronological Milestones Card
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Milestone 1: Created on
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.CalendarToday,
                                    contentDescription = "Created Icon",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = Loc.getText("stat_created_date"),
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = createdDateStr,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                            // Milestone 2: Time elapsed since creation
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Schedule,
                                    contentDescription = "Elapsed Icon",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = Loc.getText("stat_days_remaining"),
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = timeSinceCreation,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                            // Milestone 3: First play
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.PlayArrow,
                                    contentDescription = "First Play Icon",
                                    tint = if (firstPlayTimestamp != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.size(16.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = Loc.getText("stat_first_play"),
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = firstPlayDateStr,
                                        fontSize = 12.sp,
                                        fontWeight = if (firstPlayTimestamp != null) FontWeight.Bold else FontWeight.Normal,
                                        color = if (firstPlayTimestamp != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    )
                                }
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                            // Milestone 4: Completion Date
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = if (currentTask.isCompleted) Icons.Filled.CheckCircle else Icons.Filled.PendingActions,
                                    contentDescription = "Completion Icon",
                                    tint = if (currentTask.isCompleted) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.size(16.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = Loc.getText("stat_completion_date"),
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = completionDateStr,
                                        fontSize = 12.sp,
                                        fontWeight = if (currentTask.isCompleted) FontWeight.Bold else FontWeight.Normal,
                                        color = if (currentTask.isCompleted) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    )
                                }
                            }
                        }
                    }

                    // 3. Quantitative Achievements Card
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Stat row 1: Target requirements
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = Loc.getText("stat_target_type"),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                val targetDesc = if (currentTask.targetType == "PLAY_COUNT") {
                                    "${currentTask.targetValue} " + Loc.getText("sort_play_count")
                                } else {
                                    "${currentTask.targetValue} " + Loc.getText("sort_progress")
                                }
                                Text(
                                    text = targetDesc,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                            // Stat row 2: Total sessions completed / days active
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val totalLabel = if (currentTask.targetType == "PLAY_COUNT") Loc.getText("stat_total_listens") else Loc.getText("days_completed")
                                val totalValue = if (currentTask.targetType == "PLAY_COUNT") {
                                    progressList.sumOf { it.completedPlayCount }
                                } else {
                                    progressList.sumOf { it.getDaysList().size }
                                }
                                Text(
                                    text = totalLabel,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "$totalValue",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                            // Stat row 3: Tracks fully completed ratio
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = Loc.getText("stat_completed_tracks_ratio"),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "$finishedTracksCount / $totalTracksCount",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                            // Stat row 4: Total Task Duration
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = Loc.getText("stat_total_duration"),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = totalTaskDurationStr,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                            // Stat row 5: Cumulative Time Listened
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = Loc.getText("stat_total_time_listened"),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = totalTimeListenedStr,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showTaskStatsDialog = false },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(Loc.getText("close_dialog"))
                }
            }
        )
    }
}

// --- SUB-SCREEN 7: CREATE / EDIT TASK VIEW ---
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CreateTaskScreen(
    viewModel: AppViewModel,
    editingTask: Task? = null,
    predefinedSource: Pair<String, Long?>? = null, // type, id
    taskCreationPreselectedTrackIds: Set<Long> = emptySet(),
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var title by remember(editingTask) { mutableStateOf(editingTask?.getBaseTitle() ?: "") }
    var isTitleManuallyEdited by remember(editingTask) { mutableStateOf(editingTask != null) }
    val normalizedPredefinedType = when (predefinedSource?.first) {
        "FOLDER", "FOLDERS" -> "FOLDER"
        "PLAYLIST" -> "PLAYLIST"
        "TRACKS" -> "TRACKS"
        else -> predefinedSource?.first
    }
    var sourceType by remember(editingTask, predefinedSource) {
        mutableStateOf(editingTask?.sourceType ?: normalizedPredefinedType ?: "FOLDER")
    } // FOLDER, PLAYLIST, TRACKS
    var sourceId by remember(editingTask, predefinedSource) {
        mutableStateOf(editingTask?.sourceId ?: predefinedSource?.second)
    }

    var targetType by remember(editingTask) { mutableStateOf(editingTask?.targetType ?: "PLAY_COUNT") } // PLAY_COUNT, DAYS_COUNT
    var targetValue by remember(editingTask) { mutableStateOf(editingTask?.targetValue ?: 3) }

    var useCustomThreshold by remember(editingTask) { mutableStateOf(editingTask?.customThreshold != null) }
    var taskThresholdValue by remember(editingTask) { mutableStateOf((editingTask?.customThreshold ?: 90).toFloat()) }

    val initialDays = remember(editingTask) {
        if (!editingTask?.scheduledDays.isNullOrEmpty()) {
            editingTask!!.scheduledDays.split(",").filter { it.isNotEmpty() }.toSet()
        } else {
            setOf("SUNDAY", "MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY")
        }
    }
    var scheduledDays by remember(initialDays) { mutableStateOf(initialDays) }
    var reminderTime by remember(editingTask) { mutableStateOf(editingTask?.reminderTime ?: "09:00 AM") }
    var enableDailyGoal by remember(editingTask) {
        mutableStateOf(editingTask?.dailyTargetValue != null && editingTask.dailyTargetValue > 0)
    }
    var dailyTargetValue by remember(editingTask) {
        mutableStateOf(editingTask?.dailyTargetValue ?: 1)
    }

    val coroutineScope = rememberCoroutineScope()
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { 4 })
    val currentStep = pagerState.currentPage + 1

    val allFolders by viewModel.folders.collectAsStateWithLifecycle()
    val allPlaylists by viewModel.playlists.collectAsStateWithLifecycle()
    val allTracks by viewModel.tracks.collectAsStateWithLifecycle()

    val taskLabelsList by viewModel.taskLabels.collectAsStateWithLifecycle(emptyList())
    val activeTasksList by viewModel.activeTasks.collectAsStateWithLifecycle(emptyList())
    val completedTasksList by viewModel.completedTasks.collectAsStateWithLifecycle(emptyList())

    val availableExistingLabels = remember(taskLabelsList, activeTasksList, completedTasksList) {
        val fromDb = taskLabelsList.map { it.name.trim() }
        val fromTasks = (activeTasksList + completedTasksList).flatMap { it.getLabelsList() }
        (fromDb + fromTasks).filter { it.isNotBlank() }.distinctBy { it.lowercase(Locale.getDefault()) }
    }

    var taskLabels by remember(editingTask) {
        mutableStateOf(editingTask?.getLabelsList() ?: emptyList())
    }
    var newLabelInput by remember { mutableStateOf("") }

    var selectedManualTrackIds by remember(editingTask) { mutableStateOf(emptySet<Long>()) }

    // Auto-update title based on selection if not manually edited yet or if currently empty
    LaunchedEffect(sourceType, sourceId, selectedManualTrackIds, allFolders, allPlaylists, allTracks) {
        if (editingTask == null && (!isTitleManuallyEdited || title.trim().isEmpty())) {
            val autoTitle = when (sourceType) {
                "FOLDER" -> {
                    if (sourceId != null) {
                        allFolders.find { it.id == sourceId }?.folderName ?: ""
                    } else ""
                }
                "PLAYLIST" -> {
                    if (sourceId != null) {
                        allPlaylists.find { it.id == sourceId }?.name ?: ""
                    } else ""
                }
                "TRACKS" -> {
                    if (selectedManualTrackIds.size == 1) {
                        val trackId = selectedManualTrackIds.first()
                        allTracks.find { it.id == trackId }?.getDisplayTitle() ?: ""
                    } else if (selectedManualTrackIds.size > 1) {
                        Loc.getText("group_goal_task_title")
                    } else ""
                }
                else -> ""
            }
            if (autoTitle.isNotEmpty()) {
                title = autoTitle
            }
        }
    }

    // Smart auto-population of initial title and track selection when database loads
    LaunchedEffect(allFolders, allPlaylists, allTracks, predefinedSource, editingTask, taskCreationPreselectedTrackIds) {
        if (editingTask != null) {
            title = editingTask.getBaseTitle()
            sourceType = editingTask.sourceType
            sourceId = editingTask.sourceId
            targetType = editingTask.targetType
            targetValue = editingTask.targetValue
            reminderTime = editingTask.reminderTime
            enableDailyGoal = editingTask.dailyTargetValue != null && editingTask.dailyTargetValue > 0
            dailyTargetValue = editingTask.dailyTargetValue ?: 1
            
            if (editingTask.sourceType == "TRACKS") {
                val progress = viewModel.getProgressForTask(editingTask.id)
                selectedManualTrackIds = progress.map { it.trackId }.toSet()
            }
        } else {
            if (predefinedSource != null) {
                val normType = when (predefinedSource.first) {
                    "FOLDER", "FOLDERS" -> "FOLDER"
                    "PLAYLIST" -> "PLAYLIST"
                    "TRACKS" -> "TRACKS"
                    else -> predefinedSource.first
                }
                sourceType = normType
                if (predefinedSource.second != null) {
                    sourceId = predefinedSource.second
                }
            }
            if (title.isEmpty()) {
                val initialTitle = if (predefinedSource?.first == "TRACKS" && predefinedSource.second != null) {
                    val matchedTrack = allTracks.find { it.id == predefinedSource.second }
                    matchedTrack?.getDisplayTitle() ?: ""
                } else if ((predefinedSource?.first == "FOLDER" || predefinedSource?.first == "FOLDERS") && predefinedSource.second != null) {
                    val matchedFolder = allFolders.find { it.id == predefinedSource.second }
                    matchedFolder?.folderName ?: ""
                } else if (predefinedSource?.first == "PLAYLIST" && predefinedSource.second != null) {
                    val matchedPlaylist = allPlaylists.find { it.id == predefinedSource.second }
                    matchedPlaylist?.name ?: ""
                } else if (predefinedSource?.first == "TRACKS" && taskCreationPreselectedTrackIds.isNotEmpty()) {
                    Loc.getText("group_goal_task_title")
                } else {
                    ""
                }
                if (initialTitle.isNotEmpty()) {
                    title = initialTitle
                }
            }
        }

        if (editingTask == null && selectedManualTrackIds.isEmpty()) {
            if (predefinedSource?.first == "TRACKS") {
                if (predefinedSource.second != null) {
                    selectedManualTrackIds = setOf(predefinedSource.second!!)
                } else if (taskCreationPreselectedTrackIds.isNotEmpty()) {
                    selectedManualTrackIds = taskCreationPreselectedTrackIds
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
    ) {
        // Top Header & Stepper pinned at the top
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 20.dp, start = 24.dp, end = 24.dp, bottom = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (editingTask != null) Loc.getText("edit_task_title") else Loc.getText("create_task_title"),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
            }
            
            // Stepper Progress dots
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                (1..4).forEach { step ->
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(
                                color = if (currentStep == step) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                shape = CircleShape
                            )
                            .clickable {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(step - 1)
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "$step",
                            color = if (currentStep == step) Color.White else MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // SWIPEABLE TABS / STEPS CONTENT (HorizontalPager)
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) { page ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 8.dp)
            ) {
                when (page) {
                    0 -> {
                Text(Loc.getText("step_1"), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.secondary)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = title,
                    onValueChange = { 
                        title = it
                        isTitleManuallyEdited = true
                    },
                    label = { Text(Loc.getText("task_title_label")) },
                    placeholder = { Text(Loc.getText("placeholder_task_title")) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("task_title_input")
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                Text(Loc.getText("source_type_label"), fontWeight = FontWeight.Bold)
                
                // Radios
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = sourceType == "FOLDER", onClick = { sourceType = "FOLDER"; sourceId = null })
                    Text(Loc.getText("use_folder"))
                }
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = sourceType == "PLAYLIST", onClick = { sourceType = "PLAYLIST"; sourceId = null })
                    Text(Loc.getText("use_playlist"))
                }
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = sourceType == "TRACKS", onClick = { sourceType = "TRACKS"; sourceId = null })
                    Text(Loc.getText("use_tracks"))
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                )

                // Dropdowns for folder / playlists
                Spacer(modifier = Modifier.height(4.dp))
                if (sourceType == "FOLDER") {
                    val folderTree = remember(allFolders) {
                        buildFolderTree(allFolders)
                    }

                    // Keep root/parent folders and any ancestors of selected folder expanded by default
                    var expandedFolderIds by remember(allFolders, sourceId) {
                        val parentIds = allFolders.filter { f ->
                            allFolders.any { it.parentFolderId == f.id || (it.id != f.id && it.folderPath.startsWith(f.folderPath + java.io.File.separator)) }
                        }.map { it.id }.toMutableSet()
                        if (sourceId != null) {
                            var curr = allFolders.find { it.id == sourceId }
                            while (curr != null && curr.parentFolderId != null) {
                                parentIds.add(curr.parentFolderId!!)
                                curr = allFolders.find { it.id == curr.parentFolderId }
                            }
                        }
                        mutableStateOf(parentIds.toSet())
                    }

                    val flattenedVisibleTree = remember(folderTree, expandedFolderIds) {
                        flattenFolderTree(folderTree, expandedFolderIds)
                    }

                    val allParentIds = remember(allFolders) {
                        allFolders.filter { f ->
                            allFolders.any { it.parentFolderId == f.id || (it.id != f.id && it.folderPath.startsWith(f.folderPath + java.io.File.separator)) }
                        }.map { it.id }.toSet()
                    }

                    if (allParentIds.isNotEmpty()) {
                        val isAllExpanded = expandedFolderIds.containsAll(allParentIds)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 6.dp),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = {
                                    expandedFolderIds = if (isAllExpanded) emptySet() else allParentIds
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Icon(
                                    imageVector = if (isAllExpanded) Icons.Filled.UnfoldLess else Icons.Filled.UnfoldMore,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (isAllExpanded) Loc.getText("collapse_all") else Loc.getText("expand_all"),
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    if (flattenedVisibleTree.isEmpty()) {
                        Text(
                            text = Loc.getText("no_folders_found"),
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    } else {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            flattenedVisibleTree.forEach { node ->
                                val folder = node.folder
                                val isSelected = (sourceId == folder.id)
                                val hasChildren = node.children.isNotEmpty()
                                val isExpanded = expandedFolderIds.contains(folder.id)
                                val trackCount = allTracks.count { it.parentFolderId == folder.id }

                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f) else Color.Transparent,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = (node.depth * 18).dp)
                                        .clickable { sourceId = folder.id }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 4.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Expand/Collapse arrow icon (if has subfolders) or Indent spacer
                                        if (hasChildren) {
                                            IconButton(
                                                onClick = {
                                                    expandedFolderIds = if (isExpanded) {
                                                        expandedFolderIds - folder.id
                                                    } else {
                                                        expandedFolderIds + folder.id
                                                    }
                                                },
                                                modifier = Modifier.size(26.dp)
                                            ) {
                                                Icon(
                                                    imageVector = if (isExpanded) Icons.Filled.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                                                    modifier = Modifier.size(18.dp),
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        } else {
                                            if (node.depth > 0) {
                                                Icon(
                                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                                    contentDescription = null,
                                                    modifier = Modifier
                                                        .size(18.dp)
                                                        .padding(start = 2.dp),
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                            } else {
                                                Spacer(modifier = Modifier.width(26.dp))
                                            }
                                        }

                                        RadioButton(
                                            selected = isSelected,
                                            onClick = { sourceId = folder.id },
                                            modifier = Modifier.size(28.dp)
                                        )

                                        Spacer(modifier = Modifier.width(4.dp))

                                        Icon(
                                            imageVector = if (hasChildren && isExpanded) Icons.Filled.FolderOpen else Icons.Filled.Folder,
                                            contentDescription = null,
                                            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )

                                        Spacer(modifier = Modifier.width(6.dp))

                                        Text(
                                            text = folder.folderName,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (node.depth == 0) FontWeight.SemiBold else FontWeight.Normal,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.weight(1f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )

                                        if (trackCount > 0) {
                                            Surface(
                                                shape = CircleShape,
                                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                                modifier = Modifier.padding(start = 4.dp)
                                            ) {
                                                Text(
                                                    text = "$trackCount",
                                                    fontSize = 11.sp,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else if (sourceType == "PLAYLIST") {
                    allPlaylists.forEach { p ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { sourceId = p.id }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = sourceId == p.id, onClick = { sourceId = p.id })
                            Text(p.name, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                } else if (sourceType == "TRACKS") {
                    val folderTree = remember(allFolders) {
                        buildFolderTree(allFolders)
                    }

                    // Keep root/parent folders and any folders with selected tracks expanded by default
                    var expandedFolderIds by remember(allFolders) {
                        val parentIds = allFolders.filter { f ->
                            allFolders.any { it.parentFolderId == f.id || (it.id != f.id && it.folderPath.startsWith(f.folderPath + java.io.File.separator)) }
                        }.map { it.id }.toMutableSet()
                        val foldersWithSelected = allTracks.filter { selectedManualTrackIds.contains(it.id) && it.parentFolderId != null }.mapNotNull { it.parentFolderId }
                        parentIds.addAll(foldersWithSelected)
                        if (parentIds.isEmpty() && allFolders.isNotEmpty()) {
                            parentIds.addAll(allFolders.map { it.id })
                        }
                        mutableStateOf(parentIds.toSet())
                    }
                    var independentTracksExpanded by remember { mutableStateOf(true) }

                    val flattenedVisibleTree = remember(folderTree, expandedFolderIds) {
                        flattenFolderTree(folderTree, expandedFolderIds)
                    }

                    val allParentFolderIds = remember(allFolders) {
                        allFolders.map { it.id }.toSet()
                    }

                    // Header with selection summary and Expand/Collapse All controls
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (selectedManualTrackIds.isNotEmpty()) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Audiotrack,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = if (selectedManualTrackIds.isNotEmpty()) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "${selectedManualTrackIds.size} ${Loc.getText("selected")}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (selectedManualTrackIds.isNotEmpty()) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (selectedManualTrackIds.isNotEmpty()) {
                                TextButton(
                                    onClick = { selectedManualTrackIds = emptySet() },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = Loc.getText("deselect_all_tasks"),
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }

                            if (allParentFolderIds.isNotEmpty()) {
                                val isAllExpanded = expandedFolderIds.containsAll(allParentFolderIds) && independentTracksExpanded
                                TextButton(
                                    onClick = {
                                        if (isAllExpanded) {
                                            expandedFolderIds = emptySet()
                                            independentTracksExpanded = false
                                        } else {
                                            expandedFolderIds = allParentFolderIds
                                            independentTracksExpanded = true
                                        }
                                    },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isAllExpanded) Icons.Filled.UnfoldLess else Icons.Filled.UnfoldMore,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (isAllExpanded) Loc.getText("collapse_all") else Loc.getText("expand_all"),
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }

                    if (allTracks.isEmpty()) {
                        Text(
                            text = Loc.getText("empty_tracks_desc"),
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    } else {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            // Render folders in tree hierarchy
                            flattenedVisibleTree.forEach { node ->
                                val folder = node.folder
                                val hasSubfolders = node.children.isNotEmpty()
                                val isExpanded = expandedFolderIds.contains(folder.id)
                                val directTracks = allTracks.filter { it.parentFolderId == folder.id }
                                val hasItems = hasSubfolders || directTracks.isNotEmpty()
                                val selectedInFolderCount = directTracks.count { selectedManualTrackIds.contains(it.id) }

                                // 1. Folder row (selection of files only, no folders)
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (selectedInFolderCount > 0) MaterialTheme.colorScheme.primary.copy(alpha = 0.06f) else Color.Transparent,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = (node.depth * 18).dp)
                                        .clickable {
                                            expandedFolderIds = if (isExpanded) {
                                                expandedFolderIds - folder.id
                                            } else {
                                                expandedFolderIds + folder.id
                                            }
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 4.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (hasItems) {
                                            IconButton(
                                                onClick = {
                                                    expandedFolderIds = if (isExpanded) {
                                                        expandedFolderIds - folder.id
                                                    } else {
                                                        expandedFolderIds + folder.id
                                                    }
                                                },
                                                modifier = Modifier.size(26.dp)
                                            ) {
                                                Icon(
                                                    imageVector = if (isExpanded) Icons.Filled.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                                                    modifier = Modifier.size(18.dp),
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        } else {
                                            if (node.depth > 0) {
                                                Icon(
                                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                                    contentDescription = null,
                                                    modifier = Modifier
                                                        .size(18.dp)
                                                        .padding(start = 2.dp),
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                            } else {
                                                Spacer(modifier = Modifier.width(26.dp))
                                            }
                                        }

                                        Icon(
                                            imageVector = if (hasItems && isExpanded) Icons.Filled.FolderOpen else Icons.Filled.Folder,
                                            contentDescription = null,
                                            tint = if (selectedInFolderCount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )

                                        Spacer(modifier = Modifier.width(6.dp))

                                        Text(
                                            text = folder.folderName,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (node.depth == 0) FontWeight.SemiBold else FontWeight.Normal,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.weight(1f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )

                                        if (selectedInFolderCount > 0) {
                                            Surface(
                                                shape = CircleShape,
                                                color = MaterialTheme.colorScheme.primaryContainer,
                                                modifier = Modifier.padding(horizontal = 4.dp)
                                            ) {
                                                Text(
                                                    text = "$selectedInFolderCount",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                                )
                                            }
                                        }

                                        if (directTracks.isNotEmpty()) {
                                            Surface(
                                                shape = CircleShape,
                                                color = MaterialTheme.colorScheme.surfaceVariant,
                                                modifier = Modifier.padding(start = 2.dp)
                                            ) {
                                                Text(
                                                    text = "${directTracks.size}",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Normal,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }

                                // 2. Direct files inside this folder (when expanded)
                                if (isExpanded) {
                                    directTracks.forEach { track ->
                                        val isTrackSelected = selectedManualTrackIds.contains(track.id)
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (isTrackSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f) else Color.Transparent,
                                              modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(start = ((node.depth + 1) * 18 + 8).dp)
                                                .clickable {
                                                    val current = selectedManualTrackIds.toMutableSet()
                                                    if (current.contains(track.id)) current.remove(track.id) else current.add(track.id)
                                                    selectedManualTrackIds = current
                                                }
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 6.dp, vertical = 5.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Checkbox(
                                                    checked = isTrackSelected,
                                                    onCheckedChange = { checked ->
                                                        val current = selectedManualTrackIds.toMutableSet()
                                                        if (checked) current.add(track.id) else current.remove(track.id)
                                                        selectedManualTrackIds = current
                                                    },
                                                    modifier = Modifier.size(24.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Icon(
                                                    imageVector = getTrackFileIcon(track),
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = middleEllipse(track.getDisplayTitle(), maxLength = 28),
                                                        fontSize = 13.sp,
                                                        fontWeight = if (isTrackSelected) FontWeight.SemiBold else FontWeight.Normal,
                                                        color = if (isTrackSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    Row(
                                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        if (track.duration > 0) {
                                                            Text(
                                                                text = formatDuration(track.duration),
                                                                fontSize = 10.sp,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                                            )
                                                        }
                                                        Text(
                                                            text = "${track.getProgressPercent()}%",
                                                            fontSize = 10.sp,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // Independent tracks (not in any scanned folder)
                            val validFolderIds = allFolders.map { it.id }.toSet()
                            val independentTracks = allTracks.filter { (it.parentFolderId == null || !validFolderIds.contains(it.parentFolderId)) }
                            if (independentTracks.isNotEmpty()) {
                                val selectedIndCount = independentTracks.count { selectedManualTrackIds.contains(it.id) }
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (selectedIndCount > 0) MaterialTheme.colorScheme.primary.copy(alpha = 0.06f) else Color.Transparent,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { independentTracksExpanded = !independentTracksExpanded }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 4.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        IconButton(
                                            onClick = { independentTracksExpanded = !independentTracksExpanded },
                                            modifier = Modifier.size(26.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (independentTracksExpanded) Icons.Filled.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                                contentDescription = if (independentTracksExpanded) "Collapse" else "Expand",
                                                modifier = Modifier.size(18.dp),
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }

                                        Icon(
                                            imageVector = if (independentTracksExpanded) Icons.Filled.FolderOpen else Icons.Filled.Folder,
                                            contentDescription = null,
                                            tint = if (selectedIndCount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )

                                        Spacer(modifier = Modifier.width(6.dp))

                                        Text(
                                            text = Loc.getText("independent_tracks_section"),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.weight(1f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )

                                        if (selectedIndCount > 0) {
                                            Surface(
                                                shape = CircleShape,
                                                color = MaterialTheme.colorScheme.primaryContainer,
                                                modifier = Modifier.padding(horizontal = 4.dp)
                                            ) {
                                                Text(
                                                    text = "$selectedIndCount",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                                )
                                            }
                                        }

                                        Surface(
                                            shape = CircleShape,
                                            color = MaterialTheme.colorScheme.surfaceVariant,
                                            modifier = Modifier.padding(start = 2.dp)
                                        ) {
                                            Text(
                                                text = "${independentTracks.size}",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Normal,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }

                                if (independentTracksExpanded) {
                                    independentTracks.forEach { track ->
                                        val isTrackSelected = selectedManualTrackIds.contains(track.id)
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (isTrackSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f) else Color.Transparent,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(start = 26.dp)
                                                .clickable {
                                                    val current = selectedManualTrackIds.toMutableSet()
                                                    if (current.contains(track.id)) current.remove(track.id) else current.add(track.id)
                                                    selectedManualTrackIds = current
                                                }
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 6.dp, vertical = 5.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Checkbox(
                                                    checked = isTrackSelected,
                                                    onCheckedChange = { checked ->
                                                        val current = selectedManualTrackIds.toMutableSet()
                                                        if (checked) current.add(track.id) else current.remove(track.id)
                                                        selectedManualTrackIds = current
                                                    },
                                                    modifier = Modifier.size(24.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Icon(
                                                    imageVector = getTrackFileIcon(track),
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = middleEllipse(track.getDisplayTitle(), maxLength = 28),
                                                        fontSize = 13.sp,
                                                        fontWeight = if (isTrackSelected) FontWeight.SemiBold else FontWeight.Normal,
                                                        color = if (isTrackSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    Row(
                                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        if (track.duration > 0) {
                                                            Text(
                                                                text = formatDuration(track.duration),
                                                                fontSize = 10.sp,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                                            )
                                                        }
                                                        Text(
                                                            text = "${track.getProgressPercent()}%",
                                                            fontSize = 10.sp,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                                        )
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
            }

                    1 -> {
                Text(Loc.getText("step_2"), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.secondary)
                Spacer(modifier = Modifier.height(12.dp))
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = targetType == "PLAY_COUNT", onClick = { targetType = "PLAY_COUNT" })
                    Text(Loc.getText("goal_type_plays"))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = targetType == "DAYS_COUNT", onClick = { targetType = "DAYS_COUNT" })
                    Text(Loc.getText("goal_type_days"))
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text(Loc.getText("target_value_label"), fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { if (targetValue > 1) targetValue-- }) {
                        Icon(Icons.Filled.RemoveCircleOutline, "Dec")
                    }
                    Text("$targetValue", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp))
                    IconButton(onClick = { targetValue++ }) {
                        Icon(Icons.Filled.AddCircleOutline, "Inc")
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                )
                Spacer(modifier = Modifier.height(12.dp))

                // Custom Completion Threshold Toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { useCustomThreshold = !useCustomThreshold }
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = Loc.getText("custom_threshold_toggle"),
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                    Switch(
                        checked = useCustomThreshold,
                        onCheckedChange = { useCustomThreshold = it },
                        modifier = Modifier.testTag("custom_threshold_switch")
                    )
                }

                if (useCustomThreshold) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = Loc.getText("custom_threshold_slider_label") + ": ${taskThresholdValue.toInt()}%",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Slider(
                                value = taskThresholdValue,
                                onValueChange = { taskThresholdValue = it },
                                valueRange = 70f..100f,
                                steps = 30,
                                modifier = Modifier.testTag("custom_threshold_slider")
                            )
                            Text(
                                text = Loc.getText("eligible_threshold_note"),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                )
                Spacer(modifier = Modifier.height(12.dp))

                // Labels or Tags section
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Label,
                        contentDescription = "Labels",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = Loc.getText("task_labels_title"),
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Text(
                            text = Loc.getText("task_labels_desc"),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))

                // Input field + Add button for new label
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = newLabelInput,
                        onValueChange = { newLabelInput = it },
                        placeholder = { Text(Loc.getText("new_label_placeholder"), fontSize = 13.sp) },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("task_label_input"),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            imeAction = androidx.compose.ui.text.input.ImeAction.Done
                        ),
                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                            onDone = {
                                val trimmed = newLabelInput.trim()
                                if (trimmed.isNotEmpty() && !taskLabels.any { it.equals(trimmed, ignoreCase = true) }) {
                                    taskLabels = taskLabels + trimmed
                                    newLabelInput = ""
                                }
                            }
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val trimmed = newLabelInput.trim()
                            if (trimmed.isNotEmpty() && !taskLabels.any { it.equals(trimmed, ignoreCase = true) }) {
                                taskLabels = taskLabels + trimmed
                                newLabelInput = ""
                            }
                        },
                        enabled = newLabelInput.isNotBlank(),
                        modifier = Modifier.testTag("add_task_label_btn")
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "Add", modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(Loc.getText("add_label"), fontSize = 12.sp)
                    }
                }

                // Selected labels chips
                if (taskLabels.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        taskLabels.forEach { label ->
                            InputChip(
                                selected = true,
                                onClick = { taskLabels = taskLabels - label },
                                label = { Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
                                trailingIcon = {
                                    Icon(
                                        imageVector = Icons.Filled.Close,
                                        contentDescription = "Remove",
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                colors = InputChipDefaults.inputChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            )
                        }
                    }
                }

                // Currently used labels from other tasks / DB
                val unselectedExistingLabels = availableExistingLabels.filter { exist ->
                    !taskLabels.any { it.equals(exist, ignoreCase = true) }
                }
                if (unselectedExistingLabels.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = Loc.getText("currently_used_labels_hint"),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        unselectedExistingLabels.forEach { existing ->
                            FilterChip(
                                selected = false,
                                onClick = {
                                    taskLabels = taskLabels + existing
                                },
                                label = { Text(existing, fontSize = 12.sp) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Filled.Add,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            )
                        }
                    }
                }
            }

                    2 -> {
                Text(Loc.getText("step_3"), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.secondary)
                Spacer(modifier = Modifier.height(12.dp))
                Text(Loc.getText("select_days"), fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(8.dp))

                val weekDays = listOf(
                    Pair("SUNDAY", "الأحد"),
                    Pair("MONDAY", "الإثنين"),
                    Pair("TUESDAY", "الثلاثاء"),
                    Pair("WEDNESDAY", "الأربعاء"),
                    Pair("THURSDAY", "الخميس"),
                    Pair("FRIDAY", "الجمعة"),
                    Pair("SATURDAY", "السبت")
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    weekDays.forEach { (en, ar) ->
                        val checked = scheduledDays.contains(en)
                        val label = if (Loc.currentLanguage == "ar") {
                            when (en) {
                                "SUNDAY" -> "أحد"
                                "MONDAY" -> "اثنين"
                                "TUESDAY" -> "ثلاثاء"
                                "WEDNESDAY" -> "أربعاء"
                                "THURSDAY" -> "خميس"
                                "FRIDAY" -> "جمعة"
                                "SATURDAY" -> "سبت"
                                else -> ar
                            }
                        } else {
                            when (en) {
                                "SUNDAY" -> "Sun"
                                "MONDAY" -> "Mon"
                                "TUESDAY" -> "Tue"
                                "WEDNESDAY" -> "Wed"
                                "THURSDAY" -> "Thu"
                                "FRIDAY" -> "Fri"
                                "SATURDAY" -> "Sat"
                                else -> en
                            }
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 2.dp)
                                .aspectRatio(1f)
                                .clip(CircleShape)
                                .background(
                                    if (checked) MaterialTheme.colorScheme.primary 
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .clickable {
                                    val current = scheduledDays.toMutableSet()
                                    if (checked) current.remove(en) else current.add(en)
                                    scheduledDays = current
                                }
                                .padding(2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                color = if (checked) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
                Text(Loc.getText("reminder_time_label"), fontWeight = FontWeight.Bold)
                Button(
                    onClick = {
                        val cal = Calendar.getInstance()
                        TimePickerDialog(
                            context,
                            { _, h, m ->
                                val ampm = if (h >= 12) "PM" else "AM"
                                val displayH = if (h % 12 == 0) 12 else h % 12
                                reminderTime = String.format(Locale.getDefault(), "%02d:%02d %s", displayH, m, ampm)
                            },
                            cal.get(Calendar.HOUR_OF_DAY),
                            cal.get(Calendar.MINUTE),
                            false
                        ).show()
                    },
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    Text("${Loc.getText("change_time")}: $reminderTime")
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 14.dp),
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                )

                // Optional Daily Mini-Goal
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { enableDailyGoal = !enableDailyGoal }
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = Loc.getText("daily_goal_title"),
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = Loc.getText("daily_goal_desc"),
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                        )
                    }
                    Switch(
                        checked = enableDailyGoal,
                        onCheckedChange = { enableDailyGoal = it },
                        modifier = Modifier.testTag("daily_goal_switch")
                    )
                }

                if (enableDailyGoal) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = Loc.getText("daily_goal_plays_label"),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilledTonalIconButton(
                                    onClick = { if (dailyTargetValue > 1) dailyTargetValue-- },
                                    modifier = Modifier.size(36.dp).testTag("dec_daily_goal_btn")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Remove,
                                        contentDescription = "Decrease daily goal",
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Text(
                                    text = "$dailyTargetValue",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(horizontal = 12.dp)
                                )
                                FilledTonalIconButton(
                                    onClick = { dailyTargetValue++ },
                                    modifier = Modifier.size(36.dp).testTag("inc_daily_goal_btn")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Add,
                                        contentDescription = "Increase daily goal",
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = Loc.getText("daily_goal_unit"),
                                    fontSize = 12.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

                    3 -> {
                Text(Loc.getText("step_4"), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.secondary)
                Spacer(modifier = Modifier.height(12.dp))
                
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val isAr = Loc.currentLanguage == "ar"
                        
                        // Title
                        val currentWizardTitle = Task.buildCombinedTitle(
                            title.trim().ifEmpty {
                                val autoTitle = when (sourceType) {
                                    "FOLDER" -> allFolders.find { it.id == sourceId }?.folderName
                                    "PLAYLIST" -> allPlaylists.find { it.id == sourceId }?.name
                                    "TRACKS" -> {
                                        if (selectedManualTrackIds.size == 1) {
                                            allTracks.find { it.id == selectedManualTrackIds.first() }?.getDisplayTitle()
                                        } else if (selectedManualTrackIds.size > 1) {
                                            Loc.getText("group_goal_task_title")
                                        } else null
                                    }
                                    else -> null
                                }
                                autoTitle ?: (if (isAr) "مهمة غير مسماة" else "Unnamed Task")
                            },
                            taskLabels.joinToString(",")
                        )
                        Row {
                            Text(
                                text = "${Loc.getText("wizard_title")}: ", 
                                fontWeight = FontWeight.Bold, 
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(text = currentWizardTitle, fontWeight = FontWeight.Normal)
                        }

                        // Source details
                        val typeLabel = when (sourceType) {
                            "FOLDER" -> Loc.getText("use_folder")
                            "PLAYLIST" -> Loc.getText("use_playlist")
                            "TRACKS" -> Loc.getText("use_tracks")
                            else -> sourceType
                        }
                        
                        val sourceDetailsName = when (sourceType) {
                            "FOLDER" -> allFolders.find { it.id == sourceId }?.folderName ?: ""
                            "PLAYLIST" -> allPlaylists.find { it.id == sourceId }?.name ?: ""
                            "TRACKS" -> {
                                if (isAr) {
                                    "${selectedManualTrackIds.size} ملف(ات) صوتية محددة"
                                } else {
                                    "${selectedManualTrackIds.size} files selected manually"
                                }
                            }
                            else -> ""
                        }
                        
                        Row {
                            Text(
                                text = "${Loc.getText("wizard_source")}: ", 
                                fontWeight = FontWeight.Bold, 
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = if (sourceDetailsName.isNotEmpty()) "$typeLabel ($sourceDetailsName)" else typeLabel,
                                fontWeight = FontWeight.Normal
                            )
                        }

                        // Target
                        val targetDesc = if (targetType == "PLAY_COUNT") {
                            if (isAr) "الاستماع لكل ملف $targetValue مرات كاملة" else "Listen to each file $targetValue times fully"
                        } else {
                            if (isAr) "الاستماع لكل ملف في $targetValue أيام مختلفة" else "Listen to each file on $targetValue different days"
                        }
                        
                        Row {
                            Text(
                                text = "${Loc.getText("wizard_target")}: ", 
                                fontWeight = FontWeight.Bold, 
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(text = targetDesc, fontWeight = FontWeight.Normal)
                        }

                        // Custom Completion Threshold info in summary
                        Row {
                            Text(
                                text = if (isAr) "عتبة الاكتمال: " else "Completion Threshold: ",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = if (useCustomThreshold) {
                                    "${taskThresholdValue.toInt()}%"
                                } else {
                                    if (isAr) "تلقائي (حسب الإعدادات)" else "Default Settings Threshold"
                                },
                                fontWeight = FontWeight.Normal
                            )
                        }

                        // Scheduled days translated
                        val mappedDays = if (scheduledDays.size >= 7) {
                            Loc.getText("all_days")
                        } else {
                            scheduledDays.map { day ->
                                if (isAr) {
                                    when (day) {
                                        "SUNDAY" -> "أحد"
                                        "MONDAY" -> "اثنين"
                                        "TUESDAY" -> "ثلاث"
                                        "WEDNESDAY" -> "أربع"
                                        "THURSDAY" -> "خميس"
                                        "FRIDAY" -> "جمعة"
                                        "SATURDAY" -> "سبت"
                                        else -> day
                                    }
                                } else {
                                    when (day) {
                                        "SUNDAY" -> "Sun"
                                        "MONDAY" -> "Mon"
                                        "TUESDAY" -> "Tue"
                                        "WEDNESDAY" -> "Wed"
                                        "THURSDAY" -> "Thu"
                                        "FRIDAY" -> "Fri"
                                        "SATURDAY" -> "Sat"
                                        else -> day
                                    }
                                }
                            }.joinToString(", ")
                        }
                        
                        Row {
                            Text(
                                text = if (isAr) "جدولة التنبيهات: " else "Alert Schedule: ", 
                                fontWeight = FontWeight.Bold, 
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = if (isAr) "كل [$mappedDays] الساعة $reminderTime" else "On [$mappedDays] at $reminderTime",
                                fontWeight = FontWeight.Normal
                            )
                        }

                        // Daily Goal Summary
                        Row {
                            Text(
                                text = if (isAr) "الهدف اليومي: " else "Daily Goal: ",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = if (enableDailyGoal && dailyTargetValue > 0) {
                                    "$dailyTargetValue ${Loc.getText("daily_goal_unit")}"
                                } else {
                                    Loc.getText("daily_goal_unset")
                                },
                                fontWeight = FontWeight.Normal
                            )
                        }
                    }
                }
            }
                }
                Spacer(modifier = Modifier.height(20.dp))
            }
        }

        // Navigation Footer controls inside stepper
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 3.dp,
            shadowElevation = 4.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (currentStep > 1) {
                    TextButton(onClick = {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage - 1)
                        }
                    }) {
                        Text(Loc.getText("prev"))
                    }
                } else {
                    TextButton(onClick = onDismiss) {
                        Text(Loc.getText("cancel"))
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (editingTask != null && currentStep < 4) {
                        OutlinedButton(
                            onClick = {
                                val rawTitle = title.trim().ifEmpty {
                                    val autoTitle = when (sourceType) {
                                        "FOLDER" -> allFolders.find { it.id == sourceId }?.folderName
                                        "PLAYLIST" -> allPlaylists.find { it.id == sourceId }?.name
                                        "TRACKS" -> {
                                            if (selectedManualTrackIds.size == 1) {
                                                allTracks.find { it.id == selectedManualTrackIds.first() }?.getDisplayTitle()
                                            } else if (selectedManualTrackIds.size > 1) {
                                                Loc.getText("group_goal_task_title")
                                            } else null
                                        }
                                        else -> null
                                    }
                                    autoTitle ?: (if (Loc.currentLanguage == "ar") "مهمة غير مسماة" else "Unnamed Task")
                                }
                                val finalTitle = Task.buildCombinedTitle(rawTitle, taskLabels.joinToString(","))
                                viewModel.editTask(
                                    taskId = editingTask.id,
                                    title = finalTitle,
                                    targetType = targetType,
                                    targetValue = targetValue,
                                    scheduledDays = scheduledDays.joinToString(","),
                                    reminderTime = reminderTime,
                                    startDate = editingTask.startDate,
                                    endDate = editingTask.endDate,
                                    sourceType = sourceType,
                                    sourceId = sourceId,
                                    manualTrackIds = selectedManualTrackIds.toList(),
                                    customThreshold = if (useCustomThreshold) taskThresholdValue.toInt() else null,
                                    labels = taskLabels.joinToString(","),
                                    dailyTargetValue = if (enableDailyGoal && dailyTargetValue > 0) dailyTargetValue else null
                                )
                                onDismiss()
                            },
                            modifier = Modifier.testTag("quick_save_btn")
                        ) {
                            Text(Loc.getText("update_task_btn"))
                        }
                    }

                    if (currentStep < 4) {
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                }
                            },
                            modifier = Modifier.testTag("step_next_btn")
                        ) {
                            Text(Loc.getText("next"))
                        }
                    } else {
                        Button(
                            onClick = {
                                val rawTitle = title.trim().ifEmpty {
                                    val autoTitle = when (sourceType) {
                                        "FOLDER" -> allFolders.find { it.id == sourceId }?.folderName
                                        "PLAYLIST" -> allPlaylists.find { it.id == sourceId }?.name
                                        "TRACKS" -> {
                                            if (selectedManualTrackIds.size == 1) {
                                                allTracks.find { it.id == selectedManualTrackIds.first() }?.getDisplayTitle()
                                            } else if (selectedManualTrackIds.size > 1) {
                                                Loc.getText("group_goal_task_title")
                                            } else null
                                        }
                                        else -> null
                                    }
                                    autoTitle ?: (if (Loc.currentLanguage == "ar") "مهمة غير مسماة" else "Unnamed Task")
                                }
                                val finalTitle = Task.buildCombinedTitle(rawTitle, taskLabels.joinToString(","))
                                if (editingTask != null) {
                                    viewModel.editTask(
                                        taskId = editingTask.id,
                                        title = finalTitle,
                                        targetType = targetType,
                                        targetValue = targetValue,
                                        scheduledDays = scheduledDays.joinToString(","),
                                        reminderTime = reminderTime,
                                        startDate = editingTask.startDate,
                                        endDate = editingTask.endDate,
                                        sourceType = sourceType,
                                        sourceId = sourceId,
                                        manualTrackIds = selectedManualTrackIds.toList(),
                                        customThreshold = if (useCustomThreshold) taskThresholdValue.toInt() else null,
                                        labels = taskLabels.joinToString(","),
                                        dailyTargetValue = if (enableDailyGoal && dailyTargetValue > 0) dailyTargetValue else null
                                    )
                                } else {
                                    viewModel.createTask(
                                        title = finalTitle,
                                        sourceType = sourceType,
                                        sourceId = sourceId,
                                        targetType = targetType,
                                        targetValue = targetValue,
                                        scheduledDays = scheduledDays.joinToString(","),
                                        reminderTime = reminderTime,
                                        startDate = System.currentTimeMillis(),
                                        endDate = null,
                                        manualTrackIds = selectedManualTrackIds.toList(),
                                        customThreshold = if (useCustomThreshold) taskThresholdValue.toInt() else null,
                                        labels = taskLabels.joinToString(","),
                                        dailyTargetValue = if (enableDailyGoal && dailyTargetValue > 0) dailyTargetValue else null
                                    )
                                }
                                onDismiss()
                            },
                            modifier = Modifier.testTag("step_save_btn")
                        ) {
                            Text(if (editingTask != null) Loc.getText("update_task_btn") else Loc.getText("save_task"))
                        }
                    }
                }
            }
        }
    }
}

// Note: StatsView, charts, and statistics data structures are located in StatsScreen.kt

// --- SUB-SCREEN 9: SETTINGS VIEW (Themes, Lang, sliders) ---
@Composable
fun SettingsView(viewModel: AppViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    var sliderValue by remember { mutableStateOf(viewModel.thresholdSetting.toFloat()) }
    var skipVal by remember { mutableStateOf(viewModel.skipSecondsSetting) }
    var chosenTheme by remember { mutableStateOf(viewModel.selectedTheme) }
    var chosenLang by remember { mutableStateOf(Loc.currentLanguage) }
    var headsetEnabled by remember { mutableStateOf(viewModel.headsetControlsEnabled) }
    var headsetAction by remember { mutableStateOf(viewModel.headsetMultiClickAction) }
    var segmentSource by remember { mutableStateOf(viewModel.segmentSourceSetting) }
    var pauseMultiplier by remember { mutableStateOf(viewModel.practicePauseMultiplierSetting) }
    var silenceSensitivity by remember { mutableStateOf(viewModel.silenceSensitivitySetting) }
    var silenceMinDuration by remember { mutableStateOf(viewModel.silenceMinDurationSetting) }
    var silencePadding by remember { mutableStateOf(viewModel.silencePaddingSetting) }
    var showWaveformDialogInSettings by remember { mutableStateOf(false) }
    val currentPlayingTrack by AudioPlayerManager.currentTrack.collectAsStateWithLifecycle()

    val playbackHistoryList by viewModel.playbackHistory.collectAsStateWithLifecycle()
    var showClearDialog by remember { mutableStateOf(false) }
    var selectedPruneOption by remember { mutableStateOf("ALL") } // "ALL", "LIMIT_1000", "OLDER_YEAR"
    var showFinalConfirm by remember { mutableStateOf(false) }

    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let {
            try {
                context.contentResolver.openOutputStream(it)?.use { outputStream ->
                    val success = viewModel.exportBackupToJson(outputStream)
                    if (success) {
                        Toast.makeText(context, Loc.getText("backup_success"), Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(context, Loc.getText("backup_failed"), Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, Loc.getText("backup_failed"), Toast.LENGTH_SHORT).show()
            }
        }
    }

    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { fileUri ->
            try {
                val jsonBytes = context.contentResolver.openInputStream(fileUri)?.use { stream ->
                    stream.readBytes()
                }
                if (jsonBytes != null && jsonBytes.isNotEmpty()) {
                    val offset = if (jsonBytes.size >= 3 &&
                        jsonBytes[0] == 0xEF.toByte() &&
                        jsonBytes[1] == 0xBB.toByte() &&
                        jsonBytes[2] == 0xBF.toByte()
                    ) 3 else 0
                    val jsonString = String(jsonBytes, offset, jsonBytes.size - offset, Charsets.UTF_8)
                    viewModel.restoreBackupFromJsonString(jsonString)
                } else {
                    Toast.makeText(context, Loc.getText("restore_failed"), Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, Loc.getText("restore_failed"), Toast.LENGTH_SHORT).show()
            }
        }
    }

    LaunchedEffect(chosenTheme, sliderValue, skipVal, chosenLang) {
        viewModel.updateSettings(chosenTheme, sliderValue.toInt(), skipVal, chosenLang)
    }

    LaunchedEffect(headsetEnabled, headsetAction) {
        viewModel.updateHeadsetSettings(headsetEnabled, headsetAction)
    }

    LaunchedEffect(segmentSource, pauseMultiplier) {
        viewModel.updatePracticeSettings(segmentSource, pauseMultiplier)
    }

    LaunchedEffect(silenceSensitivity, silenceMinDuration, silencePadding) {
        viewModel.updateSilenceSettings(silenceSensitivity, silenceMinDuration, silencePadding)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.testTag("btn_settings_back")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(Loc.getText("settings"), fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
        }

        // Threshold slider
        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(Loc.getText("completion_slider") + ": ${sliderValue.toInt()}%", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(2.dp))
                Slider(
                    value = sliderValue,
                    onValueChange = { sliderValue = it },
                    valueRange = 80f..100f,
                    steps = 20,
                    modifier = Modifier.testTag("threshold_slider")
                )
                Text(Loc.getText("eligible_threshold_note"), fontSize = 10.sp, color = Color.Gray)
            }
        }

        // skip durations
        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(Loc.getText("skip_duration_label"), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                    listOf(5, 10, 15, 30).forEach { s ->
                        val selected = skipVal == s
                        Box(
                            modifier = Modifier
                                .background(
                                    if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable { skipVal = s }
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text("${s}s", color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        // Headset & Media Controls Card
        Card(
            modifier = Modifier.fillMaxWidth().testTag("card_headset_controls"),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = Loc.getText("headset_controls_title"),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Switch(
                        checked = headsetEnabled,
                        onCheckedChange = { headsetEnabled = it },
                        modifier = Modifier.testTag("switch_headset_controls")
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = Loc.getText("headset_controls_desc"),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                if (headsetEnabled) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = Loc.getText("headset_single_click_hint"),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = Loc.getText("headset_double_click_hint"),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = Loc.getText("headset_triple_click_hint"),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "⚡ " + Loc.getText("headset_unplug_pause_hint"),
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = Loc.getText("headset_double_action_label"),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    listOf(
                        "NEXT_PREV" to Loc.getText("headset_action_track"),
                        "SKIP_SECONDS" to Loc.getText("headset_action_skip")
                    ).forEach { (actionKey, actionLabel) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { headsetAction = actionKey }
                                .padding(vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = headsetAction == actionKey,
                                onClick = { headsetAction = actionKey }
                            )
                            Text(
                                text = actionLabel,
                                modifier = Modifier.padding(start = 8.dp),
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }

        // Practice Mode & Segmentation Card
        Card(
            modifier = Modifier.fillMaxWidth().testTag("card_practice_settings"),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.RecordVoiceOver,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = Loc.getText("practice_mode"),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f))

                // Segment source selection
                Text(
                    text = Loc.getText("segment_source"),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = Loc.getText("segment_source_desc"),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                listOf(
                    "SILENCE" to Loc.getText("segment_source_silence"),
                    "SUBTITLES" to Loc.getText("segment_source_subtitles"),
                    "MANUAL" to Loc.getText("segment_source_manual")
                ).forEach { (sourceKey, sourceLabel) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { segmentSource = sourceKey }
                            .padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = segmentSource.equals(sourceKey, ignoreCase = true),
                            onClick = { segmentSource = sourceKey }
                        )
                        Text(
                            text = sourceLabel,
                            modifier = Modifier.padding(start = 8.dp),
                            fontSize = 12.sp
                        )
                    }
                }

                if (segmentSource.equals("MANUAL", ignoreCase = true)) {
                    val trackForWaveform = currentPlayingTrack
                    if (trackForWaveform != null) {
                        OutlinedButton(
                            onClick = { showWaveformDialogInSettings = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp)
                                .testTag("btn_open_waveform_editor_settings"),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Filled.GraphicEq, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(Loc.getText("open_waveform_editor"), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    } else {
                        Text(
                            text = Loc.getText("manual_segments_desc"),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f))

                // Pause length multiplier setting
                Text(
                    text = Loc.getText("pause_multiplier_title"),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = Loc.getText("pause_multiplier_desc"),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Stepper: Minus button, center chip holding value, Plus button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalIconButton(
                        onClick = {
                            val next = (Math.round((pauseMultiplier - 0.25f) * 4f) / 4f).coerceIn(0.25f, 4.0f)
                            pauseMultiplier = next
                        },
                        enabled = pauseMultiplier > 0.25f,
                        modifier = Modifier
                            .size(42.dp)
                            .testTag("btn_pause_multiplier_decrease")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Remove,
                            contentDescription = "Decrease multiplier",
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        tonalElevation = 2.dp,
                        modifier = Modifier
                            .clickable { pauseMultiplier = 1.0f }
                            .testTag("chip_pause_multiplier_value")
                    ) {
                        Box(
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = String.format(java.util.Locale.US, "%.2f", pauseMultiplier) + "x",
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    FilledTonalIconButton(
                        onClick = {
                            val next = (Math.round((pauseMultiplier + 0.25f) * 4f) / 4f).coerceIn(0.25f, 4.0f)
                            pauseMultiplier = next
                        },
                        enabled = pauseMultiplier < 4.0f,
                        modifier = Modifier
                            .size(42.dp)
                            .testTag("btn_pause_multiplier_increase")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = "Increase multiplier",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f))

                // Silence Analysis Tuning Section
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Tune,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = Loc.getText("silence_analysis_tuning"),
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = Loc.getText("silence_analysis_tuning_desc"),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // 1. Word Ending Safety Buffer (Padding)
                Text(
                    text = Loc.getText("silence_padding"),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = Loc.getText("silence_padding_desc"),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        100L to "100ms",
                        200L to "200ms ★",
                        300L to "300ms",
                        400L to "400ms"
                    ).forEach { (paddingMs, label) ->
                        val isSelected = silencePadding == paddingMs
                        FilterChip(
                            selected = isSelected,
                            onClick = { silencePadding = paddingMs },
                            label = {
                                Text(
                                    text = label,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    maxLines = 1
                                )
                            },
                            modifier = Modifier.weight(1f).testTag("chip_silence_padding_${paddingMs}")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                // 2. Minimum Pause Duration
                Text(
                    text = Loc.getText("silence_min_duration"),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = Loc.getText("silence_min_duration_desc"),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        350L to "350ms",
                        500L to "500ms ★",
                        750L to "750ms",
                        1000L to "1.0s"
                    ).forEach { (minMs, label) ->
                        val isSelected = silenceMinDuration == minMs
                        FilterChip(
                            selected = isSelected,
                            onClick = { silenceMinDuration = minMs },
                            label = {
                                Text(
                                    text = label,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    maxLines = 1
                                )
                            },
                            modifier = Modifier.weight(1f).testTag("chip_silence_duration_${minMs}")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                // 3. Detection Sensitivity
                Text(
                    text = Loc.getText("silence_sensitivity"),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = Loc.getText("silence_sensitivity_desc"),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                listOf(
                    "HIGH" to Loc.getText("silence_sensitivity_high"),
                    "MEDIUM" to Loc.getText("silence_sensitivity_medium") + " ★",
                    "LOW" to Loc.getText("silence_sensitivity_low")
                ).forEach { (sensKey, sensLabel) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { silenceSensitivity = sensKey }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = silenceSensitivity.equals(sensKey, ignoreCase = true),
                            onClick = { silenceSensitivity = sensKey }
                        )
                        Text(
                            text = sensLabel,
                            modifier = Modifier.padding(start = 8.dp),
                            fontSize = 12.sp
                        )
                    }
                }

                // 4. Re-analyze Current Track Button
                Spacer(modifier = Modifier.height(4.dp))
                Button(
                    onClick = {
                        Toast.makeText(context, Loc.getText("reanalyzing_with_new_settings"), Toast.LENGTH_SHORT).show()
                        viewModel.reanalyzeCurrentTrackPracticeSegments(context)
                    },
                    modifier = Modifier.fillMaxWidth().testTag("btn_reanalyze_current_track"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(Loc.getText("reanalyze_current_track"), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // App Theme Selector
        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(Loc.getText("theme"), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(4.dp))
                listOf("system" to Loc.getText("system"), "light" to Loc.getText("light"), "dark" to Loc.getText("dark")).forEach { (key, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { chosenTheme = key }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = chosenTheme == key, onClick = { chosenTheme = key })
                        Text(label, modifier = Modifier.padding(start = 8.dp), fontSize = 12.sp)
                    }
                }
            }
        }

        // Language Selector
        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(Loc.getText("app_language"), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(4.dp))
                listOf("ar" to Loc.getText("ar_label"), "en" to Loc.getText("en_label")).forEach { (key, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { chosenLang = key }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = chosenLang == key, onClick = { chosenLang = key })
                        Text(label, modifier = Modifier.padding(start = 8.dp), fontSize = 12.sp)
                    }
                }
            }
        }

        // Gemini AI Assistant Settings Card with Multiple API Keys Management
        var showAddEditDialog by remember { mutableStateOf(false) }
        var editingApiKey by remember { mutableStateOf<AppViewModel.SavedApiKey?>(null) }
        var dialogKeyName by remember { mutableStateOf("") }
        var dialogKeyValue by remember { mutableStateOf("") }
        var dialogKeySetAsActive by remember { mutableStateOf(true) }
        var dialogKeyPasswordVisible by remember { mutableStateOf(false) }
        var keyToDelete by remember { mutableStateOf<AppViewModel.SavedApiKey?>(null) }
        val clipboardManager = LocalClipboardManager.current

        fun maskApiKey(key: String): String {
            val trimmed = key.trim()
            if (trimmed.isBlank()) return "—"
            if (trimmed.length <= 10) return "••••••••"
            return "${trimmed.take(6)}••••${trimmed.takeLast(4)}"
        }

        Card(
            modifier = Modifier.fillMaxWidth().testTag("card_gemini_ai_settings"),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.primary,
                                        MaterialTheme.colorScheme.tertiary
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AutoAwesome,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = Loc.getText("gemini_ai_assistant"),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = Loc.getText("custom_gemini_api_key_multi_desc"),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Active Key Status Banner
                val activeSavedKey = viewModel.savedApiKeys.firstOrNull { it.id == viewModel.activeApiKeyId }
                if (activeSavedKey != null) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("active_api_key_banner")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF4CAF50))
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = Loc.getText("active_key_label") + ":",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = activeSavedKey.name,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = maskApiKey(activeSavedKey.key),
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Surface(
                                color = MaterialTheme.colorScheme.primary,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = Loc.getText("in_use"),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("no_active_api_key_banner")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = Loc.getText("no_api_keys_saved"),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // List of Saved Keys (with 1-tap switching)
                if (viewModel.savedApiKeys.isNotEmpty()) {
                    Text(
                        text = Loc.getText("saved_api_keys") + " (${viewModel.savedApiKeys.size})",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Column(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        viewModel.savedApiKeys.forEach { item ->
                            val isSelected = item.id == viewModel.activeApiKeyId
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                                        else MaterialTheme.colorScheme.surface,
                                border = BorderStroke(
                                    width = if (isSelected) 1.5.dp else 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        if (!isSelected) {
                                            viewModel.selectActiveApiKey(item.id)
                                            Toast.makeText(
                                                context,
                                                Loc.getFormattedText("key_switched_success", item.name),
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                    .testTag("saved_api_key_item_${item.id}")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = {
                                            if (!isSelected) {
                                                viewModel.selectActiveApiKey(item.id)
                                                Toast.makeText(
                                                    context,
                                                    Loc.getFormattedText("key_switched_success", item.name),
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        },
                                        modifier = Modifier.size(24.dp).testTag("radio_api_key_${item.id}")
                                    )

                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(start = 4.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = item.name,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                fontSize = 13.sp,
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (isSelected) {
                                                Surface(
                                                    color = MaterialTheme.colorScheme.primary,
                                                    shape = RoundedCornerShape(4.dp)
                                                ) {
                                                    Text(
                                                        text = Loc.getText("active_key_badge"),
                                                        color = MaterialTheme.colorScheme.onPrimary,
                                                        fontSize = 8.5.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                    )
                                                }
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = maskApiKey(item.key),
                                            fontSize = 11.sp,
                                            fontFamily = FontFamily.Monospace,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    IconButton(
                                        onClick = {
                                            editingApiKey = item
                                            dialogKeyName = item.name
                                            dialogKeyValue = item.key
                                            dialogKeySetAsActive = isSelected
                                            dialogKeyPasswordVisible = false
                                            showAddEditDialog = true
                                        },
                                        modifier = Modifier.size(32.dp).testTag("btn_edit_api_key_${item.id}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = Loc.getText("edit_api_key"),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }

                                    IconButton(
                                        onClick = {
                                            keyToDelete = item
                                        },
                                        modifier = Modifier.size(32.dp).testTag("btn_delete_api_key_${item.id}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.DeleteOutline,
                                            contentDescription = Loc.getText("delete_api_key"),
                                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.85f),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Add Key Button
                Button(
                    onClick = {
                        editingApiKey = null
                        dialogKeyName = "Key ${viewModel.savedApiKeys.size + 1}"
                        dialogKeyValue = ""
                        dialogKeySetAsActive = viewModel.savedApiKeys.isEmpty()
                        dialogKeyPasswordVisible = false
                        showAddEditDialog = true
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(38.dp)
                        .testTag("btn_add_new_api_key"),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = Loc.getText("add_api_key"),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // Add / Edit API Key Dialog
        if (showAddEditDialog) {
            AlertDialog(
                onDismissRequest = { showAddEditDialog = false },
                title = {
                    Text(
                        text = if (editingApiKey == null) Loc.getText("add_api_key") else Loc.getText("edit_api_key"),
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                },
                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = dialogKeyName,
                            onValueChange = { dialogKeyName = it },
                            label = { Text(Loc.getText("key_name_label"), fontSize = 12.sp) },
                            placeholder = { Text(Loc.getText("key_name_placeholder"), fontSize = 12.sp) },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("dialog_api_key_name_input")
                        )

                        OutlinedTextField(
                            value = dialogKeyValue,
                            onValueChange = { dialogKeyValue = it },
                            label = { Text(Loc.getText("api_key_value_label"), fontSize = 12.sp) },
                            placeholder = { Text(Loc.getText("api_key_value_placeholder"), fontSize = 12.sp) },
                            singleLine = true,
                            visualTransformation = if (dialogKeyPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = {
                                            val clipText = clipboardManager.getText()?.text
                                            if (!clipText.isNullOrBlank()) {
                                                dialogKeyValue = clipText.trim()
                                            }
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ContentPaste,
                                            contentDescription = "Paste",
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    IconButton(
                                        onClick = { dialogKeyPasswordVisible = !dialogKeyPasswordVisible },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (dialogKeyPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            contentDescription = if (dialogKeyPasswordVisible) "Hide key" else "Show key",
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("dialog_api_key_value_input")
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { dialogKeySetAsActive = !dialogKeySetAsActive }
                                .padding(vertical = 4.dp)
                        ) {
                            Checkbox(
                                checked = dialogKeySetAsActive,
                                onCheckedChange = { dialogKeySetAsActive = it }
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = Loc.getText("set_as_active_key"),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (dialogKeyValue.isBlank()) {
                                Toast.makeText(context, Loc.getText("api_key_cannot_be_empty"), Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            if (editingApiKey == null) {
                                viewModel.addSavedApiKey(dialogKeyName, dialogKeyValue, dialogKeySetAsActive)
                                Toast.makeText(context, Loc.getText("key_added_success"), Toast.LENGTH_SHORT).show()
                            } else {
                                viewModel.updateSavedApiKey(editingApiKey!!.id, dialogKeyName, dialogKeyValue)
                                if (dialogKeySetAsActive) {
                                    viewModel.selectActiveApiKey(editingApiKey!!.id)
                                }
                                Toast.makeText(context, Loc.getText("key_updated_success"), Toast.LENGTH_SHORT).show()
                            }
                            showAddEditDialog = false
                        },
                        enabled = dialogKeyValue.isNotBlank()
                    ) {
                        Text(Loc.getText("save"))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAddEditDialog = false }) {
                        Text(Loc.getText("cancel"))
                    }
                }
            )
        }

        // Delete Confirmation Dialog
        if (keyToDelete != null) {
            AlertDialog(
                onDismissRequest = { keyToDelete = null },
                title = {
                    Text(
                        text = Loc.getText("delete_api_key"),
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                },
                text = {
                    Text(
                        text = Loc.getFormattedText("delete_api_key_confirm", keyToDelete!!.name),
                        fontSize = 13.sp
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val target = keyToDelete
                            if (target != null) {
                                viewModel.deleteSavedApiKey(target.id)
                                Toast.makeText(context, Loc.getText("key_deleted_success"), Toast.LENGTH_SHORT).show()
                            }
                            keyToDelete = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text(Loc.getText("delete"), color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { keyToDelete = null }) {
                        Text(Loc.getText("cancel"))
                    }
                }
            )
        }

        // Clear/Prune History button
        Card(
            modifier = Modifier.fillMaxWidth().testTag("card_clear_history"),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(Loc.getText("clear_history_btn"), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                Spacer(modifier = Modifier.height(4.dp))
                Text(Loc.getText("clear_history_dialog_desc"), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { showClearDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.align(Alignment.End).height(38.dp)
                ) {
                    Text(Loc.getText("clear_history_btn").replace("🧹 ", ""), fontSize = 12.sp, color = Color.White)
                }
            }
        }

        // Backup & Restore Card
        Card(
            modifier = Modifier.fillMaxWidth().testTag("card_backup_restore"),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(Loc.getText("backup_restore_title"), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                Spacer(modifier = Modifier.height(4.dp))
                Text(Loc.getText("backup_restore_desc"), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                            createDocumentLauncher.launch("hearmark_backup_$timeStamp.json")
                        },
                        modifier = Modifier.weight(1f).height(38.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("📤 " + Loc.getText("export_backup_json"), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    OutlinedButton(
                        onClick = {
                            openDocumentLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                        },
                        modifier = Modifier.weight(1f).height(38.dp)
                    ) {
                        Text("📥 " + Loc.getText("restore_backup_json"), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))

                Text(Loc.getText("auto_backup_title"), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                Spacer(modifier = Modifier.height(2.dp))
                Text(Loc.getText("auto_backup_desc"), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                val lastAutoBackupTime = remember { viewModel.getAutoBackupLastModified() }
                if (lastAutoBackupTime != null) {
                    val dateFormatted = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(lastAutoBackupTime))
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("ℹ️ " + Loc.getText("latest_auto_snapshot_label") + " $dateFormatted", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
                }

                Spacer(modifier = Modifier.height(8.dp))
                FilledTonalButton(
                    onClick = {
                        viewModel.restoreLatestAutoBackup { success, msg ->
                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(38.dp)
                ) {
                    Text(Loc.getText("restore_auto_snapshot_btn"), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }

        if (showClearDialog) {
            AlertDialog(
                onDismissRequest = { 
                    showClearDialog = false 
                    showFinalConfirm = false
                },
                title = {
                    Text(
                        text = if (showFinalConfirm) Loc.getText("clear_history_confirm_title") else Loc.getText("clear_history_dialog_title"),
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (!showFinalConfirm) {
                            Text(
                                text = Loc.getText("clear_history_dialog_desc"),
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            
                            // Export button
                            Button(
                                onClick = { 
                                    viewModel.copyHistoryClipboard(context, playbackHistoryList)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                modifier = Modifier.fillMaxWidth().height(42.dp)
                            ) {
                                Text("📋 " + Loc.getText("export_data"), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                            
                            HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))
                            
                            // Radio option buttons
                            listOf(
                                "ALL" to Loc.getText("clear_history_option_all"),
                                "LIMIT_1000" to Loc.getText("clear_history_option_limit_1000"),
                                "OLDER_YEAR" to Loc.getText("clear_history_option_older_year")
                            ).forEach { (optionKey, optionLabel) ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { selectedPruneOption = optionKey }
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = selectedPruneOption == optionKey,
                                        onClick = { selectedPruneOption = optionKey }
                                    )
                                    Text(
                                        text = optionLabel,
                                        modifier = Modifier.padding(start = 8.dp),
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        } else {
                            // Show final confirmation dialog text
                            Text(
                                text = Loc.getText("clear_history_confirm_desc"),
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            // Summary of what is chosen
                            val summaryText = when (selectedPruneOption) {
                                "ALL" -> Loc.getText("clear_history_option_all")
                                "LIMIT_1000" -> Loc.getText("clear_history_option_limit_1000")
                                else -> Loc.getText("clear_history_option_older_year")
                            }
                            Text(
                                text = "👉 $summaryText",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (!showFinalConfirm) {
                                showFinalConfirm = true
                            } else {
                                // Perform delete action
                                when (selectedPruneOption) {
                                    "ALL" -> viewModel.clearAllPlaybackHistory()
                                    "LIMIT_1000" -> viewModel.prunePlaybackHistoryToRecent1000()
                                    "OLDER_YEAR" -> viewModel.prunePlaybackHistoryOlderThanOneYear()
                                }
                                Toast.makeText(context, Loc.getText("clear_history_success"), Toast.LENGTH_LONG).show()
                                showClearDialog = false
                                showFinalConfirm = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (showFinalConfirm) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text(
                            text = if (showFinalConfirm) Loc.getText("delete_completely") else Loc.getText("next"),
                            color = Color.White
                        )
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            if (showFinalConfirm) {
                                showFinalConfirm = false
                            } else {
                                showClearDialog = false
                            }
                        }
                    ) {
                        Text(text = if (showFinalConfirm) Loc.getText("prev") else Loc.getText("cancel"))
                    }
                }
            )
        }

        if (showWaveformDialogInSettings) {
            val trackForWaveform = currentPlayingTrack
            if (trackForWaveform != null) {
                WaveformSegmentEditorDialog(
                    track = trackForWaveform,
                    viewModel = viewModel,
                    onDismiss = { showWaveformDialogInSettings = false }
                )
            }
        }
    }
}

// --- UTILITY FORMATS FOR PLAYBACK SPEED & DURATION ---
fun formatPlaybackSpeed(speed: Float): String {
    val rounded = (Math.round(speed * 100f) / 100f)
    return when {
        Math.abs(rounded - rounded.toInt()) < 0.001f -> "${rounded.toInt()}x"
        Math.abs(rounded * 10f - (rounded * 10f).toInt()) < 0.001f -> String.format(java.util.Locale.US, "%.1fx", rounded)
        else -> String.format(java.util.Locale.US, "%.2fx", rounded)
    }
}

// --- UTILITY FORMATS FOR MILLISECONDS DURATION ---
fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
}

@Composable
fun PlaylistDetailsView(
    playlistId: Long,
    viewModel: AppViewModel,
    backPressed: () -> Unit,
    onCreateTaskForPlaylist: (String, Long) -> Unit,
    onShowAssociatedTasks: (String, Long, String) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val allPlaylists by viewModel.playlists.collectAsStateWithLifecycle()
    val playlist = allPlaylists.find { it.id == playlistId }
    val playlistName = playlist?.name ?: ""

    val tracksFlow = remember(playlistId) { viewModel.repository.getTracksForPlaylistFlow(playlistId) }
    val rawTracks by tracksFlow.collectAsStateWithLifecycle(emptyList())

    var playlistSortBy by remember { mutableStateOf("name") }
    var playlistIsAscending by remember { mutableStateOf(true) }

    val tracks = remember(rawTracks, playlistSortBy, playlistIsAscending) {
        getSortedTracks(rawTracks, playlistSortBy, playlistIsAscending)
    }

    val allTracks by viewModel.tracks.collectAsStateWithLifecycle()

    var showAddTracksDialog by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showAddToPlaylistDialogForTrack by remember { mutableStateOf<AudioTrack?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Top Action Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = backPressed) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
            }
            Box {
                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = "More Options",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        modifier = Modifier.size(20.dp)
                    )
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text = { Text(Loc.getText("play_next")) },
                        onClick = {
                            showMenu = false
                            viewModel.addTracksToPlayNext(tracks)
                            Toast.makeText(context, Loc.getText("added_to_queue"), Toast.LENGTH_SHORT).show()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(Loc.getText("choose_files")) },
                        onClick = {
                            showMenu = false
                            showAddTracksDialog = true
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(Loc.getText("create_task")) },
                        onClick = {
                            showMenu = false
                            onCreateTaskForPlaylist("PLAYLIST", playlistId)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(Loc.getText("show_associated_tasks")) },
                        onClick = {
                            showMenu = false
                            onShowAssociatedTasks("PLAYLIST", playlistId, playlistName)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(Loc.getText("delete_history"), color = MaterialTheme.colorScheme.error) },
                        onClick = {
                            showMenu = false
                            viewModel.deletePlaylist(playlistId, false)
                            backPressed()
                        }
                    )
                }
            }
        }

        // Playlist Name in separate row with generous space and prominent typography
        Text(
            text = playlistName,
            fontSize = 24.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp)
        )

        if (tracks.isEmpty()) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlaylistPlay,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                    )
                    Text(
                        text = Loc.getText("empty_playlists_desc"),
                        color = Color.Gray,
                        textAlign = TextAlign.Center,
                        fontSize = 14.sp
                    )
                    Button(onClick = { showAddTracksDialog = true }) {
                        Text(Loc.getText("choose_files"))
                    }
                }
            }
        } else {
            TrackListSortHeader(
                totalCount = tracks.size,
                sortBy = playlistSortBy,
                onSortByChange = { playlistSortBy = it },
                isAscending = playlistIsAscending,
                onIsAscendingChange = { playlistIsAscending = it }
            )
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(tracks) { track ->
                    val isCurrentExecuting = AudioPlayerManager.currentTrack.collectAsStateWithLifecycle().value?.id == track.id
                    val isPlaying = AudioPlayerManager.isPlaying.collectAsStateWithLifecycle().value

                    UnifiedAudioTrackRow(
                        track = track,
                        isCurrentExecuting = isCurrentExecuting,
                        isPlaying = isPlaying,
                        viewModel = viewModel,
                        playlistId = playlistId,
                        onTrackPlaylistMenuClicked = { showAddToPlaylistDialogForTrack = it },
                        onCreateTask = { type, id -> onCreateTaskForPlaylist(type, id) },
                        onShowAssociatedTasks = { type, id, name -> onShowAssociatedTasks(type, id, name) },
                        playlistTracks = tracks
                    )
                }
            }
        }
    }

    // Tracks Selection Dialog for adding to Playlist
    if (showAddTracksDialog) {
        val attachedTrackIds = tracks.map { it.id }.toSet()
        val eligibleTracks = allTracks.filter { it.id !in attachedTrackIds }
        var selectedIds by remember { mutableStateOf(setOf<Long>()) }

        AlertDialog(
            onDismissRequest = { showAddTracksDialog = false },
            title = { Text(Loc.getText("select_tracks_dialog_title")) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (eligibleTracks.isEmpty()) {
                        Text(
                            text = Loc.getText("no_tracks_to_add"),
                            fontSize = 14.sp,
                            color = Color.Gray,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.heightIn(max = 280.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(eligibleTracks) { track ->
                                val isChecked = selectedIds.contains(track.id)
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedIds = if (isChecked) {
                                                selectedIds - track.id
                                            } else {
                                                selectedIds + track.id
                                            }
                                        },
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isChecked) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Checkbox(
                                            checked = isChecked,
                                            onCheckedChange = { checked ->
                                                selectedIds = if (checked == true) {
                                                    selectedIds + track.id
                                                } else {
                                                    selectedIds - track.id
                                                }
                                            }
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = middleEllipse(track.getDisplayTitle(), maxLength = 26),
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 13.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            val parentFolderName = if (track.parentFolderId != null) "📁 " + Loc.getText("library") else "🎵 " + Loc.getText("independent_track")
                                            Text(
                                                text = "$parentFolderName | ${formatDuration(track.duration)} | ${track.getProgressPercent()}%",
                                                fontSize = 11.sp,
                                                color = Color.Gray
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (selectedIds.isNotEmpty()) {
                            viewModel.addTracksToPlaylist(playlistId, selectedIds.toList())
                        }
                        showAddTracksDialog = false
                    },
                    enabled = selectedIds.isNotEmpty()
                ) {
                    Text(Loc.getText("add_selected"))
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddTracksDialog = false }) {
                    Text(Loc.getText("cancel"))
                }
            }
        )
    }

    if (showAddToPlaylistDialogForTrack != null) {
        AddToPlaylistDialog(
            track = showAddToPlaylistDialogForTrack!!,
            playlists = allPlaylists,
            onDismiss = { showAddToPlaylistDialogForTrack = null },
            onPlaylistSelected = { playlist ->
                viewModel.addTrackToPlaylist(playlist.id, showAddToPlaylistDialogForTrack!!.id)
                Toast.makeText(context, String.format(Loc.getText("added_to_playlist_success"), playlist.name), Toast.LENGTH_SHORT).show()
            },
            onCreatePlaylistClicked = {}
        )
    }
}

@Composable
fun AddToPlaylistDialog(
    track: AudioTrack,
    playlists: List<Playlist>,
    onDismiss: () -> Unit,
    onPlaylistSelected: (Playlist) -> Unit,
    onCreatePlaylistClicked: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(Loc.getText("choose_playlist_dialog_title")) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (playlists.isEmpty()) {
                    Text(
                        text = Loc.getText("empty_playlists_desc"),
                        fontSize = 14.sp,
                        color = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = {
                            onDismiss()
                            onCreatePlaylistClicked()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(Loc.getText("add_playlist"))
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 300.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(playlists) { playlist ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onPlaylistSelected(playlist)
                                        onDismiss()
                                    },
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.PlaylistPlay,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = playlist.name,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(Loc.getText("cancel"))
            }
        }
    )
}

fun formatScheduledDays(daysString: String): String {
    val days = daysString.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    if (days.isEmpty()) return ""
    
    // Check if contains all 7 days of the week
    val weekDays = setOf("SUNDAY", "MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY")
    val upperDays = days.map { it.uppercase() }.toSet()
    if (upperDays.containsAll(weekDays) || days.size >= 7) {
        return Loc.getText("all_days")
    }
    
    val dayTranslations = mapOf(
        "SUNDAY" to mapOf("en" to "Sun", "ar" to "أحد"),
        "MONDAY" to mapOf("en" to "Mon", "ar" to "اثنين"),
        "TUESDAY" to mapOf("en" to "Tue", "ar" to "ثلاث"),
        "WEDNESDAY" to mapOf("en" to "Wed", "ar" to "أربع"),
        "THURSDAY" to mapOf("en" to "Thu", "ar" to "خميس"),
        "FRIDAY" to mapOf("en" to "Fri", "ar" to "جمعة"),
        "SATURDAY" to mapOf("en" to "Sat", "ar" to "سبت")
    )
    val localizedDays = days.map { dayName ->
        val trans = dayTranslations[dayName.uppercase()]
        if (trans != null) {
            trans[Loc.currentLanguage] ?: trans["en"] ?: dayName
        } else {
            dayName
        }
    }
    return localizedDays.joinToString(", ")
}

@Composable
fun AssociatedTasksDialog(
    itemType: String, // "TRACKS", "FOLDER", "PLAYLIST"
    itemId: Long,
    itemName: String,
    viewModel: AppViewModel,
    onDismiss: () -> Unit,
    onViewTaskDetails: (Task) -> Unit,
    onCreateTask: () -> Unit
) {
    val allTasks by viewModel.allTasks.collectAsStateWithLifecycle()
    var trackTasks by remember { mutableStateOf<List<Task>>(emptyList()) }
    
    LaunchedEffect(itemId, allTasks) {
        if (itemType == "TRACKS") {
            trackTasks = viewModel.getAllTasksForTrack(itemId)
        }
    }
    
    val itemTasks = if (itemType == "TRACKS") {
        trackTasks
    } else {
        remember(allTasks) {
            allTasks.filter { it.sourceType == itemType && it.sourceId == itemId }
        }
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "${Loc.getText("associated_tasks_title")}: $itemName",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (itemTasks.isEmpty()) {
                    Text(
                        text = Loc.getText("no_associated_tasks"),
                        fontSize = 14.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            onDismiss()
                            onCreateTask()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(Loc.getText("create_task"))
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 280.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        items(itemTasks) { task ->
                            val progresses by viewModel.repository.getProgressForTaskFlow(task.id).collectAsStateWithLifecycle(emptyList())
                            val overallPercent = if (progresses.isNotEmpty()) {
                                var totalCompleted = 0
                                var totalRequired = 0
                                val targetVal = task.targetValue
                                if (task.targetType == "PLAY_COUNT") {
                                    progresses.forEach { p ->
                                        totalCompleted += minOf(p.completedPlayCount, targetVal)
                                        totalRequired += targetVal
                                    }
                                } else {
                                    progresses.forEach { p ->
                                        totalCompleted += minOf(p.getDaysList().size, targetVal)
                                        totalRequired += targetVal
                                    }
                                }
                                val isAllTracksDone = progresses.isNotEmpty() && progresses.all { it.isTrackCompleted }
                                if (isAllTracksDone) 1.0f else if (totalRequired > 0) totalCompleted.toFloat() / totalRequired.toFloat() else 0f
                            } else {
                                0f
                            }
                            
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onDismiss()
                                        onViewTaskDetails(task)
                                    },
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = if (task.isCompleted) Icons.Filled.CheckCircle else Icons.Filled.PendingActions,
                                            contentDescription = "State",
                                            tint = if (task.isCompleted) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(19.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(task.getDisplayTitle(), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                            Text(
                                                text = formatScheduledDays(task.scheduledDays),
                                                fontSize = 11.sp,
                                                color = Color.Gray,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Column(horizontalAlignment = Alignment.End) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = "${(overallPercent * 100).toInt()}%",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.sp,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Icon(
                                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                                    contentDescription = "Navigate to Task Details",
                                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    LinearProgressIndicator(
                                        progress = { overallPercent },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(4.dp)
                                            .clip(CircleShape),
                                        color = if (task.isCompleted) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary,
                                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(Loc.getText("cancel"))
            }
        }
    )
}

fun formatTimestampMs(ms: Long): String {
    val totalSeconds = (ms.coerceAtLeast(0L) / 1000)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}

fun parseTimestampToMs(input: String): Long? {
    val rawParts = input.trim().split(":")
    val parts = rawParts.map { it.trim().toLongOrNull() }
    if (parts.any { it == null || it < 0L }) return null
    val nonNullParts = parts.filterNotNull()
    return when (nonNullParts.size) {
        1 -> nonNullParts[0] * 1000L
        2 -> (nonNullParts[0] * 60L + nonNullParts[1]) * 1000L
        3 -> (nonNullParts[0] * 3600L + nonNullParts[1] * 60L + nonNullParts[2]) * 1000L
        else -> null
    }
}

@Composable
fun EditVirtualSceneDialog(
    track: AudioTrack,
    viewModel: AppViewModel,
    onDismiss: () -> Unit
) {
    var titleText by remember { mutableStateOf(track.fileName) }
    var startMs by remember { mutableLongStateOf(track.startOffsetMs) }
    var endMs by remember { mutableLongStateOf(track.endOffsetMs ?: (track.startOffsetMs + track.duration)) }
    var isPreviewPlaying by remember { mutableStateOf(false) }
    var startInputText by remember { mutableStateOf(formatTimestampMs(track.startOffsetMs)) }
    var endInputText by remember { mutableStateOf(formatTimestampMs(track.endOffsetMs ?: (track.startOffsetMs + track.duration))) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    DisposableEffect(Unit) {
        onDispose {
            NoteAudioPlayer.stop()
        }
    }

    AlertDialog(
        onDismissRequest = {
            NoteAudioPlayer.stop()
            onDismiss()
        },
        icon = {
            Icon(
                imageVector = Icons.Filled.MovieCreation,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(30.dp)
            )
        },
        title = {
            Text(
                text = Loc.getText("edit_virtual_track"),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Scene Title Field
                OutlinedTextField(
                    value = titleText,
                    onValueChange = { titleText = it },
                    label = { Text(Loc.getText("scene_title_label")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Start Time Field & Controls
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = Loc.getText("scene_start"),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedTextField(
                            value = startInputText,
                            onValueChange = { input ->
                                startInputText = input
                                parseTimestampToMs(input)?.let { parsed ->
                                    startMs = parsed
                                    errorMessage = if (endMs <= startMs) Loc.getText("scene_time_invalid") else null
                                }
                            },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            leadingIcon = {
                                Icon(Icons.Default.AccessTime, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                        )
                        FilledTonalIconButton(
                            onClick = {
                                val next = (startMs - 1000L).coerceAtLeast(0L)
                                startMs = next
                                startInputText = formatTimestampMs(next)
                                errorMessage = if (endMs <= startMs) Loc.getText("scene_time_invalid") else null
                                if (isPreviewPlaying) {
                                    NoteAudioPlayer.playSnippet(context, track.filePath, -track.id, next, endMs)
                                }
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Text("-1s", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp))
                        }
                        FilledTonalIconButton(
                            onClick = {
                                val next = startMs + 1000L
                                startMs = next
                                startInputText = formatTimestampMs(next)
                                errorMessage = if (endMs <= startMs) Loc.getText("scene_time_invalid") else null
                                if (isPreviewPlaying) {
                                    NoteAudioPlayer.playSnippet(context, track.filePath, -track.id, next, endMs)
                                }
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Text("+1s", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp))
                        }
                    }
                }

                // End Time Field & Controls
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = Loc.getText("scene_end"),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedTextField(
                            value = endInputText,
                            onValueChange = { input ->
                                endInputText = input
                                parseTimestampToMs(input)?.let { parsed ->
                                    endMs = parsed
                                    errorMessage = if (endMs <= startMs) Loc.getText("scene_time_invalid") else null
                                    if (isPreviewPlaying) {
                                        NoteAudioPlayer.playSnippet(context, track.filePath, -track.id, startMs, parsed)
                                    }
                                }
                            },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            leadingIcon = {
                                Icon(Icons.Default.AccessTime, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                        )
                        FilledTonalIconButton(
                            onClick = {
                                val next = (endMs - 1000L).coerceAtLeast(startMs + 1000L)
                                endMs = next
                                endInputText = formatTimestampMs(next)
                                errorMessage = if (endMs <= startMs) Loc.getText("scene_time_invalid") else null
                                if (isPreviewPlaying) {
                                    NoteAudioPlayer.playSnippet(context, track.filePath, -track.id, startMs, next)
                                }
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Text("-1s", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp))
                        }
                        FilledTonalIconButton(
                            onClick = {
                                val next = endMs + 1000L
                                endMs = next
                                endInputText = formatTimestampMs(next)
                                errorMessage = if (endMs <= startMs) Loc.getText("scene_time_invalid") else null
                                if (isPreviewPlaying) {
                                    NoteAudioPlayer.playSnippet(context, track.filePath, -track.id, startMs, next)
                                }
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Text("+1s", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp))
                        }
                    }
                }

                // Duration display & Preview snippet
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            val durationCalc = maxOf(0L, endMs - startMs)
                            Text(
                                text = String.format(Loc.getText("scene_duration_label"), formatDuration(durationCalc)),
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                text = "${formatTimestampMs(startMs)} -> ${formatTimestampMs(endMs)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.75f)
                            )
                        }

                        Button(
                            onClick = {
                                if (isPreviewPlaying) {
                                    NoteAudioPlayer.stop()
                                    isPreviewPlaying = false
                                } else {
                                    NoteAudioPlayer.playSnippet(
                                        context = context,
                                        trackFilePath = track.filePath,
                                        noteId = -track.id,
                                        startMs = startMs,
                                        endMs = endMs
                                    )
                                    isPreviewPlaying = true
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isPreviewPlaying) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary
                            ),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = if (isPreviewPlaying) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isPreviewPlaying) Loc.getText("stop_preview") else Loc.getText("preview_scene"),
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (endMs <= startMs) {
                        errorMessage = Loc.getText("scene_time_invalid")
                        return@Button
                    }
                    NoteAudioPlayer.stop()
                    viewModel.updateVirtualScene(
                        track = track,
                        newTitle = titleText,
                        newStartOffsetMs = startMs,
                        newEndOffsetMs = endMs,
                        onSuccess = onDismiss
                    )
                },
                enabled = endMs > startMs && titleText.isNotBlank()
            ) {
                Text(Loc.getText("save_scene_changes"))
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = {
                        NoteAudioPlayer.stop()
                        viewModel.deleteVirtualScene(track, onSuccess = onDismiss)
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(Loc.getText("delete_scene_btn"))
                }
                TextButton(
                    onClick = {
                        NoteAudioPlayer.stop()
                        onDismiss()
                    }
                ) {
                    Text(Loc.getText("cancel"))
                }
            }
        }
    )
}

@Composable
fun AddNewVirtualSceneDialog(
    parentTrack: AudioTrack,
    viewModel: AppViewModel,
    folderId: Long?,
    onDismiss: () -> Unit
) {
    var titleText by remember { mutableStateOf("") }
    var startMs by remember { mutableLongStateOf(0L) }
    var endMs by remember { mutableLongStateOf(minOf(60000L, parentTrack.duration)) }
    var isPreviewPlaying by remember { mutableStateOf(false) }
    var startInputText by remember { mutableStateOf(formatTimestampMs(0L)) }
    var endInputText by remember { mutableStateOf(formatTimestampMs(minOf(60000L, parentTrack.duration))) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    DisposableEffect(Unit) {
        onDispose {
            NoteAudioPlayer.stop()
        }
    }

    AlertDialog(
        onDismissRequest = {
            NoteAudioPlayer.stop()
            onDismiss()
        },
        icon = {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(30.dp)
            )
        },
        title = {
            Text(
                text = Loc.getText("create_scene_manual"),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                OutlinedTextField(
                    value = titleText,
                    onValueChange = { titleText = it },
                    label = { Text(Loc.getText("scene_title_label")) },
                    placeholder = { Text(Loc.getText("scene_intro_placeholder")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Start Time
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = Loc.getText("scene_start"),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedTextField(
                            value = startInputText,
                            onValueChange = { input ->
                                startInputText = input
                                parseTimestampToMs(input)?.let { parsed ->
                                    startMs = parsed
                                    errorMessage = if (endMs <= startMs) Loc.getText("scene_time_invalid") else null
                                }
                            },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            leadingIcon = {
                                Icon(Icons.Default.AccessTime, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                        )
                        FilledTonalIconButton(
                            onClick = {
                                val next = (startMs - 1000L).coerceAtLeast(0L)
                                startMs = next
                                startInputText = formatTimestampMs(next)
                                errorMessage = if (endMs <= startMs) Loc.getText("scene_time_invalid") else null
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Text("-1s", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp))
                        }
                        FilledTonalIconButton(
                            onClick = {
                                val next = startMs + 1000L
                                startMs = next
                                startInputText = formatTimestampMs(next)
                                errorMessage = if (endMs <= startMs) Loc.getText("scene_time_invalid") else null
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Text("+1s", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp))
                        }
                    }
                }

                // End Time
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = Loc.getText("scene_end"),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedTextField(
                            value = endInputText,
                            onValueChange = { input ->
                                endInputText = input
                                parseTimestampToMs(input)?.let { parsed ->
                                    endMs = parsed
                                    errorMessage = if (endMs <= startMs) Loc.getText("scene_time_invalid") else null
                                }
                            },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            leadingIcon = {
                                Icon(Icons.Default.AccessTime, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                        )
                        FilledTonalIconButton(
                            onClick = {
                                val next = (endMs - 1000L).coerceAtLeast(startMs + 1000L)
                                endMs = next
                                endInputText = formatTimestampMs(next)
                                errorMessage = if (endMs <= startMs) Loc.getText("scene_time_invalid") else null
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Text("-1s", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp))
                        }
                        FilledTonalIconButton(
                            onClick = {
                                val next = endMs + 1000L
                                endMs = next
                                endInputText = formatTimestampMs(next)
                                errorMessage = if (endMs <= startMs) Loc.getText("scene_time_invalid") else null
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Text("+1s", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp))
                        }
                    }
                }

                // Duration & Preview
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            val durationCalc = maxOf(0L, endMs - startMs)
                            Text(
                                text = String.format(Loc.getText("scene_duration_label"), formatDuration(durationCalc)),
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "${formatTimestampMs(startMs)} -> ${formatTimestampMs(endMs)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                            )
                        }

                        Button(
                            onClick = {
                                if (isPreviewPlaying) {
                                    NoteAudioPlayer.stop()
                                    isPreviewPlaying = false
                                } else {
                                    NoteAudioPlayer.playSnippet(
                                        context = context,
                                        trackFilePath = parentTrack.filePath,
                                        noteId = -parentTrack.id,
                                        startMs = startMs,
                                        endMs = endMs
                                    )
                                    isPreviewPlaying = true
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isPreviewPlaying) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                            ),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = if (isPreviewPlaying) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isPreviewPlaying) Loc.getText("stop_preview") else Loc.getText("preview_scene"),
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (endMs <= startMs) {
                        errorMessage = Loc.getText("scene_time_invalid")
                        return@Button
                    }
                    NoteAudioPlayer.stop()
                    viewModel.addNewVirtualScene(
                        parentTrack = parentTrack,
                        folderId = folderId,
                        title = titleText,
                        startOffsetMs = startMs,
                        endOffsetMs = endMs,
                        onSuccess = onDismiss
                    )
                },
                enabled = endMs > startMs && titleText.isNotBlank()
            ) {
                Text(Loc.getText("save_scene_changes"))
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    NoteAudioPlayer.stop()
                    onDismiss()
                }
            ) {
                Text(Loc.getText("cancel"))
            }
        }
    )
}

@Composable
fun ReviewScenesDialog(
    parentTrack: AudioTrack,
    viewModel: AppViewModel,
    onDismiss: () -> Unit,
    onPlayScene: (AudioTrack) -> Unit
) {
    val scenesFlow = remember(parentTrack.id) { viewModel.getScenesForTrackFlow(parentTrack.id) }
    val scenes by scenesFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    var sceneToEdit by remember { mutableStateOf<AudioTrack?>(null) }
    var isAddingScene by remember { mutableStateOf(false) }

    if (sceneToEdit != null) {
        EditVirtualSceneDialog(
            track = sceneToEdit!!,
            viewModel = viewModel,
            onDismiss = { sceneToEdit = null }
        )
    }

    if (isAddingScene) {
        val targetFolderId = scenes.firstOrNull()?.parentFolderId ?: parentTrack.parentFolderId
        AddNewVirtualSceneDialog(
            parentTrack = parentTrack,
            viewModel = viewModel,
            folderId = targetFolderId,
            onDismiss = { isAddingScene = false }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.MovieCreation,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column {
                        Text(
                            text = Loc.getText("review_scenes_title"),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = parentTrack.fileName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                IconButton(
                    onClick = { isAddingScene = true },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = Loc.getText("add_scene_btn"),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        text = {
            if (scenes.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        text = Loc.getText("no_scenes_found"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Button(
                        onClick = {
                            onDismiss()
                            viewModel.openAiHub(
                                function = AiFunctionType.SCENES,
                                track = parentTrack
                            )
                        },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(Loc.getText("detect_scenes_btn"))
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(scenes, key = { it.id }) { scene ->
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Scene number badge
                                val badgeText = String.format(Locale.US, "#%02d", scene.sceneNumber ?: 0)
                                Surface(
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.padding(end = 10.dp)
                                ) {
                                    Text(
                                        text = badgeText,
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = scene.fileName,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = "${formatTimestampMs(scene.startOffsetMs)} - ${formatTimestampMs(scene.endOffsetMs ?: (scene.startOffsetMs + scene.duration))}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                        )
                                        Text(
                                            text = "•",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                        )
                                        Text(
                                            text = formatDuration(scene.duration),
                                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }

                                // Actions: Play & Edit
                                IconButton(
                                    onClick = {
                                        onDismiss()
                                        onPlayScene(scene)
                                    },
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.PlayArrow,
                                        contentDescription = "Play",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                IconButton(
                                    onClick = { sceneToEdit = scene },
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Edit,
                                        contentDescription = "Edit",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(Loc.getText("close_dialog"))
            }
        }
    )
}

@Composable
fun UnifiedTrackDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    track: AudioTrack,
    viewModel: AppViewModel,
    playlistId: Long? = null,
    taskId: Long? = null,
    onCreateTask: () -> Unit = {},
    onShowAssociatedTasks: () -> Unit = {},
    onAddToPlaylist: () -> Unit = {},
    onPlay: () -> Unit = {},
    onViewInfo: () -> Unit = {},
    onEditScene: () -> Unit = {},
    onReviewScenes: () -> Unit = {}
) {
    val context = LocalContext.current
    DropdownMenu(expanded = expanded, onDismissRequest = onDismissRequest) {
        DropdownMenuItem(
            text = { Text(Loc.getText("play")) },
            onClick = {
                onDismissRequest()
                onPlay()
            }
        )
        DropdownMenuItem(
            text = { Text(Loc.getText("play_next")) },
            onClick = {
                onDismissRequest()
                viewModel.addTrackToPlayNext(track)
                Toast.makeText(context, Loc.getText("added_to_queue"), Toast.LENGTH_SHORT).show()
            }
        )
        DropdownMenuItem(
            text = { Text(Loc.getText("add_to_playlist")) },
            onClick = {
                onDismissRequest()
                onAddToPlaylist()
            }
        )
        DropdownMenuItem(
            text = { Text(Loc.getText("create_task")) },
            onClick = {
                onDismissRequest()
                onCreateTask()
            }
        )
        DropdownMenuItem(
            text = { Text(Loc.getText("show_associated_tasks")) },
            onClick = {
                onDismissRequest()
                onShowAssociatedTasks()
            }
        )
        DropdownMenuItem(
            text = { Text(Loc.getText("info")) },
            onClick = {
                onDismissRequest()
                onViewInfo()
            }
        )
        DropdownMenuItem(
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.RestartAlt,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(Loc.getText("reset_segments"))
                }
            },
            onClick = {
                onDismissRequest()
                viewModel.resetTrackSegments(track)
                Toast.makeText(context, Loc.getText("segments_reset_success"), Toast.LENGTH_SHORT).show()
            }
        )
        if (track.isVirtualScene) {
            DropdownMenuItem(
                text = { Text(Loc.getText("edit_scene_title")) },
                onClick = {
                    onDismissRequest()
                    onEditScene()
                }
            )
        } else {
            DropdownMenuItem(
                text = { Text(Loc.getText("review_edit_scenes")) },
                onClick = {
                    onDismissRequest()
                    onReviewScenes()
                }
            )
            DropdownMenuItem(
                text = { Text(Loc.getText("ai_scene_detection_option")) },
                leadingIcon = {
                    Icon(
                        Icons.Filled.MovieCreation,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                },
                onClick = {
                    onDismissRequest()
                    viewModel.openAiHub(
                        function = AiFunctionType.SCENES,
                        track = track
                    )
                }
            )
        }
        DropdownMenuItem(
            text = { Text(Loc.getText("delete_history"), color = MaterialTheme.colorScheme.error) },
            onClick = {
                onDismissRequest()
                when {
                    taskId != null -> {
                        viewModel.resetTaskTrackProgress(taskId, track.id)
                        Toast.makeText(context, Loc.getText("re_activated_msg"), Toast.LENGTH_SHORT).show()
                    }
                    playlistId != null -> {
                        viewModel.removeTrackFromPlaylist(playlistId, track.id)
                        Toast.makeText(context, Loc.getText("delete_history"), Toast.LENGTH_SHORT).show()
                    }
                    else -> {
                        viewModel.deleteTrackFromApp(track)
                        Toast.makeText(context, Loc.getText("delete_history"), Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }
}

@Composable
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
fun UnifiedAudioTrackRow(
    track: AudioTrack,
    isCurrentExecuting: Boolean,
    isPlaying: Boolean,
    viewModel: AppViewModel,
    playlistId: Long? = null,
    taskId: Long? = null,
    onTrackPlaylistMenuClicked: (AudioTrack) -> Unit = {},
    onCreateTask: (String, Long) -> Unit = { _, _ -> },
    onShowAssociatedTasks: (String, Long, String) -> Unit = { _, _, _ -> },
    playlistTracks: List<AudioTrack> = emptyList(),
    isSelected: Boolean = false,
    isBulkSelectMode: Boolean = false,
    onClick: () -> Unit = { viewModel.selectAndPlay(track, playlistTracks) },
    onLongClick: () -> Unit = {},
    customStartIcon: ImageVector? = null,
    customStartIconTint: Color? = null,
    taskProgressText: String? = null
) {
    var showTrackMenu by remember { mutableStateOf(false) }
    var showInfoDialog by remember { mutableStateOf(false) }
    var showEditSceneDialog by remember { mutableStateOf(false) }
    var showReviewScenesDialog by remember { mutableStateOf(false) }

    if (showInfoDialog) {
        TrackInfoDialog(track = track, onDismiss = { showInfoDialog = false })
    }

    if (showEditSceneDialog) {
        EditVirtualSceneDialog(
            track = track,
            viewModel = viewModel,
            onDismiss = { showEditSceneDialog = false }
        )
    }

    if (showReviewScenesDialog) {
        ReviewScenesDialog(
            parentTrack = track,
            viewModel = viewModel,
            onDismiss = { showReviewScenesDialog = false },
            onPlayScene = { scene -> viewModel.selectAndPlay(scene, playlistTracks) }
        )
    }

    val isVideoTrack = remember(track.filePath) { SubtitleParser.isVideoFile(track.filePath) }
    val displayName = remember(track.fileName, track.filePath, track.isVirtualScene) {
        track.getDisplayTitle()
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.18f)
            } else if (isCurrentExecuting) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            } else {
                MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
            }
        ),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            1.dp,
            if (isSelected || isCurrentExecuting) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, top = 10.dp, end = 4.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isBulkSelectMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { _ -> onLongClick() }
                )
                Spacer(modifier = Modifier.width(8.dp))
            } else {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(
                            if (track.isMissing) {
                                MaterialTheme.colorScheme.error.copy(alpha = 0.08f)
                            } else {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.06f)
                            },
                            RoundedCornerShape(12.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = customStartIcon ?: if (isCurrentExecuting && isPlaying) {
                            Icons.Filled.PlayArrow
                        } else if (track.isMissing) {
                            Icons.Filled.Warning
                        } else {
                            getTrackFileIcon(track)
                        },
                        contentDescription = "Track",
                        tint = customStartIconTint ?: if (track.isMissing) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isCurrentExecuting) displayName else middleEllipse(displayName, 26), modifier = if (isCurrentExecuting) Modifier.basicMarquee() else Modifier,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.SansSerif
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                if (track.isMissing) {
                    Text(
                        text = Loc.getText("missing_file_warning"),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold)
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AccessTime,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = formatDuration(track.duration),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                        )

                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${track.getProgressPercent()}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                        )
                        if (!taskProgressText.isNullOrEmpty()) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "•",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (taskProgressText.startsWith("🎧")) {
                                    Icon(
                                        imageVector = Icons.Filled.Headphones,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = taskProgressText.substring(2).trim(),
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                } else if (taskProgressText.startsWith("📅")) {
                                    Icon(
                                        imageVector = Icons.Filled.CalendarToday,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.size(11.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = taskProgressText.substring(2).trim(),
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                } else {
                                    Text(
                                        text = taskProgressText,
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (taskId == null && taskProgressText.isNullOrEmpty() && !track.isMissing && track.playCount > 0) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(end = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Headphones,
                        contentDescription = "Plays count",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${track.playCount}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            if (!isBulkSelectMode) {
                Box {
                    IconButton(
                        onClick = { showTrackMenu = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = "More options",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    UnifiedTrackDropdownMenu(
                        expanded = showTrackMenu,
                        onDismissRequest = { showTrackMenu = false },
                        track = track,
                        viewModel = viewModel,
                        playlistId = playlistId,
                        taskId = taskId,
                        onCreateTask = { onCreateTask("TRACKS", track.id) },
                        onShowAssociatedTasks = { onShowAssociatedTasks("TRACKS", track.id, track.getDisplayTitle()) },
                        onAddToPlaylist = { onTrackPlaylistMenuClicked(track) },
                        onPlay = { onClick() },
                        onViewInfo = { showInfoDialog = true },
                        onEditScene = { showEditSceneDialog = true },
                        onReviewScenes = { showReviewScenesDialog = true }
                    )
                }
            }
        }
    }
}


fun middleEllipse(text: String, maxLength: Int = 26): String {
    if (text.length <= maxLength) return text
    val half = (maxLength - 3) / 2
    return text.take(half) + "..." + text.takeLast(maxLength - 3 - half)
}

@Composable
fun SmartFileNameText(
    text: String,
    isActive: Boolean = false,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyMedium,
    modifier: Modifier = Modifier,
    maxLength: Int = 26
) {
    Text(
        text = if (isActive) text else middleEllipse(text, maxLength),
        style = style,
        modifier = if (isActive) modifier.basicMarquee() else modifier,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

fun getSortedTracks(tracks: List<AudioTrack>, sortBy: String, isAscending: Boolean): List<AudioTrack> {
    val sorted = when (sortBy) {
        "name" -> tracks.sortedBy { it.getDisplayTitle().lowercase() }
        "duration" -> tracks.sortedBy { it.duration }
        "progress" -> tracks.sortedBy { it.getProgressPercent() }
        "play_count" -> tracks.sortedBy { it.playCount }
        "date" -> tracks.sortedBy { it.id }
        else -> tracks
    }
    return if (isAscending) sorted else sorted.reversed()
}

@Composable
fun TrackListSortHeader(
    totalCount: Int,
    sortBy: String,
    onSortByChange: (String) -> Unit,
    isAscending: Boolean,
    onIsAscendingChange: (Boolean) -> Unit,
    showCount: Boolean = true,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showCount) {
            Text(
                text = String.format(Loc.getText("files_count"), totalCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Spacer(modifier = Modifier.width(1.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box {
                TextButton(
                    onClick = { expanded = true },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    val sortLabel = when (sortBy) {
                        "name" -> Loc.getText("sort_name")
                        "duration" -> Loc.getText("sort_duration")
                        "progress" -> Loc.getText("sort_progress")
                        "play_count" -> Loc.getText("sort_play_count")
                        "date" -> Loc.getText("sort_date")
                        else -> Loc.getText("sort_by")
                    }
                    Text(
                        text = sortLabel,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Filled.ArrowDropDown,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    val sortOptions = listOf(
                        "name" to Loc.getText("sort_name"),
                        "duration" to Loc.getText("sort_duration"),
                        "progress" to Loc.getText("sort_progress"),
                        "play_count" to Loc.getText("sort_play_count"),
                        "date" to Loc.getText("sort_date")
                    )
                    sortOptions.forEach { (key, label) ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = label,
                                    fontWeight = if (sortBy == key) FontWeight.Bold else FontWeight.Normal,
                                    color = if (sortBy == key) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            },
                            onClick = {
                                onSortByChange(key)
                                expanded = false
                            }
                        )
                    }
                }
            }
            IconButton(
                onClick = { onIsAscendingChange(!isAscending) },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = if (isAscending) Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward,
                    contentDescription = "Sort order",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun SubfolderDetailsItem(
    sub: Folder,
    depth: Int = 0,
    viewModel: AppViewModel,
    onNavigate: (Long) -> Unit,
    onRenameFolder: (Folder) -> Unit = {},
    onCreateTaskForSource: (String, Long) -> Unit = { _, _ -> },
    onShowAssociatedTasks: (String, Long, String) -> Unit = { _, _, _ -> }
) {
    val subTracks by viewModel.repository.getTracksForFolderFlow(sub.id).collectAsStateWithLifecycle(emptyList())
    val childSubfolders by viewModel.getSubfolders(sub.id).collectAsStateWithLifecycle(emptyList())
    var isExpanded by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = (depth * 16).dp)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onNavigate(sub.id) }
                .testTag("subfolder_card_${sub.id}"),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
            ),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, top = 10.dp, end = 12.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                            RoundedCornerShape(12.dp)
                        )
                        .clip(RoundedCornerShape(12.dp))
                        .then(
                            if (childSubfolders.isNotEmpty()) {
                                Modifier.clickable { isExpanded = !isExpanded }
                            } else Modifier
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isExpanded && childSubfolders.isNotEmpty()) Icons.Filled.FolderOpen else Icons.Filled.Folder,
                        contentDescription = "Folder",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    if (childSubfolders.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(2.dp)
                                .size(15.dp)
                                .background(
                                    MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isExpanded) Icons.Filled.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = if (isExpanded) "Collapse" else "Expand",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = sub.folderName,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    val summaryParts = mutableListOf<String>()
                    if (subTracks.isNotEmpty()) {
                        summaryParts.add(String.format(Loc.getText("files_count"), subTracks.size))
                    }
                    if (childSubfolders.isNotEmpty()) {
                        summaryParts.add(String.format(Loc.getText("folders_count"), childSubfolders.size))
                    }
                    val folderSummaryText = summaryParts.joinToString(" • ")
                    if (folderSummaryText.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = folderSummaryText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }
                }
                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = "Folder Options",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(Loc.getText("rename")) },
                            onClick = {
                                showMenu = false
                                onRenameFolder(sub)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(Loc.getText("create_task")) },
                            onClick = {
                                showMenu = false
                                onCreateTaskForSource("FOLDER", sub.id)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(Loc.getText("show_associated_tasks")) },
                            onClick = {
                                showMenu = false
                                onShowAssociatedTasks("FOLDER", sub.id, sub.folderName)
                            }
                        )
                    }
                }
            }
        }

        if (isExpanded && childSubfolders.isNotEmpty()) {
            Spacer(modifier = Modifier.height(6.dp))
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                childSubfolders.forEach { childSub ->
                    SubfolderDetailsItem(
                        sub = childSub,
                        depth = depth + 1,
                        viewModel = viewModel,
                        onNavigate = onNavigate,
                        onRenameFolder = onRenameFolder,
                        onCreateTaskForSource = onCreateTaskForSource,
                        onShowAssociatedTasks = onShowAssociatedTasks
                    )
                }
            }
        }
    }
}

@Composable
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
fun FolderTreeNodeItem(
    folder: Folder,
    depth: Int = 0,
    viewModel: AppViewModel,
    isFolderBulkSelectMode: Boolean = false,
    selectedFolderIds: Set<Long> = emptySet(),
    onToggleSelectFolder: (Long) -> Unit = {},
    onFolderDetailsClicked: (Long) -> Unit = {},
    onLongClickFolder: (Long) -> Unit = {},
    onRenameFolder: (Folder) -> Unit = {},
    onCreateTaskForSource: (String, Long) -> Unit = { _, _ -> },
    onShowAssociatedTasks: (String, Long, String) -> Unit = { _, _, _ -> }
) {
    var showMenu by remember { mutableStateOf(false) }
    var isExpanded by remember { mutableStateOf(false) }
    var showDeleteFolderConfirm by remember { mutableStateOf(false) }
    val isSelected = selectedFolderIds.contains(folder.id)
    val folderTracks by viewModel.repository.getTracksForFolderFlow(folder.id).collectAsStateWithLifecycle(emptyList())
    val childSubfolders by viewModel.getSubfolders(folder.id).collectAsStateWithLifecycle(emptyList())

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = (depth * 16).dp)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = {
                        if (isFolderBulkSelectMode) {
                            onToggleSelectFolder(folder.id)
                        } else {
                            onFolderDetailsClicked(folder.id)
                        }
                    },
                    onLongClick = {
                        onLongClickFolder(folder.id)
                    }
                ),
            colors = CardDefaults.cardColors(
                containerColor = if (isSelected) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.18f)
                } else {
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
                }
            ),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(
                1.dp,
                if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, top = 10.dp, end = 4.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isFolderBulkSelectMode) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { onToggleSelectFolder(folder.id) }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                } else {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                                RoundedCornerShape(12.dp)
                            )
                            .clip(RoundedCornerShape(12.dp))
                            .then(
                                if (childSubfolders.isNotEmpty()) {
                                    Modifier.clickable { isExpanded = !isExpanded }
                                } else Modifier
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isExpanded && childSubfolders.isNotEmpty()) Icons.Filled.FolderOpen else Icons.Filled.Folder,
                            contentDescription = "Folder",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        if (childSubfolders.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(2.dp)
                                    .size(15.dp)
                                    .background(
                                        MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isExpanded) Icons.Filled.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = folder.folderName,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val summaryParts = mutableListOf<String>()
                    if (folderTracks.isNotEmpty()) {
                        summaryParts.add(String.format(Loc.getText("files_count"), folderTracks.size))
                    }
                    if (childSubfolders.isNotEmpty()) {
                        summaryParts.add(String.format(Loc.getText("folders_count"), childSubfolders.size))
                    }
                    val folderSummaryText = summaryParts.joinToString(" • ")
                    if (folderSummaryText.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = folderSummaryText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
                if (!isFolderBulkSelectMode) {
                    Box {
                        IconButton(
                            onClick = { showMenu = true },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.MoreVert,
                                contentDescription = "Folder Options",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(Loc.getText("rename")) },
                                onClick = {
                                    showMenu = false
                                    onRenameFolder(folder)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(Loc.getText("create_task")) },
                                onClick = {
                                    showMenu = false
                                    onCreateTaskForSource("FOLDER", folder.id)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(Loc.getText("show_associated_tasks")) },
                                onClick = {
                                    showMenu = false
                                    onShowAssociatedTasks("FOLDER", folder.id, folder.folderName)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(Loc.getText("delete"), color = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    showMenu = false
                                    showDeleteFolderConfirm = true
                                }
                            )
                        }
                    }
                }
            }
        }

        if (showDeleteFolderConfirm) {
            AlertDialog(
                onDismissRequest = { showDeleteFolderConfirm = false },
                title = { Text(Loc.getText("delete_folder_title")) },
                text = { Text(Loc.getText("delete_folder_confirm")) },
                confirmButton = {
                    Button(
                        onClick = {
                            showDeleteFolderConfirm = false
                            viewModel.deleteFolder(folder.id)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text(Loc.getText("delete"))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteFolderConfirm = false }) {
                        Text(Loc.getText("cancel"))
                    }
                }
            )
        }

        if (isExpanded && childSubfolders.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                childSubfolders.forEach { child ->
                    FolderTreeNodeItem(
                        folder = child,
                        depth = depth + 1,
                        viewModel = viewModel,
                        isFolderBulkSelectMode = isFolderBulkSelectMode,
                        selectedFolderIds = selectedFolderIds,
                        onToggleSelectFolder = onToggleSelectFolder,
                        onFolderDetailsClicked = onFolderDetailsClicked,
                        onLongClickFolder = onLongClickFolder,
                        onRenameFolder = onRenameFolder,
                        onCreateTaskForSource = onCreateTaskForSource,
                        onShowAssociatedTasks = onShowAssociatedTasks
                    )
                }
            }
        }
    }
}

@Composable
fun TrackInfoDialog(
    track: AudioTrack,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = Loc.getText("file_info_title"),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                InfoItemRow(label = Loc.getText("name_header"), value = track.getDisplayTitle())
                InfoItemRow(label = Loc.getText("file_path"), value = track.filePath)
                InfoItemRow(label = Loc.getText("duration"), value = formatDuration(track.duration))
                InfoItemRow(label = Loc.getText("sort_progress"), value = "${track.getProgressPercent()}%")
                InfoItemRow(label = Loc.getText("plays_count"), value = "${track.playCount}")
                if (track.isVirtualScene) {
                    InfoItemRow(label = Loc.getText("virtual_scene_badge"), value = Loc.getText("yes"))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(Loc.getText("close"))
            }
        },
        shape = RoundedCornerShape(20.dp)
    )
}

@Composable
private fun InfoItemRow(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
