package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.*
import com.example.util.AutoBackupManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BackupRestoreRoundTripTest {

    @Test
    fun buildBackupJsonFromRepository_exportsAllEntitiesAndRelationships() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val repo = AppRepository(db.appDao(), db.vocabularyItemDao(), db)

        try {
            val folderId = repo.addFolder("/storage/Arabic", "Arabic Course")
            val trackId = repo.insertTrack(
                AudioTrack(
                    filePath = "/storage/Arabic/ep1.mp3",
                    fileName = "ep1",
                    duration = 120_000L,
                    parentFolderId = folderId,
                    isIndependent = false,
                    playCount = 4,
                    listenedSegments = "0,1,2,3"
                )
            )
            val playlistId = repo.addPlaylist("Favorites")
            repo.addTrackToPlaylist(playlistId, trackId)

            val noteId = repo.insertNote(
                Note(
                    trackId = trackId,
                    trackName = "ep1",
                    folderId = folderId,
                    folderName = "Arabic Course",
                    startTimestampMs = 5000L,
                    endTimestampMs = 12000L,
                    text = "Important phrase",
                    comment = "Review daily",
                    tags = "grammar,important",
                    createdAt = 1000L,
                    updatedAt = 2000L,
                    targetWord = "مرحبا",
                    meaning = "Hello"
                )
            )

            repo.insertPlaybackHistory(
                PlaybackHistory(
                    trackId = trackId,
                    trackName = "ep1",
                    completedAt = 9000L,
                    durationMs = 120_000L,
                    playbackSpeed = 1.25f,
                    actualListenedMs = 110_000L,
                    activeTasks = "Playlist Goal"
                )
            )

            val taskId = repo.addTask(
                title = "Playlist Goal",
                sourceType = "PLAYLIST",
                sourceId = playlistId,
                targetType = "PLAY_COUNT",
                targetValue = 5,
                scheduledDays = "SUNDAY,TUESDAY",
                reminderTime = "08:30 AM",
                startDate = 5000L,
                endDate = null,
                labels = "daily"
            )
            repo.incrementDailyPlayCount(taskId, "2026-10-05")

            val backupJson = AutoBackupManager.buildBackupJsonFromRepository(repo, requireNonEmpty = true)
            assertNotNull(backupJson)

            val historyArr = backupJson!!.getJSONArray("playbackHistory")
            val notesArr = backupJson.getJSONArray("notes")
            val tasksArr = backupJson.getJSONArray("tasks")
            val progressArr = backupJson.getJSONArray("taskProgress")
            val dailyArr = backupJson.getJSONArray("taskDailyProgress")
            val vocabArr = backupJson.getJSONArray("vocabularyItems")
            val labelsArr = backupJson.getJSONArray("taskLabels")

            assertEquals(1, historyArr.length())
            assertEquals("ep1", historyArr.getJSONObject(0).getString("trackName"))
            assertEquals(110_000L, historyArr.getJSONObject(0).getLong("actualListenedMs"))

            assertEquals(1, notesArr.length())
            assertEquals("مرحبا", notesArr.getJSONObject(0).getString("targetWord"))
            assertEquals("Arabic Course", notesArr.getJSONObject(0).getString("folderName"))

            assertEquals(1, tasksArr.length())
            assertEquals(taskId, tasksArr.getJSONObject(0).getLong("id"))
            assertEquals(1, progressArr.length())
            assertEquals(trackId, progressArr.getJSONObject(0).getLong("trackId"))

            assertEquals(1, dailyArr.length())
            assertEquals("2026-10-05", dailyArr.getJSONObject(0).getString("date"))
            assertEquals(1, dailyArr.getJSONObject(0).getInt("completedPlayCount"))

            assertEquals(1, vocabArr.length())
            assertEquals("مرحبا", vocabArr.getJSONObject(0).getString("targetWord"))
            assertEquals(1, labelsArr.length())
        } finally {
            db.close()
        }
    }

    @Test
    fun safetyBackupDatabaseFile_isVersionGatedAndIncludesWalFile() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("db_backup_meta", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()

        val dbFile = context.getDatabasePath("smart_audio_tasks_db")
        dbFile.parentFile?.mkdirs()
        dbFile.writeText("dummy_sqlite_payload")
        val walFile = context.getDatabasePath("smart_audio_tasks_db-wal")
        walFile.writeText("dummy_wal_payload")

        val safetyDir = File(context.filesDir, "db_safety_backups")
        val bakDb = File(safetyDir, "smart_audio_tasks_db.v19.pre_migration.bak")
        val bakWal = File(safetyDir, "smart_audio_tasks_db.v19.pre_migration.bak-wal")

        try {
            AutoBackupManager.safetyBackupDatabaseFile(context, targetVersion = 19)

            assertTrue(bakDb.exists())
            assertTrue(bakWal.exists())
            assertEquals("dummy_sqlite_payload", bakDb.readText())
            assertEquals("dummy_wal_payload", bakWal.readText())

            // Modify source file and verify calling with same targetVersion = 19 does NOT overwrite the v19 backup
            dbFile.writeText("modified_after_migration")
            AutoBackupManager.safetyBackupDatabaseFile(context, targetVersion = 19)
            assertEquals("dummy_sqlite_payload", bakDb.readText())
        } finally {
            dbFile.delete()
            walFile.delete()
            safetyDir.deleteRecursively()
            prefs.edit().clear().commit()
        }
    }
}
