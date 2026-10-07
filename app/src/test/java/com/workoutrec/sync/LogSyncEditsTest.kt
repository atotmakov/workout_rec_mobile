package com.workoutrec.sync

import com.workoutrec.fakes.FakeLogSyncStore
import com.workoutrec.fakes.FakeSheetsLogApi
import com.workoutrec.fakes.FakeSheetsLogApi.Cells
import com.workoutrec.fakes.FakeSyncStatusStore
import com.workoutrec.google.LogEdit
import com.workoutrec.workout.ChangeKind
import com.workoutrec.workout.PendingChange
import com.workoutrec.workout.Reps
import com.workoutrec.workout.SetKey
import com.workoutrec.workout.Weight
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// contracts/sheets-log.md steps 4-5 for edits and deletes; FR-013a, FR-015, SC-008; analysis fix U2
class LogSyncEditsTest {

    private val sheets = FakeSheetsLogApi(timeZone = "UTC")
    private val store = FakeLogSyncStore()
    private val status = FakeSyncStatusStore()
    private val now = LocalDateTime.parse("2026-01-12T20:00:00").toEpochSecond(ZoneOffset.UTC) * 1000
    private val sync = LogSync(store, sheets, status, spreadsheetId = { "s1" }, now = { now })

    private fun t(local: String) = LocalDateTime.parse(local).toEpochSecond(ZoneOffset.UTC)
    private fun serial(t: Long) = 25569.0 + t / 86400.0
    private fun key(time: String, kg: Int = 20, reps: Int = 10, exercise: String = "squat") =
        SetKey(t(time), exercise, Weight.ofHundredths(kg * 100L), Reps(reps))
    private fun cells(k: SetKey) = Cells(serial(k.time), k.exercise, k.weight.toDouble(), k.reps.value)

    // Rows 1-4 (sheet rows 2-5): a past day 2026-01-10 with three sets, and one set today.
    private val first = key("2026-01-10T18:00:00", reps = 10)
    private val middle = key("2026-01-10T18:05:00", reps = 9)
    private val last = key("2026-01-10T18:10:00", reps = 8)
    private val today = key("2026-01-12T18:00:00", reps = 7)

    private fun sheetHas(vararg keys: SetKey) {
        sheets.log.clear()
        keys.forEach { sheets.log += cells(it) }
    }

    private fun edit(id: String, target: SetKey, hint: Int, reps: Int, exercise: String = target.exercise) =
        PendingChange(id, ChangeKind.EDIT, target.copy(exercise = exercise, reps = Reps(reps)), lastSeen = target, rowHint = hint, createdAt = 1)

    private fun delete(id: String, target: SetKey, hint: Int) =
        PendingChange(id, ChangeKind.DELETE, target, lastSeen = target, rowHint = hint, createdAt = 1)

    @Test
    fun `an edit changes only exercise, weight and reps of its row`() = runTest {
        sheetHas(first, middle, last)
        store.pending += edit("e", middle, hint = 2, reps = 12, exercise = "bench")
        sync.run()
        assertEquals(listOf(LogEdit(2, "bench", 20.0, 12)), sheets.writes.single().edits)
        assertEquals(listOf(cells(first), cells(middle.copy(exercise = "bench", reps = Reps(12))), cells(last)), sheets.log)
        assertTrue(store.pending.isEmpty())
        assertTrue(store.notices.isEmpty())
    }

    @Test
    fun `a delete removes exactly its row`() = runTest {
        sheetHas(first, middle, last)
        store.pending += delete("d", middle, hint = 2)
        sync.run()
        assertEquals(listOf(2), sheets.writes.single().deletes)
        assertEquals(listOf(cells(first), cells(last)), sheets.log)
    }

    @Test
    fun `edits, deletes and new sets go in one write`() = runTest {
        sheetHas(first, middle, last, today)
        store.pending += delete("d", first, hint = 1)
        store.pending += edit("e", last, hint = 3, reps = 5)
        store.pending += PendingChange("n", ChangeKind.NEW, key("2026-01-12T18:05:00"), createdAt = 2)
        sync.run()
        val write = sheets.writes.single()
        assertEquals(listOf(1), write.deletes)
        assertEquals(listOf(3), write.edits.map { it.rowIndex })
        assertEquals(1, write.appends.size)
        assertEquals(listOf(cells(middle), cells(last.copy(reps = Reps(5))), cells(today), cells(key("2026-01-12T18:05:00"))), sheets.log)
    }

    @Test
    fun `rows inserted above in the web UI do not break the target`() = runTest {
        val inserted = key("2026-01-09T10:00:00", exercise = "plank")
        sheetHas(inserted, first, middle, last)
        store.pending += edit("e", middle, hint = 2, reps = 3)
        sync.run()
        assertEquals(listOf(3), sheets.writes.single().edits.map { it.rowIndex })
    }

    @Test
    fun `with identical rows the one closest to the remembered row is changed`() = runTest {
        sheetHas(first, middle, last, middle)
        store.pending += edit("e", middle, hint = 4, reps = 1)
        sync.run()
        assertEquals(listOf(4), sheets.writes.single().edits.map { it.rowIndex })
    }

    @Test
    fun `a row changed in the web UI keeps the web value and the user is told`() = runTest {
        sheetHas(first, middle.copy(reps = Reps(11)), last)
        store.pending += edit("e", middle, hint = 2, reps = 12)
        sync.run()
        assertTrue(sheets.writes.isEmpty())
        assertTrue(store.pending.isEmpty())
        assertEquals(listOf(SyncNotice(NoticeKind.CONFLICT_DROPPED, middle)), store.notices)
    }

    @Test
    fun `an edit already applied by an interrupted run is not reported as a conflict`() = runTest {
        sheetHas(first, middle.copy(reps = Reps(12)), last)
        store.pending += edit("e", middle, hint = 2, reps = 12)
        sync.run()
        assertTrue(sheets.writes.isEmpty())
        assertTrue(store.pending.isEmpty())
        assertTrue(store.notices.isEmpty())
    }

    @Test
    fun `deleting a row that is already gone is done, not a conflict`() = runTest {
        sheetHas(first, last)
        store.pending += delete("d", middle, hint = 2)
        sync.run()
        assertTrue(sheets.writes.isEmpty())
        assertTrue(store.pending.isEmpty())
        assertTrue(store.notices.isEmpty())
    }

    @Test
    fun `deleting the first or last set of a past day says its workout row is not updated`() = runTest {
        sheetHas(first, middle, last, today)
        store.pending += delete("d", first, hint = 1)
        sync.run()
        assertEquals(listOf(SyncNotice(NoticeKind.WORKOUT_ROW_NOT_UPDATED, first)), store.notices)
    }

    @Test
    fun `deleting a middle set, a set of today, or editing never touches the workout row`() = runTest {
        sheetHas(first, middle, last, today)
        store.pending += delete("d1", middle, hint = 2)
        store.pending += delete("d2", today, hint = 4)
        store.pending += edit("e", last, hint = 3, reps = 1)
        sync.run()
        assertTrue(store.notices.isEmpty())
    }
}
