# Feature Specification: Bottom Tab Navigation

**Feature Branch**: `004-bottom-tab-navigation`

**Created**: 2026-10-21

**Status**: Draft

**Input**: User description: "I want menu in the bottom of the screen (like in youtube mobile app) with
these tabs: Today (current default screen), History (current workout button), Exercises (updatable
list of exercise), Money (new tab, compilation of "money" tab and "balance" tab from spreadsheet)"

## Context

Today the app has one main screen (Today) and reaches everything else through links on it:
"Workouts" opens the past days, "Enter a past workout" starts the past-workout flow, and the
exercise list is only seen inside "Add exercise". Exercises can only be added in the
spreadsheet's drills tab in the web UI (feature 002 left an "Exercises library" for later). The
spreadsheet's money tab (payments: date, workouts paid for, sum) and balance tab (paid workouts not
yet used, recalculated about once a day by the attached script, feature 001) are not shown in the
app at all.

This feature replaces the links with a bar of four tabs at the bottom of the screen, as in the
YouTube mobile app, and adds two new destinations: Exercises and Money. It is part of the redesign
(see `design/redesign-brief.md`, idea F "bottom tabs").

## Clarifications

### Session 2026-10-22

- Q: Which changes can the user make in the Exercises tab? → A: Add, rename and delete (C); an
  exercise can be deleted only if it was never logged in any workout.
- Q: Can the user add a payment from the Money tab? → A: Yes, saved on the phone and synced to the
  money tab (A).
- Q: Which balance is shown? → A: The balance tab's value (A), with a note that the balance is
  calculated once a day.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Switch between sections with bottom tabs (Priority: P1)

The user sees a bar at the bottom of the screen with four tabs: Today, History, Exercises and
Money, each with an icon and a short label. One tap opens a section; the current tab is
highlighted. The app opens on Today, as now.

**Why this priority**: The bar is the frame for the other stories; with only Today and History in
it, it already replaces the "Workouts" link and makes past workouts one tap away.

**Independent Test**: Open the app, check Today is selected; tap History and see the past days;
tap Today and see today's workout again, unchanged.

**Acceptance Scenarios**:

1. **Given** a set-up account, **When** the app starts, **Then** the Today tab is selected and
   shows today's workout as before.
2. **Given** any tab, **When** the user taps another tab, **Then** that section opens and its tab
   is highlighted.
3. **Given** the user is deeper inside a tab (e.g. a day in History), **When** they switch to
   another tab and back, **Then** they return to where they were in that tab.
4. **Given** the user is deeper inside a tab, **When** they tap the already selected tab, **Then**
   the tab returns to its first screen.
5. **Given** the user is on the first screen of a tab other than Today, **When** they press the
   system Back, **Then** the app goes to Today; Back on Today leaves the app.
6. **Given** the user is logging sets of an exercise or editing a past workout, **When** the
   screen is open, **Then** the bottom bar is hidden so the logging controls have the full height;
   it comes back on leaving that screen.

---

### User Story 2 - History tab (Priority: P1)

The History tab shows the past days list that "Workouts" opens today (newest first, with
exercise and set counts), and offers "Enter a past workout". Tapping a day opens its details, as
now.

**Why this priority**: It moves an existing, often used function into the bar; no new data is
needed.

**Independent Test**: With some past workouts, tap History, see the days; tap a day, see its sets;
start "Enter a past workout" from the tab.

**Acceptance Scenarios**:

1. **Given** past workouts exist, **When** the user opens History, **Then** the days are listed
   newest first with their exercise and set counts.
2. **Given** History is open, **When** the user taps "Enter a past workout", **Then** the past
   workout flow starts as in feature 002, and after Save the user is back in History with the new
   day in the list.
3. **Given** no past workouts, **When** the user opens History, **Then** an empty message is shown
   with "Enter a past workout".
4. **Given** the new tabs exist, **When** the user looks at Today, **Then** the "Workouts" and
   "Enter a past workout" links are no longer on it.

---

### User Story 3 - Exercises tab (Priority: P2)

The Exercises tab shows the exercise list from the drills tab, grouped by muscle group, with
search, and lets the user keep it up to date from the phone: add an exercise, rename it (its past
sets are renamed too), and delete an exercise that was never logged.

**Why this priority**: Today a new exercise can only be added in the web UI, which is awkward in
the gym; it is the most requested missing piece after navigation.

**Independent Test**: Open Exercises, add "Face pull" to "Shoulders", see it in the list and in
"Add exercise" on Today, and see a new row in the drills tab after sync.

**Acceptance Scenarios**:

1. **Given** the Exercises tab, **When** it opens, **Then** the exercises are shown grouped by
   muscle group, in the same order as in "Add exercise", with a search field.
2. **Given** the Exercises tab, **When** the user adds an exercise with a muscle group (picked
   from the existing groups or typed as a new one) and a name, **Then** it appears in the list and
   in "Add exercise" at once, also offline, and is written to the drills tab on the next sync.
