package com.workoutrec.workout

import java.time.LocalDate

/** A `workout` row with a date; [rowIndex] is the 0-based sheet row (row 2 → 1). */
data class DayRow(val rowIndex: Int, val date: LocalDate, val status: DayStatus)

/** A day's status into column C of an existing row. */
data class RowUpdate(val rowIndex: Int, val status: DayStatus)

/** A new row: the date, the duration formula, the status. */
data class RowAppend(val date: LocalDate, val status: DayStatus)

data class DayRowsResult(
    val updates: List<RowUpdate>,
    /** Rows to delete, highest first. */
    val deletes: List<Int>,
    /** In date order. */
    val appends: List<RowAppend>,
    /** Waiting statuses already right in the sheet. */
    val synced: List<LocalDate>,
    /** Waiting statuses sent in [updates] or [appends]. */
    val written: List<LocalDate>,
    /** Waiting statuses changed in the sheet since the phone choice; the sheet wins. */
    val dropped: List<LocalDate>,
    /** The status each dropped date keeps. */
    val kept: List<Pair<LocalDate, DayStatus>>,
)

/**
 * Feature 007 research R3: keeps the `workout` tab in step with the log, one row per date with
 * sets and only for those dates. The attached script's `workoutRowsPlan` follows the same rule;
 * both pass `apps-script/tests/fixtures/day-rows.json` (research R4).
 */
object DayRowsPlan {

    /**
     * [daysWithSets]: dates with sets once this run's writes are applied. [logEmpty]: the log read had
     * no sets at all, so nothing is deleted (spec FR-007a exception).
     */
    fun plan(rows: List<DayRow>, daysWithSets: Set<LocalDate>, logEmpty: Boolean, waiting: List<PendingStatus>): DayRowsResult {
        // Several rows for one date: only the first counts (feature 006).
        val byDate = rows.distinctBy { it.date }.associateBy { it.date }
        val known = byDate.mapValuesTo(mutableMapOf()) { it.value.status }
        val updates = mutableListOf<RowUpdate>()
        val appends = mutableListOf<RowAppend>()
        val synced = mutableListOf<LocalDate>()
        val written = mutableListOf<LocalDate>()
        val dropped = mutableListOf<LocalDate>()
        val kept = mutableListOf<Pair<LocalDate, DayStatus>>()

        // Feature 006 contracts/sheets.md "What gets written".
        waiting.sortedBy { it.date }.forEach { status ->
            val row = byDate[status.date]
            when {
                row != null && status.lastSeen != null && row.status != status.lastSeen -> {
                    dropped += status.date
                    kept += status.date to row.status
                }
                status.date !in daysWithSets -> Unit
                row == null -> {
                    appends += RowAppend(status.date, status.status)
                    known[status.date] = status.status
                    written += status.date
                }
                row.status == status.status -> synced += status.date
                else -> {
                    updates += RowUpdate(row.rowIndex, status.status)
                    known[status.date] = status.status
                    written += status.date
                }
            }
        }

        // Days with sets and no row get the default, earliest first so each feeds the next.
        daysWithSets.filter { it !in known }.sorted().forEach { date ->
            val status = DayStatusRules.defaultFor(date, known)
            appends += RowAppend(date, status)
            known[date] = status
        }

        val deletes = if (logEmpty) emptyList() else rows.filter { it.date !in daysWithSets }.map { it.rowIndex }.sortedDescending()
        return DayRowsResult(updates, deletes, appends.sortedBy { it.date }, synced, written, dropped, kept)
    }
}
