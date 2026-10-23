---

description: "Task list for Paid or Free Workouts"
---

# Tasks: Paid or Free Workouts

**Input**: Design documents from `/specs/006-workout-paid-status/`

**Prerequisites**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md),
[data-model.md](data-model.md), [contracts/sheets.md](contracts/sheets.md),
[contracts/apps-script.md](contracts/apps-script.md), [contracts/screens.md](contracts/screens.md),
[quickstart.md](quickstart.md)

**Tests**: Required. Constitution II (Test-First) for the app and the script, and V (each new
error path has a test). Every test task is written first and must be seen failing before its
implementation task.

**Organization**: by user story (spec.md): US1 today's switch (P1), US2 default from the previous
workout (P1), US3 status in History (P2), US4 past days and the past-workout dialog (P3).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: can run in parallel (different files, no dependency on unfinished tasks)
- **[Story]**: user story from spec.md

## Path Conventions

Single Android module plus the attached script. Abbreviations used below:
`main/` = `app/src/main/java/com/workoutrec/`, `test/` = `app/src/test/java/com/workoutrec/`,
`uitest/` = `app/src/androidTest/java/com/workoutrec/`, `res/` = `app/src/main/res/`,
`script/` = `apps-script/`. Reports in tests are checked with `test/fakes/FakeMonitoring.kt`.

---

## Phase 1: Setup

**Purpose**: A known-good baseline.

- [X] T001 Run `./gradlew :app:testDebugUnitTest :app:lintDebug` and `node --test "apps-script/tests/*.test.mjs"` on the branch before any change and note the result in this file under T001 (expected: green) **Result 2026-10-24**: green (unit tests, lint; script tests 24/24)

---

## Phase 2: Foundational (blocking all stories)

**Purpose**: The status rule, the phone table, and reading/writing the `workout` tab. Every story
needs them.

### Tests (write first, see them fail)

- [X] T002 [P] Write failing tests in `test/workout/DayStatusRulesTest.kt` (data-model "Domain"): `fromSheet` — number `1` and text `"1"` → FREE; `0`, empty, `"yes"`, `2` → PAID; `toSheet` FREE → 1, PAID → 0; `defaultFor` = status of the latest known date before the date, ignoring later dates and the date itself; no earlier date → FREE; `shown` = the date's own status when known, else the default
- [X] T003 [P] Write a failing migration test in `uitest/workout/data/MigrationTest.kt`: a schema-2 database with a `past_workout_draft` row migrates to 3; the draft keeps its values with `free` = null; table `day_status` exists and is empty
- [X] T004 [P] Write failing DAO tests in `uitest/workout/data/WorkoutDaoV3Test.kt` (data-model "Writes"): upsert a status with `lastSeenFree` stored and returned, and a second upsert of a pending date keeping the first `lastSeenFree`; `dropPendingStatuses(dates)`; `replaceCachedStatuses` deletes all `pending = 0` rows and inserts the given ones but skips dates that have a `pending = 1` row; `markStatusesSynced(dates)` sets `pending = 0`; `keepOnlyNewSets` deletes `pending = 0` rows and keeps pending ones; `clearAll` deletes all — *DAO names: chooseStatus (records lastSeenFree), insertDefaultStatus, replaceCachedStatuses, markStatusesSynced, dropPendingStatuses*
- [X] T005 [P] Write failing client tests in `test/google/SheetsLogClientTest.kt` (contracts/sheets.md): call 20 returns `workoutSheetId` (null when the tab is missing); call 21 with `withWorkout = true` asks for `workout!A2:C` after the optional money range and parses rows — non-number A skipped, B ignored, C via `fromSheet`, date from a serial with no zone shift — and keeps the money range parsing right when both, one or neither optional range is asked for; call 22 with a `WorkoutWrite` sends one `updateCells` per update at column index 2 with `numberValue` 0/1, then one `appendCells` with `[serial, {}, 0|1]` rows in date order, after the money append; nothing else pending + only a `WorkoutWrite` still sends the request

