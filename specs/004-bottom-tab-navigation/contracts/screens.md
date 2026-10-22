# Contract: Tabs and Screens

UI contract for Compose UI tests. Extends feature 002's
[screens.md](../../002-log-workout/contracts/screens.md); screens not listed here are unchanged.
All texts in `values/` (EN) and `values-ru/` (RU); light and dark themes (FR-011).

## Navigation

```text
Bottom bar: [Today] [History] [Exercises] [Money]          (bar_tab_<today|history|exercises|money>)

Today ──Add exercise──▶ Picker ──▶ Plan sets ──▶ Logging*
History ──tap day──▶ Day detail ──tap set──▶ Edit set
History ──Enter a past workout──▶ Past workout dialog ──▶ Editor* ──Save──▶ History
Exercises ──tap──▶ Exercise detail ──Log today──▶ (Today tab) Plan sets ──▶ Logging*
Exercises ──Add──▶ Exercise dialog            Exercise detail ──Rename──▶ Exercise dialog
Money ──Add payment──▶ Payment dialog
* bar hidden
```

## Bottom bar — `bottom_bar` (FR-001–FR-005)

| Element | Tag | Behaviour |
|---------|-----|-----------|
| Tab item ×4, icon + label | `bar_tab_today`, `bar_tab_history`, `bar_tab_exercises`, `bar_tab_money` | selected = `selected` semantics; tap → tab (state kept); tap on selected → its start screen |
| Bar | `bottom_bar` | absent on logging, past workout editor, setup |

Back on a tab's start screen (not Today) → Today; on Today → app closes.

## Today — `today_screen`

As in 002 without the "Workouts" and "Enter a past workout" links (`menu_workouts`,
`menu_past_workout` removed). Top bar: sync icon, avatar.

## History — `past_days` (FR-006)

The 002 past days list as the tab's start screen. Top bar action "Enter a past workout"
(`history_past_workout`); empty state (`past_days_empty`) with the same button. Day detail and
edit set as in 002.

## Exercises — `exercises_screen` (FR-007, FR-008)

| Element | Tag | Behaviour |
|---------|-----|-----------|
| Search field | `exercises_search` | filters by part of the name, case-insensitive |
| Group header + exercise rows | `exercises_item_<name>` | tap → Exercise detail; a waiting exercise shows a small cloud-upload mark |
| Add button (FAB) | `exercises_add` | → Exercise dialog (add) |
| Pull to refresh | `exercises_refresh` | runs a sync (re-reads drills) |
| Empty state | `exercises_empty` | "No exercises yet" + Add |

### Exercise detail — `exercise_detail`

Name, muscle group; last time and record (002 `last_time`, `record`); buttons "Log today"
(`exercise_log_today`), "Rename" (`exercise_rename`), "Delete" (`exercise_delete`, only when
deletable, confirmation `exercise_delete_confirm`). Not deletable → note `exercise_in_use_note`.

### Exercise dialog — `exercise_dialog` (add and rename)

Muscle group field with suggestions of existing groups (`exercise_group`), name field
(`exercise_name`), Save (`exercise_save`, disabled while invalid). Errors under the name:
empty, too long, "This exercise already exists".

## Money — `money_screen` (FR-009, FR-009a, FR-009b, FR-010)

| Element | Tag | Behaviour |
|---------|-----|-----------|
| Balance card: number of paid workouts left; negative → "N workouts owed" in the error colour | `money_balance` | |
| Balance note (research R7) | `money_balance_note` | link "Enable automation" when off or stopped |
| Downloaded at ("Updated 14:05" / offline) | `money_downloaded` | |
| Totals: paid workouts, paid sum | `money_totals` | |
| Payment rows, newest first: date, workouts, sum; waiting ones marked | `money_payment_<index>` | |
| Add payment (FAB) | `money_add` | → Payment dialog |
| Pull to refresh | `money_refresh` | call 23 |
| Empty / error states | `money_empty`, `money_error` | empty: explains the money tab; error: "Money tab not found" or offline |

### Payment dialog — `payment_dialog`

Date (`payment_date`, date picker, future dates disabled, default today), workouts
(`payment_workouts`, − / + and keyboard, default last payment's value or 1), sum (`payment_sum`,
decimal keyboard, default last payment's sum or empty), Save (`payment_save`, disabled while
invalid).

## Sync icon and sign out (research R10)

The count and the sign-out warning count all waiting changes: sets, exercise changes, payments.
New notices: `EXERCISE_CHANGE_DROPPED` ("{name} was changed in the spreadsheet; your change was
not applied"), `EXERCISE_IN_USE` ("{name} has sets in the log and was not deleted"), shown on
Today like 002's notices.
