package com.workoutrec.sync

import com.workoutrec.google.ApiError
import com.workoutrec.google.LogAndDrills
import com.workoutrec.google.LogAppend
import com.workoutrec.google.LogEdit
import com.workoutrec.google.MissingTabException
import com.workoutrec.google.SheetsLogApi
import com.workoutrec.workout.ChangeKind
import com.workoutrec.workout.Exercise
import com.workoutrec.workout.LogRow
import com.workoutrec.workout.PendingChange
import com.workoutrec.workout.Reps
import com.workoutrec.workout.RowLocator
import com.workoutrec.workout.SetKey
import com.workoutrec.workout.SheetTime
import com.workoutrec.workout.Weight
import java.time.DateTimeException
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

enum class NoticeKind { CONFLICT_DROPPED, WORKOUT_ROW_NOT_UPDATED }

/** A message about a sync result (data-model.md `sync_notice`). */
data class SyncNotice(val kind: NoticeKind, val key: SetKey)

/** The phone data the sync reads and writes (implemented by `WorkoutRepository`). */
interface LogSyncStore {
    suspend fun pendingNow(): List<PendingChange>

    /** One transaction: replace caches, remove handled changes, add notices (data-model "Sync run"). */
    suspend fun commit(exercises: List<Exercise>, rows: List<LogRow>, doneIds: Collection<String>, notices: List<SyncNotice>)

    /** The bound spreadsheet changed: drop caches, edits, deletes and notices; keep new sets (research R13). */
    suspend fun keepOnlyNewSets()
}

sealed interface SyncOutcome {
    data class Synced(val wrote: Boolean) : SyncOutcome
    data class Failed(val phase: SyncPhase) : SyncOutcome
    data object NoSpreadsheet : SyncOutcome
}

