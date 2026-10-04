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
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.automirrored.filled.Sort
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
                                imageVector = Icons.AutoMirrored.Filled.Sort,
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
                                    imageVector = Icons.AutoMirrored.Filled.Label,
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
                            imageVector = Icons.AutoMirrored.Filled.Label,
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
                        items(filteredAndSortedTasks, key = { it.first.id }) { (task, overallPercent, taskTracks) ->
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
