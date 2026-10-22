package com.workoutrec.sync

import com.workoutrec.fakes.FakeLogSyncStore
import com.workoutrec.fakes.FakeSheetsLogApi
import com.workoutrec.fakes.FakeSyncStatusStore
import com.workoutrec.google.MoneyAppend
import com.workoutrec.workout.ChangeKind
import com.workoutrec.workout.PendingChange
import com.workoutrec.workout.PendingPayment
import com.workoutrec.workout.Reps
import com.workoutrec.workout.SetKey
import com.workoutrec.workout.Weight
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// Feature 004 FR-009b, research R5 (payments), contracts/sheets.md calls 21 and 22
class LogSyncPaymentsTest {

    private val sheets = FakeSheetsLogApi(timeZone = "UTC", logSheetId = 3, moneySheetId = 9)
    private val store = FakeLogSyncStore()
    private var refreshes = 0
    private val sync = LogSync(store, sheets, FakeSyncStatusStore(), spreadsheetId = { "s1" }, onPaymentsWritten = { refreshes++ })

    private val oct20 = LocalDate.of(2026, 10, 20)
    /** Days from 1899-12-30 to 2026-10-20. */
    private val serial = 46315.0

    private fun payment(id: String, workouts: Int = 8, sum: Long = 800_000, createdAt: Long = 1) = PendingPayment(id, oct20, workouts, sum, createdAt)

    @Test
    fun `the money tab is read only when payments wait`() = runTest {
        sync.run()
        assertEquals(0, sheets.moneyReads)
        store.payments += payment("p")
        sync.run()
        assertEquals(1, sheets.moneyReads)
    }

    @Test
    fun `waiting payments are appended in the order they were added`() = runTest {
        store.payments += payment("b", workouts = 4, sum = 450_050, createdAt = 2)
        store.payments += payment("a", createdAt = 1)
        sync.run()
        assertEquals(listOf(MoneyAppend(serial, 8, 8000.0), MoneyAppend(serial, 4, 4500.5)), sheets.writes.single().money?.appends)
        assertEquals(9, sheets.writes.single().money?.moneySheetId)
        assertTrue(store.payments.isEmpty())
        assertEquals(1, refreshes)
    }

    @Test
    fun `a payment already in the money tab is not written again`() = runTest {
        sheets.money += FakeSheetsLogApi.MoneyCells(serial, 8.0, 8000.0)
        store.payments += payment("a")
        store.payments += payment("b", createdAt = 2)
        sync.run()
        // Two identical payments, one already there: one more row.
        assertEquals(1, sheets.writes.single().money?.appends?.size)
        assertEquals(2, sheets.money.size)
        assertTrue(store.payments.isEmpty())
    }

    @Test
    fun `without a money tab payments wait and sets still sync`() = runTest {
        sheets.moneySheetId = null
        store.payments += payment("p")
        store.pending += PendingChange("s", ChangeKind.NEW, SetKey(1_700_000_000, "squat", Weight.ofHundredths(2000), Reps(10)), createdAt = 1)
        sync.run()
        assertEquals(0, sheets.moneyReads)
        assertEquals(null, sheets.writes.single().money)
        assertEquals(listOf("p"), store.payments.map { it.id })
        assertTrue(store.pending.isEmpty())
        assertEquals(0, refreshes)
    }

    @Test
    fun `nothing written, no money refresh`() = runTest {
        sheets.money += FakeSheetsLogApi.MoneyCells(serial, 8.0, 8000.0)
        store.payments += payment("a")
        sync.run()
        assertTrue(sheets.writes.isEmpty())
        assertEquals(0, refreshes)
    }
}
