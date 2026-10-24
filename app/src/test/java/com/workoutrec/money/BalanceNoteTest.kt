package com.workoutrec.money

import com.workoutrec.automation.AutomationStatus
import org.junit.Assert.assertEquals
import org.junit.Test

// Feature 007 research R9, contracts/screens.md "Balance card"; spec FR-002, FR-005a
class BalanceNoteTest {

    /** 2026-10-21T03:00Z */
    private val lastRun = 1_792_551_600_000L

    private fun note(calculated: Boolean, automation: AutomationStatus, waiting: Boolean = false) =
        BalanceNotes.of(calculated, automation, lastRun, waiting)

    @Test
    fun aCalculatedSpreadsheetSaysSoWhateverTheAutomation() {
        AutomationStatus.entries.forEach { assertEquals(BalanceNote.Calculated, note(calculated = true, it).note) }
    }

    @Test
    fun anOldScriptStillCalculatingKeepsItsDailyNote() {
        assertEquals(BalanceNote.Daily(lastRun), note(calculated = false, AutomationStatus.On).note)
        assertEquals(BalanceNote.Stopped(lastRun), note(calculated = false, AutomationStatus.Stopped).note)
    }

    @Test
    fun neverANotUpdatedNote() {
        // FR-002: without automation the first sync converts the spreadsheet.
        assertEquals(BalanceNote.Calculated, note(calculated = false, AutomationStatus.NotEnabled).note)
        assertEquals(BalanceNote.Calculated, note(calculated = false, AutomationStatus.ScriptMissing).note)
    }

    @Test
    fun waitingChangesAddTheUpdatesAfterSyncLine() {
        assertEquals(BalanceNoteState(BalanceNote.Calculated, waiting = true), note(calculated = true, AutomationStatus.NotEnabled, waiting = true))
        assertEquals(false, note(calculated = true, AutomationStatus.On).waiting)
        assertEquals(true, note(calculated = false, AutomationStatus.On, waiting = true).waiting)
    }
}
