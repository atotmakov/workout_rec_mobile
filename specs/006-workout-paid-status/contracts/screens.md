# Contract: Screens

Changes to feature 004's screens ([004 screens.md](../../004-bottom-tab-navigation/contracts/screens.md)).
Research: [R8](../research.md).

## Day status control

A Material 3 single-choice segmented button with two options, **Paid** and **Free**, in that
order; the selected option is the date's shown status (data-model `DayStatusRules.shown`). One
tap selects the other option and saves at once (no confirm). Each option's test tag is the
control's tag plus `_paid` / `_free`.

| Screen | Place | Tag | Date |
|--------|-------|-----|------|
| Today | under the top bar, above today's exercises and the notices | `today_day_status` | today in the spreadsheet's time zone |
| Day details (History) | under the top bar, above the exercises | `day_status` | the opened day |
| Past-workout dialog | under the duration field | `past_workout_status` | the dialog's date; follows the date field when it holds a valid date, unless the user already picked an option |

## History list

Each day's line ends with a short label `Paid` / `Free` (tag `day_status_label`), in the
secondary text colour; a pending status shows the existing "not in the spreadsheet yet" mark next
to it (as for pending sets).

## Strings

| Key | en | ru |
|-----|----|----|
| `day_status_paid` | Paid | Платная |
| `day_status_free` | Free | Бесплатная |
| `day_status_title` (control's accessibility label) | Workout | Тренировка |
