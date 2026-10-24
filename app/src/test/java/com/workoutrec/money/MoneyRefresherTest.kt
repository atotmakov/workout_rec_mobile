package com.workoutrec.money

import com.workoutrec.automation.AutomationStatus
import com.workoutrec.fakes.FakeMonitoring
import com.workoutrec.google.ApiError
import com.workoutrec.google.MissingTabException
import com.workoutrec.google.MoneyApi
import com.workoutrec.google.MoneyRead
import com.workoutrec.google.SheetMoneyRow
import com.workoutrec.monitoring.NonFatalIssue
import com.workoutrec.sync.SheetInfoCache
import com.workoutrec.workout.PaymentRow
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

// Feature 004 FR-010, research R6: when the Money tab downloads, and what a failure keeps
class MoneyRefresherTest {

    private class FakeMoneyApi : MoneyApi {
        var reads = 0
        var next: () -> MoneyRead = {
            MoneyRead(
                payments = listOf(SheetMoneyRow(1, 46315.0, 8.0, 8000.0), SheetMoneyRow(2, 46316.0, null, 4000.5)),
                balance = 11.0,
                metadata = mapOf(
                    "workout_rec.script_id" to "s",
                    "workout_rec.automation_enabled_at" to "2026-10-01T00:00:00Z",
                    "workout_rec.last_daily_run" to "2026-10-21T03:00:00Z",
                ),
            )
        }
        var asked: Pair<Boolean, Boolean>? = null

        override suspend fun readMoney(spreadsheetId: String, hasMoney: Boolean, hasBalance: Boolean): MoneyRead {
            reads++
            asked = hasMoney to hasBalance
            return next()
        }
    }

    private class FakeMoneyStore : MoneyStore {
        var rows: List<PaymentRow> = emptyList()
        var summary: MoneySummary? = null
        override suspend fun moneySummaryNow() = summary
        override suspend fun replaceMoney(rows: List<PaymentRow>, summary: MoneySummary) {
            this.rows = rows
            this.summary = summary
        }
        override suspend fun moneyFailed(error: MoneyError) {
            summary = (summary ?: MoneySummary(null, null, AutomationStatus.ScriptMissing, downloadedAt = 0, error = error)).copy(error = error)
        }
    }

    private val api = FakeMoneyApi()
    private val store = FakeMoneyStore()
    /** 2026-10-22T10:00Z */
    private var now = 1_792_663_200_000L
    private var sheet: SheetInfoCache? = SheetInfoCache("s1", "UTC", 0, drillsSheetId = 1, moneySheetId = 2, balanceSheetId = 3)
    private val monitoring = FakeMonitoring()
    private val refresher = MoneyRefresher(api, store, sheet = { sheet }, now = { now }, monitoring = monitoring)

    private fun reports() = monitoring.nonFatals.map { it.issue to it.details }

    @Test
    fun `opening Money downloads and stores payments, balance and the automation state`() = runTest {
        refresher.onOpen()
        assertEquals(listOf(PaymentRow(1, LocalDate.of(2026, 10, 20), 8.0, 800_000), PaymentRow(2, LocalDate.of(2026, 10, 21), null, 400_050)), store.rows)
        assertEquals(MoneySummary(11.0, 1_792_551_600_000L, AutomationStatus.On, downloadedAt = now, error = MoneyError.NONE), store.summary)
        assertEquals(true to true, api.asked)
    }

    @Test
    fun `opening again within 5 minutes uses what is stored`() = runTest {
        refresher.onOpen()
        now += 4 * 60_000
        refresher.onOpen()
        assertEquals(1, api.reads)
        now += 2 * 60_000
        refresher.onOpen()
        assertEquals(2, api.reads)
    }

    @Test
    fun `pulling down always downloads`() = runTest {
        refresher.onOpen()
        refresher.refresh()
        assertEquals(2, api.reads)
    }

    @Test
    fun `app start downloads once per start`() = runTest {
        refresher.onAppStart()
        now += 10 * 60_000
        refresher.onAppStart()
        assertEquals(1, api.reads)
    }

