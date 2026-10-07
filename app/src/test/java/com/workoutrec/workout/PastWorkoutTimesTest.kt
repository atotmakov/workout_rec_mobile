package com.workoutrec.workout

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// research R10; FR-006, FR-006a; SC-009
class PastWorkoutTimesTest {

    private val start = 1_768_240_800L

    @Test
    fun `one set gets the start time`() {
        assertEquals(listOf(start), PastWorkoutTimes.times(start, durationMinutes = 60, count = 1))
    }

    @Test
    fun `sets are spread evenly from start to end`() {
        assertEquals(
            listOf(start, start + 900, start + 1800, start + 2700, start + 3600),
            PastWorkoutTimes.times(start, durationMinutes = 60, count = 5),
        )
    }

    @Test
    fun `times stay strictly increasing when there are more sets than seconds`() {
        val times = PastWorkoutTimes.times(start, durationMinutes = 1, count = 200)
        assertEquals(start, times.first())
        assertTrue(times.zipWithNext().all { (a, b) -> b > a })
    }

    @Test
    fun `no sets means no times`() {
        assertEquals(emptyList<Long>(), PastWorkoutTimes.times(start, durationMinutes = 60, count = 0))
    }

    @Test
    fun `the date cannot be in the future and the duration is 1 to 600 minutes`() {
        val today = LocalDate.of(2026, 1, 12)
        assertEquals(PastWorkoutError.FUTURE_DATE, PastWorkoutTimes.validate(LocalDate.of(2026, 1, 13), today, 60))
        assertEquals(PastWorkoutError.DURATION, PastWorkoutTimes.validate(today, today, 0))
        assertEquals(PastWorkoutError.DURATION, PastWorkoutTimes.validate(today, today, 601))
        assertNull(PastWorkoutTimes.validate(today, today, 1))
        assertNull(PastWorkoutTimes.validate(LocalDate.of(2026, 1, 11), today, 600))
    }
}
