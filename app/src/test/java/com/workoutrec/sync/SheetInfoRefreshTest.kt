package com.workoutrec.sync

import com.workoutrec.fakes.FakeLogSyncStore
import com.workoutrec.fakes.FakeSheetsLogApi
import com.workoutrec.fakes.FakeSyncStatusStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

// Feature 004 contracts/sheets.md call 20: the sync knows the sheet IDs of drills, rec, money, balance
class SheetInfoRefreshTest {

    private val sheets = FakeSheetsLogApi(timeZone = "UTC", logSheetId = 3, drillsSheetId = 4, recSheetId = 5, moneySheetId = 6, balanceSheetId = 7)

    @Test
    fun `call 20 stores all sheet ids`() = runTest {
        val status = FakeSyncStatusStore()
        LogSync(FakeLogSyncStore(), sheets, status, spreadsheetId = { "s1" }).run()
        assertEquals(SheetInfoCache("s1", "UTC", 3, drillsSheetId = 4, recSheetId = 5, moneySheetId = 6, balanceSheetId = 7), status.status.value.sheet)
    }

    @Test
    fun `a cache from before feature 004 is read again`() = runTest {
        val status = FakeSyncStatusStore(SyncStatus(sheet = SheetInfoCache("s1", "UTC", 3)))
        val store = FakeLogSyncStore()
        LogSync(store, sheets, status, spreadsheetId = { "s1" }).run()
        assertEquals(listOf("info:s1", "read:s1"), sheets.calls)
        assertEquals(4, status.status.value.sheet?.drillsSheetId)
        // Same spreadsheet: nothing on the phone is dropped (research R13 does not apply).
        assertEquals(0, store.keptOnlyNew)
    }

    @Test
    fun `a complete cache is used as it is`() = runTest {
        val status = FakeSyncStatusStore(SyncStatus(sheet = SheetInfoCache("s1", "UTC", 3, drillsSheetId = 4)))
        LogSync(FakeLogSyncStore(), sheets, status, spreadsheetId = { "s1" }).run()
        assertEquals(listOf("read:s1"), sheets.calls)
    }

    // Feature 006 research R6: a cache from before the workout tab was known is read again once
    @Test
    fun `a version 1 cache is read again and stores the workout id`() = runTest {
        val status = FakeSyncStatusStore(SyncStatus(sheet = SheetInfoCache("s1", "UTC", 3, drillsSheetId = 4, version = 1)))
        val sheets = FakeSheetsLogApi(logSheetId = 3, drillsSheetId = 4, workoutSheetId = 9)
        LogSync(FakeLogSyncStore(), sheets, status, spreadsheetId = { "s1" }).run()
        assertEquals("info:s1", sheets.calls.first())
        assertEquals(SheetInfoCache("s1", "UTC", 3, drillsSheetId = 4, workoutSheetId = 9, version = 2), status.status.value.sheet)
    }

    @Test
    fun `a version 2 cache without a workout tab is used as it is`() = runTest {
        val status = FakeSyncStatusStore(SyncStatus(sheet = SheetInfoCache("s1", "UTC", 3, drillsSheetId = 4, version = 2)))
        val sheets = FakeSheetsLogApi(logSheetId = 3, drillsSheetId = 4)
        LogSync(FakeLogSyncStore(), sheets, status, spreadsheetId = { "s1" }).run()
        assertEquals(listOf("read:s1"), sheets.calls)
    }
}
