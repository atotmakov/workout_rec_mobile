package com.workoutrec.workout

import java.time.LocalDate
import java.time.ZoneId

/** Sheet date-time serials (days since 1899-12-30, local time) ↔ epoch seconds (research R8). */
object SheetTime {

    fun toSerial(epochSeconds: Long, zone: ZoneId): Double = TODO()

    fun toEpochSeconds(serial: Double, zone: ZoneId): Long = TODO()

    fun workoutDay(epochSeconds: Long, zone: ZoneId): LocalDate = TODO()
}