/** One sync run (contracts/sheets-log.md "Sync algorithm"). */
class LogSync(
    private val store: LogSyncStore,
    private val sheets: SheetsLogApi,
    private val status: SyncStatusStore,
    private val spreadsheetId: suspend () -> String?,
    private val now: () -> Long = System::currentTimeMillis,
    /** Diagnostic log for background sync (quickstart-results.md issue 1). */
    private val log: (String) -> Unit = {},
) {

    /** Never throws for Google or network errors; they become [SyncOutcome.Failed] (contracts "Errors"). */
    suspend fun run(): SyncOutcome {
        log("sync: start")
        val id = spreadsheetId() ?: return SyncOutcome.NoSpreadsheet.also { log("sync: no spreadsheet") }
        status.setPhase(SyncPhase.Running)
        return try {
            withTimeout(RUN_TIMEOUT_MILLIS) { runFor(id) }
        } catch (e: TimeoutCancellationException) {
            // A hanging call must not hold the sync lock forever (quickstart-results.md issue 1).
            log("sync: failed: no answer in ${RUN_TIMEOUT_MILLIS / 1000} s")
            status.setPhase(SyncPhase.Failing(FailReason.NETWORK))
            SyncOutcome.Failed(SyncPhase.Failing(FailReason.NETWORK))
        } catch (e: CancellationException) {
            log("sync: cancelled")
            throw e
        } catch (e: Exception) {
            log("sync: failed: ${e::class.simpleName}: ${e.message}")
            val phase = phaseOf(e)
            status.setPhase(phase)
            SyncOutcome.Failed(phase)
        }
    }

    private suspend fun runFor(id: String): SyncOutcome {
        // Research R13: the spreadsheet was rewritten or re-created; only new sets still apply.
        status.current().sheet?.let { cached -> if (cached.spreadsheetId != id) store.keepOnlyNewSets() }
        val sheet = sheetInfo(id)
        val zone = zoneOf(sheet.timeZone)

        var read = sheets.readLogAndDrills(id)
        var rows = rowsOf(read, zone)
        val pending = store.pendingNow().filter { it.draftId == null }.sortedBy { it.createdAt }
        log("sync: ${pending.size} pending")

        // Step 4 (research R5): a new set already in the log was written by an earlier run.
        val unmatched = rows.map { it.key }.toMutableList()
        val done = mutableListOf<String>()
        val written = mutableListOf<String>()
        val appends = mutableListOf<PendingChange>()
        pending.filter { it.kind == ChangeKind.NEW }.forEach { change ->
            if (unmatched.remove(change.key)) done += change.id else appends += change
        }

        // Step 4 for edits and deletes: find each target row by the values the app last saw (FR-015).
        val targeted = mutableSetOf<Int>()
        val deletes = mutableListOf<Int>()
        val edits = mutableListOf<LogEdit>()
        val notices = mutableListOf<SyncNotice>()
        pending.filter { it.kind != ChangeKind.NEW }.forEach { change ->
            val lastSeen = change.lastSeen ?: change.key
            val free = rows.filter { it.rowIndex !in targeted }
            val row = RowLocator.locate(free, lastSeen, change.rowHint)
            when {
                // Analysis fix U2: a delete whose row is gone has reached its goal.
                row == null && change.kind == ChangeKind.DELETE -> done += change.id
                // Analysis fix U2: an interrupted earlier run already applied this edit.
                row == null && RowLocator.locate(free, change.key, change.rowHint) != null -> done += change.id
                // Changed or removed in the web UI: the sheet wins and the user is told.
                row == null -> {
                    done += change.id
                    notices += SyncNotice(NoticeKind.CONFLICT_DROPPED, lastSeen)
                }
                change.kind == ChangeKind.DELETE -> {
                    targeted += row.rowIndex
                    deletes += row.rowIndex
                    written += change.id
                    if (changesWorkoutRow(row, rows, zone)) notices += SyncNotice(NoticeKind.WORKOUT_ROW_NOT_UPDATED, row.key)
                }
                row.key == change.key -> done += change.id
                else -> {
                    targeted += row.rowIndex
                    edits += LogEdit(row.rowIndex, change.key.exercise, change.key.weight.toDouble(), change.key.reps.value)
                    written += change.id
                }
            }
        }

        // Step 5: one atomic write, then a fresh read for the caches.
        val wrote = appends.isNotEmpty() || deletes.isNotEmpty() || edits.isNotEmpty()
        if (wrote) {
            sheets.writeLog(
                spreadsheetId = id,
                logSheetId = sheet.logSheetId,
                deletes = deletes,
                edits = edits,
                appends = appends.map { it.key.toAppend(zone) },
            )
            done += written + appends.map { it.id }
            read = sheets.readLogAndDrills(id)
            rows = rowsOf(read, zone)
        }

        // Step 6.
        store.commit(read.drills.map { Exercise(it.name, it.muscleGroup) }, rows, done, notices)
        status.markSuccess(now())
        log("sync: done, wrote ${appends.size} new, ${edits.size} edited, ${deletes.size} deleted")
        return SyncOutcome.Synced(wrote)
    }

    /**
     * The daily script added a workout row for a past day once, from its first and last set; deleting
     * one of those (or the day's only set) makes that row wrong (spec edge case). Edits never change
     * times, so they never affect it.
     */
    private fun changesWorkoutRow(row: LogRow, rows: List<LogRow>, zone: ZoneId): Boolean {
        val day = SheetTime.workoutDay(row.key.time, zone)
        val today = SheetTime.workoutDay(Math.floorDiv(now(), 1000L), zone)
        if (day >= today) return false
        val daySets = rows.filter { SheetTime.workoutDay(it.key.time, zone) == day }.sortedBy { it.key.time }
        return row == daySets.first() || row == daySets.last()
    }

    companion object {
        /** quickstart-results.md issue 1. */
        const val RUN_TIMEOUT_MILLIS = 120_000L
    }

    private fun phaseOf(e: Exception): SyncPhase = when (e) {
        is ApiError.Offline, is ApiError.ServiceUnavailable -> SyncPhase.Failing(FailReason.NETWORK)
        is ApiError.AccessDenied, is ApiError.TokenExpired -> SyncPhase.NeedsSignIn
        is ApiError.NotFound -> SyncPhase.Failing(FailReason.SPREADSHEET)
        is MissingTabException -> SyncPhase.Failing(FailReason.STRUCTURE)
        else -> SyncPhase.Failing(FailReason.OTHER)
    }

    /** Call 20, once per spreadsheet (research R8). */
    private suspend fun sheetInfo(id: String): SheetInfoCache {
        status.current().sheet?.takeIf { it.spreadsheetId == id }?.let { return it }
        val info = sheets.logSheetInfo(id)
        return SheetInfoCache(id, info.timeZone, info.logSheetId).also { status.setSheet(it) }
    }

    private fun zoneOf(timeZone: String): ZoneId =
        try {
            ZoneId.of(timeZone)
        } catch (e: DateTimeException) {
            ZoneOffset.UTC
        }

    private fun rowsOf(read: LogAndDrills, zone: ZoneId): List<LogRow> = read.log.map {
        LogRow(it.rowIndex, SetKey(SheetTime.toEpochSeconds(it.serial, zone), it.exercise, Weight.fromSheet(it.weight), Reps(it.reps)))
    }

    private fun SetKey.toAppend(zone: ZoneId) =
        LogAppend(SheetTime.toSerial(time, zone), exercise, weight.toDouble(), reps.value)
}
