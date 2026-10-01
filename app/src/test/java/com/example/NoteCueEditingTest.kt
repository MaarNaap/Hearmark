package com.example

import com.example.player.SubtitleCue
import com.example.ui.canAddNextCue
import com.example.ui.canAddPreviousCue
import com.example.ui.mergeNextCue
import com.example.ui.mergePreviousCue
import org.junit.Assert.*
import org.junit.Test

class NoteCueEditingTest {

    private val cues = listOf(
        SubtitleCue(id = 1, startMs = 1000L, endMs = 3000L, text = "First line"),
        SubtitleCue(id = 2, startMs = 3500L, endMs = 6000L, text = "Second line"),
        SubtitleCue(id = 3, startMs = 6500L, endMs = 9000L, text = "Third line")
    )

    @Test
    fun canAddPreviousAndNextCue_respectsBoundaries() {
        assertFalse(canAddPreviousCue(cues, currentStartCueIndex = 0, startMs = 1000L))
        assertTrue(canAddPreviousCue(cues, currentStartCueIndex = 1, startMs = 3500L))

        assertTrue(canAddNextCue(cues, currentEndCueIndex = 1, endMs = 6000L))
        assertFalse(canAddNextCue(cues, currentEndCueIndex = 2, endMs = 9000L))
    }

    @Test
    fun mergePreviousCue_prependsTextAndExpandsStartMs() {
        val result = mergePreviousCue(
            trackCues = cues,
            currentStartCueIndex = 1,
            currentEndCueIndex = 1,
            startMs = 3500L,
            endMs = 6000L,
            noteText = "Second line",
            minBound = 0L,
            maxBound = 20000L
        )
        assertNotNull(result)
        assertEquals(0, result!!.newStartCueIndex)
        assertEquals(1, result.newEndCueIndex)
        assertEquals(1000L, result.newStartMs)
        assertEquals(6000L, result.newEndMs)
        assertEquals("First line Second line", result.newNoteText)
    }

    @Test
    fun mergeNextCue_appendsTextAndExpandsEndMs() {
        val result = mergeNextCue(
            trackCues = cues,
            currentStartCueIndex = 1,
            currentEndCueIndex = 1,
            startMs = 3500L,
            endMs = 6000L,
            noteText = "Second line",
            minBound = 0L,
            maxBound = 20000L
        )
        assertNotNull(result)
        assertEquals(1, result!!.newStartCueIndex)
        assertEquals(2, result.newEndCueIndex)
        assertEquals(3500L, result.newStartMs)
        assertEquals(9000L, result.newEndMs)
        assertEquals("Second line Third line", result.newNoteText)
    }
}
