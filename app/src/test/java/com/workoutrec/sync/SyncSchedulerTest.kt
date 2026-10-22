package com.workoutrec.sync

import com.workoutrec.fakes.FakeLogSyncStore
import com.workoutrec.fakes.FakeMonitoring
import com.workoutrec.fakes.FakeSheetsLogApi
import com.workoutrec.fakes.FakeSyncStatusStore
import com.workoutrec.monitoring.NonFatalIssue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Feature 005 US1: a failure around a sync run (outside its own handling) is reported
class SyncSchedulerTest {

    private val monitoring = FakeMonitoring()

    private fun scheduler(spreadsheetId: suspend () -> String?) = SyncScheduler(
        LogSync(FakeLogSyncStore(), FakeSheetsLogApi(), FakeSyncStatusStore(), spreadsheetId),
        TestScope(),
        monitoring = monitoring,
    )

    @Test
    fun `an unexpected failure returns null and is reported`() = runTest {
        assertNull(scheduler { throw IllegalStateException("settings unreadable") }.runNow())
        assertEquals(listOf(NonFatalIssue.SYNC_UNEXPECTED to "operation=sync-request"), monitoring.nonFatals.map { it.issue to it.details })
    }

    @Test
    fun `cancellation is passed on and not reported`() = runTest {
        val thrown = runCatching { scheduler { throw CancellationException("stopped") }.runNow() }.exceptionOrNull()
        assertTrue(thrown is CancellationException)
        assertEquals(0, monitoring.nonFatals.size)
    }

    @Test
    fun `a normal run is not reported`() = runTest {
        assertEquals(SyncOutcome.NoSpreadsheet, scheduler { null }.runNow())
        assertEquals(0, monitoring.nonFatals.size)
    }
}
