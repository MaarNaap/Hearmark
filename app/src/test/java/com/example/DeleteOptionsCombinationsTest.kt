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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DeleteOptionsCombinationsTest {

    private fun setupTestDb(context: Context): Pair<AppDatabase, AppRepository> {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val repo = AppRepository(db.appDao(), db.vocabularyItemDao(), db)
        return Pair(db, repo)
    }

    data class TestEntities(val trackId: Long, val noteId: Long, val compQId: Long, val vocabQId: Long)

    private suspend fun seedTestData(repo: AppRepository): TestEntities {
        val folderId = repo.addFolder("/test", "TestFolder")
        val trackId = repo.insertTrack(
            AudioTrack(
                filePath = "/test/audio.mp3",
                fileName = "audio",
                duration = 60_000L,
                parentFolderId = folderId
            )
        )
        val noteId = repo.insertNote(
            Note(
                trackId = trackId,
                trackName = "audio",
                folderId = folderId,
                folderName = "TestFolder",
                startTimestampMs = 5000L,
                endTimestampMs = 10000L,
                text = "Test note"
            )
        )
        // Comprehension question
        val compQId = repo.insertQuizQuestion(
            QuizQuestion(
                trackId = trackId,
                noteId = null,
                questionType = "MCQ",
                category = "COMPREHENSION",
                question = "Comprehension test question",
                optionsJson = "[\"A\",\"B\"]",
                correctIndex = 0,
                explanation = "exp"
            )
        )
        // Vocabulary question
        val vocabQId = repo.insertQuizQuestion(
            QuizQuestion(
                trackId = trackId,
                noteId = noteId,
                questionType = "VOCABULARY",
                category = "VOCABULARY",
                question = "Vocabulary test question",
                optionsJson = "[\"A\",\"B\"]",
                correctIndex = 0,
                explanation = "exp"
            )
        )
        return TestEntities(trackId, noteId, compQId, vocabQId)
    }

    // Combination 1: (deleteNotes=false, deleteComprehension=false, deleteVocabulary=false) -> ALL KEPT & DETACHED
    @Test
    fun deleteOptions_keepAll_detachesNotesAndQuestionsWithCachedTrackName() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val (db, repo) = setupTestDb(context)
        try {
            val (trackId, noteId, compQId, vocabQId) = seedTestData(repo)
            val track = repo.getTrackById(trackId)!!

            repo.deleteTrack(track, DeleteOptions(deleteNotes = false, deleteComprehension = false, deleteVocabulary = false))

            // Track is deleted
            assertNull(repo.getTrackById(track.id))

            // Note is kept and detached
            val note = repo.getNoteById(noteId)
            assertNotNull(note)
            assertNull(note!!.trackId)
            assertEquals("audio", note.trackName)
            assertEquals("TestFolder", note.folderName)

            // Comp question is kept and detached with cached trackName
            val compQ = repo.dao.getQuestionByIdDirect(compQId)
            assertNotNull(compQ)
            assertNull(compQ!!.trackId)
            assertEquals("audio", compQ.trackName)

            // Vocab question is kept and detached
            val vocabQ = repo.dao.getQuestionByIdDirect(vocabQId)
            assertNotNull(vocabQ)
            assertNull(vocabQ!!.trackId)
            assertEquals("audio", vocabQ.trackName)
        } finally {
            db.close()
        }
    }

    // Combination 2: (deleteNotes=true, deleteComprehension=false, deleteVocabulary=false)
    @Test
    fun deleteOptions_deleteNotesOnly_deletesNotesKeepsQuestionsDetached() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val (db, repo) = setupTestDb(context)
        try {
            val (trackId, noteId, compQId, vocabQId) = seedTestData(repo)
            val track = repo.getTrackById(trackId)!!

            repo.deleteTrack(track, DeleteOptions(deleteNotes = true, deleteComprehension = false, deleteVocabulary = false))

            assertNull(repo.getNoteById(noteId))
            assertNotNull(repo.dao.getQuestionByIdDirect(compQId))
            assertNotNull(repo.dao.getQuestionByIdDirect(vocabQId))
        } finally {
            db.close()
        }
    }

    // Combination 3: (deleteNotes=false, deleteComprehension=true, deleteVocabulary=false)
    @Test
    fun deleteOptions_deleteComprehensionOnly_deletesCompKeepsNotesAndVocab() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val (db, repo) = setupTestDb(context)
        try {
            val (trackId, noteId, compQId, vocabQId) = seedTestData(repo)
            val track = repo.getTrackById(trackId)!!

            repo.deleteTrack(track, DeleteOptions(deleteNotes = false, deleteComprehension = true, deleteVocabulary = false))

            assertNotNull(repo.getNoteById(noteId))
            assertNull(repo.dao.getQuestionByIdDirect(compQId))
            assertNotNull(repo.dao.getQuestionByIdDirect(vocabQId))
        } finally {
            db.close()
        }
    }

    // Combination 4: (deleteNotes=false, deleteComprehension=false, deleteVocabulary=true)
    @Test
    fun deleteOptions_deleteVocabularyOnly_deletesVocabKeepsNotesAndComp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val (db, repo) = setupTestDb(context)
        try {
            val (trackId, noteId, compQId, vocabQId) = seedTestData(repo)
            val track = repo.getTrackById(trackId)!!

            repo.deleteTrack(track, DeleteOptions(deleteNotes = false, deleteComprehension = false, deleteVocabulary = true))

            assertNotNull(repo.getNoteById(noteId))
            assertNotNull(repo.dao.getQuestionByIdDirect(compQId))
            assertNull(repo.dao.getQuestionByIdDirect(vocabQId))
        } finally {
            db.close()
        }
    }

    // Combination 5: (deleteNotes=true, deleteComprehension=true, deleteVocabulary=false)
    @Test
    fun deleteOptions_deleteNotesAndComp_deletesNotesAndCompKeepsVocab() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val (db, repo) = setupTestDb(context)
        try {
            val (trackId, noteId, compQId, vocabQId) = seedTestData(repo)
            val track = repo.getTrackById(trackId)!!

            repo.deleteTrack(track, DeleteOptions(deleteNotes = true, deleteComprehension = true, deleteVocabulary = false))

            assertNull(repo.getNoteById(noteId))
            assertNull(repo.dao.getQuestionByIdDirect(compQId))
            assertNotNull(repo.dao.getQuestionByIdDirect(vocabQId))
        } finally {
            db.close()
        }
    }

    // Combination 6: (deleteNotes=true, deleteComprehension=false, deleteVocabulary=true)
    @Test
    fun deleteOptions_deleteNotesAndVocab_deletesNotesAndVocabKeepsComp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val (db, repo) = setupTestDb(context)
        try {
            val (trackId, noteId, compQId, vocabQId) = seedTestData(repo)
            val track = repo.getTrackById(trackId)!!

            repo.deleteTrack(track, DeleteOptions(deleteNotes = true, deleteComprehension = false, deleteVocabulary = true))

            assertNull(repo.getNoteById(noteId))
            assertNotNull(repo.dao.getQuestionByIdDirect(compQId))
            assertNull(repo.dao.getQuestionByIdDirect(vocabQId))
        } finally {
            db.close()
        }
    }

    // Combination 7: (deleteNotes=false, deleteComprehension=true, deleteVocabulary=true)
    @Test
    fun deleteOptions_deleteBothQuestions_keepsNotesOnly() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val (db, repo) = setupTestDb(context)
        try {
            val (trackId, noteId, compQId, vocabQId) = seedTestData(repo)
            val track = repo.getTrackById(trackId)!!

            repo.deleteTrack(track, DeleteOptions(deleteNotes = false, deleteComprehension = true, deleteVocabulary = true))

            assertNotNull(repo.getNoteById(noteId))
            assertNull(repo.dao.getQuestionByIdDirect(compQId))
            assertNull(repo.dao.getQuestionByIdDirect(vocabQId))
        } finally {
            db.close()
        }
    }

    // Combination 8: (deleteNotes=true, deleteComprehension=true, deleteVocabulary=true) -> DELETE ALL
    @Test
    fun deleteOptions_deleteAll_removesEverything() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val (db, repo) = setupTestDb(context)
        try {
            val (trackId, noteId, compQId, vocabQId) = seedTestData(repo)
            val track = repo.getTrackById(trackId)!!

            repo.deleteTrack(track, DeleteOptions(deleteNotes = true, deleteComprehension = true, deleteVocabulary = true))

            assertNull(repo.getNoteById(noteId))
            assertNull(repo.dao.getQuestionByIdDirect(compQId))
            assertNull(repo.dao.getQuestionByIdDirect(vocabQId))
        } finally {
            db.close()
        }
    }
}
