package com.workoutrec.sync

import com.workoutrec.fakes.FakeLogSyncStore
import com.workoutrec.fakes.FakeMonitoring
import com.workoutrec.fakes.FakeSheetsLogApi
import com.workoutrec.fakes.FakeSyncStatusStore
import com.workoutrec.google.ApiError
import com.workoutrec.monitoring.ProfileTarget
import com.workoutrec.workout.ChangeKind
import com.workoutrec.workout.PendingChange
import com.workoutrec.workout.Reps
import com.workoutrec.workout.SetKey
import com.workoutrec.workout.Weight
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// Feature 003 FR-011, research R9: a sync run is one profile with a section per step
class LogSyncProfileTest {

    private val sheets = FakeSheetsLogApi()
    private val store = FakeLogSyncStore()
    private val status = FakeSyncStatusStore()
    private val monitoring = FakeMonitoring()
    private val sync = LogSync(store, sheets, status, spreadsheetId = { "s1" }, now = { 7L }, monitoring = monitoring)

    private val set = PendingChange("a", ChangeKind.NEW, SetKey(1_700_000_000, "squat", Weight.ofHundredths(2000), Reps(10)), createdAt = 1)

    @Test
    fun `a run that writes is committed with all four steps`() = runTest {
        store.pending += set
        sync.run()
        val profile = monitoring.profiles.single()
        assertEquals(ProfileTarget.SYNC, profile.target)
        assertTrue(profile.committed)
        assertEquals(listOf("info", "read", "write", "commit"), profile.sections)
    }

    @Test
    fun `a run with nothing to write skips the write step`() = runTest {
        sync.run()
        assertEquals(listOf("info", "read", "commit"), monitoring.profiles.single().sections)
    }

    @Test
    fun `a failed run is dropped`() = runTest {
        store.pending += set
        sheets.failNext("read", ApiError.ServiceUnavailable(503))
        sync.run()
        assertTrue(monitoring.profiles.single().aborted)
    }

    @Test
    fun `a cancelled run is dropped`() = runTest {
        store.pending += set
        sheets.hangOn = "read"
        val job = launch { sync.run() }
        runCurrent()
        job.cancel()
        job.join()
        assertTrue(monitoring.profiles.single().aborted)
    }

    @Test
    fun `no spreadsheet means no profile`() = runTest {
        LogSync(store, sheets, status, spreadsheetId = { null }, monitoring = monitoring).run()
        assertTrue(monitoring.profiles.isEmpty())
    }
}
