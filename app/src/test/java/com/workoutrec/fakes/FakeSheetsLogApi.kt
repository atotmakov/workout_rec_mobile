package com.workoutrec.fakes

import com.workoutrec.google.ApiError
import com.workoutrec.google.DrillsWrite
import com.workoutrec.google.MoneyWrite
import com.workoutrec.google.SheetMoneyRow
import com.workoutrec.google.LogAndDrills
import com.workoutrec.google.LogAppend
import com.workoutrec.google.LogEdit
import com.workoutrec.google.LogSheetInfo
import com.workoutrec.google.MissingTabException
import com.workoutrec.google.SheetDrill
import com.workoutrec.google.SheetLogRow
import com.workoutrec.google.SheetsLogApi
import com.workoutrec.google.SheetWorkoutRow
import com.workoutrec.google.WorkoutWrite
import com.workoutrec.money.PaymentValues
import com.workoutrec.workout.DayStatusRules
import kotlinx.coroutines.awaitCancellation

/**
 * In-memory log and drills tabs that apply call 22 like the server (contracts/sheets-log.md):
 * deletes and edits by row index in the given order, then appends after the last row.
 */
class FakeSheetsLogApi(
    var timeZone: String = "UTC",
    var logSheetId: Int = 0,
    var drillsSheetId: Int = 1,
    var recSheetId: Int? = null,
    var moneySheetId: Int? = null,
    var balanceSheetId: Int? = null,
    var tabs: Set<String> = setOf("log", "drills"),
    val log: MutableList<Cells> = mutableListOf(),
    var drills: List<SheetDrill> = emptyList(),
    /** The rec tab's chosen exercise. */
    var recA1: String? = null,
    val money: MutableList<MoneyCells> = mutableListOf(),
    /** Feature 006: null = no `workout` tab. */
    var workoutSheetId: Int? = null,
    val workout: MutableList<WorkoutCells> = mutableListOf(),
) : SheetsLogApi {

    /** A `workout` row: date serial (null = not a date), duration, "work alone" as stored. */
    data class WorkoutCells(val serial: Double?, val duration: Any? = null, val alone: Any? = 0.0)

    val workoutReads get() = workoutReadCount
    private var workoutReadCount = 0

    data class MoneyCells(val serial: Double, val workouts: Double, val sum: Double)

    var moneyReads = 0

    /** One log row's cells; row index = list position + 1. */
    data class Cells(val serial: Double, val exercise: String, val weight: Double, val reps: Int)

    val calls = mutableListOf<String>()
    val writes = mutableListOf<Write>()

    data class Write(
        val deletes: List<Int>,
        val edits: List<LogEdit>,
        val appends: List<LogAppend>,
        val drills: DrillsWrite? = null,
        val money: MoneyWrite? = null,
        val workout: WorkoutWrite? = null,
    )

    /** Fails the next call to [call] (`info`, `read` or `write`) with [error]. */
    private val failures = mutableMapOf<String, Exception>()
    fun failNext(call: String, error: Exception) {
        failures[call] = error
    }

    /** Lets the next write reach the sheet but then fail, like a lost response. */
    var loseNextWriteResponse = false

    /** The next call to [call] never returns. */
    var hangOn: String? = null

    private suspend fun maybeFail(call: String) {
        if (hangOn == call) awaitCancellation()
        failures.remove(call)?.let { throw it }
    }

    override suspend fun logSheetInfo(spreadsheetId: String): LogSheetInfo {
        calls += "info:$spreadsheetId"
        maybeFail("info")
        listOf("log", "drills").firstOrNull { it !in tabs }?.let { throw MissingTabException(it) }
        return LogSheetInfo(timeZone, logSheetId, drillsSheetId, recSheetId, moneySheetId, balanceSheetId, workoutSheetId)
    }

    override suspend fun readLogAndDrills(spreadsheetId: String, withMoney: Boolean, withWorkout: Boolean): LogAndDrills {
        calls += "read:$spreadsheetId"
        maybeFail("read")
        return LogAndDrills(
            log = log.mapIndexed { i, c -> SheetLogRow(i + 1, c.serial, c.exercise, c.weight, c.reps) },
            drills = drills.mapIndexed { i, d -> d.copy(rowIndex = i + 1) },
            money = if (withMoney) money.mapIndexed { i, m -> SheetMoneyRow(i + 1, m.serial, m.workouts, m.sum) }.also { moneyReads++ } else emptyList(),
            workout = if (withWorkout) {
                workoutReadCount++
                workout.mapIndexedNotNull { i, w -> w.serial?.let { SheetWorkoutRow(i + 1, PaymentValues.dateOf(it), DayStatusRules.fromSheet(w.alone)) } }
            } else {
                emptyList()
            },
        )
    }

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
        calls += "write:$spreadsheetId"
        maybeFail("write")
        writes += Write(deletes, edits, appends, drillsPart, moneyPart, workoutPart)
        workoutPart?.let { w ->
            w.updates.forEach { this.workout[it.rowIndex - 1] = this.workout[it.rowIndex - 1].copy(alone = DayStatusRules.toSheet(it.status).toDouble()) }
            w.appends.forEach { this.workout += WorkoutCells(it.serial, null, DayStatusRules.toSheet(it.status).toDouble()) }
        }
        moneyPart?.appends?.forEach { this.money += MoneyCells(it.serial, it.workouts.toDouble(), it.sum) }
        val ops = deletes.map { it to null } + edits.map { it.rowIndex to it }
        ops.sortedByDescending { it.first }.forEach { (row, edit) ->
            val i = row - 1
            if (edit == null) {
                log.removeAt(i)
            } else {
                log[i] = log[i].copy(exercise = edit.exercise, weight = edit.weight, reps = edit.reps)
            }
        }
        drillsPart?.let { d ->
            val rows = this.drills.toMutableList()
            val drillOps = d.deletes.map { it to null } + d.renames.map { it.rowIndex to it }
            drillOps.sortedByDescending { it.first }.forEach { (row, rename) ->
                if (rename == null) rows.removeAt(row - 1) else rows[row - 1] = SheetDrill(rename.muscleGroup, rename.name)
            }
            d.replaces.forEach { r ->
                log.replaceAll { if (it.exercise == r.old) it.copy(exercise = r.new) else it }
                if (d.recSheetId != null && recA1 == r.old) recA1 = r.new
            }
            this.drills = rows + d.appends
        }
        appends.forEach { log += Cells(it.serial, it.exercise, it.weight, it.reps) }
        if (loseNextWriteResponse) {
            loseNextWriteResponse = false
            throw ApiError.Offline
        }
    }
}
