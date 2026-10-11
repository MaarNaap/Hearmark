package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DeleteReimportRelinkRoundTripTest {

    @Test
    fun roundTrip_deleteParentWithKeep_reimportSameFile_relinksNotesAndQuestions() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val repo = AppRepository(db.appDao(), db.vocabularyItemDao(), db)

        val tempAudio = File(context.filesDir, "audio_roundtrip.mp3")
        tempAudio.writeText("fake audio binary")

        try {
            val folderId = repo.addFolder(context.filesDir.absolutePath, "AudioCourse")
            val originalTrackId = repo.insertTrack(
                AudioTrack(
                    filePath = tempAudio.absolutePath,
                    fileName = "audio_roundtrip",
                    duration = 100_000L,
                    parentFolderId = folderId
                )
            )

            val originalNoteId = repo.insertNote(
                Note(
                    trackId = originalTrackId,
                    trackName = "audio_roundtrip",
                    folderId = folderId,
                    folderName = "AudioCourse",
                    startTimestampMs = 12_000L,
                    endTimestampMs = 18_000L,
                    text = "Persisted learning note"
                )
            )

            // Step 1: User deletes track with options: keep notes, keep questions
            val trackToDelete = repo.getTrackById(originalTrackId)!!
            repo.deleteTrack(trackToDelete, DeleteOptions(deleteNotes = false, deleteComprehension = false, deleteVocabulary = false))

            // Verify track is gone, but note survived and is detached
            assertNull(repo.getTrackById(originalTrackId))
            val detachedNote = repo.getNoteById(originalNoteId)
            assertNotNull(detachedNote)
            assertNull("Note should have null trackId when detached", detachedNote!!.trackId)
            assertEquals("audio_roundtrip", detachedNote.trackName)
            assertEquals("AudioCourse", detachedNote.folderName)

            // Step 2: User re-imports the physical audio file (creates new track with new id)
            tempAudio.writeText("fake audio binary reimported")
            val newTrackId = repo.insertTrack(
                AudioTrack(
                    filePath = tempAudio.absolutePath,
                    fileName = "audio_roundtrip",
                    duration = 100_000L,
                    parentFolderId = folderId
                )
            )
            assertNotEquals(originalTrackId, newTrackId)

            // Step 3: Run automatic relink sweep
            val relinkedCount = repo.relinkNotesToActiveTracks()
            assertTrue("Expected at least 1 note relinked", relinkedCount >= 1)

            // Step 4: Verify note is successfully relinked to the new track
            val relinkedNote = repo.getNoteById(originalNoteId)
            assertNotNull(relinkedNote)
            assertEquals(newTrackId, relinkedNote!!.trackId)
            assertEquals("audio_roundtrip", relinkedNote.trackName)
            assertEquals(folderId, relinkedNote.folderId)
            assertEquals("AudioCourse", relinkedNote.folderName)
        } finally {
            if (tempAudio.exists()) tempAudio.delete()
            db.close()
        }
    }

    @Test
    fun relink_prefersMatchingFolderAndFallsBackToTrackNameAlone() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val repo = AppRepository(db.appDao(), db.vocabularyItemDao(), db)

        val dirA = File(context.filesDir, "FolderA").apply { mkdirs() }
        val dirB = File(context.filesDir, "FolderB").apply { mkdirs() }
        val fileA = File(dirA, "lesson1.mp3").apply { writeText("audio A") }
        val fileB = File(dirB, "lesson1.mp3").apply { writeText("audio B") }
        val fileC = File(dirB, "unique_track.mp3").apply { writeText("audio C") }

        try {
            val folderAId = repo.addFolder(dirA.absolutePath, "FolderA")
            val folderBId = repo.addFolder(dirB.absolutePath, "FolderB")

            val trackAId = repo.insertTrack(
                AudioTrack(filePath = fileA.absolutePath, fileName = "lesson1", duration = 50_000L, parentFolderId = folderAId)
            )
            val trackBId = repo.insertTrack(
                AudioTrack(filePath = fileB.absolutePath, fileName = "lesson1", duration = 50_000L, parentFolderId = folderBId)
            )
            val trackCId = repo.insertTrack(
                AudioTrack(filePath = fileC.absolutePath, fileName = "unique_track", duration = 50_000L, parentFolderId = folderBId)
            )

            // Detached note from FolderB with duplicate name "lesson1" -> should prefer trackBId over trackAId
            val noteBId = repo.insertNote(
                Note(
                    trackId = null,
                    trackName = "lesson1",
                    folderId = null,
                    folderName = "FolderB",
                    startTimestampMs = 1000L,
                    endTimestampMs = 2000L,
                    text = "Note for FolderB lesson1"
                )
            )

            // Detached note from OldFolder with unique name "unique_track" -> should fall back to name match (trackCId)
            val noteFallbackId = repo.insertNote(
                Note(
                    trackId = null,
                    trackName = "unique_track",
                    folderId = null,
                    folderName = "OldDeletedFolder",
                    startTimestampMs = 3000L,
                    endTimestampMs = 4000L,
                    text = "Note with renamed folder"
                )
            )

            // Detached comprehension question with cached trackName "unique_track" -> should relink to trackCId
            val compQId = repo.insertQuizQuestion(
                QuizQuestion(
                    trackId = null,
                    trackName = "unique_track",
                    noteId = null,
                    questionType = "MCQ",
                    category = "COMPREHENSION",
                    question = "What happened?",
                    optionsJson = "[\"A\",\"B\"]",
                    correctIndex = 0,
                    explanation = "exp"
                )
            )

            repo.relinkNotesToActiveTracks()

            val updatedNoteB = repo.getNoteById(noteBId)!!
            assertEquals("Should prefer track in matching folder FolderB", trackBId, updatedNoteB.trackId)
            assertNotEquals(trackAId, updatedNoteB.trackId)

            val updatedNoteFallback = repo.getNoteById(noteFallbackId)!!
            assertEquals("Should fall back to track name match when folder differs", trackCId, updatedNoteFallback.trackId)

            val updatedCompQ = repo.dao.getQuestionByIdDirect(compQId)!!
            assertEquals("Detached comprehension question should relink by trackName", trackCId, updatedCompQ.trackId)
        } finally {
            dirA.deleteRecursively()
            dirB.deleteRecursively()
            db.close()
        }
    }

    @Test
    fun sceneNormalizationAndDeletion_preservesParentNotesAndQuestions() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val repo = AppRepository(db.appDao(), db.vocabularyItemDao(), db)
        val audioFile = File(context.filesDir, "parent_ep.mp3").apply { writeText("audio") }

        try {
            val parentId = repo.insertTrack(
                AudioTrack(
                    filePath = audioFile.absolutePath,
                    fileName = "parent_ep",
                    duration = 120_000L,
                    isVirtualScene = false
                )
            )
            val sceneId = repo.insertTrack(
                AudioTrack(
                    filePath = audioFile.absolutePath,
                    fileName = "Scene 1",
                    duration = 30_000L,
                    startOffsetMs = 0L,
                    endOffsetMs = 30_000L,
                    parentTrackId = parentId,
                    isVirtualScene = true
                )
            )

            // Legacy note and comprehension question mistakenly attached to sceneId
            val legacyNoteId = repo.insertNote(
                Note(trackId = sceneId, trackName = "Scene 1", startTimestampMs = 5000L, endTimestampMs = 10000L, text = "Scene note")
            )
            val legacyCompId = repo.insertQuizQuestion(
                QuizQuestion(
                    trackId = sceneId,
                    trackName = "Scene 1",
                    noteId = null,
                    questionType = "MCQ",
                    category = "COMPREHENSION",
                    question = "Scene Q",
                    optionsJson = "[\"A\",\"B\"]",
                    correctIndex = 0,
                    explanation = "exp"
                )
            )

            // Deleting the virtual scene should reassign both to parentId and never delete them
            val sceneTrack = repo.getTrackById(sceneId)!!
            repo.deleteTrack(sceneTrack, DeleteOptions(deleteNotes = true, deleteComprehension = true, deleteVocabulary = true))

            assertNull(repo.getTrackById(sceneId))
            val noteAfterSceneDelete = repo.getNoteById(legacyNoteId)
            assertNotNull(noteAfterSceneDelete)
            assertEquals(parentId, noteAfterSceneDelete!!.trackId)

            val compAfterSceneDelete = repo.dao.getQuestionByIdDirect(legacyCompId)
            assertNotNull(compAfterSceneDelete)
            assertEquals(parentId, compAfterSceneDelete!!.trackId)
        } finally {
            audioFile.delete()
            db.close()
        }
    }
}

