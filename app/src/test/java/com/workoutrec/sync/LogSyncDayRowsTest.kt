package com.workoutrec.sync

import com.workoutrec.fakes.FakeLogSyncStore
import com.workoutrec.fakes.FakeMonitoring
import com.workoutrec.fakes.FakeSheetsLogApi
import com.workoutrec.fakes.FakeSheetsLogApi.WorkoutCells
import com.workoutrec.fakes.FakeSyncStatusStore
import com.workoutrec.google.WorkoutAppend
import com.workoutrec.spreadsheet.ReferenceStructure
import com.workoutrec.workout.ChangeKind
import com.workoutrec.workout.DayStatus
import com.workoutrec.workout.PendingChange
import com.workoutrec.workout.PendingStatus
import com.workoutrec.workout.Reps
import com.workoutrec.workout.SetKey
import com.workoutrec.workout.Weight
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// Feature 007 research R3, contracts/sheets.md "Call 22: the day-rows step"; spec US2, US3 #2, FR-006, FR-007a
class LogSyncDayRowsTest {

    private val sheets = FakeSheetsLogApi(timeZone = "UTC", logSheetId = 3, workoutSheetId = 9)
    private val store = FakeLogSyncStore()
    private val monitoring = FakeMonitoring()
    private val sync = LogSync(store, sheets, FakeSyncStatusStore(), spreadsheetId = { "s1" }, monitoring = monitoring)

    private val oct20 = LocalDate.of(2026, 10, 20)
    private val oct21 = oct20.plusDays(1)
    private val oct22 = oct20.plusDays(2)

    /** Days from 1899-12-30 to 2026-10-20. */
    private val serial20 = 46315.0

    private fun serial(date: LocalDate) = serial20 + (date.toEpochDay() - oct20.toEpochDay())

    private fun key(date: LocalDate) = SetKey(date.atTime(18, 0).toEpochSecond(ZoneOffset.UTC), "squat", Weight.ofHundredths(2000), Reps(10))

    /** A set at 18:00 UTC on [date], already in the log tab. */
    private fun logSet(date: LocalDate) {
        sheets.log += FakeSheetsLogApi.Cells(serial(date) + 0.75, "squat", 20.0, 10)
    }

    private fun row(date: LocalDate, free: Boolean, duration: Any? = ReferenceStructure.DURATION_FORMULA) =
        WorkoutCells(serial(date), duration, if (free) 1.0 else 0.0)

    private val workoutWrite get() = sheets.writes.single().workout

    @Test
    fun `a new day's first set and its status go out in one write, the row with the duration formula`() = runTest {
        store.pending += PendingChange("s", ChangeKind.NEW, key(oct20), createdAt = 1)
        store.statuses += PendingStatus(oct20, DayStatus.PAID, lastSeen = null)
        sync.run()
        val write = sheets.writes.single()
        assertEquals(1, write.appends.size)
        assertEquals(listOf(WorkoutAppend(serial20, DayStatus.PAID)), write.workout?.appends)
        assertEquals(ReferenceStructure.DURATION_FORMULA, sheets.workout.single().duration)
        assertTrue(store.statuses.isEmpty())
    }

    @Test
    fun `a day typed only in the spreadsheet gets its row with the default`() = runTest {
        logSet(oct20)
        logSet(oct21)
        sheets.workout += row(oct20, free = false)
        sync.run()
        assertEquals(listOf(WorkoutAppend(serial(oct21), DayStatus.PAID)), workoutWrite?.appends)
    }

    @Test
    fun `the first day ever is Free`() = runTest {
        logSet(oct20)
        sync.run()
        assertEquals(listOf(WorkoutAppend(serial20, DayStatus.FREE)), workoutWrite?.appends)
    }

    @Test
    fun `a row added by hand for a day without sets is removed`() = runTest {
        logSet(oct20)
        sheets.workout += row(oct20, free = false)
        sheets.workout += row(oct21, free = false, duration = 30.0)
        sync.run()
        assertEquals(listOf(2), workoutWrite?.deletes)
        assertEquals(listOf(serial20), sheets.workout.map { it.serial })
    }

    @Test
    fun `deleting a day's last set in the app removes its row in the same write`() = runTest {
        logSet(oct20)
        logSet(oct21)
        sheets.workout += row(oct20, free = false)
        sheets.workout += row(oct21, free = false)
        store.pending += PendingChange("d", ChangeKind.DELETE, key(oct21), lastSeen = key(oct21), rowHint = 2, createdAt = 1)
        sync.run()
        val write = sheets.writes.single()
        assertEquals(listOf(2), write.deletes)
        assertEquals(listOf(2), write.workout?.deletes)
        assertEquals(listOf(serial20), sheets.workout.map { it.serial })
    }

    @Test
    fun `deleting every set in the app still removes the rows`() = runTest {
        // Analysis U1: the empty-log guard looks at the read before this run's writes.
        logSet(oct20)
        sheets.workout += row(oct20, free = false)
        store.pending += PendingChange("d", ChangeKind.DELETE, key(oct20), lastSeen = key(oct20), rowHint = 1, createdAt = 1)
        sync.run()
        assertEquals(listOf(1), workoutWrite?.deletes)
        assertTrue(sheets.workout.isEmpty())
    }

    @Test
    fun `an empty log read removes nothing`() = runTest {
        sheets.workout += row(oct20, free = false)
        sheets.workout += row(oct21, free = true)
        sync.run()
        assertTrue(sheets.writes.isEmpty())
        assertEquals(2, sheets.workout.size)
    }

    @Test
    fun `a day that has a row gets no second one`() = runTest {
        logSet(oct20)
        sheets.workout += row(oct20, free = false)
        sheets.workout += row(oct20, free = true)
        sync.run()
        assertTrue(sheets.writes.isEmpty())
    }

    @Test
    fun `days typed in the spreadsheet and rows without sets are fixed in one write`() = runTest {
        logSet(oct20)
        logSet(oct22)
        sheets.workout += row(oct20, free = true)
        sheets.workout += row(oct21, free = false)
        sync.run()
        assertEquals(listOf(2), workoutWrite?.deletes)
        // The default for Oct 22 copies Oct 20: the removed Oct 21 row no longer counts.
        assertEquals(listOf(WorkoutAppend(serial(oct22), DayStatus.FREE)), workoutWrite?.appends)
    }

    // US3 #2, T029: rows the old nightly script wrote stay when their day has sets.
    @Test
    fun `an old script row with a date-time and a number duration stays while its day has sets`() = runTest {
        logSet(oct20)
        sheets.workout += WorkoutCells(serial20 + 0.75, 45.0, 0.0)
        sheets.workout += WorkoutCells(serial(oct21) + 0.5, 30.0, 1.0)
        sync.run()
        assertEquals(listOf(2), workoutWrite?.deletes)
        assertEquals(listOf(serial20 + 0.75), sheets.workout.map { it.serial })
    }

    @Test
    fun `a sync with nothing else waiting no longer reports workout rows as not updated`() = runTest {
        logSet(oct20)
        logSet(oct21)
        sheets.workout += row(oct20, free = false)
        sheets.workout += row(oct21, free = false)
        store.pending += PendingChange("d", ChangeKind.DELETE, key(oct20), lastSeen = key(oct20), rowHint = 1, createdAt = 1)
        sync.run()
        assertTrue(store.notices.isEmpty())
    }
}
