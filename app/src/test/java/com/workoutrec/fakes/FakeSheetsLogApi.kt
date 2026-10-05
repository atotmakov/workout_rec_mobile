package com.workoutrec.fakes

import com.workoutrec.google.ApiError
import com.workoutrec.google.LogAndDrills
import com.workoutrec.google.LogAppend
import com.workoutrec.google.LogEdit
import com.workoutrec.google.LogSheetInfo
import com.workoutrec.google.MissingTabException
import com.workoutrec.google.SheetDrill
import com.workoutrec.google.SheetLogRow
import com.workoutrec.google.SheetsLogApi

/**
 * In-memory log and drills tabs that apply call 22 like the server (contracts/sheets-log.md):
 * deletes and edits by row index in the given order, then appends after the last row.
 */
class FakeSheetsLogApi(
    var timeZone: String = "UTC",
    var logSheetId: Int = 0,
    var tabs: Set<String> = setOf("log", "drills"),
    val log: MutableList<Cells> = mutableListOf(),
    var drills: List<SheetDrill> = emptyList(),
) : SheetsLogApi {

    /** One log row's cells; row index = list position + 1. */
    data class Cells(val serial: Double, val exercise: String, val weight: Double, val reps: Int)

    val calls = mutableListOf<String>()
    val writes = mutableListOf<Write>()

    data class Write(val deletes: List<Int>, val edits: List<LogEdit>, val appends: List<LogAppend>)

    /** Fails the next call to [call] (`info`, `read` or `write`) with [error]. */
    private val failures = mutableMapOf<String, Exception>()
    fun failNext(call: String, error: Exception) {
        failures[call] = error
    }

    /** Lets the next write reach the sheet but then fail, like a lost response. */
    var loseNextWriteResponse = false

    private fun maybeFail(call: String) {
        failures.remove(call)?.let { throw it }
    }

    override suspend fun logSheetInfo(spreadsheetId: String): LogSheetInfo {
        calls += "info:$spreadsheetId"
        maybeFail("info")
        listOf("log", "drills").firstOrNull { it !in tabs }?.let { throw MissingTabException(it) }
        return LogSheetInfo(timeZone, logSheetId)
    }

    override suspend fun readLogAndDrills(spreadsheetId: String): LogAndDrills {
        calls += "read:$spreadsheetId"
        maybeFail("read")
        return LogAndDrills(
            log = log.mapIndexed { i, c -> SheetLogRow(i + 1, c.serial, c.exercise, c.weight, c.reps) },
            drills = drills,
        )
    }

    override suspend fun writeLog(
        spreadsheetId: String,
        logSheetId: Int,
        deletes: List<Int>,
        edits: List<LogEdit>,
        appends: List<LogAppend>,
    ) {
        if (deletes.isEmpty() && edits.isEmpty() && appends.isEmpty()) return
        calls += "write:$spreadsheetId"
        maybeFail("write")
        writes += Write(deletes, edits, appends)
        val ops = deletes.map { it to null } + edits.map { it.rowIndex to it }
        ops.sortedByDescending { it.first }.forEach { (row, edit) ->
            val i = row - 1
            if (edit == null) {
                log.removeAt(i)
            } else {
                log[i] = log[i].copy(exercise = edit.exercise, weight = edit.weight, reps = edit.reps)
            }
        }
        appends.forEach { log += Cells(it.serial, it.exercise, it.weight, it.reps) }
        if (loseNextWriteResponse) {
            loseNextWriteResponse = false
            throw ApiError.Offline
        }
    }
}
