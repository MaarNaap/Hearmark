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