### Implementation

- [X] T006 [P] Create `main/workout/DayStatus.kt`: `enum DayStatus { PAID, FREE }`, `KnownStatus(date, status, pending)`, `object DayStatusRules` with `fromSheet(cell: Any?)`, `toSheet`, `defaultFor(date, known: Map<LocalDate, DayStatus>)`, `shown(date, known)` so T002 passes
- [X] T007 Add `DayStatusEntity` (`@Entity(tableName = "day_status")`: `date` TEXT primary key "ISO yyyy-MM-dd", `free` Boolean, `pending` Boolean, `updatedAt` Long, `lastSeenFree` Boolean? "null = no row then") and `PastWorkoutDraftEntity.free: Boolean?` (nullable, "null in drafts saved before schema 3") in `main/workout/data/Entities.kt`; register the entity, `version = 3`, `AutoMigration(from = 2, to = 3)` in `main/workout/data/WorkoutDatabase.kt`; build to export `app/schemas/com.workoutrec.workout.data.WorkoutDatabase/3.json` so T003 passes
- [X] T008 Add to `main/workout/data/WorkoutDao.kt`: `statuses(): Flow<List<DayStatusEntity>>`, `statusesNow()`, `statusFor(date)`, `upsertStatus`, `replaceCachedStatuses(rows)` and `markStatusesSynced(dates)`, `dropPendingStatuses(dates)` (all `@Transaction`), and the `day_status` lines in `clearAll` / `keepOnlyNewSets` so T004 passes
- [X] T009 Extend `main/google/SheetsLogClient.kt`: `LogSheetInfo.workoutSheetId`, `SheetWorkoutRow(rowIndex, date, status)`, `LogAndDrills.workout`, `readLogAndDrills(id, withMoney, withWorkout)` with range-index tracking, `WorkoutWrite(workoutSheetId, updates: List<WorkoutUpdate(rowIndex, status)>, appends: List<WorkoutAppend(serial, status)>)` and the `workout` parameter of `writeLog` (interface `SheetsLogApi` and `test/fakes/FakeSheetsLogApi.kt` too) so T005 passes — *dates via PaymentValues.dateOf/serial; WorkoutAppend carries the date serial*
- [X] T010a [P] Write a failing test in `test/sync/SheetInfoRefreshTest.kt`: a version-1 cache (no `version` stored) reads call 20 once and stores the workout ID; a version-2 cache does not
- [X] T010b Add `workoutSheetId: Int?` and `version: Int = 2` to `SheetInfoCache` in `main/sync/SyncStatusStore.kt` (stored the same way as the other IDs; a stored cache without `version` reads as 1), and make `LogSync.sheetInfo` read call 20 again when the cached version is below 2, so T010a passes
- [X] T011a [P] Write a failing test in `test/monitoring/NonFatalIssueCatalogTest.kt` for `workout-tab-missing` (WARN) and `script-upgrade-failed` (WARN)
- [X] T011b Add `NonFatalIssue.WORKOUT_TAB_MISSING("workout-tab-missing", WARN)` and `SCRIPT_UPGRADE_FAILED("script-upgrade-failed", WARN)` in `main/monitoring/Monitoring.kt` and their rows in `specs/005-error-reporting-audit/contracts/issues.md`, so T011a passes

**Checkpoint**: rules, storage and client ready; nothing visible yet.

---

## Phase 3: User Story 1 — Mark today's workout paid or free (Priority: P1) 🎯 MVP

**Goal**: Today's Paid | Free control; the choice works offline and reaches today's `workout` row.

**Independent Test**: Log a set, tap Free, sync: today's row has C = `1`; tap Paid: C = `0`.

### Tests for User Story 1 (write first, see them fail)

