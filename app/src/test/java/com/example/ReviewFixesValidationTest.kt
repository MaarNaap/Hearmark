package com.example

import android.content.Context
import android.content.Intent
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.ai.GeminiKeyStore
import com.example.data.*
import com.example.player.HearmarkPlaybackService
import com.example.util.AutoBackupManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ReviewFixesValidationTest {

    @org.junit.Before
    fun setUp() {
        com.example.player.AudioPlayerManager.stop()
    }

    @Test
    fun playbackService_onStartCommand_returnsExpectedServiceStatus() {
        val serviceController = Robolectric.buildService(HearmarkPlaybackService::class.java)
        val service = serviceController.create().get()

        // When starting without active playback session, onStartCommand stops itself and returns START_NOT_STICKY
        val result = service.onStartCommand(Intent(), 0, 1)
        assertEquals(android.app.Service.START_NOT_STICKY, result)
    }

    @Test
    fun autoBackupManager_safetyBackupDatabaseFile_createsBackupsSynchronously() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbFile = context.getDatabasePath("smart_audio_tasks_db")
        dbFile.parentFile?.mkdirs()
        dbFile.writeText("sample sqlite header data v20")

        val walFile = context.getDatabasePath("smart_audio_tasks_db-wal")
        walFile.writeText("sample wal content")

        val targetVersion = 21
        val success = AutoBackupManager.safetyBackupDatabaseFile(context, targetVersion)
        assertTrue(success)

        val safetyDir = File(context.filesDir, "db_safety_backups")
        val versionedBackup = File(safetyDir, "smart_audio_tasks_db.v21.pre_migration.bak")
        val latestBackup = File(safetyDir, "smart_audio_tasks_db.pre_migration.bak")
        val versionedWal = File(safetyDir, "smart_audio_tasks_db.v21.pre_migration.bak-wal")

        assertTrue(versionedBackup.exists())
        assertEquals("sample sqlite header data v20", versionedBackup.readText())
        assertTrue(latestBackup.exists())
        assertTrue(versionedWal.exists())
        assertEquals("sample wal content", versionedWal.readText())

        val prefs = context.getSharedPreferences("db_backup_meta", Context.MODE_PRIVATE)
        assertEquals(targetVersion, prefs.getInt("last_backed_up_version", 0))
    }

    @Test
    fun geminiKeyStore_preventsPlaintextFallbackWhenKeystoreUnavailable() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val originalKeyJson = """[{"id":"k1","name":"Key 1","key":"AIzaSyD-TestKey12345"}]"""
        val activeKey = "AIzaSyD-TestKey12345"

        // In JVM/Robolectric test environment without hardware AndroidKeyStore,
        // encryptValue must NOT silently fall back to storing plaintext!
        val encrypted = GeminiKeyStore.encryptValue(activeKey)
        // Either Keystore successfully encrypted with ENC_V1 prefix, OR returned null to prevent plaintext leak
        if (encrypted == null) {
            // Verified: Never leaks plaintext
            assertNull(encrypted)
            val saveResult = GeminiKeyStore.saveKeys(
                context = context,
                savedKeysJson = originalKeyJson,
                activeKeyId = "k1",
                activeKeyString = activeKey
            )
            // saveKeys should safely refuse to write corrupt or plaintext data
            assertFalse(saveResult)

            val securePrefs = context.getSharedPreferences(GeminiKeyStore.SECURE_PREFS_NAME, Context.MODE_PRIVATE)
            assertFalse(securePrefs.contains("saved_gemini_api_keys"))
        } else {
            assertTrue(encrypted.startsWith("ENC_V1:"))
            assertFalse(encrypted.contains("AIzaSyD-TestKey12345"))
        }
    }

    @Test
    fun geminiKeyStore_decryptFailure_doesNotWipeSavedKeys() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val securePrefs = context.getSharedPreferences(GeminiKeyStore.SECURE_PREFS_NAME, Context.MODE_PRIVATE)
        // Simulate corrupted or locked Keystore ciphertext in storage
        securePrefs.edit()
            .putString("saved_gemini_api_keys", "ENC_V1:CorruptedOrLockedCiphertext")
            .putString("active_gemini_api_key_id", "key_123")
            .commit()

        val loaded = GeminiKeyStore.loadStoredKeys(context)
        // Must flag decryption failure and preserve the storage rather than wiping it
        assertTrue(loaded.decryptionFailed)
        assertEquals("key_123", loaded.activeKeyId)

        // Ensure preferences were NOT wiped
        assertEquals("ENC_V1:CorruptedOrLockedCiphertext", securePrefs.getString("saved_gemini_api_keys", null))
    }

    @Test
    fun repository_withTransaction_executesAtomically() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val repo = AppRepository(db.appDao(), db.vocabularyItemDao(), db)

        // Verify transaction executes block and returns result
        val result = repo.withTransaction {
            val task = Task(
                title = "Atomic Task",
                sourceType = "TRACKS",
                sourceId = null,
                targetType = "PLAY_COUNT",
                targetValue = 5,
                scheduledDays = "DAILY",
                reminderTime = "08:00 AM",
                startDate = System.currentTimeMillis()
            )
            val taskId = repo.insertTask(task)
            taskId
        }
        assertTrue(result > 0)
        val loaded = repo.getTaskById(result)
        assertNotNull(loaded)
        assertEquals("Atomic Task", loaded?.title)
        db.close()
    }
}
