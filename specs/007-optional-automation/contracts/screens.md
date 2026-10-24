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
| `calculated` | "Calculated by your spreadsheet." |
| not calculated, automation On | as today: "Calculated daily, last at {time}" |
| not calculated, Stopped | as today: the stopped text |
| not calculated, otherwise | "Calculated by your spreadsheet." (the first sync converts it) |
| any, with sets, statuses or payments waiting | adds the line "Updates after your changes sync." |

These are removed: the `money_note_off` text, `money_note_new_payments` (replaced by the waiting
line) and the "Enable automation" button in the card.

**Automation entry** (`AutomationEntry`, tag `automation_entry`)

This is a list item below the balance card, shown in every state, including errors.

- Headline: "Spreadsheet automation".
- Supporting text when Off (NotEnabled or ScriptMissing): "Off. Turn it on to add new days while
  the app is closed, fill in the time when you type a set in the spreadsheet, and make the rec tab
  follow the selected exercise."
- Supporting text when On: "On. Runs every night." When Stopped, the existing stopped wording is
  used.
- Tap: `SetupAction.StartAutomation`. This opens the existing guide screens for "Allow scripts"
  and "Enable automation". "Not now" (`ContinueForNow`) and finishing both go back to the tab the
  entry was opened from.

## Account menu (FR-012)

There is a new item, "Spreadsheet automation", placed above "Share sync log". It shows the same
status in its supporting text and has the same tap action.

## Day notices

`WORKOUT_ROW_NOT_UPDATED` is no longer produced (R10). Its string stays so that old stored notices
still render.
