# Feature Specification: Log New Workout

**Feature Branch**: `002-log-workout`

**Created**: 2026-10-05

**Status**: Draft

**Input**: User description: "Log new workout"

## Context

Feature 001 gives the user a Google account in the app and a workout spreadsheet
(`workout_rec_database_`) with the reference structure. This feature lets the user log a workout
in the app: pick exercises from the spreadsheet's exercise list (drills tab), record sets with
weight and reps, and have them appear as rows in the spreadsheet's log tab — also when the phone
has no connection in the gym.

One **log row is one set**: date-time, exercise, weight, reps (spec 001, Reference Spreadsheet
Structure). A **workout** is all sets logged on one day; the spreadsheet's script later turns
each day into a workout row whose duration is the time between the first and the last set of the
day. The set times the app records therefore directly determine the workout duration.

## Clarifications

### Session 2026-10-05

- Q: When does a set get its date-time? → A: The moment the user confirms the set during the
  workout; in addition the user can enter a past workout, giving its date and start time (B).
- Q: Can sets already in the spreadsheet be edited or deleted from the app? → A: Yes, any set
  from any day (C).
- Q: Can the user add a new exercise from the app? → A: Yes, but in a separate later feature
  "Exercises library"; out of scope here.
- Q: Does the user start and finish a workout explicitly, or is a day's sets the workout? → A: No
  start/finish; the main screen shows today's workout with "Add exercise", and the first set of
  the day starts the day's workout (A).
- Q: Should the app show an exercise's full history, or only last time and the record? → A: Only
  last time and the record in this feature; the full history of an exercise comes in a separate
  later feature, "History of the exercise".
- Q: How does the user change weight and reps when a set differs from the pre-filled values? →
  A: − / + buttons (weight ±0.5 kg, reps ±1), and tapping the number to type it. Also confirmed:
  the number of sets is chosen when an exercise is added, suggested from the previous workout
  with this exercise and changeable; each set's weight and reps are pre-filled from the previous
  workout; pre-filled (suggested) values look different from values entered or confirmed in the
  current workout (e.g. gray vs black); the exercise's maximum weight and reps are shown.
- Q: Which maximum is shown for the selected exercise? → A: One record, as in the rec tab: the
  heaviest weight and the most reps done at that weight (e.g. 40 kg × 8) (A).

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Log sets of an exercise (Priority: P1)

In the gym, the user opens the app, picks an exercise from their exercise list, says how many
sets they plan, and records the weight and reps of each set. The sets are saved on the phone
immediately and later appear as rows in the log tab of their spreadsheet.

**Why this priority**: Logging sets is the product's core value; without it nothing else in the
app is useful.

**Independent Test**: With a set-up account, pick an exercise, plan 3 sets, enter weight and reps
for each, and check that the log tab gets exactly 3 rows with the exercise, the weights, the
reps and the set times.

**Acceptance Scenarios**:

1. **Given** the main screen of a set-up account, **When** the user taps "Add exercise", **Then** the list shows the exercises of the drills tab grouped by muscle group,
   with a search field.
2. **Given** an exercise is picked, **When** the user chooses 3 sets, **Then** the app shows 3
   set rows to fill, each with weight and reps fields.
3. **Given** a set row, **When** the user enters weight 17,5 and reps 15 and confirms the set,
   **Then** the set is saved on the phone at once with its date-time per FR-006, and is
   shown as done.
4. **Given** all planned sets of an exercise are done, **When** the user wants one more set,
   **Then** they can add a set beyond the planned number.
5. **Given** sets are saved and the phone is online, **When** they are synced, **Then** each set is
   one new row at the end of the log tab: Date = set date-time, Drill = exercise name, W = weight,
   R = reps.
6. **Given** a set is being entered, **When** the user enters an invalid value (negative weight,
   reps 0 or a fraction, empty field), **Then** the set cannot be confirmed and the field shows
   why.

---

### User Story 2 - Log a workout without connection (Priority: P1)

The gym has no signal. The user logs the whole workout anyway; nothing is lost, and the sets
reach the spreadsheet by themselves once the phone is online again.

