package com.example.ui

import com.example.data.*
import java.text.SimpleDateFormat
import java.util.*

data class TrackStatItem(
    val trackId: Long,
    val name: String,
    val periodCount: Int,
    val totalAllTimeCount: Int
)

data class DayTrackItem(
    val trackId: Long,
    val trackName: String,
    val playCount: Int,
    val durationMs: Long,
    val totalActualMs: Long,
    val avgSpeed: Float,
    val attachedTasks: List<String>
)

data class DaySummaryItem(
    val dayKey: String,
    val dayDisplay: String,
    val timestamp: Long,
    val totalActualMs: Long,
    val totalContentMs: Long,
    val tracks: List<DayTrackItem>
)

fun formatStatsDuration(ms: Long): String {
    if (ms <= 0L) return Loc.getText("stats_duration_zero")
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    val formatted = when {
        hours > 0 && minutes > 0 -> Loc.getFormattedText("stats_duration_hm", hours, minutes)
        hours > 0 -> Loc.getFormattedText("stats_duration_h", hours)
        minutes > 0 -> Loc.getFormattedText("stats_duration_m", minutes)
        else -> Loc.getFormattedText("stats_duration_s", seconds)
    }
    return formatted.toWesternDigits()
}

data class ActivityChartDataPoint(
    val timestamp: Long,
    val axisLabel: String,
    val showAxisLabel: Boolean,
    val fullTitle: String,
    val actualDurationMs: Long,
    val contentDurationMs: Long,
    val playCount: Int
)

// --- FOLDER & FILE TREE FILTER DATA STRUCTURES & HELPERS ---

sealed class FolderTreeItem {
    data class FolderRow(
        val node: FolderTreeNode,
        val depth: Int,
        val isExpanded: Boolean,
        val hasChildren: Boolean,
        val directTracksCount: Int,
        val totalTracksCount: Int,
        val allSubfolderIds: Set<Long>,
        val allRecursiveTrackIds: Set<Long>
    ) : FolderTreeItem()

    data class TrackRow(
        val track: AudioTrack,
        val depth: Int
    ) : FolderTreeItem()

    data class IndependentHeader(
        val count: Int,
        val isExpanded: Boolean
    ) : FolderTreeItem()
}

fun getAllSubfolderIds(parentFolderId: Long, allFolders: List<Folder>): Set<Long> {
    val set = mutableSetOf(parentFolderId)
    val children = allFolders.filter { it.parentFolderId == parentFolderId }
    for (child in children) {
        set.addAll(getAllSubfolderIds(child.id, allFolders))
    }
    return set
}

fun flattenFolderFileTree(
    nodes: List<FolderTreeNode>,
    allFolders: List<Folder>,
    allTracks: List<AudioTrack>,
    expandedFolderIds: Set<Long>,
    searchQuery: String,
    independentExpanded: Boolean
): List<FolderTreeItem> {
    val result = mutableListOf<FolderTreeItem>()
    val query = searchQuery.trim().lowercase()

    fun processNode(node: FolderTreeNode, depth: Int) {
        val allSubIds = getAllSubfolderIds(node.folder.id, allFolders)
        val recursiveTracks = allTracks.filter { it.parentFolderId in allSubIds }
        val directTracks = allTracks.filter { it.parentFolderId == node.folder.id }
        val subfolderCount = node.children.size

        val folderMatches = query.isEmpty() || node.folder.folderName.lowercase().contains(query)
        val matchingTracks = if (query.isEmpty()) directTracks else directTracks.filter { it.fileName.lowercase().contains(query) }
        val anyChildMatches = if (query.isEmpty()) false else recursiveTracks.any { it.fileName.lowercase().contains(query) } || node.children.any { c ->
            allFolders.any { it.parentFolderId == c.folder.id && it.folderName.lowercase().contains(query) } || c.folder.folderName.lowercase().contains(query)
        }

        if (query.isEmpty() || folderMatches || matchingTracks.isNotEmpty() || anyChildMatches) {
            val isExpanded = if (query.isNotEmpty()) true else expandedFolderIds.contains(node.folder.id)
            val hasChildren = subfolderCount > 0 || directTracks.isNotEmpty()

            result.add(
                FolderTreeItem.FolderRow(
                    node = node,
                    depth = depth,
                    isExpanded = isExpanded,
                    hasChildren = hasChildren,
                    directTracksCount = directTracks.size,
                    totalTracksCount = recursiveTracks.size,
                    allSubfolderIds = allSubIds,
                    allRecursiveTrackIds = recursiveTracks.map { it.id }.toSet()
                )
            )

            if (isExpanded) {
                node.children.forEach { child ->
                    processNode(child, depth + 1)
                }
                val tracksToShow = if (query.isNotEmpty() && !folderMatches) matchingTracks else directTracks
                tracksToShow.forEach { track ->
                    result.add(
                        FolderTreeItem.TrackRow(
                            track = track,
                            depth = depth + 1
                        )
                    )
                }
            }
        }
    }

    nodes.forEach { processNode(it, 0) }

    val folderIds = allFolders.map { it.id }.toSet()
    val independentTracks = allTracks.filter { it.parentFolderId == null || !folderIds.contains(it.parentFolderId) }
    if (independentTracks.isNotEmpty()) {
        val matchingIndTracks = if (query.isEmpty()) independentTracks else independentTracks.filter { it.fileName.lowercase().contains(query) }
        if (matchingIndTracks.isNotEmpty()) {
            val isIndExpanded = if (query.isNotEmpty()) true else independentExpanded
            result.add(FolderTreeItem.IndependentHeader(count = independentTracks.size, isExpanded = isIndExpanded))
            if (isIndExpanded) {
                matchingIndTracks.forEach { track ->
                    result.add(FolderTreeItem.TrackRow(track = track, depth = 1))
                }
            }
        }
    }

    return result
}

