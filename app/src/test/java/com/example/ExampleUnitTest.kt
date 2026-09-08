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
}
