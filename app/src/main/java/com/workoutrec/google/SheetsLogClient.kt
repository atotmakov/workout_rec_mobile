package com.workoutrec.google

import com.workoutrec.money.PaymentValues
import com.workoutrec.spreadsheet.ReferenceStructure
import com.workoutrec.workout.DayStatus
import com.workoutrec.workout.DayStatusRules
import java.time.LocalDate
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
    /** Feature 006: null when the spreadsheet has no `workout` tab. */
    val workoutSheetId: Int? = null,
)

/** A valid set row of the log tab; [rowIndex] is the 0-based sheet row (row 2 → 1). */
data class SheetLogRow(val rowIndex: Int, val serial: Double, val exercise: String, val weight: Double, val reps: Int)

/** A drills row; [rowIndex] is the 0-based sheet row (row 2 → 1), for renames and deletes (feature 004). */
data class SheetDrill(val muscleGroup: String, val name: String, val rowIndex: Int = 0)

/** A money row as read for matching payments (feature 004 research R5); null cells were not numbers. */
data class SheetMoneyRow(val rowIndex: Int, val serial: Double?, val workouts: Double?, val sum: Double?)

/** A `workout` row with a date (feature 006 contracts/sheets.md); [rowIndex] is 0-based (row 2 → 1). */
data class SheetWorkoutRow(val rowIndex: Int, val date: LocalDate, val status: DayStatus)

/** Call 21 result; [money] and [workout] only when asked for. */
data class LogAndDrills(
    val log: List<SheetLogRow>,
    val drills: List<SheetDrill>,
    val money: List<SheetMoneyRow> = emptyList(),
    val workout: List<SheetWorkoutRow> = emptyList(),
)

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

/** A money row to append: date serial, workouts paid for, sum (feature 004 FR-009b). */
data class MoneyAppend(val serial: Double, val workouts: Int, val sum: Double)

/** Feature 004: the money part of call 22. */
data class MoneyWrite(val moneySheetId: Int, val appends: List<MoneyAppend>)

/** Feature 006: a day's status into column C of an existing `workout` row. */
data class WorkoutUpdate(val rowIndex: Int, val status: DayStatus)

/** Feature 006: a new `workout` row: date serial, the duration formula (feature 007), status. */
data class WorkoutAppend(val serial: Double, val status: DayStatus)

/** Feature 006: the `workout` part of call 22; feature 007 adds [deletes] (0-based rows of days without sets). */
data class WorkoutWrite(
    val workoutSheetId: Int,
    val updates: List<WorkoutUpdate>,
    val appends: List<WorkoutAppend>,
    val deletes: List<Int> = emptyList(),
) {
    val isEmpty: Boolean get() = updates.isEmpty() && appends.isEmpty() && deletes.isEmpty()
}

/** Feature 007 call 24: a cell's content; [formula] is null for a value or an empty cell. */
data class CellContent(val formula: String?, val empty: Boolean)

/** A `workout` data row's date and duration cells; [rowIndex] is 0-based (row 2 → 1). */
data class CalculatedRow(val rowIndex: Int, val hasDate: Boolean, val duration: CellContent)

/** Call 24 result: the cells that should hold formulas, and the spreadsheet's metadata. */
data class CalculatedCells(
    val rows: List<CalculatedRow>,
    val balance: CellContent,
    val metadata: Map<String, String>,
    val metadataIds: Map<String, Int>,
)

/**
 * Call 25: [durationRuns] are 0-based row ranges whose B gets the duration formula; a non-null
 * [balanceSheetId] gets the balance formula in A1; [metadata] is upserted with [metadataIds].
 */
data class CalculatedRepair(
    val workoutSheetId: Int,
    val durationRuns: List<IntRange>,
    val balanceSheetId: Int?,
    val metadata: Map<String, String>,
    val metadataIds: Map<String, Int>,
) {
    val isEmpty: Boolean get() = durationRuns.isEmpty() && balanceSheetId == null && metadata.isEmpty()
}

/** The spreadsheet has no `log` or `drills` tab (contracts/sheets-log.md, failure `Structure`). */
class MissingTabException(val tab: String) : Exception("Missing tab $tab")

/** Sheets calls 20–22 (contracts/sheets-log.md). */
interface SheetsLogApi {
    suspend fun logSheetInfo(spreadsheetId: String): LogSheetInfo
    suspend fun readLogAndDrills(spreadsheetId: String, withMoney: Boolean = false, withWorkout: Boolean = false): LogAndDrills
    suspend fun writeLog(
        spreadsheetId: String,
        logSheetId: Int,
        deletes: List<Int>,
        edits: List<LogEdit>,
        appends: List<LogAppend>,
        drills: DrillsWrite? = null,
        money: MoneyWrite? = null,
        workout: WorkoutWrite? = null,
    )

    /** Feature 007 call 24. */
    suspend fun readCalculatedCells(spreadsheetId: String): CalculatedCells

