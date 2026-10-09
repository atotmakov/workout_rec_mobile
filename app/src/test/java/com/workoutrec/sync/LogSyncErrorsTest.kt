package com.workoutrec.sync

import com.workoutrec.fakes.FakeLogSyncStore
import com.workoutrec.fakes.FakeSheetsLogApi
import com.workoutrec.fakes.FakeSyncStatusStore
import com.workoutrec.google.ApiError
import com.workoutrec.google.MissingTabException
import com.workoutrec.workout.ChangeKind
import com.workoutrec.workout.PendingChange
import com.workoutrec.workout.Reps
import com.workoutrec.workout.SetKey
import com.workoutrec.workout.Weight
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// contracts/sheets-log.md "Errors"; FR-009, FR-010, SC-004
class LogSyncErrorsTest {

    private val sheets = FakeSheetsLogApi()
    private val store = FakeLogSyncStore()
    private val status = FakeSyncStatusStore()
    private val sync = LogSync(store, sheets, status, spreadsheetId = { "s1" }, now = { 7L })

    private val set = PendingChange("a", ChangeKind.NEW, SetKey(1_700_000_000, "squat", Weight.ofHundredths(2000), Reps(10)), createdAt = 1)

    private suspend fun failingWith(call: String, error: Exception): SyncOutcome {
        store.pending += set
        sheets.failNext(call, error)
        return sync.run()
    }

    @Test
    fun `no connection keeps the sets and reports a network failure`() = runTest {
        val outcome = failingWith("read", ApiError.Offline)
        assertEquals(SyncOutcome.Failed(SyncPhase.Failing(FailReason.NETWORK)), outcome)
        assertEquals(SyncPhase.Failing(FailReason.NETWORK), status.status.value.phase)
        assertEquals(listOf("a"), store.pending.map { it.id })
    }

    @Test
    fun `Google being unavailable is a network failure`() = runTest {
        assertEquals(SyncOutcome.Failed(SyncPhase.Failing(FailReason.NETWORK)), failingWith("write", ApiError.ServiceUnavailable(503)))
        assertEquals(listOf("a"), store.pending.map { it.id })
    }

    @Test
    fun `missing access needs the user to sign in again`() = runTest {
        assertEquals(SyncOutcome.Failed(SyncPhase.NeedsSignIn), failingWith("info", ApiError.AccessDenied("no consent")))
        assertEquals(SyncPhase.NeedsSignIn, status.status.value.phase)
    }

    @Test
    fun `a trashed spreadsheet is a spreadsheet failure`() = runTest {
        assertEquals(SyncOutcome.Failed(SyncPhase.Failing(FailReason.SPREADSHEET)), failingWith("info", ApiError.NotFound))
    }

    @Test
    fun `a missing tab is a structure failure`() = runTest {
        assertEquals(SyncOutcome.Failed(SyncPhase.Failing(FailReason.STRUCTURE)), failingWith("info", MissingTabException("log")))
    }

    @Test
    fun `other errors are reported as other`() = runTest {
        assertEquals(SyncOutcome.Failed(SyncPhase.Failing(FailReason.OTHER)), failingWith("read", ApiError.Unexpected(400, "bad")))
    }

    @Test
    fun `a write whose response was lost is not repeated on the next run`() = runTest {
        store.pending += set
        sheets.loseNextWriteResponse = true
        assertEquals(SyncOutcome.Failed(SyncPhase.Failing(FailReason.NETWORK)), sync.run())
        assertEquals(listOf("a"), store.pending.map { it.id })
        assertEquals(SyncOutcome.Synced(wrote = false), sync.run())
        assertEquals(1, sheets.log.size)
        assertEquals(1, sheets.writes.size)
        assertTrue(store.pending.isEmpty())
    }

    @Test
    fun `a run is shown as running, then idle with the success time`() = runTest {
        sync.run()
        assertEquals(listOf(SyncPhase.Running, SyncPhase.Idle), status.phases)
        assertEquals(7L, status.status.value.lastSuccessAt)
    }

    // quickstart-results.md issue 1: a hanging call must not keep a run (and the sync lock) forever.
    @Test
    fun `a run that hangs ends as a network failure after the time limit`() = runTest {
        store.pending += set
        sheets.hangOn = "read"
        assertEquals(SyncOutcome.Failed(SyncPhase.Failing(FailReason.NETWORK)), sync.run())
        assertEquals(LogSync.RUN_TIMEOUT_MILLIS, testScheduler.currentTime)
        assertEquals(SyncPhase.Failing(FailReason.NETWORK), status.status.value.phase)
        assertEquals(listOf("a"), store.pending.map { it.id })
    }

    // quickstart-results.md issue 1: the shared log shows each run and why it failed.
    @Test
    fun `each run and its error are written to the diagnostic log`() = runTest {
        val logged = mutableListOf<String>()
        val logging = LogSync(store, sheets, status, spreadsheetId = { "s1" }, now = { 7L }, log = { logged += it })
        store.pending += set
        sheets.failNext("read", ApiError.Offline)
        logging.run()
        assertEquals("sync: start", logged.first())
        assertTrue(logged.toString(), logged.any { it.startsWith("sync: failed") && "Offline" in it })
    }

    @Test
    fun `a successful run writes how many changes it sent`() = runTest {
        val logged = mutableListOf<String>()
        val logging = LogSync(store, sheets, status, spreadsheetId = { "s1" }, now = { 7L }, log = { logged += it })
        store.pending += set
        logging.run()
        assertEquals(listOf("sync: start", "sync: 1 pending", "sync: done, wrote 1 new, 0 edited, 0 deleted"), logged)
    }
}
