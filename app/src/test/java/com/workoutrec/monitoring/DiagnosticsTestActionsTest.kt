package com.workoutrec.monitoring

import com.workoutrec.fakes.FakeMonitoring
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

// research R10: hidden actions that produce one report of each kind on the phone
class DiagnosticsTestActionsTest {

    private val posted = mutableListOf<() -> Unit>()
    private val slept = mutableListOf<Long>()
    private val monitoring = FakeMonitoring()

    private val heapDumps = mutableListOf<String>()
    private var diskChecks = 0
    private var profileForced = 0

    private val actions = DiagnosticsTestActions(
        runOnMain = { posted += it },
        sleep = { slept += it },
        monitoring = monitoring,
        takeHeapDump = { heapDumps += it },
        runDiskCheck = { diskChecks++ },
        forceProfileNextStart = { profileForced++ },
    )

    @Test
    fun `crash now throws a test crash on the main thread`() {
        actions.crashNow()
        assertEquals(1, posted.size)
        try {
            posted.single().invoke()
            fail("expected a crash")
        } catch (e: DiagnosticsTestCrash) {
            // expected
        }
    }

    @Test
    fun `freeze blocks the main thread for the given time`() {
        actions.freeze(10_000)
        assertTrue(slept.isEmpty())
        posted.single().invoke()
        assertEquals(listOf(10_000L), slept)
    }

    @Test
    fun `send test error reports a diagnostics-test non-fatal`() {
        actions.sendTestError()
        assertEquals(NonFatalIssue.DIAGNOSTICS_TEST, monitoring.nonFatals.single().issue)
    }

    // User Story 3, research R7
    @Test
    fun `memory snapshot now takes a manual snapshot`() {
        actions.memorySnapshotNow()
        assertEquals(listOf("manual"), heapDumps)
    }

    // User Story 4, research R8
    @Test
    fun `disk check now runs one disk check`() {
        actions.diskCheckNow()
        assertEquals(1, diskChecks)
    }

    // User Story 5, research R9
    @Test
    fun `profile next app start forces the next start to be profiled`() {
        actions.profileNextAppStart()
        assertEquals(1, profileForced)
    }
}
