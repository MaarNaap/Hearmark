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
                            text = middleEllipse(latestResumableTrack.getDisplayTitle(), 42),
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
                            val segmentsColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                            val cursorColor = MaterialTheme.colorScheme.onPrimaryContainer
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
                                if (playbackFraction > 0f) {
                                    val cursorX = (playbackFraction.coerceIn(0f, 1f) * size.width).coerceIn(0f, size.width)
                                    drawCircle(
                                        color = cursorColor,
                                        radius = size.height * 0.8f,
                                        center = androidx.compose.ui.geometry.Offset(x = cursorX, y = size.height / 2f)
                                    )
                                }
                            }
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
