package com.workoutrec.workout

import java.time.LocalDate
import kotlin.math.roundToLong

enum class PastWorkoutError { FUTURE_DATE, DURATION }

/** Set times of a past workout (research R10, FR-006). */
object PastWorkoutTimes {
    const val MIN_DURATION = 1
    const val MAX_DURATION = 600
    const val DEFAULT_DURATION = 60

    /** [count] times from [start] to start + duration, in entry order, strictly increasing. */
    fun times(start: Long, durationMinutes: Int, count: Int): List<Long> {
        if (count <= 0) return emptyList()
        if (count == 1) return listOf(start)
        val duration = durationMinutes * 60.0
        val times = mutableListOf<Long>()
        for (i in 0 until count) {
            var t = start + (i * duration / (count - 1)).roundToLong()
            // Unique seconds keep every set's key unique (research R5).
            if (times.isNotEmpty() && t <= times.last()) t = times.last() + 1
            times += t
        }
        return times
    }

    /** The date may not be in the future; the duration is 1–600 minutes (FR-006a). */
    fun validate(date: LocalDate, today: LocalDate, durationMinutes: Int): PastWorkoutError? = when {
        date > today -> PastWorkoutError.FUTURE_DATE
        durationMinutes !in MIN_DURATION..MAX_DURATION -> PastWorkoutError.DURATION
        else -> null
    }
}
