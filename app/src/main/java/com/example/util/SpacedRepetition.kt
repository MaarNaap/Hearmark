package com.example.util

import java.util.Calendar
import java.util.TimeZone
import kotlin.math.ceil

/**
 * Spaced Repetition Scheduling Engine (SM-2 simplified for vocabulary comprehension).
 *
 * Intervals:
 * - Repetition 0 -> 1: 1 day
 * - Repetition 1 -> 2: 3 days
 * - Repetition >= 2: interval * ease (rounded to integer days, capped at 365 days)
 *
 * Ease:
 * - Default: 2.5f
 * - Minimum (floor): 1.3f
 * - Maximum (cap): 2.5f
 * - On success: increases by 0.1f (bounded to 2.5f)
 * - On lapse (incorrect): decreases by 0.2f (bounded to 1.3f), repetitions reset to 0, interval resets to 1 day.
 *
 * Snapping:
 * - Due dates snap to local midnight (00:00:00.000) of the target day.
 * - Due checks use endOfTodayMillis (23:59:59.999) to keep "Due Today" consistent across the entire day.
 */
object SpacedRepetition {

    const val DEFAULT_EASE = 2.5f
    const val MIN_EASE = 1.3f
    const val MAX_EASE = 2.5f
    const val MAX_INTERVAL_DAYS = 365
    const val SESSION_CARD_CAP = 20

    data class CardState(
        val repetitions: Int = 0,
        val intervalDays: Int = 0,
        val ease: Float = DEFAULT_EASE,
        val lapses: Int = 0,
        val nextReviewAt: Long? = null
    )

    enum class BadgeType {
        NEW,
        DUE,
        SCHEDULED
    }

    data class StatusBadgeInfo(
        val badgeType: BadgeType,
        val daysRemaining: Int = 0
    )

    fun review(
        state: CardState,
        isCorrect: Boolean,
        nowMs: Long = System.currentTimeMillis(),
        timeZone: TimeZone = TimeZone.getDefault()
    ): CardState {
        val currentEase = state.ease.coerceIn(MIN_EASE, MAX_EASE)

        return if (isCorrect) {
            val newEase = (currentEase + 0.1f).coerceIn(MIN_EASE, MAX_EASE)
            val (newReps, newInterval) = when (state.repetitions) {
                0 -> 1 to 1
                1 -> 2 to 3
                else -> {
                    val computed = Math.round(state.intervalDays * currentEase).toInt()
                    val clamped = computed.coerceIn(1, MAX_INTERVAL_DAYS)
                    (state.repetitions + 1) to clamped
                }
            }
            CardState(
                repetitions = newReps,
                intervalDays = newInterval,
                ease = newEase,
                lapses = state.lapses,
                nextReviewAt = addDaysAtMidnight(nowMs, newInterval, timeZone)
            )
        } else {
            val newEase = (currentEase - 0.2f).coerceIn(MIN_EASE, MAX_EASE)
            CardState(
                repetitions = 0,
                intervalDays = 1,
                ease = newEase,
                lapses = state.lapses + 1,
                nextReviewAt = addDaysAtMidnight(nowMs, 1, timeZone)
            )
        }
    }

    fun isDue(
        nextReviewAt: Long?,
        nowMs: Long = System.currentTimeMillis(),
        timeZone: TimeZone = TimeZone.getDefault()
    ): Boolean {
        if (nextReviewAt == null) return true
        return nextReviewAt <= endOfTodayMillis(nowMs, timeZone)
    }

    fun startOfDayMillis(
        timestampMs: Long = System.currentTimeMillis(),
        timeZone: TimeZone = TimeZone.getDefault()
    ): Long {
        val cal = Calendar.getInstance(timeZone).apply {
            timeInMillis = timestampMs
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    fun endOfTodayMillis(
        timestampMs: Long = System.currentTimeMillis(),
        timeZone: TimeZone = TimeZone.getDefault()
    ): Long {
        val cal = Calendar.getInstance(timeZone).apply {
            timeInMillis = timestampMs
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }
        return cal.timeInMillis
    }

    fun addDaysAtMidnight(
        nowMs: Long,
        days: Int,
        timeZone: TimeZone = TimeZone.getDefault()
    ): Long {
        val cal = Calendar.getInstance(timeZone).apply {
            timeInMillis = nowMs
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_YEAR, days)
        }
        return cal.timeInMillis
    }

    fun getStatusBadgeInfo(
        nextReviewAt: Long?,
        nowMs: Long = System.currentTimeMillis(),
        timeZone: TimeZone = TimeZone.getDefault()
    ): StatusBadgeInfo {
        if (nextReviewAt == null) {
            return StatusBadgeInfo(
                badgeType = BadgeType.NEW,
                daysRemaining = 0
            )
        }

        if (isDue(nextReviewAt, nowMs, timeZone)) {
            return StatusBadgeInfo(
                badgeType = BadgeType.DUE,
                daysRemaining = 0
            )
        }

        val startOfToday = startOfDayMillis(nowMs, timeZone)
        val diffMs = nextReviewAt - startOfToday
        val days = ceil(diffMs / 86400000.0).toInt().coerceAtLeast(1)

        return StatusBadgeInfo(
            badgeType = BadgeType.SCHEDULED,
            daysRemaining = days
        )
    }
}
