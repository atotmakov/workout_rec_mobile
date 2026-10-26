# Feature Specification: Optional Spreadsheet Automation

**Feature Branch**: `007-optional-automation`

**Created**: 2026-10-24

**Status**: Draft

**Input**: User description: "Make spreadsheet automation optional. A newcomer goes from choosing a Google account straight to Today, with no "allow scripts" or "enable automation" steps and no reminder. The workout tab (one row per day: date, duration, paid/free) and the balance stay up to date without the nightly script: the balance and each day's duration are calculated by the spreadsheet itself (formulas), so they update immediately on any change, from the app or typed in the spreadsheet; the app adds the row for a new day (date and paid/free) on sync, also for days typed in the spreadsheet. Existing spreadsheets are migrated once to this form. Allowing scripts and enabling automation move to an optional place (Money tab and account menu) for users who want new-day rows added while the app is closed and the spreadsheet conveniences (auto-filled time, rec tab follow-along). Existing spreadsheets with automation keep working."

## Context

Today a newcomer must finish two Google permission screens after choosing an account:

- **Allow scripts**: a Google account setting that lets the app attach a helper script to the
  spreadsheet.
- **Enable automation**: lets that script run every night, and shows Google's "hasn't verified
  this app" warning.

Until both are done, Today shows a reminder and the Money tab says the balance is not updated,
because only the nightly run does two jobs:

- it adds a `workout` row per day with its duration;
- it recalculates the balance (paid workouts bought in `money` minus paid days in `workout`).

Since feature 006 the app already adds a day's `workout` row when it writes the day's Paid/Free
status, but leaves the duration empty for the nightly run.

This feature moves the calculations into the spreadsheet itself, where they update immediately,
and makes the app add every missing day row. The script becomes an optional extra.

| Job | Today | After this feature |
|-----|-------|--------------------|
| Add a `workout` row for a new day (date, Paid/Free) | nightly run; app only when a status is written | app on every sync; nightly run too when automation is on |
| A day's duration | nightly run | the spreadsheet calculates it from the day's sets |
| Balance | nightly run | the spreadsheet calculates it |
| Fill the time when a set is typed in the spreadsheet; `rec` tab follows the selected exercise | script (needs Allow scripts) | unchanged, optional |

## Clarifications

### Session 2026-10-24

- Q: When someone types a duration by hand in the `workout` tab, should it stay, or should the
  spreadsheet's calculation replace it? → A: The calculation always wins (option B). A workout day
  can't exist without sets: a `workout` row whose date has no sets in `log`, for example one
  typed by hand, is removed to keep the tab consistent.
- Q: When an existing spreadsheet is converted, what happens to old `workout` rows whose dates
  have no sets in `log` (history kept before the app)? → A: They are removed too (option A); the
  rule has no exception, and the balance may change after the update.
- Q: Should the Money tab update the balance at once on the phone, or only after the change
  reaches the spreadsheet? → A: Only the spreadsheet's number is shown (option A); while changes
  are waiting, the Money tab says the balance updates after sync. The app does not calculate the
  balance itself.

### Session 2026-10-26

- Q: Where is spreadsheet automation turned on? → A: Only from the account menu. The entry on the
  Money tab is removed: automation is mainly for people who keep their workouts in the spreadsheet
  without the app, and the Money tab stays about the balance and payments. The user guide explains
  what automation adds.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Start logging without the script steps (Priority: P1)

A newcomer installs the app, chooses a Google account, waits while the spreadsheet is created and
lands on Today. No script or automation screen appears and no reminder asks for them.

**Why this priority**: The two Google screens, one with a security warning, are the hardest part
of the first run and block nothing the newcomer needs to log a workout.

**Independent Test**: With a new Google account, go from first launch to logging the first set
without seeing any script or automation step or reminder.

**Acceptance Scenarios**:

1. **Given** a first launch, **When** the user chooses an account and the spreadsheet is created,
   **Then** the app opens Today with no script or automation step in between.
