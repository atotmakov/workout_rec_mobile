package com.workoutrec.workout

import java.time.LocalDate
import java.time.ZoneId

/** The sets of the newest earlier day with an exercise (FR-011). */
data class LastTime(val date: LocalDate, val sets: List<SetKey>)

/** Heaviest weight and the most reps at that weight, as highlighted in the rec tab (FR-011). */
data class Record(val weight: Weight, val reps: Reps)

/** Planned set count and the values each set starts with (FR-012). */
data class Prefill(val plannedCount: Int, private val sets: List<SetKey>) {
    /** Set [index]'s weight and reps: last time's set, or its last set when there were fewer. */
    fun valuesFor(index: Int): Pair<Weight, Reps>? = TODO()
}

/** Last time, record and pre-fill (research R11). */
object History {
    fun lastTime(sets: List<DisplaySet>, exercise: String, today: LocalDate, zone: ZoneId): LastTime? = TODO()

    fun record(sets: List<DisplaySet>, exercise: String): Record? = TODO()

    fun prefill(lastTime: LastTime?): Prefill = TODO()
}
