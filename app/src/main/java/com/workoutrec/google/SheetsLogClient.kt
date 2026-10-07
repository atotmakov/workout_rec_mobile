package com.workoutrec.google

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request

/** Call 20 result. */
data class LogSheetInfo(val timeZone: String, val logSheetId: Int)

/** A valid set row of the log tab; [rowIndex] is the 0-based sheet row (row 2 → 1). */
data class SheetLogRow(val rowIndex: Int, val serial: Double, val exercise: String, val weight: Double, val reps: Int)

data class SheetDrill(val muscleGroup: String, val name: String)

/** Call 21 result. */
data class LogAndDrills(val log: List<SheetLogRow>, val drills: List<SheetDrill>)

data class LogEdit(val rowIndex: Int, val exercise: String, val weight: Double, val reps: Int)

data class LogAppend(val serial: Double, val exercise: String, val weight: Double, val reps: Int)

/** The spreadsheet has no `log` or `drills` tab (contracts/sheets-log.md, failure `Structure`). */
class MissingTabException(val tab: String) : Exception("Missing tab $tab")

/** Sheets calls 20–22 (contracts/sheets-log.md). */
interface SheetsLogApi {
    suspend fun logSheetInfo(spreadsheetId: String): LogSheetInfo
    suspend fun readLogAndDrills(spreadsheetId: String): LogAndDrills
    suspend fun writeLog(
        spreadsheetId: String,
        logSheetId: Int,
        deletes: List<Int>,
        edits: List<LogEdit>,
        appends: List<LogAppend>,
    )
}

