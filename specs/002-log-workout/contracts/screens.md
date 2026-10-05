# Contract: Screens and Navigation

UI contract for Compose UI tests: screens, what they show, actions and test tags. All texts are
string resources in `values/` (EN) and `values-ru/` (RU) (FR-016). The top bar with the account
avatar and the automation reminder from feature 001 stay on the main screen.

## Navigation

```text
Main (Today) ──Add exercise──▶ Exercise picker ──pick──▶ Plan sets dialog ──▶ Exercise logging
     │                                                                          ▲
     ├──tap exercise in today's list───────────────────────────────────────────┘
     ├──Workouts──▶ Past days ──tap day──▶ Day detail ──tap set──▶ Edit set dialog
     └──Enter past workout──▶ Past workout dialog ──▶ Past workout editor ──Save──▶ Main
```

## Main (Today) — `today_screen` (FR-000, FR-010, FR-013)

| Element | Tag | Behaviour |
|---------|-----|-----------|
| Today's exercises, each with its sets ("17,5 × 15"), in first-set order | `today_exercise_<index>` | tap → Exercise logging for that exercise |
| Empty state ("No sets today") | `today_empty` | |
| Add exercise button | `add_exercise` | → Exercise picker |
| Sync status line: "N sets not synced" / "Sync failing: <reason>" / "Sign in again" | `sync_status` | hidden when nothing is pending and sync is OK; "Sign in again" tap → consent (R3) |
| Notices (conflicts, workout row not updated) | `sync_notice_<id>` | dismiss button |
| Menu: Workouts, Enter past workout | `menu_workouts`, `menu_past_workout` | |

## Exercise picker — `exercise_picker` (FR-001, FR-002)

| Element | Tag | Behaviour |
|---------|-----|-----------|
| Search field | `exercise_search` | filters by part of the name, case-insensitive |
| Recent section (up to 5, last used first) | `exercise_recent` | hidden while searching |
| Muscle group headers and exercises | `exercise_item_<name>` | tap → Plan sets dialog |
| Empty list message + "Open drills tab" | `exercise_list_empty` | opens the spreadsheet in the browser (drills tab) |

## Plan sets dialog — `plan_sets` (FR-003, FR-012)

Number field with − / + (1–20), pre-filled with last time's set count (default 3); OK →
Exercise logging with that many set rows. When the picked exercise is already in today's
workout, the dialog asks how many sets to add, and the new rows are added to that exercise
(spec US4 scenario 8).

## Exercise logging — `exercise_logging` (FR-003–FR-006, FR-011, FR-012)

| Element | Tag | Behaviour |
|---------|-----|-----------|
| Exercise name, muscle group | `logging_title` | |
| Last time: date and sets, or "First time" | `last_time` | |
| Record "40 kg × 8" | `record` | hidden when no history |
| Set row *i* | `set_row_<i>` | weight and reps with − / +, number tap → keyboard; Confirm button; done rows show sync state |
| Weight / reps values | `set_weight_<i>`, `set_reps_<i>` | suggested values use the "suggested" style (gray, semantics `suggested=true`); changed or confirmed use the normal style |
| Steppers | `set_weight_minus_<i>`, `set_weight_plus_<i>`, `set_reps_minus_<i>`, `set_reps_plus_<i>` | ±0.5 kg (≥ 0), ±1 rep (≥ 1) |
| Confirm set | `set_confirm_<i>` | disabled when a value is invalid; error text below the field |
| Add set | `add_set` | adds a row pre-filled like the previous row |

Unconfirmed rows are not saved and are discarded on leaving the screen (FR-003: unfilled sets are
not logged).

## Past days — `past_days` (FR-013)

List of days newest first: date, exercises count, sets count (`past_day_<yyyy-MM-dd>`); tap →
Day detail.

## Day detail — `day_detail` (FR-013, FR-013a)

Exercises with their sets and sync state; tap a set → Edit set dialog.

## Edit set dialog — `edit_set` (FR-013, FR-013a, FR-015)

Exercise (picker from the exercise list), weight and reps with steppers; Save; Delete (asks for
confirmation, `delete_set_confirm`). The date-time is shown, not editable (FR-006).

## Past workout dialog and editor — `past_workout` (FR-006, FR-006a, US5)

Dialog: date (not in the future), start time, duration in minutes (1–600, default 60); a warning
when the day already has sets. Editor: same as Today, but for the draft; sets show the computed
times; Save (`past_workout_save`) makes the sets pending; Cancel discards the draft.

## Sign out / switch account (FR-014)

Feature 001's `SignOutDialog` (and the switch confirmation) gets a line "N sets are not synced
and will be lost" (`unsynced_warning`) when N > 0 after the sync attempt (R13).
