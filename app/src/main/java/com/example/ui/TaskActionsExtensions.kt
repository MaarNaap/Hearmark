package com.example.ui

import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// --- TASK CONTROL EXTENSIONS ---
fun AppViewModel.createTask(
    title: String,
    sourceType: String,
    sourceId: Long?,
    targetType: String,
    targetValue: Int,
    scheduledDays: String,
    reminderTime: String,
    startDate: Long,
    endDate: Long?,
    manualTrackIds: List<Long> = emptyList(),
    customThreshold: Int? = null,
    labels: String = "",
    dailyTargetValue: Int? = null
) {
    viewModelScope.launch(Dispatchers.IO) {
        val finalTitle = Task.buildCombinedTitle(title, labels)
        val taskId = repository.addTask(
            title = finalTitle,
            sourceType = sourceType,
            sourceId = sourceId,
            targetType = targetType,
            targetValue = targetValue,
            scheduledDays = scheduledDays,
            reminderTime = reminderTime,
            startDate = startDate,
            endDate = endDate,
            customThreshold = customThreshold,
            labels = labels,
            dailyTargetValue = dailyTargetValue
        )

        // If manual, setup progresses
        if (sourceType == "TRACKS") {
            manualTrackIds.forEach { trackId ->
                repository.dao.insertTaskProgress(TaskTrackProgress(taskId, trackId))
            }
        }

        val createdTask = repository.getTaskById(taskId)
        // Schedule daily alarm reminder
        com.example.receiver.AlarmReceiver.scheduleAlarm(
            getApplication(),
            taskId,
            createdTask?.getDisplayTitle() ?: finalTitle,
            scheduledDays,
            reminderTime
        )
    }
}

fun AppViewModel.editTask(
    taskId: Long,
    title: String,
    targetType: String,
    targetValue: Int,
    scheduledDays: String,
    reminderTime: String,
    startDate: Long,
    endDate: Long?,
    sourceType: String,
    sourceId: Long?,
    manualTrackIds: List<Long> = emptyList(),
    customThreshold: Int? = null,
    labels: String = "",
    dailyTargetValue: Int? = null
) {
    viewModelScope.launch(Dispatchers.IO) {
        val previousTask = repository.getTaskById(taskId) ?: return@launch
        val finalTitle = Task.buildCombinedTitle(title, labels)
        val updatedTask = previousTask.copy(
            title = finalTitle,
            targetType = targetType,
            targetValue = targetValue,
            scheduledDays = scheduledDays,
            reminderTime = reminderTime,
            startDate = startDate,
            endDate = endDate,
            sourceType = sourceType,
            sourceId = sourceId,
            customThreshold = customThreshold,
            labels = labels,
            dailyTargetValue = dailyTargetValue
        )
        repository.updateTask(updatedTask)

        // Preserve old track progress item states if any of them remain in the newly associated tracks
        val existingProgressMap = repository.getProgressForTask(taskId).associateBy { it.trackId }

        val newTrackIds = when (sourceType) {
            "TRACKS" -> manualTrackIds
            "FOLDER" -> {
                if (sourceId != null) {
                    repository.getTracksForFolderRecursive(sourceId).map { it.id }
                } else emptyList()
            }
            "PLAYLIST" -> {
                if (sourceId != null) {
                    repository.getTracksForPlaylist(sourceId).map { it.id }
                } else emptyList()
            }
            else -> emptyList()
        }

        val newTrackIdsSet = newTrackIds.toSet()

        // 1. Delete progress entries for tracks that have been removed
        existingProgressMap.keys.forEach { oldTrackId ->
            if (oldTrackId !in newTrackIdsSet) {
                repository.deleteSingleTaskProgress(taskId, oldTrackId)
            }
        }

        // 2. Add progress entries for newly added tracks, but keep existing progress entries completely untouched!
        newTrackIds.forEach { trackId ->
            if (trackId !in existingProgressMap) {
                repository.insertTaskProgress(TaskTrackProgress(taskId, trackId))
            }
        }

        // 3. Immediately evaluate updated progress states matching the new targetType and targetValue
        val refreshedProgressList = repository.getProgressForTask(taskId)
        var allTracksCompleted = refreshedProgressList.isNotEmpty()
        refreshedProgressList.forEach { progress ->
            val isDone = if (targetType == "PLAY_COUNT") {
                progress.completedPlayCount >= targetValue
            } else {
                progress.getDaysList().size >= targetValue
            }
            if (progress.isTrackCompleted != isDone) {
                val updatedPrg = progress.copy(isTrackCompleted = isDone)
                repository.insertTaskProgress(updatedPrg)
            }
            if (!isDone) {
                allTracksCompleted = false
            }
        }

        // 4. Update task completion status
        val finalTask = updatedTask.copy(
            isCompleted = allTracksCompleted,
            status = if (allTracksCompleted) "COMPLETED" else "ACTIVE"
        )
        repository.updateTask(finalTask)

        // Reschedule alarm if the task is active and not completed
        if (!allTracksCompleted) {
            com.example.receiver.AlarmReceiver.scheduleAlarm(
                getApplication(),
                taskId,
                finalTask.getDisplayTitle(),
                scheduledDays,
                reminderTime
            )
        } else {
            com.example.receiver.AlarmReceiver.cancelAlarm(getApplication(), taskId)
        }
    }
}

