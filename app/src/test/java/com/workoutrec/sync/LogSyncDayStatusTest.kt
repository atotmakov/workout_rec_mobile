package com.workoutrec.sync

import com.workoutrec.fakes.FakeLogSyncStore
import com.workoutrec.fakes.FakeMonitoring
import com.workoutrec.fakes.FakeSheetsLogApi
import com.workoutrec.fakes.FakeSheetsLogApi.WorkoutCells
import com.workoutrec.fakes.FakeSyncStatusStore
import com.workoutrec.google.WorkoutAppend
import com.workoutrec.google.WorkoutUpdate
import com.workoutrec.monitoring.NonFatalIssue
import com.workoutrec.workout.ChangeKind
import com.workoutrec.workout.DayStatus
import com.workoutrec.workout.KnownStatus
import com.workoutrec.workout.PendingChange
import com.workoutrec.workout.PendingStatus
import com.workoutrec.workout.Reps
import com.workoutrec.workout.SetKey
import com.workoutrec.workout.Weight
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Feature 006 research R4, contracts/sheets.md "What gets written"; spec US1, FR-004, FR-005, FR-010
class LogSyncDayStatusTest {

    private val sheets = FakeSheetsLogApi(timeZone = "UTC", logSheetId = 3, workoutSheetId = 9)
    private val store = FakeLogSyncStore()
    private val monitoring = FakeMonitoring()
    private val sync = LogSync(store, sheets, FakeSyncStatusStore(), spreadsheetId = { "s1" }, monitoring = monitoring)

    private val oct20 = LocalDate.of(2026, 10, 20)
    private val oct21 = oct20.plusDays(1)
    /** Days from 1899-12-30 to 2026-10-20. */
    private val serial20 = 46315.0

    /** A set at 18:00 UTC on [date], already in the log tab. */
    private fun logSet(date: LocalDate) {
        sheets.log += FakeSheetsLogApi.Cells(serial20 + (date.toEpochDay() - oct20.toEpochDay()) + 0.75, "squat", 20.0, 10)
    }

    private fun newSet(id: String, date: LocalDate) = PendingChange(
        id, ChangeKind.NEW, SetKey(date.atTime(18, 0).toEpochSecond(ZoneOffset.UTC), "squat", Weight.ofHundredths(2000), Reps(10)), createdAt = 1,
    )

    private fun waiting(date: LocalDate, status: DayStatus, lastSeen: DayStatus? = null) = PendingStatus(date, status, lastSeen)

    private val workoutWrite get() = sheets.writes.single().workout

    @Test
    fun `a day with sets and no workout row gets a new row`() = runTest {
        logSet(oct20)
        store.statuses += waiting(oct20, DayStatus.FREE)
        sync.run()
        assertEquals(listOf(WorkoutAppend(serial20, DayStatus.FREE)), workoutWrite?.appends)
        assertEquals(9, workoutWrite?.workoutSheetId)
        assertTrue(store.statuses.isEmpty())
        assertEquals(listOf(KnownStatus(oct20, DayStatus.FREE, pending = false)), store.cachedStatuses)
    }

    @Test
    fun `a row with a different status is updated in column C`() = runTest {
        logSet(oct20)
        sheets.workout += WorkoutCells(serial20, 45.0, 0.0)
        store.statuses += waiting(oct20, DayStatus.FREE, lastSeen = DayStatus.PAID)
        sync.run()
        assertEquals(listOf(WorkoutUpdate(1, DayStatus.FREE)), workoutWrite?.updates)
        assertEquals(1.0, sheets.workout.single().alone)
        assertTrue(store.statuses.isEmpty())
    }

    @Test
    fun `a row that already has the status is not written`() = runTest {
        logSet(oct20)
        sheets.workout += WorkoutCells(serial20, 45.0, 1.0)
        store.statuses += waiting(oct20, DayStatus.FREE, lastSeen = DayStatus.FREE)
        sync.run()
        assertTrue(sheets.writes.isEmpty())
        assertTrue(store.statuses.isEmpty())
        assertEquals(listOf(oct20), store.syncedStatusDates)
    }

    @Test
    fun `a day whose first set is sent in the same run gets its row in the same write`() = runTest {
        store.pending += newSet("s", oct20)
        store.statuses += waiting(oct20, DayStatus.PAID)
        sync.run()
        val write = sheets.writes.single()
        assertEquals(1, write.appends.size)
        assertEquals(listOf(WorkoutAppend(serial20, DayStatus.PAID)), write.workout?.appends)
    }

    @Test
    fun `a day without sets keeps waiting and nothing is written`() = runTest {
        store.statuses += waiting(oct20, DayStatus.FREE)
        sync.run()
        assertTrue(sheets.writes.isEmpty())
        assertEquals(listOf(waiting(oct20, DayStatus.FREE)), store.statuses)
    }

