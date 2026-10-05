package com.workoutrec.workout

import java.time.LocalDate
import java.time.ZoneId

/** Cached log rows with the app's pending changes applied (research R9, data-model "Display model"). */
object DisplayModel {

    fun sets(rows: List<LogRow>, pending: List<PendingChange>): List<DisplaySet> = TODO()

    fun today(sets: List<DisplaySet>, today: LocalDate, zone: ZoneId): List<ExerciseGroup> = TODO()

    fun day(sets: List<DisplaySet>, date: LocalDate, zone: ZoneId): List<ExerciseGroup> = TODO()

    fun pastDays(sets: List<DisplaySet>, today: LocalDate, zone: ZoneId): List<DaySummary> = TODO()

    fun recentExercises(sets: List<DisplaySet>, limit: Int = 5): List<String> = TODO()

    fun pendingCount(pending: List<PendingChange>): Int = TODO()
}

/** Finds the row an edit or delete targets (research R5). */
object RowLocator {
    fun locate(rows: List<LogRow>, key: SetKey, hint: Int?): LogRow? = TODO()
}
