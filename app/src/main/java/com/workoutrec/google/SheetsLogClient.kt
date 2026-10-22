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

/** Call 20 result; feature 004 adds the drills, rec, money and balance sheets (null when missing). */
data class LogSheetInfo(
    val timeZone: String,
    val logSheetId: Int,
    val drillsSheetId: Int = 0,
    val recSheetId: Int? = null,
    val moneySheetId: Int? = null,
    val balanceSheetId: Int? = null,
)

/** A valid set row of the log tab; [rowIndex] is the 0-based sheet row (row 2 → 1). */
data class SheetLogRow(val rowIndex: Int, val serial: Double, val exercise: String, val weight: Double, val reps: Int)

/** A drills row; [rowIndex] is the 0-based sheet row (row 2 → 1), for renames and deletes (feature 004). */
data class SheetDrill(val muscleGroup: String, val name: String, val rowIndex: Int = 0)

/** A money row as read for matching payments (feature 004 research R5); null cells were not numbers. */
data class SheetMoneyRow(val rowIndex: Int, val serial: Double?, val workouts: Double?, val sum: Double?)

/** Call 21 result; [money] only when asked for. */
data class LogAndDrills(val log: List<SheetLogRow>, val drills: List<SheetDrill>, val money: List<SheetMoneyRow> = emptyList())

data class LogEdit(val rowIndex: Int, val exercise: String, val weight: Double, val reps: Int)

data class LogAppend(val serial: Double, val exercise: String, val weight: Double, val reps: Int)

/** A drills row's new values (feature 004 rename). */
data class DrillRename(val rowIndex: Int, val muscleGroup: String, val name: String)

/** Every whole cell [old] becomes [new], in log column B and rec A1 (feature 004 research R4). */
data class NameReplace(val old: String, val new: String)

/** Feature 004: the drills part of call 22 (contracts/sheets.md "Call 22 request order"). */
data class DrillsWrite(
    val drillsSheetId: Int,
    /** Null when the spreadsheet has no rec tab: rec A1 is then not renamed. */
    val recSheetId: Int?,
    val deletes: List<Int> = emptyList(),
    val renames: List<DrillRename> = emptyList(),
    val replaces: List<NameReplace> = emptyList(),
    val appends: List<SheetDrill> = emptyList(),
) {
    val isEmpty: Boolean get() = deletes.isEmpty() && renames.isEmpty() && replaces.isEmpty() && appends.isEmpty()
}

/** The spreadsheet has no `log` or `drills` tab (contracts/sheets-log.md, failure `Structure`). */
class MissingTabException(val tab: String) : Exception("Missing tab $tab")

/** Sheets calls 20–22 (contracts/sheets-log.md). */
interface SheetsLogApi {
    suspend fun logSheetInfo(spreadsheetId: String): LogSheetInfo
    suspend fun readLogAndDrills(spreadsheetId: String, withMoney: Boolean = false): LogAndDrills
    suspend fun writeLog(
        spreadsheetId: String,
        logSheetId: Int,
        deletes: List<Int>,
        edits: List<LogEdit>,
        appends: List<LogAppend>,
        drills: DrillsWrite? = null,
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
        return LogSheetInfo(
            timeZone = response.properties.timeZone,
            logSheetId = sheetIds.getValue(LOG),
            drillsSheetId = sheetIds.getValue(DRILLS),
            recSheetId = sheetIds[REC],
            moneySheetId = sheetIds[MONEY],
            balanceSheetId = sheetIds[BALANCE],
        )
    }

    /** Call 21. */
    override suspend fun readLogAndDrills(spreadsheetId: String, withMoney: Boolean): LogAndDrills {
        val url = spreadsheets().addPathSegment("$spreadsheetId").addPathSegment("values:batchGet")
            .addQueryParameter("ranges", "$LOG!A2:D")
            .addQueryParameter("ranges", "$DRILLS!A2:B")
            .apply { if (withMoney) addQueryParameter("ranges", "$MONEY!A2:C") }
            .addQueryParameter("valueRenderOption", "UNFORMATTED_VALUE")
            .addQueryParameter("dateTimeRenderOption", "SERIAL_NUMBER")
            .addQueryParameter("majorDimension", "ROWS")
            .build()
        val response = GoogleJson.decodeFromString<BatchGetResponse>(http.execute(Request.Builder().url(url).build()))
        val logValues = response.valueRanges.getOrNull(0)?.values.orEmpty()
        val drillValues = response.valueRanges.getOrNull(1)?.values.orEmpty()
        val moneyValues = response.valueRanges.getOrNull(2)?.values.orEmpty()
        return LogAndDrills(
            log = logValues.mapIndexedNotNull { i, cells -> logRow(i + 1, cells) },
            drills = drillValues.mapIndexedNotNull { i, cells ->
                val name = cells.text(1)
                if (name.isEmpty()) null else SheetDrill(cells.text(0), name, i + 1)
            },
            money = moneyValues.mapIndexedNotNull { i, cells ->
                if (cells.isEmpty()) null else SheetMoneyRow(i + 1, cells.number(0), cells.number(1), cells.number(2))
            },
        )
    }

