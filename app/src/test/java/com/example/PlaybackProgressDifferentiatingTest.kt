package com.example

import com.example.data.AudioTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PlaybackProgressDifferentiatingTest {

    @Test
    fun testDifferentiatingCurrentPositionAndMaximumProgress() {
        val durationMs = 100_000L // 100 seconds
        val track = AudioTrack(
            id = 1L,
            filePath = "/dummy/test.mp3",
            fileName = "test.mp3",
            duration = durationMs,
            playCount = 0,
            lastPosition = 0L,
            listenedSegments = ""
        )

        val numSegments = track.getAdaptiveNumSegments() // 100 segments
        assertEquals(100, numSegments)

        // 1. Initial fresh track
        assertEquals(0L, track.lastPosition)
        assertEquals(0, track.getProgressPercent())

        // 2. User seeks to near the end (95% position, 95 seconds) without listening yet
        val trackAfterSeek = track.copy(lastPosition = 95_000L)
        assertEquals(95_000L, trackAfterSeek.lastPosition)
        // Maximum listening progress must STILL be 0%, NOT 95%!
        assertEquals(0, trackAfterSeek.getProgressPercent())

        // 3. User listens for 1 second at near the end (segment 95)
        val trackWith1Segment = trackAfterSeek.copy(
            lastPosition = 96_000L,
            listenedSegments = "95"
        )
        assertEquals(96_000L, trackWith1Segment.lastPosition)
        // Position is at 96%, but maximum progress is strictly 1%!
        assertEquals(1, trackWith1Segment.getProgressPercent())

        // 4. User plays until the physical end of the file (segments 95, 96, 97, 98, 99)
        val trackAtEnd = trackWith1Segment.copy(
            lastPosition = 100_000L,
            listenedSegments = "95,96,97,98,99"
        )
        // Position reached end of file, but listening progress is only 5%!
        val progressAtEnd = trackAtEnd.getProgressPercent()
        assertEquals(5, progressAtEnd)

        // 5. Verify completion threshold criteria (threshold = 90%)
        val completionThreshold = 90
        val criteriaMet = (progressAtEnd >= completionThreshold) || (progressAtEnd >= 100)
        assertFalse("Criteria must NOT be met when only 5% was listened, even if physical end was reached", criteriaMet)

        val isFullyListened = progressAtEnd >= 100
        assertFalse("Track must NOT be marked as 100% fully listened", isFullyListened)
    }

    @Test
    fun testTrueCompletionAtThresholdOr100Percent() {
        val durationMs = 100_000L
        val numSegments = 100
        val completionThreshold = 90

        // Continuous listening from 0 to 90 segments
        val segmentsListened90 = (0 until 90).joinToString(",")
        val track90 = AudioTrack(
            id = 2L,
            filePath = "/dummy/test2.mp3",
            fileName = "test2.mp3",
            duration = durationMs,
            playCount = 0,
            lastPosition = 90_000L,
            listenedSegments = segmentsListened90
        )
        assertEquals(90, track90.getProgressPercent())
        assertTrue(track90.getProgressPercent() >= completionThreshold)

        // All 100 segments listened
        val segmentsListened100 = (0 until 100).joinToString(",")
        val track100 = track90.copy(
            lastPosition = 100_000L,
            listenedSegments = segmentsListened100
        )
        assertEquals(100, track100.getProgressPercent())
        assertTrue(track100.getProgressPercent() >= 100)
    }
}
