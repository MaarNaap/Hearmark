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
    var showImportJsonDialog by remember { mutableStateOf(false) }

    if (showImportJsonDialog) {
        ImportScenesJsonDialog(
            parentTrack = parentTrack,
            viewModel = viewModel,
            onDismiss = { showImportJsonDialog = false }
        )
    }

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
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.MovieCreation,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = Loc.getText("review_scenes_title"),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
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

                Spacer(modifier = Modifier.width(8.dp))

                // Add Scene Manually (+) Icon Button at top
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(38.dp)
                ) {
                    IconButton(
                        onClick = { isAddingScene = true },
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = Loc.getText("add_scene_btn"),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        },
        text = {
            if (scenes.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
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
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 1. Detect Scenes with AI button (primary action with fixed compact 44dp height)
                        Button(
                            onClick = {
                                onDismiss()
                                viewModel.openAiHub(
                                    function = AiFunctionType.SCENES,
                                    track = parentTrack
                                )
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            contentPadding = PaddingValues(horizontal = 12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.AutoAwesome,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = Loc.getText("detect_scenes_btn"),
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // 2. Import Scenes from JSON (compact square 44x44dp icon button, never tall)
                        OutlinedIconButton(
                            onClick = { showImportJsonDialog = true },
                            modifier = Modifier.size(44.dp),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                        ) {
                            Icon(
                                imageVector = Icons.Filled.FileUpload,
                                contentDescription = Loc.getText("import_scenes_json_btn"),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
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
    onReviewScenes: () -> Unit = {},
    onImportScenesJson: () -> Unit = {}
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
            DropdownMenuItem(
                text = { Text(Loc.getText("import_scenes_json_option")) },
                leadingIcon = {
                    Icon(
                        Icons.Filled.FileUpload,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(20.dp)
                    )
                },
                onClick = {
                    onDismissRequest()
                    onImportScenesJson()
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
    var showImportScenesJsonDialog by remember { mutableStateOf(false) }

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

    if (showImportScenesJsonDialog) {
        ImportScenesJsonDialog(
            parentTrack = track,
            viewModel = viewModel,
            onDismiss = { showImportScenesJsonDialog = false }
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
                        onReviewScenes = { showReviewScenesDialog = true },
                        onImportScenesJson = { showImportScenesJsonDialog = true }
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
