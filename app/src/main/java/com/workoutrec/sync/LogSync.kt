package com.workoutrec.sync

import com.workoutrec.google.SheetsLogApi
import com.workoutrec.workout.Exercise
import com.workoutrec.workout.LogRow
import com.workoutrec.workout.PendingChange
import com.workoutrec.workout.SetKey

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
    suspend fun run(): SyncOutcome = TODO()
}
