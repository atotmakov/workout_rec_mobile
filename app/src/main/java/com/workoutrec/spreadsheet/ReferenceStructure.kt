package com.workoutrec.spreadsheet

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

/** The reference structure from contracts/spreadsheet.md; the single source for its values. */
object ReferenceStructure {
    const val TITLE = "workout_rec_database_"
    const val LOCALE = "ru_RU"
    const val STRUCTURE_VERSION = "1"
    val TABS = listOf("log", "drills", "rec", "money", "workout", "balance")
    val HEADERS: Map<String, List<String>> = mapOf(
        "log" to listOf("Date", "Drill", "W", "R"),
        "drills" to listOf("mscl", "drill"),
        "money" to listOf("date", "workouts", "sum"),
        "workout" to listOf("date", "duration, min", "work alone"),
    )
    const val REC_FORMULA = "=FILTER(log!A:Z, log!B:B=A1)"
    const val REC_NOTE = "FILTER(log!A:Z, log!B:B=A1)"
    const val REC_DROPDOWN_RANGE = "=drills!\$B:\$B"
    val REC_RULES = listOf(
        ConditionalRule("C2:C1000", "=C2=MAX(\$C\$2:\$C)"),
        ConditionalRule("D2:D1000", "=AND(\$C2=MAX(\$C:\$C), \$D2=MAXIFS(\$D:\$D, \$C:\$C, MAX(\$C:\$C)))"),
    )

    /** Call 5b ranges per tab, in request order. */
    val STRUCTURE_RANGES: Map<String, String> = linkedMapOf(
        "log" to "log!1:1",
        "drills" to "drills!1:1",
        "money" to "money!1:1",
        "workout" to "workout!1:1",
        "rec" to "rec!A1:D2",
    )

    fun createRequest(timeZone: String): JsonObject = TODO()
    fun minimalCreateRequest(timeZone: String): JsonObject = TODO()
    fun completionRequests(): JsonArray = TODO()
}
