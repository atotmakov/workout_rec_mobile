package com.workoutrec.workout

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.roundToLong

/** Sheet date-time serials (days since 1899-12-30, local time) ↔ epoch seconds (research R8). */
object SheetTime {

    private val EPOCH = LocalDateTime.of(1899, 12, 30, 0, 0)
    private const val SECONDS_PER_DAY = 86_400.0

    fun toSerial(epochSeconds: Long, zone: ZoneId): Double {
        val local = LocalDateTime.ofInstant(Instant.ofEpochSecond(epochSeconds), zone)
        return Duration.between(EPOCH, local).seconds / SECONDS_PER_DAY
    }

    /** Rounds to the nearest second; a date-only serial is midnight. */
    fun toEpochSeconds(serial: Double, zone: ZoneId): Long {
        val local = EPOCH.plusSeconds((serial * SECONDS_PER_DAY).roundToLong())
        return local.atZone(zone).toEpochSecond()
    }

    fun workoutDay(epochSeconds: Long, zone: ZoneId): LocalDate =
        Instant.ofEpochSecond(epochSeconds).atZone(zone).toLocalDate()
}