fun computeAllowedTrackIdsForTasks(
    selectedTaskIds: Set<Long>,
    allTasks: List<Task>,
    allTracks: List<AudioTrack>,
    allTaskProgress: List<TaskTrackProgress>
): Set<Long>? {
    if (selectedTaskIds.isEmpty()) return null
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
    return ids
}

fun computeAllowedTrackIdsForFolderFiles(
    selectedFolderIds: Set<Long>,
    selectedFileTrackIds: Set<Long>,
    allFolders: List<Folder>,
    allTracks: List<AudioTrack>
): Set<Long>? {
    if (selectedFolderIds.isEmpty() && selectedFileTrackIds.isEmpty()) return null
    val ids = mutableSetOf<Long>()
    ids.addAll(selectedFileTrackIds)
    if (selectedFolderIds.isNotEmpty()) {
        val allTargetFolderIds = mutableSetOf<Long>()
        selectedFolderIds.forEach { fId ->
            allTargetFolderIds.addAll(getAllSubfolderIds(fId, allFolders))
        }
        allTracks.filter { it.parentFolderId in allTargetFolderIds }.forEach { ids.add(it.id) }
    }
    return ids
}

fun filterHistoryByTimeRange(
    history: List<PlaybackHistory>,
    statsFilter: String
): List<PlaybackHistory> {
    return when (statsFilter) {
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
        else -> history
    }
}

fun filterHistoryByActiveFilters(
    timeFiltered: List<PlaybackHistory>,
    selectedTaskIds: Set<Long>,
    combinedAllowedTrackIds: Set<Long>?,
    allowedTrackIdsForTasks: Set<Long>?,
    allowedTrackIdsForFolderFiles: Set<Long>?,
    allTasks: List<Task>
): List<PlaybackHistory> {
    return if (selectedTaskIds.isEmpty()) {
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

fun computeAggregatedStats(
    rangeFiltered: List<PlaybackHistory>,
    allTracks: List<AudioTrack>
): Triple<Long, Long, Float> {
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
    return Triple(contentSum, actualSum, avgSpd)
}

fun computeDayGroups(
    rangeFiltered: List<PlaybackHistory>,
    allTracks: List<AudioTrack>,
    allTasks: List<Task>,
    allTaskProgress: List<TaskTrackProgress>
): List<DaySummaryItem> {
    val trackMap = allTracks.associateBy { it.id }
    val sdfKey = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    val sdfDisplay = SimpleDateFormat("EEEE, d MMMM", Locale(Loc.currentLanguage))

    return rangeFiltered.groupBy { sdfKey.format(Date(it.completedAt)) }
        .map { (dayKey, records) ->
            val dayTimestamp = records.first().completedAt
            val dayDisplay = sdfDisplay.format(Date(dayTimestamp)).toWesternDigits()
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
                        val rTrackId = r.trackId
                        allTasks.filter { task ->
                            val wasActiveThen = r.completedAt >= task.startDate && (task.endDate == null || r.completedAt <= task.endDate)
                            wasActiveThen && when (task.sourceType) {
                                "FOLDER" -> track?.parentFolderId == task.sourceId
                                "TRACKS" -> allTaskProgress.any { it.taskId == task.id && it.trackId == rTrackId }
                                else -> allTaskProgress.any { it.taskId == task.id && it.trackId == rTrackId }
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

fun computeChartDataPoints(
    rangeFiltered: List<PlaybackHistory>,
    allTracks: List<AudioTrack>,
    statsFilter: String,
    currentLanguage: String
): List<ActivityChartDataPoint> {
    val trackMap = allTracks.associateBy { it.id }
    val locale = Locale(currentLanguage)

    return when (statsFilter) {
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
                    axisLabel = sdfAxis.format(Date(startMs)).toWesternDigits(),
                    showAxisLabel = true,
                    fullTitle = sdfFull.format(Date(startMs)).toWesternDigits(),
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
                    axisLabel = sdfAxis.format(Date(startMs)).toWesternDigits(),
                    showAxisLabel = showLabel,
                    fullTitle = sdfFull.format(Date(startMs)).toWesternDigits(),
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
                    axisLabel = sdfAxis.format(Date(startMs)).toWesternDigits(),
                    showAxisLabel = showLabel,
                    fullTitle = sdfFull.format(Date(startMs)).toWesternDigits(),
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
                        axisLabel = sdfAxis.format(Date(startMs)).toWesternDigits(),
                        showAxisLabel = true,
                        fullTitle = sdfFull.format(Date(startMs)).toWesternDigits(),
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
                        axisLabel = sdfAxis.format(Date(startMs)).toWesternDigits(),
                        showAxisLabel = showLabel,
                        fullTitle = sdfFull.format(Date(startMs)).toWesternDigits(),
                        actualDurationMs = actualMs,
                        contentDurationMs = contentMs,
                        playCount = dayRecords.size
                    )
                }
            }
        }
    }
}

fun computeTrackStats(
    rangeFiltered: List<PlaybackHistory>,
    allTracks: List<AudioTrack>,
    history: List<PlaybackHistory>,
    sortBy: String
): List<TrackStatItem> {
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
    return when (sortBy) {
        "total_count" -> mapped.sortedByDescending { it.totalAllTimeCount }
        "name" -> mapped.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
        else -> mapped.sortedByDescending { it.periodCount }
    }
}