    @Test
    fun `only the first row of a date is updated`() = runTest {
        logSet(oct20)
        sheets.workout += WorkoutCells(serial20, 45.0, 0.0)
        sheets.workout += WorkoutCells(serial20, 10.0, 0.0)
        store.statuses += waiting(oct20, DayStatus.FREE, lastSeen = DayStatus.PAID)
        sync.run()
        assertEquals(listOf(WorkoutUpdate(1, DayStatus.FREE)), workoutWrite?.updates)
        assertEquals(0.0, sheets.workout[1].alone)
    }

    @Test
    fun `new rows are appended in date order`() = runTest {
        logSet(oct20)
        logSet(oct21)
        store.statuses += waiting(oct21, DayStatus.PAID)
        store.statuses += waiting(oct20, DayStatus.FREE)
        sync.run()
        assertEquals(listOf(WorkoutAppend(serial20, DayStatus.FREE), WorkoutAppend(serial20 + 1, DayStatus.PAID)), workoutWrite?.appends)
    }

    @Test
    fun `the workout tab is read with the log, and the cache replaced, even with nothing waiting`() = runTest {
        sheets.workout += WorkoutCells(serial20, 45.0, 1.0)
        sheets.workout += WorkoutCells(serial20 + 1, 30.0, 0.0)
        sync.run()
        assertEquals(1, sheets.workoutReads)
        assertEquals(listOf(KnownStatus(oct20, DayStatus.FREE, false), KnownStatus(oct21, DayStatus.PAID, false)), store.cachedStatuses)
    }

    @Test
    fun `the last seen value still in the sheet means the phone's choice is written`() = runTest {
        logSet(oct20)
        sheets.workout += WorkoutCells(serial20, 45.0, 0.0)
        store.statuses += waiting(oct20, DayStatus.FREE, lastSeen = DayStatus.PAID)
        sync.run()
        assertEquals(1.0, sheets.workout.single().alone)
        assertTrue(store.notices.isEmpty())
    }

    @Test
    fun `a status changed in the sheet since the phone change wins and the user is told`() = runTest {
        logSet(oct20)
        sheets.workout += WorkoutCells(serial20, 45.0, 1.0)
        store.statuses += waiting(oct20, DayStatus.PAID, lastSeen = DayStatus.PAID)
        sync.run()
        assertTrue(sheets.writes.isEmpty())
        assertEquals(1.0, sheets.workout.single().alone)
        assertTrue(store.statuses.isEmpty())
        assertEquals(listOf(oct20), store.droppedStatusDates)
        assertEquals(listOf(SyncNotice.dayStatus(oct20, DayStatus.FREE)), store.notices)
    }

    @Test
    fun `a row added since a change made with no row is not a conflict`() = runTest {
        // The nightly script added the day's row while the phone's default waited (research R4).
        logSet(oct20)
        sheets.workout += WorkoutCells(serial20, 45.0, 0.0)
        store.statuses += waiting(oct20, DayStatus.FREE, lastSeen = null)
        sync.run()
        assertEquals(listOf(WorkoutUpdate(1, DayStatus.FREE)), workoutWrite?.updates)
        assertTrue(store.notices.isEmpty())
    }

    @Test
    fun `several changes before a run give one write with the last status`() = runTest {
        // The phone keeps one row per date (data-model "Writes"), so the run sees only the last.
        logSet(oct20)
        store.statuses += waiting(oct20, DayStatus.PAID)
        sync.run()
        assertEquals(1, sheets.writes.single().workout?.appends?.size)
    }

    // US1 edge case, FR-011
    @Test
    fun `without a workout tab statuses wait, sets still sync and one report is sent`() = runTest {
        sheets.workoutSheetId = null
        store.pending += newSet("s", oct20)
        store.statuses += waiting(oct20, DayStatus.FREE)
        sync.run()
        assertEquals(1, sheets.writes.single().appends.size)
        assertNull(sheets.writes.single().workout)
        assertEquals(0, sheets.workoutReads)
        assertEquals(listOf(waiting(oct20, DayStatus.FREE)), store.statuses)
        assertNull(store.cachedStatuses)
        val report = monitoring.nonFatals.single()
        assertEquals(NonFatalIssue.WORKOUT_TAB_MISSING, report.issue)
        assertEquals("operation=sync", report.details)
    }

    @Test
    fun `without a workout tab and nothing waiting nothing is reported`() = runTest {
        sheets.workoutSheetId = null
        sync.run()
        assertTrue(monitoring.nonFatals.isEmpty())
    }
}
