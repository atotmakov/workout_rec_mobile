package com.workoutrec.sync

import com.workoutrec.fakes.FakeLogSyncStore
import com.workoutrec.fakes.FakeSheetsLogApi
import com.workoutrec.fakes.FakeSyncStatusStore
import com.workoutrec.google.SheetDrill
import com.workoutrec.workout.ChangeKind
import com.workoutrec.workout.Exercise
import com.workoutrec.workout.PendingChange
import com.workoutrec.workout.Reps
import com.workoutrec.workout.SetKey
import com.workoutrec.workout.Weight
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// contracts/sheets-log.md "Sync algorithm" for new sets; FR-007, FR-009
class LogSyncNewSetsTest {

    private val sheets = FakeSheetsLogApi(timeZone = "UTC", logSheetId = 3, drills = listOf(SheetDrill("legs", "squat")))
    private val store = FakeLogSyncStore()
    private val status = FakeSyncStatusStore()
    private var spreadsheetId: String? = "s1"
    private val sync = LogSync(store, sheets, status, spreadsheetId = { spreadsheetId }, now = { 99L })

    /** UTC epoch seconds → sheet serial. */
    private fun serial(t: Long) = 25569.0 + t / 86400.0

    private fun key(t: Long, exercise: String = "squat", kg: Int = 20, reps: Int = 10) =
        SetKey(t, exercise, Weight.ofHundredths(kg * 100L), Reps(reps))

    private fun new(id: String, key: SetKey, createdAt: Long, draftId: String? = null) =
        PendingChange(id, ChangeKind.NEW, key, createdAt = createdAt, draftId = draftId)

    @Test
    fun `new sets are appended in logging order in one write`() = runTest {
        store.pending += new("b", key(1_700_000_100, reps = 8), createdAt = 2)
        store.pending += new("a", key(1_700_000_000), createdAt = 1)
        sync.run()
        val write = sheets.writes.single()
        assertEquals(emptyList<Int>(), write.deletes)
        assertEquals(listOf(10, 8), write.appends.map { it.reps })
        assertEquals(serial(1_700_000_000), write.appends[0].serial, 1e-9)
        assertEquals(listOf("squat", "squat"), write.appends.map { it.exercise })
        assertEquals(20.0, write.appends[0].weight, 0.0)
        assertEquals(1, sheets.calls.count { it.startsWith("write") })
    }

    @Test
    fun `after a write the caches come from a fresh read and the sets are no longer pending`() = runTest {
        store.pending += new("a", key(1_700_000_000), createdAt = 1)
        assertEquals(SyncOutcome.Synced(wrote = true), sync.run())
        assertEquals(listOf("info:s1", "read:s1", "write:s1", "read:s1"), sheets.calls)
        assertTrue(store.pending.isEmpty())
        assertEquals(listOf(key(1_700_000_000)), store.rows.map { it.key })
        assertEquals(listOf(1), store.rows.map { it.rowIndex })
        assertEquals(listOf(Exercise("squat", "legs")), store.exercises)
        assertEquals(99L, status.status.value.lastSuccessAt)
        assertEquals(SyncPhase.Idle, status.status.value.phase)
    }

    @Test
    fun `a set already in the sheet is not written again`() = runTest {
        // A previous run wrote it but its response was lost.
        sheets.log += FakeSheetsLogApi.Cells(serial(1_700_000_000), "squat", 20.0, 10)
        store.pending += new("a", key(1_700_000_000), createdAt = 1)
        store.pending += new("b", key(1_700_000_100), createdAt = 2)
        sync.run()
        assertEquals(listOf(1_700_000_100L), sheets.writes.single().appends.map { Math.round((it.serial - 25569.0) * 86400.0) })
        assertEquals(2, sheets.log.size)
        assertTrue(store.pending.isEmpty())
    }

    @Test
    fun `nothing pending means one read and no write`() = runTest {
        sheets.log += FakeSheetsLogApi.Cells(serial(1_700_000_000), "squat", 20.0, 10)
        assertEquals(SyncOutcome.Synced(wrote = false), sync.run())
        assertEquals(listOf("info:s1", "read:s1"), sheets.calls)
        assertEquals(1, store.rows.size)
    }

    @Test
    fun `sheet info is read once per spreadsheet`() = runTest {
        sync.run()
        sync.run()
        assertEquals(1, sheets.calls.count { it.startsWith("info") })
        assertEquals(SheetInfoCache("s1", "UTC", 3), status.status.value.sheet)
    }

    @Test
    fun `draft sets of a past workout are never written`() = runTest {
        store.pending += new("d", key(1_700_000_000), createdAt = 1, draftId = "draft-1")
        sync.run()
        assertTrue(sheets.writes.isEmpty())
        assertEquals(listOf("d"), store.pending.map { it.id })
    }

    @Test
    fun `an unsynced set keeps its exercise name after the exercise was removed from drills`() = runTest {
        sheets.drills = emptyList()
        store.pending += new("a", key(1_700_000_000, exercise = "old name"), createdAt = 1)
        sync.run()
        assertEquals("old name", sheets.writes.single().appends.single().exercise)
    }

    @Test
    fun `no spreadsheet bound means nothing happens`() = runTest {
        spreadsheetId = null
        assertEquals(SyncOutcome.NoSpreadsheet, sync.run())
        assertTrue(sheets.calls.isEmpty())
    }
}
