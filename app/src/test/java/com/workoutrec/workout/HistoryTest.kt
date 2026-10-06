package com.workoutrec.workout

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// research R11; FR-011, FR-012; US3 scenarios 1-5
class HistoryTest {

    private val zone = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 1, 12)

    private fun key(time: String, kg: Int, reps: Int, exercise: String = "squat") =
        SetKey(LocalDateTime.parse(time).toEpochSecond(zone), exercise, Weight.ofHundredths(kg * 100L), Reps(reps))

    private fun synced(key: SetKey) = DisplaySet(key, SyncState.SYNCED, SetRef.Row(1, key))
    private fun pending(key: SetKey) = DisplaySet(key, SyncState.NOT_SYNCED, SetRef.Pending("p"))

    private val older = listOf(key("2026-01-05T18:00:00", 30, 12), key("2026-01-05T18:03:00", 35, 10))
    private val last = listOf(key("2026-01-09T18:00:00", 40, 6), key("2026-01-09T18:03:00", 40, 8), key("2026-01-09T18:06:00", 35, 12))
    private val otherExercise = key("2026-01-10T18:00:00", 100, 5, exercise = "deadlift")
    private val todays = key("2026-01-12T18:00:00", 45, 3)

    private val sets = (older + last + listOf(otherExercise, todays)).map { synced(it) }

    @Test
    fun `last time is the newest earlier day with the exercise, in time order`() {
        val lastTime = History.lastTime(sets, "squat", today, zone)!!
        assertEquals(LocalDate.of(2026, 1, 9), lastTime.date)
        assertEquals(last, lastTime.sets)
    }

    @Test
    fun `sets logged today do not count as last time`() {
        assertEquals(LocalDate.of(2026, 1, 9), History.lastTime(sets, "squat", today, zone)!!.date)
    }

    @Test
    fun `the record is the heaviest weight and the most reps at that weight`() {
        // 45 x 3 today is heavier than 40 x 8: the record includes today's sets.
        assertEquals(Record(Weight.ofHundredths(4500), Reps(3)), History.record(sets, "squat"))
        val withoutToday = sets.filter { it.key != todays }
        assertEquals(Record(Weight.ofHundredths(4000), Reps(8)), History.record(withoutToday, "squat"))
    }

    @Test
    fun `an exercise without history has no last time and no record`() {
        assertNull(History.lastTime(sets, "plank", today, zone))
        assertNull(History.record(sets, "plank"))
    }

    @Test
    fun `sets saved on the phone count for history`() {
        val phoneOnly = listOf(pending(key("2026-01-11T19:00:00", 50, 5)))
        assertEquals(LocalDate.of(2026, 1, 11), History.lastTime(sets + phoneOnly, "squat", today, zone)!!.date)
    }

    @Test
    fun `pre-fill plans last time's set count with its values`() {
        val prefill = History.prefill(History.lastTime(sets, "squat", today, zone))
        assertEquals(3, prefill.plannedCount)
        assertEquals(Pair(Weight.ofHundredths(4000), Reps(6)), prefill.valuesFor(0))
        assertEquals(Pair(Weight.ofHundredths(3500), Reps(12)), prefill.valuesFor(2))
        // More sets than last time: the extra ones copy last time's last set.
        assertEquals(Pair(Weight.ofHundredths(3500), Reps(12)), prefill.valuesFor(4))
    }

    @Test
    fun `without history three empty sets are planned`() {
        val prefill = History.prefill(null)
        assertEquals(3, prefill.plannedCount)
        assertNull(prefill.valuesFor(0))
    }
}
