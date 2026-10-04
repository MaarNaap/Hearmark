package com.example

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.AppRepository
import com.example.data.Task
import com.example.data.TaskTrackProgress
import com.example.util.AutoBackupManager
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BackupRestoreParsingTest {

    @Test
    fun testDirectRepositoryBackupPreservesCompletedTaskAndProgress() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val repo = AppRepository(db.appDao(), db.vocabularyItemDao())

        try {
            val taskId = repo.insertTask(
                Task(
                    id = 10L,
                    title = "Daily Listening Goal",
                    sourceType = "TRACKS",
                    sourceId = null,
                    targetType = "PLAY_COUNT",
                    targetValue = 3,
                    scheduledDays = "SUNDAY,MONDAY",
                    reminderTime = "08:00 AM",
                    startDate = 1000L,
                    endDate = null,
                    isCompleted = true,
                    status = "COMPLETED"
                )
            )
            repo.insertTaskProgress(
                TaskTrackProgress(
                    taskId = taskId,
                    trackId = 99L,
                    completedPlayCount = 3,
                    isTrackCompleted = true
                )
            )

            // Build backup directly from repository without any UI StateFlow subscribers
            val jsonObj = AutoBackupManager.buildBackupJsonFromRepository(repo, requireNonEmpty = true)
            assertNotNull(jsonObj)

            val tasksArr = jsonObj!!.getJSONArray("tasks")
            assertEquals(1, tasksArr.length())
            val exportedTask = tasksArr.getJSONObject(0)
            assertTrue(exportedTask.getBoolean("isCompleted"))
            assertEquals("COMPLETED", exportedTask.getString("status"))

            val progressArr = jsonObj.getJSONArray("taskProgress")
            assertEquals(1, progressArr.length())
            val exportedProgress = progressArr.getJSONObject(0)
            assertEquals(3, exportedProgress.getInt("completedPlayCount"))
            assertTrue(exportedProgress.getBoolean("isTrackCompleted"))
        } finally {
            db.close()
        }
    }

    @Test
    fun testBomStripping() {
        val rawWithBom = "\uFEFF{\"version\": 1, \"tasks\": []}"
        val clean = rawWithBom.trim().removePrefix("\uFEFF").trim()
        assertTrue(clean.startsWith("{"))
        val obj = JSONObject(clean)
        assertEquals(1, obj.getInt("version"))
    }

    @Test
    fun testJsonArrayRootDetection() {
        val rawArray = "[{\"title\": \"Task 1\", \"targetType\": \"PLAY_COUNT\"}]"
        assertTrue(rawArray.trim().startsWith("["))
        val arr = JSONArray(rawArray)
        assertEquals(1, arr.length())
        val item = arr.getJSONObject(0)
        assertEquals("Task 1", item.getString("title"))
    }

    @Test
    fun testEnvelopeUnpacking() {
        val rawWithDataEnvelope = "{\"data\": {\"playbackHistory\": [{\"trackName\": \"Track 1\"}]}}"
        val root = JSONObject(rawWithDataEnvelope)
        val effective = if (root.has("data") && root.optJSONObject("data") != null) root.optJSONObject("data")!! else root
        assertTrue(effective.has("playbackHistory"))
        assertEquals(1, effective.getJSONArray("playbackHistory").length())
    }

    @Test
    fun testFlexibleTypesInJsonObject() {
        val json = JSONObject("""
            {
                "id": "42",
                "customThreshold": "85",
                "playbackSpeed": "1.5",
                "isCompleted": "true",
                "nullField": null
            }
        """.trimIndent())

        val idStr = json.opt("id")
        val idLong = when (idStr) {
            is Number -> idStr.toLong()
            is String -> idStr.trim().toLongOrNull()
            else -> null
        }
        assertEquals(42L, idLong)

        val threshold = when (val t = json.opt("customThreshold")) {
            is Number -> t.toInt()
            is String -> t.trim().toIntOrNull()
            else -> null
        }
        assertEquals(85, threshold)

        val speed = when (val s = json.opt("playbackSpeed")) {
            is Number -> s.toDouble()
            is String -> s.trim().toDoubleOrNull()
            else -> null
        }
        assertEquals(1.5, speed ?: 0.0, 0.001)

        val completed = when (val c = json.opt("isCompleted")) {
            is Boolean -> c
            is String -> c.trim().lowercase() == "true"
            else -> false
        }
        assertTrue(completed)
    }

    @Test
    fun testNormalizeReminderTimeHandlesArabicDigitsAndMarkers() {
        val sdf = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.US)

        val cases = listOf(
            "09:00 AM" to "09:00 AM",
            "٠٩:٠٠ AM" to "09:00 AM",
            "۰۹:۳۰ PM" to "09:30 PM",
            "٠٨:١٥ ص" to "08:15 AM",
            "٠٧:٤٥ مساءً" to "07:45 PM"
        )

        for ((raw, expected) in cases) {
            val normalized = com.example.receiver.AlarmReceiver.normalizeReminderTime(raw)
            assertEquals(expected, normalized)
            assertNotNull("Expected normalized time '$normalized' to parse cleanly", sdf.parse(normalized))
        }
    }

    @Test
    fun testRescheduleAllActiveTasksSchedulesOnlyActiveUncompletedTasks() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val tasks = listOf(
            Task(
                id = 101L,
                title = "Active Task 1",
                sourceType = "TRACKS",
                sourceId = null,
                targetType = "PLAY_COUNT",
                targetValue = 2,
                scheduledDays = "SUNDAY,MONDAY",
                reminderTime = "٠٩:٠٠ AM",
                startDate = 1000L,
                endDate = null,
                isCompleted = false,
                status = "ACTIVE"
            ),
            Task(
                id = 102L,
                title = "Completed Task",
                sourceType = "TRACKS",
                sourceId = null,
                targetType = "PLAY_COUNT",
                targetValue = 2,
                scheduledDays = "SUNDAY",
                reminderTime = "10:00 AM",
                startDate = 1000L,
                endDate = null,
                isCompleted = true,
                status = "COMPLETED"
            )
        )

        val count = com.example.receiver.AlarmReceiver.rescheduleAllActiveTasks(context, tasks)
        assertEquals(1, count)
    }
}