- [X] T012 [P] [US1] Write failing repository tests in `test/workout/data/DayStatusRepositoryTest.kt` (with a fake DAO or an in-memory store as the existing repository tests do): `setStatus(date, status)` stores `pending = true`; `statuses` flow maps rows to `KnownStatus`; `waitingCount` counts a pending status only when its date has a cached log row or a pending NEW set (data-model "Waiting count"), never a date without sets; three `setStatus` calls for one date before a sync leave one row with the last status and the first `lastSeenFree`; `setStatus` records `lastSeenFree` from the cached value; `statusesNow()` for the sync — *pure part in test/workout/WaitingCountTest.kt; DB part in uitest/workout/data/DayStatusRepositoryTest.kt (Room needs a device)*
- [X] T013 [P] [US1] Write failing sync tests in `test/sync/LogSyncDayStatusTest.kt` (contracts/sheets.md "What gets written"): pending FREE for a date with a log row and no `workout` row → one append `[serial, FREE]`; date with a row whose C differs → one update of that row; equal → no write and marked synced; date with no set in the log but a NEW set appended in the same run → append; date with no set at all → nothing written, stays pending; two rows for one date → only the first is updated; `withWorkout` is asked only when `workoutSheetId` is known; after the write `markStatusesSynced` and `replaceCachedStatuses` from the fresh read run in the commit; no pending statuses → cache still replaced from the read; the sheet still has the last-seen value (or still no row) → the phone's status is written; the sheet changed since → nothing written, pending dropped (`dropPendingStatuses`), one `DAY_STATUS_DROPPED` notice for that date; several `setStatus` calls before the run produce one update or append with the last status — *conflict only when the phone change saw a row (lastSeen set) and the sheet differs now; a row added since a change made without one (the nightly script) is not a conflict*
- [X] T014 [P] [US1] Write a failing test in `test/sync/LogSyncDayStatusTest.kt`: no `workout` tab (null ID) with a pending status → sets still synced, status stays pending, exactly one `WORKOUT_TAB_MISSING` report per run with no date or status in its details
- [X] T015 [P] [US1] Write failing Compose tests in `uitest/log/DayStatusControlTest.kt` (contracts/screens.md): the control shows both options with `_paid` / `_free` tags, the given status selected; a tap on the other option calls `onChange` once with it; a tap on the selected one does nothing; Today (`TodayScreen` with a status and callback) shows `today_day_status` above the exercises; `TodayRoute` with yesterday PAID known and no status for today shows Paid, and with nothing known shows Free — *Today tests go through TodayScreen(today, statuses, onDayStatus)*

### Implementation for User Story 1

- [X] T016 [US1] Add to `main/workout/data/WorkoutRepository.kt`: `statuses: Flow<List<KnownStatus>>`, `setStatus(date, status)` (records `lastSeenFree` from the cache), `statusesNow()`, the waiting-count term, and implement the store methods `LogSync` needs (`LogSyncStore.statusesNow()`, `commit(..., syncedStatusDates, cachedStatuses)` default parameters in `main/sync/LogSync.kt`) so T012 passes
- [X] T017 [US1] Add the status step to `LogSync.runFor` in `main/sync/LogSync.kt` per research R4: read with `withWorkout = sheet.workoutSheetId != null`; the days with sets = fresh log days ∪ days of this run's set appends (sheet time zone); apply the last-seen check first (changed → drop + `NoticeKind.DAY_STATUS_DROPPED`, shown by the existing `SyncNoticeList` in `main/log/`); build `WorkoutWrite`; include it in `wrote`; pass synced dates and the fresh `workout` rows to `store.commit`; report `WORKOUT_TAB_MISSING` when statuses wait and the ID is null, so T013 and T014 pass
- [X] T018 [P] [US1] Create `main/log/DayStatusControl.kt`: `DayStatusControl(status: DayStatus, onChange: (DayStatus) -> Unit, tag: String, modifier)` as a Material 3 `SingleChoiceSegmentedButtonRow` (Paid, Free; option tags `tag + "_paid"` / `"_free"`; content description `day_status_title`); add `day_status_paid`, `day_status_free`, `day_status_title` and the notice text `notice_day_status_dropped` (en: "Changed in the spreadsheet: %1$s kept as %2$s", ru: "Изменено в таблице: %1$s оставлено как «%2$s»") to `res/values/strings.xml` and `res/values-ru/strings.xml` (Платная, Бесплатная, Тренировка)
- [X] T019 [US1] Add a `dayStatus: DayStatus` + `onDayStatus` parameter pair to `TodayScreen` in `main/log/TodayScreen.kt` (control under the top bar, above the groups and the notices) and wire `TodayRoute` in `main/log/LogRoutes.kt`: today = `LocalDate.now(zone)`, shown = `DayStatusRules.shown(today, statuses)`, change → `setStatus` + `syncScheduler.requestSync()`, so T015 passes