2. **Given** a spreadsheet whose automation was never enabled, **When** the user uses any tab,
   **Then** no reminder or warning about automation is shown.
3. **Given** a user who skipped the steps in an earlier version, **When** they update the app,
   **Then** the automation reminder on Today is gone.

---

### User Story 2 - Balance and workout days always up to date (Priority: P1)

Whatever the user does (logs sets, switches Paid/Free, adds a payment in the app or types any of
it in the spreadsheet), the `workout` tab and the balance reflect it without waiting for a night.

**Why this priority**: Without the nightly run, the balance and day rows would otherwise stop
updating; this story is what makes US1 safe.

**Independent Test**: With automation off, log sets on a new day, sync, and check the `workout`
tab and the Money balance; then add a payment in the spreadsheet in a browser and check the
balance again.

**Acceptance Scenarios**:

1. **Given** automation is off and the user logs sets on a new day, **When** the sets sync,
   **Then** the `workout` tab has one row for that day with its date, Paid/Free status and a
   duration from its first to its last set.
2. **Given** that row, **When** the user adds or removes a set that changes the day's first or
   last time, **Then** the duration changes accordingly once the change syncs.
3. **Given** a paid day, **When** the user switches it to Free (or back), **Then** the balance
   changes by one as soon as the change is in the spreadsheet.
4. **Given** the user adds a payment in the app or types one in the `money` tab, **When** it is
   in the spreadsheet, **Then** the balance includes it at once, and the Money tab shows it the
   next time it loads.
