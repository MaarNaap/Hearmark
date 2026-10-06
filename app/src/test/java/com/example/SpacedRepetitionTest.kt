package com.example

import com.example.util.SpacedRepetition
import com.example.util.SpacedRepetition.BadgeType
import com.example.util.SpacedRepetition.CardState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class SpacedRepetitionTest {

    private val utcTz: TimeZone = TimeZone.getTimeZone("UTC")
    private val riyadhTz: TimeZone = TimeZone.getTimeZone("Asia/Riyadh")
    private val newYorkTz: TimeZone = TimeZone.getTimeZone("America/New_York")

    private fun makeTimestamp(
        year: Int,
        monthZeroBased: Int,
        day: Int,
        hour: Int,
        minute: Int,
        second: Int = 0,
        millis: Int = 0,
        tz: TimeZone = utcTz
    ): Long {
        return Calendar.getInstance(tz).apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, monthZeroBased)
            set(Calendar.DAY_OF_MONTH, day)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, second)
            set(Calendar.MILLISECOND, millis)
        }.timeInMillis
    }

    @Test
    fun review_correctAnswersProgressThrough1dThen3dThenEaseMultiplierAndCapAt365() {
        val nowMs = makeTimestamp(2026, Calendar.OCTOBER, 5, 14, 30, tz = utcTz)

        // 1st correct: repetitions 0 -> 1, interval 1d, ease 2.5 (capped at 2.5)
        val s1 = SpacedRepetition.review(CardState(), isCorrect = true, nowMs = nowMs, timeZone = utcTz)
        assertEquals(1, s1.repetitions)
        assertEquals(1, s1.intervalDays)
        assertEquals(2.5f, s1.ease, 0.0001f)
        assertEquals(0, s1.lapses)
        assertEquals(makeTimestamp(2026, Calendar.OCTOBER, 6, 0, 0, tz = utcTz), s1.nextReviewAt)

        // 2nd correct: repetitions 1 -> 2, interval 3d
        val s2 = SpacedRepetition.review(s1, isCorrect = true, nowMs = nowMs, timeZone = utcTz)
        assertEquals(2, s2.repetitions)
        assertEquals(3, s2.intervalDays)
        assertEquals(2.5f, s2.ease, 0.0001f)
        assertEquals(makeTimestamp(2026, Calendar.OCTOBER, 8, 0, 0, tz = utcTz), s2.nextReviewAt)

        // 3rd correct: repetitions 2 -> 3, interval round(3 * 2.5) = 8d
        val s3 = SpacedRepetition.review(s2, isCorrect = true, nowMs = nowMs, timeZone = utcTz)
        assertEquals(3, s3.repetitions)
        assertEquals(8, s3.intervalDays)
        assertEquals(makeTimestamp(2026, Calendar.OCTOBER, 13, 0, 0, tz = utcTz), s3.nextReviewAt)

        // 4th correct: repetitions 3 -> 4, interval round(8 * 2.5) = 20d
        val s4 = SpacedRepetition.review(s3, isCorrect = true, nowMs = nowMs, timeZone = utcTz)
        assertEquals(4, s4.repetitions)
        assertEquals(20, s4.intervalDays)

        // Verify 365-day maximum interval cap
        var state = s4
        repeat(6) {
            state = SpacedRepetition.review(state, isCorrect = true, nowMs = nowMs, timeZone = utcTz)
        }
        assertEquals(SpacedRepetition.MAX_INTERVAL_DAYS, state.intervalDays)
    }

    @Test
    fun review_wrongAnswerResetsRepetitionsAndIntervalIncrementsLapsesAndFloorsEaseAt1_3() {
        val nowMs = makeTimestamp(2026, Calendar.OCTOBER, 5, 19, 15, tz = riyadhTz)
        val initial = CardState(
            repetitions = 4,
            intervalDays = 20,
            ease = 2.5f,
            lapses = 0,
            nextReviewAt = nowMs
        )

        val afterWrong = SpacedRepetition.review(initial, isCorrect = false, nowMs = nowMs, timeZone = riyadhTz)
        assertEquals(0, afterWrong.repetitions)
        assertEquals(1, afterWrong.intervalDays)
        assertEquals(2.3f, afterWrong.ease, 0.0001f)
        assertEquals(1, afterWrong.lapses)
        assertEquals(makeTimestamp(2026, Calendar.OCTOBER, 6, 0, 0, tz = riyadhTz), afterWrong.nextReviewAt)

        // Repeated wrong answers never drop ease below 1.3f
        var repeatedFail = afterWrong
        repeat(10) {
            repeatedFail = SpacedRepetition.review(repeatedFail, isCorrect = false, nowMs = nowMs, timeZone = riyadhTz)
        }
        assertEquals(SpacedRepetition.MIN_EASE, repeatedFail.ease, 0.0001f)
        assertEquals(11, repeatedFail.lapses)
        assertEquals(0, repeatedFail.repetitions)
        assertEquals(1, repeatedFail.intervalDays)

        // Subsequent correct answer recovers ease by +0.1f
        val recovered = SpacedRepetition.review(repeatedFail, isCorrect = true, nowMs = nowMs, timeZone = riyadhTz)
        assertEquals(1.4f, recovered.ease, 0.0001f)
        assertEquals(1, recovered.repetitions)
        assertEquals(1, recovered.intervalDays)
    }

    @Test
    fun midnightSnappingAndIsDue_respectExplicitTimeZonesAcrossWholeDay() {
        for (tz in listOf(utcTz, riyadhTz, newYorkTz)) {
            val morningToday = makeTimestamp(2026, Calendar.OCTOBER, 5, 8, 0, tz = tz)
            val lateEveningToday = makeTimestamp(2026, Calendar.OCTOBER, 5, 22, 45, tz = tz)
            val startOfToday = SpacedRepetition.startOfDayMillis(morningToday, tz)
            val endOfToday = SpacedRepetition.endOfTodayMillis(morningToday, tz)
            val startOfTomorrow = SpacedRepetition.addDaysAtMidnight(morningToday, 1, tz)

            assertEquals(makeTimestamp(2026, Calendar.OCTOBER, 5, 0, 0, 0, 0, tz = tz), startOfToday)
            assertEquals(makeTimestamp(2026, Calendar.OCTOBER, 5, 23, 59, 59, 999, tz = tz), endOfToday)
            assertEquals(makeTimestamp(2026, Calendar.OCTOBER, 6, 0, 0, 0, 0, tz = tz), startOfTomorrow)

            // Null nextReviewAt is always due
            assertTrue(SpacedRepetition.isDue(null, morningToday, tz))

            // Scheduled for today's midnight or later today is due both morning and late evening
            assertTrue(SpacedRepetition.isDue(startOfToday, morningToday, tz))
            assertTrue(SpacedRepetition.isDue(lateEveningToday, morningToday, tz))
            assertTrue(SpacedRepetition.isDue(endOfToday, lateEveningToday, tz))

            // Scheduled for tomorrow at 00:00:00.000 is NOT due today
            assertFalse(SpacedRepetition.isDue(startOfTomorrow, morningToday, tz))
            assertFalse(SpacedRepetition.isDue(startOfTomorrow, lateEveningToday, tz))
        }
    }

    @Test
    fun getStatusBadgeInfo_returnsNewDueAndScheduledWithDaysRemaining() {
        val nowMs = makeTimestamp(2026, Calendar.OCTOBER, 5, 15, 0, tz = riyadhTz)

        val newBadge = SpacedRepetition.getStatusBadgeInfo(null, nowMs, riyadhTz)
        assertEquals(BadgeType.NEW, newBadge.badgeType)
        assertEquals(0, newBadge.daysRemaining)

        val dueBadge = SpacedRepetition.getStatusBadgeInfo(
            SpacedRepetition.startOfDayMillis(nowMs, riyadhTz),
            nowMs,
            riyadhTz
        )
        assertEquals(BadgeType.DUE, dueBadge.badgeType)
        assertEquals(0, dueBadge.daysRemaining)

        val tomorrowMidnight = SpacedRepetition.addDaysAtMidnight(nowMs, 1, riyadhTz)
        val in1DayBadge = SpacedRepetition.getStatusBadgeInfo(tomorrowMidnight, nowMs, riyadhTz)
        assertEquals(BadgeType.SCHEDULED, in1DayBadge.badgeType)
        assertEquals(1, in1DayBadge.daysRemaining)

        val in3DaysMidnight = SpacedRepetition.addDaysAtMidnight(nowMs, 3, riyadhTz)
        val in3DaysBadge = SpacedRepetition.getStatusBadgeInfo(in3DaysMidnight, nowMs, riyadhTz)
        assertEquals(BadgeType.SCHEDULED, in3DaysBadge.badgeType)
        assertEquals(3, in3DaysBadge.daysRemaining)
        assertNotNull(in3DaysBadge)
    }
}
