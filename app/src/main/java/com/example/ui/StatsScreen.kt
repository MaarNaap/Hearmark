package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
        computeAllowedTrackIdsForTasks(selectedTaskIds, allTasks, allTracks, allTaskProgress)
    }

    // Allowed track IDs based on selected folders and files
    val allowedTrackIdsForFolderFiles = remember(selectedFolderIds, selectedFileTrackIds, allFolders, allTracks) {
        computeAllowedTrackIdsForFolderFiles(selectedFolderIds, selectedFileTrackIds, allFolders, allTracks)
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
        filterHistoryByTimeRange(history, viewModel.statsFilter)
    }

    // Combine time filter and selected entity filters
    val rangeFiltered = remember(timeFiltered, selectedTaskIds, combinedAllowedTrackIds, allowedTrackIdsForTasks, allowedTrackIdsForFolderFiles, allTasks) {
        filterHistoryByActiveFilters(
            timeFiltered = timeFiltered,
            selectedTaskIds = selectedTaskIds,
            combinedAllowedTrackIds = combinedAllowedTrackIds,
            allowedTrackIdsForTasks = allowedTrackIdsForTasks,
            allowedTrackIdsForFolderFiles = allowedTrackIdsForFolderFiles,
            allTasks = allTasks
        )
    }

    val totalListensInPeriod = rangeFiltered.size
    val uniqueTracksCountInPeriod = remember(rangeFiltered) {
        rangeFiltered.map { it.trackId }.distinct().size
    }

    // Calculate aggregated durations and speed
    val (totalContentMs, totalActualMs, avgPlaybackSpeed) = remember(rangeFiltered, allTracks) {
        computeAggregatedStats(rangeFiltered, allTracks)
    }

    val timeSavedMs = remember(totalContentMs, totalActualMs) {
        maxOf(0L, totalContentMs - totalActualMs)
    }

    // Daily breakdown groups
    val dayGroups: List<DaySummaryItem> = remember(rangeFiltered, allTracks, allTasks, allTaskProgress) {
        computeDayGroups(rangeFiltered, allTracks, allTasks, allTaskProgress)
    }

    val activeDaysCount = dayGroups.size
    val dailyAvgMs = remember(totalActualMs, activeDaysCount) {
        if (activeDaysCount > 0) totalActualMs / activeDaysCount else 0L
    }

    // Chart Data Points computed according to the active date filter
    val chartDataPoints: List<ActivityChartDataPoint> = remember(rangeFiltered, allTracks, viewModel.statsFilter, Loc.currentLanguage) {
        computeChartDataPoints(rangeFiltered, allTracks, viewModel.statsFilter, Loc.currentLanguage)
    }

    var expandedDays by remember { mutableStateOf<Set<String>>(emptySet()) }

    // Track statistics for Tab 2
    val trackStats = remember(rangeFiltered, allTracks, history, sortBy) {
        computeTrackStats(rangeFiltered, allTracks, history, sortBy)
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
                0 -> StatsOverviewTab(
                    totalActualMs = totalActualMs,
                    totalContentMs = totalContentMs,
                    timeSavedMs = timeSavedMs,
                    avgPlaybackSpeed = avgPlaybackSpeed,
                    totalListensInPeriod = totalListensInPeriod,
                    uniqueTracksCountInPeriod = uniqueTracksCountInPeriod,
                    dailyAvgMs = dailyAvgMs,
                    chartDataPoints = chartDataPoints,
                    statsFilter = viewModel.statsFilter,
                    rangeFiltered = rangeFiltered,
                    onShowDetailedHistoryLog = { showDetailedHistoryLog = true }
                )
                1 -> StatsDailyActivityTab(
                    dayGroups = dayGroups,
                    expandedDays = expandedDays,
                    onUpdateExpandedDays = { expandedDays = it },
                    allTracks = allTracks
                )
                2 -> StatsTracksPerformanceTab(
                    context = context,
                    trackStats = trackStats,
                    allTracks = allTracks,
                    showSortMenu = showSortMenu,
                    onShowSortMenuChange = { showSortMenu = it },
                    onSortByChange = { sortBy = it }
                )
            }
        }
    }

    // Dialog for selecting multiple tasks to filter by
    if (showTaskFilterDialog) {
        StatsTaskFilterDialog(
            allTasks = allTasks,
            initialSelectedTaskIds = selectedTaskIds,
            onDismiss = { showTaskFilterDialog = false },
            onApply = { newSelectedTaskIds ->
                selectedTaskIds = newSelectedTaskIds
                showTaskFilterDialog = false
            }
        )
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
        DetailedHistoryLogDialog(
            viewModel = viewModel,
            context = context,
            history = history,
            rangeFiltered = rangeFiltered,
            allTracks = allTracks,
            allTasks = allTasks,
            allTaskProgress = allTaskProgress,
            selectedTaskIds = selectedTaskIds,
            selectedFolderIds = selectedFolderIds,
            selectedFileTrackIds = selectedFileTrackIds,
            onDismiss = { showDetailedHistoryLog = false }
        )
    }
}
