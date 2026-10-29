package com.workoutrec.sync

import com.workoutrec.automation.AutomationStatusEvaluator
import com.workoutrec.fakes.FakeLogSyncStore
import com.workoutrec.fakes.FakeMonitoring
import com.workoutrec.fakes.FakeSheetsLogApi
import com.workoutrec.fakes.FakeSheetsLogApi.WorkoutCells
import com.workoutrec.fakes.FakeSyncStatusStore
import com.workoutrec.google.ApiError
import com.workoutrec.monitoring.NonFatalIssue
import com.workoutrec.spreadsheet.ReferenceStructure
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

// Feature 007 research R5, R6, R10; contracts/sheets.md calls 24-25; spec US3, FR-009, FR-011, FR-016
class FormCheckTest {

    /** 2026-10-24T10:00Z */
    private val now = 1_792_836_000_000L
    private val sheets = FakeSheetsLogApi(timeZone = "UTC", logSheetId = 3, workoutSheetId = 9, balanceSheetId = 5)
    private val monitoring = FakeMonitoring()
    private val check = FormCheck(sheets, monitoring, now = { now })
    private val sync = LogSync(store = FakeLogSyncStore(), sheets = sheets, status = FakeSyncStatusStore(), spreadsheetId = { "s1" }, now = { now }, monitoring = monitoring, formCheck = check)

    private val sheet = SheetInfoCache("s1", "UTC", 3, drillsSheetId = 1, balanceSheetId = 5, workoutSheetId = 9)
    private val version = ReferenceStructure.STRUCTURE_VERSION_KEY
    /** Days from 1899-12-30 to 2026-10-20. */
    private val serial20 = 46315.0

    private fun reports() = monitoring.nonFatals.map { it.issue to it.details }

    private fun automationOn(scriptVersion: String) {
        sheets.metadata += mapOf(
            AutomationStatusEvaluator.SCRIPT_ID to "s",
            AutomationStatusEvaluator.ENABLED_AT to "2026-10-01T00:00:00Z", AutomationStatusEvaluator.DAILY_TRIGGER to "head",
            AutomationStatusEvaluator.LAST_DAILY_RUN to "2026-10-24T03:00:00Z",
            AutomationStatusEvaluator.SCRIPT_VERSION to scriptVersion,
        )
    }

    @Test
    fun `a ru_RU spreadsheet gets durations with its separator, and unparsed ones are rewritten once`() = runTest {
        sheets.locale = "ru_RU"
        sheets.metadata[version] = "3"
        sheets.balanceA1 = ReferenceStructure.BALANCE_FORMULA
        sheets.log += FakeSheetsLogApi.Cells(serial20 + 0.75, "squat", 20.0, 10)
        sheets.workout += WorkoutCells(serial20, ReferenceStructure.DURATION_FORMULA, 0.0)
        sync.run()
        assertEquals(ReferenceStructure.DURATION_FORMULA.replace(',', ';'), sheets.workout.single().duration)
        assertTrue(reports().isEmpty())
        sync.run()
        assertEquals(1, sheets.repairs.size)
    }

    @Test
    fun `a sync converts an old spreadsheet once`() = runTest {
        sheets.log += FakeSheetsLogApi.Cells(serial20 + 0.75, "squat", 20.0, 10)
        sheets.workout += WorkoutCells(serial20 + 0.75, 45.0, 0.0)
        sheets.balanceA1 = 4.0
        sheets.metadata[version] = "2"
        sync.run()
        assertEquals(ReferenceStructure.DURATION_FORMULA, sheets.workout.single().duration)
        assertEquals(ReferenceStructure.BALANCE_FORMULA, sheets.balanceA1)
        assertEquals("3", sheets.metadata[version])
        assertTrue(reports().isEmpty())
        sync.run()
        assertEquals(1, sheets.repairs.size)
    }

    @Test
    fun `nothing to repair - one read, no write`() = runTest {
        sheets.balanceA1 = ReferenceStructure.BALANCE_FORMULA
        sheets.metadata[version] = "3"
        check.run("s1", sheet)
        assertEquals(listOf("cells:s1"), sheets.calls)
    }

    @Test
    fun `without the workout or balance tab nothing is checked`() = runTest {
        check.run("s1", sheet.copy(balanceSheetId = null))
        check.run("s1", sheet.copy(workoutSheetId = null))
        assertTrue(sheets.calls.isEmpty())
    }

    @Test
    fun `an overwritten formula is restored and reported without values`() = runTest {
        sheets.metadata[version] = "3"
        sheets.workout += WorkoutCells(serial20, 45.0, 0.0)
        sheets.workout += WorkoutCells(serial20 + 1, 30.0, 1.0)
        sheets.balanceA1 = 11.0
        check.run("s1", sheet)
        assertEquals(ReferenceStructure.BALANCE_FORMULA, sheets.balanceA1)
        val report = monitoring.nonFatals.single()
        assertEquals(NonFatalIssue.CALCULATION_RESTORED, report.issue)
        assertEquals("cells=balance:1,duration:2", report.details)
    }

    @Test
    fun `a failed read is reported and does not fail the sync`() = runTest {
        sheets.failNext("cells", ApiError.Unexpected(500, "boom"))
        assertEquals(SyncOutcome.Synced(wrote = false), sync.run())
        assertEquals(listOf(NonFatalIssue.CALCULATION_CHECK_FAILED to "operation=calculation-check"), reports())
    }

    @Test
    fun `a failed repair is reported, and without connection as info`() = runTest {
        sheets.balanceA1 = 3.0
        sheets.failNext("repair", ApiError.Offline)
        check.run("s1", sheet)
        assertEquals(listOf(NonFatalIssue.NO_CONNECTION to "operation=calculation-check"), reports())
    }

    @Test
    fun `cancellation is not swallowed`() = runTest {
        sheets.failNext("cells", CancellationException("stopped"))
        try {
            check.run("s1", sheet)
            fail("expected cancellation")
        } catch (e: CancellationException) {
            assertTrue(reports().isEmpty())
        }
    }

    // US5, T043: an old script still writes the balance at night, so the balance formula waits.
    @Test
    fun `automation on with an old script - durations only, no marker, no report`() = runTest {
        automationOn(scriptVersion = "2")
        sheets.metadata[version] = "2"
        sheets.workout += WorkoutCells(serial20, 45.0, 0.0)
        sheets.balanceA1 = 9.0
        check.run("s1", sheet)
        assertEquals(ReferenceStructure.DURATION_FORMULA, sheets.workout.single().duration)
        assertEquals(9.0, sheets.balanceA1)
        assertEquals("2", sheets.metadata[version])
        assertTrue(reports().isEmpty())
    }

    @Test
    fun `automation on after the script upgrade - the balance formula and the marker`() = runTest {
        automationOn(scriptVersion = "3")
        sheets.metadata[version] = "2"
        sheets.balanceA1 = 9.0
        check.run("s1", sheet)
        assertEquals(ReferenceStructure.BALANCE_FORMULA, sheets.balanceA1)
        assertEquals("3", sheets.metadata[version])
    }
}
