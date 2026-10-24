package com.workoutrec.workout

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

// Feature 007 research R3, data-model "Workout day row: rules", contracts/sheets.md "What gets written"
class DayRowsPlanTest {

    private val d1 = LocalDate.of(2026, 10, 1)
    private val d2 = LocalDate.of(2026, 10, 2)
    private val d3 = LocalDate.of(2026, 10, 3)

    private fun row(index: Int, date: LocalDate, status: DayStatus) = DayRow(index, date, status)

    @Test
    fun `waiting statuses follow the 006 table`() {
        val rows = listOf(row(1, d1, DayStatus.PAID), row(2, d2, DayStatus.PAID))
        val waiting = listOf(
            PendingStatus(d1, DayStatus.FREE, lastSeen = DayStatus.PAID), // update
            PendingStatus(d2, DayStatus.PAID, lastSeen = null), // already right
            PendingStatus(d3, DayStatus.FREE, lastSeen = null), // append
        )
        val result = DayRowsPlan.plan(rows, setOf(d1, d2, d3), logEmpty = false, waiting = waiting)
        assertEquals(listOf(RowUpdate(1, DayStatus.FREE)), result.updates)
        assertEquals(listOf(RowAppend(d3, DayStatus.FREE)), result.appends)
        assertEquals(listOf(d2), result.synced)
        assertEquals(listOf(d1, d3), result.written)
        assertEquals(emptyList<Int>(), result.deletes)
    }

    @Test
    fun `a status changed in the sheet since the phone choice is dropped`() {
        val rows = listOf(row(1, d1, DayStatus.FREE))
        val result = DayRowsPlan.plan(rows, setOf(d1), logEmpty = false, waiting = listOf(PendingStatus(d1, DayStatus.PAID, lastSeen = DayStatus.PAID)))
        assertEquals(listOf(d1), result.dropped)
        assertEquals(listOf(d1 to DayStatus.FREE), result.kept)
        assertEquals(emptyList<RowUpdate>(), result.updates)
    }

    @Test
    fun `a waiting status for a day without sets keeps waiting and adds no row`() {
        val result = DayRowsPlan.plan(emptyList(), setOf(d1), logEmpty = false, waiting = listOf(PendingStatus(d2, DayStatus.PAID, lastSeen = null)))
        assertEquals(listOf(RowAppend(d1, DayStatus.FREE)), result.appends)
        assertEquals(emptyList<LocalDate>(), result.written + result.synced + result.dropped)
    }

    @Test
    fun `a waiting status overrides the default of a new row and feeds later defaults`() {
        val result = DayRowsPlan.plan(emptyList(), setOf(d1, d2), logEmpty = false, waiting = listOf(PendingStatus(d1, DayStatus.PAID, lastSeen = null)))
        assertEquals(listOf(RowAppend(d1, DayStatus.PAID), RowAppend(d2, DayStatus.PAID)), result.appends)
    }

    @Test
    fun `rows of days without sets are deleted bottom-up, also duplicates`() {
        val rows = listOf(row(1, d1, DayStatus.PAID), row(2, d2, DayStatus.PAID), row(4, d2, DayStatus.FREE), row(5, d3, DayStatus.PAID))
        val result = DayRowsPlan.plan(rows, setOf(d1, d3), logEmpty = false, waiting = emptyList())
        assertEquals(listOf(4, 2), result.deletes)
        assertEquals(emptyList<RowAppend>(), result.appends)
    }

    @Test
    fun `an empty log deletes nothing`() {
        val result = DayRowsPlan.plan(listOf(row(1, d1, DayStatus.PAID)), emptySet(), logEmpty = true, waiting = emptyList())
        assertEquals(emptyList<Int>(), result.deletes)
    }

    @Test
    fun `a day with a row is never appended again`() {
        val rows = listOf(row(1, d1, DayStatus.PAID), row(2, d1, DayStatus.FREE))
        val result = DayRowsPlan.plan(rows, setOf(d1), logEmpty = false, waiting = emptyList())
        assertEquals(emptyList<RowAppend>(), result.appends)
        assertEquals(emptyList<Int>(), result.deletes)
    }
}
