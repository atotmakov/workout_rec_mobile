package com.workoutrec.workout

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

// research.md R8
class SheetTimeTest {

    private val moscow = ZoneId.of("Europe/Moscow")
    private val berlin = ZoneId.of("Europe/Berlin")

    private fun epoch(local: String, zone: ZoneId) = LocalDateTime.parse(local).atZone(zone).toEpochSecond()

    @Test
    fun `serial 0 is midnight of 30 December 1899 local time`() {
        assertEquals(0.0, SheetTime.toSerial(epoch("1899-12-30T00:00:00", moscow), moscow), 0.0)
    }

    @Test
    fun `a set time converts to the sheet serial and back`() {
        val t = epoch("2026-01-12T18:30:15", moscow)
        val serial = SheetTime.toSerial(t, moscow)
        // 2026-01-12 is day 46034; 18:30:15 is 66615 s of 86400.
        assertEquals(46034 + 66615.0 / 86400.0, serial, 1e-9)
        assertEquals(t, SheetTime.toEpochSeconds(serial, moscow))
    }

    @Test
    fun `serials with float noise round to the nearest second`() {
        val t = epoch("2026-01-12T18:30:15", moscow)
        val serial = SheetTime.toSerial(t, moscow)
        assertEquals(t, SheetTime.toEpochSeconds(serial + 0.4 / 86400.0, moscow))
        assertEquals(t, SheetTime.toEpochSeconds(serial - 0.4 / 86400.0, moscow))
    }

    @Test
    fun `a date-only serial is midnight`() {
        assertEquals(epoch("2026-01-12T00:00:00", moscow), SheetTime.toEpochSeconds(46034.0, moscow))
    }

    @Test
    fun `the same local time maps to the same serial in any zone`() {
        val serialMoscow = SheetTime.toSerial(epoch("2026-01-12T18:30:15", moscow), moscow)
        val serialBerlin = SheetTime.toSerial(epoch("2026-01-12T18:30:15", berlin), berlin)
        assertEquals(serialMoscow, serialBerlin, 1e-9)
    }

    @Test
    fun `daylight saving day keeps local wall time`() {
        // Berlin switched to summer time on 2026-03-29 at 02:00 -> 03:00.
        val before = epoch("2026-03-29T01:30:00", berlin)
        val after = epoch("2026-03-29T03:30:00", berlin)
        assertEquals(1.0 / 12, SheetTime.toSerial(after, berlin) - SheetTime.toSerial(before, berlin), 1e-9)
        assertEquals(after, SheetTime.toEpochSeconds(SheetTime.toSerial(after, berlin), berlin))
    }

    @Test
    fun `workout day is the local date`() {
        assertEquals(LocalDate.of(2026, 1, 12), SheetTime.workoutDay(epoch("2026-01-12T23:59:59", moscow), moscow))
        assertEquals(LocalDate.of(2026, 1, 13), SheetTime.workoutDay(epoch("2026-01-13T00:00:00", moscow), moscow))
    }
}
