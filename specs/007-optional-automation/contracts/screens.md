# Contract: Screens

Research: R8, R9. Strings come in English and Russian (`values`, `values-ru`).

## First run (FR-001)

The flow is ChooseAccount → Google account and consent → SetupProgress → **Today**.
`NeedsApiSetting` and `NeedsEnable` are never reached from setup.

## Today (FR-002)

The automation reminder banner (`AutomationReminder`) is removed. Nothing replaces it.

## Money tab

**Balance card**

| State | Note text (en) |
|-------|----------------|
| any | "The balance is calculated by your spreadsheet." |
| any, with sets, statuses or payments waiting | adds the line "Updates after your changes sync." |

These are removed: the `money_note_off` text, `money_note_new_payments` (replaced by the waiting
line), the "Enable automation" button in the card, and (clarification 2026-10-26) the old
script's notes `money_note_daily`, `money_note_daily_unknown` and `money_note_stopped`: the Money
tab shows no automation notes.

The Money tab has no automation entry (clarification 2026-10-26).

## Account menu (FR-012)

**Automation entry**: the item "Spreadsheet automation" (tag `account_automation`), placed above
"Share sync log". Its supporting text is the status: "Off" (NotEnabled or ScriptMissing), "On" or
"Stopped".

Tap: `SetupAction.StartAutomation`. This opens the existing guide screens for "Allow scripts" and
"Enable automation". "Continue for now" (`ContinueForNow`) and finishing both go back to the tab
the menu was opened on.

## Day notices

`WORKOUT_ROW_NOT_UPDATED` is no longer produced (R10). Its string stays so that old stored notices
still render.
