package com.example.util

import android.content.Context
import android.util.Log
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

    /**
     * Safely copies the SQLite database file before Room runs migrations.
     * Preserves a clean copy of the database before any schema update.
     */
    fun safetyBackupDatabaseFile(context: Context) {
        try {
            val dbFile = context.getDatabasePath("smart_audio_tasks_db")
            if (dbFile.exists() && dbFile.length() > 0) {
                val safetyDir = File(context.filesDir, SAFETY_DB_DIR_NAME)
                if (!safetyDir.exists()) safetyDir.mkdirs()
                val backupFile = File(safetyDir, "smart_audio_tasks_db.pre_migration.bak")
                dbFile.copyTo(backupFile, overwrite = true)
                Log.d(TAG, "Safety backup of database created (${dbFile.length()} bytes)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create database safety backup", e)
        }
    }

    /**
     * Writes an automatic JSON snapshot to both internal files and external app files dir.
     * Keeps the latest snapshot and rolling daily history (up to 5 days).
     */
    fun saveAutoBackup(context: Context, jsonString: String) {
        if (jsonString.isBlank()) return
        try {
            // 1. Internal storage auto_backups/
            val internalDir = File(context.filesDir, BACKUP_DIR_NAME)
            if (!internalDir.exists()) internalDir.mkdirs()

            val latestFile = File(internalDir, LATEST_BACKUP_NAME)
            latestFile.writeText(jsonString, Charsets.UTF_8)

            // Rolling daily snapshot
            val dateTag = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
            val dailyFile = File(internalDir, "hearmark_autosnapshot_$dateTag.json")
            if (!dailyFile.exists()) {
                dailyFile.writeText(jsonString, Charsets.UTF_8)
            }

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
}
