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
}
