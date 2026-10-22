package com.workoutrec.money

import com.workoutrec.automation.AutomationStatus
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test

// Feature 004 FR-009a, research R7
class BalanceNoteTest {

    private val zone = ZoneOffset.UTC
    /** 2026-10-21T03:00Z */
    private val lastRun = 1_792_551_600_000L

    private fun note(automation: AutomationStatus, pendingPayment: Boolean = false, latestPayment: LocalDate? = null, run: Long? = lastRun) =
        BalanceNotes.of(automation, run, pendingPayment, latestPayment, zone)

    @Test
    fun dailyCalculationWithItsTime() =
        assertEquals(BalanceNoteState(BalanceNote.Calculated(lastRun), newPaymentsWaiting = false), note(AutomationStatus.On))

    @Test
    fun stoppedAutomationKeepsTheLastTime() =
        assertEquals(BalanceNote.Stopped(lastRun), note(AutomationStatus.Stopped).note)

    @Test
    fun automationOffOrScriptMissingMeansNotUpdated() {
        assertEquals(BalanceNote.Off, note(AutomationStatus.NotEnabled).note)
        assertEquals(BalanceNote.Off, note(AutomationStatus.ScriptMissing, run = null).note)
    }

    @Test
    fun aWaitingPaymentIsCountedAtTheNextCalculation() =
        assertEquals(true, note(AutomationStatus.On, pendingPayment = true).newPaymentsWaiting)

    @Test
    fun aPaymentDatedAfterTheLastCalculationIsToo() {
        assertEquals(true, note(AutomationStatus.On, latestPayment = LocalDate.of(2026, 10, 22)).newPaymentsWaiting)
        assertEquals(false, note(AutomationStatus.On, latestPayment = LocalDate.of(2026, 10, 21)).newPaymentsWaiting)
    }

    @Test
    fun noLineAboutNewPaymentsWhenTheBalanceIsNotUpdatedAnyway() =
        assertEquals(false, note(AutomationStatus.NotEnabled, pendingPayment = true).newPaymentsWaiting)
}