fun AppViewModel.deleteTask(id: Long) {
    viewModelScope.launch(Dispatchers.IO) {
        repository.deleteTask(id)
        com.example.receiver.AlarmReceiver.cancelAlarm(getApplication(), id)
    }
}

fun AppViewModel.resetTaskTrackProgress(taskId: Long, trackId: Long) {
    viewModelScope.launch(Dispatchers.IO) {
        repository.insertTaskProgress(TaskTrackProgress(taskId, trackId, completedPlayCount = 0, completedDays = "", isTrackCompleted = false))
    }
}

fun AppViewModel.reactivateTask(id: Long) {
    viewModelScope.launch(Dispatchers.IO) {
        repository.reactivateTask(id)
        val task = repository.getTaskById(id)
        if (task != null) {
            com.example.receiver.AlarmReceiver.scheduleAlarm(
                getApplication(),
                task.id,
                task.getDisplayTitle(),
                task.scheduledDays,
                task.reminderTime
            )
        }
    }
}

fun AppViewModel.archiveTask(id: Long) {
    viewModelScope.launch(Dispatchers.IO) {
        val task = repository.getTaskById(id) ?: return@launch
        repository.updateTask(task.copy(isCompleted = true, status = "COMPLETED"))
        com.example.receiver.AlarmReceiver.cancelAlarm(getApplication(), id)
    }
}

fun AppViewModel.duplicateTask(id: Long, onCompleted: (() -> Unit)? = null) {
    viewModelScope.launch(Dispatchers.IO) {
        val originalTask = repository.getTaskById(id) ?: return@launch
        val now = System.currentTimeMillis()
        val newTaskId = repository.addTask(
            title = originalTask.title,
            sourceType = originalTask.sourceType,
            sourceId = originalTask.sourceId,
            targetType = originalTask.targetType,
            targetValue = originalTask.targetValue,
            scheduledDays = originalTask.scheduledDays,
            reminderTime = originalTask.reminderTime,
            startDate = now,
            endDate = originalTask.endDate,
            customThreshold = originalTask.customThreshold,
            labels = originalTask.labels,
            dailyTargetValue = originalTask.dailyTargetValue
        )

        // If it's a manual TRACKS task, copy the track IDs from existing progress
        if (originalTask.sourceType == "TRACKS") {
            val existingProgress = repository.getProgressForTask(originalTask.id)
            for (progress in existingProgress) {
                repository.dao.insertTaskProgress(
                    TaskTrackProgress(
                        taskId = newTaskId,
                        trackId = progress.trackId,
                        completedPlayCount = 0,
                        completedDays = "",
                        isTrackCompleted = false
                    )
                )
            }
        }

        val createdTask = repository.getTaskById(newTaskId)
        // Schedule daily alarm reminder for the new task
        com.example.receiver.AlarmReceiver.scheduleAlarm(
            getApplication(),
            newTaskId,
            createdTask?.getDisplayTitle() ?: originalTask.getDisplayTitle(),
            originalTask.scheduledDays,
            originalTask.reminderTime
        )

        withContext(Dispatchers.Main) {
            onCompleted?.invoke()
        }
    }
}

fun AppViewModel.deleteMultipleFolders(ids: Set<Long>) {
    viewModelScope.launch(Dispatchers.IO) {
        for (id in ids) {
            repository.deleteFolder(id)
        }
    }
}

