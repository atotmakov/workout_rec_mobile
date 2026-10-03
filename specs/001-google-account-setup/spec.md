# Feature Specification: Google Account Setup

**Feature Branch**: `001-google-account-setup`

**Created**: 2026-10-01

**Status**: Draft

**Input**: User description: "At first applicaton start user should choose google account he want to use with the app, after user pic show selected user in up right corner. On user google account in google sheets created the table with tabs: log, drills, rec, money, workout, balance."

## Clarifications

### Session 2026-10-01

- Q: Should the app look for an existing workout_rec_database_ spreadsheet anywhere in the user's
  Google Drive, or only among spreadsheets the app itself created? → A: Only spreadsheets the app
  created; a hand-made spreadsheet with that name is ignored.
- Q: When the user answers "Yes" to "want to rewrite it?", should the old spreadsheet's data be
  deleted or kept as a backup? → A: Kept: the old spreadsheet is renamed to
  workout_rec_database_backup_<date> and a fresh spreadsheet is created.
- Q: Is it acceptable for first-time setup to need one manual step outside the app to switch on
  the spreadsheet's automation? → A: Yes: the app opens the new spreadsheet in the browser and the
  user taps "Enable automation" once, which approves the script and turns on its daily schedule.
  *(Revised during planning, 2026-10-03: Google only lets an app attach a script after the user
  turns on "Google Apps Script API" in their Apps Script settings, and spreadsheet menus are not
  shown on phones.)* → Revised A: two one-time browser steps: (1) the user turns on "Google Apps
  Script API" at script.google.com/home/usersettings; (2) after the app attaches the script, the
  user opens the script's "Enable automation" page and approves it.
- Q: Which language should the app's screens and messages use? → A: Russian and English,
  following the phone's language setting; English for any other language.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Choose a Google account on first launch (Priority: P1)

The first time the user opens the app, they are asked to choose which Google account to use with
the app. After choosing, the app shows the selected account (profile picture, or initials if
there is no picture) in the top-right corner of the main screen. On later launches the app opens
directly with the same account selected, without asking again.

**Why this priority**: Every other feature reads from and writes to the user's Google Sheet; the
app cannot do anything useful until it knows which Google account to use.

**Independent Test**: Install the app fresh on a phone with two Google accounts, launch it,
choose the second account, and verify its picture is shown top-right; close and relaunch the app
and verify it opens straight to the main screen with the same account.

**Acceptance Scenarios**:

1. **Given** the app is launched for the first time, **When** it opens, **Then** the user is
   asked to choose a Google account before reaching the main screen.
2. **Given** the account chooser is shown, **When** the user picks an account and grants access,
   **Then** the main screen opens with that account's picture (or initials) in the top-right
   corner.
3. **Given** an account was chosen earlier, **When** the app is relaunched, **Then** the main
   screen opens directly with the same account shown top-right.
4. **Given** the account chooser is shown, **When** the user cancels or denies access, **Then**
   the app explains that a Google account is required and offers to choose again.
5. **Given** the main screen is shown, **When** the user taps the account picture, **Then** the
   account name and email are shown, with options to switch account or sign out.

---

### User Story 2 - Create the workout spreadsheet in the user's Google account (Priority: P1)

After the account is chosen, the app makes sure the user's Google account has the workout
spreadsheet, with the tabs **log**, **drills**, **rec**, **money**, **workout**, and **balance**.
The user can open the spreadsheet in the Google Sheets web UI and see these tabs.

**Why this priority**: The spreadsheet is the app's data store (constitution, Principle III); all
later features (logging, exercises, records, workouts by day) depend on it existing with these
tabs.

**Independent Test**: With a Google account that has no workout spreadsheet, complete the
account selection, then open Google Sheets on the web and verify a new spreadsheet exists with
exactly the six tabs in the listed order.

**Acceptance Scenarios**:

1. **Given** the chosen account has no workout spreadsheet, **When** account selection completes,
   **Then** a spreadsheet is created in that account with the tabs log, drills, rec, money,
   workout, balance, in that order.
2. **Given** the spreadsheet was created, **When** the user opens Google Sheets on the web,
   **Then** they can find it by its name and open and edit it.
3. **Given** the chosen account already has a spreadsheet named workout_rec_database_ whose
   structure matches the reference structure, **When** account selection completes, **Then** the
   app uses the existing spreadsheet without changing it.
4. **Given** the chosen account already has a spreadsheet named workout_rec_database_ whose
   structure differs from the reference structure, **When** account selection completes,
   **Then** the app asks "Spreadsheet with name workout_rec_database_ already exists in the
   account, want to rewrite it?".
