package com.workoutrec.spreadsheet

/** Test fixtures: a snapshot that exactly matches contracts/spreadsheet.md. */
object Snapshots {
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
    )
}
