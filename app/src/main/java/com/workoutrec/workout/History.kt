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
    fun valuesFor(index: Int): Pair<Weight, Reps>? =
        (sets.getOrNull(index) ?: sets.lastOrNull())?.let { it.weight to it.reps }
}

/** Last time, record and pre-fill (research R11). */
object History {

    /** Planned sets when the exercise has no history (FR-012). */
    const val DEFAULT_SET_COUNT = 3

    fun lastTime(sets: List<DisplaySet>, exercise: String, today: LocalDate, zone: ZoneId): LastTime? =
        sets.filter { it.key.exercise == exercise }
            .groupBy { SheetTime.workoutDay(it.key.time, zone) }
            .filterKeys { it < today }
            .maxByOrNull { it.key }
            ?.let { (date, daySets) -> LastTime(date, daySets.map { it.key }.sortedBy { it.time }) }

    fun record(sets: List<DisplaySet>, exercise: String): Record? {
        val keys = sets.map { it.key }.filter { it.exercise == exercise }
        val heaviest = keys.maxOfOrNull { it.weight.hundredths } ?: return null
        val reps = keys.filter { it.weight.hundredths == heaviest }.maxOf { it.reps.value }
        return Record(Weight.ofHundredths(heaviest), Reps(reps))
    }

    fun prefill(lastTime: LastTime?): Prefill =
        Prefill(lastTime?.sets?.size ?: DEFAULT_SET_COUNT, lastTime?.sets.orEmpty())
}
