package com.workoutrec.google

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

// contracts/sheets-log.md calls 20-22
class SheetsLogClientTest {

    private lateinit var server: MockWebServer
    private lateinit var client: SheetsLogClient

    @Before
    fun setUp() {
        server = MockWebServer().also { it.start() }
        client = SheetsLogClient(GoogleHttp(OkHttpClient(), { "tok" }, sleep = {}), baseUrl = server.url("/"))
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `call 20 reads the time zone and the log sheet id`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"properties":{"timeZone":"Europe/Moscow"},
                   "sheets":[{"properties":{"title":"log"}},{"properties":{"sheetId":7,"title":"drills"}}]}""",
            ),
        )
        // sheetId 0 is omitted by proto3 JSON.
        assertEquals(LogSheetInfo("Europe/Moscow", 0), client.logSheetInfo("id1"))
        val url = server.takeRequest().requestUrl!!
        assertEquals("/v4/spreadsheets/id1", url.encodedPath)
        assertEquals("properties.timeZone,sheets.properties(sheetId,title)", url.queryParameter("fields"))
    }

    @Test
    fun `call 20 fails with the missing tab`() = runTest {
        server.enqueue(MockResponse().setBody("""{"properties":{"timeZone":"UTC"},"sheets":[{"properties":{"sheetId":3,"title":"log"}}]}"""))
        try {
            client.logSheetInfo("id1")
            fail("expected MissingTabException")
        } catch (e: MissingTabException) {
            assertEquals("drills", e.tab)
        }
    }

    @Test
    fun `call 21 reads log and drills unformatted with serial dates`() = runTest {
        server.enqueue(MockResponse().setBody("""{"valueRanges":[{"range":"log!A2:D1000"},{"range":"drills!A2:B1000"}]}"""))
        assertEquals(LogAndDrills(emptyList(), emptyList()), client.readLogAndDrills("id1"))
        val url = server.takeRequest().requestUrl!!
        assertEquals("/v4/spreadsheets/id1/values:batchGet", url.encodedPath)
        assertEquals(listOf("log!A2:D", "drills!A2:B"), url.queryParameterValues("ranges"))
        assertEquals("UNFORMATTED_VALUE", url.queryParameter("valueRenderOption"))
        assertEquals("SERIAL_NUMBER", url.queryParameter("dateTimeRenderOption"))
        assertEquals("ROWS", url.queryParameter("majorDimension"))
    }

    @Test
    fun `call 21 keeps valid set rows with their sheet row index`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"valueRanges":[
                     {"values":[
                       [46034.77, "squat", 17.5, 15],
                       [46034.78, " bench ", "", 10],
                       [46034.79, "row", 20],
                       [],
                       ["", "plank", 0, 1],
                       [46034.8, "", 10, 10],
                       [46034.81, "curl", "heavy", 10],
                       [46034.82, "curl", 10, 10.5],
                       [46034.83, 21, 10, 3.0],
                       [46034.84, "dip"]
                     ]},
                     {"values":[["back", "pull-up"], ["", "plank"], ["legs"], [" chest ", " bench "]]}
                   ]}""",
            ),
        )
        val result = client.readLogAndDrills("id1")
        assertEquals(
            listOf(
                SheetLogRow(1, 46034.77, "squat", 17.5, 15),
                SheetLogRow(2, 46034.78, "bench", 0.0, 10),
                SheetLogRow(9, 46034.83, "21", 10.0, 3),
            ),
            result.log,
        )
        assertEquals(listOf(SheetDrill("back", "pull-up"), SheetDrill("", "plank"), SheetDrill("chest", "bench")), result.drills)
    }

    @Test
    fun `call 22 sends deletes and edits bottom-up, then one appendCells`() = runTest {
        server.enqueue(MockResponse().setBody("""{"replies":[]}"""))
        client.writeLog(
            spreadsheetId = "id1",
            logSheetId = 4,
            deletes = listOf(3, 10),
            edits = listOf(LogEdit(5, "squat", 20.0, 8), LogEdit(12, "bench", 17.5, 15)),
            appends = listOf(LogAppend(46034.5, "row", 30.0, 12), LogAppend(46034.6, "row", 30.5, 10)),
        )
        val request = server.takeRequest()
        assertEquals("/v4/spreadsheets/id1:batchUpdate", request.requestUrl!!.encodedPath)
        val requests = GoogleJson.parseToJsonElement(request.body.readUtf8()).jsonObject["requests"]!!.jsonArray.map { it.jsonObject }
        assertEquals(listOf("updateCells", "deleteDimension", "updateCells", "deleteDimension", "appendCells"), requests.map { it.keys.single() })

        val edit12 = requests[0]["updateCells"]!!.jsonObject
        assertEquals(4, edit12["start"]!!.jsonObject["sheetId"]!!.jsonPrimitive.int)
        assertEquals(12, edit12["start"]!!.jsonObject["rowIndex"]!!.jsonPrimitive.int)
        assertEquals(1, edit12["start"]!!.jsonObject["columnIndex"]!!.jsonPrimitive.int)
        assertEquals("userEnteredValue", edit12["fields"]!!.jsonPrimitive.content)
        val editCells = edit12["rows"]!!.jsonArray.single().jsonObject["values"]!!.jsonArray.map { it.jsonObject["userEnteredValue"]!!.jsonObject }
        assertEquals("bench", editCells[0]["stringValue"]!!.jsonPrimitive.content)
        assertEquals(17.5, editCells[1]["numberValue"]!!.jsonPrimitive.double, 0.0)
        assertEquals(15.0, editCells[2]["numberValue"]!!.jsonPrimitive.double, 0.0)

        val delete10 = requests[1]["deleteDimension"]!!.jsonObject["range"]!!.jsonObject
        assertEquals(listOf("4", "ROWS", "10", "11"), listOf("sheetId", "dimension", "startIndex", "endIndex").map { delete10[it]!!.jsonPrimitive.content })
        assertEquals(5, requests[2]["updateCells"]!!.jsonObject["start"]!!.jsonObject["rowIndex"]!!.jsonPrimitive.int)
        assertEquals(3, requests[3]["deleteDimension"]!!.jsonObject["range"]!!.jsonObject["startIndex"]!!.jsonPrimitive.int)

        val append = requests[4]["appendCells"]!!.jsonObject
        assertEquals(4, append["sheetId"]!!.jsonPrimitive.int)
        assertEquals("userEnteredValue", append["fields"]!!.jsonPrimitive.content)
        val rows = append["rows"]!!.jsonArray.map { r -> r.jsonObject["values"]!!.jsonArray.map { it.jsonObject["userEnteredValue"]!!.jsonObject } }
        assertEquals(2, rows.size)
        assertEquals(46034.5, rows[0][0]["numberValue"]!!.jsonPrimitive.double, 0.0)
        assertEquals("row", rows[0][1]["stringValue"]!!.jsonPrimitive.content)
        assertEquals(30.0, rows[0][2]["numberValue"]!!.jsonPrimitive.double, 0.0)
        assertEquals(12.0, rows[0][3]["numberValue"]!!.jsonPrimitive.double, 0.0)
        assertEquals(30.5, rows[1][2]["numberValue"]!!.jsonPrimitive.double, 0.0)
    }

    @Test
    fun `call 22 omits empty parts and is skipped when there is nothing`() = runTest {
        server.enqueue(MockResponse().setBody("""{"replies":[]}"""))
        client.writeLog("id1", 0, emptyList(), emptyList(), listOf(LogAppend(1.0, "a", 1.0, 1)))
        val requests = GoogleJson.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject["requests"]!!.jsonArray
        assertEquals(listOf("appendCells"), requests.map { (it as JsonObject).keys.single() })

        client.writeLog("id1", 0, emptyList(), emptyList(), emptyList())
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `errors use the feature 001 mapping`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404).setBody("""{"error":{"code":404,"message":"not found"}}"""))
        try {
            client.readLogAndDrills("gone")
            fail("expected NotFound")
        } catch (e: ApiError) {
            assertTrue(e is ApiError.NotFound)
        }
    }
}
