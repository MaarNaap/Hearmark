package com.example.ui

import android.app.Application
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// =========================================================================
// @LOCKED: Full Backup & Restore Serialization Engine - STRICT FREEZE
// DO NOT MODIFY OR REFACTOR THIS BLOCK WITHOUT EXPLICIT PERMISSION IN PROMPT
// =========================================================================
suspend fun AppViewModel.exportBackupToJson(outputStream: java.io.OutputStream): Boolean =
    withContext(Dispatchers.IO) {
        try {
            val root = com.example.util.AutoBackupManager.buildBackupJsonFromRepository(
                repository = repository,
                requireNonEmpty = false
            ) ?: return@withContext false

            outputStream.write(root.toString(2).toByteArray(Charsets.UTF_8))
            outputStream.flush()
            true
        } catch (e: Exception) {
            Log.e("BackupRestore", "Failed to export backup to JSON stream", e)
            false
        }
    }

fun AppViewModel.exportBackupToUri(
    uri: android.net.Uri,
    onResult: ((Boolean) -> Unit)? = null
) {
    viewModelScope.launch(Dispatchers.IO) {
        val context = getApplication<Application>()
        val success = try {
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                exportBackupToJson(outputStream)
            } ?: false
        } catch (e: Exception) {
            Log.e("BackupRestore", "Failed to open output stream for backup export", e)
            false
        }
        withContext(Dispatchers.Main) {
            if (onResult != null) {
                onResult(success)
            } else if (success) {
                Toast.makeText(context, Loc.getText("backup_success"), Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(context, Loc.getText("backup_failed"), Toast.LENGTH_SHORT).show()
            }
        }
    }
}

fun AppViewModel.restoreBackupFromUri(uri: android.net.Uri) {
    viewModelScope.launch(Dispatchers.IO) {
        val context = getApplication<Application>()
        val jsonBytes = try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                stream.readBytes()
            }
        } catch (e: Exception) {
            Log.e("BackupRestore", "Failed to read backup input stream from URI", e)
            null
        }
        if (jsonBytes == null || jsonBytes.isEmpty()) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, Loc.getText("restore_failed"), Toast.LENGTH_SHORT).show()
            }
            return@launch
        }
        val offset = if (jsonBytes.size >= 3 &&
            jsonBytes[0] == 0xEF.toByte() &&
            jsonBytes[1] == 0xBB.toByte() &&
            jsonBytes[2] == 0xBF.toByte()
        ) 3 else 0
        val jsonString = String(jsonBytes, offset, jsonBytes.size - offset, Charsets.UTF_8)
        restoreBackupFromJsonString(jsonString, isAutoRecovery = false)
    }
}

fun AppViewModel.restoreBackupFromJson(inputStream: java.io.InputStream) {
    viewModelScope.launch(Dispatchers.IO) {
        val jsonBytes = try {
            inputStream.use { it.readBytes() }
        } catch (e: Exception) {
            Log.e("BackupRestore", "Failed to read backup input stream", e)
            null
        }
        if (jsonBytes == null || jsonBytes.isEmpty()) {
            withContext(Dispatchers.Main) {
                val context = getApplication<Application>()
                Toast.makeText(context, Loc.getText("restore_failed"), Toast.LENGTH_LONG).show()
            }
            return@launch
        }
        val offset = if (jsonBytes.size >= 3 &&
            jsonBytes[0] == 0xEF.toByte() &&
            jsonBytes[1] == 0xBB.toByte() &&
            jsonBytes[2] == 0xBF.toByte()
        ) 3 else 0
        val jsonString = String(jsonBytes, offset, jsonBytes.size - offset, Charsets.UTF_8)
        restoreBackupFromJsonString(jsonString, isAutoRecovery = false)
    }
}

