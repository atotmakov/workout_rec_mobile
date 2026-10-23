# Research: Paid or Free Workouts

Decisions for [plan.md](plan.md). Earlier features' research (002 R1–R14, 004 R1–R14, 005 R1–R10)
still applies; "002-R5" points there.

## R1. Where the status lives in the spreadsheet

- **Decision**: The existing `workout` tab, column C "work alone": Free = `1`, Paid = `0`. When
  reading, `1` (number) or `"1"` (text) is Free and anything else is Paid, exactly as the nightly
  balance counts (`String(flag) !== '1'`). The app writes numbers only.
- **Rationale**: The request names this tab; the balance already uses this column, so no
  structure change, no new tab and no migration of existing spreadsheets (spec FR-012).
- **Alternatives**: a new column D "paid" (changes the structure match rule and the balance);
  a new tab (duplicates the day list).

## R2. One status per day on the phone, fixed when the day starts

- **Decision**: The phone keeps a **day status** per date in a new table `day_status`
  (date, free, `pending`, updatedAt). A row is written:
  - when the user changes the status (Today, a day's details, the past-workout dialog);
  - when a day's **first set** is confirmed on the phone (Today logging or a saved past
    workout) and the date has no status yet: the default (R3) is written then, `pending = true`.

  `pending = false` rows are the cache of the `workout` tab (R6). The status shown for a date is
  `day_status` for that date, else the default (R3).
- **Rationale**: Writing the default when the day starts keeps a later change of an earlier day
  from changing this day (spec FR-015, US2 scenario 4), and sends every app-logged day's status to
  the spreadsheet, so the nightly script never has to guess it.
- **Alternatives**: computing the default every time it is shown (an earlier day's change would
  ripple forward); separate tables for pending and cached values (the shown value then needs a
  merge everywhere; one table with a flag mirrors how the UI uses it).

## R3. The default rule

- **Decision**: `DayStatusRules.defaultFor(date, known)` = the status of the latest date before
  `date` in `known`, else Free. `known` = all `day_status` rows (phone choices and the cached
  `workout` tab). Pure Kotlin, unit-tested. The script uses the same rule over the `workout` tab
  (R5).
- **Rationale**: Spec FR-003; "previous workout day" is the latest earlier date with a status,
  whichever side recorded it (spec Assumptions).
- **Alternatives**: the previous day with sets in the log (a day logged in the web UI without a
  row yet has no status to copy).

## R4. Writing the status (sync, call 22)

- **Decision**: Statuses join the existing sync run, same atomic `batchUpdate` as sets:
  1. Call 21 also reads `workout!A2:C` when call 20 found the tab (R6).
  2. For each pending status whose date has a set in the fresh log or among this run's set
     appends (spec US1 scenario 4: no row for a day without sets):
     - the first `workout` row with that date exists → if column C already equals the status, it
       is done; else `updateCells` at column C of that row;
     - no row → `appendCells` `[date serial, (empty), 0|1]`.
  3. After a successful write: the pending rows are marked synced; the fresh read refreshes the
     cache.

  A pending status for a date with no sets yet stays pending and is not counted as waiting
  (spec FR-009 counts only what sync can send).
- **Conflict rule (constitution III)**: like set edits (002 FR-015), each phone change records
  the spreadsheet value it replaced (`lastSeenFree`; null = no row then). At sync, if the date's
  first row still has that value (or there is still no row), the phone's choice is written. A row
  that appeared since a change made without one is not a conflict: it is normally the nightly
  script's row for the day. If the spreadsheet value changed since, the spreadsheet wins: the
  pending change is dropped and a
  `DAY_STATUS_DROPPED` notice ("Changed in the spreadsheet: <date> kept as <status>") is shown with
  the existing sync notices. The default written with a day's first set records the cached value
  at that moment the same way. Without a pending change the spreadsheet wins (spec FR-008).
  Several rows for one date: only the first is read and written (spec edge case).
- **Rationale**: Reuses the read–match–write–reread run of 002 R5 and 004 R5; sets and statuses
  reach the sheet together, so the date's row is created in the same request that adds its first
  set.
- **Alternatives**: a separate request after the set write (two requests, partial failures);
  waiting for the nightly script to create the row and then updating it (the status reaches the
  sheet a day late; SC-002 asks for 1 minute).

## R5. The nightly script, version 2

- **Decision**: `Logic.gs` replaces `dailyWorkoutRows` with
  `workoutRowsPlan(logRows, workoutRows, toDayKey)` returning:
  - `appends`: one row per log day without a `workout` row, `[first set, duration, flag]`, where
    `flag` is the previous workout day's flag (`1` → `1`, else `0`) over existing rows and the
    appends before it in date order, `1` (Free) when there is none (spec FR-006);
  - `durations`: `{row, minutes}` for existing rows whose duration cell is empty and whose day is
    in the log: the rows the app added (spec FR-005).

  `dailyJob` applies both, then computes the balance as before. `SCRIPT_VERSION = 2`.
- **Rationale**: The app adds rows without a duration because today's last set isn't known when
  the row is created; at 03:00 the day is over. The rule for days the app never saw (sets typed in
  the web UI) must match the app's.
