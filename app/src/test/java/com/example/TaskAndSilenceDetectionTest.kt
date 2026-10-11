package com.example

import com.example.data.AudioTrack
import com.example.data.Task
import com.example.player.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TaskAndSilenceDetectionTest {

    @Test
    fun taskCombinedTitle_formatsLabelsInBrackets() {
        val result = Task.buildCombinedTitle("Practice Session", "Pronunciation, Loud")
        assertEquals("Practice Session [Loud. Pronunciation]", result)
    }

    @Test
    fun taskCombinedTitle_titlecasesAndSortsAlphabetically() {
        val result = Task.buildCombinedTitle("Grammar", "slow, fast, normal")
        assertEquals("Grammar [Fast. Normal. Slow]", result)
    }

    @Test
    fun taskCombinedTitle_doesNotDuplicateIfAlreadyAppended() {
        val alreadyFormatted = "Grammar [Fast. Normal. Slow]"
        val result = Task.buildCombinedTitle(alreadyFormatted, "slow, fast, normal")
        assertEquals("Grammar [Fast. Normal. Slow]", result)
    }

    @Test
    fun taskCombinedTitle_replacesPreviousLabelsWhenEdited() {
        val oldTaskTitle = "Morning Routine [Fast. Loud]"
        val result = Task.buildCombinedTitle(oldTaskTitle, "Slow, Calm")
        assertEquals("Morning Routine [Calm. Slow]", result)
    }

    @Test
    fun taskCombinedTitle_handlesEmptyLabels() {
        val result = Task.buildCombinedTitle("Chapter 1", "")
        assertEquals("Chapter 1", result)
    }

    @Test
    fun taskCombinedTitle_handlesEmptyTitleWithLabels() {
        val result = Task.buildCombinedTitle("", "Grammar, Daily")
        assertEquals("[Daily. Grammar]", result)
    }

    @Test
    fun taskExtractBaseTitle_correctlyExtractsGivenTitle() {
        val base = Task.extractBaseTitle("Daily Quiz [Level 1. Vocabulary]", "Level 1, Vocabulary")
        assertEquals("Daily Quiz", base)
    }

    @Test
    fun taskDisplayTitle_matchesDatabaseTitleWithoutDuplication() {
        val task = Task(
            id = 1,
            title = "Morning Reading [Loud. Slow]",
            sourceType = "FOLDER",
            sourceId = 1L,
            targetType = "PLAY_COUNT",
            targetValue = 2,
            scheduledDays = "",
            reminderTime = "",
            startDate = 0L,
            labels = "Slow, Loud"
        )
        assertEquals("Morning Reading [Loud. Slow]", task.title)
        assertEquals("Morning Reading [Loud. Slow]", task.getDisplayTitle())
        assertEquals("Morning Reading", task.getBaseTitle())
    }

    @Test
    fun audioTrack_getPracticeSegmentsList_parsesAndSortsCorrectly() {
        val track = AudioTrack(
            id = 1,
            fileName = "Lesson 1",
            filePath = "/path/to/audio.mp3",
            duration = 15000L,
            practiceSegments = "5000, 12000, 2000, 8500"
        )
        val list = track.getPracticeSegmentsList()
        assertEquals(listOf(2000L, 5000L, 8500L, 12000L), list)
    }

    @Test
    fun audioTrack_getPracticeSegmentsSource_identifiesAllSources() {
        val manualTrack = AudioTrack(
            id = 1,
            fileName = "Manual Lesson",
            filePath = "/path/to/audio.mp3",
            duration = 15000L,
            practiceSegments = "MAN:2500,6000,11000"
        )
        assertEquals("MANUAL", manualTrack.getPracticeSegmentsSource())
        assertEquals(listOf(2500L, 6000L, 11000L), manualTrack.getPracticeSegmentsList())

        val subTrack = manualTrack.copy(practiceSegments = "SUB:1000,4000")
        assertEquals("SUBTITLES", subTrack.getPracticeSegmentsSource())

        val silTrack = manualTrack.copy(practiceSegments = "SIL:3000,7000")
        assertEquals("SILENCE", silTrack.getPracticeSegmentsSource())
    }

    @Test
    fun audioTrack_getPracticeSegmentsList_handlesNullAndEmpty() {
        val track1 = AudioTrack(
            id = 1,
            fileName = "Lesson 1",
            filePath = "/path/to/audio.mp3",
            duration = 15000L,
            practiceSegments = null
        )
        assertTrue(track1.getPracticeSegmentsList().isEmpty())

        val track2 = track1.copy(practiceSegments = "   ")
        assertTrue(track2.getPracticeSegmentsList().isEmpty())
    }

    @Test
    fun silenceDetector_fallbackSegments_generatesEvenSegments() {
        val segments = SilenceDetector.generateFallbackSegments(15000L)
        assertEquals(listOf(6000L, 12000L, 15000L), segments)
    }

    @Test
    fun silenceDetector_extractBoundariesFromWindows_identifiesSilences() {
        val windows = (0 until 100).map { i ->
            val timeMs = i * 100L
            val isSilence = (i in 30..39) || (i in 70..79)
            val rms = if (isSilence) 50.0 else 2000.0
            SilenceDetector.AudioWindow(timeMs = timeMs, rms = rms)
        }

        val boundaries = SilenceDetector.extractBoundariesFromWindows(windows, 10000L)
        assertTrue("Boundaries should not be empty", boundaries.isNotEmpty())
        assertTrue("Should detect boundary near 3500ms", boundaries.any { it in 3200L..3800L })
        assertTrue("Should detect boundary near 7500ms", boundaries.any { it in 7200L..7800L })
        assertEquals(10000L, boundaries.last())
    }

    @Test
    fun practiceMode_subtitleCuts_skipForwardAndBackwardNavigatesBetweenSubtitleLines() {
        val am = com.example.player.AudioPlayerManager
        am.mediaPlayer = null
        val cues = listOf(
            com.example.player.SubtitleCue(id = 1, startMs = 1000L, endMs = 3000L, text = "First line", isTimed = true),
            com.example.player.SubtitleCue(id = 2, startMs = 4000L, endMs = 6000L, text = "Second line", isTimed = true),
            com.example.player.SubtitleCue(id = 3, startMs = 7500L, endMs = 9500L, text = "Third line", isTimed = true)
        )
        val track = AudioTrack(
            id = 501L,
            fileName = "Subtitled Lesson",
            filePath = "/tmp/lesson.mp4",
            duration = 12000L,
            practiceSegments = "SUB:3000,6000,9500"
        )
        am.currentTrackValue = track
        am._duration.value = 12000L
        am._currentPosition.value = 1500L
        am.segmentSource = "SUBTITLES"
        am._subtitlesCues.value = cues
        am._currentPracticeSegments.value = listOf(3000L, 6000L, 9500L)
        am._isPracticeMode.value = true
        am._isPracticePausing.value = false
        am.isFocusMode.value = true

        // Currently inside Cue 1 (1500ms) -> skipForward goes to Cue 2 (4000ms)
        am.skipForward()
        assertEquals(4000L, am.currentPosition.value)
        assertEquals("Second line", am.activeSubtitleCue.value?.text)

        // From Cue 2 (4000ms) -> skipForward goes to Cue 3 (7500ms)
        am.skipForward()
        assertEquals(7500L, am.currentPosition.value)
        assertEquals("Third line", am.activeSubtitleCue.value?.text)

        // From Cue 3 (7500ms) -> skipBackward goes to Cue 2 (4000ms)
        am.skipBackward()
        assertEquals(4000L, am.currentPosition.value)
        assertEquals("Second line", am.activeSubtitleCue.value?.text)

        // From Cue 2 (4000ms) -> skipBackward goes to Cue 1 (1000ms)
        am.skipBackward()
        assertEquals(1000L, am.currentPosition.value)
        assertEquals("First line", am.activeSubtitleCue.value?.text)

        am._isPracticeMode.value = false
        am.isFocusMode.value = false
        am._subtitlesCues.value = emptyList()
        am._currentPracticeSegments.value = emptyList()
        am.currentTrackValue = null
    }

    @Test
    fun practiceMode_autoGeneratedOrManualCuts_skipForwardAndBackwardNavigatesBetweenCutStarts() {
        val am = com.example.player.AudioPlayerManager
        am.mediaPlayer = null
        val track = AudioTrack(
            id = 502L,
            fileName = "Silence Cut Lesson",
            filePath = "/tmp/audio.mp3",
            duration = 20000L,
            practiceSegments = "SIL:4000,9000,15000,20000"
        )
        am.currentTrackValue = track
        am._duration.value = 20000L
        am._currentPosition.value = 1200L
        am.segmentSource = "SILENCE"
        am._subtitlesCues.value = emptyList()
        am._currentPracticeSegments.value = listOf(4000L, 9000L, 15000L, 20000L)
        am._isPracticeMode.value = true
        am._isPracticePausing.value = false
        am.isFocusMode.value = true

        // Cut starts are [0, 4000, 9000, 15000]
        // At 1200ms (Cut 0) -> skipForward goes to Cut 1 start (4000ms)
        am.skipForward()
        assertEquals(4000L, am.currentPosition.value)

        // At 4000ms (Cut 1) -> skipForward goes to Cut 2 start (9000ms)
        am.skipForward()
        assertEquals(9000L, am.currentPosition.value)

        // At 9000ms (Cut 2) -> skipBackward goes to Cut 1 start (4000ms)
        am.skipBackward()
        assertEquals(4000L, am.currentPosition.value)

        // At 4000ms (Cut 1) -> skipBackward goes to Cut 0 start (0ms)
        am.skipBackward()
        assertEquals(0L, am.currentPosition.value)

        am._isPracticeMode.value = false
        am.isFocusMode.value = false
        am._currentPracticeSegments.value = emptyList()
        am.currentTrackValue = null
    }

    @Test
    fun videoFocusMode_withoutPracticeMode_skipForwardAndBackwardUsesSeconds() {
        val am = com.example.player.AudioPlayerManager
        am.mediaPlayer = null
        val track = AudioTrack(
            id = 503L,
            fileName = "Focus Video",
            filePath = "/tmp/video.mp4",
            duration = 60000L,
            practiceSegments = "SIL:4000,9000,15000,20000"
        )
        am.currentTrackValue = track
        am._duration.value = 60000L
        am._currentPosition.value = 12000L
        am.skipTimeSeconds = 10
        am._currentPracticeSegments.value = listOf(4000L, 9000L, 15000L, 20000L)
        // Focus mode on video is active, but Practice Mode is OFF
        am._isPracticeMode.value = false
        am.isFocusMode.value = true

        am.skipForward()
        assertEquals(22000L, am.currentPosition.value)

        am.skipBackward()
        assertEquals(12000L, am.currentPosition.value)

        am.isFocusMode.value = false
        am._currentPracticeSegments.value = emptyList()
        am.currentTrackValue = null
    }

    @Test
    fun practiceMode_segmentRepeatCount_repeatsConsecutivelyBeforePausing() {
        val am = com.example.player.AudioPlayerManager
        am.mediaPlayer = null
        val track = AudioTrack(
            id = 601L,
            fileName = "Repeat Test Track",
            filePath = "/tmp/repeat_track.mp3",
            duration = 30000L,
            practiceSegments = "SIL:3000,7000,12000"
        )
        am.currentTrackValue = track
        am._duration.value = 30000L
        am._currentPosition.value = 0L
        am.segmentSource = "SILENCE"
        am._currentPracticeSegments.value = listOf(3000L, 7000L, 12000L)
        am._isPracticeMode.value = true
        am._isPracticePausing.value = false
        am.pendingEndOfTrackAfterPracticePause = false

        // Set repeat count to 3
        am.setPracticeRepeatCount(3)
        assertEquals(3, am.practiceSegmentRepeatCount)
        assertEquals(3, am.practiceSegmentRepeatsFlow.value)

        // Reset tracking at start (0ms)
        am.resetPracticeSegmentTracking(0L)
        assertEquals(1, am.practiceCurrentSegmentPlayCountFlow.value)
        assertEquals(0, am.practiceNextBoundaryIndex)

        // Play segment 1: reaches first boundary (3000ms) for the 1st time
        am.checkPracticeSegmentBoundary(3000L)
        // Should NOT pause yet; should seek back to segment start (0ms) and increment play count to 2
        assertFalse(am.isPracticePausing.value)
        assertEquals(2, am.practiceCurrentSegmentPlayCountFlow.value)
        assertEquals(0L, am.currentPosition.value)
        assertEquals(0, am.practiceNextBoundaryIndex)

        // Reaches boundary (3000ms) for the 2nd time
        am.checkPracticeSegmentBoundary(3000L)
        // Should NOT pause yet; should seek back to segment start (0ms) and increment play count to 3
        assertFalse(am.isPracticePausing.value)
        assertEquals(3, am.practiceCurrentSegmentPlayCountFlow.value)
        assertEquals(0L, am.currentPosition.value)
        assertEquals(0, am.practiceNextBoundaryIndex)

        // Reaches boundary (3000ms) for the 3rd time (all repeats fulfilled)
        am.checkPracticeSegmentBoundary(3000L)
        // Now it MUST trigger practice pause!
        assertTrue(am.isPracticePausing.value)
        assertEquals(1, am.practiceCurrentSegmentPlayCountFlow.value)
        assertEquals(1, am.practiceNextBoundaryIndex) // advanced to next boundary

        // User resumes from practice pause (or pause timer finishes)
        am.resumeFromPracticePause()
        assertFalse(am.isPracticePausing.value)
        assertEquals(1, am.practiceCurrentSegmentPlayCountFlow.value)

        // Now playing segment 2 (from 3000ms to 7000ms): reaches boundary 7000ms for 1st time
        am.checkPracticeSegmentBoundary(7000L)
        assertFalse(am.isPracticePausing.value)
        assertEquals(2, am.practiceCurrentSegmentPlayCountFlow.value)
        assertEquals(3000L, am.currentPosition.value) // replayed from start of 2nd segment (3000ms)

        // Cleanup
        am._isPracticeMode.value = false
        am._isPracticePausing.value = false
        am.setPracticeRepeatCount(1)
        am._currentPracticeSegments.value = emptyList()
        am.currentTrackValue = null
    }
}