5. **Given** sets typed only in the spreadsheet for a day that has no `workout` row, **When**
   the app next syncs, **Then** it adds the row for that day with the default Paid/Free status
   (feature 006: the previous day's, Free when there is none).

---

### User Story 3 - Existing spreadsheets switch over once (Priority: P1)

A user who already has a spreadsheet (with or without automation) updates the app. The
spreadsheet is converted once so that durations and the balance are calculated by the
spreadsheet. Days with sets keep their numbers; `workout` rows of dates without sets are removed,
which can change the balance.

**Why this priority**: Every current user has an existing spreadsheet; US2 must hold for them too.

**Independent Test**: Take a copy of a real spreadsheet with history and payments, note the
balance and every duration, open it with the new app version, and compare.

**Acceptance Scenarios**:

1. **Given** an existing spreadsheet, **When** the new app version first syncs it, **Then** the
   balance and the duration of every day with sets in the log are calculated by the spreadsheet,
   and, if every `workout` row has sets and nothing changed since, the balance equals what the
   last nightly run produced.
2. **Given** a `workout` row whose day has no sets in the log (history typed before the app
   existed), **When** the conversion runs, **Then** that row is removed, and if it was Paid the
   balance goes up by one.
3. **Given** the conversion was interrupted (no connection, app closed), **When** the app syncs
   again, **Then** it completes the conversion; running it twice changes nothing.

---

### User Story 4 - Turn on automation when wanted (Priority: P2)

A user who wants the spreadsheet to be updated while the app is closed (for example, they log
sets in a browser) or wants the spreadsheet conveniences finds the automation setup in the
account menu and completes the same two steps as before.

**Why this priority**: Useful to some users, not needed to use the app.

**Independent Test**: With automation off, open it from the account menu,
complete both steps and see it shown as on; then type sets for a new day in a browser and see the
day's row the next morning without opening the app.

**Acceptance Scenarios**:

1. **Given** automation is off, **When** the user opens the account menu, **Then** there is an
   item for spreadsheet automation that says it is off. The Money tab has no automation entry.
2. **Given** the user starts it, **When** they complete Allow scripts and Enable automation,
   **Then** the entry shows automation as on.
3. **Given** automation is on, **When** days are added only in the spreadsheet and the app is not
   opened, **Then** the nightly run adds their `workout` rows with the default Paid/Free status.
4. **Given** automation is on, **When** the nightly run happens, **Then** it never overwrites the
   spreadsheet's calculated durations or balance.

---

### User Story 5 - Spreadsheets with automation keep working (Priority: P2)

A user who already enabled automation notices no loss: the nightly run continues, the
conveniences in the spreadsheet continue, and the app and the nightly run never produce
different results for the same data.

**Why this priority**: Protects current users from a regression.

**Independent Test**: On a spreadsheet with automation on, update the app, use it for two days
and check the `workout` tab has exactly one row per day and the balance matches the spreadsheet's
calculation.

**Acceptance Scenarios**:

1. **Given** automation is on, **When** the app updates, **Then** automation stays on with no
   action from the user.
2. **Given** both the app and the nightly run may add a day's row, **When** one of them has
   already added it, **Then** the other does not add a second row (FR-007).

---

### Edge Cases

- A day with exactly one set has a duration of 0 minutes.
- The user deletes all sets of a day: the day's `workout` row is removed on the next sync, so a
  Paid day no longer counts in the balance.
- Someone adds a `workout` row by hand for a date with no sets in `log`: it is removed on the next
  sync (or by the nightly run when automation is on).
- Someone overwrites a calculated cell in the spreadsheet by hand: on the next sync the app puts
  the calculation back and reports it as a non-fatal issue (no contents in the report).
- The `money` tab has non-numeric "workouts" values (notes, empty cells): they count as 0, as in
  the nightly run's current rule.
- The spreadsheet's time zone differs from the phone's: days are split by the spreadsheet's time
  zone, as the nightly run does today.
- A tab or header the calculation needs is missing or renamed: the Money tab shows that the
  balance can't be calculated (as for a missing tab today), and the issue is reported.
- Two rows for the same day (app and nightly run at the same moment, or typed by hand): the
  balance counts every row, as today; the app and the script never add a second row when one
  exists.
- The user is offline: sets, statuses and payments wait on the phone as today; the balance shown
  is the last one read, and the Money tab says it updates after sync (FR-005a).

## Requirements *(mandatory)*

### Functional Requirements

**First run and reminders**

- **FR-001**: After choosing an account and creating (or finding) the spreadsheet, the app MUST
  open Today directly, with no Allow scripts or Enable automation step.
- **FR-002**: The app MUST NOT show a reminder, warning or error because automation is off or the
  script is missing. The Money tab MUST NOT describe the balance as not updated for that reason.

**Calculations in the spreadsheet**

- **FR-003**: The balance MUST be calculated by the spreadsheet itself as: the total of numeric
  "workouts" values in `money` minus the number of `workout` rows that are not Free. It MUST stay
  in `balance` cell A1, and update whenever those tabs change, whoever changes them.
- **FR-004**: Each `workout` row's duration MUST be calculated by the spreadsheet as the minutes
  from the first to the last set of that date in `log`, rounded to whole minutes, and update when
  the day's sets change.
- **FR-005**: The conversion MUST apply FR-007a to existing rows as well: `workout` rows whose
  dates have no sets in `log` are removed, with no exception for rows dated before the conversion.
- **FR-005a**: The Money tab MUST show only the balance read from `balance` A1; the app MUST NOT
  calculate or estimate it. While sets, statuses or payments are waiting to sync, the Money tab
  MUST say that the balance updates after sync.

**Day rows**

- **FR-006**: On every sync the app MUST make sure each date with sets in `log` (from the app or
  typed in the spreadsheet) has a `workout` row, adding the missing ones with the date and the
  default Paid/Free status of feature 006.
- **FR-007**: The app and the nightly run MUST NOT add a row for a date that already has one.
- **FR-007a**: A `workout` row whose date has no sets in `log` MUST be removed: by the app on
  every sync, and by the nightly run when automation is on. A hand-typed duration is never kept;
  the calculation always applies (FR-004, FR-011). Exception: when the `log` tab has no sets at
  all, nothing is removed (an empty log is far more likely a mistake than a real request).

**Conversion of existing spreadsheets**

- **FR-008**: The app MUST convert an existing spreadsheet once to the calculated form (FR-003 to
  FR-005). Apart from removed rows (FR-005), it MUST NOT change any value the user sees except
  durations of days whose sets changed since the last nightly run.
- **FR-009**: The conversion MUST be safe to repeat and to resume after an interruption; the app
  MUST record that it is done in the spreadsheet so other phones don't repeat it.
- **FR-010**: New spreadsheets MUST be created in the calculated form.
- **FR-011**: If a calculated cell is found overwritten, the app MUST restore the calculation on
  the next sync and report a non-fatal issue without spreadsheet contents.

**Optional automation**

- **FR-012**: The account menu, and only the account menu, MUST offer spreadsheet automation,
  showing whether it is Off, On or Stopped (clarification 2026-10-26). What it adds (new days' rows
  while the app is closed, the time filled in when a set is typed in the spreadsheet, and the `rec`
  tab following the selected exercise) is explained in the user guide.
- **FR-013**: Turning automation on MUST use the same two steps as before (Allow scripts, Enable
  automation), each skippable, and the entry MUST show the result.
- **FR-014**: When automation is on, the nightly run MUST add missing day rows with the default
  status, remove rows of dates without sets (FR-007a), and MUST NOT write durations, nor the
  balance of a converted spreadsheet.
- **FR-015**: Spreadsheets with automation already on MUST keep it on through the update with no
  user action; the app MUST update their script to the version that follows FR-014 (as the app
  already updates outdated scripts).

**Reporting**

- **FR-016**: Failures of the conversion, of adding day rows and of restoring a calculation MUST
  be reported to monitoring (constitution V), without dates, values or names from the
  spreadsheet.

### Key Entities

- **Workout day row**: one per date with sets in `log`, and only for such dates, in the `workout`
  tab: date, duration (calculated), Paid/Free ("work alone", `1` = Free).
- **Balance**: one number in `balance` A1 (calculated): paid workouts left.
- **Spreadsheet form**: whether a spreadsheet is in the calculated form; recorded in the
  spreadsheet once the conversion is done.
- **Automation**: optional; off or on per spreadsheet, as recorded today.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A newcomer reaches Today in at most 3 screens after installing (welcome, Google
  account choice and access, setup progress), against 5 or more today.
- **SC-002**: With automation off, a payment or Paid/Free change shows in the Money balance within
  one sync and one reload of the Money tab, instead of the next morning.
- **SC-003**: After the conversion of a real spreadsheet copy, 100% of durations of days with sets
  equal the values before the conversion, and the balance differs from before by exactly the
  number of removed Paid rows (when nothing changed in between).
- **SC-004**: Over a week of daily use with automation on, the `workout` tab has exactly one row
  per day with sets.
- **SC-005**: Zero automation reminders or "balance not updated" notes are shown to a user who has
  never turned automation on.

## Assumptions

- The app keeps reading the balance from `balance` A1, so anything else that reads that cell
  keeps working.
- The day split, the Paid/Free default and the "first row per date" rule are those of features
  002 and 006.
- Durations of days whose sets were edited after the last nightly run may change in the
  conversion; that is a correction, not a loss.
- Removing rows without sets in existing spreadsheets is accepted by the owner even though it can
  change the balance (Clarifications, Q2). Removed rows stay recoverable from the spreadsheet's
  own version history.
- While automation is on with a script that can't be upgraded yet (for example, the Allow scripts
  setting was turned off later), the nightly run keeps writing the balance and the spreadsheet is
  converted only once the upgrade succeeds; durations are calculated in the meantime.
- The balance formula counts numbers in `money` "workouts"; a text such as "2 pcs" counts as 0
  (the old nightly run read it as 2). The app and the web UI's number column store numbers.
- The `rec` tab and the auto-filled time stay script features; making them work without the
  script is out of scope.
- Removing the script from spreadsheets that already have it is out of scope; it simply stops
  writing durations and the balance (FR-014).
- The quick-start guide and README are updated to describe the shorter first run.