    /** Feature 007 call 25; nothing is sent when [repair] is empty. */
    suspend fun repairCalculatedCells(spreadsheetId: String, repair: CalculatedRepair)
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
            workoutSheetId = sheetIds[WORKOUT],
        )
    }

    /** Call 21. */
    override suspend fun readLogAndDrills(spreadsheetId: String, withMoney: Boolean, withWorkout: Boolean): LogAndDrills {
        val url = spreadsheets().addPathSegment("$spreadsheetId").addPathSegment("values:batchGet")
            .addQueryParameter("ranges", "$LOG!A2:D")
            .addQueryParameter("ranges", "$DRILLS!A2:B")
            .apply { if (withMoney) addQueryParameter("ranges", "$MONEY!A2:C") }
            .apply { if (withWorkout) addQueryParameter("ranges", "$WORKOUT!A2:C") }
            .addQueryParameter("valueRenderOption", "UNFORMATTED_VALUE")
            .addQueryParameter("dateTimeRenderOption", "SERIAL_NUMBER")
            .addQueryParameter("majorDimension", "ROWS")
            .build()
        val response = GoogleJson.decodeFromString<BatchGetResponse>(http.execute(Request.Builder().url(url).build()))
        val logValues = response.valueRanges.getOrNull(0)?.values.orEmpty()
        val drillValues = response.valueRanges.getOrNull(1)?.values.orEmpty()
        // The optional ranges come back in request order.
        val moneyValues = if (withMoney) response.valueRanges.getOrNull(2)?.values.orEmpty() else emptyList()
        val workoutValues = if (withWorkout) response.valueRanges.getOrNull(if (withMoney) 3 else 2)?.values.orEmpty() else emptyList()
        return LogAndDrills(
            log = logValues.mapIndexedNotNull { i, cells -> logRow(i + 1, cells) },
            drills = drillValues.mapIndexedNotNull { i, cells ->
                val name = cells.text(1)
                if (name.isEmpty()) null else SheetDrill(cells.text(0), name, i + 1)
            },
            money = moneyValues.mapIndexedNotNull { i, cells ->
                if (cells.isEmpty()) null else SheetMoneyRow(i + 1, cells.number(0), cells.number(1), cells.number(2))
            },
            workout = workoutValues.mapIndexedNotNull { i, cells ->
                cells.number(0)?.let { serial -> SheetWorkoutRow(i + 1, PaymentValues.dateOf(serial), DayStatusRules.fromSheet(cells.cell(2))) }
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
        money: MoneyWrite?,
        workout: WorkoutWrite?,
    ) {
        val drillsPart = drills?.takeUnless { it.isEmpty }
        val moneyPart = money?.takeUnless { it.appends.isEmpty() }
        val workoutPart = workout?.takeUnless { it.isEmpty }
        if (deletes.isEmpty() && edits.isEmpty() && appends.isEmpty() && drillsPart == null && moneyPart == null && workoutPart == null) return
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
            moneyPart?.let { add(appendMoney(it)) }
            // Feature 006: a separate tab, so its row indexes are not moved by log deletes.
            workoutPart?.updates?.forEach { add(updateStatus(workoutPart.workoutSheetId, it)) }
            // Feature 007: after the updates, so their row indexes are still right; bottom-up.
            workoutPart?.deletes?.sortedDescending()?.forEach { add(deleteRow(workoutPart.workoutSheetId, it)) }
            workoutPart?.appends?.takeIf { it.isNotEmpty() }?.let { add(appendWorkouts(workoutPart.workoutSheetId, it)) }
        }
        val url = spreadsheets().addPathSegment("$spreadsheetId:batchUpdate").build()
        val body = buildJsonObject { put("requests", requests) }.toString().toJsonBody()
        http.execute(Request.Builder().url(url).post(body).build())
    }

    @Serializable
    private data class CellJson(val userEnteredValue: JsonObject? = null)

    @Serializable
    private data class RowJson(val values: List<CellJson> = emptyList())

    @Serializable
    private data class GridJson(val startRow: Int = 0, val rowData: List<RowJson> = emptyList())

    @Serializable
    private data class GridSheet(val properties: Properties = Properties(), val data: List<GridJson> = emptyList())

    @Serializable
    private data class MetadataJson(val metadataId: Int = 0, val metadataKey: String = "", val metadataValue: String = "")

    @Serializable
    private data class CellsResponse(val developerMetadata: List<MetadataJson> = emptyList(), val sheets: List<GridSheet> = emptyList())

    /** Call 24 (feature 007 contracts/sheets.md): what the calculated cells hold, with the metadata. */
    override suspend fun readCalculatedCells(spreadsheetId: String): CalculatedCells {
        val url = spreadsheets().addPathSegment(spreadsheetId)
            .addQueryParameter("ranges", "$WORKOUT!A2:B")
            .addQueryParameter("ranges", "$BALANCE!A1")
            .addQueryParameter(
                "fields",
                "developerMetadata(metadataId,metadataKey,metadataValue),sheets(properties(title),data(startRow,rowData(values(userEnteredValue))))",
            )
            .build()
        val response = GoogleJson.decodeFromString<CellsResponse>(http.execute(Request.Builder().url(url).build()))
        val grids = response.sheets.associate { it.properties.title to it.data.firstOrNull() }
        val workout = grids[WORKOUT]
        val rows = workout?.rowData.orEmpty().mapIndexed { i, row ->
            val date = row.values.getOrNull(0)?.userEnteredValue?.get("numberValue")
            CalculatedRow(workout!!.startRow + i, hasDate = date != null, duration = content(row.values.getOrNull(1)))
        }
        val balance = content(grids[BALANCE]?.rowData?.firstOrNull()?.values?.firstOrNull())
        return CalculatedCells(
            rows = rows,
            balance = balance,
            metadata = response.developerMetadata.associate { it.metadataKey to it.metadataValue },
            metadataIds = response.developerMetadata.associate { it.metadataKey to it.metadataId },
        )
    }

    private fun content(cell: CellJson?): CellContent {
        val value = cell?.userEnteredValue ?: return CellContent(formula = null, empty = true)
        return CellContent(formula = (value["formulaValue"] as? JsonPrimitive)?.content, empty = false)
    }

    /** Call 25: formulas back into the calculated cells, and the conversion marker, in one write. */
    override suspend fun repairCalculatedCells(spreadsheetId: String, repair: CalculatedRepair) {
        if (repair.isEmpty) return
        val requests = buildJsonArray {
            repair.durationRuns.forEach { run ->
                add(
                    buildJsonObject {
                        putJsonObject("repeatCell") {
                            putJsonObject("range") {
                                put("sheetId", repair.workoutSheetId)
                                put("startRowIndex", run.first)
                                put("endRowIndex", run.last + 1)
                                put("startColumnIndex", 1)
                                put("endColumnIndex", 2)
                            }
                            putJsonObject("cell") { put("userEnteredValue", formula(ReferenceStructure.DURATION_FORMULA)) }
                            put("fields", "userEnteredValue")
                        }
                    },
                )
            }
            repair.balanceSheetId?.let { sheetId ->
                add(
                    buildJsonObject {
                        putJsonObject("updateCells") {
                            putJsonObject("start") {
                                put("sheetId", sheetId)
                                put("rowIndex", 0)
                                put("columnIndex", 0)
                            }
                            putJsonArray("rows") { add(rowData(listOf(formula(ReferenceStructure.BALANCE_FORMULA)))) }
                            put("fields", "userEnteredValue")
                        }
                    },
                )
            }
            SheetsClient.metadataRequests(repair.metadata, repair.metadataIds).forEach { add(it) }
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

    private fun appendMoney(money: MoneyWrite) = buildJsonObject {
        putJsonObject("appendCells") {
            put("sheetId", money.moneySheetId)
            putJsonArray("rows") { money.appends.forEach { add(rowData(listOf(number(it.serial), number(it.workouts), number(it.sum)))) } }
            put("fields", "userEnteredValue")
        }
    }

    private fun updateStatus(sheetId: Int, update: WorkoutUpdate) = buildJsonObject {
        putJsonObject("updateCells") {
            putJsonObject("start") {
                put("sheetId", sheetId)
                put("rowIndex", update.rowIndex)
                put("columnIndex", 2)
            }
            putJsonArray("rows") { add(rowData(listOf(number(DayStatusRules.toSheet(update.status))))) }
            put("fields", "userEnteredValue")
        }
    }

    /** Column B holds the duration formula (feature 007 research R1). */
    private fun appendWorkouts(sheetId: Int, appends: List<WorkoutAppend>) = buildJsonObject {
        putJsonObject("appendCells") {
            put("sheetId", sheetId)
            putJsonArray("rows") {
                appends.forEach { append ->
                    add(
                        buildJsonObject {
                            putJsonArray("values") {
                                add(buildJsonObject { put("userEnteredValue", number(append.serial)) })
                                add(buildJsonObject { put("userEnteredValue", formula(ReferenceStructure.DURATION_FORMULA)) })
                                add(buildJsonObject { put("userEnteredValue", number(DayStatusRules.toSheet(append.status))) })
                            }
                        },
                    )
                }
            }
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
    private fun formula(value: String) = buildJsonObject { put("formulaValue", value) }
    private fun number(value: Number) = buildJsonObject { put("numberValue", value) }

    private fun spreadsheets(): HttpUrl.Builder = baseUrl.newBuilder().addPathSegments("v4/spreadsheets")

    private companion object {
        const val LOG = "log"
        const val DRILLS = "drills"
        const val REC = "rec"
        const val MONEY = "money"
        const val BALANCE = "balance"
        const val WORKOUT = "workout"

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

        /** A cell as a number or a string, for values the sheet may hold either way. */
        fun List<JsonElement>.cell(i: Int): Any? =
            (getOrNull(i) as? JsonPrimitive)?.let { if (it.isString) it.content else it.doubleOrNull }

        fun List<JsonElement>.text(i: Int): String = (getOrNull(i) as? JsonPrimitive)?.content?.trim().orEmpty()
    }
}
