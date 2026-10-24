package com.workoutrec.workout

import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Feature 007 research R4: the app and the attached script pass the same day-row cases. */
class DayRowsFixtureTest {

    @Serializable
    private data class Row(val date: String, val free: Boolean)

    @Serializable
    private data class Expected(val appends: List<Row>, val removals: List<String>)

    @Serializable
    private data class Case(val name: String, val logDays: List<String>, val workoutRows: List<Row>, val expected: Expected)

    /** Unit tests run in the `app` module directory. */
    private val cases: List<Case> =
        Json.decodeFromString(File("../apps-script/tests/fixtures/day-rows.json").readText())

    /** A date-time in the sheet (an old script append) is that day. */
    private fun day(text: String): LocalDate = if ('T' in text) LocalDateTime.parse(text).toLocalDate() else LocalDate.parse(text)

    private fun status(free: Boolean) = if (free) DayStatus.FREE else DayStatus.PAID

    @Test
    fun `every shared case`() {
        assertTrue(cases.size >= 8)
        cases.forEach { case ->
            val rows = case.workoutRows.mapIndexed { i, row -> DayRow(i + 1, day(row.date), status(row.free)) }
            val days = case.logDays.mapTo(mutableSetOf(), ::day)
            val result = DayRowsPlan.plan(rows, days, logEmpty = days.isEmpty(), waiting = emptyList())
            assertEquals(case.name, case.expected.appends.map { RowAppend(day(it.date), status(it.free)) }, result.appends)
            val removed = result.deletes.map { index -> rows.single { it.rowIndex == index }.date }
            assertEquals(case.name, case.expected.removals.map(::day).sorted(), removed.sorted())
        }
    }
}
