package com.workoutrec.money

/**
 * The note under the balance (feature 007 research R9): the spreadsheet calculates it, and
 * [waiting] adds that changes on the phone count once they sync (spec FR-005a). The Money tab shows
 * no automation notes (clarification 2026-10-26).
 */
data class BalanceNoteState(val waiting: Boolean)
