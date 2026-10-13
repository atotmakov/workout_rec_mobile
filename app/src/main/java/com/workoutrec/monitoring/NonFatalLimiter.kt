package com.workoutrec.monitoring

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** FR-007, data-model.md "NonFatalLimiter": in memory, per process. */
class NonFatalLimiter(
    private val now: () -> Long = System::currentTimeMillis,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
) {
    private val lastSent = mutableMapOf<NonFatalIssue, Long>()
    private var day: LocalDate? = null
    private var sentToday = 0

    /** True when an event of [issue] may be sent now; counts it as sent. */
    @Synchronized
    fun allow(issue: NonFatalIssue): Boolean {
        val at = now()
        val today = Instant.ofEpochMilli(at).atZone(zone()).toLocalDate()
        if (today != day) {
            day = today
            sentToday = 0
        }
        val last = lastSent[issue]
        if (last != null && at - last < MIN_GAP_MILLIS) return false
        if (sentToday >= MAX_PER_DAY) return false
        lastSent[issue] = at
        sentToday++
        return true
    }

    companion object {
        const val MIN_GAP_MILLIS = 30 * 60_000L
        const val MAX_PER_DAY = 20
    }
}