**Checkpoint**: MVP: the switch works and reaches the spreadsheet (with the default still Free until US2).

---

## Phase 4: User Story 2 — Sensible default from the previous workout (Priority: P1)

**Goal**: New days start with the previous workout day's status (Free for the first), fixed when
the day starts; the nightly script uses the same rule and fills durations; existing scripts are
upgraded.

**Independent Test**: Yesterday Paid → Today shows Paid and the first set stores Paid; nothing
earlier → Free; a web-only day gets the previous flag at night.

### Tests for User Story 2 (write first, see them fail)

- [X] T020 [P] [US2] Write failing repository tests in `test/workout/data/DayStatusRepositoryTest.kt`: `confirmSet` of the first set of a date without a status stores `defaultFor(date)` with `pending = true` in the same transaction; a second set does not change it; a date that already has a status (chosen or cached) keeps it; the date uses the sheet time zone; changing an earlier date later does not change this date (FR-015) — *uitest/workout/data/DayStatusRepositoryTest.kt*
- [X] T021 [P] [US2] Write failing Node tests in `script/tests/logic.test.mjs` for `workoutRowsPlan` (contracts/apps-script.md): a log day without a row → append `[first, minutes, flag]`; flag copies the latest earlier day's flag among existing rows (`1`/`"1"` → 1, `0`/`''` → 0); with two new days the second copies the first's planned flag; no earlier day → 1; an existing row with `''` duration and log sets → `durations` entry with sheet row number (header = 1); a row with a duration or without log sets → untouched; existing flags never changed
- [X] T022 [P] [US2] Write failing Node tests in `script/tests/code.test.mjs`: `dailyJob` writes each duration with `getRange(row, 2).setValue(minutes)`, appends the planned rows, then sets the balance from all non-empty rows as before
- [X] T023 [P] [US2] Write failing tests in `test/automation/ScriptUpgradeTest.kt` (contracts/apps-script.md "Upgrading"): `upgrade` uploads the same five files as `install` (Config with `SCRIPT_VERSION = 2`) to the recorded script ID and upserts `workout_rec.script_version = 2`, without `createVersion` or `deployWebApp`; `install` also writes `script_version`; extend `test/automation/ScriptInstallerTest.kt` for the new metadata key
- [X] T024 [P] [US2] Write failing tests in `test/setup/ScriptUpgradeRefreshTest.kt`: `SpreadsheetSetupFlow.refresh` calls `upgrade` when `script_id` is set and `script_version` is missing or `1`; not when `2`, not when no script; an upgrade failure leaves the refresh result unchanged and is reported once as `SCRIPT_UPGRADE_FAILED` (no-connection → `NO_CONNECTION` with `operation=script-upgrade`; `AppsScriptApiDisabled` → `SCRIPT_UPGRADE_FAILED` with the `reportable` message); the next refresh tries again

### Implementation for User Story 2

