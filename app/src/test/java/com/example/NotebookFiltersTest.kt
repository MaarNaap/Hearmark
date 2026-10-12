package com.example

import com.example.data.AudioTrack
import com.example.data.Folder
import com.example.data.Note
import com.example.ui.extractAllNotebookTags
import com.example.ui.filterNotes
import org.junit.Assert.assertEquals
import org.junit.Test

class NotebookFiltersTest {

    private val sampleFolders = listOf(
        Folder(id = 1L, folderPath = "/music/podcasts", folderName = "Podcasts"),
        Folder(id = 2L, folderPath = "/music/audiobooks", folderName = "Audiobooks")
    )

    private val sampleTracks = listOf(
        AudioTrack(id = 10L, filePath = "/music/podcasts/ep1.mp3", fileName = "ep1.mp3", duration = 60000L, parentFolderId = 1L),
        AudioTrack(id = 20L, filePath = "/music/audiobooks/ch1.mp3", fileName = "ch1.mp3", duration = 60000L, parentFolderId = 2L)
    )

    private val sampleNotes = listOf(
        Note(
            id = 101L,
            text = "Serendipity means a happy accident",
            comment = "Good vocabulary word",
            tags = "vocab, favorite",
            trackId = 10L,
            trackName = "ep1.mp3",
            folderId = 1L,
            folderName = "Podcasts"
        ),
        Note(
            id = 102L,
            text = "Grammar rule for subjunctive mood",
            comment = "Important for exam",
            tags = "grammar",
            trackId = 20L,
            trackName = "ch1.mp3",
            folderId = 2L,
            folderName = "Audiobooks"
        ),
        Note(
            id = 103L,
            text = "Unlinked note with folder name fallback",
            comment = "",
            tags = "VOCAB, review",
            trackId = null,
            trackName = "ep1.mp3",
            folderId = null,
            folderName = "podcasts"
        )
    )

    @Test
    fun filterNotes_searchQueryMatchesTextCommentTagsAndTrack() {
        val byText = filterNotes(sampleNotes, "serendipity", emptySet(), emptySet(), null, sampleFolders, sampleTracks)
        assertEquals(listOf(101L), byText.map { it.id })

        val byComment = filterNotes(sampleNotes, "exam", emptySet(), emptySet(), null, sampleFolders, sampleTracks)
        assertEquals(listOf(102L), byComment.map { it.id })

        val byTrack = filterNotes(sampleNotes, "ep1", emptySet(), emptySet(), null, sampleFolders, sampleTracks)
        assertEquals(listOf(101L, 103L), byTrack.map { it.id })
    }

    @Test
    fun filterNotes_folderFilterMatchesIdAndFallbackFolderName() {
        val byFolder1 = filterNotes(sampleNotes, "", setOf(1L), emptySet(), null, sampleFolders, sampleTracks)
        assertEquals(listOf(101L, 103L), byFolder1.map { it.id })

        val byFolder2 = filterNotes(sampleNotes, "", setOf(2L), emptySet(), null, sampleFolders, sampleTracks)
        assertEquals(listOf(102L), byFolder2.map { it.id })
    }

    @Test
    fun filterNotes_tagFilterIsCaseInsensitive() {
        val byVocab = filterNotes(sampleNotes, "", emptySet(), setOf("vocab"), null, sampleFolders, sampleTracks)
        assertEquals(listOf(101L, 103L), byVocab.map { it.id })
    }

    @Test
    fun filterNotes_trackFilterMatchesIndividualTrack() {
        val byTrack10 = filterNotes(
            notes = sampleNotes,
            searchQuery = "",
            selectedFolderIds = emptySet(),
            selectedTags = emptySet(),
            selectedTrackId = null,
            folders = sampleFolders,
            tracks = sampleTracks,
            selectedTrackIds = setOf(10L)
        )
        // Matches note 101 (linked to track 10) and note 103 (fallback trackName ep1.mp3)
        assertEquals(listOf(101L, 103L), byTrack10.map { it.id })

        val byTrack20 = filterNotes(
            notes = sampleNotes,
            searchQuery = "",
            selectedFolderIds = emptySet(),
            selectedTags = emptySet(),
            selectedTrackId = null,
            folders = sampleFolders,
            tracks = sampleTracks,
            selectedTrackIds = setOf(20L)
        )
        assertEquals(listOf(102L), byTrack20.map { it.id })
    }

    @Test
    fun filterNotes_mixedFolderAndTrackFilterMatchesBoth() {
        // Folder 2 (contains note 102) + individual track 10 from Folder 1 (contains notes 101 and 103)
        val mixed = filterNotes(
            notes = sampleNotes,
            searchQuery = "",
            selectedFolderIds = setOf(2L),
            selectedTags = emptySet(),
            selectedTrackId = null,
            folders = sampleFolders,
            tracks = sampleTracks,
            selectedTrackIds = setOf(10L)
        )
        assertEquals(listOf(101L, 102L, 103L), mixed.map { it.id })
    }

    @Test
    fun extractAllNotebookTags_deduplicatesCaseInsensitivelyAndSorts() {
        val tags = extractAllNotebookTags(sampleNotes)
        assertEquals(listOf("favorite", "grammar", "review", "vocab"), tags.map { it.lowercase() })
    }
}
