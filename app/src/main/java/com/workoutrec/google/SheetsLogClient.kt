package com.workoutrec.google

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

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

    override suspend fun logSheetInfo(spreadsheetId: String): LogSheetInfo = TODO()

    override suspend fun readLogAndDrills(spreadsheetId: String): LogAndDrills = TODO()

    override suspend fun writeLog(
        spreadsheetId: String,
        logSheetId: Int,
        deletes: List<Int>,
        edits: List<LogEdit>,
        appends: List<LogAppend>,
    ): Unit = TODO()
}
