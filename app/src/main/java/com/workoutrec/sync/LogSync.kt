package com.workoutrec.sync

import com.workoutrec.google.ApiError
import com.workoutrec.google.DrillsWrite
import com.workoutrec.google.LogAndDrills
import com.workoutrec.google.LogAppend
import com.workoutrec.google.LogEdit
import com.workoutrec.google.MissingTabException
import com.workoutrec.google.SheetsLogApi
import com.workoutrec.monitoring.Monitoring
import com.workoutrec.monitoring.NoMonitoring
import com.workoutrec.monitoring.NonFatalIssue
import com.workoutrec.monitoring.Profile
import com.workoutrec.monitoring.ProfileTarget
import com.workoutrec.workout.ChangeKind
import com.workoutrec.workout.Exercise
import com.workoutrec.workout.ExerciseChange
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

enum class NoticeKind { CONFLICT_DROPPED, WORKOUT_ROW_NOT_UPDATED, EXERCISE_CHANGE_DROPPED, EXERCISE_IN_USE }

/** A message about a sync result (data-model.md `sync_notice`). */
data class SyncNotice(val kind: NoticeKind, val key: SetKey) {
    companion object {
        /** Feature 004: a notice about an exercise; only the name is used. */
        fun exercise(kind: NoticeKind, name: String) = SyncNotice(kind, SetKey(0, name, Weight.ofHundredths(0), Reps(0)))
    }
}

/** The phone data the sync reads and writes (implemented by `WorkoutRepository`). */
interface LogSyncStore {
    suspend fun pendingNow(): List<PendingChange>

    /** Feature 004: waiting exercise changes, oldest first. */
    suspend fun exerciseChangesNow(): List<ExerciseChange> = emptyList()

    /**
     * One transaction: replace caches, remove handled changes, add notices (data-model "Sync run");
     * feature 004: remove handled exercise changes and give waiting sets of a dropped rename their
     * old name back ([renamesBack]: new → old).
     */
    suspend fun commit(
        exercises: List<Exercise>,
        rows: List<LogRow>,
        doneIds: Collection<String>,
        notices: List<SyncNotice>,
        doneExerciseIds: Collection<String> = emptyList(),
        renamesBack: Map<String, String> = emptyMap(),
    )

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
    /** Feature 003: handled errors as non-fatal events (FR-005). */
    private val monitoring: Monitoring = NoMonitoring,
) {

    /** Never throws for Google or network errors; they become [SyncOutcome.Failed] (contracts "Errors"). */
    suspend fun run(): SyncOutcome {
        log("sync: start")
        val id = spreadsheetId() ?: return SyncOutcome.NoSpreadsheet.also { log("sync: no spreadsheet") }
        val profile = monitoring.beginProfile(ProfileTarget.SYNC)
        status.setPhase(SyncPhase.Running)
        return try {
            withTimeout(RUN_TIMEOUT_MILLIS) { runFor(id, profile) }.also { profile.commit() }
        } catch (e: TimeoutCancellationException) {
            profile.abort()
            // A hanging call must not hold the sync lock forever (quickstart-results.md issue 1).
            log("sync: failed: no answer in ${RUN_TIMEOUT_MILLIS / 1000} s")
            monitoring.nonFatal(NonFatalIssue.SYNC_TIMEOUT, e)
            status.setPhase(SyncPhase.Failing(FailReason.NETWORK))
            SyncOutcome.Failed(SyncPhase.Failing(FailReason.NETWORK))
        } catch (e: CancellationException) {
            profile.abort()
            log("sync: cancelled")
            throw e
        } catch (e: Exception) {
            profile.abort()
            log("sync: failed: ${e::class.simpleName}: ${e.message}")
            monitoring.nonFatal(NonFatalIssue.SYNC_FAILED, e)
            val phase = phaseOf(e)
            status.setPhase(phase)
            SyncOutcome.Failed(phase)
        }
    }

    /** [profile] gets one section per step: info, read, write (only when writing), commit. */
    private suspend fun runFor(id: String, profile: Profile): SyncOutcome {
        // Research R13: the spreadsheet was rewritten or re-created; only new sets still apply.
        status.current().sheet?.let { cached -> if (cached.spreadsheetId != id) store.keepOnlyNewSets() }
        val sheet = profile.section("info").use { sheetInfo(id) }
        val zone = zoneOf(sheet.timeZone)

        var read = profile.section("read").use { sheets.readLogAndDrills(id) }
        var rows = rowsOf(read, zone)
        // Feature 004 research R5: exercise changes first, so a dropped rename's sets keep the old name.
        val exercisePlan = ExercisePlan.of(store.exerciseChangesNow().sortedBy { it.createdAt }, read.drills, read.log)
        val pending = store.pendingNow().filter { it.draftId == null }.sortedBy { it.createdAt }
            .map { change -> exercisePlan.renamesBack[change.key.exercise]?.takeIf { change.kind != ChangeKind.DELETE }?.let { change.copy(key = change.key.copy(exercise = it)) } ?: change }
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
        val notices = exercisePlan.notices.toMutableList()
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
        val wrote = appends.isNotEmpty() || deletes.isNotEmpty() || edits.isNotEmpty() || exercisePlan.writes
        val exercisesDone = exercisePlan.done.toMutableList()
        if (wrote) {
            profile.section("write").use {
                sheets.writeLog(
                    spreadsheetId = id,
                    logSheetId = sheet.logSheetId,
                    deletes = deletes,
                    edits = edits,
                    appends = appends.map { it.key.toAppend(zone) },
                    drills = exercisePlan.takeIf { it.writes }?.let { plan ->
                        DrillsWrite(sheet.drillsSheetId ?: 0, sheet.recSheetId, plan.deletes, plan.renames, plan.replaces, plan.appends)
                    },
                )
                done += written + appends.map { it.id }
                exercisesDone += exercisePlan.written
                read = sheets.readLogAndDrills(id)
                rows = rowsOf(read, zone)
            }
        }

        // Step 6.
        profile.section("commit").use {
            store.commit(read.drills.map { Exercise(it.name, it.muscleGroup) }, rows, done, notices, exercisesDone, exercisePlan.renamesBack)
            status.markSuccess(now())
        }
        log("sync: done, wrote ${appends.size} new, ${edits.size} edited, ${deletes.size} deleted, ${exercisePlan.written.size} exercise changes")
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

    /** Call 20, once per spreadsheet (research R8), and again for a cache from before feature 004. */
    private suspend fun sheetInfo(id: String): SheetInfoCache {
        status.current().sheet?.takeIf { it.spreadsheetId == id && it.drillsSheetId != null }?.let { return it }
        val info = sheets.logSheetInfo(id)
        return SheetInfoCache(id, info.timeZone, info.logSheetId, info.drillsSheetId, info.recSheetId, info.moneySheetId, info.balanceSheetId)
            .also { status.setSheet(it) }
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