5. **Given** that question is shown, **When** the user answers "Yes", **Then** the old
   spreadsheet is renamed to workout_rec_database_backup_<date> with its data untouched, and a
   new workout_rec_database_ is created with the reference structure; **When** the user answers
   "No", **Then** the app returns to the account chooser.
6. **Given** a new spreadsheet was created and the user's "Google Apps Script API" setting is
   off, **When** the app tries to attach the script, **Then** it explains the one-time setting and
   opens script.google.com/home/usersettings in the browser; after the user turns it on and
   returns, the app attaches the script.
7. **Given** the script is attached, **When** the app finishes, **Then** it explains the one-time
   "Enable automation" step and opens the script's "Enable automation" page in the browser.
8. **Given** the user approved the script on the "Enable automation" page, **When** they return
   to the app, **Then** the app confirms that automation is on and opens the main screen.
9. **Given** the user returns without completing either step, **When** the app checks, **Then**
   it shows which step is still missing, with options to open it again or continue for now.
10. **Given** setup of the spreadsheet is in progress, **When** it fails, **Then** the user sees
   why and can retry.

---

### User Story 3 - Switch or sign out of the Google account (Priority: P3)

From the account picture in the top-right corner, the user can switch to a different Google
account or sign out. Switching repeats the spreadsheet check for the new account; signing out
returns the app to the first-launch account chooser.

**Why this priority**: Useful when the wrong account was chosen, but not needed for the first
working version.

**Independent Test**: Choose account A, switch to account B, and verify B's picture is shown
top-right and B's account has the workout spreadsheet; then sign out and verify the account
chooser is shown.

**Acceptance Scenarios**:

1. **Given** account A is selected, **When** the user switches to account B, **Then** B is shown
   top-right and the app uses B's spreadsheet (created if missing).
2. **Given** an account is selected, **When** the user signs out and confirms, **Then** the app
   shows the account chooser as on first launch.

---

### Edge Cases

- No network on first launch: the user is told that an internet connection is needed for the
  initial setup and can retry once online.
- The phone has no Google account: the account chooser lets the user add one.
- Access is revoked later (e.g., from Google account settings): on next use the app tells the
  user and asks them to choose an account again.
- The user deletes the spreadsheet in the web UI: on the next account selection a new one is
  created with the reference structure.
- The user renames a required tab or changes its columns in the web UI: the structure no longer
  matches, so the next account selection shows the rewrite question (FR-008).
- The user adds extra tabs or extra columns after the reference columns: the structure check
  ignores them, and the existing spreadsheet is still used.
- The user answers "Yes" to the rewrite question: the old data stays in the backup spreadsheet;
  the question MUST say where the old data will be kept.
- A backup with the same date already exists (two rewrites in one day): the new backup name gets
  a numeric suffix, e.g. workout_rec_database_backup_2026-10-01_2.
- More than one app-created spreadsheet named workout_rec_database_ exists in the account: the
  app uses the most recently modified one for the structure check.
- The user made a spreadsheet named workout_rec_database_ by hand (or copied one): the app does
  not see it and creates its own spreadsheet, so the account ends up with two files of that name.
- The user never turns on "Google Apps Script API": the spreadsheet is created without the
  script; the in-sheet auto-fill and follow selection do not work, and the app keeps reminding
  (FR-015). Once the setting is on, the app attaches the script without recreating the spreadsheet.
- The user skips "Enable automation": the spreadsheet works, the in-sheet auto-fill and follow
  selection still work, but workout and balance are not updated until the step is done; the app
  keeps reminding (FR-015).
- The user later removes the script's approval in their Google account: the app detects that
  automation is off and shows the reminder again.
- Setup is interrupted midway (app closed, network lost): on the next launch the setup resumes,
  without creating a second spreadsheet or duplicate tabs.
- Signing out while unsynced data exists (from later features): the user is warned that unsynced
  data will be lost before confirming.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: On first launch, the app MUST require the user to choose a Google account before
  showing the main screen.
- **FR-002**: The app MUST request only the access needed to create and edit the app's own
  spreadsheet and its attached automation; it MUST NOT request access to the user's other Drive
  files, and MUST explain why access is needed before asking.
- **FR-003**: After an account is chosen, the app MUST show that account's profile picture (or
  its initials if none) in the top-right corner of the main screen.
- **FR-004**: The app MUST remember the chosen account and MUST NOT ask again on later launches
  unless the user signs out or access is revoked.
- **FR-005**: Tapping the account picture MUST show the account name and email, and options to
  switch account and to sign out.
- **FR-006**: After an account is chosen, the app MUST ensure the account has a spreadsheet named
  workout_rec_database_ containing the tabs log, drills, rec, money, workout, balance, in that
  order; the spreadsheet MUST use the dd.MM.yyyy date format regardless of the phone's language.
