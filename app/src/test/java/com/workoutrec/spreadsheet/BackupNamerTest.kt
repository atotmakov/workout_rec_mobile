package com.workoutrec.spreadsheet

import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

// data-model.md "Naming rules", spec edge case "two rewrites in one day"
class BackupNamerTest {

    private val date = LocalDate.of(2026, 10, 3)

    @Test
    fun `uses the date when free`() = runTest {
        assertEquals("workout_rec_database_backup_2026-10-03", BackupNamer.choose(date) { false })
    }

    @Test
    fun `appends _2 then _3 when taken`() = runTest {
        val taken = setOf("workout_rec_database_backup_2026-10-03")
        assertEquals("workout_rec_database_backup_2026-10-03_2", BackupNamer.choose(date) { it in taken })
        val takenTwice = taken + "workout_rec_database_backup_2026-10-03_2"
        assertEquals("workout_rec_database_backup_2026-10-03_3", BackupNamer.choose(date) { it in takenTwice })
    }

    @Test
    fun `pads month and day`() = runTest {
        assertEquals("workout_rec_database_backup_2027-01-05", BackupNamer.choose(LocalDate.of(2027, 1, 5)) { false })
    }
}
