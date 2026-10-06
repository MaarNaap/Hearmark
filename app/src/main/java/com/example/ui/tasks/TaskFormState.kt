package com.example.ui

import androidx.compose.runtime.*
import com.example.data.AudioTrack
import com.example.data.Folder
import com.example.data.Playlist
import com.example.data.Task

@Stable
class TaskFormState(
    val editingTask: Task?,
    val predefinedSource: Pair<String, Long?>?,
    val taskCreationPreselectedTrackIds: Set<Long>
) {
    var title by mutableStateOf(editingTask?.getBaseTitle() ?: "")
    var isTitleManuallyEdited by mutableStateOf(editingTask != null)

    val normalizedPredefinedType = when (predefinedSource?.first) {
        "FOLDER", "FOLDERS" -> "FOLDER"
        "PLAYLIST" -> "PLAYLIST"
        "TRACKS" -> "TRACKS"
        else -> predefinedSource?.first
    }

    var sourceType by mutableStateOf(editingTask?.sourceType ?: normalizedPredefinedType ?: "FOLDER")
    var sourceId by mutableStateOf<Long?>(editingTask?.sourceId ?: predefinedSource?.second)

    var targetType by mutableStateOf(editingTask?.targetType ?: "PLAY_COUNT")
    var targetValue by mutableIntStateOf(editingTask?.targetValue ?: 3)

    var useCustomThreshold by mutableStateOf(editingTask?.customThreshold != null)
    var taskThresholdValue by mutableFloatStateOf((editingTask?.customThreshold ?: 90).toFloat())

    val initialDays: Set<String> = if (!editingTask?.scheduledDays.isNullOrEmpty()) {
        editingTask.scheduledDays.split(",").filter { it.isNotEmpty() }.toSet()
    } else {
        setOf("SUNDAY", "MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY")
    }
    var scheduledDays by mutableStateOf(initialDays)
    var reminderTime by mutableStateOf(editingTask?.reminderTime ?: "09:00 AM")
    var enableDailyGoal by mutableStateOf(editingTask?.dailyTargetValue != null && editingTask.dailyTargetValue > 0)
    var dailyTargetValue by mutableIntStateOf(editingTask?.dailyTargetValue ?: 1)

    var taskLabels by mutableStateOf(editingTask?.getLabelsList() ?: emptyList())
    var newLabelInput by mutableStateOf("")

    var selectedManualTrackIds by mutableStateOf(emptySet<Long>())

    fun resolveRawTitle(
        allFolders: List<Folder>,
        allPlaylists: List<Playlist>,
        allTracks: List<AudioTrack>
    ): String {
        return title.trim().ifEmpty {
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
            autoTitle ?: Loc.getText("unnamed_task")
        }
    }

    fun resolveFinalTitle(
        allFolders: List<Folder>,
        allPlaylists: List<Playlist>,
        allTracks: List<AudioTrack>
    ): String {
        val raw = resolveRawTitle(allFolders, allPlaylists, allTracks)
        return Task.buildCombinedTitle(raw, taskLabels.joinToString(","))
    }
}

@Composable
fun rememberTaskFormState(
    editingTask: Task? = null,
    predefinedSource: Pair<String, Long?>? = null,
    taskCreationPreselectedTrackIds: Set<Long> = emptySet()
): TaskFormState {
    return remember(editingTask, predefinedSource, taskCreationPreselectedTrackIds) {
        TaskFormState(editingTask, predefinedSource, taskCreationPreselectedTrackIds)
    }
}
