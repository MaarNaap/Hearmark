package com.example

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.example.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DatabaseMigrationAndIndexTest {

    private fun getIndexNames(db: SupportSQLiteDatabase, tableName: String): Set<String> {
        val result = mutableSetOf<String>()
        db.query("PRAGMA index_list(`$tableName`)").use { cursor ->
            val nameCol = cursor.getColumnIndex("name")
            while (cursor.moveToNext()) {
                if (nameCol != -1) {
                    result.add(cursor.getString(nameCol))
                }
            }
        }
        return result
    }

    @Test
    fun migration18To19_createsAllExpectedRoomIndicesIdempotently() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val roomDb = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        try {
            val sqlDb = roomDb.openHelper.writableDatabase
            // Re-run DatabaseMigrations.MIGRATION_18_19.migrate to verify idempotency (CREATE INDEX IF NOT EXISTS)
            DatabaseMigrations.MIGRATION_18_19.migrate(sqlDb)

            assertTrue(getIndexNames(sqlDb, "folders").contains("index_folders_parentFolderId"))
            val trackIndices = getIndexNames(sqlDb, "audio_tracks")
            assertTrue(trackIndices.contains("index_audio_tracks_parentFolderId"))
            assertTrue(trackIndices.contains("index_audio_tracks_filePath"))
            assertTrue(trackIndices.contains("index_audio_tracks_parentTrackId"))

            assertTrue(getIndexNames(sqlDb, "playlist_tracks").contains("index_playlist_tracks_trackId"))
            assertTrue(getIndexNames(sqlDb, "task_track_progress").contains("index_task_track_progress_trackId"))
            assertTrue(getIndexNames(sqlDb, "task_daily_progress").contains("index_task_daily_progress_date"))

            val historyIndices = getIndexNames(sqlDb, "playback_history")
            assertTrue(historyIndices.contains("index_playback_history_completedAt"))
            assertTrue(historyIndices.contains("index_playback_history_trackId"))

            val noteIndices = getIndexNames(sqlDb, "notes")
            assertTrue(noteIndices.contains("index_notes_trackId"))
            assertTrue(noteIndices.contains("index_notes_folderId"))
        } finally {
            roomDb.close()
        }
    }

    @Test
    fun atomicCascadeDeletes_removeTrackAndTaskAndFolderCleanly() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val roomDb = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val repo = AppRepository(roomDb.appDao(), roomDb.vocabularyItemDao(), roomDb)

        try {
            val rootFolderId = repo.addFolder("/tmp/root", "Root Folder")
            val childFolderId = repo.addFolder("/tmp/root/sub", "Child Folder", parentFolderId = rootFolderId)

            val trackId = repo.insertTrack(
                AudioTrack(
                    filePath = "/tmp/root/sub/lesson1.mp3",
                    fileName = "lesson1",
                    duration = 60_000L,
                    parentFolderId = childFolderId,
                    isIndependent = false
                )
            )

            val taskId = repo.addTask(
                title = "Folder Task",
                sourceType = "FOLDER",
                sourceId = rootFolderId,
                targetType = "PLAY_COUNT",
                targetValue = 2,
                scheduledDays = "MONDAY",
                reminderTime = "09:00 AM",
                startDate = 1000L,
                endDate = null
            )

            repo.incrementDailyPlayCount(taskId, "2026-10-05")
            assertEquals(1, repo.getProgressForTask(taskId).size)
            assertNotNull(repo.getDailyProgress(taskId, "2026-10-05"))

            // Delete root folder atomically -> should remove root folder, child folder, track, and track progress
            repo.deleteFolder(rootFolderId)

            assertNull(repo.getFolderById(rootFolderId))
            assertNull(repo.getFolderById(childFolderId))
            assertNull(repo.getTrackById(trackId))
            assertTrue(repo.getProgressForTask(taskId).isEmpty())

            // Delete task atomically -> should remove task and its daily progress
            repo.deleteTask(taskId)
            assertNull(repo.getTaskById(taskId))
            assertNull(repo.getDailyProgress(taskId, "2026-10-05"))
        } finally {
            roomDb.close()
        }
    }

    @Test
    fun migration19To20_addsSpacedRepetitionColumnsIndexAndSeedsStrongItems() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val roomDb = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        try {
            val sqlDb = roomDb.openHelper.writableDatabase
            val baseAnsweredAt = 1_700_000_000_000L

            // Insert 3 questions simulating pre-migration state:
            // 1) Strong item (4/4 = 100% >= 75%)
            sqlDb.execSQL("""
                INSERT INTO `quiz_questions` (
                    `id`, `questionType`, `category`, `question`, `optionsJson`, `correctIndex`,
                    `explanation`, `timesAnswered`, `timesCorrect`, `lastAnsweredAt`, `createdAt`,
                    `srRepetitions`, `srIntervalDays`, `srEase`, `srLapses`, `srNextReviewAt`
                ) VALUES (
                    1, 'VOCABULARY', 'VOCABULARY', 'Strong Q', '["A","B","C","D"]', 0,
                    'Exp', 4, 4, $baseAnsweredAt, $baseAnsweredAt, 0, 0, 2.5, 0, NULL
                )
            """.trimIndent())

            // 2) Weak item (1/4 = 25% < 75%)
            sqlDb.execSQL("""
                INSERT INTO `quiz_questions` (
                    `id`, `questionType`, `category`, `question`, `optionsJson`, `correctIndex`,
                    `explanation`, `timesAnswered`, `timesCorrect`, `lastAnsweredAt`, `createdAt`,
                    `srRepetitions`, `srIntervalDays`, `srEase`, `srLapses`, `srNextReviewAt`
                ) VALUES (
                    2, 'VOCABULARY', 'VOCABULARY', 'Weak Q', '["A","B","C","D"]', 0,
                    'Exp', 4, 1, $baseAnsweredAt, $baseAnsweredAt, 0, 0, 2.5, 0, NULL
                )
            """.trimIndent())

            // 3) Untested item (0/0)
            sqlDb.execSQL("""
                INSERT INTO `quiz_questions` (
                    `id`, `questionType`, `category`, `question`, `optionsJson`, `correctIndex`,
                    `explanation`, `timesAnswered`, `timesCorrect`, `lastAnsweredAt`, `createdAt`,
                    `srRepetitions`, `srIntervalDays`, `srEase`, `srLapses`, `srNextReviewAt`
                ) VALUES (
                    3, 'VOCABULARY', 'VOCABULARY', 'Untested Q', '["A","B","C","D"]', 0,
                    'Exp', 0, 0, NULL, $baseAnsweredAt, 0, 0, 2.5, 0, NULL
                )
            """.trimIndent())

            // Execute MIGRATION_19_20
            DatabaseMigrations.MIGRATION_19_20.migrate(sqlDb)

            // Verify index_quiz_questions_srNextReviewAt exists
            assertTrue(getIndexNames(sqlDb, "quiz_questions").contains("index_quiz_questions_srNextReviewAt"))

            // Verify strong item was seeded with 3-day interval (259_200_000 ms) and repetitions = 2
            val dao = roomDb.appDao()
            runBlocking {
                val strongQ = dao.getQuestionByIdDirect(1L)
                assertNotNull(strongQ)
                assertEquals(2, strongQ!!.srRepetitions)
                assertEquals(3, strongQ.srIntervalDays)
                assertEquals(2.5f, strongQ.srEase, 0.001f)
                assertEquals(0, strongQ.srLapses)
                assertEquals(baseAnsweredAt + 259_200_000L, strongQ.srNextReviewAt)

                // Weak and untested items remain unscheduled (NULL nextReviewAt -> due immediately)
                val weakQ = dao.getQuestionByIdDirect(2L)
                assertNotNull(weakQ)
                assertEquals(0, weakQ!!.srRepetitions)
                assertEquals(0, weakQ.srIntervalDays)
                assertNull(weakQ.srNextReviewAt)

                val untestedQ = dao.getQuestionByIdDirect(3L)
                assertNotNull(untestedQ)
                assertEquals(0, untestedQ!!.srRepetitions)
                assertEquals(0, untestedQ.srIntervalDays)
                assertNull(untestedQ.srNextReviewAt)
            }
        } finally {
            roomDb.close()
        }
    }
}
