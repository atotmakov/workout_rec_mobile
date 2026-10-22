package com.workoutrec.money

import com.workoutrec.automation.AutomationStatus
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** What the note under the balance says (feature 004 FR-009a, research R7). */
sealed interface BalanceNote {
    /** Calculated once a day; [lastRun] epoch millis when known. */
    data class Calculated(val lastRun: Long?) : BalanceNote
    /** The daily update seems stopped; the balance is from [lastRun]. */
    data class Stopped(val lastRun: Long?) : BalanceNote
    /** Daily automation is off: the balance is not updated. */
    data object Off : BalanceNote
}

/** [newPaymentsWaiting]: a payment the next daily calculation will count (spec US4 scenario 7). */
data class BalanceNoteState(val note: BalanceNote, val newPaymentsWaiting: Boolean)

object BalanceNotes {
    fun of(automation: AutomationStatus, lastDailyRun: Long?, pendingPayment: Boolean, latestPayment: LocalDate?, zone: ZoneId): BalanceNoteState {
        val note = when (automation) {
            AutomationStatus.On -> BalanceNote.Calculated(lastDailyRun)
            AutomationStatus.Stopped -> BalanceNote.Stopped(lastDailyRun)
            AutomationStatus.NotEnabled, AutomationStatus.ScriptMissing -> BalanceNote.Off
        }
        val lastRunDay = lastDailyRun?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
        val newer = latestPayment != null && lastRunDay != null && latestPayment.isAfter(lastRunDay)
        return BalanceNoteState(note, newPaymentsWaiting = note != BalanceNote.Off && (pendingPayment || newer))
    }
}