3. **Given** a name that already exists (ignoring case and spaces at the ends), **When** the user
   tries to add it, **Then** the app refuses and says the exercise exists.
4. **Given** the user pulls the list down, **When** online, **Then** the list is downloaded again
   from the drills tab, so exercises added in the web UI appear.
5. **Given** an exercise in the list, **When** the user taps it, **Then** its last time and record
   are shown (as when logging), with buttons to log it today, rename it and, when allowed,
   delete it.
6. **Given** an exercise, **When** the user renames it (name and/or muscle group), **Then** the new
   name is shown at once everywhere in the app, including its past sets, also offline; on the next
   sync the drills row and every log row with the old name get the new name.
7. **Given** a new name that another exercise already has, **When** the user saves the rename,
   **Then** the app refuses and says the exercise exists.
8. **Given** an exercise that has no sets in the log and none waiting on the phone, **When** the
   user deletes it and confirms, **Then** it disappears from the list and "Add exercise", and its
   drills row is removed on the next sync.
9. **Given** an exercise with at least one logged set, **When** the user opens it, **Then** Delete
   is not offered, and a short note says exercises with logged sets cannot be deleted.

---

### User Story 4 - Money tab (Priority: P2)

The Money tab shows how many paid workouts are left (the balance) at the top, and below it the
payments from the money tab, newest first: date, workouts paid for, and sum. The user can add a
payment; it is saved on the phone and written to the money tab like sets to the log.

**Why this priority**: The money and balance tabs exist in every spreadsheet but are only visible
in the web UI; seeing the balance before a training session is useful, but not needed to log one.

**Independent Test**: With payments in the money tab and a balance value, open Money and compare
the numbers with the spreadsheet.

**Acceptance Scenarios**:

1. **Given** payments in the money tab, **When** the user opens Money, **Then** they are listed
   newest first with date, workouts and sum, and the total paid is shown.
2. **Given** a balance, **When** the user opens Money, **Then** the remaining paid workouts are
   shown prominently; a negative balance is shown as workouts owed, in a warning colour.
3. **Given** the phone is offline, **When** the user opens Money, **Then** the last downloaded
   values are shown with the time they were downloaded.
4. **Given** no payments, **When** the user opens Money, **Then** an empty message explains that
   payments come from the spreadsheet's money tab.
5. **Given** the balance is shown, **When** the user looks at it, **Then** a note under it says the
   balance is calculated once a day, with the time of the last calculation when known.
6. **Given** the Money tab, **When** the user adds a payment (date — today by default, not in the
   future; workouts paid for, a whole number of at least 1; sum, a number of at least 0), **Then**
   it appears at the top of the list at once, also offline, marked as waiting in the sync icon,
   and is written as a new row of the money tab on the next sync.
7. **Given** a payment was just added, **When** the user looks at the balance, **Then** the balance
   is not changed yet, and the note says the payment will be counted at the next daily
   calculation.
8. **Given** the spreadsheet's daily automation is not enabled (feature 001), **When** the user
   opens Money, **Then** the note says the balance is not being updated and points to enabling
   automation.

---

### Edge Cases

- **Small screens and large font**: the four labels must fit at 360 dp width and at the largest
  system font size; if they cannot, labels may wrap or shorten but icons stay.
- **Account menu and sync icon**: the avatar and the sync icon (redesign 1a) stay in the top bar
  of each tab's first screen.
- **Unsaved logging**: switching tabs is not possible while the bar is hidden (logging, past
  workout editor), so unconfirmed sets are not lost by a tab switch.
- **Setup not finished**: before an account and spreadsheet are set up (feature 001), the setup
  screens are shown without the bar.
- **Automation reminder and notices** (feature 001/002) keep showing on Today.
- **Drills tab changed in the web UI** while the app has an unsynced new exercise: both are kept;
  the app's exercise is appended after the existing rows. If the web UI added the same name, the
  app does not add a second row.
- **Rename conflicts**: the exercise was renamed or removed in the web UI before the app's rename
  synced → the web-UI version wins (as for sets in feature 002); the user is told, and the app
  shows the current name. Log rows typed in the web UI with the old name after the rename synced
  are not renamed.
- **Delete check uses what the phone knows**: the last downloaded log plus sets waiting on the
  phone. If a set with that exercise is found in the log on the next download, the delete is not
  applied and the user is told.
- **Records drop-down**: the rec tab's chosen exercise (rec A1) is renamed too when it holds the
  old name, so the record view keeps working.
- **Money tab changed in the web UI** while a payment is waiting: the app's payment is appended
  after the existing rows; nothing is overwritten.
- **Money tab with bad values** (text in "workouts" or "sum", empty date): the row is shown with
  the readable values; non-numbers are not counted in the total, the same way the script ignores
  them for the balance.
