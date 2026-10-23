package com.workoutrec.workout

import java.time.LocalDate

/** Feature 006: a paid workout uses a prepaid one from the money tab; a free one is done alone. */
enum class DayStatus { PAID, FREE }

/** A date's status as the app knows it; [pending] = chosen on the phone, not in the sheet yet. */
data class KnownStatus(val date: LocalDate, val status: DayStatus, val pending: Boolean)

/** A status waiting for sync; [lastSeen] is the sheet's value when it was chosen (null = no row then). */
data class PendingStatus(val date: LocalDate, val status: DayStatus, val lastSeen: DayStatus?)

/** The `workout` tab's "work alone" column and the default (research R1, R3). */
object DayStatusRules {

    /** `1` or `"1"` is free; anything else is paid, as the nightly balance counts. */
    fun fromSheet(cell: Any?): DayStatus = when (cell) {
        is Number -> if (cell.toDouble() == 1.0) DayStatus.FREE else DayStatus.PAID
        is String -> if (cell.trim() == "1") DayStatus.FREE else DayStatus.PAID
        else -> DayStatus.PAID
    }

    fun toSheet(status: DayStatus): Int = if (status == DayStatus.FREE) 1 else 0

    /** The latest earlier date's status; the first workout is free (spec FR-003). */
    fun defaultFor(date: LocalDate, known: Map<LocalDate, DayStatus>): DayStatus =
        known.filterKeys { it < date }.maxByOrNull { it.key }?.value ?: DayStatus.FREE

    fun shown(date: LocalDate, known: Map<LocalDate, DayStatus>): DayStatus = known[date] ?: defaultFor(date, known)
}
