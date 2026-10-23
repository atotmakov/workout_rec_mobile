# Feature Specification: Paid or Free Workouts

**Feature Branch**: `006-workout-paid-status`

**Created**: 2026-10-23

**Status**: Draft

**Input**: User description: "I would like to mark each workout paid or free (by default use previous workout on first workout it is free), paid status should be shown on "history" tab, on "today" tab there should be switch paid/free, payment should be stored in "workout" tab of original spreadsheet."

## Context

The spreadsheet already keeps one row per workout day in its `workout` tab: date, duration in
minutes, and "work alone". The balance (paid workouts left, shown on the Money tab since feature
004) is the total of paid workouts in the `money` tab minus every workout day whose "work alone"
is not `1`. In this feature's words:

| This feature | `workout` tab, "work alone" | Effect on the balance |
|--------------|-----------------------------|-----------------------|
| **Paid** workout (with the coach, uses a prepaid workout) | anything but `1` (today `0`) | −1 |
| **Free** workout (alone) | `1` | none |

Today the rows are added by the spreadsheet's nightly automation, always with `0`, so every
workout counts as paid unless the user opens the spreadsheet and types `1`. The app neither shows
nor changes this value.

## Clarifications

### Session 2026-10-23

- Q: Can the status of past days be changed in the app? → A: Yes (option C): from a past day's
  details in History, and when entering a past workout with "Enter a past workout"; Today keeps
  its control for today.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Mark today's workout paid or free (Priority: P1)

On the Today tab the user sees whether today's workout is paid or free and can flip it with one
tap. The choice reaches the spreadsheet's `workout` tab for today's date, so the nightly
balance counts the workout correctly without editing the spreadsheet by hand.

**Why this priority**: This is the core of the request; without it every workout keeps counting
as paid and the balance is wrong whenever the user trains alone.

**Independent Test**: Log a set today, set the control to Free, wait for sync, and check that
today's row in the `workout` tab has "work alone" = `1`; set it to Paid and check it is no longer
`1`.

**Acceptance Scenarios**:

1. **Given** the user opens Today, **When** the tab is shown, **Then** a Paid/Free control shows
   today's status, set to the default (User Story 2) until the user changes it.
2. **Given** today's workout has sets, **When** the user changes the control, **Then** the new
   status is kept on the phone at once, the control shows it, and sync writes it to today's row in
   the `workout` tab.
3. **Given** the phone is offline, **When** the user changes the control, **Then** the change is
   kept and shown, counted as waiting to be synced like other unsent changes, and written when the
   phone is back online.
4. **Given** today has no sets yet, **When** the user sets the control, **Then** the choice is kept
   and applies to today's workout once the first set is logged; no `workout` row is written for a
   day without sets.
5. **Given** the user changes the control several times before sync, **When** sync runs, **Then**
   only the last status ends up in the spreadsheet.

---

### User Story 2 - Sensible default from the previous workout (Priority: P1)

A new workout starts with the status of the user's previous workout, so a user who always trains
with the coach (or always alone) never has to touch the control. The very first workout is free.

**Why this priority**: The default decides the balance for every workout the user doesn't
change; it ships together with User Story 1.

**Independent Test**: Mark yesterday's workout Paid, open Today the next day: the control shows
Paid. With no earlier workouts at all, the control shows Free.

**Acceptance Scenarios**:

1. **Given** the most recent earlier workout day is paid, **When** a new day's workout starts,
   **Then** its status is Paid.
2. **Given** the most recent earlier workout day is free, **When** a new day's workout starts,
   **Then** its status is Free.
3. **Given** there is no earlier workout day (on the phone or in the spreadsheet), **When** the
   first workout starts, **Then** its status is Free.
4. **Given** the previous workout's status is changed later (e.g., in the spreadsheet), **When**
   today's status was already set or used, **Then** today's status does not change.
