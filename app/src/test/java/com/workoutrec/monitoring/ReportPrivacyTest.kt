package com.workoutrec.monitoring

import com.workoutrec.auth.AccountRepository
import com.workoutrec.auth.AuthOutcome
import com.workoutrec.data.SelectedAccount
import com.workoutrec.fakes.FakeApiAuthorizer
import com.workoutrec.fakes.FakeLogSyncStore
import com.workoutrec.fakes.FakeMonitoring
import com.workoutrec.fakes.FakeScriptInstaller
import com.workoutrec.fakes.FakeSettingsStore
import com.workoutrec.fakes.FakeSyncStatusStore
import com.workoutrec.google.DriveClient
import com.workoutrec.google.GoogleHttp
import com.workoutrec.google.MoneyClient
import com.workoutrec.google.SheetsClient
import com.workoutrec.google.SheetsLogClient
import com.workoutrec.money.MoneyError
import com.workoutrec.money.MoneyRefresher
import com.workoutrec.money.MoneyStore
import com.workoutrec.money.MoneySummary
import com.workoutrec.setup.SpreadsheetSetupFlow
import com.workoutrec.spreadsheet.SpreadsheetSetupService
import com.workoutrec.sync.LogSync
import com.workoutrec.sync.SheetInfoCache
import com.workoutrec.workout.ChangeKind
import com.workoutrec.workout.PaymentRow
import com.workoutrec.workout.PendingChange
import com.workoutrec.workout.Reps
import com.workoutrec.workout.SetKey
import com.workoutrec.workout.Weight
import java.time.Instant
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

// Feature 005 SC-003, FR-007: no report carries workout or money data, the email or a token,
// even when Google's error reply repeats them.
class ReportPrivacyTest {

    private val secrets = listOf("Bench press", "1234.50", "tester@example.com", "ya29.test-token")
    private val jsonReply = """{"error":{"code":400,"message":"Invalid value 'Bench press' 1234.50 for tester@example.com ya29.test-token","status":"INVALID_ARGUMENT"}}"""
    private val rawReply = "<html>Bench press 1234.50 tester@example.com ya29.test-token</html>"

    private val monitoring = FakeMonitoring()
    private lateinit var server: MockWebServer
    private lateinit var http: GoogleHttp

    @Before
    fun setUp() {
        server = MockWebServer().also { it.start() }
        http = GoogleHttp(OkHttpClient(), { "ya29.test-token" }, sleep = {})
    }

    @After
    fun tearDown() = server.shutdown()

    private fun replyWith(code: Int, body: String) = server.enqueue(MockResponse().setResponseCode(code).setBody(body))

    private fun assertNothingPrivate() {
        assertFalse("expected at least one report", monitoring.nonFatals.isEmpty())
        monitoring.nonFatals.forEach { report ->
            val texts = listOfNotNull(report.details) + generateSequence(report.error) { it.cause }.mapNotNull { it.message }
            texts.forEach { text -> secrets.forEach { secret -> assertFalse("${report.issue} sent \"$text\"", secret in text) } }
        }
    }

    @Test
    fun `sync reports keep Google's error text out`() = runTest {
        val store = FakeLogSyncStore().apply {
            pending += PendingChange("a", ChangeKind.NEW, SetKey(1_700_000_000, "Bench press", Weight.ofHundredths(123450), Reps(10)), createdAt = 1)
        }
        val sync = LogSync(store, SheetsLogClient(http, baseUrl = server.url("/")), FakeSyncStatusStore(), spreadsheetId = { "s1" }, monitoring = monitoring)
        replyWith(400, jsonReply)
        sync.run()
        replyWith(403, rawReply)
        sync.run()
        assertEquals(2, monitoring.nonFatals.size)
        assertNothingPrivate()
    }

    @Test
    fun `money refresh reports keep Google's error text out`() = runTest {
        val store = object : MoneyStore {
            override suspend fun moneySummaryNow(): MoneySummary? = null
            override suspend fun replaceMoney(rows: List<PaymentRow>, summary: MoneySummary) = Unit
            override suspend fun moneyFailed(error: MoneyError) = Unit
        }
        val sheet = SheetInfoCache("s1", "UTC", 0, drillsSheetId = 1, moneySheetId = 2, balanceSheetId = 3)
        val refresher = MoneyRefresher(MoneyClient(http, baseUrl = server.url("/")), store, sheet = { sheet }, monitoring = monitoring)
        replyWith(400, jsonReply)
        refresher.refresh()
        replyWith(400, rawReply)
        refresher.refresh()
        assertEquals(2, monitoring.nonFatals.size)
        assertNothingPrivate()
    }

    @Test
    fun `setup reports keep Google's error text out`() = runTest {
        val service = SpreadsheetSetupService(DriveClient(http, baseUrl = server.url("/")), SheetsClient(http, baseUrl = server.url("/")), monitoring = monitoring)
        val account = SelectedAccount("tester@example.com", "Tester", null)
        val flow = SpreadsheetSetupFlow(service, FakeScriptInstaller(), FakeSettingsStore(account), timeZone = { "UTC" }, now = { Instant.EPOCH }, monitoring = monitoring)
        replyWith(400, jsonReply)
        flow.run(account)
        assertEquals(1, monitoring.nonFatals.size)
        assertNothingPrivate()
    }

    @Test
    fun `token reports carry neither the email nor a token`() = runTest {
        val account = SelectedAccount("tester@example.com", "Tester", null)
        listOf(AuthOutcome.Denied, AuthOutcome.Failed(10), AuthOutcome.Unavailable).forEach { outcome ->
            val repo = AccountRepository(FakeApiAuthorizer(outcome), FakeSettingsStore(account), monitoring = monitoring)
            runCatching { repo.accessToken(forceRefresh = false) }
        }
        assertEquals(3, monitoring.nonFatals.size)
        assertNothingPrivate()
    }
}
