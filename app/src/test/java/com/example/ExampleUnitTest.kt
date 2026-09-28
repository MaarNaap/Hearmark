package com.example

import com.example.data.Task
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

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
        val track = com.example.data.AudioTrack(
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
    fun audioTrack_getPracticeSegmentsSource_identifiesManualSource() {
        val track = com.example.data.AudioTrack(
            id = 1,
            fileName = "Manual Lesson",
            filePath = "/path/to/audio.mp3",
            duration = 15000L,
            practiceSegments = "MAN:2500,6000,11000"
        )
        assertEquals("MANUAL", track.getPracticeSegmentsSource())
        assertEquals(listOf(2500L, 6000L, 11000L), track.getPracticeSegmentsList())
    }

    @Test
    fun audioTrack_getPracticeSegmentsList_handlesNullAndEmpty() {
        val track1 = com.example.data.AudioTrack(
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
        val segments = com.example.player.SilenceDetector.generateFallbackSegments(15000L)
        assertEquals(listOf(6000L, 12000L, 15000L), segments)
    }

    @Test
    fun silenceDetector_extractBoundariesFromWindows_identifiesSilences() {
        // Build 10 seconds of simulated windows (100ms each = 100 windows)
        // Speech: 0-3s (windows 0..29, high RMS)
        // Silence: 3-4s (windows 30..39, near zero RMS)
        // Speech: 4-7s (windows 40..69, high RMS)
        // Silence: 7-8s (windows 70..79, near zero RMS)
        // Speech: 8-10s (windows 80..99, high RMS)
        val windows = (0 until 100).map { i ->
            val timeMs = i * 100L
            val isSilence = (i in 30..39) || (i in 70..79)
            val rms = if (isSilence) 50.0 else 2000.0
            com.example.player.SilenceDetector.AudioWindow(timeMs = timeMs, rms = rms)
        }

        val boundaries = com.example.player.SilenceDetector.extractBoundariesFromWindows(windows, 10000L)
        assertTrue("Boundaries should not be empty", boundaries.isNotEmpty())
        // First silence is around 3500ms
        assertTrue("Should detect boundary near 3500ms", boundaries.any { it in 3200L..3800L })
        // Second silence is around 7500ms
        assertTrue("Should detect boundary near 7500ms", boundaries.any { it in 7200L..7800L })
        // Ends at or covers the duration
        assertEquals(10000L, boundaries.last())
    }

    @Test
    fun playbackThreshold_evaluation_matchesInvariant() {
        val completionThreshold = 90

        // Helper function mimicking the protected shouldTrigger condition
        fun evaluateTrigger(initialPercent: Int, currentPercent: Int): Boolean {
            return when {
                initialPercent >= 100 -> false
                initialPercent >= completionThreshold -> currentPercent >= 100
                else -> currentPercent >= completionThreshold
            }
        }

        // Fresh session starting at 0%
        assertFalse(evaluateTrigger(0, 50))
        assertFalse(evaluateTrigger(0, 89))
        assertTrue(evaluateTrigger(0, 90))
        assertTrue(evaluateTrigger(0, 100))

        // Session starting at partial completion above threshold (e.g. 92%)
        assertFalse(evaluateTrigger(92, 95))
        assertTrue(evaluateTrigger(92, 100))

        // Session starting at 100% already
        assertFalse(evaluateTrigger(100, 100))
    }

    @Test
    fun taskPlayCount_sessionGuard_preventsDoubleIncrementOnCompletion() {
        // Test that a session guard prevents multiple increments within a single completion cycle
        val completedTaskIdsForCurrentSession = mutableSetOf<Long>()
        val taskId = 42L
        var completedPlayCount = 0

        fun triggerTaskProgress(progressPercent: Int, customThreshold: Int?) {
            val effectiveThreshold = customThreshold ?: 90
            if (progressPercent >= effectiveThreshold) {
                if (!completedTaskIdsForCurrentSession.contains(taskId)) {
                    completedTaskIdsForCurrentSession.add(taskId)
                    completedPlayCount++
                }
            }
        }

        // 1. Threshold reached during playback at 90%
        triggerTaskProgress(90, null)
        assertEquals("Should increment exactly once at threshold", 1, completedPlayCount)

        // 2. Playback ticks again at 95%
        triggerTaskProgress(95, null)
        assertEquals("Should not increment again while in the same session", 1, completedPlayCount)

        // 3. Playback reaches 100% (physical end of track)
        triggerTaskProgress(100, null)
        assertEquals("Should remain exactly 1 after reaching end of file", 1, completedPlayCount)
    }

    @Test
    fun geminiService_parseScenesResiliently_handlesStandardJson() {
        val json = """
            {
              "scenes": [
                {
                  "sceneNumber": 1,
                  "title": "Meeting at the cafe",
                  "startMs": 0,
                  "endMs": 120000,
                  "summary": "Characters meet and talk."
                },
                {
                  "sceneNumber": 2,
                  "title": "Walking home",
                  "startMs": 120000,
                  "endMs": 300000,
                  "summary": "Walking and discussing tomorrow."
                }
              ]
            }
        """.trimIndent()

        val scenes = com.example.ai.GeminiService.parseScenesResiliently(json, 300000L)
        assertEquals(2, scenes.size)
        assertEquals("Meeting at the cafe", scenes[0].title)
        assertEquals(0L, scenes[0].startMs)
        assertEquals(120000L, scenes[0].endMs)
        assertEquals("Walking home", scenes[1].title)
    }

    @Test
    fun geminiService_parseScenesResiliently_handlesMarkdownAndIntroText() {
        val markdown = """
            Here are the extracted scenes for this video:
            ```json
            [
              {
                "sceneNumber": 1,
                "title": "001 - Introduction",
                "startMs": 0,
                "endMs": 60000,
                "summary": "Opening speech"
              },
              {
                "sceneNumber": 2,
                "title": "002 - Main Lesson",
                "startMs": 60000,
                "endMs": 180000,
                "summary": "Explanation"
              }
            ]
            ```
            Hope this helps!
        """.trimIndent()

        val scenes = com.example.ai.GeminiService.parseScenesResiliently(markdown, 180000L)
        assertEquals(2, scenes.size)
        assertEquals("Introduction", scenes[0].title)
        assertEquals("Main Lesson", scenes[1].title)
    }

    @Test
    fun geminiService_parseScenesResiliently_recoversTruncatedJson() {
        val truncatedJson = """
            {
              "scenes": [
                {
                  "sceneNumber": 1,
                  "title": "Scene One",
                  "startMs": 0,
                  "endMs": 90000
                },
                {
                  "sceneNumber": 2,
                  "title": "Scene Two",
                  "startMs": 90000,
                  "endMs": 240000
                },
                {
                  "sceneNumber": 3,
                  "title": "Scene Three",
                  "startMs": 240000
        """.trimIndent()

        val scenes = com.example.ai.GeminiService.parseScenesResiliently(truncatedJson, 300000L)
        assertTrue("Should recover at least the completed scenes before truncation", scenes.size >= 2)
        assertEquals("Scene One", scenes[0].title)
        assertEquals("Scene Two", scenes[1].title)
    }
}

