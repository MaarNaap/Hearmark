package com.example.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.Task
import kotlinx.coroutines.launch
import java.util.Locale

// --- SUB-SCREEN 7: CREATE / EDIT TASK VIEW ---
@Composable
fun CreateTaskScreen(
    viewModel: AppViewModel,
    editingTask: Task? = null,
    predefinedSource: Pair<String, Long?>? = null, // type, id
    taskCreationPreselectedTrackIds: Set<Long> = emptySet(),
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val formState = rememberTaskFormState(editingTask, predefinedSource, taskCreationPreselectedTrackIds)

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

    // Auto-update title based on selection if not manually edited yet or if currently empty
    LaunchedEffect(formState.sourceType, formState.sourceId, formState.selectedManualTrackIds, allFolders, allPlaylists, allTracks) {
        if (editingTask == null && (!formState.isTitleManuallyEdited || formState.title.trim().isEmpty())) {
            val autoTitle = when (formState.sourceType) {
                "FOLDER" -> {
                    if (formState.sourceId != null) {
                        allFolders.find { it.id == formState.sourceId }?.folderName ?: ""
                    } else ""
                }
                "PLAYLIST" -> {
                    if (formState.sourceId != null) {
                        allPlaylists.find { it.id == formState.sourceId }?.name ?: ""
                    } else ""
                }
                "TRACKS" -> {
                    if (formState.selectedManualTrackIds.size == 1) {
                        val trackId = formState.selectedManualTrackIds.first()
                        allTracks.find { it.id == trackId }?.getDisplayTitle() ?: ""
                    } else if (formState.selectedManualTrackIds.size > 1) {
                        Loc.getText("group_goal_task_title")
                    } else ""
                }
                else -> ""
            }
            if (autoTitle.isNotEmpty()) {
                formState.title = autoTitle
            }
        }
    }

    // Smart auto-population of initial title and track selection when database loads
    LaunchedEffect(allFolders, allPlaylists, allTracks, predefinedSource, editingTask, taskCreationPreselectedTrackIds) {
        if (editingTask != null) {
            formState.title = editingTask.getBaseTitle()
            formState.sourceType = editingTask.sourceType
            formState.sourceId = editingTask.sourceId
            formState.targetType = editingTask.targetType
            formState.targetValue = editingTask.targetValue
            formState.reminderTime = editingTask.reminderTime
            formState.enableDailyGoal = editingTask.dailyTargetValue != null && editingTask.dailyTargetValue > 0
            formState.dailyTargetValue = editingTask.dailyTargetValue ?: 1

            if (editingTask.sourceType == "TRACKS") {
                val progress = viewModel.getProgressForTask(editingTask.id)
                formState.selectedManualTrackIds = progress.map { it.trackId }.toSet()
            }
        } else {
            if (predefinedSource != null) {
                val normType = when (predefinedSource.first) {
                    "FOLDER", "FOLDERS" -> "FOLDER"
                    "PLAYLIST" -> "PLAYLIST"
                    "TRACKS" -> "TRACKS"
                    else -> predefinedSource.first
                }
                formState.sourceType = normType
                if (predefinedSource.second != null) {
                    formState.sourceId = predefinedSource.second
                }
            }
            if (formState.title.isEmpty()) {
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
                    formState.title = initialTitle
                }
            }
        }

        if (editingTask == null && formState.selectedManualTrackIds.isEmpty()) {
            if (predefinedSource?.first == "TRACKS") {
                if (predefinedSource.second != null) {
                    formState.selectedManualTrackIds = setOf(predefinedSource.second!!)
                } else if (taskCreationPreselectedTrackIds.isNotEmpty()) {
                    formState.selectedManualTrackIds = taskCreationPreselectedTrackIds
                }
            }
        }
    }

    fun handleSave() {
        val finalTitle = formState.resolveFinalTitle(allFolders, allPlaylists, allTracks)
        if (editingTask != null) {
            viewModel.editTask(
                taskId = editingTask.id,
                title = finalTitle,
                targetType = formState.targetType,
                targetValue = formState.targetValue,
                scheduledDays = formState.scheduledDays.joinToString(","),
                reminderTime = formState.reminderTime,
                startDate = editingTask.startDate,
                endDate = editingTask.endDate,
                sourceType = formState.sourceType,
                sourceId = formState.sourceId,
                manualTrackIds = formState.selectedManualTrackIds.toList(),
                customThreshold = if (formState.useCustomThreshold) formState.taskThresholdValue.toInt() else null,
                labels = formState.taskLabels.joinToString(","),
                dailyTargetValue = if (formState.enableDailyGoal && formState.dailyTargetValue > 0) formState.dailyTargetValue else null
            )
        } else {
            viewModel.createTask(
                title = finalTitle,
                sourceType = formState.sourceType,
                sourceId = formState.sourceId,
                targetType = formState.targetType,
                targetValue = formState.targetValue,
                scheduledDays = formState.scheduledDays.joinToString(","),
                reminderTime = formState.reminderTime,
                startDate = System.currentTimeMillis(),
                endDate = null,
                manualTrackIds = formState.selectedManualTrackIds.toList(),
                customThreshold = if (formState.useCustomThreshold) formState.taskThresholdValue.toInt() else null,
                labels = formState.taskLabels.joinToString(","),
                dailyTargetValue = if (formState.enableDailyGoal && formState.dailyTargetValue > 0) formState.dailyTargetValue else null
            )
        }
        onDismiss()
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Top Header & Stepper pinned at the top
        TaskStepper(
            isEditing = editingTask != null,
            currentStep = currentStep,
            onStepClicked = { step ->
                coroutineScope.launch {
                    pagerState.animateScrollToPage(step - 1)
                }
            }
        )

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
                    0 -> TaskSourceStep(
                        formState = formState,
                        allFolders = allFolders,
                        allPlaylists = allPlaylists,
                        allTracks = allTracks
                    )
                    1 -> TaskThresholdLabelsStep(
                        formState = formState,
                        availableExistingLabels = availableExistingLabels
                    )
                    2 -> TaskScheduleStep(
                        formState = formState,
                        context = context
                    )
                    3 -> TaskSummaryStep(
                        formState = formState,
                        allFolders = allFolders,
                        allPlaylists = allPlaylists,
                        allTracks = allTracks
                    )
                }
                Spacer(modifier = Modifier.height(20.dp))
            }
        }

        // Navigation Footer controls inside stepper
        TaskFooterNav(
            currentStep = currentStep,
            isEditing = editingTask != null,
            onPrevOrCancel = {
                if (currentStep > 1) {
                    coroutineScope.launch {
                        pagerState.animateScrollToPage(pagerState.currentPage - 1)
                    }
                } else {
                    onDismiss()
                }
            },
            onQuickSave = { handleSave() },
            onNext = {
                coroutineScope.launch {
                    pagerState.animateScrollToPage(pagerState.currentPage + 1)
                }
            },
            onSave = { handleSave() }
        )
    }
}