**Why this priority**: Required by the constitution (Hybrid Data Architecture); gyms often have
poor connectivity, so logging that needs a network would fail exactly where it is used.

**Independent Test**: Turn on airplane mode, log 2 exercises with several sets, close the app,
turn airplane mode off, and check that all sets appear in the log tab once, in the order they were
logged, with their original times.

**Acceptance Scenarios**:

1. **Given** the phone is offline, **When** the user opens the exercise list, **Then** the list
   from the last successful download is shown.
2. **Given** the phone is offline, **When** the user logs sets, **Then** each set is saved and
   marked "not synced yet"; no error blocks logging.
3. **Given** unsynced sets exist and the app was closed or the phone restarted, **When** the phone
   is online again, **Then** the sets are synced without the user opening the app or doing
   anything, within the time in SC-003.
4. **Given** a sync was interrupted halfway, **When** sync runs again, **Then** no set is
   written twice and none is missing.
5. **Given** unsynced sets exist, **When** the user looks at the main screen, **Then** they see how
   many sets are waiting to be synced, and that the count drops to zero after sync.

---

### User Story 3 - See previous results while logging (Priority: P2)

While logging an exercise, the user sees what they did last time and their record for it, and the
new sets start pre-filled with last time's weight and reps, so most sets need one tap.

**Why this priority**: Makes logging fast and supports progression, but logging works without it.

**Independent Test**: For an exercise with history in the log tab, open it in the app and check
that the last workout's sets and the record match the log tab, and that the set rows are
pre-filled from the last workout.

**Acceptance Scenarios**:

1. **Given** the exercise has sets in the log tab, **When** the user picks it, **Then** the app
   shows the sets of the most recent earlier day with this exercise (date, weight × reps).
2. **Given** the exercise has history, **When** the app shows it, **Then** the record is shown:
   the heaviest weight, and the most reps done at that weight (the same rule as the rec tab
   highlighting).
3. **Given** last time had 3 sets, **When** the user plans sets, **Then** the planned number
   defaults to 3 and set N is pre-filled with last time's set N (or the last one if there were
   fewer), and the user can change any value.
6. **Given** a set row is pre-filled, **When** it is shown, **Then** its values look like
   suggestions (e.g. gray); a value the user changes, and all values of a confirmed set, look
   like entered values (e.g. black).
7. **Given** a set row, **When** the user taps − or + next to the weight or reps, **Then** the
   weight changes by 0.5 kg (not below 0) or the reps by 1 (not below 1); tapping the number
   opens the number keyboard to type it.
4. **Given** the exercise has no history, **When** it is picked, **Then** the app says this is
   the first time and the set rows are empty.
5. **Given** the phone is offline, **When** an exercise is picked, **Then** history from the last
   successful download plus the sets logged on this phone is shown.

---

### User Story 4 - Review and correct workouts (Priority: P2)

The user sees today's workout and a list of past workouts (by day), and can fix a typo or remove
a set logged by mistake on any day — including sets that are already in the spreadsheet and sets
that were entered in the web UI.

**Why this priority**: Mistyped weights are common on a phone; without corrections bad data
reaches the history and the records.

**Independent Test**: Log 2 exercises, change the reps of one set and delete another; open a
past day that came from the web UI and change one weight; check the log tab shows the corrected
values, no deleted set, and no other row changed.

**Acceptance Scenarios**:

1. **Given** sets were logged today, **When** the user opens today's workout, **Then** the
   exercises are listed in the order they were started, each with its sets and their sync state.
2. **Given** the log tab has earlier days, **When** the user opens the workout list, **Then** the
   days are listed newest first with the number of exercises and sets, and opening a day shows
   its exercises and sets.
3. **Given** a set is not synced yet, **When** the user edits or deletes it, **Then** the change
   applies on the phone and only the corrected set (or nothing) is synced.
4. **Given** a set is already in the log tab, **When** the user changes its exercise, weight or
   reps, **Then** that row in the log tab gets the new values and no other row changes.
