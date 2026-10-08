package com.example

import com.example.data.AudioTrack
import com.example.data.Note
import com.example.ui.belongsToScene
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SceneNoteBoundaryTest {

    private val parentTrack = AudioTrack(
        id = 100L,
        filePath = "/storage/audio/episode1.mp3",
        fileName = "episode1.mp3",
        duration = 120_000L,
        isVirtualScene = false
    )

    // Scene A: [0, 30_000)
    private val sceneA = AudioTrack(
        id = 101L,
        filePath = "/storage/audio/episode1.mp3",
        fileName = "Scene 1",
        duration = 30_000L,
        startOffsetMs = 0L,
        endOffsetMs = 30_000L,
        parentTrackId = 100L,
        isVirtualScene = true
    )

    // Scene B: [30_000, 60_000)
    private val sceneB = AudioTrack(
        id = 102L,
        filePath = "/storage/audio/episode1.mp3",
        fileName = "Scene 2",
        duration = 30_000L,
        startOffsetMs = 30_000L,
        endOffsetMs = 60_000L,
        parentTrackId = 100L,
        isVirtualScene = true
    )

    // Scene C: [60_000, 90_000) using duration only (endOffsetMs = null)
    private val sceneC = AudioTrack(
        id = 103L,
        filePath = "/storage/audio/episode1.mp3",
        fileName = "Scene 3",
        duration = 30_000L,
        startOffsetMs = 60_000L,
        endOffsetMs = null,
        parentTrackId = 100L,
        isVirtualScene = true
    )

    private val allTracks = listOf(parentTrack, sceneA, sceneB, sceneC)

    @Test
    fun belongsToScene_noteOnParentAtExactBoundaryLandsInNextSceneOnly() {
        // Boundary at 30_000 ms:
        // Scene A is [0, 30000) -> note at 30_000 should NOT belong to Scene A
        // Scene B is [30000, 60000) -> note at 30_000 SHOULD belong to Scene B
        val boundaryNote = Note(
            id = 1L,
            trackId = parentTrack.id,
            startTimestampMs = 30_000L,
            endTimestampMs = 35_000L,
            text = "Boundary Note"
        )

        assertFalse("Note at 30,000ms must not belong to Scene A [0, 30000)", belongsToScene(boundaryNote, sceneA, allTracks))
        assertTrue("Note at 30,000ms must belong to Scene B [30000, 60000)", belongsToScene(boundaryNote, sceneB, allTracks))
    }

    @Test
    fun belongsToScene_originStartMsTakesPrecedenceOverStartTimestampMs() {
        val noteWithOrigin = Note(
            id = 2L,
            trackId = parentTrack.id,
            startTimestampMs = 15_000L, // in Scene A
            originStartMs = 35_000L,   // in Scene B
            text = "Adjusted Note"
        )

        assertFalse("Origin is in Scene B, should not belong to Scene A", belongsToScene(noteWithOrigin, sceneA, allTracks))
        assertTrue("Origin is in Scene B, should belong to Scene B", belongsToScene(noteWithOrigin, sceneB, allTracks))
    }

    @Test
    fun belongsToScene_computesEndFromDurationWhenEndOffsetMsIsNull() {
        val noteAtStartOfC = Note(
            id = 3L,
            trackId = parentTrack.id,
            startTimestampMs = 60_000L,
            text = "Scene C start"
        )
        val noteInsideC = Note(
            id = 4L,
            trackId = parentTrack.id,
            startTimestampMs = 75_000L,
            text = "Scene C inside"
        )
        val noteAtEndOfC = Note(
            id = 5L,
            trackId = parentTrack.id,
            startTimestampMs = 90_000L,
            text = "Scene C end boundary"
        )

        assertTrue(belongsToScene(noteAtStartOfC, sceneC, allTracks))
        assertTrue(belongsToScene(noteInsideC, sceneC, allTracks))
        assertFalse("Note at 90_000ms must not belong to [60000, 90000)", belongsToScene(noteAtEndOfC, sceneC, allTracks))
    }

    @Test
    fun belongsToScene_resolvesParentViaFilePathFallbackWhenParentTrackIdIsNull() {
        val orphanScene = sceneA.copy(parentTrackId = null)
        val noteOnParent = Note(
            id = 6L,
            trackId = parentTrack.id,
            startTimestampMs = 10_000L,
            text = "Note on parent"
        )

        assertTrue(
            "Should resolve parent by matching filePath with parentTrack",
            belongsToScene(noteOnParent, orphanScene, allTracks)
        )
    }

    @Test
    fun belongsToScene_returnsFalseForDifferentTrackFile() {
        val otherTrackNote = Note(
            id = 7L,
            trackId = 999L,
            startTimestampMs = 10_000L,
            text = "Different Track Note"
        )

        assertFalse(belongsToScene(otherTrackNote, sceneA, allTracks))
    }
}
