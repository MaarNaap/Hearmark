package com.example.util

import android.content.Context
import android.util.Log
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AutoBackupManager {
    private const val TAG = "AutoBackupManager"
    private const val BACKUP_DIR_NAME = "auto_backups"
    private const val SAFETY_DB_DIR_NAME = "db_safety_backups"
    private const val LATEST_BACKUP_NAME = "hearmark_autosnapshot_latest.json"
    private const val MAX_DAILY_SNAPSHOTS = 5

    private const val DB_BACKUP_META_PREFS = "db_backup_meta"
    private const val PREF_LAST_BACKED_UP_VERSION = "last_backed_up_version"

    /**
     * Checks if the database needs a pre-migration backup for [targetVersion] and dispatches
     * the file copy on Dispatchers.IO so process startup is never blocked on the main thread.
     */
    fun scheduleSafetyBackupIfNeeded(context: Context, targetVersion: Int = 19) {
        val appCtx = context.applicationContext
        val prefs = appCtx.getSharedPreferences(DB_BACKUP_META_PREFS, Context.MODE_PRIVATE)
        if (prefs.getInt(PREF_LAST_BACKED_UP_VERSION, 0) >= targetVersion) return

        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            safetyBackupDatabaseFile(appCtx, targetVersion)
        }
    }

    /**
     * Safely copies the SQLite database file and WAL journal when upgrading to a new schema version.
     * Keeps one version-specific snapshot per schema upgrade plus the latest pre_migration.bak file.
     */
    @Synchronized
    fun safetyBackupDatabaseFile(context: Context, targetVersion: Int = 19) {
        try {
            val prefs = context.getSharedPreferences(DB_BACKUP_META_PREFS, Context.MODE_PRIVATE)
            val lastVersion = prefs.getInt(PREF_LAST_BACKED_UP_VERSION, 0)
            if (lastVersion >= targetVersion) return

            val dbFile = context.getDatabasePath("smart_audio_tasks_db")
            if (dbFile.exists() && dbFile.length() > 0) {
                val safetyDir = File(context.filesDir, SAFETY_DB_DIR_NAME)
                if (!safetyDir.exists()) safetyDir.mkdirs()

                val versionedBackup = File(safetyDir, "smart_audio_tasks_db.v${targetVersion}.pre_migration.bak")
                dbFile.copyTo(versionedBackup, overwrite = true)

                val latestBackup = File(safetyDir, "smart_audio_tasks_db.pre_migration.bak")
                dbFile.copyTo(latestBackup, overwrite = true)

                val walFile = context.getDatabasePath("smart_audio_tasks_db-wal")
                if (walFile.exists() && walFile.length() > 0) {
                    val versionedWal = File(safetyDir, "smart_audio_tasks_db.v${targetVersion}.pre_migration.bak-wal")
                    walFile.copyTo(versionedWal, overwrite = true)
                    val latestWal = File(safetyDir, "smart_audio_tasks_db.pre_migration.bak-wal")
                    walFile.copyTo(latestWal, overwrite = true)
                }

                prefs.edit().putInt(PREF_LAST_BACKED_UP_VERSION, targetVersion).apply()
                Log.d(TAG, "Safety backup of database created for v$targetVersion (${dbFile.length()} bytes)")
            } else {
                prefs.edit().putInt(PREF_LAST_BACKED_UP_VERSION, targetVersion).apply()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create database safety backup", e)
        }
    }

    /**
     * Writes an automatic JSON snapshot to both internal files and external app files dir.
     * Keeps the latest snapshot and rolling daily history (up to 5 days).
     */
    @Synchronized
    fun saveAutoBackup(context: Context, jsonString: String) {
        if (jsonString.isBlank()) return
        try {
            // 1. Internal storage auto_backups/
            val internalDir = File(context.filesDir, BACKUP_DIR_NAME)
            if (!internalDir.exists()) internalDir.mkdirs()

            val latestFile = File(internalDir, LATEST_BACKUP_NAME)
            latestFile.writeText(jsonString, Charsets.UTF_8)

            // Rolling daily snapshot (always refresh today's file so it reflects latest completed tasks/progress)
            val dateTag = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
            val dailyFile = File(internalDir, "hearmark_autosnapshot_$dateTag.json")
            dailyFile.writeText(jsonString, Charsets.UTF_8)

            // Prune snapshots older than MAX_DAILY_SNAPSHOTS
            val allDaily = internalDir.listFiles { _, name ->
                name.startsWith("hearmark_autosnapshot_") && name.endsWith(".json") && name != LATEST_BACKUP_NAME
            }?.sortedByDescending { it.lastModified() } ?: emptyList()

            if (allDaily.size > MAX_DAILY_SNAPSHOTS) {
                allDaily.drop(MAX_DAILY_SNAPSHOTS).forEach { oldFile ->
                    try { oldFile.delete() } catch (_: Exception) {}
                }
            }

            // 2. Secondary copy to app's external files directory (preserved across updates)
            try {
                val externalDir = context.getExternalFilesDir("backups")
                if (externalDir != null) {
                    if (!externalDir.exists()) externalDir.mkdirs()
                    File(externalDir, LATEST_BACKUP_NAME).writeText(jsonString, Charsets.UTF_8)
                }
            } catch (ex: Exception) {
                Log.w(TAG, "Could not write to external backup dir", ex)
            }

            Log.d(TAG, "Auto-backup saved successfully (${jsonString.length} chars)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save auto-backup snapshot", e)
        }
    }

    /**
     * Retrieves the latest JSON auto-backup string from internal or external storage.
     */
    fun getLatestAutoBackupString(context: Context): String? {
        try {
            // Check internal latest
            val internalLatest = File(File(context.filesDir, BACKUP_DIR_NAME), LATEST_BACKUP_NAME)
            if (internalLatest.exists() && internalLatest.length() > 20) {
                val text = internalLatest.readText(Charsets.UTF_8).trim()
                if (text.isNotBlank()) return text
            }

            // Check external latest fallback
            val externalLatest = File(context.getExternalFilesDir("backups"), LATEST_BACKUP_NAME)
            if (externalLatest.exists() && externalLatest.length() > 20) {
                val text = externalLatest.readText(Charsets.UTF_8).trim()
                if (text.isNotBlank()) return text
            }

            // Check any daily snapshot in internal
            val internalDir = File(context.filesDir, BACKUP_DIR_NAME)
            val newestDaily = internalDir.listFiles { _, name ->
                name.startsWith("hearmark_autosnapshot_") && name.endsWith(".json")
            }?.maxByOrNull { it.lastModified() }

            if (newestDaily != null && newestDaily.length() > 20) {
                val text = newestDaily.readText(Charsets.UTF_8).trim()
                if (text.isNotBlank()) return text
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read latest auto-backup", e)
        }
        return null
    }

    /**
     * Returns the timestamp (ms) when the latest auto-backup was created.
     */
    fun getLatestAutoBackupTimestamp(context: Context): Long? {
        val internalLatest = File(File(context.filesDir, BACKUP_DIR_NAME), LATEST_BACKUP_NAME)
        if (internalLatest.exists() && internalLatest.length() > 20) {
            return internalLatest.lastModified()
        }
        val externalLatest = File(context.getExternalFilesDir("backups"), LATEST_BACKUP_NAME)
        if (externalLatest.exists() && externalLatest.length() > 20) {
            return externalLatest.lastModified()
        }
        return null
    }

    /**
     * Checks if a valid auto-backup snapshot is available.
     */
    fun isAutoBackupAvailable(context: Context): Boolean {
        return getLatestAutoBackupTimestamp(context) != null
    }

    /**
     * Queries the Room database directly via [repository] and builds a complete backup JSON object.
     * Avoids relying on uncollected or stale UI StateFlows.
     */
    suspend fun buildBackupJsonFromRepository(
        repository: com.example.data.AppRepository,
        requireNonEmpty: Boolean = false
    ): org.json.JSONObject? {
        val historyList = repository.dao.getPlaybackHistoryDirect()
        val tasksList = repository.dao.getAllTasksDirect()
        val progressList = repository.dao.getAllTaskProgressDirect()
        val dailyProgressList = repository.dao.getAllTaskDailyProgressDirect()
        val notesList = repository.dao.getAllNotesDirect()
        val tagsList = repository.dao.getAllTagsDirect()
        val labelsList = repository.dao.getAllTaskLabelsDirect()
        val vocabList = repository.vocabDao?.getAllVocabularyItemsDirect() ?: emptyList()

        if (requireNonEmpty &&
            tasksList.isEmpty() &&
            progressList.isEmpty() &&
            historyList.isEmpty() &&
            notesList.isEmpty() &&
            vocabList.isEmpty()
        ) {
            return null
        }

        val root = org.json.JSONObject()
        root.put("version", 1)
        root.put("exportedAt", System.currentTimeMillis())
        root.put("appName", "Hearmark")

        val historyArray = org.json.JSONArray()
        historyList.forEach { item ->
            val obj = org.json.JSONObject()
            obj.put("id", item.id)
            obj.put("trackId", item.trackId)
            obj.put("trackName", item.trackName)
            obj.put("completedAt", item.completedAt)
            obj.put("durationMs", item.durationMs)
            obj.put("playbackSpeed", item.playbackSpeed.toDouble())
            obj.put("actualListenedMs", item.actualListenedMs)
            obj.put("activeTasks", item.activeTasks)
            historyArray.put(obj)
        }
        root.put("playbackHistory", historyArray)

        val tasksArray = org.json.JSONArray()
        tasksList.forEach { task ->
            val obj = org.json.JSONObject()
            obj.put("id", task.id)
            obj.put("title", task.title)
            obj.put("sourceType", task.sourceType)
            obj.put("sourceId", task.sourceId)
            obj.put("targetType", task.targetType)
            obj.put("targetValue", task.targetValue)
            obj.put("scheduledDays", task.scheduledDays)
            obj.put("reminderTime", task.reminderTime)
            obj.put("startDate", task.startDate)
            if (task.endDate != null) obj.put("endDate", task.endDate)
            obj.put("isCompleted", task.isCompleted)
            obj.put("status", task.status)
            if (task.customThreshold != null) obj.put("customThreshold", task.customThreshold)
            obj.put("labels", task.labels)
            if (task.dailyTargetValue != null) obj.put("dailyTargetValue", task.dailyTargetValue)
            tasksArray.put(obj)
        }
        root.put("tasks", tasksArray)

        val progressArray = org.json.JSONArray()
        progressList.forEach { progress ->
            val obj = org.json.JSONObject()
            obj.put("taskId", progress.taskId)
            obj.put("trackId", progress.trackId)
            obj.put("completedPlayCount", progress.completedPlayCount)
            obj.put("completedDays", progress.completedDays)
            obj.put("isTrackCompleted", progress.isTrackCompleted)
            progressArray.put(obj)
        }
        root.put("taskProgress", progressArray)

        val dailyArray = org.json.JSONArray()
        dailyProgressList.forEach { dp ->
            val obj = org.json.JSONObject()
            obj.put("taskId", dp.taskId)
            obj.put("date", dp.date)
            obj.put("completedPlayCount", dp.completedPlayCount)
            dailyArray.put(obj)
        }
        root.put("taskDailyProgress", dailyArray)

        val notesArray = org.json.JSONArray()
        notesList.forEach { note ->
            val obj = org.json.JSONObject()
            obj.put("id", note.id)
            obj.put("text", note.text)
            obj.put("comment", note.comment)
            if (note.trackId != null) obj.put("trackId", note.trackId)
            if (note.trackName != null) obj.put("trackName", note.trackName)
            if (note.folderId != null) obj.put("folderId", note.folderId)
            if (note.folderName != null) obj.put("folderName", note.folderName)
            obj.put("startTimestampMs", note.startTimestampMs)
            obj.put("endTimestampMs", note.endTimestampMs)
            if (note.originStartMs != null) obj.put("originStartMs", note.originStartMs)
            obj.put("tags", note.tags)
            obj.put("createdAt", note.createdAt)
            obj.put("updatedAt", note.updatedAt)
            if (note.targetWord != null) obj.put("targetWord", note.targetWord)
            if (note.meaning != null) obj.put("meaning", note.meaning)
            if (note.contextSentence != null) obj.put("contextSentence", note.contextSentence)
            notesArray.put(obj)
        }
        root.put("notes", notesArray)

        val tagsArray = org.json.JSONArray()
        tagsList.forEach { tag ->
            val obj = org.json.JSONObject()
            obj.put("id", tag.id)
            obj.put("name", tag.name)
            if (tag.colorHex != null) obj.put("colorHex", tag.colorHex)
            obj.put("createdAt", tag.createdAt)
            tagsArray.put(obj)
        }
        root.put("noteTags", tagsArray)

        val labelsArray = org.json.JSONArray()
        labelsList.forEach { label ->
            val obj = org.json.JSONObject()
            obj.put("id", label.id)
            obj.put("name", label.name)
            obj.put("createdAt", label.createdAt)
            labelsArray.put(obj)
        }
        root.put("taskLabels", labelsArray)

        val vocabArray = org.json.JSONArray()
        vocabList.forEach { v ->
            val obj = org.json.JSONObject()
            obj.put("id", v.id)
            obj.put("targetWord", v.targetWord)
            obj.put("meaning", v.meaning)
            obj.put("contextSentence", v.contextSentence)
            if (v.noteId != null) obj.put("noteId", v.noteId)
            if (v.trackId != null) obj.put("trackId", v.trackId)
            if (v.timestampMs != null) obj.put("timestampMs", v.timestampMs)
            obj.put("timesReviewed", v.timesReviewed)
            obj.put("timesCorrect", v.timesCorrect)
            obj.put("isMastered", v.isMastered)
            if (v.lastReviewedAt != null) obj.put("lastReviewedAt", v.lastReviewedAt)
            obj.put("createdAt", v.createdAt)
            vocabArray.put(obj)
        }
        root.put("vocabularyItems", vocabArray)

        return root
    }

    /**
     * Immediately exports the latest Room database state to the auto-backup snapshot files.
     */
    suspend fun saveAutoBackupFromRepository(
        context: Context?,
        repository: com.example.data.AppRepository?
    ) {
        val ctx = context ?: return
        val repo = repository ?: return
        try {
            val root = buildBackupJsonFromRepository(repo, requireNonEmpty = true) ?: return
            saveAutoBackup(ctx, root.toString(2))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save auto-backup from repository", e)
        }
    }
}