5. **Given** a set is already in the log tab, **When** the user deletes it (after confirming),
   **Then** that row is removed from the log tab and no other set is lost or changed.
6. **Given** the row was changed or removed in the web UI after the app last downloaded it,
   **When** the app's edit is synced, **Then** the web-UI version wins: the app's change is not
   applied, the user is told which set it was, and the app shows the current values. A deletion
   whose row is already gone is simply done, without a message.
7. **Given** the phone is offline, **When** the user edits or deletes a synced set, **Then** the
   change is saved on the phone, shown as "not synced yet", and applied on the next sync.
8. **Given** today's workout, **When** the user picks an exercise already in it, **Then** the new
   sets are added to that exercise.

---

### User Story 5 - Enter a past workout (Priority: P3)

The user forgot to log a workout (or trained without the phone) and enters it afterwards, giving
the day, the start time and the duration, so the spreadsheet still gets a workout with the right
date and duration.

**Why this priority**: Live logging covers the normal case; this fills gaps without editing the
sheet by hand.

**Independent Test**: Enter a workout for yesterday starting 18:00 lasting 60 minutes with 2
exercises and 5 sets, and check the log tab gets 5 rows dated yesterday, the first at 18:00 and
the last at 19:00, and that the next daily run adds a 60-minute workout row.

**Acceptance Scenarios**:

1. **Given** the main screen, **When** the user chooses to enter a past workout, **Then** they
   set the date (not in the future), the start time and the duration (default 60 minutes), and
   then log exercises and sets the same way as live.
2. **Given** a past workout with N sets, **When** it is saved, **Then** set times are spread
   evenly from the start time to start + duration in the order the sets were entered (first set
   at the start time, last set at the end; a single set gets the start time).
3. **Given** a past workout is being entered, **When** the user adds or deletes sets, **Then**
   the times are recalculated so the first and last set still match the start and end.
4. **Given** the chosen day already has sets in the log, **When** the user enters a past workout
   for it, **Then** the app warns that the day already has a workout and the new sets are added
   to that day.

---

### Edge Cases

- **Exercise list empty**: the drills tab has no exercises (e.g. a new spreadsheet). The app says
  so and offers to open the spreadsheet's drills tab to add exercises (muscle group and name).
  Adding exercises in the app comes with the later "Exercises library" feature.
- **Correcting a day the script already counted**: the daily script adds a workout row once per
  day and does not update it. Editing or deleting sets of such a day changes the log only; if
  a deletion removes the first or last set of the day (changing its duration) or all sets of the
  day, the app says the workout row of that day is not updated and can be fixed in the web UI.
  Edits never change set times, so they do not affect the workout row.
- **Several pending changes to one set** (e.g. edited twice offline, then deleted): only the
  final state is applied at sync.
- **Edits on two devices or in the web UI at the same time**: the change that reaches the
  spreadsheet first wins; the later one is rejected under the rule in user story 4, scenario 6.
- **Exercise renamed or removed in the web UI** after the list was downloaded: unsynced sets are
  still written with the name they were logged with; the next list download shows the new list.
- **Rows added in the web UI** while the app has unsynced sets: the app's sets are appended after
  the last filled row at sync time; existing rows are never overwritten, moved or reordered.
- **Spreadsheet trashed or replaced** (rewrite) while sets are unsynced: the sets stay on the
  phone; once setup (feature 001) has made a spreadsheet available again, they are synced to it.
- **Sign out or switch account with unsynced sets**: the confirmation says how many sets are not
  synced and that they will be lost; nothing is lost without that confirmation. A short sync
  attempt is made first when online.
- **Access revoked or Google service errors** during sync: sets stay unsynced, sync retries later,
  and the main screen shows that sync is failing (with the reason when it needs the user, e.g.
  sign in again).
- **Day boundary**: a workout that goes past midnight is two workout days in the spreadsheet (the
  script groups by date); the app does not change this.
- **Time zone**: set times are written in the spreadsheet's time zone, so the log tab shows the
  local gym time.