- [X] T025 [US2] In `main/workout/data/WorkoutRepository.kt` and `main/workout/data/WorkoutDao.kt`: `confirmSet` / `confirmSets` insert the default status for each new date without one, inside the existing transaction; the repository gets `zone: suspend () -> ZoneId` (from `SyncStatusStore`, as `SyncStatus.zone()`) wired in `main/AppContainer.kt`, so T020 passes — *WorkoutDao.insertSetsWithDefaults; repository zone = sync status zone flow*
- [X] T026 [US2] Replace `dailyWorkoutRows` with `workoutRowsPlan` in `script/Logic.gs` so T021 passes; update `dailyJob` in `script/Code.gs` to read the `workout` rows, apply durations and appends, then the balance, so T022 passes (the app bundles `apps-script/` automatically via `copyScriptAssets` in `app/build.gradle.kts`)
- [X] T027 [US2] In `main/automation/ScriptInstaller.kt`: `SCRIPT_VERSION = 2`, `upgrade(spreadsheetId, scriptId, timeZone, existingIds)` on `ScriptInstall`, and `script_version` written by `install` and `upgrade`; key constant `SCRIPT_VERSION` = `workout_rec.script_version` in `main/automation/AutomationStatus.kt`; fake in `test/fakes/FakeSpreadsheetSetup.kt`, so T023 passes
- [X] T028 [US2] In `main/setup/SpreadsheetSetupFlow.kt` `refresh`: after the snapshot, upgrade when needed (try/catch rethrowing `CancellationException`, `monitoring.failure(SCRIPT_UPGRADE_FAILED, e, "script-upgrade")`), so T024 passes; add nothing to `testres/normal-outcomes.txt` (the catch reports)
- [X] T029 [US2] Removed: the "Today shows the default before the first set" test moved into T015, written before T019 (analysis F1); kept so task IDs stay stable — *placeholder, nothing to do*

**Checkpoint**: US1 + US2 = the requested behavior on Today, correct balance for every day.

---

## Phase 5: User Story 3 — See paid or free in History (Priority: P2)

**Goal**: Each past day in History shows Paid/Free with the waiting mark; the day details show the
status.

**Independent Test**: With paid and free rows in the `workout` tab, History's labels match.

### Tests for User Story 3 (write first, see them fail)

- [ ] T030 [P] [US3] Write failing tests in `test/workout/DisplayModelTest.kt`: `pastDays(sets, today, zone, statuses)` gives each `DaySummary` its shown status and `statusPending`; a day without a known status gets the default
- [ ] T031 [P] [US3] Write failing Compose tests in `uitest/log/HistoryTabTest.kt`: each day line shows `day_status_label` with "Paid"/"Free"; a pending status also shows the waiting mark; the day details show `day_status` with the day's status

### Implementation for User Story 3

- [ ] T032 [US3] Add `status: DayStatus` and `statusPending: Boolean` to `DaySummary` in `main/workout/Model.kt` and compute them in `DisplayModel.pastDays` in `main/workout/DisplayModel.kt`, so T030 passes
- [ ] T033 [US3] Show the label and waiting mark in `PastDaysScreen` in `main/log/CorrectionScreens.kt`; wire statuses in `WorkoutsRoute` in `main/log/LogRoutes.kt`, so the list part of T031 passes
- [ ] T034 [US3] Show `DayStatusControl` (tag `day_status`) at the top of `DayDetailScreen` in `main/log/CorrectionScreens.kt` with the day's shown status, read-only wiring in `DayRoute` for now (changes come in US4; same stored status as Today's control, spec US4 scenario 5), so the details part of T031 passes

**Checkpoint**: statuses visible in History.

---

## Phase 6: User Story 4 — Fix a past day, or set it when entering a past workout (Priority: P3)

**Goal**: Change a past day's status from its details; choose it in "Enter a past workout".

**Independent Test**: Past paid day → Free → its row C = `1`; past workout saved as Paid → its new
row C = `0`.

### Tests for User Story 4 (write first, see them fail)