class SheetsLogClient(
    private val http: GoogleHttp,
    private val baseUrl: HttpUrl = "https://sheets.googleapis.com/".toHttpUrl(),
) : SheetsLogApi {

    @Serializable
    private data class Properties(val timeZone: String = "", val sheetId: Int = 0, val title: String = "")

    @Serializable
    private data class Sheet(val properties: Properties = Properties())

    @Serializable
    private data class InfoResponse(val properties: Properties = Properties(), val sheets: List<Sheet> = emptyList())

    @Serializable
    private data class ValueRange(val values: List<List<JsonElement>> = emptyList())

    @Serializable
    private data class BatchGetResponse(val valueRanges: List<ValueRange> = emptyList())

    /** Call 20. */
    override suspend fun logSheetInfo(spreadsheetId: String): LogSheetInfo {
        val url = spreadsheets().addPathSegment(spreadsheetId)
            .addQueryParameter("fields", "properties.timeZone,sheets.properties(sheetId,title)")
            .build()
        val response = GoogleJson.decodeFromString<InfoResponse>(http.execute(Request.Builder().url(url).build()))
        val sheetIds = response.sheets.associate { it.properties.title to it.properties.sheetId }
        listOf(LOG, DRILLS).firstOrNull { it !in sheetIds }?.let { throw MissingTabException(it) }
        return LogSheetInfo(response.properties.timeZone, sheetIds.getValue(LOG))
    }

    /** Call 21. */
    override suspend fun readLogAndDrills(spreadsheetId: String): LogAndDrills {
        val url = spreadsheets().addPathSegment("$spreadsheetId").addPathSegment("values:batchGet")
            .addQueryParameter("ranges", "$LOG!A2:D")
            .addQueryParameter("ranges", "$DRILLS!A2:B")
            .addQueryParameter("valueRenderOption", "UNFORMATTED_VALUE")
            .addQueryParameter("dateTimeRenderOption", "SERIAL_NUMBER")
            .addQueryParameter("majorDimension", "ROWS")
            .build()
        val response = GoogleJson.decodeFromString<BatchGetResponse>(http.execute(Request.Builder().url(url).build()))
        val logValues = response.valueRanges.getOrNull(0)?.values.orEmpty()
        val drillValues = response.valueRanges.getOrNull(1)?.values.orEmpty()
        return LogAndDrills(
            log = logValues.mapIndexedNotNull { i, cells -> logRow(i + 1, cells) },
            drills = drillValues.mapNotNull { cells ->
                val name = cells.text(1)
                if (name.isEmpty()) null else SheetDrill(cells.text(0), name)
            },
        )
    }

    /** Call 22: deletes and edits bottom-up so row indexes stay valid, then one append. */
    override suspend fun writeLog(
        spreadsheetId: String,
        logSheetId: Int,
        deletes: List<Int>,
        edits: List<LogEdit>,
        appends: List<LogAppend>,
    ) {
        if (deletes.isEmpty() && edits.isEmpty() && appends.isEmpty()) return
        val byRow: List<Pair<Int, JsonObject>> =
            deletes.map { it to deleteRow(logSheetId, it) } + edits.map { it.rowIndex to editRow(logSheetId, it) }
        val requests = buildJsonArray {
            byRow.sortedByDescending { it.first }.forEach { add(it.second) }
            if (appends.isNotEmpty()) add(appendRows(logSheetId, appends))
        }
        val url = spreadsheets().addPathSegment("$spreadsheetId:batchUpdate").build()
        val body = buildJsonObject { put("requests", requests) }.toString().toJsonBody()
        http.execute(Request.Builder().url(url).post(body).build())
    }

    private fun deleteRow(sheetId: Int, row: Int) = buildJsonObject {
        putJsonObject("deleteDimension") {
            putJsonObject("range") {
                put("sheetId", sheetId)
                put("dimension", "ROWS")
                put("startIndex", row)
                put("endIndex", row + 1)
            }
        }
    }

    /** Columns B–D only: an edit never changes the date-time (FR-006). */
    private fun editRow(sheetId: Int, edit: LogEdit) = buildJsonObject {
        putJsonObject("updateCells") {
            putJsonObject("start") {
                put("sheetId", sheetId)
                put("rowIndex", edit.rowIndex)
                put("columnIndex", 1)
            }
            putJsonArray("rows") { add(rowData(listOf(text(edit.exercise), number(edit.weight), number(edit.reps)))) }
            put("fields", "userEnteredValue")
        }
    }

    private fun appendRows(sheetId: Int, appends: List<LogAppend>) = buildJsonObject {
        putJsonObject("appendCells") {
            put("sheetId", sheetId)
            putJsonArray("rows") {
                appends.forEach { add(rowData(listOf(number(it.serial), text(it.exercise), number(it.weight), number(it.reps)))) }
            }
            put("fields", "userEnteredValue")
        }
    }

    private fun rowData(cells: List<JsonObject>) = buildJsonObject {
        put("values", JsonArray(cells.map { value -> buildJsonObject { put("userEnteredValue", value) } }))
    }

    private fun text(value: String) = buildJsonObject { put("stringValue", value) }
    private fun number(value: Number) = buildJsonObject { put("numberValue", value) }

    private fun spreadsheets(): HttpUrl.Builder = baseUrl.newBuilder().addPathSegments("v4/spreadsheets")

    private companion object {
        const val LOG = "log"
        const val DRILLS = "drills"

        /** A set row: A is a number, B is not blank, C is a number or blank (0), D is a whole number ≥ 1. */
        fun logRow(rowIndex: Int, cells: List<JsonElement>): SheetLogRow? {
            val serial = cells.number(0) ?: return null
            val exercise = cells.text(1).ifEmpty { return null }
            val weight = if (cells.text(2).isEmpty()) 0.0 else cells.number(2) ?: return null
            val reps = cells.number(3) ?: return null
            if (reps < 1 || reps != Math.floor(reps)) return null
            return SheetLogRow(rowIndex, serial, exercise, weight, reps.toInt())
        }

        fun List<JsonElement>.number(i: Int): Double? =
            (getOrNull(i) as? JsonPrimitive)?.takeIf { !it.isString }?.doubleOrNull

        fun List<JsonElement>.text(i: Int): String = (getOrNull(i) as? JsonPrimitive)?.content?.trim().orEmpty()
    }
}
