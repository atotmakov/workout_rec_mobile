package com.workoutrec.workout

import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs

/** Cached log rows with the app's pending changes applied (research R9, data-model "Display model"). */
object DisplayModel {

    /**
     * [renames] (old → new name, feature 004) show cached rows under a waiting rename's new name;
     * their [SetRef.Row] keeps the sheet values, so edits and deletes still find the row.
     */
    fun sets(rows: List<LogRow>, pending: List<PendingChange>, renames: Map<String, String> = emptyMap()): List<DisplaySet> {
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
                null -> DisplaySet(row.key.renamed(renames), SyncState.SYNCED, SetRef.Row(row.rowIndex, row.key))
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

    /** Feature 004 FR-012: everything waiting on the phone, for the sync icon and the sign-out warning. */
    fun waitingCount(pending: List<PendingChange>, exerciseChanges: Int, payments: Int, statuses: Int = 0): Int =
        pendingCount(pending) + exerciseChanges + payments + statuses

    /** Feature 006 data-model "Waiting count": statuses of days with a set, the ones sync can send. */
    fun waitingStatuses(statuses: List<PendingStatus>, sets: List<DisplaySet>, zone: ZoneId): Int {
        val days = sets.mapTo(mutableSetOf()) { SheetTime.workoutDay(it.key.time, zone) }
        return statuses.count { it.date in days }
    }

    /** Feature 004: the drills list with the app's waiting exercise changes applied (data-model.md). */
    fun exercises(cache: List<Exercise>, changes: List<ExerciseChange>, sets: List<DisplaySet>): List<ShownExercise> {
        val used = sets.mapTo(mutableSetOf()) { it.key.exercise }
        val byLastSeen = changes.filter { it.kind != ExerciseChangeKind.ADD }.associateBy { it.lastSeenName }
        val fromCache = cache.mapNotNull { exercise ->
            when (val change = byLastSeen[exercise.name]) {
                null -> ShownExercise(exercise.name, exercise.muscleGroup, pending = false, deletable = exercise.name !in used)
                else -> if (change.kind == ExerciseChangeKind.DELETE) null else ShownExercise(change.name, change.muscleGroup, pending = true, deletable = change.name !in used)
            }
        }
        val added = changes.filter { it.kind == ExerciseChangeKind.ADD }
            .map { ShownExercise(it.name, it.muscleGroup, pending = true, deletable = it.name !in used) }
        return fromCache + added
    }

    /** Feature 004: old → new name for each waiting rename, so history follows it at once. */
    fun renames(changes: List<ExerciseChange>): Map<String, String> =
        changes.filter { it.kind == ExerciseChangeKind.RENAME && it.lastSeenName != null && it.lastSeenName != it.name }
            .associate { it.lastSeenName!! to it.name }

    /** Feature 004: newest first; the same day: waiting first, then the lower sheet rows (added later). */
    fun payments(rows: List<PaymentRow>, pending: List<PendingPayment>): List<ShownPayment> {
        val waiting = pending.sortedByDescending { it.createdAt }
            .map { PaymentLine(it.date, 0, 0, ShownPayment(it.date, it.workouts.toDouble(), it.sumHundredths, pending = true)) }
        val stored = rows.map { PaymentLine(it.date, 1, it.rowIndex, ShownPayment(it.date, it.workouts, it.sumHundredths, pending = false)) }
        return (waiting + stored)
            .sortedWith(compareByDescending<PaymentLine> { it.date }.thenBy { it.group }.thenByDescending { it.rowIndex })
            .map { it.shown }
    }

    private data class PaymentLine(val date: LocalDate, val group: Int, val rowIndex: Int, val shown: ShownPayment)

    fun totals(payments: List<ShownPayment>): MoneyTotals =
        MoneyTotals(payments.sumOf { it.workouts ?: 0.0 }, payments.sumOf { it.sumHundredths ?: 0L })

    private fun SetKey.renamed(renames: Map<String, String>) = renames[exercise]?.let { copy(exercise = it) } ?: this

    private fun PendingChange.toDisplay() = DisplaySet(key, SyncState.NOT_SYNCED, SetRef.Pending(id))
}

/** Finds the row an edit or delete targets (research R5). */
object RowLocator {
    fun locate(rows: List<LogRow>, key: SetKey, hint: Int?): LogRow? =
        rows.filter { it.key == key }.minByOrNull { abs(it.rowIndex - (hint ?: it.rowIndex)) }
}
