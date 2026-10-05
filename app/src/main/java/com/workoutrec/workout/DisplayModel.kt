package com.workoutrec.workout

import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs

/** Cached log rows with the app's pending changes applied (research R9, data-model "Display model"). */
object DisplayModel {

    fun sets(rows: List<LogRow>, pending: List<PendingChange>): List<DisplaySet> {
        val active = pending.filter { it.draftId == null }
        // Which cached row each edit or delete targets; one change per row.
        val targeted = mutableMapOf<Int, PendingChange>()
        val unlocatedEdits = mutableListOf<PendingChange>()
        active.filter { it.kind != ChangeKind.NEW }.forEach { change ->
            val remaining = rows.filter { it.rowIndex !in targeted }
            val row = RowLocator.locate(remaining, change.lastSeen ?: change.key, change.rowHint)
            when {
                row != null -> targeted[row.rowIndex] = change
                change.kind == ChangeKind.EDIT -> unlocatedEdits += change
            }
        }
        val fromRows = rows.mapNotNull { row ->
            when (val change = targeted[row.rowIndex]) {
                null -> DisplaySet(row.key, SyncState.SYNCED, SetRef.Row(row.rowIndex, row.key))
                else -> if (change.kind == ChangeKind.DELETE) null else change.toDisplay()
            }
        }
        val fromPending = active.filter { it.kind == ChangeKind.NEW }.map { it.toDisplay() } + unlocatedEdits.map { it.toDisplay() }
        return (fromRows + fromPending).sortedBy { it.key.time }
    }

    fun today(sets: List<DisplaySet>, today: LocalDate, zone: ZoneId): List<ExerciseGroup> = day(sets, today, zone)

    fun day(sets: List<DisplaySet>, date: LocalDate, zone: ZoneId): List<ExerciseGroup> =
        sets.filter { SheetTime.workoutDay(it.key.time, zone) == date }
            .sortedBy { it.key.time }
            .groupBy { it.key.exercise }
            .map { (exercise, groupSets) -> ExerciseGroup(exercise, groupSets) }

    fun pastDays(sets: List<DisplaySet>, today: LocalDate, zone: ZoneId): List<DaySummary> =
        sets.groupBy { SheetTime.workoutDay(it.key.time, zone) }
            .filterKeys { it < today }
            .map { (date, daySets) -> DaySummary(date, daySets.map { it.key.exercise }.distinct().size, daySets.size) }
            .sortedByDescending { it.date }

    fun recentExercises(sets: List<DisplaySet>, limit: Int = 5): List<String> =
        sets.groupBy { it.key.exercise }
            .mapValues { (_, exerciseSets) -> exerciseSets.maxOf { it.key.time } }
            .entries.sortedByDescending { it.value }
            .take(limit)
            .map { it.key }

    fun pendingCount(pending: List<PendingChange>): Int = pending.count { it.draftId == null }

    private fun PendingChange.toDisplay() = DisplaySet(key, SyncState.NOT_SYNCED, SetRef.Pending(id))
}

/** Finds the row an edit or delete targets (research R5). */
object RowLocator {
    fun locate(rows: List<LogRow>, key: SetKey, hint: Int?): LogRow? =
        rows.filter { it.key == key }.minByOrNull { abs(it.rowIndex - (hint ?: it.rowIndex)) }
}
