package com.workoutrec.google

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

// Feature 004 contracts/sheets.md call 23
class MoneyClientTest {

    private lateinit var server: MockWebServer
    private lateinit var client: MoneyClient

    @Before
    fun setUp() {
        server = MockWebServer().also { it.start() }
        client = MoneyClient(GoogleHttp(OkHttpClient(), { "tok" }, sleep = {}), baseUrl = server.url("/"))
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `reads payments, the balance and the metadata in one request`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"developerMetadata":[
                     {"metadataKey":"workout_rec.last_daily_run","metadataValue":"2026-10-21T03:00:00Z"},
                     {"metadataKey":"workout_rec.script_id","metadataValue":"abc"}],
                   "sheets":[
                     {"properties":{"title":"money"},"data":[{"rowData":[
                       {"values":[{"effectiveValue":{"numberValue":46000}},{"effectiveValue":{"numberValue":8}},{"effectiveValue":{"numberValue":8000}}]},
                       {},
                       {"values":[{"effectiveValue":{"stringValue":"soon"}},{"effectiveValue":{"numberValue":4}}]},
                       {"values":[{"effectiveValue":{"numberValue":46030}},{"effectiveValue":{"stringValue":"x"}},{"effectiveValue":{"numberValue":12000.5}}]}
                     ]}]},
                     {"properties":{"title":"balance"},"data":[{"rowData":[{"values":[{"effectiveValue":{"numberValue":11}}]}]}]}
                   ]}""",
            ),
        )
        val read = client.readMoney("id1", hasMoney = true, hasBalance = true)
        assertEquals(listOf(SheetMoneyRow(1, 46000.0, 8.0, 8000.0), SheetMoneyRow(4, 46030.0, null, 12000.5)), read.payments)
        assertEquals(11.0, read.balance)
        assertEquals(mapOf("workout_rec.last_daily_run" to "2026-10-21T03:00:00Z", "workout_rec.script_id" to "abc"), read.metadata)

        val url = server.takeRequest().requestUrl!!
        assertEquals("/v4/spreadsheets/id1", url.encodedPath)
        assertEquals(listOf("money!A2:C", "balance!A1"), url.queryParameterValues("ranges"))
        assertEquals("true", url.queryParameter("includeGridData"))
        assertEquals(
            "developerMetadata(metadataKey,metadataValue),sheets(properties(title),data(rowData(values(effectiveValue))))",
            url.queryParameter("fields"),
        )
    }

    @Test
    fun `an empty balance cell is null`() = runTest {
        server.enqueue(MockResponse().setBody("""{"sheets":[{"properties":{"title":"money"},"data":[{}]},{"properties":{"title":"balance"},"data":[{"rowData":[{}]}]}]}"""))
        val read = client.readMoney("id1", hasMoney = true, hasBalance = true)
        assertEquals(null, read.balance)
        assertEquals(emptyList<SheetMoneyRow>(), read.payments)
        assertEquals(emptyMap<String, String>(), read.metadata)
    }

    @Test
    fun `a missing tab is reported without a request`() = runTest {
        try {
            client.readMoney("id1", hasMoney = true, hasBalance = false)
            fail("expected MissingTabException")
        } catch (e: MissingTabException) {
            assertEquals("balance", e.tab)
        }
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `errors use the feature 001 mapping`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404).setBody("""{"error":{"code":404,"message":"not found"}}"""))
        try {
            client.readMoney("gone", hasMoney = true, hasBalance = true)
            fail("expected ApiError.NotFound")
        } catch (e: ApiError.NotFound) {
            // expected
        }
    }
}