    /**
     * Call 22: deletes and edits bottom-up so row indexes stay valid, per tab; then the renames
     * (feature 004); then the appends. One atomic write.
     */
    override suspend fun writeLog(
        spreadsheetId: String,
        logSheetId: Int,
        deletes: List<Int>,
        edits: List<LogEdit>,
        appends: List<LogAppend>,
        drills: DrillsWrite?,
    ) {
        val drillsPart = drills?.takeUnless { it.isEmpty }
        if (deletes.isEmpty() && edits.isEmpty() && appends.isEmpty() && drillsPart == null) return
        val byRow: List<Pair<Int, JsonObject>> =
            deletes.map { it to deleteRow(logSheetId, it) } + edits.map { it.rowIndex to editRow(logSheetId, it) }
        val drillsByRow: List<Pair<Int, JsonObject>> = drillsPart?.let { d ->
            d.deletes.map { it to deleteRow(d.drillsSheetId, it) } + d.renames.map { it.rowIndex to renameDrill(d.drillsSheetId, it) }
        }.orEmpty()
        val requests = buildJsonArray {
            byRow.sortedByDescending { it.first }.forEach { add(it.second) }
            drillsByRow.sortedByDescending { it.first }.forEach { add(it.second) }
            drillsPart?.replaces?.forEach { replace ->
                add(findReplace(replace, logColumnB(logSheetId)))
                drillsPart.recSheetId?.let { add(findReplace(replace, recA1(it))) }
            }
            if (appends.isNotEmpty()) add(appendRows(logSheetId, appends))
            drillsPart?.appends?.takeIf { it.isNotEmpty() }?.let { add(appendDrills(drillsPart.drillsSheetId, it)) }
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

    private fun renameDrill(sheetId: Int, rename: DrillRename) = buildJsonObject {
        putJsonObject("updateCells") {
            putJsonObject("start") {
                put("sheetId", sheetId)
                put("rowIndex", rename.rowIndex)
                put("columnIndex", 0)
            }
            putJsonArray("rows") { add(rowData(listOf(text(rename.muscleGroup), text(rename.name)))) }
            put("fields", "userEnteredValue")
        }
    }

    private fun appendDrills(sheetId: Int, drills: List<SheetDrill>) = buildJsonObject {
        putJsonObject("appendCells") {
            put("sheetId", sheetId)
            putJsonArray("rows") { drills.forEach { add(rowData(listOf(text(it.muscleGroup), text(it.name)))) } }
            put("fields", "userEnteredValue")
        }
    }

    /** Whole-cell, case-sensitive: only cells that are exactly the old name change. */
    private fun findReplace(replace: NameReplace, range: JsonObject) = buildJsonObject {
        putJsonObject("findReplace") {
            put("find", replace.old)
            put("replacement", replace.new)
            put("matchCase", true)
            put("matchEntireCell", true)
            put("searchByRegex", false)
            put("includeFormulas", false)
            put("range", range)
        }
    }

    private fun logColumnB(sheetId: Int) = buildJsonObject {
        put("sheetId", sheetId)
        put("startRowIndex", 1)
        put("startColumnIndex", 1)
        put("endColumnIndex", 2)
    }

    private fun recA1(sheetId: Int) = buildJsonObject {
        put("sheetId", sheetId)
        put("startRowIndex", 0)
        put("endRowIndex", 1)
        put("startColumnIndex", 0)
        put("endColumnIndex", 1)
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
        const val REC = "rec"
        const val MONEY = "money"
        const val BALANCE = "balance"

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
