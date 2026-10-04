package com.workoutrec.google

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

// contracts/google-apis.md calls 4, 4b, 5a, 5b, 6
class SheetsClientTest {

    private lateinit var server: MockWebServer
    private lateinit var sheets: SheetsClient

    @Before
    fun setUp() {
        server = MockWebServer().also { it.start() }
        sheets = SheetsClient(GoogleHttp(OkHttpClient(), { "tok" }, sleep = {}), baseUrl = server.url("/"))
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `call 4 posts the spreadsheet and returns its id`() = runTest {
        server.enqueue(MockResponse().setBody("""{"spreadsheetId":"new-id","properties":{"title":"workout_rec_database_"}}"""))
        val body = buildJsonObject { put("x", 1) }
        assertEquals("new-id", sheets.create(body))
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/v4/spreadsheets", request.requestUrl!!.encodedPath)
        assertEquals("""{"x":1}""", request.body.readUtf8())
    }

    @Test
    fun `call 4b sends requests in one batchUpdate`() = runTest {
        server.enqueue(MockResponse().setBody("""{"spreadsheetId":"id1","replies":[]}"""))
        sheets.batchUpdate("id1", JsonArray(listOf(buildJsonObject { put("a", 1) }, buildJsonObject { put("b", 2) })))
        val request = server.takeRequest()
        assertEquals("/v4/spreadsheets/id1:batchUpdate", request.requestUrl!!.encodedPath)
        val requests = GoogleJson.parseToJsonElement(request.body.readUtf8()).jsonObject["requests"]!!.jsonArray
        assertEquals(2, requests.size)
    }

    @Test
    fun `call 5a reads tab titles, tables and metadata without ranges`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"sheets":[{"properties":{"sheetId":0,"title":"log"},
                               "tables":[{"name":"log","range":{"sheetId":0,"startRowIndex":0,"endRowIndex":1000,"startColumnIndex":0,"endColumnIndex":4},
                                          "columnProperties":[{"columnName":"Date","columnType":"DATE"},
                                                              {"columnIndex":1,"columnName":"Drill"},
                                                              {"columnIndex":2,"columnName":"W","columnType":"DOUBLE"}]}]},
                              {"properties":{"sheetId":5,"title":"pay"}}],
                   "developerMetadata":[{"metadataId":11,"metadataKey":"workout_rec.script_id","metadataValue":"s1"},
                                        {"metadataId":12,"metadataKey":"workout_rec.structure_version","metadataValue":"1"}]}""",
            ),
        )
        val result = sheets.readTabsAndMetadata("id1")
        assertEquals(listOf("log", "pay"), result.titles)
        assertEquals(mapOf("workout_rec.script_id" to "s1", "workout_rec.structure_version" to "1"), result.metadata)
        assertEquals(mapOf("workout_rec.script_id" to 11, "workout_rec.structure_version" to 12), result.metadataIds)
        // Omitted columnIndex means 0 and omitted columnType means unspecified (proto3 JSON defaults).
        assertEquals(
            mapOf(
                "log" to listOf(
                    SheetTable(
                        "log",
                        listOf(
                            TableColumn(0, "Date", "DATE"),
                            TableColumn(1, "Drill", "COLUMN_TYPE_UNSPECIFIED"),
                            TableColumn(2, "W", "DOUBLE"),
                        ),
                    ),
                ),
            ),
            result.tables,
        )
        val request = server.takeRequest()
        assertEquals("/v4/spreadsheets/id1", request.requestUrl!!.encodedPath)
        assertEquals(null, request.requestUrl!!.queryParameter("ranges"))
        assertEquals(
            "sheets(properties(sheetId,title),tables(name,range,columnProperties(columnIndex,columnName,columnType))),developerMetadata",
            request.requestUrl!!.queryParameter("fields"),
        )
    }

    @Test
    fun `call 5a without metadata gives empty maps`() = runTest {
        server.enqueue(MockResponse().setBody("""{"sheets":[{"properties":{"sheetId":0,"title":"log"}}]}"""))
        val result = sheets.readTabsAndMetadata("id1")
        assertEquals(emptyMap<String, String>(), result.metadata)
    }

    @Test
    fun `call 5b requests only the given ranges and the structure fields`() = runTest {
        server.enqueue(MockResponse().setBody("""{"sheets":[]}"""))
        sheets.readStructureCells("id1", listOf("log!1:1", "rec!A1:D2"))
        val url = server.takeRequest().requestUrl!!
        assertEquals(listOf("log!1:1", "rec!A1:D2"), url.queryParameterValues("ranges"))
        assertEquals(
            "sheets(properties.title,conditionalFormats,data.rowData.values(userEnteredValue,dataValidation))",
            url.queryParameter("fields"),
        )
    }

    @Test
    fun `call 5b is skipped when there are no ranges`() = runTest {
        assertEquals(0, sheets.readStructureCells("id1", emptyList()).size)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `call 6 creates new keys and updates existing ones`() = runTest {
        server.enqueue(MockResponse().setBody("""{"spreadsheetId":"id1","replies":[]}"""))
        sheets.upsertMetadata(
            "id1",
            values = linkedMapOf("workout_rec.script_id" to "s2", "workout_rec.enable_url" to "https://e"),
            existingIds = mapOf("workout_rec.script_id" to 11),
        )
        val requests = GoogleJson.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject["requests"]!!.jsonArray
        val update = requests[0].jsonObject["updateDeveloperMetadata"]!!.jsonObject
        assertEquals(
            11,
            update["dataFilters"]!!.jsonArray[0].jsonObject["developerMetadataLookup"]!!.jsonObject["metadataId"]!!.jsonPrimitive.content.toInt(),
        )
        assertEquals("s2", update["developerMetadata"]!!.jsonObject["metadataValue"]!!.jsonPrimitive.content)
        assertEquals("metadataValue", update["fields"]!!.jsonPrimitive.content)
        val create = requests[1].jsonObject["createDeveloperMetadata"]!!.jsonObject["developerMetadata"]!!.jsonObject
        assertEquals("workout_rec.enable_url", create["metadataKey"]!!.jsonPrimitive.content)
        assertEquals("DOCUMENT", create["visibility"]!!.jsonPrimitive.content)
        assertEquals("true", create["location"]!!.jsonObject["spreadsheet"]!!.jsonPrimitive.content)
    }
}