5. **Given** a workout day was logged without the app (sets typed in the spreadsheet), **When**
   the nightly automation adds its `workout` row, **Then** the row uses the same default rule
   (previous workout day's status, Free if none) instead of always Paid.

---

### User Story 3 - See paid or free in History (Priority: P2)

The History tab shows for each workout day whether it was paid or free, so the user can check
the days that used up prepaid workouts and compare them with the balance.

**Why this priority**: Useful for checking the balance, but the balance is correct without it.
Changing a past day is User Story 4.

**Independent Test**: With some paid and some free days in the `workout` tab, open History and
compare each day's mark with the spreadsheet.

**Acceptance Scenarios**:

1. **Given** workout days exist, **When** the user opens History, **Then** each day shows Paid or
   Free, readable without opening the day.
2. **Given** a day's status was changed in the spreadsheet, **When** the app next syncs, **Then**
   History shows the spreadsheet's status.
3. **Given** a day's status is waiting to be synced, **When** the user opens History, **Then** the
   phone's status is shown with the usual "not in the spreadsheet yet" mark.
4. **Given** a day has no status anywhere yet (e.g., the nightly automation hasn't added its row),
   **When** History shows it, **Then** the status that will be used (the default) is shown.
5. **Given** a day is opened, **When** its details are shown, **Then** the status is shown there
   too.

---

### User Story 4 - Fix a past day, or set it when entering a past workout (Priority: P3)

The user can correct a past day's status from that day's details in History, and choose Paid or
Free while entering a past workout, instead of editing the spreadsheet.

**Why this priority**: Mistakes and forgotten days can be fixed in the spreadsheet already; doing
it on the phone is a convenience on top of User Stories 1–3.

**Independent Test**: Open a past paid day in History, switch it to Free, wait for sync, and check
that day's `workout` row has "work alone" = `1`. Enter a past workout, choose Paid, save, and
check its day in History and in the spreadsheet.

**Acceptance Scenarios**:

1. **Given** a past day's details are open, **When** the user changes its Paid/Free control,
   **Then** the new status is kept on the phone at once, shown in the details and in the History
   list, and written by sync to that day's `workout` row (added if missing), offline included.
2. **Given** the user enters a past workout, **When** the entry form is shown, **Then** it has a
   Paid/Free choice set to the default for that date (the status of the workout day before it,
   Free if none), and the chosen status is saved with the workout.
3. **Given** the past workout's date already has a workout day, **When** the form is shown,
   **Then** the choice shows that day's status, and changing it changes that day's status.
4. **Given** a past day's status changes, **When** later days already have a status, **Then** their
   statuses do not change (the default is only taken when a day's workout starts).
5. **Given** a status was changed on Today or in a day's details, **When** the other place shows
   that date, **Then** it shows the same status (one stored status per date).

---

### Edge Cases

- **Workout row not yet in the spreadsheet**: the app writes today's status before the nightly
  automation has added the day's row; the automation must then keep that status and still fill
  in the duration, and must not add a second row for the same day.
- **Several rows for one date** (typed by hand, or the app and the nightly run adding the same
  day's row within the same few minutes at 03:00): the app treats the first row for that date as
  the day's row and changes only that row.
- **"Work alone" values other than `0`/`1`** (empty, text): shown as Paid, matching how the balance
  counts them; the app writes only `0` or `1`.
- **Day crosses midnight**: the status belongs to the day the workout's first set was logged on,
  the same day the `workout` tab uses.
- **Past workout entered for a date with no earlier workout**: Free, as for the very first
  workout.
- **Past day without a `workout` row** (e.g., before the automation was enabled): changing its
  status adds the row with the date and status; the duration stays empty unless the nightly
  automation fills it in.
- **Sign out / switch account**: the phone's statuses go with the other workout data of that
  account; the new account's statuses come from its own spreadsheet.
- **`workout` tab missing or renamed**: sets still sync; the status stays waiting on the phone and
  the failure is reported (Principle V), as for a missing `money` tab.
- **Balance**: the balance stays the spreadsheet's nightly value (feature 004); a changed status,
  today's or a past day's, shows up in it after the next nightly calculation, which the Money
  note already explains.
- **Script not updated yet** (offline, or the Apps Script API setting off): rows the app adds have
  no duration until the script is updated; the app retries the update on every start.
- **Status changed in the spreadsheet after a phone change made offline**: the spreadsheet's value
  is kept and the user sees a notice (the same rule as for edited sets).

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Every workout day MUST have a status, Paid or Free.
- **FR-002**: Today MUST show a Paid/Free control for today's workout that the user can change
  with one tap.
- **FR-003**: A new workout day's default status MUST be the status of the most recent earlier
  workout day; if there is none, Free.
- **FR-004**: A status chosen on the phone MUST be kept on the phone at once, work offline, and be
  written by sync to that day's row in the spreadsheet's `workout` tab, "work alone" = `1` for Free
  and `0` for Paid.
- **FR-005**: If the day's `workout` row does not exist yet, sync MUST add it with the date and
  status; the nightly automation MUST then fill in its duration (once the spreadsheet's script is
  updated to this version) without changing the status or adding a second row.
- **FR-006**: The nightly automation MUST give rows it adds for days without a status from the app
  the default from FR-003 instead of always Paid.
- **FR-007**: History MUST show each workout day's status in the list and in the day's details,
  using the phone's unsynced status when there is one, otherwise the spreadsheet's.
- **FR-008**: Statuses changed in the spreadsheet MUST reach the app on the next sync and replace
  the phone's value unless the phone has an unsynced change for that day.
- **FR-009**: Unsynced status changes MUST count in the existing "not in the spreadsheet yet"
  indicators.
- **FR-010**: Only the last status chosen before a sync MUST be written; the spreadsheet MUST end
  with one value per day.
- **FR-011**: Failures to read or write statuses MUST be reported through the app's monitoring
  (constitution Principle V) without the spreadsheet's contents.
- **FR-012**: Existing `workout` rows MUST keep their values until the user changes them; this
  feature changes no other tab, header or the balance formula.
- **FR-013**: A past day's details in History MUST offer the same Paid/Free control, with the
  same storage, offline and sync behavior as Today's (FR-004, FR-005, FR-009, FR-010).
- **FR-014**: "Enter a past workout" MUST offer a Paid/Free choice, defaulting to the status of
  the workout day before the chosen date (Free if none), or to the date's existing status if it
  already has a workout day; the choice is saved as that day's status.
- **FR-015**: Changing a day's status MUST NOT change any other day's status.

### Key Entities

- **Workout day**: a date with at least one logged set; has a status (Paid/Free), a duration (from
  the spreadsheet) and whether its status is waiting to be synced.
- **Workout status**: Paid or Free; stored in the spreadsheet as "work alone" (`0`/`1`) on the
  day's `workout` row, and on the phone until synced.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: The user can mark today's workout paid or free with 1 tap from Today, and a past
  day with 1 tap from its details.
- **SC-002**: In 100% of checks, a status set on the phone (today or a past day) matches the spreadsheet's `workout` row
  for that day within 1 minute of the phone being online.
- **SC-003**: A user who never touches the control gets the previous workout's status on every new
  day (Free on the very first day), in 100% of checks.
- **SC-004**: History shows the same status as the spreadsheet for 100% of synced days.
- **SC-005**: After the nightly calculation, the balance matches the payments minus the paid
  days shown in History.
- **SC-006**: No workout day ends up with two `workout` rows because of this feature, except when
  the app adds a day's row during the nightly run that adds it too (a few minutes at 03:00); then
  the app keeps using the first row.

## Assumptions

- "Paid" means the workout uses a prepaid workout from the `money` tab (with the coach); "free"
  means working alone, matching the existing "work alone" column, so the balance logic stays the
  same.
- One status per day: the spreadsheet has one `workout` row per day, and several sessions in a day
  are one workout.
- The previous workout day is the most recent earlier day with a status on the phone or in the
  spreadsheet, whichever is newer.
- The control is shown on Today all day, even before the first set, so the user can set it at the
  start of a session.
- Existing spreadsheets need no structure change; the attached automation is updated the same
  way earlier features updated it.
- The balance stays the nightly value from the spreadsheet (feature 004 FR-009a); showing an
  instant estimate is out of scope.