fun AppViewModel.restoreBackupFromJsonString(jsonString: String, isAutoRecovery: Boolean = false) {
    viewModelScope.launch(Dispatchers.IO) {
        try {
            val cleanJson = jsonString.trim().removePrefix("\uFEFF").trim()
            if (cleanJson.isBlank()) {
                if (!isAutoRecovery) {
                    withContext(Dispatchers.Main) {
                        val context = getApplication<Application>()
                        Toast.makeText(context, Loc.getText("restore_failed"), Toast.LENGTH_LONG).show()
                    }
                }
                return@launch
            }

            fun optLongSafe(obj: org.json.JSONObject, vararg keys: String, defaultVal: Long = 0L): Long {
                for (k in keys) {
                    if (obj.has(k) && !obj.isNull(k)) {
                        val v = obj.opt(k)
                        when (v) {
                            is Number -> return v.toLong()
                            is String -> v.trim().toLongOrNull()?.let { return it }
                        }
                    }
                }
                return defaultVal
            }

            fun optNullableLongSafe(obj: org.json.JSONObject, vararg keys: String): Long? {
                for (k in keys) {
                    if (obj.has(k) && !obj.isNull(k)) {
                        val v = obj.opt(k)
                        when (v) {
                            is Number -> return v.toLong()
                            is String -> v.trim().toLongOrNull()?.let { return it }
                        }
                    }
                }
                return null
            }

            fun optIntSafe(obj: org.json.JSONObject, vararg keys: String, defaultVal: Int = 0): Int {
                for (k in keys) {
                    if (obj.has(k) && !obj.isNull(k)) {
                        val v = obj.opt(k)
                        when (v) {
                            is Number -> return v.toInt()
                            is String -> v.trim().toIntOrNull()?.let { return it }
                        }
                    }
                }
                return defaultVal
            }

            fun optNullableIntSafe(obj: org.json.JSONObject, vararg keys: String): Int? {
                for (k in keys) {
                    if (obj.has(k) && !obj.isNull(k)) {
                        val v = obj.opt(k)
                        when (v) {
                            is Number -> return v.toInt()
                            is String -> v.trim().toIntOrNull()?.let { return it }
                        }
                    }
                }
                return null
            }

            fun optStringSafe(obj: org.json.JSONObject, vararg keys: String, defaultVal: String = ""): String {
                for (k in keys) {
                    if (obj.has(k) && !obj.isNull(k)) {
                        val s = obj.optString(k, "").trim()
                        if (s.isNotEmpty() && s != "null") return s
                    }
                }
                return defaultVal
            }

            fun optNullableStringSafe(obj: org.json.JSONObject, vararg keys: String): String? {
                for (k in keys) {
                    if (obj.has(k) && !obj.isNull(k)) {
                        val s = obj.optString(k, "").trim()
                        if (s.isNotEmpty() && s != "null") return s
                    }
                }
                return null
            }

            fun optDoubleSafe(obj: org.json.JSONObject, vararg keys: String, defaultVal: Double = 1.0): Double {
                for (k in keys) {
                    if (obj.has(k) && !obj.isNull(k)) {
                        val v = obj.opt(k)
                        when (v) {
                            is Number -> return v.toDouble()
                            is String -> v.trim().toDoubleOrNull()?.let { return it }
                        }
                    }
                }
                return defaultVal
            }

            fun optBooleanSafe(obj: org.json.JSONObject, vararg keys: String, defaultVal: Boolean = false): Boolean {
                for (k in keys) {
                    if (obj.has(k) && !obj.isNull(k)) {
                        val v = obj.opt(k)
                        when (v) {
                            is Boolean -> return v
                            is Number -> return v.toInt() != 0
                            is String -> {
                                val s = v.trim().lowercase()
                                if (s == "true" || s == "1") return true
                                if (s == "false" || s == "0") return false
                            }
                        }
                    }
                }
                return defaultVal
            }

            var historyCount = 0
            var tasksCount = 0
            var progressCount = 0
            var tagsCount = 0
            var notesCount = 0
            var labelsCount = 0
            var dailyProgressCount = 0
            var vocabCount = 0
            var quizQuestionsCount = 0

            suspend fun parseQuizQuestionItem(obj: org.json.JSONObject) {
                val question = optStringSafe(obj, "question", defaultVal = "")
                val optionsJson = optStringSafe(obj, "optionsJson", "options_json", defaultVal = "[]")
                if (question.isNotBlank()) {
                    val q = QuizQuestion(
                        id = optLongSafe(obj, "id", defaultVal = 0L),
                        trackId = optNullableLongSafe(obj, "trackId", "track_id"),
                        noteId = optNullableLongSafe(obj, "noteId", "note_id"),
                        questionType = optStringSafe(obj, "questionType", "question_type", defaultVal = "MCQ"),
                        category = optStringSafe(obj, "category", defaultVal = "COMPREHENSION"),
                        question = question,
                        optionsJson = optionsJson,
                        correctIndex = optIntSafe(obj, "correctIndex", "correct_index", defaultVal = 0),
                        explanation = optStringSafe(obj, "explanation", defaultVal = ""),
                        timestampMs = optNullableLongSafe(obj, "timestampMs", "timestamp_ms"),
                        timesAnswered = optIntSafe(obj, "timesAnswered", "times_answered", defaultVal = 0),
                        timesCorrect = optIntSafe(obj, "timesCorrect", "times_correct", defaultVal = 0),
                        lastAnsweredAt = optNullableLongSafe(obj, "lastAnsweredAt", "last_answered_at"),
                        createdAt = optLongSafe(obj, "createdAt", "created_at", defaultVal = System.currentTimeMillis()),
                        targetWord = optNullableStringSafe(obj, "targetWord", "target_word"),
                        meaning = optNullableStringSafe(obj, "meaning", "definition"),
                        contextSentence = optNullableStringSafe(obj, "contextSentence", "context_sentence"),
                        srRepetitions = optIntSafe(obj, "srRepetitions", "sr_repetitions", defaultVal = 0),
                        srIntervalDays = optIntSafe(obj, "srIntervalDays", "sr_interval_days", defaultVal = 0),
                        srEase = optDoubleSafe(obj, "srEase", "sr_ease", defaultVal = 2.5).toFloat(),
                        srLapses = optIntSafe(obj, "srLapses", "sr_lapses", defaultVal = 0),
                        srNextReviewAt = optNullableLongSafe(obj, "srNextReviewAt", "sr_next_review_at")
                    )
                    repository.dao.insertQuizQuestion(q)
                    quizQuestionsCount++
                }
            }

            suspend fun parseHistoryItem(obj: org.json.JSONObject) {
                val id = optLongSafe(obj, "id", defaultVal = 0L)
                val trackId = optLongSafe(obj, "trackId", "track_id", defaultVal = 0L)
                val trackName = optStringSafe(obj, "trackName", "track_name", "title", "name", defaultVal = "Track")
                val completedAt = optLongSafe(obj, "completedAt", "completed_at", "timestamp", "date", defaultVal = System.currentTimeMillis())
                val durationMs = optLongSafe(obj, "durationMs", "duration_ms", "duration", defaultVal = 0L)
                val playbackSpeed = optDoubleSafe(obj, "playbackSpeed", "playback_speed", "speed", defaultVal = 1.0).toFloat()
                val actualListenedMs = optLongSafe(obj, "actualListenedMs", "actual_listened_ms", "listenedMs", defaultVal = 0L)
                val activeTasks = optStringSafe(obj, "activeTasks", "active_tasks", defaultVal = "")

                val history = PlaybackHistory(
                    id = id,
                    trackId = trackId,
                    trackName = trackName,
                    completedAt = completedAt,
                    durationMs = durationMs,
                    playbackSpeed = playbackSpeed,
                    actualListenedMs = actualListenedMs,
                    activeTasks = activeTasks
                )
                repository.insertPlaybackHistory(history)
                historyCount++
            }

            suspend fun parseTaskItem(obj: org.json.JSONObject) {
                val id = optLongSafe(obj, "id", defaultVal = 0L)
                val title = optStringSafe(obj, "title", "taskName", "name", defaultVal = "Task")
                val sourceType = optStringSafe(obj, "sourceType", "source_type", defaultVal = "TRACKS")
                val sourceId = optNullableLongSafe(obj, "sourceId", "source_id")
                val targetType = optStringSafe(obj, "targetType", "target_type", defaultVal = "PLAY_COUNT")
                val targetValue = optIntSafe(obj, "targetValue", "target_value", defaultVal = 1).coerceAtLeast(1)
                val scheduledDays = optStringSafe(obj, "scheduledDays", "scheduled_days", defaultVal = "")
                val reminderTime = optStringSafe(obj, "reminderTime", "reminder_time", defaultVal = "08:00 AM")
                val startDate = optLongSafe(obj, "startDate", "start_date", defaultVal = System.currentTimeMillis())
                val endDate = optNullableLongSafe(obj, "endDate", "end_date")
                val isCompleted = optBooleanSafe(obj, "isCompleted", "is_completed", "completed", defaultVal = false)
                val status = optStringSafe(obj, "status", defaultVal = if (isCompleted) "COMPLETED" else "ACTIVE")
                val customThreshold = optNullableIntSafe(obj, "customThreshold", "custom_threshold")
                val labels = optStringSafe(obj, "labels", defaultVal = "")
                val dailyTargetValue = optNullableIntSafe(obj, "dailyTargetValue", "daily_target_value")

                val existingTask = if (isAutoRecovery && id > 0L) repository.getTaskById(id) else null
                val finalIsCompleted = if (existingTask?.isCompleted == true) true else isCompleted
                val finalStatus = if (finalIsCompleted) "COMPLETED" else status

                val task = Task(
                    id = id,
                    title = title,
                    sourceType = sourceType,
                    sourceId = sourceId,
                    targetType = targetType,
                    targetValue = targetValue,
                    scheduledDays = scheduledDays,
                    reminderTime = reminderTime,
                    startDate = startDate,
                    endDate = endDate,
                    isCompleted = finalIsCompleted,
                    status = finalStatus,
                    customThreshold = customThreshold,
                    labels = labels,
                    dailyTargetValue = dailyTargetValue
                )
                repository.insertTask(task)
                tasksCount++
            }

            suspend fun parseTaskProgressItem(obj: org.json.JSONObject) {
                val taskId = optLongSafe(obj, "taskId", "task_id", defaultVal = 0L)
                val trackId = optLongSafe(obj, "trackId", "track_id", defaultVal = 0L)
                val completedPlayCount = optIntSafe(obj, "completedPlayCount", "completed_play_count", defaultVal = 0)
                val completedDays = optStringSafe(obj, "completedDays", "completed_days", defaultVal = "")
                val isTrackCompleted = optBooleanSafe(obj, "isTrackCompleted", "is_track_completed", defaultVal = false)

                if (taskId > 0L) {
                    val existingProgress = if (isAutoRecovery) {
                        repository.getProgressForTask(taskId).find { it.trackId == trackId }
                    } else null

                    val mergedPlayCount = if (existingProgress != null) {
                        maxOf(existingProgress.completedPlayCount, completedPlayCount)
                    } else completedPlayCount

                    val mergedDays = if (existingProgress != null) {
                        val combined = (existingProgress.getDaysList() + completedDays.split(",").map { it.trim() }.filter { it.isNotEmpty() }).toSet()
                        combined.joinToString(",")
                    } else completedDays

                    val mergedCompleted = (existingProgress?.isTrackCompleted == true) || isTrackCompleted

                    val p = TaskTrackProgress(
                        taskId = taskId,
                        trackId = trackId,
                        completedPlayCount = mergedPlayCount,
                        completedDays = mergedDays,
                        isTrackCompleted = mergedCompleted
                    )
                    repository.insertTaskProgress(p)
                    progressCount++
                }
            }

            suspend fun parseNoteItem(obj: org.json.JSONObject) {
                val id = optLongSafe(obj, "id", defaultVal = 0L)
                val text = optStringSafe(obj, "text", "content", "quote", defaultVal = "")
                val comment = optStringSafe(obj, "comment", "translation", "explanation", "meaning", defaultVal = "")
                val trackId = optNullableLongSafe(obj, "trackId", "track_id")
                val trackName = optNullableStringSafe(obj, "trackName", "track_name")
                val folderId = optNullableLongSafe(obj, "folderId", "folder_id")
                val folderName = optNullableStringSafe(obj, "folderName", "folder_name")
                val startTimestampMs = optLongSafe(obj, "startTimestampMs", "start_timestamp_ms", "startTime", defaultVal = 0L)
                val endTimestampMs = optLongSafe(obj, "endTimestampMs", "end_timestamp_ms", "endTime", defaultVal = 0L)
                val originStartMs = optNullableLongSafe(obj, "originStartMs", "origin_start_ms")
                val tags = optStringSafe(obj, "tags", defaultVal = "")
                val createdAt = optLongSafe(obj, "createdAt", "created_at", defaultVal = System.currentTimeMillis())
                val updatedAt = optLongSafe(obj, "updatedAt", "updated_at", defaultVal = System.currentTimeMillis())
                val targetWord = optNullableStringSafe(obj, "targetWord", "target_word", "word")
                val meaning = optNullableStringSafe(obj, "meaning", "definition")
                val contextSentence = optNullableStringSafe(obj, "contextSentence", "context_sentence", "sentence")

                if (text.isNotBlank() || comment.isNotBlank() || !targetWord.isNullOrBlank()) {
                    val note = Note(
                        id = id,
                        text = text,
                        comment = comment,
                        trackId = trackId,
                        trackName = trackName,
                        folderId = folderId,
                        folderName = folderName,
                        startTimestampMs = startTimestampMs,
                        endTimestampMs = endTimestampMs,
                        originStartMs = originStartMs,
                        tags = tags,
                        createdAt = createdAt,
                        updatedAt = updatedAt,
                        targetWord = targetWord,
                        meaning = meaning,
                        contextSentence = contextSentence
                    )
                    repository.insertNote(note)
                    notesCount++
                }
            }

            suspend fun parseNoteTagItem(item: Any?) {
                if (item is org.json.JSONObject) {
                    val name = optStringSafe(item, "name", "tag", "tagName")
                    if (name.isNotEmpty()) {
                        val colorHex = optNullableStringSafe(item, "colorHex", "color_hex", "color")
                        repository.insertTag(name, colorHex)
                        tagsCount++
                    }
                } else if (item is String && item.isNotBlank()) {
                    repository.insertTag(item.trim(), null)
                    tagsCount++
                }
            }

            suspend fun parseTaskLabelItem(item: Any?) {
                val name = when (item) {
                    is org.json.JSONObject -> optStringSafe(item, "name", "label", "title")
                    is String -> item.trim()
                    else -> ""
                }
                if (name.isNotEmpty()) {
                    repository.dao.insertTaskLabel(TaskLabel(name = name))
                    labelsCount++
                }
            }

            suspend fun parseDailyProgressItem(obj: org.json.JSONObject) {
                val taskId = optLongSafe(obj, "taskId", "task_id", defaultVal = 0L)
                val date = optStringSafe(obj, "date", defaultVal = "")
                val completedPlayCount = optIntSafe(obj, "completedPlayCount", "completed_play_count", defaultVal = 0)
                if (taskId > 0L && date.isNotEmpty()) {
                    repository.insertTaskDailyProgress(TaskDailyProgress(taskId = taskId, date = date, completedPlayCount = completedPlayCount))
                    dailyProgressCount++
                }
            }

            suspend fun parseVocabItem(obj: org.json.JSONObject) {
                val targetWord = optStringSafe(obj, "targetWord", "target_word", "word")
                val meaning = optStringSafe(obj, "meaning", "definition")
                if (targetWord.isNotBlank()) {
                    val item = VocabularyItem(
                        id = optLongSafe(obj, "id", defaultVal = 0L),
                        targetWord = targetWord,
                        meaning = meaning,
                        contextSentence = optStringSafe(obj, "contextSentence", "context_sentence", defaultVal = ""),
                        noteId = optNullableLongSafe(obj, "noteId", "note_id"),
                        trackId = optNullableLongSafe(obj, "trackId", "track_id"),
                        timestampMs = optNullableLongSafe(obj, "timestampMs", "timestamp_ms"),
                        timesReviewed = optIntSafe(obj, "timesReviewed", "times_reviewed", defaultVal = 0),
                        timesCorrect = optIntSafe(obj, "timesCorrect", "times_correct", defaultVal = 0),
                        isMastered = optBooleanSafe(obj, "isMastered", "is_mastered", defaultVal = false),
                        lastReviewedAt = optNullableLongSafe(obj, "lastReviewedAt", "last_reviewed_at"),
                        createdAt = optLongSafe(obj, "createdAt", "created_at", defaultVal = System.currentTimeMillis())
                    )
                    repository.insertVocabularyItem(item)
                    vocabCount++
                }
            }

            if (cleanJson.startsWith("[")) {
                val arr = org.json.JSONArray(cleanJson)
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    try {
                        if (obj.has("targetType") || obj.has("target_type") || obj.has("scheduledDays") || obj.has("scheduled_days")) {
                            parseTaskItem(obj)
                        } else if (obj.has("actualListenedMs") || obj.has("actual_listened_ms") || obj.has("completedAt") || obj.has("completed_at")) {
                            parseHistoryItem(obj)
                        } else if (obj.has("startTimestampMs") || obj.has("start_timestamp_ms") || obj.has("originStartMs") || (obj.has("comment") && obj.has("text"))) {
                            parseNoteItem(obj)
                        } else if (obj.has("completedPlayCount") && obj.has("taskId") && obj.has("trackId")) {
                            parseTaskProgressItem(obj)
                        } else if (obj.has("targetWord") || obj.has("target_word")) {
                            parseVocabItem(obj)
                        } else if (obj.has("taskId") && obj.has("date")) {
                            parseDailyProgressItem(obj)
                        } else if (obj.has("question") && (obj.has("optionsJson") || obj.has("options_json") || obj.has("correctIndex") || obj.has("questionType"))) {
                            parseQuizQuestionItem(obj)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            } else {
                val root = org.json.JSONObject(cleanJson)
                val effectiveRoot = when {
                    root.has("data") && root.optJSONObject("data") != null -> root.optJSONObject("data")!!
                    root.has("backup") && root.optJSONObject("backup") != null -> root.optJSONObject("backup")!!
                    root.has("hearmark") && root.optJSONObject("hearmark") != null -> root.optJSONObject("hearmark")!!
                    root.has("content") && root.optJSONObject("content") != null -> root.optJSONObject("content")!!
                    root.has("export") && root.optJSONObject("export") != null -> root.optJSONObject("export")!!
                    else -> root
                }

                // 1. Playback history
                val historyArr = effectiveRoot.optJSONArray("playbackHistory")
                    ?: effectiveRoot.optJSONArray("playback_history")
                    ?: effectiveRoot.optJSONArray("history")
                    ?: effectiveRoot.optJSONArray("historyList")
                    ?: effectiveRoot.optJSONArray("playbackRecords")
                    ?: effectiveRoot.optJSONArray("stats")
                if (historyArr != null) {
                    for (i in 0 until historyArr.length()) {
                        try {
                            val obj = historyArr.optJSONObject(i) ?: continue
                            parseHistoryItem(obj)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }

                // 2. Tasks
                val tasksArr = effectiveRoot.optJSONArray("tasks")
                    ?: effectiveRoot.optJSONArray("taskList")
                    ?: effectiveRoot.optJSONArray("task_list")
                    ?: effectiveRoot.optJSONArray("allTasks")
                    ?: effectiveRoot.optJSONArray("all_tasks")
                if (tasksArr != null) {
                    for (i in 0 until tasksArr.length()) {
                        try {
                            val obj = tasksArr.optJSONObject(i) ?: continue
                            parseTaskItem(obj)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }

                // 3. Task Progress
                val progressArr = effectiveRoot.optJSONArray("taskProgress")
                    ?: effectiveRoot.optJSONArray("task_progress")
                    ?: effectiveRoot.optJSONArray("progress")
                    ?: effectiveRoot.optJSONArray("allTaskProgress")
                if (progressArr != null) {
                    for (i in 0 until progressArr.length()) {
                        try {
                            val obj = progressArr.optJSONObject(i) ?: continue
                            parseTaskProgressItem(obj)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }

                // 4. Notes
                val notesArr = effectiveRoot.optJSONArray("notes")
                    ?: effectiveRoot.optJSONArray("noteList")
                    ?: effectiveRoot.optJSONArray("notesList")
                    ?: effectiveRoot.optJSONArray("note_list")
                    ?: effectiveRoot.optJSONArray("allNotes")
                    ?: effectiveRoot.optJSONArray("notebook")
                if (notesArr != null) {
                    for (i in 0 until notesArr.length()) {
                        try {
                            val obj = notesArr.optJSONObject(i) ?: continue
                            parseNoteItem(obj)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                    try {
                        repository.relinkNotesToActiveTracks()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                // 5. Note Tags
                val tagsArr = effectiveRoot.optJSONArray("noteTags")
                    ?: effectiveRoot.optJSONArray("note_tags")
                    ?: effectiveRoot.optJSONArray("tags")
                    ?: effectiveRoot.optJSONArray("tagList")
                if (tagsArr != null) {
                    for (i in 0 until tagsArr.length()) {
                        try {
                            parseNoteTagItem(tagsArr.opt(i))
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }

                // 6. Task Labels
                val labelsArr = effectiveRoot.optJSONArray("taskLabels")
                    ?: effectiveRoot.optJSONArray("task_labels")
                    ?: effectiveRoot.optJSONArray("labels")
                    ?: effectiveRoot.optJSONArray("labelList")
                if (labelsArr != null) {
                    for (i in 0 until labelsArr.length()) {
                        try {
                            parseTaskLabelItem(labelsArr.opt(i))
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }

                // 7. Daily Progress
                val dailyArr = effectiveRoot.optJSONArray("taskDailyProgress")
                    ?: effectiveRoot.optJSONArray("task_daily_progress")
                    ?: effectiveRoot.optJSONArray("dailyProgress")
                    ?: effectiveRoot.optJSONArray("daily_progress")
                if (dailyArr != null) {
                    for (i in 0 until dailyArr.length()) {
                        try {
                            val obj = dailyArr.optJSONObject(i) ?: continue
                            parseDailyProgressItem(obj)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }

                // 8. Vocabulary
                val vocabArr = effectiveRoot.optJSONArray("vocabularyItems")
                    ?: effectiveRoot.optJSONArray("vocabulary_items")
                    ?: effectiveRoot.optJSONArray("vocabulary")
                    ?: effectiveRoot.optJSONArray("vocab")
                    ?: effectiveRoot.optJSONArray("words")
                if (vocabArr != null) {
                    for (i in 0 until vocabArr.length()) {
                        try {
                            val obj = vocabArr.optJSONObject(i) ?: continue
                            parseVocabItem(obj)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }

                // 9. Quiz Questions & Spaced Repetition Scheduling
                val quizArr = effectiveRoot.optJSONArray("quizQuestions")
                    ?: effectiveRoot.optJSONArray("quiz_questions")
                    ?: effectiveRoot.optJSONArray("questions")
                    ?: effectiveRoot.optJSONArray("quiz")
                if (quizArr != null) {
                    for (i in 0 until quizArr.length()) {
                        try {
                            val obj = quizArr.optJSONObject(i) ?: continue
                            parseQuizQuestionItem(obj)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            }

            // If tasks were restored, sync labels and reschedule active alarms
            if (tasksCount > 0) {
                try {
                    repository.syncAndCleanTaskLabels()
                } catch (e: Exception) {
                    Log.w("BackupRestore", "Failed to sync task labels after restore", e)
                }
                try {
                    com.example.receiver.AlarmReceiver.rescheduleAllActiveTasks(getApplication())
                } catch (e: Exception) {
                    Log.w("BackupRestore", "Failed to reschedule active task alarms after restore", e)
                }
            }

            try {
                repository.relinkNotesToActiveTracks()
            } catch (e: Exception) {
                e.printStackTrace()
            }

            val totalRestored = historyCount + tasksCount + progressCount + notesCount + tagsCount + labelsCount + dailyProgressCount + vocabCount + quizQuestionsCount
            if (!isAutoRecovery) {
                com.example.util.AutoBackupManager.saveAutoBackupFromRepository(getApplication(), repository)
                withContext(Dispatchers.Main) {
                    val context = getApplication<Application>()
                    if (totalRestored > 0 || cleanJson.contains("Hearmark", ignoreCase = true) || cleanJson.contains("version", ignoreCase = true)) {
                        Toast.makeText(context, "${Loc.getText("restore_success")} ($totalRestored records)", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(context, Loc.getText("restore_failed"), Toast.LENGTH_LONG).show()
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            if (!isAutoRecovery) {
                withContext(Dispatchers.Main) {
                    val context = getApplication<Application>()
                    Toast.makeText(context, Loc.getText("restore_failed"), Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}

fun AppViewModel.triggerAutoBackup() {
    viewModelScope.launch(Dispatchers.IO) {
        com.example.util.AutoBackupManager.saveAutoBackupFromRepository(getApplication(), repository)
    }
}

fun AppViewModel.restoreLatestAutoBackup(onResult: (Boolean, String) -> Unit) {
    val json = com.example.util.AutoBackupManager.getLatestAutoBackupString(getApplication())
    if (!json.isNullOrBlank()) {
        restoreBackupFromJsonString(json)
        onResult(true, Loc.getText("auto_restore_success"))
    } else {
        onResult(false, Loc.getText("auto_restore_no_snapshot"))
    }
}

fun AppViewModel.getAutoBackupLastModified(): Long? {
    return com.example.util.AutoBackupManager.getLatestAutoBackupTimestamp(getApplication())
}
// =========================================================================
// @END_LOCKED: Full Backup & Restore Serialization Engine
// =========================================================================
