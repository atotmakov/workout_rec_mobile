package com.workoutrec.money

import com.workoutrec.automation.AutomationStatus

/** What the note under the balance says (feature 007 research R9). */
sealed interface BalanceNote {
    /** The spreadsheet calculates the balance from every change. */
    data object Calculated : BalanceNote
    /** Not converted yet: an old attached script still calculates it once a day; [lastRun] epoch millis when known. */
    data class Daily(val lastRun: Long?) : BalanceNote
    /** Not converted yet and that daily update seems stopped; the balance is from [lastRun]. */
    data class Stopped(val lastRun: Long?) : BalanceNote
}

/** [waiting]: changes on the phone the balance will include after they sync (spec FR-005a). */
data class BalanceNoteState(val note: BalanceNote, val waiting: Boolean)

object BalanceNotes {
    /**
     * [calculated]: the spreadsheet is in the calculated form. A spreadsheet without running
     * automation is converted by its first sync, so it never shows a "not updated" note (FR-002).
     */
    fun of(calculated: Boolean, automation: AutomationStatus, lastDailyRun: Long?, waiting: Boolean): BalanceNoteState {
        val note = when {
            calculated -> BalanceNote.Calculated
            automation == AutomationStatus.On -> BalanceNote.Daily(lastDailyRun)
            automation == AutomationStatus.Stopped -> BalanceNote.Stopped(lastDailyRun)
            else -> BalanceNote.Calculated
        }
        return BalanceNoteState(note, waiting)
    }
}
