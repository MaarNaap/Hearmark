package com.example.player

import android.util.Log
import com.example.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal object PlaybackTaskHistoryController {

    // =========================================================================
    // @LOCKED: Session History Sync & Task Completion Engine - STRICT FREEZE
    // DO NOT MODIFY OR REFACTOR THIS BLOCK WITHOUT EXPLICIT PERMISSION IN PROMPT
    // =========================================================================
    fun syncCurrentSessionHistory(isFinishing: Boolean = false) {
        with(AudioPlayerManager) {
            val track = currentTrackValue ?: return
            accumulateActiveListeningTime()
            accumulatePracticePauseTime()

            // Total actual listening duration for THIS PLAY of the track across all sessions
            val accumulatedSoFar = trackAccumulatedListeningMsMap[track.id] ?: track.currentPlayActualListeningMs
            val totalActualMs = accumulatedSoFar + sessionActualListeningMs
            val shouldRecord = isThresholdTriggeredForCurrentSession
            if (!shouldRecord) {
                if (isFinishing) {
                    // When finishing an uncompleted session (e.g. paused at 20% or 80%),
                    // flush session listening time into the track so it is retained for future sessions!
                    persistCurrentPlayListeningTime()
                    sessionActualListeningMs = 0L
                    currentSessionHistoryId = null
                }
                return
            }

            val historyId = currentSessionHistoryId ?: 0L
            val recordedActual = if (totalActualMs > 0L) totalActualMs else track.duration
            val speed = _playbackSpeed.value

            coroutineScope.launch(Dispatchers.IO) {
                try {
                    val activeTasksAtThatTime = repository?.getActiveTasksForTrackDirect(track) ?: emptyList()
                    val activeTasksJson = if (activeTasksAtThatTime.isNotEmpty()) {
                        val arr = org.json.JSONArray()
                        activeTasksAtThatTime.forEach { t ->
                            val obj = org.json.JSONObject()
                            obj.put("id", t.id)
                            obj.put("title", t.getDisplayTitle())
                            arr.put(obj)
                        }
                        arr.toString()
                    } else {
                        ""
                    }

                    val history = PlaybackHistory(
                        id = historyId,
                        trackId = track.id,
                        trackName = track.fileName,
                        completedAt = System.currentTimeMillis(),
                        durationMs = track.duration,
                        playbackSpeed = speed,
                        actualListenedMs = recordedActual,
                        activeTasks = activeTasksJson
                    )
                    val newId = repository?.insertPlaybackHistory(history) ?: 0L
                    if (currentSessionHistoryId == null && newId > 0L) {
                        currentSessionHistoryId = newId
                    }
                    com.example.util.AutoBackupManager.saveAutoBackupFromRepository(appContext, repository)
                } catch (e: Exception) {
                    Log.e(TAG, "syncCurrentSessionHistory error: ${e.message}")
                } finally {
                    if (isFinishing) {
                        currentSessionHistoryId = null
                        sessionActualListeningMs = 0L
                    }
                }
            }
        }
    }

    fun checkAndTriggerTaskSpecificProgress(track: AudioTrack, progressPercent: Int) {
        with(AudioPlayerManager) {
            val repo = repository ?: return
            coroutineScope.launch(Dispatchers.IO) {
                val activeTasksList = repo.getAllTasksDirect()
                val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

                for (task in activeTasksList) {
                    if (task.isCompleted) continue
                    val customThreshold = task.customThreshold ?: continue
                    if (progressPercent >= customThreshold) {
                        if (completedTaskIdsForCurrentSession.contains(task.id)) continue

                        val taskProgresses = repo.getProgressForTask(task.id)
                        val matchingProgress = taskProgresses.find { it.trackId == track.id }
                        if (matchingProgress != null) {
                            completedTaskIdsForCurrentSession.add(task.id)
                            repo.incrementDailyPlayCount(task.id, todayStr)

                            var updatedProgress = matchingProgress
                            if (task.targetType == "PLAY_COUNT") {
                                val newCount = matchingProgress.completedPlayCount + 1
                                val isTrackDone = newCount >= task.targetValue
                                updatedProgress = matchingProgress.copy(
                                    completedPlayCount = newCount,
                                    isTrackCompleted = isTrackDone
                                )
                            } else if (task.targetType == "DAYS_COUNT") {
                                val newCount = matchingProgress.completedPlayCount + 1
                                val days = matchingProgress.getDaysList().toMutableSet()
                                days.add(todayStr)
                                val newDaysStr = days.joinToString(",")
                                val isTrackDone = days.size >= task.targetValue
                                updatedProgress = matchingProgress.copy(
                                    completedPlayCount = newCount,
                                    completedDays = newDaysStr,
                                    isTrackCompleted = isTrackDone
                                )
                            }

                            repo.insertTaskProgress(updatedProgress)

                            val refreshedProgresses = repo.getProgressForTask(task.id)
                            val allTracksCompleted = refreshedProgresses.isNotEmpty() && refreshedProgresses.all { it.isTrackCompleted }

                            if (allTracksCompleted && !task.isCompleted) {
                                repo.updateTask(task.copy(isCompleted = true, status = "COMPLETED"))
                                appContext?.let { com.example.receiver.AlarmReceiver.cancelAlarm(it, task.id) }
                                sendTaskCompletionNotification(task)
                            }
                            com.example.util.AutoBackupManager.saveAutoBackupFromRepository(appContext, repo)
                        }
                    }
                }
            }
        }
    }

    suspend fun updateAssociatedTasks(trackId: Long) {
        with(AudioPlayerManager) {
            val repo = repository ?: return
            val track = currentTrackValue
            val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

            val activeTasksList = repo.getAllTasksDirect()
            var anyTaskUpdated = false

            for (task in activeTasksList) {
                if (task.isCompleted) continue
                if (completedTaskIdsForCurrentSession.contains(task.id)) continue

                if (task.customThreshold != null) {
                    val currentProgress = track?.getProgressPercent() ?: 0
                    if (currentProgress < task.customThreshold) {
                        continue
                    }
                }

                val taskProgresses = repo.getProgressForTask(task.id)
                val matchingProgress = taskProgresses.find { it.trackId == trackId }

                if (matchingProgress != null) {
                    completedTaskIdsForCurrentSession.add(task.id)
                    repo.incrementDailyPlayCount(task.id, todayStr)
                    var updatedProgress = matchingProgress
                    if (task.targetType == "PLAY_COUNT") {
                        val newCount = matchingProgress.completedPlayCount + 1
                        val isTrackDone = newCount >= task.targetValue
                        updatedProgress = matchingProgress.copy(
                            completedPlayCount = newCount,
                            isTrackCompleted = isTrackDone
                        )
                    } else if (task.targetType == "DAYS_COUNT") {
                        val days = matchingProgress.getDaysList().toMutableSet()
                        days.add(todayStr)
                        val newDaysStr = days.joinToString(",")
                        val isTrackDone = days.size >= task.targetValue
                        updatedProgress = matchingProgress.copy(
                            completedDays = newDaysStr,
                            isTrackCompleted = isTrackDone
                        )
                    }

                    repo.insertTaskProgress(updatedProgress)
                    anyTaskUpdated = true

                    val refreshedProgresses = repo.getProgressForTask(task.id)
                    val allTracksCompleted = refreshedProgresses.isNotEmpty() && refreshedProgresses.all { it.isTrackCompleted }

                    if (allTracksCompleted && !task.isCompleted) {
                        repo.updateTask(task.copy(isCompleted = true, status = "COMPLETED"))
                        appContext?.let { com.example.receiver.AlarmReceiver.cancelAlarm(it, task.id) }
                        sendTaskCompletionNotification(task)
                    }
                }
            }

            if (anyTaskUpdated) {
                com.example.util.AutoBackupManager.saveAutoBackupFromRepository(appContext, repo)
            }
        }
    }
    // =========================================================================
    // @END_LOCKED: Session History Sync & Task Completion Engine
    // =========================================================================
}
