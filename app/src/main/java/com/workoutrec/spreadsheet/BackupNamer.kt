package com.workoutrec.spreadsheet

import java.time.LocalDate

object BackupNamer {
    suspend fun choose(date: LocalDate, isTaken: suspend (String) -> Boolean): String = TODO()
}
