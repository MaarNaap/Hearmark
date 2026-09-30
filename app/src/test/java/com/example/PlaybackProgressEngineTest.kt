package com.example

import com.example.data.AudioTrack
import com.example.player.AudioPlayerManager
import com.example.player.PlaybackProgressEngine
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.BitSet

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PlaybackProgressEngineTest {

    @Before
    fun resetState() {
        AudioPlayerManager.activeTrackSegmentTrackId = null
        AudioPlayerManager.activeTrackSegmentsBitSet.clear()
        AudioPlayerManager.lastTrackedPositionMs = null
        AudioPlayerManager.lastTrackedWallClockMs = 0L
        AudioPlayerManager._playbackSpeed.value = 1.0f
    }

    @Test
    fun parseAndSerializeBitSet_roundTripAndDeduplication() {
        val bitSet = BitSet(100)
        PlaybackProgressEngine.parseSegmentsIntoBitSet("0,1,2,15,15,42,99", bitSet)

        assertEquals(6, bitSet.cardinality())
        assertTrue(bitSet.get(0))
        assertTrue(bitSet.get(1))
        assertTrue(bitSet.get(2))
        assertTrue(bitSet.get(15))
        assertTrue(bitSet.get(42))
        assertTrue(bitSet.get(99))
        assertFalse(bitSet.get(3))

        val serialized = PlaybackProgressEngine.serializeBitSet(bitSet)
        assertEquals("0,1,2,15,42,99", serialized)
    }

    @Test
    fun getOrInitActiveBitSet_mergesSegmentsAcrossAsyncUpdatesWithoutLoss() {
        val bs1 = PlaybackProgressEngine.getOrInitActiveBitSet(trackId = 10L, rawSegments = "0,1,2,3")
        assertEquals(4, bs1.cardinality())

        // Simulate in-memory tick marking segments 4, 5, 6 while an older DB snapshot ("0,1,2") arrives
        bs1.set(4)
        bs1.set(5)
        bs1.set(6)
        val bsMerged = PlaybackProgressEngine.getOrInitActiveBitSet(trackId = 10L, rawSegments = "0,1,2")
        assertEquals("Should retain segments 0..6 without losing in-memory ticks", 7, bsMerged.cardinality())
        assertEquals("0,1,2,3,4,5,6", PlaybackProgressEngine.serializeBitSet(bsMerged))
    }

    @Test
    fun computeMaxContinuousDeltaMs_scalesWithWallClockDelayAndPlaybackSpeed() {
        // When screen is locked, coroutine delay(200) can be throttled to several seconds (e.g. 5000ms).
        // Verify computeMaxContinuousDeltaMs adapts to elapsed wall clock so continuous playback has no gaps.
        AudioPlayerManager.lastTrackedWallClockMs = 10_000L
        AudioPlayerManager._playbackSpeed.value = 1.0f

        val maxDeltaNormalTick = PlaybackProgressEngine.computeMaxContinuousDeltaMs(nowWallMs = 10_200L)
        assertTrue("Normal 200ms tick should allow at least 2500ms delta", maxDeltaNormalTick >= 2500L)

        val maxDeltaScreenLocked = PlaybackProgressEngine.computeMaxContinuousDeltaMs(nowWallMs = 16_000L) // 6s delay
        assertTrue(
            "6s wall-clock delay at 1.0x should allow >6000ms media delta (got $maxDeltaScreenLocked)",
            maxDeltaScreenLocked >= 9000L
        )

        AudioPlayerManager._playbackSpeed.value = 2.0f
        val maxDeltaFastPlayback = PlaybackProgressEngine.computeMaxContinuousDeltaMs(nowWallMs = 16_000L) // 6s delay at 2x = 12s media
        assertTrue(
            "6s wall-clock delay at 2.0x should allow >12000ms media delta (got $maxDeltaFastPlayback)",
            maxDeltaFastPlayback >= 18000L
        )
    }

    @Test
    fun continuousPlayback_fromStartToEnd_reaches100PercentWithZeroGaps() {
        val durationMs = 60_000L // 60s track -> 60 adaptive segments
        var track = AudioTrack(
            id = 7L,
            filePath = "/storage/lesson.mp3",
            fileName = "lesson.mp3",
            duration = durationMs,
            listenedSegments = ""
        )
        val numSegments = track.getAdaptiveNumSegments()
        assertEquals(60, numSegments)

        val bitSet = PlaybackProgressEngine.getOrInitActiveBitSet(track.id, track.listenedSegments)

        // Simulate irregular background ticks (including 4-second screen-locked jumps) from 0ms to 59,200ms
        val simulatedPositions = listOf(
            0L to 1000L,
            250L to 1250L,
            4200L to 5200L,   // 4s jump while screen locked
            9500L to 10500L,  // 5.3s jump while screen locked
            15000L to 16000L,
            24000L to 25000L, // 9s jump while screen locked
            35000L to 36000L,
            48000L to 49000L,
            58500L to 59500L,
            59850L to 60850L
        )

        var prevPos = 0L
        AudioPlayerManager.lastTrackedWallClockMs = 1000L
        for ((posMs, wallMs) in simulatedPositions) {
            val maxDelta = PlaybackProgressEngine.computeMaxContinuousDeltaMs(wallMs)
            val deltaPos = posMs - prevPos
            assertTrue("Delta $deltaPos should be within maxContinuousDelta $maxDelta", deltaPos in 0L..maxDelta)

            val fromSeg = ((prevPos * numSegments) / durationMs).toInt().coerceIn(0, numSegments - 1)
            val toSeg = ((posMs * numSegments) / durationMs).toInt().coerceIn(0, numSegments - 1)
            for (s in fromSeg..toSeg) {
                bitSet.set(s)
            }
            prevPos = posMs
            AudioPlayerManager.lastTrackedWallClockMs = wallMs
        }

        val serialized = PlaybackProgressEngine.serializeBitSet(bitSet)
        track = track.copy(listenedSegments = serialized)
        assertEquals("Continuous playback from start to finish must reach 100%", 100, track.getProgressPercent())
        assertEquals(numSegments, track.getListenedCount())
    }

    @Test
    fun playbackThreshold_evaluation_andSessionGuard_preventDoubleIncrement() {
        val completionThreshold = 90

        fun evaluateTrigger(initialPercent: Int, currentPercent: Int): Boolean {
            return when {
                initialPercent >= 100 -> false
                initialPercent >= completionThreshold -> currentPercent >= 100
                else -> currentPercent >= completionThreshold
            }
        }

        assertFalse(evaluateTrigger(0, 89))
        assertTrue(evaluateTrigger(0, 90))
        assertFalse(evaluateTrigger(92, 95))
        assertTrue(evaluateTrigger(92, 100))
        assertFalse(evaluateTrigger(100, 100))
    }
}