- **FR-007**: Each tab MUST be created with the reference structure (columns, column types, and
  formatting) defined in [Reference Spreadsheet Structure](#reference-spreadsheet-structure),
  including the rec conditional formatting rules and the rec A1 drop-down.
- **FR-008**: The app MUST look only at spreadsheets it created itself in the chosen account;
  spreadsheets created by hand or by other apps are never read, even if they have the same name.
  If the chosen account already has an app-created spreadsheet named workout_rec_database_, the
  app MUST compare its structure with the reference structure as defined in "Structure check"
  (tabs, header rows, the rec formula, the rec drop-down, and the rec conditional formatting rules):
  - if it matches, the app MUST use the existing spreadsheet without modifying it;
  - if it differs, the app MUST ask "Spreadsheet with name workout_rec_database_ already exists
    in the account, want to rewrite it?"; on "Yes" it MUST rename the old spreadsheet to
    workout_rec_database_backup_<date> (date as yyyy-MM-dd, without changing its contents) and
    create a new workout_rec_database_ with the reference structure; on "No" it MUST return to
    the account chooser without changing anything.
- **FR-009**: Spreadsheet setup MUST be safe to repeat: re-running it MUST NOT create a second
  spreadsheet or duplicate tabs, and MUST NOT modify a matching spreadsheet's data.
- **FR-010**: The app MUST never delete a spreadsheet, and MUST never delete, rename, or reorder
  tabs or data in it; the only exception is renaming the old spreadsheet to a backup during a
  rewrite the user confirmed (FR-008).
- **FR-011**: If setup cannot complete (no network, access denied, Google service error), the app
  MUST show a clear message and a retry option.
- **FR-012**: Signing out MUST require confirmation and MUST return the app to the first-launch
  account chooser.
- **FR-013**: A newly created (or rewritten) spreadsheet MUST have the attached automation
  described in [Attached automation](#attached-automation-spreadsheet-script), including both
  scheduled behaviors (daily workouts and balance), so it works the same as the reference
  spreadsheet. The app MUST attach the script to the spreadsheet. Two one-time manual steps are
  allowed, each opened in the browser by the app with an explanation: (1) turning on the user's
  "Google Apps Script API" setting, needed only before the app can attach the script; (2)
  approving the script on its "Enable automation" page, which turns on the daily schedule. The
  app MUST be able to tell whether each step has been done.
- **FR-014**: All app screens and messages MUST be available in Russian and English; the app MUST
  use Russian when the phone's language is Russian and English otherwise. Messages quoted in this
  spec are the English versions. Spreadsheet names, tab names, and column headers are not
  translated.
- **FR-015**: Until the script is attached and automation is enabled, the app MUST show a visible
  reminder (e.g., on the account menu) naming the missing step, with a way to open it; the rest of the app MUST
  stay usable.

### Key Entities *(include if feature involves data)*

- **Selected Account**: The Google account the app uses; has name, email, and profile picture.
  Exactly one at a time.
- **Workout Spreadsheet**: The spreadsheet in the selected account that stores all app data; has
  a name and the six tabs below.
- **Tab**: A sheet within the workout spreadsheet: log (logged workouts), drills (exercise list),
  rec (records), money, workout (workouts by day), balance. Their contents are defined by later
  features.

### Reference Spreadsheet Structure

Source: reference spreadsheet provided by the user
(`https://docs.google.com/spreadsheets/d/1nKZrHyaD2Icvs_z3KKzunXuRywQgPaVxlG2tDHQGuDs`).

A new spreadsheet contains the header rows and fixed cells below and no data rows. Dates use the
format `dd.MM.yyyy`.

| Tab | Column / cell | Header | Type | Example | Meaning |
|-----|---------------|--------|------|---------|---------|
| log | A | Date | date-time, shown as date | 12.01.2026 | Moment the set was logged; times are used to compute workout duration |
| log | B | Drill | text | пуловер в кроссовере | Exercise name (from drills) |
| log | C | W | decimal number ≥ 0 | 17,5 | Weight; 0 for bodyweight |
| log | D | R | whole number ≥ 1 | 15 | Reps |
| drills | A | mscl | text | back | Muscle group |
| drills | B | drill | text | пуловер в кроссовере | Exercise name |
| rec | A1 | — | drop-down list of values in `drills!$B:$B` | жим гантелей от лба лежа | Exercise to look up; chosen by the user or set by the script |
| rec | A2 | — | formula | `=FILTER(log!A:Z, log!B:B=A1)` | Lists all log rows for the exercise in A1 |
| rec | D1 | — | text | `FILTER(log!A:Z, log!B:B=A1)` | Note showing the formula used |
| money | A | date | date | 01.01.2026 | Payment date |
| money | B | workouts | whole number ≥ 0 | 24 | Workouts paid for |
| money | C | sum | number ≥ 0 | 24000 | Amount paid |
| workout | A | date | date | 12.01.2026 | Workout day |
| workout | B | duration, min | whole number ≥ 0 | 91 | Workout duration in minutes |
| workout | C | work alone | 0 or 1 | 0 | 1 if trained alone, 0 otherwise |
| balance | A1 | — | whole number (may be negative) | 11 | Paid workouts not yet used; calculated by the script, no header |

#### Conditional formatting (rec tab)

| Range | Rule (custom formula) | Style | Meaning |
|-------|-----------------------|-------|---------|
| C2:C1000 | `=C2=MAX($C$2:$C)` | light green fill | Highlights the heaviest weight logged for the exercise |
| D2:D1000 | `=AND($C2=MAX($C:$C), $D2=MAXIFS($D:$D, $C:$C, MAX($C:$C)))` | light green fill | Highlights the most reps at that heaviest weight (the personal record) |

#### Attached automation (spreadsheet script)

The spreadsheet has an attached script that behaves as follows. A new spreadsheet MUST have the
same behavior.

| Behavior | Runs when | What it does |
|----------|-----------|--------------|
| Auto-fill log rows | A user edits column B (Drill) of the log tab in the web UI | Fills the empty rows directly above with the same exercise, going up until a filled row is reached; fills column A of those rows and of the edited row (when empty) with the current date-time; sets rec A1 to the edited exercise |
| Follow selection | A user selects a cell in column B of the log tab in the web UI | Sets rec A1 to the selected exercise, so rec shows its history |
| Daily workouts | On a time-based schedule (about once a day) | For each day in log not yet in workout, adds a workout row with exactly three values: date, duration in minutes (last minus first logged time that day), and work alone 0. Nothing is written beyond column C |
| Show status | The user runs it manually from the script editor (Extensions → Apps Script → select `logMetadata` → Run) | Prints the app's hidden status markers (structure version, script ID, Enable automation page, enabled time, last daily run) to the editor's Execution log; changes nothing |
| Balance | On a time-based schedule (about once a day) | Sets balance A1 to the total of money "workouts" minus the number of workout rows whose "work alone" is not 1 |

Structure check (FR-008): the spreadsheet matches when all six tabs exist with the headers above
in row 1 (log, drills, money, workout), the rec formula in A2, the rec A1 drop-down, and the two
rec conditional formatting rules. Data rows and the values in rec A1 and balance A1 are not
compared. A missing script, or automation that is not yet enabled, is tracked separately
(FR-015) and fixed by attaching the script, not treated as a structure difference. In a new
spreadsheet rec A1 is empty and balance A1 is 0.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A new user goes from first launch to the main screen with their account shown in
  under 1 minute, and completes both one-time automation steps in under 3 more minutes.
- **SC-002**: After setup, 100% of tested accounts have exactly one workout spreadsheet with the
  six tabs in the specified order, the rec highlighting and drop-down, and working automation
  (editing log column B in the web UI auto-fills dates; balance and workout update within a day).
- **SC-003**: Repeating setup (relaunch, interrupted setup, reinstall) never creates a duplicate
  spreadsheet or tab in testing.
- **SC-004**: On later launches the main screen opens with the account shown without any account
  prompt, in 100% of launches while access remains granted.
- **SC-005**: The user can open the created spreadsheet in the Google Sheets web UI and edit it
  without any setup beyond the two one-time automation steps.

## Assumptions

- One Google account is used at a time; multiple simultaneous accounts are out of scope.
- The spreadsheet is named workout_rec_database_ and is created in the root of the user's
  Google Drive.
- The tab names are used exactly as given (lowercase): log, drills, rec, money, workout, balance.
- The tab columns come from the reference spreadsheet provided by the user; how each tab's data
  is used is defined by later features.
- The two scheduled behaviors run about once a day; the exact schedule of the reference
  spreadsheet was not visible and is assumed to be daily.
- The "auto-fill log rows" and "follow selection" behaviors react only to edits made by a person
  in the web UI; data written by the app does not trigger them, so later features that write to
  log MUST fill the Date column with the date-time themselves.
- "Rewrite" means the existing spreadsheet is kept as a renamed backup and a new, empty
  spreadsheet with the reference structure takes the workout_rec_database_ name. Backups are
  never read by the app.
- Initial setup requires internet access; offline use of the app starts after setup completes.
- The user's phone has Google account support available.