fun AppViewModel.deleteMultiplePlaylists(ids: Set<Long>) {
    viewModelScope.launch(Dispatchers.IO) {
        for (id in ids) {
            val tracks = repository.getTracksForPlaylist(id)
            for (track in tracks) {
                if (track.parentFolderId == null) {
                    repository.updateTrack(track.copy(isIndependent = true))
                }
            }
            repository.deletePlaylist(id)
        }
    }
}

fun AppViewModel.deleteMultipleTasks(ids: Set<Long>) {
    viewModelScope.launch(Dispatchers.IO) {
        for (id in ids) {
            repository.deleteTask(id)
            com.example.receiver.AlarmReceiver.cancelAlarm(getApplication(), id)
        }
    }
}

fun AppViewModel.bulkAddFoldersToTasks(folderIds: Set<Long>) {
    viewModelScope.launch(Dispatchers.IO) {
        for (id in folderIds) {
            val folder = repository.getFolderById(id) ?: continue
            val tracks = repository.getTracksForFolderRecursive(id)
            val titleString = folder.folderName
            
            val taskId = repository.addTask(
                title = titleString,
                sourceType = "FOLDER",
                sourceId = id,
                targetType = "PLAY_COUNT",
                targetValue = 3,
                scheduledDays = "SUNDAY,MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY,SATURDAY",
                reminderTime = "09:00 AM",
                startDate = System.currentTimeMillis(),
                endDate = null
            )

            for (track in tracks) {
                repository.dao.insertTaskProgress(
                    TaskTrackProgress(
                        taskId = taskId,
                        trackId = track.id,
                        completedPlayCount = 0,
                        completedDays = "",
                        isTrackCompleted = false
                    )
                )
            }

            com.example.receiver.AlarmReceiver.scheduleAlarm(
                getApplication(),
                taskId,
                titleString,
                "SUNDAY,MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY,SATURDAY",
                "09:00 AM"
            )
        }
    }
}

fun AppViewModel.bulkAddPlaylistsToTasks(playlistIds: Set<Long>) {
    viewModelScope.launch(Dispatchers.IO) {
        for (id in playlistIds) {
            val playlist = repository.getPlaylistById(id) ?: continue
            val tracks = repository.getTracksForPlaylist(id)
            val titleString = playlist.name
            
            val taskId = repository.addTask(
                title = titleString,
                sourceType = "PLAYLIST",
                sourceId = id,
                targetType = "PLAY_COUNT",
                targetValue = 3,
                scheduledDays = "SUNDAY,MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY,SATURDAY",
                reminderTime = "09:00 AM",
                startDate = System.currentTimeMillis(),
                endDate = null
            )

            for (track in tracks) {
                repository.dao.insertTaskProgress(
                    TaskTrackProgress(
                        taskId = taskId,
                        trackId = track.id,
                        completedPlayCount = 0,
                        completedDays = "",
                        isTrackCompleted = false
                    )
                )
            }

            com.example.receiver.AlarmReceiver.scheduleAlarm(
                getApplication(),
                taskId,
                titleString,
                "SUNDAY,MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY,SATURDAY",
                "09:00 AM"
            )
        }
    }
}

suspend fun AppViewModel.getActiveTasksForTrack(trackId: Long): List<Task> {
    return repository.getActiveTasksForTrack(trackId)
}

suspend fun AppViewModel.getAllTasksForTrack(trackId: Long): List<Task> {
    return repository.getAllTasksForTrack(trackId)
}

suspend fun AppViewModel.getProgressForTask(taskId: Long): List<TaskTrackProgress> {
    return repository.getProgressForTask(taskId)
}

fun AppViewModel.quickAddTaskForTracks(tracks: List<AudioTrack>, title: String, playsTarget: Int) {
    viewModelScope.launch(Dispatchers.IO) {
        val taskId = repository.addTask(
            title = title,
            sourceType = "TRACKS",
            sourceId = null,
            targetType = "PLAY_COUNT",
            targetValue = playsTarget,
            scheduledDays = "SUNDAY,MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY,SATURDAY",
            reminderTime = "09:00 AM",
            startDate = System.currentTimeMillis(),
            endDate = null
        )
        tracks.forEach { track ->
            repository.dao.insertTaskProgress(TaskTrackProgress(taskId, track.id))
        }
    }
}