- **Sign out / switch account**: the tabs' data belongs to the account and is cleared with the
  rest of its data.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The app MUST show a bottom bar with four tabs, in this order: Today, History,
  Exercises, Money; each has an icon and a label in English and Russian.
- **FR-002**: The app MUST open on Today; the selected tab MUST be highlighted.
- **FR-003**: Each tab MUST keep its own place (its screen stack and scroll position) while the
  user is in other tabs; tapping the selected tab again MUST return it to its first screen.
- **FR-004**: System Back on the first screen of History, Exercises or Money MUST go to Today; on
  Today it MUST leave the app.
- **FR-005**: The bar MUST be hidden on the exercise logging screen, the past workout editor,
  dialogs, and the setup screens, and shown on all other screens.
- **FR-006**: History MUST show the past days list and day details from feature 002 (FR-013) and
  offer "Enter a past workout" (feature 002, US5); Today MUST no longer show the "Workouts" and
  "Enter a past workout" links.
- **FR-007**: Exercises MUST show the drills-tab exercise list grouped by muscle group with search,
  work offline from the last download, and refresh on pull-down when online.
- **FR-008**: Exercises MUST let the user add an exercise (muscle group + name), saved on the phone
  first and written to the drills tab by the same sync that writes sets; duplicate names (ignoring
  case and outer spaces) MUST be refused.
- **FR-008a**: Exercises MUST let the user rename an exercise and change its muscle group; the
  rename MUST apply on the phone at once and, on sync, to the drills row, every log row with the
  old name, and rec A1 when it holds the old name. A rename to an existing name MUST be refused.
- **FR-008b**: Exercises MUST let the user delete an exercise only when it has no set in the log
  (as last downloaded) and none waiting on the phone; otherwise Delete MUST NOT be offered.
  Deleting MUST ask for confirmation and remove the drills row on sync.
- **FR-009**: Money MUST show the balance (paid workouts left) and the payments from the money tab,
  newest first, with date, workouts and sum, and the total paid; it MUST work offline from the last
  download and show when the data was downloaded.
- **FR-009a**: The balance MUST be the value of the balance tab, with a note that it is
  calculated once a day (and the time of the last calculation when known); when the daily
  automation is not enabled, the note MUST say so.
- **FR-009b**: Money MUST let the user add a payment (date not in the future, default today;
  workouts paid for, whole number ≥ 1; sum, number ≥ 0), saved on the phone first and appended to
  the money tab by the same sync that writes sets; waiting payments count in the sync icon.
- **FR-010**: Data from the money and balance tabs MUST be downloaded when Money is opened online,
  on pull-down, and at most once per app start in the background; downloading MUST NOT slow down
  logging.
- **FR-011**: All new texts MUST be in English and Russian, in light and dark themes.
- **FR-012**: The sync icon's count and the sign-out / switch-account warning MUST include every
  change waiting on the phone: sets, exercise changes and payments.

### Key Entities

- **Tab**: one of Today, History, Exercises, Money; has its own screen stack.
- **Exercise**: muscle group and name, from the drills tab (feature 002); an exercise added,
  renamed or deleted in the app is pending until written to the drills tab (and, for a rename, to
  the log).
- **Payment**: a row of the money tab — date, workouts paid for (whole number), sum; one added in
  the app is pending until appended to the money tab.
- **Balance**: paid workouts not yet used (whole number, may be negative), from the balance tab.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Every section (Today, past days, exercise list, money) is reachable in one tap from
  any tab's first screen.
- **SC-002**: Switching tabs shows the target section within 0.5 s, also offline.
- **SC-003**: Returning to a tab restores the screen and scroll position the user left in 100% of
  switches within one app session.
- **SC-004**: A new exercise added in the app can be logged immediately, offline, and appears in
  the drills tab within 1 minute of the phone being online.
- **SC-005**: The balance and payments in the app match the spreadsheet in 100% of checks made
  after a refresh.
- **SC-007**: After a rename syncs, 100% of the exercise's log rows that existed at the time of the
  rename carry the new name, and its record and last time are still shown.
- **SC-008**: An added payment appears in the money tab within 1 minute of the phone being online.
- **SC-006**: The bar fits without overlap at 360 dp width at the largest system font size, in
  both languages.

## Assumptions

- The bar follows the platform's standard bottom navigation (icon + label for all four tabs), as
  in the YouTube app; no badges on tabs in this feature.
- The exercise tap-through (last time, record, "Log today") reuses what logging already shows; the
  full history of one exercise stays a separate later feature.
- The money and balance tabs' structure is the one from feature 001 and is not changed; the app
  never writes the balance tab: the nightly script stays the only source of the balance.
- Editing or deleting payments in the app is out of scope; they are changed in the web UI.
- Sync of new exercises follows the same rules as sets (feature 002): local first, retried in the
  background, conflicts resolved in favour of the web UI.
- Muscle group names are free text, as in the drills tab; the picker offers existing ones.
