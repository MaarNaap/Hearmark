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
    var showDeleteTaskConfirm by remember { mutableStateOf(false) }

    if (showDeleteTaskConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteTaskConfirm = false },
            title = { Text(Loc.getText("delete_task_confirm_title")) },
            text = { Text(Loc.getText("delete_task_confirm_desc")) },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteTaskConfirm = false
                        viewModel.deleteTask(currentTask.id)
                        onBack()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(Loc.getText("delete"))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteTaskConfirm = false }) {
                    Text(Loc.getText("cancel"))
                }
            }
        )
    }
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
                        text = { Text(Loc.getText("delete"), color = MaterialTheme.colorScheme.error) },
                        onClick = {
                            expandedMenu = false
                            showDeleteTaskConfirm = true
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
                    text = Loc.getFormattedText("schedule_alert_desc", currentTask.reminderTime.toWesternDigits(), formatScheduledDays(currentTask.scheduledDays)),
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
            items(sortedProgressWithTracks, key = { it.first.trackId }) { (progress, track) ->
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

        val totalTaskDurationStr = remember(totalTaskDurationMs, Loc.currentLanguage) {
            val totalSeconds = totalTaskDurationMs / 1000
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            val seconds = totalSeconds % 60
            when {
                hours > 0 -> Loc.getFormattedText("task_dur_hms", hours, minutes, seconds)
                minutes > 0 -> Loc.getFormattedText("task_dur_ms", minutes, seconds)
                else -> Loc.getFormattedText("task_dur_s", seconds)
            }
        }

        val totalTimeListenedStr = remember(totalTimeListenedMs, Loc.currentLanguage) {
            val totalSeconds = totalTimeListenedMs / 1000
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            val seconds = totalSeconds % 60
            when {
                hours > 0 -> Loc.getFormattedText("task_dur_hms", hours, minutes, seconds)
                minutes > 0 -> Loc.getFormattedText("task_dur_ms", minutes, seconds)
                else -> Loc.getFormattedText("task_dur_s", seconds)
            }
        }

        val sdf = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US) }
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
            when {
                days > 1 -> Loc.getFormattedText("time_ago_days", days)
                days == 1L -> Loc.getText("time_ago_day_one")
                hours > 1 -> Loc.getFormattedText("time_ago_hours", hours)
                hours == 1L -> Loc.getText("time_ago_hour_one")
                mins > 1 -> Loc.getFormattedText("time_ago_mins", mins)
                mins == 1L -> Loc.getText("time_ago_min_one")
                else -> Loc.getText("time_ago_just_now")
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
