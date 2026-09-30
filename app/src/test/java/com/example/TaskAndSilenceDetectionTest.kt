package com.example

import com.example.data.AudioTrack
import com.example.data.Task
import com.example.player.SilenceDetector
import org.junit.Assert.*
import org.junit.Test

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
}
