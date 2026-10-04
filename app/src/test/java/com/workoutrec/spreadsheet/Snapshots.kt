package com.workoutrec.spreadsheet

import com.workoutrec.google.SheetTable
import com.workoutrec.google.TableColumn

/** Test fixtures: a snapshot that exactly matches contracts/spreadsheet.md. */
object Snapshots {
    private const val UNSET = "COLUMN_TYPE_UNSPECIFIED"

    private fun table(name: String, vararg columns: Pair<String, String>) =
        SheetTable(name, columns.mapIndexed { index, (column, type) -> TableColumn(index, column, type) })

    val referenceTables: Map<String, List<SheetTable>> = mapOf(
        "log" to listOf(table("log", "Date" to "DATE", "Drill" to UNSET, "W" to "DOUBLE", "R" to "DOUBLE")),
        "drills" to listOf(table("drills", "mscl" to UNSET, "drill" to UNSET)),
        "money" to listOf(table("payments", "date" to "DATE", "workouts" to "DOUBLE", "sum" to "DOUBLE")),
        "workout" to listOf(table("workouts", "date" to "DATE", "duration, min" to UNSET, "work alone" to UNSET)),
    )

    fun reference(): SpreadsheetSnapshot = SpreadsheetSnapshot(
        tabTitles = listOf("log", "drills", "rec", "money", "workout", "balance"),
        headerRows = mapOf(
            "log" to listOf("Date", "Drill", "W", "R"),
            "drills" to listOf("mscl", "drill"),
            "money" to listOf("date", "workouts", "sum"),
            "workout" to listOf("date", "duration, min", "work alone"),
        ),
        recDropDownRange = "=drills!\$B:\$B",
        recA2Formula = "=FILTER(log!A:Z, log!B:B=A1)",
        recRules = listOf(
            ConditionalRule("C2:C1000", "=C2=MAX(\$C\$2:\$C)"),
            ConditionalRule("D2:D1000", "=AND(\$C2=MAX(\$C:\$C), \$D2=MAXIFS(\$D:\$D, \$C:\$C, MAX(\$C:\$C)))"),
        ),
        metadata = emptyMap(),
        metadataIds = emptyMap(),
        tables = referenceTables,
        logDropDownRange = "=drills!\$B\$2:\$B",
    )
}
