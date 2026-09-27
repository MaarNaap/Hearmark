package com.example.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.input.pointer.pointerInput
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.*
import com.example.ui.theme.ColorSuccess
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

// --- SUB-SCREEN 8: STATISTICS VIEW (Multi-tab Analytics, Daily Breakdown & Performance) ---

@Composable
fun StatsView(viewModel: AppViewModel) {
    val history by viewModel.playbackHistory.collectAsStateWithLifecycle(emptyList())
    val allTracks by viewModel.tracks.collectAsStateWithLifecycle(emptyList())
    val allFolders by viewModel.folders.collectAsStateWithLifecycle(emptyList())
    val allTasks by viewModel.allTasks.collectAsStateWithLifecycle(emptyList())
    val allTaskProgress by viewModel.allTaskProgress.collectAsStateWithLifecycle(emptyList())
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { 3 }) // 0: Overview, 1: Daily, 2: Tracks

    var showDetailedHistoryLog by remember { mutableStateOf(false) }
    var sortBy by remember { mutableStateOf("period_count") } // period_count, total_count, name
    var showSortMenu by remember { mutableStateOf(false) }

    // Task Filter state
    var selectedTaskIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var showTaskFilterDialog by remember { mutableStateOf(false) }

    // Folder & File Filter state
    var selectedFolderIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var selectedFileTrackIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var showFolderFileFilterDialog by remember { mutableStateOf(false) }

    // Allowed track IDs based on selected tasks
    val allowedTrackIdsForTasks = remember(selectedTaskIds, allTasks, allTracks, allTaskProgress) {
        if (selectedTaskIds.isEmpty()) {
            null
        } else {
            val selectedTasks = allTasks.filter { it.id in selectedTaskIds }
            val ids = mutableSetOf<Long>()
            selectedTasks.forEach { task ->
                when (task.sourceType) {
                    "FOLDER" -> {
                        allTracks.filter { it.parentFolderId == task.sourceId }.forEach { ids.add(it.id) }
                    }
                    "TRACKS" -> {
                        allTaskProgress.filter { it.taskId == task.id }.forEach { ids.add(it.trackId) }
                    }
                    else -> {
                        allTaskProgress.filter { it.taskId == task.id }.forEach { ids.add(it.trackId) }
                    }
                }
            }
            ids
        }
    }

    // Allowed track IDs based on selected folders and files
    val allowedTrackIdsForFolderFiles = remember(selectedFolderIds, selectedFileTrackIds, allFolders, allTracks) {
        if (selectedFolderIds.isEmpty() && selectedFileTrackIds.isEmpty()) {
            null
        } else {
            val ids = mutableSetOf<Long>()
            ids.addAll(selectedFileTrackIds)
            if (selectedFolderIds.isNotEmpty()) {
                val allTargetFolderIds = mutableSetOf<Long>()
                selectedFolderIds.forEach { fId ->
                    allTargetFolderIds.addAll(getAllSubfolderIds(fId, allFolders))
                }
                allTracks.filter { it.parentFolderId in allTargetFolderIds }.forEach { ids.add(it.id) }
            }
            ids
        }
    }

    // Combined track filter (intersection of task filter and folder/file filter if both active)
    val combinedAllowedTrackIds = remember(allowedTrackIdsForTasks, allowedTrackIdsForFolderFiles) {
        when {
            allowedTrackIdsForTasks == null && allowedTrackIdsForFolderFiles == null -> null
            allowedTrackIdsForTasks != null && allowedTrackIdsForFolderFiles != null -> allowedTrackIdsForTasks.intersect(allowedTrackIdsForFolderFiles)
            allowedTrackIdsForTasks != null -> allowedTrackIdsForTasks
            else -> allowedTrackIdsForFolderFiles
        }
    }

    // Filter list by selected time range
    val timeFiltered = remember(history, viewModel.statsFilter) {
        when (viewModel.statsFilter) {
            "today" -> {
                val cal = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val startOfToday = cal.timeInMillis
                history.filter { it.completedAt >= startOfToday }
            }
            "week" -> {
                val cal = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                    add(Calendar.DAY_OF_YEAR, -6)
                }
                val startOfWeek = cal.timeInMillis
                history.filter { it.completedAt >= startOfWeek }
            }
            "month" -> {
                val cal = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                    add(Calendar.DAY_OF_YEAR, -29)
                }
                val startOfMonth = cal.timeInMillis
                history.filter { it.completedAt >= startOfMonth }
            }
            "ninety" -> {
                val cal = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                    add(Calendar.DAY_OF_YEAR, -89)
                }
                val startOfNinety = cal.timeInMillis
                history.filter { it.completedAt >= startOfNinety }
            }
            else -> history // all history
        }
    }

    // Combine time filter and selected entity filters
    val rangeFiltered = remember(timeFiltered, selectedTaskIds, combinedAllowedTrackIds, allowedTrackIdsForTasks, allowedTrackIdsForFolderFiles, allTasks) {
        if (selectedTaskIds.isEmpty()) {
            if (combinedAllowedTrackIds == null) {
                timeFiltered
            } else {
                timeFiltered.filter { it.trackId in combinedAllowedTrackIds }
            }
        } else {
            val selectedTasks = allTasks.filter { it.id in selectedTaskIds }
            val selectedTitles = selectedTasks.map { it.getDisplayTitle() }.toSet()
            timeFiltered.filter { item ->
                val taskIds = item.getActiveTaskIds()
                val taskTitles = item.getActiveTasksList()
                val matchesTask = if (taskIds.isNotEmpty()) {
                    taskIds.any { it in selectedTaskIds } || taskTitles.any { it in selectedTitles }
                } else if (item.activeTasks.isBlank() && allowedTrackIdsForTasks != null) {
                    item.trackId in allowedTrackIdsForTasks
                } else {
                    false
                }
                val matchesFolderFile = if (allowedTrackIdsForFolderFiles != null) {
                    item.trackId in allowedTrackIdsForFolderFiles
                } else {
                    true
                }
                matchesTask && matchesFolderFile
            }
        }
    }

    val totalListensInPeriod = rangeFiltered.size
    val uniqueTracksCountInPeriod = remember(rangeFiltered) {
        rangeFiltered.map { it.trackId }.distinct().size
    }

    // Calculate aggregated durations and speed
    val (totalContentMs, totalActualMs, avgPlaybackSpeed) = remember(rangeFiltered, allTracks) {
        val trackMap = allTracks.associateBy { it.id }
        var contentSum = 0L
        var actualSum = 0L
        var speedSum = 0f
        rangeFiltered.forEach { item ->
            val track = trackMap[item.trackId]
            val duration = if (item.durationMs > 0L) item.durationMs else (track?.duration ?: 0L)
            val speed = if (item.playbackSpeed > 0f) item.playbackSpeed else 1.0f
            contentSum += duration
            val actual = if (item.actualListenedMs > 0L) item.actualListenedMs else (if (speed > 0f) (duration / speed).toLong() else duration)
            actualSum += actual
            speedSum += speed
        }
        val avgSpd = if (rangeFiltered.isNotEmpty()) speedSum / rangeFiltered.size else 1.0f
        Triple(contentSum, actualSum, avgSpd)
    }

    val timeSavedMs = remember(totalContentMs, totalActualMs) {
        maxOf(0L, totalContentMs - totalActualMs)
    }

    // Daily breakdown groups
    val dayGroups: List<DaySummaryItem> = remember(rangeFiltered, allTracks, allTasks, allTaskProgress) {
        val trackMap = allTracks.associateBy { it.id }
        val sdfKey = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val sdfDisplay = SimpleDateFormat("EEEE, d MMMM", Locale(Loc.currentLanguage))

        rangeFiltered.groupBy { sdfKey.format(Date(it.completedAt)) }
            .map { (dayKey, records) ->
                val dayTimestamp = records.first().completedAt
                val dayDisplay = sdfDisplay.format(Date(dayTimestamp))
                var dayContent = 0L
                var dayActual = 0L

                records.forEach { r ->
                    val dur = if (r.durationMs > 0L) r.durationMs else (trackMap[r.trackId]?.duration ?: 0L)
                    val spd = if (r.playbackSpeed > 0f) r.playbackSpeed else 1.0f
                    dayContent += dur
                    val actual = if (r.actualListenedMs > 0L) r.actualListenedMs else (if (spd > 0f) (dur / spd).toLong() else dur)
                    dayActual += actual
                }

                val trackItems = records.groupBy { it.trackId }.map { (trackId, trackRecs) ->
                    val track = trackMap[trackId]
                    val rawName = track?.fileName ?: trackRecs.firstOrNull()?.trackName ?: "Track #$trackId"
                    val name = rawName.substringBeforeLast(".")
                    val playCount = trackRecs.size
                    val baseDur = if (trackRecs.first().durationMs > 0L) trackRecs.first().durationMs else (track?.duration ?: 0L)
                    val avgSpd = trackRecs.map { if (it.playbackSpeed > 0f) it.playbackSpeed else 1.0f }.average().toFloat()
                    val trackActual = trackRecs.sumOf { r ->
                        if (r.actualListenedMs > 0L) r.actualListenedMs
                        else {
                            val rDur = if (r.durationMs > 0L) r.durationMs else (track?.duration ?: 0L)
                            val rSpd = if (r.playbackSpeed > 0f) r.playbackSpeed else 1.0f
                            if (rSpd > 0f) (rDur / rSpd).toLong() else rDur
                        }
                    }

                    val attachedTaskNames = trackRecs.flatMap { r ->
                        val logged = r.getActiveTasksList()
                        if (logged.isNotEmpty()) {
                            logged
                        } else if (r.activeTasks.isBlank()) {
                            // Fallback for legacy records: only include tasks active at r.completedAt
                            val trackId = r.trackId
                            allTasks.filter { task ->
                                val wasActiveThen = r.completedAt >= task.startDate && (task.endDate == null || r.completedAt <= task.endDate)
                                wasActiveThen && when (task.sourceType) {
                                    "FOLDER" -> track?.parentFolderId == task.sourceId
                                    "TRACKS" -> allTaskProgress.any { it.taskId == task.id && it.trackId == trackId }
                                    else -> allTaskProgress.any { it.taskId == task.id && it.trackId == trackId }
                                }
                            }.map { it.getDisplayTitle() }
                        } else {
                            emptyList()
                        }
                    }.filter { it.isNotBlank() }.distinct()

                    DayTrackItem(
                        trackId = trackId,
                        trackName = name,
                        playCount = playCount,
                        durationMs = baseDur,
                        totalActualMs = trackActual,
                        avgSpeed = avgSpd,
                        attachedTasks = attachedTaskNames
                    )
                }

                DaySummaryItem(
                    dayKey = dayKey,
                    dayDisplay = dayDisplay,
                    timestamp = dayTimestamp,
                    totalActualMs = dayActual,
                    totalContentMs = dayContent,
                    tracks = trackItems
                )
            }.sortedByDescending { it.timestamp }
    }

    val activeDaysCount = dayGroups.size
    val dailyAvgMs = remember(totalActualMs, activeDaysCount) {
        if (activeDaysCount > 0) totalActualMs / activeDaysCount else 0L
    }

    // Chart Data Points computed according to the active date filter
    val chartDataPoints: List<ActivityChartDataPoint> = remember(rangeFiltered, allTracks, viewModel.statsFilter, Loc.currentLanguage) {
        val trackMap = allTracks.associateBy { it.id }
        val locale = Locale(Loc.currentLanguage)

        when (viewModel.statsFilter) {
            "today" -> {
                val cal = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val baseDayMs = cal.timeInMillis
                (0..23).map { hour ->
                    val startMs = baseDayMs + hour * 3600_000L
                    val endMs = startMs + 3600_000L - 1L
                    val hourRecords = rangeFiltered.filter { it.completedAt in startMs..endMs }
                    var actualMs = 0L
                    var contentMs = 0L
                    hourRecords.forEach { r ->
                        val dur = if (r.durationMs > 0L) r.durationMs else (trackMap[r.trackId]?.duration ?: 0L)
                        val spd = if (r.playbackSpeed > 0f) r.playbackSpeed else 1.0f
                        contentMs += dur
                        val act = if (r.actualListenedMs > 0L) r.actualListenedMs else (if (spd > 0f) (dur / spd).toLong() else dur)
                        actualMs += act
                    }
                    val hourNext = (hour + 1) % 24
                    val timeLabel = String.format(Locale.US, "%02d:00", hour)
                    val fullLabel = String.format(Locale.US, "%02d:00 - %02d:00", hour, hourNext)
                    val showLabel = hour % 4 == 0 || hour == 23
                    ActivityChartDataPoint(
                        timestamp = startMs,
                        axisLabel = timeLabel,
                        showAxisLabel = showLabel,
                        fullTitle = fullLabel,
                        actualDurationMs = actualMs,
                        contentDurationMs = contentMs,
                        playCount = hourRecords.size
                    )
                }
            }
            "week" -> {
                val cal = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                    add(Calendar.DAY_OF_YEAR, -6)
                }
                val sdfAxis = SimpleDateFormat("E", locale)
                val sdfFull = SimpleDateFormat("EEEE, d MMMM", locale)

                (0..6).map { dayIdx ->
                    val dayCal = (cal.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, dayIdx) }
                    val startMs = dayCal.timeInMillis
                    val endMs = startMs + 86400_000L - 1L
                    val dayRecords = rangeFiltered.filter { it.completedAt in startMs..endMs }
                    var actualMs = 0L
                    var contentMs = 0L
                    dayRecords.forEach { r ->
                        val dur = if (r.durationMs > 0L) r.durationMs else (trackMap[r.trackId]?.duration ?: 0L)
                        val spd = if (r.playbackSpeed > 0f) r.playbackSpeed else 1.0f
                        contentMs += dur
                        val act = if (r.actualListenedMs > 0L) r.actualListenedMs else (if (spd > 0f) (dur / spd).toLong() else dur)
                        actualMs += act
                    }
                    ActivityChartDataPoint(
                        timestamp = startMs,
                        axisLabel = sdfAxis.format(Date(startMs)),
                        showAxisLabel = true,
                        fullTitle = sdfFull.format(Date(startMs)),
                        actualDurationMs = actualMs,
                        contentDurationMs = contentMs,
                        playCount = dayRecords.size
                    )
                }
            }
            "month" -> {
                val cal = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                    add(Calendar.DAY_OF_YEAR, -29)
                }
                val sdfAxis = SimpleDateFormat("d MMM", locale)
                val sdfFull = SimpleDateFormat("EEEE, d MMMM", locale)

                (0..29).map { dayIdx ->
                    val dayCal = (cal.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, dayIdx) }
                    val startMs = dayCal.timeInMillis
                    val endMs = startMs + 86400_000L - 1L
                    val dayRecords = rangeFiltered.filter { it.completedAt in startMs..endMs }
                    var actualMs = 0L
                    var contentMs = 0L
                    dayRecords.forEach { r ->
                        val dur = if (r.durationMs > 0L) r.durationMs else (trackMap[r.trackId]?.duration ?: 0L)
                        val spd = if (r.playbackSpeed > 0f) r.playbackSpeed else 1.0f
                        contentMs += dur
                        val act = if (r.actualListenedMs > 0L) r.actualListenedMs else (if (spd > 0f) (dur / spd).toLong() else dur)
                        actualMs += act
                    }
                    val showLabel = dayIdx == 0 || dayIdx == 7 || dayIdx == 14 || dayIdx == 21 || dayIdx == 29
                    ActivityChartDataPoint(
                        timestamp = startMs,
                        axisLabel = sdfAxis.format(Date(startMs)),
                        showAxisLabel = showLabel,
                        fullTitle = sdfFull.format(Date(startMs)),
                        actualDurationMs = actualMs,
                        contentDurationMs = contentMs,
                        playCount = dayRecords.size
                    )
                }
            }
            "ninety" -> {
                val cal = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                    add(Calendar.DAY_OF_YEAR, -89)
                }
                val sdfAxis = SimpleDateFormat("d MMM", locale)
                val sdfFull = SimpleDateFormat("EEEE, d MMMM", locale)

                (0..89).map { dayIdx ->
                    val dayCal = (cal.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, dayIdx) }
                    val startMs = dayCal.timeInMillis
                    val endMs = startMs + 86400_000L - 1L
                    val dayRecords = rangeFiltered.filter { it.completedAt in startMs..endMs }
                    var actualMs = 0L
                    var contentMs = 0L
                    dayRecords.forEach { r ->
                        val dur = if (r.durationMs > 0L) r.durationMs else (trackMap[r.trackId]?.duration ?: 0L)
                        val spd = if (r.playbackSpeed > 0f) r.playbackSpeed else 1.0f
                        contentMs += dur
                        val act = if (r.actualListenedMs > 0L) r.actualListenedMs else (if (spd > 0f) (dur / spd).toLong() else dur)
                        actualMs += act
                    }
                    val showLabel = dayIdx == 0 || dayIdx % 18 == 0 || dayIdx == 89
                    ActivityChartDataPoint(
                        timestamp = startMs,
                        axisLabel = sdfAxis.format(Date(startMs)),
                        showAxisLabel = showLabel,
                        fullTitle = sdfFull.format(Date(startMs)),
                        actualDurationMs = actualMs,
                        contentDurationMs = contentMs,
                        playCount = dayRecords.size
                    )
                }
            }
            else -> {
                val todayCal = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val todayMs = todayCal.timeInMillis
                if (rangeFiltered.isEmpty()) {
                    val sdfAxis = SimpleDateFormat("E", locale)
                    val sdfFull = SimpleDateFormat("EEEE, d MMMM", locale)
                    (0..6).map { dayIdx ->
                        val startMs = todayMs - (6 - dayIdx) * 86400_000L
                        ActivityChartDataPoint(
                            timestamp = startMs,
                            axisLabel = sdfAxis.format(Date(startMs)),
                            showAxisLabel = true,
                            fullTitle = sdfFull.format(Date(startMs)),
                            actualDurationMs = 0L,
                            contentDurationMs = 0L,
                            playCount = 0
                        )
                    }
                } else {
                    val minTimestamp = rangeFiltered.minOf { it.completedAt }
                    val startCal = Calendar.getInstance().apply {
                        timeInMillis = minTimestamp
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    val totalDays = maxOf(7, (((todayMs - startCal.timeInMillis) / 86400_000L).toInt() + 1)).coerceAtMost(365)
                    val stepInterval = maxOf(1, totalDays / 6)
                    val sdfAxis = SimpleDateFormat("d MMM", locale)
                    val sdfFull = SimpleDateFormat("EEEE, d MMMM", locale)

                    (0 until totalDays).map { dayIdx ->
                        val dayCal = (startCal.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, dayIdx) }
                        val startMs = dayCal.timeInMillis
                        val endMs = startMs + 86400_000L - 1L
                        val dayRecords = rangeFiltered.filter { it.completedAt in startMs..endMs }
                        var actualMs = 0L
                        var contentMs = 0L
                        dayRecords.forEach { r ->
                            val dur = if (r.durationMs > 0L) r.durationMs else (trackMap[r.trackId]?.duration ?: 0L)
                            val spd = if (r.playbackSpeed > 0f) r.playbackSpeed else 1.0f
                            contentMs += dur
                            val act = if (r.actualListenedMs > 0L) r.actualListenedMs else (if (spd > 0f) (dur / spd).toLong() else dur)
                            actualMs += act
                        }
                        val showLabel = dayIdx == 0 || dayIdx % stepInterval == 0 || dayIdx == totalDays - 1
                        ActivityChartDataPoint(
                            timestamp = startMs,
                            axisLabel = sdfAxis.format(Date(startMs)),
                            showAxisLabel = showLabel,
                            fullTitle = sdfFull.format(Date(startMs)),
                            actualDurationMs = actualMs,
                            contentDurationMs = contentMs,
                            playCount = dayRecords.size
                        )
                    }
                }
            }
        }
    }

    var expandedDays by remember { mutableStateOf<Set<String>>(emptySet()) }

    // Track statistics for Tab 2
    val trackStats = remember(rangeFiltered, allTracks, history, sortBy) {
        val trackMap = allTracks.associateBy { it.id }
        val mapped = rangeFiltered.groupBy { it.trackId }
            .map { (trackId, records) ->
                val track = trackMap[trackId]
                val rawName = track?.fileName ?: records.firstOrNull()?.trackName ?: "Track #$trackId"
                val name = rawName.substringBeforeLast(".")
                val periodCount = records.size
                val totalCount = history.filter { it.trackId == trackId }.size
                TrackStatItem(
                    trackId = trackId,
                    name = name,
                    periodCount = periodCount,
                    totalAllTimeCount = maxOf(totalCount, track?.playCount ?: 0)
                )
            }
        when (sortBy) {
            "total_count" -> mapped.sortedByDescending { it.totalAllTimeCount }
            "name" -> mapped.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
            else -> mapped.sortedByDescending { it.periodCount }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 12.dp, start = 16.dp, end = 16.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 1. Sticky Time Filters Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            listOf(
                "all" to Loc.getText("all_time"),
                "today" to Loc.getText("today"),
                "week" to Loc.getText("seven_days"),
                "month" to Loc.getText("thirty_days"),
                "ninety" to Loc.getText("ninety_days")
            ).forEach { (key, label) ->
                val active = viewModel.statsFilter == key
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(
                            if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                            RoundedCornerShape(8.dp)
                        )
                        .clickable { viewModel.statsFilter = key }
                        .padding(vertical = 7.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        label,
                        color = if (active) Color.White else MaterialTheme.colorScheme.primary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }
            }
        }

        // 2. Filter Bar: Task Filter & Folder/File Filter
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Task Filter Chip
            if (allTasks.isNotEmpty()) {
                val hasTaskFilter = selectedTaskIds.isNotEmpty()
                Surface(
                    onClick = { showTaskFilterDialog = true },
                    shape = RoundedCornerShape(8.dp),
                    color = if (hasTaskFilter) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = BorderStroke(
                        1.dp,
                        if (hasTaskFilter) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                    ),
                    modifier = Modifier.testTag("task_filter_chip")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.FilterList,
                            contentDescription = null,
                            tint = if (hasTaskFilter) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = when {
                                selectedTaskIds.isEmpty() -> Loc.getText("all_tasks")
                                selectedTaskIds.size == 1 -> allTasks.find { it.id == selectedTaskIds.first() }?.getDisplayTitle() ?: Loc.getText("filter_by_task")
                                else -> "${selectedTaskIds.size} ${Loc.getText("filter_by_task")}"
                            },
                            fontSize = 11.sp,
                            fontWeight = if (hasTaskFilter) FontWeight.Bold else FontWeight.Medium,
                            color = if (hasTaskFilter) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (hasTaskFilter) {
                            IconButton(
                                onClick = { selectedTaskIds = emptySet() },
                                modifier = Modifier.size(16.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "Clear",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Folder & File Filter Chip
            val hasFolderFileFilter = selectedFolderIds.isNotEmpty() || selectedFileTrackIds.isNotEmpty()
            Surface(
                onClick = { showFolderFileFilterDialog = true },
                shape = RoundedCornerShape(8.dp),
                color = if (hasFolderFileFilter) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                border = BorderStroke(
                    1.dp,
                    if (hasFolderFileFilter) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                ),
                modifier = Modifier.testTag("folder_file_filter_chip")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.FolderOpen,
                        contentDescription = null,
                        tint = if (hasFolderFileFilter) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = when {
                            !hasFolderFileFilter -> Loc.getText("filter_by_folder_file")
                            selectedFolderIds.size == 1 && selectedFileTrackIds.isEmpty() -> {
                                allFolders.find { it.id == selectedFolderIds.first() }?.folderName ?: Loc.getText("filter_by_folder_file")
                            }
                            selectedFolderIds.isEmpty() && selectedFileTrackIds.size == 1 -> {
                                val tr = allTracks.find { it.id == selectedFileTrackIds.first() }
                                tr?.getDisplayTitle() ?: Loc.getText("filter_by_folder_file")
                            }
                            selectedFolderIds.isNotEmpty() && selectedFileTrackIds.isEmpty() -> {
                                "${selectedFolderIds.size} ${Loc.getText("filter_by_folder")}"
                            }
                            selectedFolderIds.isEmpty() && selectedFileTrackIds.isNotEmpty() -> {
                                "${selectedFileTrackIds.size} ${Loc.getText("filter_by_track")}"
                            }
                            else -> {
                                "${selectedFolderIds.size + selectedFileTrackIds.size} ${Loc.getText("filter_by_folder_file")}"
                            }
                        },
                        fontSize = 11.sp,
                        fontWeight = if (hasFolderFileFilter) FontWeight.Bold else FontWeight.Medium,
                        color = if (hasFolderFileFilter) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (hasFolderFileFilter) {
                        IconButton(
                            onClick = {
                                selectedFolderIds = emptySet()
                                selectedFileTrackIds = emptySet()
                            },
                            modifier = Modifier.size(16.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Clear",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }
            }
        }

        // 3. Primary Tabs Row
        TabRow(
            selectedTabIndex = pagerState.currentPage,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary,
            divider = { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)) }
        ) {
            Tab(
                selected = pagerState.currentPage == 0,
                onClick = { coroutineScope.launch { pagerState.animateScrollToPage(0) } },
                text = {
                    Text(
                        Loc.getText("tab_overview_insights"),
                        fontSize = 12.sp,
                        fontWeight = if (pagerState.currentPage == 0) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                icon = {
                    Icon(
                        imageVector = Icons.Filled.Insights,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            )
            Tab(
                selected = pagerState.currentPage == 1,
                onClick = { coroutineScope.launch { pagerState.animateScrollToPage(1) } },
                text = {
                    Text(
                        Loc.getText("tab_daily_activity"),
                        fontSize = 12.sp,
                        fontWeight = if (pagerState.currentPage == 1) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                icon = {
                    Icon(
                        imageVector = Icons.Filled.CalendarMonth,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            )
            Tab(
                selected = pagerState.currentPage == 2,
                onClick = { coroutineScope.launch { pagerState.animateScrollToPage(2) } },
                text = {
                    Text(
                        Loc.getText("tab_track_stats"),
                        fontSize = 12.sp,
                        fontWeight = if (pagerState.currentPage == 2) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                icon = {
                    Icon(
                        imageVector = Icons.Filled.BarChart,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            )
        }

        // 4. Swipeable Tab Content Switcher (HorizontalPager)
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f)
        ) { page ->
            when (page) {
                // TAB 0: OVERVIEW & INSIGHTS
                0 -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        contentPadding = PaddingValues(top = 4.dp, bottom = 8.dp)
                    ) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Card(
                                    modifier = Modifier.weight(1f),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Timer,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = formatStatsDuration(totalActualMs),
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = Loc.getText("actual_listen_time"),
                                            fontSize = 11.sp,
                                            color = Color.Gray,
                                            maxLines = 1
                                        )
                                    }
                                }

                                Card(
                                    modifier = Modifier.weight(1f),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.AccessTime,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.secondary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = formatStatsDuration(totalContentMs),
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = Loc.getText("content_listen_time"),
                                            fontSize = 11.sp,
                                            color = Color.Gray,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }

                        if (timeSavedMs > 0L) {
                            item {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Filled.Bolt,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column {
                                                Text(
                                                    text = Loc.getText("time_saved_speed"),
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Text(
                                                    text = String.format(Locale.US, "${Loc.getText("avg_speed")}: %.2fx", avgPlaybackSpeed),
                                                    fontSize = 11.sp,
                                                    color = Color.Gray
                                                )
                                            }
                                        }
                                        Text(
                                            text = formatStatsDuration(timeSavedMs),
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 15.sp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }

                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Card(
                                    modifier = Modifier.weight(1f),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(10.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = "$totalListensInPeriod",
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = Loc.getText("total_listens"),
                                            fontSize = 10.sp,
                                            color = Color.Gray,
                                            textAlign = TextAlign.Center,
                                            maxLines = 1
                                        )
                                    }
                                }

                                Card(
                                    modifier = Modifier.weight(1f),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(10.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = "$uniqueTracksCountInPeriod",
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = Loc.getText("unique_tracks_count"),
                                            fontSize = 10.sp,
                                            color = Color.Gray,
                                            textAlign = TextAlign.Center,
                                            maxLines = 1
                                        )
                                    }
                                }

                                Card(
                                    modifier = Modifier.weight(1f),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(10.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = formatStatsDuration(dailyAvgMs),
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = Loc.getText("daily_average"),
                                            fontSize = 10.sp,
                                            color = Color.Gray,
                                            textAlign = TextAlign.Center,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }

                        item {
                            ListeningActivityLineChart(
                                points = chartDataPoints,
                                statsFilter = viewModel.statsFilter,
                                totalPeriodActualMs = totalActualMs
                            )
                        }

                        item {
                            Surface(
                                onClick = { showDetailedHistoryLog = true },
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Filled.History,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text(
                                                text = Loc.getText("view_listening_log"),
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 13.sp,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            val historyCountText = if (Loc.currentLanguage == "ar") {
                                                if (rangeFiltered.isEmpty()) "لا توجد جلسات مسجلة"
                                                else if (rangeFiltered.size == 1) "جلسة استماع واحدة"
                                                else if (rangeFiltered.size == 2) "جلستا استماع"
                                                else if (rangeFiltered.size in 3..10) "${rangeFiltered.size} جلسات مسجلة"
                                                else "${rangeFiltered.size} جلسة مسجلة"
                                            } else {
                                                if (rangeFiltered.size == 1) "1 completed session" else "${rangeFiltered.size} completed sessions"
                                            }
                                            Text(
                                                text = historyCountText,
                                                fontSize = 11.sp,
                                                color = Color.Gray
                                            )
                                        }
                                    }
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // TAB 1: DAILY ACTIVITY BREAKDOWN
                1 -> {
                    if (dayGroups.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Filled.CalendarMonth,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = Loc.getText("no_daily_activity"),
                                    color = Color.Gray,
                                    fontSize = 13.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(top = 4.dp, bottom = 8.dp)
                        ) {
                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${dayGroups.size} ${Loc.getText("active_days_count")}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        TextButton(
                                            onClick = { expandedDays = dayGroups.map { it.dayKey }.toSet() },
                                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(Loc.getText("expand_all"), fontSize = 11.sp)
                                        }
                                        TextButton(
                                            onClick = { expandedDays = emptySet() },
                                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(Loc.getText("collapse_all"), fontSize = 11.sp)
                                        }
                                    }
                                }
                            }

                            items(dayGroups, key = { it.dayKey }) { day ->
                                val isExpanded = expandedDays.contains(day.dayKey)
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    expandedDays = if (isExpanded) expandedDays - day.dayKey else expandedDays + day.dayKey
                                                }
                                                .padding(12.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = if (isExpanded) Icons.Filled.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Column {
                                                    Text(
                                                        text = day.dayDisplay,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 13.sp,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                    Text(
                                                        text = "${day.tracks.size} ${Loc.getText("unique_tracks_count").lowercase()}",
                                                        fontSize = 11.sp,
                                                        color = Color.Gray
                                                    )
                                                }
                                            }
                                            Text(
                                                text = formatStatsDuration(day.totalActualMs),
                                                fontWeight = FontWeight.ExtraBold,
                                                fontSize = 14.sp,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }

                                        if (isExpanded) {
                                            HorizontalDivider(
                                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                                                modifier = Modifier.padding(horizontal = 12.dp)
                                            )
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(12.dp),
                                                verticalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                day.tracks.forEach { trackItem ->
                                                    Column(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .background(
                                                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                                                RoundedCornerShape(10.dp)
                                                            )
                                                            .padding(10.dp),
                                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                                    ) {
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.SpaceBetween,
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Row(
                                                                verticalAlignment = Alignment.CenterVertically,
                                                                modifier = Modifier.weight(1f)
                                                            ) {
                                                                val itemTrack = allTracks.find { it.id == trackItem.trackId }
                                                                val icon = if (itemTrack != null) getTrackFileIcon(itemTrack) else getTrackFileIcon(trackItem.trackName)
                                                                Icon(
                                                                    imageVector = icon,
                                                                    contentDescription = null,
                                                                    tint = MaterialTheme.colorScheme.primary,
                                                                    modifier = Modifier.size(16.dp)
                                                                )
                                                                Spacer(modifier = Modifier.width(8.dp))
                                                                Text(
                                                                    text = trackItem.trackName,
                                                                    fontWeight = FontWeight.SemiBold,
                                                                    fontSize = 12.sp,
                                                                    maxLines = 1,
                                                                    overflow = TextOverflow.Ellipsis,
                                                                    color = MaterialTheme.colorScheme.onSurface
                                                                )
                                                            }
                                                            Spacer(modifier = Modifier.width(8.dp))
                                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                                Box(
                                                                    modifier = Modifier
                                                                        .background(
                                                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                                                            RoundedCornerShape(6.dp)
                                                                        )
                                                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                                                ) {
                                                                    Text(
                                                                        text = "${formatStatsDuration(trackItem.totalActualMs)} • ${String.format(Locale.US, "%.1fx", trackItem.avgSpeed)}",
                                                                        fontSize = 10.sp,
                                                                        fontWeight = FontWeight.Bold,
                                                                        color = MaterialTheme.colorScheme.primary
                                                                    )
                                                                }
                                                                if (trackItem.playCount > 1) {
                                                                    Spacer(modifier = Modifier.width(6.dp))
                                                                    Row(
                                                                        verticalAlignment = Alignment.CenterVertically,
                                                                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                                                                    ) {
                                                                        Icon(
                                                                            imageVector = Icons.Filled.Headphones,
                                                                            contentDescription = null,
                                                                            tint = MaterialTheme.colorScheme.primary,
                                                                            modifier = Modifier.size(12.dp)
                                                                        )
                                                                        Text(
                                                                            text = "${trackItem.playCount}",
                                                                            fontSize = 11.sp,
                                                                            fontWeight = FontWeight.Bold,
                                                                            color = MaterialTheme.colorScheme.primary
                                                                        )
                                                                    }
                                                                }
                                                            }
                                                        }

                                                        if (trackItem.attachedTasks.isNotEmpty()) {
                                                            Row(
                                                                modifier = Modifier
                                                                    .fillMaxWidth()
                                                                    .padding(start = 24.dp, top = 2.dp),
                                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                                verticalAlignment = Alignment.CenterVertically
                                                            ) {
                                                                trackItem.attachedTasks.forEach { taskName ->
                                                                    Box(
                                                                        modifier = Modifier
                                                                            .background(
                                                                                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                                                                                RoundedCornerShape(6.dp)
                                                                            )
                                                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                                                    ) {
                                                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                                            Icon(
                                                                                imageVector = Icons.Filled.Bookmark,
                                                                                contentDescription = null,
                                                                                tint = MaterialTheme.colorScheme.secondary,
                                                                                modifier = Modifier.size(11.dp)
                                                                            )
                                                                            Spacer(modifier = Modifier.width(3.dp))
                                                                            Text(
                                                                                text = taskName,
                                                                                fontSize = 10.sp,
                                                                                fontWeight = FontWeight.Medium,
                                                                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                                                maxLines = 1,
                                                                                overflow = TextOverflow.Ellipsis
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
                        }
                    }
                }

                // TAB 2: TRACKS PERFORMANCE & STATS
                2 -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(top = 4.dp, bottom = 8.dp)
                    ) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box {
                                    IconButton(onClick = { showSortMenu = true }) {
                                        Icon(
                                            imageVector = Icons.Filled.Sort,
                                            contentDescription = Loc.getText("sort_by"),
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    DropdownMenu(
                                        expanded = showSortMenu,
                                        onDismissRequest = { showSortMenu = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text(Loc.getText("sort_plays_period")) },
                                            onClick = {
                                                sortBy = "period_count"
                                                showSortMenu = false
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(Loc.getText("sort_plays_all_time")) },
                                            onClick = {
                                                sortBy = "total_count"
                                                showSortMenu = false
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(Loc.getText("sort_alphabetical")) },
                                            onClick = {
                                                sortBy = "name"
                                                showSortMenu = false
                                            }
                                        )
                                    }
                                }

                                IconButton(onClick = {
                                    val sb = java.lang.StringBuilder()
                                    val statsTitle = Loc.getText("tab_track_stats")
                                    sb.append(statsTitle).append(":\n")
                                    sb.append("==============================\n")
                                    trackStats.forEach { item ->
                                        sb.append("- ").append(item.name).append(": ").append(item.periodCount).append(" plays\n")
                                    }
                                    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                    val clip = android.content.ClipData.newPlainText("Listening Stats", sb.toString())
                                    clipboard.setPrimaryClip(clip)
                                    android.widget.Toast.makeText(context, Loc.getText("export_success"), android.widget.Toast.LENGTH_SHORT).show()
                                }) {
                                    Icon(
                                        imageVector = Icons.Filled.ContentCopy,
                                        contentDescription = "Export report",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }

                        if (trackStats.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(
                                            imageVector = Icons.Filled.Analytics,
                                            contentDescription = null,
                                            modifier = Modifier.size(48.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Text(
                                            text = Loc.getText("no_track_stats_recorded"),
                                            color = Color.Gray,
                                            fontSize = 13.sp,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.padding(horizontal = 24.dp)
                                        )
                                    }
                                }
                            }
                        } else {
                            items(trackStats) { item ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(34.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                val trk = allTracks.find { it.id == item.trackId }
                                                val icon = if (trk != null) getTrackFileIcon(trk) else getTrackFileIcon(item.name)
                                                Icon(
                                                    imageVector = icon,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column {
                                                Text(
                                                    text = item.name,
                                                    fontWeight = FontWeight.SemiBold,
                                                    fontSize = 13.sp,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = "${item.totalAllTimeCount} ${Loc.getText("sort_plays_all_time").lowercase()}",
                                                    fontSize = 11.sp,
                                                    color = Color.Gray
                                                )
                                            }
                                        }

                                        Box(
                                            modifier = Modifier
                                                .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(8.dp))
                                                .padding(horizontal = 10.dp, vertical = 5.dp)
                                        ) {
                                            Text(
                                                text = "${item.periodCount} 🎧",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
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

    // Dialog for selecting multiple tasks to filter by
    if (showTaskFilterDialog) {
        var tempSelectedTaskIds by remember { mutableStateOf(selectedTaskIds) }
        var selectedLabelFilterInDialog by remember { mutableStateOf<String?>(null) }
        var taskSearchQuery by remember { mutableStateOf("") }

        val allDialogTaskLabels = remember(allTasks) {
            allTasks.flatMap { it.getLabelsList() }
                .filter { it.isNotBlank() }
                .distinctBy { it.lowercase(Locale.getDefault()) }
        }

        val filteredDialogTasks = remember(allTasks, selectedLabelFilterInDialog, taskSearchQuery) {
            val query = taskSearchQuery.trim().lowercase(Locale.getDefault())
            allTasks.filter { task ->
                val matchesLabel = if (selectedLabelFilterInDialog == null) {
                    true
                } else {
                    task.getLabelsList().any { it.equals(selectedLabelFilterInDialog, ignoreCase = true) }
                }
                val matchesSearch = if (query.isEmpty()) {
                    true
                } else {
                    task.getDisplayTitle().lowercase(Locale.getDefault()).contains(query) ||
                            task.getLabelsList().any { it.lowercase(Locale.getDefault()).contains(query) }
                }
                matchesLabel && matchesSearch
            }
        }

        Dialog(
            onDismissRequest = { showTaskFilterDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                modifier = Modifier
                    .fillMaxWidth(0.94f)
                    .fillMaxHeight(0.85f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Bookmark,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                            Column {
                                Text(
                                    text = Loc.getText("select_tasks_dialog_title"),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                if (tempSelectedTaskIds.isNotEmpty()) {
                                    Text(
                                        text = String.format(Locale.US, Loc.getText("selected_tasks_count"), tempSelectedTaskIds.size),
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                        IconButton(onClick = { showTaskFilterDialog = false }) {
                            Icon(Icons.Filled.Close, contentDescription = "Close")
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Search field
                    OutlinedTextField(
                        value = taskSearchQuery,
                        onValueChange = { taskSearchQuery = it },
                        placeholder = { Text(Loc.getText("search_tasks_hint"), fontSize = 12.sp) },
                        leadingIcon = {
                            Icon(
                                Icons.Filled.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        trailingIcon = {
                            if (taskSearchQuery.isNotEmpty()) {
                                IconButton(onClick = { taskSearchQuery = "" }) {
                                    Icon(Icons.Filled.Clear, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.12f)
                        )
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Quick Selection Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val targetIds = filteredDialogTasks.map { it.id }.toSet()
                                tempSelectedTaskIds = tempSelectedTaskIds + targetIds
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(vertical = 6.dp)
                        ) {
                            Text(Loc.getText("select_all_tasks"), fontSize = 11.sp)
                        }

                        OutlinedButton(
                            onClick = {
                                if (selectedLabelFilterInDialog == null && taskSearchQuery.isBlank()) {
                                    tempSelectedTaskIds = emptySet()
                                } else {
                                    val toRemove = filteredDialogTasks.map { it.id }.toSet()
                                    tempSelectedTaskIds = tempSelectedTaskIds - toRemove
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(vertical = 6.dp)
                        ) {
                            Text(Loc.getText("deselect_all_tasks"), fontSize = 11.sp)
                        }
                    }

                    // Label filter row
                    if (allDialogTaskLabels.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Label,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = Loc.getText("filter_tasks_by_label"),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            item {
                                FilterChip(
                                    selected = selectedLabelFilterInDialog == null,
                                    onClick = { selectedLabelFilterInDialog = null },
                                    label = { Text(Loc.getText("all_labels_filter"), fontSize = 11.sp) },
                                    modifier = Modifier.height(32.dp)
                                )
                            }
                            items(allDialogTaskLabels) { label ->
                                val isSelected = selectedLabelFilterInDialog.equals(label, ignoreCase = true)
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        selectedLabelFilterInDialog = if (isSelected) null else label
                                    },
                                    label = { Text(label, fontSize = 11.sp) },
                                    leadingIcon = if (isSelected) {
                                        {
                                            Icon(
                                                imageVector = Icons.Filled.Check,
                                                contentDescription = null,
                                                modifier = Modifier.size(12.dp)
                                            )
                                        }
                                    } else null,
                                    modifier = Modifier.height(32.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Task List
                    if (filteredDialogTasks.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (taskSearchQuery.isNotBlank()) Loc.getText("no_tasks_match_search") else Loc.getText("no_tasks_for_label"),
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(filteredDialogTasks, key = { it.id }) { task ->
                                val isChecked = task.id in tempSelectedTaskIds
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            tempSelectedTaskIds = if (isChecked) {
                                                tempSelectedTaskIds - task.id
                                            } else {
                                                tempSelectedTaskIds + task.id
                                            }
                                        },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isChecked) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                    ),
                                    border = BorderStroke(
                                        1.dp,
                                        if (isChecked) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                                        else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                                    ),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Checkbox(
                                            checked = isChecked,
                                            onCheckedChange = { checked ->
                                                tempSelectedTaskIds = if (checked) {
                                                    tempSelectedTaskIds + task.id
                                                } else {
                                                    tempSelectedTaskIds - task.id
                                                }
                                            },
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Icon(
                                            imageVector = if (task.isCompleted) Icons.Filled.CheckCircle else Icons.Filled.Bookmark,
                                            contentDescription = null,
                                            tint = if (task.isCompleted) ColorSuccess else MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = task.getDisplayTitle(),
                                            fontWeight = if (isChecked) FontWeight.Bold else FontWeight.SemiBold,
                                            fontSize = 13.5.sp,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            lineHeight = 18.sp,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                selectedTaskIds = emptySet()
                                showTaskFilterDialog = false
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(Loc.getText("all_tasks"), fontSize = 12.sp)
                        }

                        Button(
                            onClick = {
                                selectedTaskIds = tempSelectedTaskIds
                                showTaskFilterDialog = false
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(Loc.getText("apply_filter"), fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }

    // Dialog for Folder and File Tree Filter
    if (showFolderFileFilterDialog) {
        FolderFileTreeFilterDialog(
            allFolders = allFolders,
            allTracks = allTracks,
            initialSelectedFolderIds = selectedFolderIds,
            initialSelectedTrackIds = selectedFileTrackIds,
            onDismiss = { showFolderFileFilterDialog = false },
            onApply = { newFolders, newTracks ->
                selectedFolderIds = newFolders
                selectedFileTrackIds = newTracks
                showFolderFileFilterDialog = false
            }
        )
    }

    // Fullscreen Dialog overlay for the Detailed Listening Log ("سجل الاستماع") - fully synced with all active statistics filters
    if (showDetailedHistoryLog) {
        val filteredHistory = remember(rangeFiltered) {
            rangeFiltered.sortedByDescending { it.completedAt }
        }
        val isAr = Loc.currentLanguage == "ar"

        Dialog(
            onDismissRequest = { showDetailedHistoryLog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    // Header Bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.History,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = Loc.getText("listening_history_title"),
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Quick Copy/Export filtered history button
                            if (filteredHistory.isNotEmpty()) {
                                IconButton(onClick = {
                                    viewModel.copyHistoryClipboard(context, filteredHistory)
                                }) {
                                    Icon(
                                        imageVector = Icons.Filled.ContentCopy,
                                        contentDescription = "Copy filtered history",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            IconButton(onClick = { showDetailedHistoryLog = false }) {
                                Icon(Icons.Filled.Close, contentDescription = "Close")
                            }
                        }
                    }

                    // Active Filters & Summary Strip
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                val periodLabel = when (viewModel.statsFilter) {
                                    "today" -> Loc.getText("filter_today")
                                    "week" -> Loc.getText("filter_week")
                                    "month" -> Loc.getText("filter_month")
                                    "ninety" -> Loc.getText("filter_ninety")
                                    else -> Loc.getText("filter_all")
                                }
                                SuggestionChip(
                                    onClick = {},
                                    label = { Text(periodLabel, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                                    colors = SuggestionChipDefaults.suggestionChipColors(
                                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                        labelColor = MaterialTheme.colorScheme.primary
                                    ),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                                    modifier = Modifier.height(26.dp)
                                )

                                if (selectedTaskIds.isNotEmpty()) {
                                    val taskFilterText = if (isAr) "${Loc.getText("filter_by_task")}: ${selectedTaskIds.size}" else "Tasks: ${selectedTaskIds.size}"
                                    SuggestionChip(
                                        onClick = {},
                                        label = { Text(taskFilterText, fontSize = 10.sp) },
                                        colors = SuggestionChipDefaults.suggestionChipColors(
                                            containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f),
                                            labelColor = MaterialTheme.colorScheme.secondary
                                        ),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f)),
                                        modifier = Modifier.height(26.dp)
                                    )
                                }

                                if (selectedFolderIds.isNotEmpty() || selectedFileTrackIds.isNotEmpty()) {
                                    val count = selectedFolderIds.size + selectedFileTrackIds.size
                                    val folderFilterText = if (isAr) "${Loc.getText("filter_by_folder_file")}: $count" else "Files: $count"
                                    SuggestionChip(
                                        onClick = {},
                                        label = { Text(folderFilterText, fontSize = 10.sp) },
                                        colors = SuggestionChipDefaults.suggestionChipColors(
                                            containerColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f),
                                            labelColor = MaterialTheme.colorScheme.tertiary
                                        ),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.3f)),
                                        modifier = Modifier.height(26.dp)
                                    )
                                }
                            }

                            val countLabel = if (isAr) {
                                "${filteredHistory.size} ${if (filteredHistory.size in 3..10) "جلسات" else "جلسة"}"
                            } else {
                                "${filteredHistory.size} ${if (filteredHistory.size == 1) "session" else "sessions"}"
                            }
                            Text(
                                text = countLabel,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    if (filteredHistory.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Filled.HistoryToggleOff,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = if (history.isEmpty()) Loc.getText("no_history_yet") else if (isAr) "لا توجد جلسات استماع مطابقة للفلاتر المحددة" else "No listening history matches the selected filters",
                                    color = Color.Gray,
                                    fontSize = 14.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    } else {
                        val trackMap = remember(allTracks) { allTracks.associateBy { it.id } }
                        val sdf = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }

                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(filteredHistory, key = { it.id }) { logItem ->
                                val track = trackMap[logItem.trackId]
                                val title = track?.getDisplayTitle() ?: logItem.trackName
                                val formattedDate = sdf.format(Date(logItem.completedAt))

                                // Retrieve attached active task names logged at the time of playback
                                val attachedTaskNames = remember(logItem, allTasks, allTaskProgress) {
                                    val logged = logItem.getActiveTasksList()
                                    if (logged.isNotEmpty()) {
                                        logged
                                    } else if (logItem.activeTasks.isBlank()) {
                                        // Fallback for legacy records: only include tasks active at logItem.completedAt
                                        val trackId = logItem.trackId
                                        allTasks.filter { task ->
                                            val wasActiveThen = logItem.completedAt >= task.startDate && (task.endDate == null || logItem.completedAt <= task.endDate)
                                            wasActiveThen && when (task.sourceType) {
                                                "FOLDER" -> track?.parentFolderId == task.sourceId
                                                "TRACKS" -> allTaskProgress.any { it.taskId == task.id && it.trackId == trackId }
                                                else -> allTaskProgress.any { it.taskId == task.id && it.trackId == trackId }
                                            }
                                        }.map { it.getDisplayTitle() }.distinct()
                                    } else {
                                        emptyList()
                                    }
                                }

                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = title,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 13.sp,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = formattedDate,
                                                fontSize = 11.sp,
                                                color = Color.Gray
                                            )
                                            if (attachedTaskNames.isNotEmpty()) {
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = (if (isAr) "المهام: " else "Tasks: ") + attachedTaskNames.joinToString(", "),
                                                    fontSize = 10.sp,
                                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.width(8.dp))

                                        Column(horizontalAlignment = Alignment.End) {
                                            val dur = if (logItem.actualListenedMs > 0L) logItem.actualListenedMs else logItem.durationMs
                                            Text(
                                                text = formatStatsDuration(dur),
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            if (logItem.playbackSpeed > 0f && logItem.playbackSpeed != 1.0f) {
                                                Text(
                                                    text = "${String.format(Locale.US, "%.1f", logItem.playbackSpeed)}x",
                                                    fontSize = 10.sp,
                                                    color = Color.Gray
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