- **Very long workouts**: at least 200 sets per day and any number of unsynced days are kept.
- **Log tab table full**: rows written beyond the end of the log table still count as log rows for
  the rec tab and the script.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-000**: The main screen MUST show today's workout (FR-013) with an "Add exercise" action;
  there is no start or finish step — the first set of a day starts that day's workout and the
  day ends it.
- **FR-001**: The app MUST let the user pick an exercise from the exercise list taken from the
  drills tab (exercise = column B, muscle group = column A), grouped by muscle group, with
  search by part of the name, and with exercises used recently shown first.
- **FR-002**: The app MUST download the exercise list when online and keep the last downloaded
  list on the phone for offline use.
- **FR-003**: For a picked exercise the user MUST choose a planned number of sets (1–20) and the
  app MUST show that many set rows; the user MUST be able to add sets beyond the plan and leave
  planned sets unfilled (unfilled sets are not logged).
- **FR-004**: Each set MUST have a weight (decimal ≥ 0, up to 2 decimal places, comma or dot
  accepted; 0 = bodyweight) and reps (whole number 1–999); a set with invalid or empty values
  MUST NOT be confirmable. Weight and reps MUST each have − / + buttons (weight ±0.5 kg, not
  below 0; reps ±1, not below 1), and tapping the number MUST allow typing it.
- **FR-005**: A confirmed set MUST be saved on the phone immediately, before any network
  activity, and MUST survive app close, phone restart and app update.
- **FR-006**: In a live workout, each set's date-time MUST be the moment the user confirms the
  set. In a past workout (user story 5), set times MUST be spread evenly from the chosen start
  time to start + duration in entry order. A set's date-time is not editable afterwards.
- **FR-006a**: The user MUST be able to enter a past workout by choosing a date (today or
  earlier), a start time and a duration (1–600 minutes, default 60), and then log exercises and
  sets as in a live workout; entering a past workout for a day that already has sets MUST show a
  warning first.
- **FR-007**: Saved sets MUST be synced to the log tab as one row per set (Date, Drill, W, R),
  appended after the last filled row, in the order the sets were logged, without overwriting,
  moving or deleting any existing row.
- **FR-008**: Sync MUST run automatically whenever the phone is online and unsynced sets exist —
  right after a set is confirmed and in the background, including after the app was closed — and
  MUST NOT block or slow down logging.
- **FR-009**: Sync MUST write each set exactly once, including when a sync is interrupted and
  repeated.
- **FR-010**: The app MUST show each set's sync state (synced / not synced yet) and, on the main
  screen, the number of sets waiting to be synced and whether sync is failing.
- **FR-011**: When an exercise is picked, the app MUST show the most recent earlier day's sets for
  it and its record — one value, as highlighted in the rec tab: the heaviest weight and the most
  reps done at that weight (e.g. 40 kg × 8) — based on the log tab plus sets
  logged on the phone, and MUST keep this history on the phone for offline use.
