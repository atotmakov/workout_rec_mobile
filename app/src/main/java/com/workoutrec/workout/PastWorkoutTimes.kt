package com.workoutrec.workout

import java.time.LocalDate

enum class PastWorkoutError { FUTURE_DATE, DURATION }

/** Set times of a past workout (research R10, FR-006). */
object PastWorkoutTimes {
    const val MIN_DURATION = 1
    const val MAX_DURATION = 600
    const val DEFAULT_DURATION = 60

    /** [count] times from [start] to start + duration, in entry order, strictly increasing. */
    fun times(start: Long, durationMinutes: Int, count: Int): List<Long> = TODO()

    /** The date may not be in the future; the duration is 1–600 minutes (FR-006a). */
    fun validate(date: LocalDate, today: LocalDate, durationMinutes: Int): PastWorkoutError? = TODO()
}