    @Test
    fun `a failure keeps the stored values and is shown`() = runTest {
        refresher.onOpen()
        val stored = store.rows
        api.next = { throw ApiError.Offline }
        refresher.refresh()
        assertEquals(stored, store.rows)
        assertEquals(MoneyError.NETWORK, store.summary?.error)
        assertEquals(11.0, store.summary?.balance)
        // The last attempt failed: opening tries again at once.
        refresher.onOpen()
        assertEquals(3, api.reads)
    }

    @Test
    fun `a missing tab is shown as such`() = runTest {
        api.next = { throw MissingTabException("money") }
        refresher.onOpen()
        assertEquals(MoneyError.MISSING_TAB, store.summary?.error)
    }

    @Test
    fun `without sheet info yet nothing is read`() = runTest {
        sheet = null
        refresher.onOpen()
        assertEquals(0, api.reads)
        assertEquals(MoneyError.OTHER, store.summary?.error)
    }

    // Feature 005 US1, contracts/issues.md
    @Test
    fun `a missing tab is reported`() = runTest {
        api.next = { throw MissingTabException("money") }
        refresher.refresh()
        assertEquals(listOf(NonFatalIssue.MONEY_REFRESH_FAILED to "operation=money-refresh; reason=missing-tab"), reports())
    }

    @Test
    fun `another Google error is reported as other`() = runTest {
        api.next = { throw ApiError.Unexpected(500, "boom") }
        refresher.refresh()
        assertEquals(MoneyError.OTHER, store.summary?.error)
        assertEquals(listOf(NonFatalIssue.MONEY_REFRESH_FAILED to "operation=money-refresh; reason=other"), reports())
    }

    @Test
    fun `an unreadable last daily run is kept empty and reported once`() = runTest {
        api.next = {
            MoneyRead(emptyList(), 3.0, mapOf("workout_rec.script_id" to "s", "workout_rec.automation_enabled_at" to "2026-10-21T00:00:00Z", "workout_rec.last_daily_run" to "yesterday"))
        }
        refresher.refresh()
        assertEquals(null, store.summary?.lastDailyRun)
        assertEquals(listOf(NonFatalIssue.BAD_SHEET_DATA to "value=automation-timestamp"), reports())
    }

    @Test
    fun `no connection is reported as info`() = runTest {
        api.next = { throw ApiError.Offline }
        refresher.refresh()
        assertEquals(MoneyError.NETWORK, store.summary?.error)
        assertEquals(listOf(NonFatalIssue.NO_CONNECTION to "operation=money-refresh"), reports())
    }

    @Test
    fun `no sheet info, a cancelled refresh and a success send nothing`() = runTest {
        refresher.refresh()
        sheet = null
        refresher.refresh()
        sheet = SheetInfoCache("s1", "UTC", 0, drillsSheetId = 1, moneySheetId = 2, balanceSheetId = 3)
        api.next = { throw CancellationException("left the tab") }
        runCatching { refresher.refresh() }
        assertEquals(emptyList<Any>(), reports())
    }

    // Feature 007 research R9: a converted spreadsheet calculates the balance itself.
    @Test
    fun `structure version 3 or later marks the balance as calculated`() = runTest {
        listOf("3" to true, "4" to true, "2" to false, null to false).forEach { (version, calculated) ->
            api.next = { MoneyRead(emptyList(), 3.0, listOfNotNull(version?.let { "workout_rec.structure_version" to it }).toMap()) }
            refresher.refresh()
            assertEquals("version $version", calculated, store.summary?.calculated)
        }
    }

    // Analysis C1: a renamed tab breaks the formula; the balance is unknown and reported without values.
    @Test
    fun `a formula error in the balance is shown as unknown and reported`() = runTest {
        api.next = { MoneyRead(emptyList(), balance = null, metadata = emptyMap(), balanceError = true) }
        refresher.refresh()
        assertEquals(null, store.summary?.balance)
        assertEquals(MoneyError.NONE, store.summary?.error)
        assertEquals(listOf(NonFatalIssue.BAD_SHEET_DATA to "value=balance-formula"), reports())
    }
}