- **Alternatives**: the app writes and keeps updating the duration (rewrites a cell a user may have
  edited; every sync touches the row); leaving the script alone (days logged in the web UI stay
  Paid, and app-added rows never get a duration).

## R6. Reading the `workout` tab

- **Decision**: Call 20 also keeps the `workout` sheet ID (`SheetInfoCache.workoutSheetId`, null
  when the tab is missing). A cache written before this feature has no marker, so the run reads
  call 20 once more (as 004 did with `drillsSheetId`): `SheetInfoCache.version` = 2. Call 21 adds
  `workout!A2:C` only when the ID is known (a range naming a missing tab fails the whole request,
  004 contracts/sheets.md). Rows: `A` a number (date serial → local date, no time zone shift, as
  for payments), `C` → Free/Paid (R1); rows without a date are skipped. Every run replaces the
  `pending = false` rows of `day_status` with the tab's values, never touching pending rows.
- **Missing tab**: statuses stay pending; the run still syncs sets; `NonFatalIssue.WORKOUT_TAB_MISSING`
  (WARN) is reported once per run, rate-limited (constitution V, spec edge case).
- **Rationale**: The tab is one row per day (a few hundred rows a year), read with the log in the
  same call.
- **Alternatives**: reading it only when statuses wait (History then shows no spreadsheet values,
  FR-007).

## R7. Updating the script in spreadsheets that already have it

- **Decision**: New developer metadata `workout_rec.script_version`, written by the app after
  every upload. `ScriptInstaller.upgrade(spreadsheetId, scriptId, timeZone, metadataIds)` uploads
  the bundled files with `projects.updateContent` (same files as `install`) and writes the
  version. `SpreadsheetSetupFlow.refresh` (the background check on every app start, feature 001)
  calls it when a script ID is recorded and the version is missing or lower than
  `SCRIPT_VERSION`. No new deployment: the "Enable automation" page (`doGet`) is unchanged, and
  the nightly time trigger runs the project's current code.
  - Failures don't change what the user sees: the upgrade is tried again on the next start.
    They are reported as `SCRIPT_UPGRADE_FAILED` (WARN) through `Monitoring.failure`, so a
    missing connection is `no-connection` (005 R1); the Apps Script API switched off is reported
    too, as `Apps Script API disabled` (005 R10), since here it silently keeps the old script.
- **Rationale**: Today `install` returns early when a script exists, so script changes never
  reach existing spreadsheets. The token already carries `script.projects` (feature 001), so no
  new consent.
- **Alternatives**: asking the user to re-run "Enable automation" (a manual step for every
  script change); a new deployment version each time (only needed if `doGet` changes).
- **Risk**: that time-based triggers run the current code rather than a deployed version is
  verified on the device (quickstart #9): after the upgrade, the next nightly run fills the
  durations of app-added rows.

## R8. Screens

- **Decision**: One control everywhere: a two-option segmented button **Paid | Free**
  (`SingleChoiceSegmentedButtonRow`, Material 3, already in the app's dependency).
  - Today: under the top bar, above today's exercises, `today_day_status`.
  - History list: a short label "Paid" / "Free" at the end of each day's line, with the existing
    waiting mark when the status is pending, `day_status_label`.
  - Day details: the segmented button under the top bar, `day_status`.
  - Past-workout dialog: the segmented button under the duration field, `past_workout_status`,
    initial value = the date's status or default (R3), updated when the date field changes to a
    valid date.

  History lists only past days (feature 004), so today's status is changed on Today; the day
  details and Today use the same store, so both always agree (spec US4 scenario 5: one stored
  status per date).
- **Rationale**: Two named options read better than an on/off switch whose "on" side is unclear;
  same control in all four places.
- **Alternatives**: `Switch` labelled "Paid" (the off state is implicit); a chip per day in History
  that toggles from the list (easy to change by accident while scrolling).

## R9. Phone database

- **Decision**: Room schema 3: new table `day_status`. `AutoMigration(2, 3)`. (A `free` column
  on `past_workout_draft` was planned and dropped during implementation: that table is not used;
  the past-workout draft lives in its ViewModel.) Sign out and account switch clear it with
  `clearAll`; a rewritten spreadsheet (002 R13) keeps pending statuses like new sets and drops the
  cached ones.
- **Rationale**: Added table and nullable column only, as 004 R9.

## R10. Testing (constitution II)

- JVM: `DayStatusRulesTest` (default rule, first day Free, unknown values Paid);
  `LogSyncDayStatusTest` (append vs update vs already equal, no sets → stays pending, missing tab →
  report, pending wins, cache replaced, several rows for a date); `SheetsLogClientTest` cases for
  the call 21 range and call 22 requests (MockWebServer); repository tests for "first set writes
  the default", waiting count; `ScriptUpgradeTest` (upgrade when version missing/lower, not when
  equal, failure reported, retried next time); migration test 2 → 3.
- Node: `logic.test.mjs` for `workoutRowsPlan` (default from previous, first Free, fills empty
  durations only, keeps app flags); `code.test.mjs` for `dailyJob` writes.
- Compose UI: Today segmented button, History label and waiting mark, day details button,
  past-workout dialog default and change.