- **FR-012**: New set rows MUST be pre-filled from the most recent earlier day's sets of the same
  exercise (planned count defaults to that day's set count); with no history they are empty and
  the planned count defaults to 3. Pre-filled values MUST look visibly different from values the
  user changed or confirmed in the current workout (e.g. gray vs black).
- **FR-013**: The app MUST show today's workout (exercises in start order, each with its sets)
  and a list of past workout days from the log tab (newest first), and MUST let the user change
  the exercise, weight and reps of any set and delete any set (after confirmation) — whether it
  is unsynced, synced, or was entered in the web UI.
- **FR-013a**: Edits and deletions of sets already in the log tab MUST change or remove exactly
  that row and no other; they MUST work offline (saved on the phone and applied at the next
  sync), and when several changes are pending for one set only the final state is applied.
- **FR-014**: Signing out or switching account (feature 001) with unsynced sets MUST first try to
  sync, and MUST warn with the number of unsynced sets before discarding them.
- **FR-015**: Conflict resolution between app and sheet edits (constitution III):
  - new sets are only appended and never overwrite, move or reorder existing rows;
  - an edit or deletion from the app is applied only if the row still has the values the app
    last downloaded; if the row was changed or removed in the web UI meanwhile, the web-UI version
    wins, the app's change is dropped, and the user is told which set it was;
  - history, past workouts and the exercise list in the app are refreshed from the spreadsheet,
    and the spreadsheet's content wins over the app's cached copy (except for the app's own
    changes that are not synced yet).
- **FR-016**: All new screens and messages MUST be available in Russian and English, following
  the app language (as in feature 001).
- **FR-017**: Logging MUST be available only for a set-up account (feature 001 complete); while
  automation is not yet enabled the app MAY log and sync sets, since the app writes the Date
  column itself.

### Key Entities *(include if feature involves data)*

- **Exercise**: name and muscle group, from the drills tab; identified by its name (as in the log
  tab and the rec drop-down).
- **Set**: exercise name, weight, reps, date-time, order within the workout, sync state
  (new and not synced / synced / edit or deletion not synced yet), the values last seen in the
  spreadsheet (for the conflict rule in FR-015), and the identity needed to write it exactly once
  and to find its row again. Sets entered in the web UI become Sets in the app when downloaded.
- **Past workout entry**: date, start time and duration chosen by the user; determines the times
  of its sets (FR-006).
- **Workout (day)**: all sets with the same date; in the app, today's workout groups the sets by
  exercise in the order the exercises were started. Not stored separately — the spreadsheet
  script derives workout rows from the log.
- **Exercise history**: per exercise, the days and sets from the log tab (cached on the phone)
  plus sets logged on the phone; used for "last time" and the record.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Logging a set that is pre-filled from last time takes at most 2 taps; logging a
  set with new values takes under 10 seconds.
- **SC-002**: From the main screen, the user starts logging an exercise (pick + plan sets) in
  under 15 seconds for an exercise in the recent list.
- **SC-003**: Sets logged online appear in the log tab within 1 minute; sets logged offline
  appear within 15 minutes after the phone is back online, without the user opening the app.
- **SC-004**: In testing with forced interruptions (airplane mode mid-sync, app killed, phone
  restarted), 100% of confirmed sets reach the log tab exactly once, with the logged values and
  times.
- **SC-005**: Logging works with no connection for a whole workout (at least 200 sets) with no
  error shown that blocks logging.
- **SC-006**: Workout durations computed by the spreadsheet from app-logged sets match the real
  time between the first and the last set within 1 minute.
- **SC-007**: The "last time" sets and the record shown in the app match the log tab for 100% of
  tested exercises after a sync.
- **SC-008**: In testing, 100% of edits and deletions made in the app change exactly the intended
  row, and 100% of rows changed in the web UI before the app's edit synced keep the web-UI values.
- **SC-009**: A past workout of 20 sets is entered in under 3 minutes, and its duration in the
  workout tab equals the entered duration.

## Assumptions

- One active account and one workout spreadsheet, as set up by feature 001; the log, drills and
  rec tabs have the reference structure.
- The exercise name is the link between drills, log and rec; the app writes exactly the name from
  the drills tab.
- The web-UI auto-fill and follow-selection behaviors do not react to app writes (spec 001), so
  the app writes the Date column itself; the rec tab's A1 selection is not changed by the app.
- The workout and balance tabs are filled by the spreadsheet script from the log (spec 001); this
  feature does not write them. Marking a workout as "worked alone" and logging payments are out of
  scope (later features or the web UI).
- History for "last time" and records comes from the log tab of the current spreadsheet only;
  backups are not read.
- Rest timers, supersets, notes per set, units other than the spreadsheet's (kg), cardio or
  time-based exercises, charts, and changing a set's date-time after it is saved are out of
  scope.
- An exercise's full history (all past days, like the rec tab) is out of scope; it is the later
  feature "History of the exercise". This feature shows only last time and the record.
- Adding, renaming or removing exercises in the app is out of scope; it is the next feature,
  "Exercises library". Until then exercises are managed in the drills tab of the web UI.
- The app does not update the workout or balance tabs when past days are corrected (see Edge
  Cases).
- The number of planned sets is a logging aid only; it is not written to the spreadsheet.
