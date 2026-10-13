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

    @Test
    fun stripCueText_removesPrefixSuffixAndExactMatch() {
        assertEquals("Second line", com.example.ui.stripCueText("First line Second line", "First line"))
        assertEquals("First line", com.example.ui.stripCueText("First line Second line", "Second line"))
        assertEquals("", com.example.ui.stripCueText("First line", "First line"))
        assertEquals("First line Third line", com.example.ui.stripCueText("First line Second line Third line", "Second line"))
    }

    @Test
    fun removeCue_removesPrependedCue_restoresOriginalRangeAndText() {
        val spanned = listOf(cues[0], cues[1]) // First line, Second line
        val result = com.example.ui.removeCue(
            cueToRemove = cues[0],
            spannedCues = spanned,
            trackCues = cues,
            noteText = "First line Second line",
            originStartMs = 3500L
        )
        assertNotNull(result)
        assertEquals(1, result!!.newStartCueIndex)
        assertEquals(1, result.newEndCueIndex)
        assertEquals(3500L, result.newStartMs)
        assertEquals(6000L, result.newEndMs)
        assertEquals("Second line", result.newNoteText)
        assertEquals(3500L, result.newOriginStartMs)
    }

    @Test
    fun removeCue_removesAppendedCue_restoresOriginalRangeAndText() {
        val spanned = listOf(cues[1], cues[2]) // Second line, Third line
        val result = com.example.ui.removeCue(
            cueToRemove = cues[2],
            spannedCues = spanned,
            trackCues = cues,
            noteText = "Second line Third line",
            originStartMs = 3500L
        )
        assertNotNull(result)
        assertEquals(1, result!!.newStartCueIndex)
        assertEquals(1, result.newEndCueIndex)
        assertEquals(3500L, result.newStartMs)
        assertEquals(6000L, result.newEndMs)
        assertEquals("Second line", result.newNoteText)
        assertEquals(3500L, result.newOriginStartMs)
    }

    @Test
    fun removeCue_fallsBackOriginStartMs_whenRemovedCueWasDedicated() {
        val spanned = listOf(cues[0], cues[1])
        // Dedicated cue was cue 0 (1000L)
        val result = com.example.ui.removeCue(
            cueToRemove = cues[0],
            spannedCues = spanned,
            trackCues = cues,
            noteText = "First line Second line",
            originStartMs = 1000L
        )
        assertNotNull(result)
        // Should fall back to cue 1 (3500L)
        assertEquals(3500L, result!!.newOriginStartMs)
    }
}

