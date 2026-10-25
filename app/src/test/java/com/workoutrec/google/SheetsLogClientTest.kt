package com.workoutrec.google

import com.workoutrec.spreadsheet.ReferenceStructure
import com.workoutrec.workout.DayStatus
import java.time.LocalDate
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
        assertEquals(LogSheetInfo("Europe/Moscow", 0, drillsSheetId = 7), client.logSheetInfo("id1"))
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
        // Feature 004: drills keep their sheet row (row 2 → 1) for renames and deletes.
        assertEquals(listOf(SheetDrill("back", "pull-up", 1), SheetDrill("", "plank", 2), SheetDrill("chest", "bench", 4)), result.drills)
    }

    // Feature 004 contracts/sheets.md call 20
    @Test
    fun `call 20 also returns the drills, rec, money and balance sheet ids`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"properties":{"timeZone":"UTC"},"sheets":[
                     {"properties":{"title":"log"}},{"properties":{"sheetId":7,"title":"drills"}},
                     {"properties":{"sheetId":8,"title":"rec"}},{"properties":{"sheetId":9,"title":"money"}},
                     {"properties":{"sheetId":10,"title":"balance"}}]}""",
            ),
        )
        assertEquals(LogSheetInfo("UTC", 0, drillsSheetId = 7, recSheetId = 8, moneySheetId = 9, balanceSheetId = 10), client.logSheetInfo("id1"))
    }

    @Test
    fun `call 20 reports missing optional tabs as null`() = runTest {
        server.enqueue(MockResponse().setBody("""{"properties":{"timeZone":"UTC"},"sheets":[{"properties":{"sheetId":1,"title":"log"}},{"properties":{"sheetId":2,"title":"drills"}}]}"""))
        assertEquals(LogSheetInfo("UTC", 1, drillsSheetId = 2), client.logSheetInfo("id1"))
    }

    // Feature 004 contracts/sheets.md call 21, research R5
    @Test
    fun `call 21 reads the money tab only when asked`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"valueRanges":[{},{},{"values":[[46000, 8, 8000], ["", 4, 4000], [46030, "x", 12000.5]]}]}""",
            ),
        )
        val result = client.readLogAndDrills("id1", withMoney = true)
        assertEquals(listOf("log!A2:D", "drills!A2:B", "money!A2:C"), server.takeRequest().requestUrl!!.queryParameterValues("ranges"))
        assertEquals(
            listOf(SheetMoneyRow(1, 46000.0, 8.0, 8000.0), SheetMoneyRow(2, null, 4.0, 4000.0), SheetMoneyRow(3, 46030.0, null, 12000.5)),
            result.money,
        )
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

    // Feature 004 contracts/sheets.md "Call 22 request order"
    @Test
    fun `call 22 writes drills changes, renames by findReplace, then all appends`() = runTest {
        server.enqueue(MockResponse().setBody("""{"replies":[]}"""))
        client.writeLog(
            spreadsheetId = "id1",
            logSheetId = 4,
            deletes = listOf(3),
            edits = emptyList(),
            appends = listOf(LogAppend(46034.5, "Bench", 60.0, 8)),
            drills = DrillsWrite(
                drillsSheetId = 7,
                recSheetId = 8,
                deletes = listOf(2),
                renames = listOf(DrillRename(5, "Chest", "Bench")),
                replaces = listOf(NameReplace("Bench press", "Bench")),
                appends = listOf(SheetDrill("Shoulders", "Face pull")),
            ),
        )
        val requests = GoogleJson.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject["requests"]!!.jsonArray.map { it.jsonObject }
        assertEquals(
            listOf("deleteDimension", "updateCells", "deleteDimension", "findReplace", "findReplace", "appendCells", "appendCells"),
            requests.map { it.keys.single() },
        )
        // Drills bottom-up: rename row 5, then delete row 2.
        val rename = requests[1]["updateCells"]!!.jsonObject
        assertEquals(listOf(7, 5, 0), listOf("sheetId", "rowIndex", "columnIndex").map { rename["start"]!!.jsonObject[it]!!.jsonPrimitive.int })
        val renameCells = rename["rows"]!!.jsonArray.single().jsonObject["values"]!!.jsonArray.map { it.jsonObject["userEnteredValue"]!!.jsonObject["stringValue"]!!.jsonPrimitive.content }
        assertEquals(listOf("Chest", "Bench"), renameCells)
        assertEquals(7, requests[2]["deleteDimension"]!!.jsonObject["range"]!!.jsonObject["sheetId"]!!.jsonPrimitive.int)

        val logReplace = requests[3]["findReplace"]!!.jsonObject
        assertEquals("Bench press", logReplace["find"]!!.jsonPrimitive.content)
        assertEquals("Bench", logReplace["replacement"]!!.jsonPrimitive.content)
        listOf("matchCase", "matchEntireCell").forEach { assertEquals(it, "true", logReplace[it]!!.jsonPrimitive.content) }
        listOf("searchByRegex", "includeFormulas").forEach { assertEquals(it, "false", logReplace[it]!!.jsonPrimitive.content) }
        val logRange = logReplace["range"]!!.jsonObject
        assertEquals(listOf(4, 1, 1, 2), listOf("sheetId", "startRowIndex", "startColumnIndex", "endColumnIndex").map { logRange[it]!!.jsonPrimitive.int })
        val recRange = requests[4]["findReplace"]!!.jsonObject["range"]!!.jsonObject
        assertEquals(listOf(8, 0, 1, 0, 1), listOf("sheetId", "startRowIndex", "endRowIndex", "startColumnIndex", "endColumnIndex").map { recRange[it]!!.jsonPrimitive.int })

        assertEquals(4, requests[5]["appendCells"]!!.jsonObject["sheetId"]!!.jsonPrimitive.int)
        val drillAppend = requests[6]["appendCells"]!!.jsonObject
        assertEquals(7, drillAppend["sheetId"]!!.jsonPrimitive.int)
        val drillCells = drillAppend["rows"]!!.jsonArray.single().jsonObject["values"]!!.jsonArray.map { it.jsonObject["userEnteredValue"]!!.jsonObject["stringValue"]!!.jsonPrimitive.content }
        assertEquals(listOf("Shoulders", "Face pull"), drillCells)
    }

    @Test
    fun `call 22 skips the rec rename when the rec tab is missing`() = runTest {
        server.enqueue(MockResponse().setBody("""{"replies":[]}"""))
        client.writeLog("id1", 0, emptyList(), emptyList(), emptyList(), DrillsWrite(drillsSheetId = 1, recSheetId = null, replaces = listOf(NameReplace("a", "b"))))
        val requests = GoogleJson.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject["requests"]!!.jsonArray
        assertEquals(listOf("findReplace"), requests.map { (it as JsonObject).keys.single() })
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

    // Feature 006 contracts/sheets.md: the workout tab

    @Test
    fun `call 20 also returns the workout sheet id`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"properties":{"timeZone":"UTC"},"sheets":[{"properties":{"sheetId":1,"title":"log"}},
                   {"properties":{"sheetId":2,"title":"drills"}},{"properties":{"sheetId":9,"title":"workout"}}]}""",
            ),
        )
        assertEquals(9, client.logSheetInfo("id1").workoutSheetId)
        server.enqueue(MockResponse().setBody("""{"properties":{"timeZone":"UTC"},"sheets":[{"properties":{"sheetId":1,"title":"log"}},{"properties":{"sheetId":2,"title":"drills"}}]}"""))
        assertEquals(null, client.logSheetInfo("id1").workoutSheetId)
    }

    @Test
    fun `call 21 reads the workout tab after the money tab and parses its rows`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"valueRanges":[{"values":[]},{"values":[]},
                   {"values":[[46034,8,8000]]},
                   {"values":[[46030.75,45,0],["x",10,1],[46031,"",1],[46032,30,"1"],[],[46033,20]]}]}""",
            ),
        )
        val read = client.readLogAndDrills("id1", withMoney = true, withWorkout = true)
        assertEquals(listOf("log!A2:D", "drills!A2:B", "money!A2:C", "workout!A2:C"), server.takeRequest().requestUrl!!.queryParameterValues("ranges"))
        assertEquals(listOf(SheetMoneyRow(1, 46034.0, 8.0, 8000.0)), read.money)
        assertEquals(
            listOf(
                SheetWorkoutRow(1, LocalDate.of(2026, 1, 8), DayStatus.PAID),
                SheetWorkoutRow(3, LocalDate.of(2026, 1, 9), DayStatus.FREE),
                SheetWorkoutRow(4, LocalDate.of(2026, 1, 10), DayStatus.FREE),
                SheetWorkoutRow(6, LocalDate.of(2026, 1, 11), DayStatus.PAID),
            ),
            read.workout,
        )
    }

    @Test
    fun `call 21 reads the workout tab without the money tab`() = runTest {
        server.enqueue(MockResponse().setBody("""{"valueRanges":[{"values":[]},{"values":[]},{"values":[[46034,0,1]]}]}"""))
        val read = client.readLogAndDrills("id1", withWorkout = true)
        assertEquals(listOf("log!A2:D", "drills!A2:B", "workout!A2:C"), server.takeRequest().requestUrl!!.queryParameterValues("ranges"))
        assertEquals(emptyList<SheetMoneyRow>(), read.money)
        assertEquals(listOf(SheetWorkoutRow(1, LocalDate.of(2026, 1, 12), DayStatus.FREE)), read.workout)
    }

    @Test
    fun `call 22 updates the status column, then appends new workout rows last`() = runTest {
        server.enqueue(MockResponse().setBody("""{"replies":[]}"""))
        client.writeLog(
            spreadsheetId = "id1",
            logSheetId = 4,
            deletes = emptyList(),
            edits = emptyList(),
            appends = listOf(LogAppend(46034.5, "row", 30.0, 12)),
            money = MoneyWrite(6, listOf(MoneyAppend(46034.0, 8, 8000.0))),
            workout = WorkoutWrite(
                workoutSheetId = 9,
                updates = listOf(WorkoutUpdate(3, DayStatus.FREE), WorkoutUpdate(5, DayStatus.PAID)),
                appends = listOf(WorkoutAppend(46033.0, DayStatus.PAID), WorkoutAppend(46034.0, DayStatus.FREE)),
            ),
        )
        val requests = GoogleJson.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject["requests"]!!.jsonArray.map { it.jsonObject }
        assertEquals(listOf("appendCells", "appendCells", "updateCells", "updateCells", "appendCells"), requests.map { it.keys.single() })

        val update = requests[2]["updateCells"]!!.jsonObject
        assertEquals(listOf(9, 3, 2), listOf("sheetId", "rowIndex", "columnIndex").map { update["start"]!!.jsonObject[it]!!.jsonPrimitive.int })
        assertEquals("userEnteredValue", update["fields"]!!.jsonPrimitive.content)
        val cell = update["rows"]!!.jsonArray.single().jsonObject["values"]!!.jsonArray.single().jsonObject["userEnteredValue"]!!.jsonObject
        assertEquals(1.0, cell["numberValue"]!!.jsonPrimitive.double, 0.0)
        assertEquals(5, requests[3]["updateCells"]!!.jsonObject["start"]!!.jsonObject["rowIndex"]!!.jsonPrimitive.int)

        val append = requests[4]["appendCells"]!!.jsonObject
        assertEquals(9, append["sheetId"]!!.jsonPrimitive.int)
        assertEquals("userEnteredValue", append["fields"]!!.jsonPrimitive.content)
        val rows = append["rows"]!!.jsonArray.map { it.jsonObject["values"]!!.jsonArray.map { v -> v.jsonObject } }
        assertEquals(46033.0, rows[0][0]["userEnteredValue"]!!.jsonObject["numberValue"]!!.jsonPrimitive.double, 0.0)
        // Feature 007 research R1: B is left to the form step, which writes the formula in the spreadsheet's locale.
        assertEquals(null, rows[0][1]["userEnteredValue"])
        assertEquals(0.0, rows[0][2]["userEnteredValue"]!!.jsonObject["numberValue"]!!.jsonPrimitive.double, 0.0)
        assertEquals(1.0, rows[1][2]["userEnteredValue"]!!.jsonObject["numberValue"]!!.jsonPrimitive.double, 0.0)
    }

    @Test
    fun `call 22 is sent for workout statuses alone`() = runTest {
        server.enqueue(MockResponse().setBody("""{"replies":[]}"""))
        client.writeLog("id1", 0, emptyList(), emptyList(), emptyList(), workout = WorkoutWrite(9, listOf(WorkoutUpdate(1, DayStatus.FREE)), emptyList()))
        val requests = GoogleJson.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject["requests"]!!.jsonArray
        assertEquals(listOf("updateCells"), requests.map { (it as JsonObject).keys.single() })
        client.writeLog("id1", 0, emptyList(), emptyList(), emptyList(), workout = WorkoutWrite(9, emptyList(), emptyList()))
        assertEquals(1, server.requestCount)
    }

    // Feature 007 contracts/sheets.md

    @Test
    fun `call 22 updates workout statuses, then deletes rows bottom-up, then appends`() = runTest {
        server.enqueue(MockResponse().setBody("""{"replies":[]}"""))
        client.writeLog(
            "id1", 0, emptyList(), emptyList(), emptyList(),
            workout = WorkoutWrite(
                workoutSheetId = 9,
                updates = listOf(WorkoutUpdate(2, DayStatus.FREE)),
                appends = listOf(WorkoutAppend(46035.0, DayStatus.PAID)),
                deletes = listOf(3, 7),
            ),
        )
        val requests = GoogleJson.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject["requests"]!!.jsonArray.map { it.jsonObject }
        assertEquals(listOf("updateCells", "deleteDimension", "deleteDimension", "appendCells"), requests.map { it.keys.single() })
        val ranges = requests.subList(1, 3).map { it["deleteDimension"]!!.jsonObject["range"]!!.jsonObject }
        assertEquals(listOf(7, 3), ranges.map { it["startIndex"]!!.jsonPrimitive.int })
        assertEquals(listOf(8, 4), ranges.map { it["endIndex"]!!.jsonPrimitive.int })
        assertEquals(listOf("ROWS", "ROWS"), ranges.map { it["dimension"]!!.jsonPrimitive.content })
        assertEquals(listOf(9, 9), ranges.map { it["sheetId"]!!.jsonPrimitive.int })
    }

    @Test
    fun `call 22 is sent for workout deletes alone`() = runTest {
        server.enqueue(MockResponse().setBody("""{"replies":[]}"""))
        client.writeLog("id1", 0, emptyList(), emptyList(), emptyList(), workout = WorkoutWrite(9, emptyList(), emptyList(), deletes = listOf(1)))
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `call 24 reads the duration and balance cells with the metadata`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"properties":{"locale":"ru_RU"},"developerMetadata":[{"metadataId":11,"metadataKey":"workout_rec.structure_version","metadataValue":"2"},
                     {"metadataId":12,"metadataKey":"workout_rec.script_version","metadataValue":"3"}],
                   "sheets":[
                     {"properties":{"title":"workout"},"data":[{"startRow":1,"rowData":[
                        {"values":[{"userEnteredValue":{"numberValue":46000}},{"userEnteredValue":{"formulaValue":"=LET(x, 1, x)"}}]},
                        {},
                        {"values":[{"userEnteredValue":{"numberValue":46001}},{"userEnteredValue":{"numberValue":25}}]},
                        {"values":[{"userEnteredValue":{"stringValue":"note"}}]},
                        {"values":[{"userEnteredValue":{"numberValue":46002}}]},
                        {"values":[{"userEnteredValue":{"numberValue":46003}},{"userEnteredValue":{"formulaValue":"=SUM(1, 2)"},
                          "effectiveValue":{"errorValue":{"type":"ERROR","message":"Formula parse error."}}}]},
                        {"values":[{"userEnteredValue":{"numberValue":46004}},{"userEnteredValue":{"formulaValue":"=1/0"},
                          "effectiveValue":{"errorValue":{"type":"DIVIDE_BY_ZERO"}}}]}]}]},
                     {"properties":{"title":"balance"},"data":[{"rowData":[{"values":[{"userEnteredValue":{"numberValue":4}}]}]}]}]}""",
            ),
        )
        val cells = client.readCalculatedCells("id1")
        val url = server.takeRequest().requestUrl!!
        assertEquals(listOf("workout!A2:B", "balance!A1"), url.queryParameterValues("ranges"))
        assertEquals(
            "properties(locale),developerMetadata(metadataId,metadataKey,metadataValue)," +
                "sheets(properties(title),data(startRow,rowData(values(userEnteredValue,effectiveValue))))",
            url.queryParameter("fields"),
        )
        assertEquals(
            listOf(
                CalculatedRow(1, hasDate = true, duration = CellContent(formula = "=LET(x, 1, x)", empty = false)),
                CalculatedRow(2, hasDate = false, duration = CellContent(formula = null, empty = true)),
                CalculatedRow(3, hasDate = true, duration = CellContent(formula = null, empty = false)),
                CalculatedRow(4, hasDate = false, duration = CellContent(formula = null, empty = true)),
                CalculatedRow(5, hasDate = true, duration = CellContent(formula = null, empty = true)),
                CalculatedRow(6, hasDate = true, duration = CellContent(formula = "=SUM(1, 2)", empty = false, parseError = true)),
                CalculatedRow(7, hasDate = true, duration = CellContent(formula = "=1/0", empty = false)),
            ),
            cells.rows,
        )
        assertEquals(CellContent(formula = null, empty = false), cells.balance)
        assertEquals("2", cells.metadata["workout_rec.structure_version"])
        assertEquals(12, cells.metadataIds["workout_rec.script_version"])
        assertEquals("ru_RU", cells.locale)
    }

    @Test
    fun `call 24 reads an empty workout tab and an empty balance`() = runTest {
        server.enqueue(MockResponse().setBody("""{"sheets":[{"properties":{"title":"workout"},"data":[{"startRow":1}]},{"properties":{"title":"balance"},"data":[{}]}]}"""))
        val cells = client.readCalculatedCells("id1")
        assertEquals(emptyList<CalculatedRow>(), cells.rows)
        assertEquals(CellContent(formula = null, empty = true), cells.balance)
        assertEquals(emptyMap<String, String>(), cells.metadata)
        assertEquals(null, cells.locale)
    }

    @Test
    fun `call 25 writes formula runs, the balance formula and the version marker in one request`() = runTest {
        server.enqueue(MockResponse().setBody("""{"replies":[]}"""))
        client.repairCalculatedCells(
            "id1",
            CalculatedRepair(
                workoutSheetId = 9,
                durationRuns = listOf(1..2, 5..5),
                balanceSheetId = 5,
                metadata = mapOf("workout_rec.structure_version" to "3"),
                metadataIds = mapOf("workout_rec.structure_version" to 11),
                durationFormula = ReferenceStructure.durationFormula("ru_RU"),
            ),
        )
        val requests = GoogleJson.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject["requests"]!!.jsonArray.map { it.jsonObject }
        assertEquals(listOf("repeatCell", "repeatCell", "updateCells", "updateDeveloperMetadata"), requests.map { it.keys.single() })
        val first = requests[0]["repeatCell"]!!.jsonObject
        assertEquals(
            listOf(9, 1, 3, 1, 2),
            listOf("sheetId", "startRowIndex", "endRowIndex", "startColumnIndex", "endColumnIndex").map { first["range"]!!.jsonObject[it]!!.jsonPrimitive.int },
        )
        assertEquals(ReferenceStructure.durationFormula("ru_RU"), first["cell"]!!.jsonObject["userEnteredValue"]!!.jsonObject["formulaValue"]!!.jsonPrimitive.content)
        assertEquals("userEnteredValue", first["fields"]!!.jsonPrimitive.content)
        assertEquals(5, requests[1]["repeatCell"]!!.jsonObject["range"]!!.jsonObject["startRowIndex"]!!.jsonPrimitive.int)
        val balance = requests[2]["updateCells"]!!.jsonObject
        assertEquals(listOf(5, 0, 0), listOf("sheetId", "rowIndex", "columnIndex").map { balance["start"]!!.jsonObject[it]!!.jsonPrimitive.int })
        val cell = balance["rows"]!!.jsonArray.single().jsonObject["values"]!!.jsonArray.single().jsonObject["userEnteredValue"]!!.jsonObject
        assertEquals(ReferenceStructure.BALANCE_FORMULA, cell["formulaValue"]!!.jsonPrimitive.content)
        assertEquals("3", requests[3]["updateDeveloperMetadata"]!!.jsonObject["developerMetadata"]!!.jsonObject["metadataValue"]!!.jsonPrimitive.content)
    }

    @Test
    fun `call 25 sends nothing when there is nothing to repair`() = runTest {
        client.repairCalculatedCells("id1", CalculatedRepair(9, emptyList(), null, emptyMap(), emptyMap()))
        assertEquals(0, server.requestCount)
    }
}
