package com.workoutrec.workout

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

// Feature 006 research R1, R3; data-model "Domain"; spec FR-003
class DayStatusRulesTest {

    private val mon = LocalDate.of(2026, 1, 12)
    private val tue = mon.plusDays(1)
    private val wed = mon.plusDays(2)

    @Test
    fun `one as a number or as text is free, anything else is paid`() {
        assertEquals(DayStatus.FREE, DayStatusRules.fromSheet(1.0))
        assertEquals(DayStatus.FREE, DayStatusRules.fromSheet(1))
        assertEquals(DayStatus.FREE, DayStatusRules.fromSheet("1"))
        listOf(0.0, 2.0, "", "yes", "0", null).forEach { assertEquals("$it", DayStatus.PAID, DayStatusRules.fromSheet(it)) }
    }

    @Test
    fun `free is written as 1 and paid as 0`() {
        assertEquals(1, DayStatusRules.toSheet(DayStatus.FREE))
        assertEquals(0, DayStatusRules.toSheet(DayStatus.PAID))
    }

    @Test
    fun `the default is the latest earlier day's status`() {
        val known = mapOf(mon to DayStatus.FREE, tue to DayStatus.PAID)
        assertEquals(DayStatus.PAID, DayStatusRules.defaultFor(wed, known))
        assertEquals(DayStatus.FREE, DayStatusRules.defaultFor(tue, known))
    }

    @Test
    fun `later days and the day itself do not count for the default`() {
        val known = mapOf(tue to DayStatus.PAID, wed to DayStatus.PAID)
        assertEquals(DayStatus.FREE, DayStatusRules.defaultFor(tue, known))
    }

    @Test
    fun `the first workout is free`() {
        assertEquals(DayStatus.FREE, DayStatusRules.defaultFor(mon, emptyMap()))
    }

    @Test
    fun `a known status is shown, otherwise the default`() {
        val known = mapOf(mon to DayStatus.PAID, wed to DayStatus.FREE)
        assertEquals(DayStatus.FREE, DayStatusRules.shown(wed, known))
        assertEquals(DayStatus.PAID, DayStatusRules.shown(tue, known))
    }
}
