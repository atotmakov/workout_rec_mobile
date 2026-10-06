package com.workoutrec.sync

import com.workoutrec.fakes.FakeLogSyncStore
import com.workoutrec.fakes.FakeSheetsLogApi
import com.workoutrec.fakes.FakeSyncStatusStore
import com.workoutrec.workout.ChangeKind
import com.workoutrec.workout.PendingChange
import com.workoutrec.workout.Reps
import com.workoutrec.workout.SetKey
import com.workoutrec.workout.Weight
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

// research R13: the bound spreadsheet changed (rewrite or re-creation by feature 001)
class SpreadsheetChangeTest {

    private val sheets = FakeSheetsLogApi(logSheetId = 5)
    private val store = FakeLogSyncStore()
    private val status = FakeSyncStatusStore(SyncStatus(sheet = SheetInfoCache("old", "UTC", 0)))
    private var spreadsheetId = "new"
    private val sync = LogSync(store, sheets, status, spreadsheetId = { spreadsheetId })

    private fun key(t: Long) = SetKey(t, "squat", Weight.ofHundredths(2000), Reps(10))

    @Test
    fun `new sets are kept and written to the new spreadsheet, the rest is dropped`() = runTest {
        store.pending += PendingChange("n", ChangeKind.NEW, key(1_700_000_000), createdAt = 1)
        store.pending += PendingChange("e", ChangeKind.EDIT, key(1_600_000_000), lastSeen = key(1_600_000_000), rowHint = 1, createdAt = 2)
        sync.run()
        assertEquals(1, store.keptOnlyNew)
        assertEquals(listOf("info:new", "read:new", "write:new", "read:new"), sheets.calls)
        assertEquals(1, sheets.writes.single().appends.size)
        assertEquals(SheetInfoCache("new", "UTC", 5), status.status.value.sheet)
    }

    @Test
    fun `the same spreadsheet keeps everything`() = runTest {
        spreadsheetId = "old"
        sync.run()
        assertEquals(0, store.keptOnlyNew)
    }

    @Test
    fun `the first run after install is not a change`() = runTest {
        status.setSheet(null)
        sync.run()
        assertEquals(0, store.keptOnlyNew)
    }
}
