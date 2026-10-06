package com.workoutrec.sync

import com.workoutrec.google.ApiError
import com.workoutrec.google.LogAndDrills
import com.workoutrec.google.LogAppend
import com.workoutrec.google.MissingTabException
import com.workoutrec.google.SheetsLogApi
import com.workoutrec.workout.ChangeKind
import com.workoutrec.workout.Exercise
import com.workoutrec.workout.LogRow
import com.workoutrec.workout.PendingChange
import com.workoutrec.workout.Reps
import com.workoutrec.workout.SetKey
import com.workoutrec.workout.SheetTime
import com.workoutrec.workout.Weight
import java.time.DateTimeException
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.CancellationException

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
) {

    /** Never throws for Google or network errors; they become [SyncOutcome.Failed] (contracts "Errors"). */
    suspend fun run(): SyncOutcome {
        val id = spreadsheetId() ?: return SyncOutcome.NoSpreadsheet
        status.setPhase(SyncPhase.Running)
        return try {
            runFor(id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
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
        val pending = store.pendingNow().filter { it.draftId == null }

        // Step 4 (research R5): a new set already in the log was written by an earlier run.
        val unmatched = rows.map { it.key }.toMutableList()
        val done = mutableListOf<String>()
        val appends = mutableListOf<PendingChange>()
        pending.filter { it.kind == ChangeKind.NEW }.sortedBy { it.createdAt }.forEach { change ->
            if (unmatched.remove(change.key)) done += change.id else appends += change
        }

        // Step 5: one atomic write, then a fresh read for the caches.
        val wrote = appends.isNotEmpty()
        if (wrote) {
            sheets.writeLog(
                spreadsheetId = id,
                logSheetId = sheet.logSheetId,
                deletes = emptyList(),
                edits = emptyList(),
                appends = appends.map { it.key.toAppend(zone) },
            )
            done += appends.map { it.id }
            read = sheets.readLogAndDrills(id)
            rows = rowsOf(read, zone)
        }

        // Step 6.
        store.commit(read.drills.map { Exercise(it.name, it.muscleGroup) }, rows, done, emptyList())
        status.markSuccess(now())
        return SyncOutcome.Synced(wrote)
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