- [ ] T035 [P] [US4] Write failing Compose tests in `uitest/log/CorrectionsTest.kt`: in day details, tapping Free saves FREE for that date (fake repository) and the list then shows Free with the waiting mark
- [ ] T036 [P] [US4] Write failing tests in `test/log/PastWorkoutViewModelTest.kt`: `setup(date, start, duration, status)` keeps the status; `save()` stores it for the date with the sets (`confirmSets(keys, status)`), overriding the default; without a chosen status the default applies
- [ ] T037 [P] [US4] Write failing Compose tests in `uitest/log/PastWorkoutTest.kt`: the dialog shows `past_workout_status` with the default for its date (the latest earlier known status, Free if none) or the date's own status; editing the date to another valid date updates it unless the user picked an option; the chosen status is passed to `onStart`

### Implementation for User Story 4

- [ ] T038 [US4] Wire `DayRoute` in `main/log/LogRoutes.kt`: change → `setStatus(date, status)` + `requestSync()`, so T035 passes
- [ ] T039 [US4] Add the status to `PastWorkoutStore.confirmSets(keys, status)`, `PastWorkoutViewModel.setup/save` and the draft's `free` column (`main/log/PastWorkoutViewModel.kt`, `main/workout/data/WorkoutRepository.kt`), the chosen status replacing the default in the same transaction, so T036 passes
- [ ] T040 [US4] Add the control to `PastWorkoutDialog` in `main/log/PastWorkoutScreens.kt` with `defaultFor`/`shown` from the known statuses and the date-follow rule, `onStart(date, start, duration, status)`, wired in the past-workout route, so T037 passes

**Checkpoint**: all four stories done.

---

## Phase 7: Polish & Cross-Cutting Concerns

- [ ] T041 [P] Add the status column to the `workout` row of `specs/001-google-account-setup/contracts/spreadsheet.md` notes ("work alone: `1` = Free, else Paid; written by the app and the script"), the metadata key `workout_rec.script_version`, and the v2 note in `specs/001-google-account-setup/contracts/apps-script.md`
- [ ] T042 [P] README: one paragraph on Paid/Free and where it is stored
- [ ] T043 Run the full suite locally: `./gradlew :app:testDebugUnitTest :app:lintDebug` and the Node tests; the catch-site guard passes with the new catch in T028
- [ ] T044 Open the PR(s) (US1–US2 first if large), CI green (unit, UI, script), merge per the standing rule; note the release build number here
- [ ] T045 Device validation (user, quickstart #6–#15) on a test copy of the spreadsheet; record results in `specs/006-workout-paid-status/quickstart-results.md`

---

## Dependencies & Execution Order

- **Setup (T001)** → **Foundational (T002–T011b)** → stories.
- **US1 (T012–T019)** needs the foundation. **US2 (T020–T029)** needs US1's repository (T016) and
  control (T019); its script part (T021, T022, T026) and upgrade part (T023, T024, T027, T028)
  only need the foundation and can start right after it.
- **US3 (T030–T034)** needs T016 (statuses flow) and T018 (control). **US4 (T035–T040)** needs US3's
  day details (T034).
- **Polish** after the stories it documents.
- Within a story: tests first, seen failing; then implementation.

## Parallel Examples

- Foundation tests: T002, T003, T004, T005, T010a, T011a together; then T006 alongside T007–T009.
- US2: script (T021 → T022 → T026) and upgrade (T023 → T024 → T027 → T028) in parallel with the
  repository default (T020 → T025).
- US3 and US4 tests (T030, T031, T035–T037) can be written together once T034's screen exists.

## Implementation Strategy

1. **MVP = US1 + US2** (both P1): the switch on Today, the default, the script v2 and its
   upgrade. Ship as one PR when small enough; otherwise US1 alone first, since a Free-by-default
   switch already fixes the balance for alone workouts.
2. **US3** next (History labels), then **US4** (editing past days).
3. Device validation (T045) after the release containing US2, since the script upgrade and the
   nightly run can only be checked on a real spreadsheet.
