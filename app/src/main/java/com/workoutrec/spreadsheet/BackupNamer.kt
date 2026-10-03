package com.workoutrec.spreadsheet

import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Backup name for a rewrite (data-model.md "Naming rules"). */
object BackupNamer {

    /** `workout_rec_database_backup_<yyyy-MM-dd>`, then `_2`, `_3`, … while taken. */
    suspend fun choose(date: LocalDate, isTaken: suspend (String) -> Boolean): String {
        val base = "${ReferenceStructure.TITLE}backup_${date.format(DateTimeFormatter.ISO_LOCAL_DATE)}"
        if (!isTaken(base)) return base
        var suffix = 2
        while (isTaken("${base}_$suffix")) suffix++
        return "${base}_$suffix"
    }
}
