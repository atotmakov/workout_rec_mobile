---

description: "Task list for Log New Workout"
---

# Tasks: Log New Workout

**Input**: Design documents from `/specs/002-log-workout/`

**Prerequisites**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md),
[data-model.md](data-model.md), [contracts/](contracts/), [quickstart.md](quickstart.md)

**Tests**: Required. The constitution (Principle II, Test-First, non-negotiable) requires tests to
be written and observed failing before implementation. There is no local JDK: "observe failing"
means pushing the tests with compiling stubs and seeing the CI run red, then pushing the
implementation and seeing it green (see README, CI).

**Organization**: Tasks are grouped by user story so each story can be implemented and tested
independently.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies on incomplete tasks)
- **[Story]**: Which user story this task belongs to (US1–US5)

## Path Conventions

- App code: `app/src/main/java/com/workoutrec/` (below: `main/…`)
- JVM tests: `app/src/test/java/com/workoutrec/` (below: `test/…`)
- Instrumented tests (CI emulator): `app/src/androidTest/java/com/workoutrec/` (below: `androidTest/…`)
- Strings: `app/src/main/res/values/strings.xml` (EN) and `values-ru/strings.xml` (RU); every new
  user-visible text goes into both (FR-016; lint fails on missing translations)

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: New dependencies and the spike that decides how rows are written

- [X] T001 Add to `gradle/libs.versions.toml`: `room = "2.6.1"` (`androidx.room:room-runtime`, `room-ktx`, `room-compiler`, `room-testing`), `ksp = "2.1.0-1.0.29"` plugin `com.google.devtools.ksp`, `work = "2.10.0"` (`androidx.work:work-runtime-ktx`, `work-testing`) per research R14; apply the KSP plugin in root `build.gradle.kts` (apply false) and `app/build.gradle.kts`; add `ksp { arg("room.schemaLocation", "$projectDir/schemas") }`, the runtime deps, `androidTestImplementation` of `room-testing`, `work-testing`, and add `app/schemas/` to the androidTest assets source set; push and confirm the CI build is green
- [X] T002 Spike R4 (manual, blocks T021): on a throw-away spreadsheet with the reference structure (rename one of the `workout_rec_database_backup_*` files), use the Sheets API Explorer `spreadsheets.batchUpdate` with one `appendCells` row (per [contracts/sheets-log.md](contracts/sheets-log.md) call 22) and check the three cases in [quickstart.md](quickstart.md) "Spike"; ask the user before running it in their browser; record requests, responses and screenshots of the result in `specs/002-log-workout/spike-results.md`; — **Done 2026-10-06: all three cases pass, see [spike-results.md](spike-results.md)**

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Domain values, time conversion, local database, Sheets calls, display model —
needed by every story

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

### Tests (write first, push red)

- [X] T003 [P] Write failing tests in `test/workout/ValuesTest.kt` for `Weight.parse` and `Reps.parse` per [data-model.md](data-model.md) value types: `Weight` "0 ≤ w ≤ 999.99", "parsed from \"17,5\" or \"17.5\"", at most 2 decimals ("17,555" invalid), empty/negative/text invalid with a reason; `Reps` "1 ≤ r ≤ 999", whole numbers only ("1,5" and "0" invalid); `Weight.step(±1)` changes by 0.5 "clamped at 0"; `Reps.step(±1)` "clamped at 1"; `Weight("17.50") == Weight("17,5")`; display formatting with the locale's decimal separator ("17,5" in RU, "17.5" in EN, "20" for whole numbers)
- [X] T004 [P] Write failing tests in `test/workout/SheetTimeTest.kt` for serial ↔ epoch (research R8): `1899-12-30T00:00` = 0; `2026-01-12T18:30:15` in `Europe/Moscow` ↔ the matching serial; serial values read with float noise round to the nearest second; a date-only serial is 00:00:00; DST change day in `Europe/Berlin`; `workoutDay(epoch, zone)` returns the local date
- [X] T005 [P] Write failing tests in `test/workout/DisplayModelTest.kt` for the display model in [data-model.md](data-model.md) "Display model": cache rows + pending `NEW` (no `draftId`) → `DisplaySet`s with `syncState` `Synced` / `NotSynced`; pending `EDIT` replaces values and is `NotSynced`; pending `DELETE` hides the row; pending with `draftId` is not shown; "Today's workout": exercises in order of their first set time, sets in time order; "Past days": days before today, newest first, with exercise and set counts; recent exercises = last-set-time order, max 5
- [X] T006 [P] Write failing contract tests in `test/google/SheetsLogClientTest.kt` with MockWebServer for [contracts/sheets-log.md](contracts/sheets-log.md): call 20 request URL/fields and parsing of `timeZone` and the `log` sheet ID, missing `log` or `drills` → `Structure` failure; call 21 query (`ranges=log!A2:D`, `ranges=drills!A2:B`, `UNFORMATTED_VALUE`, `SERIAL_NUMBER`, `ROWS`) and parsing (row *i* → `rowIndex = i + 1`, short rows, empty values, text in C/D skipped); call 22 body JSON for delete, edit (columns B–D only, `fields: userEnteredValue`) and `appendCells` exactly as in the contract; error mapping reuses `ApiErrorMapper`
- [X] T007 Write failing instrumented tests in `androidTest/workout/data/WorkoutDaoTest.kt` (in-memory Room): `replaceCaches(exercises, logRows)` replaces both tables in one transaction; `pending_change` insert/query ordered by `createdAt`; queries return `Flow`s that emit after changes; data survives closing and reopening a file-based database (FR-005)
- [X] T008 Add compiling stubs (`TODO()` bodies) for everything T003–T007 reference so the CI run compiles and the new tests fail; push and record the red run ID in this task — **red run 37373495944** (34 unit tests and 2 DAO transaction tests failing)

### Implementation (make green)

- [X] T009 [P] Implement `main/workout/Values.kt`: `Weight` (BigDecimal scale 2), `Reps`, `SetKey(time, exercise, weight, reps)`, parse results with reasons, steppers, formatting, so T003 passes
- [X] T010 [P] Implement `main/workout/SheetTime.kt` (serial ↔ epoch seconds in a `ZoneId`, `workoutDay`) so T004 passes
- [X] T011 [P] Implement Room in `main/workout/data/Entities.kt` (`exercise`: `name` PK, `muscleGroup`, `sheetOrder`; `log_row`: `rowIndex` PK, `time`, `exercise`, `weight`, `reps`; `pending_change`: `id` UUID PK, `kind` `NEW|EDIT|DELETE`, `time`, `exercise`, `weight`, `reps`, nullable `lastSeen*` key fields, nullable `rowHint`, `createdAt`, nullable `draftId`; `past_workout_draft`: `id`, `date`, `start`, `durationMinutes`; `sync_notice`: `id` auto, `kind` `CONFLICT_DROPPED|WORKOUT_ROW_NOT_UPDATED`, key fields, `createdAt`), `main/workout/data/WorkoutDao.kt`, `main/workout/data/WorkoutDatabase.kt` (version 1, exported schema in `app/schemas/`), type converters for `Weight`, so T007 passes
- [X] T012 Implement `main/workout/DisplayModel.kt` (pure functions over cache rows + pending changes + today's date) so T005 passes
- [X] T013 Extend `main/google/SheetsClient.kt` (or a new `main/google/SheetsLogClient.kt` behind a `SheetsLogApi` interface) with calls 20–22 so T006 passes; add the fake `test/fakes/FakeSheetsLogApi.kt` (in-memory log rows; records requests; can fail the next call with a given `ApiError`; applies `deleteDimension`, `updateCells` and `appendCells` to its rows like the server)
- [X] T014 Implement `main/sync/SyncStatusStore.kt` (DataStore keys `sync.state`, `sync.lastSuccessAt`, `sync.spreadsheetId`, `sync.timeZone` per data-model "Sync status") and `main/workout/data/WorkoutRepository.kt` (exposes display-model flows; `confirmSet`, `replaceCaches`, `clearAll`), and wire the database, repository and Sheets log client in `main/AppContainer.kt`; push and confirm green

**Checkpoint**: Foundation ready — user stories can start

---

## Phase 3: User Story 1 - Log sets of an exercise (Priority: P1) 🎯 MVP

**Goal**: Today screen → Add exercise → pick → plan sets → confirm sets (with steppers) → rows in
the log tab when online

**Independent Test**: With a set-up account online, pick an exercise, plan 3 sets, confirm each;
the log tab gets exactly 3 rows with exercise, weights, reps and confirm times (spec US1)

### Tests for User Story 1 (write first, push red)

- [X] T015 [P] [US1] Write failing tests in `test/workout/LiveSetTimeTest.kt`: a confirmed live set gets the confirm time (whole seconds); a set confirmed in the same second as the previous set gets +1 s (research R5)
- [X] T016 [P] [US1] Write failing tests in `test/sync/LogSyncNewSetsTest.kt` against `FakeSheetsLogApi` per [contracts/sheets-log.md](contracts/sheets-log.md) "Sync algorithm": pending `NEW` sets are appended in `createdAt` order in one `appendCells`; a `NEW` whose `SetKey` is already in the log is not written again and is removed (FR-009); after success the caches are replaced and handled pending changes removed in one transaction; nothing pending → no call 22; call 20 is made once per spreadsheet ID; drafts (`draftId`) are never written
- [X] T017 [P] [US1] Write failing tests in `test/log/ExerciseLoggingViewModelTest.kt`: planned count creates that many rows (1–20); `Add set` adds a row copying the previous row's values; confirm is disabled while weight or reps is invalid and exposes the reason; confirming saves a pending `NEW` immediately (before any network call) and triggers a sync request; unconfirmed rows are discarded on leave (FR-003); picking an exercise already in today's workout adds rows to it
- [X] T018 [P] [US1] Write failing Compose UI tests in `androidTest/log/TodayScreenTest.kt`, `androidTest/log/ExercisePickerTest.kt`, `androidTest/log/ExerciseLoggingScreenTest.kt` per [contracts/screens.md](contracts/screens.md) tags: `today_empty`, `add_exercise`, today's exercises list; picker groups by muscle group, `exercise_search` filters case-insensitively by part of the name, `exercise_recent` hidden while searching, `exercise_list_empty` with "Open drills tab"; `plan_sets` − / + within 1–20; logging rows with `set_weight_*`, `set_reps_*`, steppers ±0.5 kg / ±1 rep, `set_confirm_*` disabled on invalid input with the error text, done rows show sync state
- [X] T019 [US1] Add compiling stubs for T015–T018, push, and record the red run ID in this task — **red run 37518281582** (20 unit + 9 UI tests failing)

### Implementation for User Story 1

- [X] T020 [P] [US1] Implement live set timing in `main/workout/LiveSetTime.kt` so T015 passes
- [X] T021 [US1] Implement `main/sync/LogSync.kt` steps 1–6 for `NEW` sets (authorize via `ApiAuthorizer`, call 20 cache, call 21, key matching, call 22 `appendCells`, re-read, one-transaction commit) so T016 passes; `EDIT`/`DELETE` handling is added in US4
- [X] T022 [US1] Implement `main/sync/SyncScheduler.kt` in-process part: process-wide `Mutex`, `requestSync()` runs `LogSync` in the app scope when online (no WorkManager yet); call it after each confirmed set and on app start
- [X] T023 [P] [US1] Implement `main/log/TodayViewModel.kt` and `main/log/TodayScreen.kt` (today's exercises from the display model, empty state, `add_exercise`), and show it as the content of `main/home/MainScreen.kt` (FR-000); keep the account avatar and automation reminder
- [X] T024 [P] [US1] Implement `main/log/ExercisePicker.kt` (grouped by muscle group, "Other" for empty groups, search, recent, empty state opening the spreadsheet URL `https://docs.google.com/spreadsheets/d/{id}/edit` via `Browser.open`)
- [X] T025 [US1] Implement `main/log/PlanSetsDialog.kt`, `main/log/SetRow.kt`, `main/log/ExerciseLoggingViewModel.kt`, `main/log/ExerciseLoggingScreen.kt` (planned rows, steppers, number tap → numeric keyboard, validation messages, confirm, add set) so T017 passes; planned count defaults to 3 here (pre-fill comes in US3)
- [X] T026 [US1] Add navigation routes Today → picker → plan dialog → logging in `main/MainActivity.kt`; add all US1 strings (EN + RU); push and confirm T018 and the whole suite are green

**Checkpoint**: US1 works online end-to-end — MVP

---

## Phase 4: User Story 2 - Log a workout without connection (Priority: P1)

**Goal**: Logging never needs the network; sets sync by themselves after reconnecting, also with
the app closed; status is visible

**Independent Test**: Airplane mode, log 2 exercises, close the app, airplane mode off; all sets
appear once, in order, with original times, without opening the app (spec US2)

### Tests for User Story 2 (write first, push red)

- [X] T027 [P] [US2] Write failing tests in `test/sync/LogSyncErrorsTest.kt` per contracts/sheets-log.md "Errors": `Offline`/`ServiceUnavailable` → `Failing(network)` and retry requested, pending kept; `TokenExpired` → one re-authorize and repeat; consent needed / `AccessDenied` → `NeedsSignIn`; `NotFound` → `Failing(spreadsheet)`; `Structure` → `Failing(structure)`; `Unexpected` → `Failing(other)`; failure after call 22 succeeded but before commit (simulated crash) → next run finds the keys and writes nothing twice (FR-009, SC-004); success sets `Idle` and `lastSuccessAt`
- [X] T028 [P] [US2] Write failing tests in `test/sync/SpreadsheetChangeTest.kt` (research R13): when the bound spreadsheet ID differs from `sync.spreadsheetId`, caches, `EDIT`/`DELETE` pending changes and notices are cleared, `NEW` pending sets are kept and written to the new spreadsheet; sign-out `clearAll()` empties the database
- [X] T029 [P] [US2] Write failing instrumented test `androidTest/sync/SyncWorkerTest.kt` with `work-testing`: enqueued unique work `log-sync` has the `CONNECTED` constraint and `KEEP` policy; the worker returns `retry` on `Failing(network)` and `success` otherwise; work survives re-enqueue without duplicates
- [X] T030 [P] [US2] Write failing Compose UI tests in `androidTest/log/SyncStatusTest.kt` and extend `androidTest/home/SignOutDialogTest.kt`: `sync_status` shows "N sets not synced", "Sync failing: <reason>", "Sign in again" (tap requests consent) and is hidden when all synced; `unsynced_warning` appears in the sign-out and switch-account confirmation with the count when N > 0 (FR-014)
- [X] T031 [US2] Add compiling stubs for T027–T030, push, and record the red run ID in this task — **red run 37527415315** (9 unit + 11 UI tests failing)

### Implementation for User Story 2

- [X] T032 [US2] Add error handling and status updates to `main/sync/LogSync.kt` (states via `SyncStatusStore`, token retry, crash-safe ordering) so T027 passes
- [X] T033 [US2] Implement the spreadsheet-change rule and `clearAll()` in `main/workout/data/WorkoutRepository.kt` / `main/sync/LogSync.kt` so T028 passes
- [X] T034 [US2] Implement `main/sync/SyncWorker.kt` (`CoroutineWorker` → `LogSync`, result mapping) and the WorkManager part of `main/sync/SyncScheduler.kt` (enqueue unique `log-sync` with `CONNECTED`, exponential backoff, `KEEP`, after every local change and on app start; same mutex as the in-process run), WorkManager initialization in `main/WorkoutRecApplication.kt`, so T029 passes
- [X] T035 [US2] Implement the sync status line in `main/log/TodayScreen.kt` (pending count from the display model, state from `SyncStatusStore`, "Sign in again" → existing consent flow in `main/setup/SetupViewModel.kt`), the unsynced warning in `main/home/SignOutDialog.kt` and the switch-account path (one sync attempt up to 10 s first, research R13), and US2 strings, so T030 passes; push and confirm green

**Checkpoint**: US1 + US2 — offline logging with background sync

---

## Phase 5: User Story 3 - See previous results while logging (Priority: P2)

**Goal**: Last time, the record, pre-filled sets with a "suggested" look

**Independent Test**: For an exercise with history, the app's last time and record match the log
tab, and set rows are pre-filled from the last workout (spec US3)

### Tests for User Story 3 (write first, push red)

- [X] T036 [P] [US3] Write failing tests in `test/workout/HistoryTest.kt` (research R11, FR-011, FR-012): last time = sets of the newest day before today with the exercise, time order; record = max weight and max reps among sets with that weight (e.g. 40 × 8 when 40 × 6, 40 × 8, 35 × 12); no history → none; planned count = last time's set count, default 3; set *i* pre-filled from last time's set *i* or its last set; today's sets of the same exercise do not count as "last time"; pending sets on the phone count for history
- [X] T037 [P] [US3] Write failing Compose UI tests in `androidTest/log/PrefillTest.kt`: `last_time` shows date and sets or "First time"; `record` shown as "40 kg × 8" or hidden; pre-filled values have semantics `suggested=true` (gray); after a stepper tap or typing, or after confirm, `suggested=false`
- [X] T038 [US3] Add compiling stubs for T036–T037, push, and record the red run ID in this task — **red run 37530326630** (11 unit + 2 UI tests failing)

### Implementation for User Story 3

- [X] T039 [US3] Implement `main/workout/History.kt` so T036 passes
- [X] T040 [US3] Show last time and the record and pre-fill rows in `main/log/ExerciseLoggingViewModel.kt` / `ExerciseLoggingScreen.kt` / `SetRow.kt` (suggested style + semantics property), default planned count in `main/log/PlanSetsDialog.kt`; add strings; push and confirm T037 green

**Checkpoint**: US1–US3 work independently

---

## Phase 6: User Story 4 - Review and correct workouts (Priority: P2)

**Goal**: Today and past days; edit/delete any set (unsynced, synced, or from the web UI) with the
"sheet wins" conflict rule

**Independent Test**: Edit and delete synced sets and a web-UI set; only those rows change in the
log tab; a set changed on the web first keeps the web value and shows a notice (spec US4)

### Tests for User Story 4 (write first, push red)

- [X] T041 [P] [US4] Write failing tests in `test/workout/CoalescerTest.kt` for every row of the data-model "Coalescing" table (synced row edit → `EDIT` with `lastSeen` = row key and `rowHint`; synced delete → `DELETE`; `NEW`+edit → `NEW` with new values; `NEW`+delete → removed; `EDIT`+edit → same `EDIT`, `lastSeen` kept; `EDIT`+delete → `DELETE` with the same `lastSeen`); the time is never changed by an edit (FR-006)
- [X] T042 [P] [US4] Write failing tests in `test/sync/LogSyncEditsTest.kt` against `FakeSheetsLogApi`: `EDIT` updates only columns B–D of the row found by `lastSeen`; `DELETE` removes exactly that row; several edits/deletes are sent in descending row order before `appendCells` in one call 22; duplicate keys → the row closest to `rowHint`; row changed or removed in the web UI (key not found) → change dropped and a `CONFLICT_DROPPED` notice added (FR-015, SC-008); rows inserted above the target in the web UI do not break targeting; deleting the first, last or only set of a day before today adds `WORKOUT_ROW_NOT_UPDATED`, edits never do
- [X] T043 [P] [US4] Write failing Compose UI tests in `androidTest/log/PastDaysTest.kt` and `androidTest/log/EditSetTest.kt`: `past_day_<date>` list newest first with counts; `day_detail` shows sets and sync state; `edit_set` changes exercise (picker), weight and reps with steppers, shows the date-time read-only; delete asks `delete_set_confirm`; `sync_notice_<id>` shows the conflict / workout-row notices and can be dismissed
- [X] T044 [US4] Add compiling stubs for T041–T043, push, and record the red run ID in this task — **red run 37550637870** (16 unit + 5 UI tests failing)

### Implementation for User Story 4

- [X] T045 [P] [US4] Implement `main/workout/Coalescer.kt` and the edit/delete transactions in `main/workout/data/WorkoutRepository.kt` so T041 passes
- [X] T046 [US4] Extend `main/sync/LogSync.kt` with `EDIT`/`DELETE` matching, descending-order requests, conflicts and notices so T042 passes
- [X] T047 [US4] Implement `main/log/PastDaysScreen.kt`, `main/log/DayDetailScreen.kt`, `main/log/EditSetDialog.kt`, notices on `main/log/TodayScreen.kt`, the `menu_workouts` entry and navigation in `main/MainActivity.kt`; edit/delete also from today's list and the logging screen's done rows; add strings; push and confirm T043 green

**Checkpoint**: US1–US4 work independently

---

## Phase 7: User Story 5 - Enter a past workout (Priority: P3)

**Goal**: Enter a forgotten workout with date, start and duration; set times spread over the
duration on Save

**Independent Test**: Yesterday 18:00, 60 min, 5 sets → 5 rows dated yesterday from 18:00:00 to
19:00:00 (spec US5, SC-009)

### Tests for User Story 5 (write first, push red)

- [X] T048 [P] [US5] Write failing tests in `test/workout/PastWorkoutTimesTest.kt` (research R10): n = 1 → start; n > 1 → `start + round(i × duration / (n − 1))` seconds, first = start, last = start + duration; strictly increasing (+1 s on rounding collisions, e.g. 200 sets in 1 minute); recalculated after adding/deleting sets; date in the future rejected; duration "1–600, default 60" enforced
- [X] T049 [P] [US5] Write failing tests in `test/log/PastWorkoutViewModelTest.kt`: draft stored with `draftId` sets that are not shown in today and not synced; warning when the day already has sets (FR-006a); Save computes times, clears `draftId` in one transaction and requests sync; Cancel deletes the draft and its sets
- [X] T050 [P] [US5] Write failing Compose UI tests in `androidTest/log/PastWorkoutTest.kt`: `menu_past_workout` opens the dialog (date not in the future, start time, duration); the editor reuses the picker and logging rows and shows computed times; `past_workout_save` returns to Today
- [X] T051 [US5] Add compiling stubs for T048–T050, push, and record the red run ID in this task — RUN_PLACEHOLDER

### Implementation for User Story 5

- [ ] T052 [P] [US5] Implement `main/workout/PastWorkoutTimes.kt` so T048 passes
- [ ] T053 [US5] Implement draft storage in `main/workout/data/WorkoutRepository.kt` and `main/log/PastWorkoutViewModel.kt` so T049 passes
- [ ] T054 [US5] Implement `main/log/PastWorkoutDialog.kt` and `main/log/PastWorkoutEditor.kt`, menu entry and navigation, strings; push and confirm T050 green

**Checkpoint**: All user stories functional

---

## Phase 8: Polish & Cross-Cutting Concerns

- [ ] T055 [P] Write a failing then passing performance test in `androidTest/workout/data/LargeLogTest.kt`: `replaceCaches` with 20,000 log rows completes in < 2 s on the CI emulator; confirming a set (`confirmSet`) with 200 pending sets completes in < 100 ms (plan Performance Goals, SC-005)
- [ ] T056 Refresh rules (research R7): refresh caches on app start and on resume at most every 5 minutes, and after a sync that wrote something, in `main/sync/SyncScheduler.kt`; test-first in `test/sync/RefreshPolicyTest.kt`
- [ ] T057 [P] Review all new RU and EN strings for consistency with feature 001 wording; lint `MissingTranslation` clean
- [ ] T058 [P] Update `README.md`: what the app now does (logging, offline sync, corrections, past workouts), and the troubleshooting line for "Sign in again" and sync notices
- [ ] T059 Merge to `master` via PR after a green CI run; install the release APK and run [quickstart.md](quickstart.md) manual scenarios 1–13 on a device with the user; record results in `specs/002-log-workout/quickstart-results.md` and fix any failure test-first

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: T001 first; T002 (spike) can run in parallel with Phase 2 but must finish
  before T021 (first `appendCells` use)
- **Foundational (Phase 2)**: depends on T001; blocks all stories
- **US1 (Phase 3)**: after Phase 2 and T002
- **US2 (Phase 4)**: after US1's `LogSync` (T021) and `SyncScheduler` (T022)
- **US3 (Phase 5)**: after Phase 2; touches US1's logging screen (T025)
- **US4 (Phase 6)**: after US1 (`LogSync`, screens); independent of US2/US3
- **US5 (Phase 7)**: after US1 (picker and logging rows)
- **Polish (Phase 8)**: after the stories that are in scope

### Within Each Story

- Tests and stubs first; push; see the CI run red (record the run ID in the stub task)
- Then pure domain code, then repository/sync, then UI; push; see green
- One branch for the feature (`002-log-workout`); PR to `master` at the end (or per story if
  the user wants intermediate builds)

### Parallel Opportunities

- T003–T006 (different test files) in parallel; T009, T010, T011 in parallel
- Within each story, the test tasks marked [P] in parallel
- US3, US4 and US5 can proceed in parallel after US1 if desired (they touch different files
  except the logging screen, which US3 and US5 both extend — do US3 first)

---

## Parallel Example: User Story 1

```text
Task: "T015 Write failing tests in test/workout/LiveSetTimeTest.kt"
Task: "T016 Write failing tests in test/sync/LogSyncNewSetsTest.kt"
Task: "T017 Write failing tests in test/log/ExerciseLoggingViewModelTest.kt"
Task: "T018 Write failing Compose UI tests for Today, picker and logging"
```

---

## Implementation Strategy

### MVP First (User Story 1, then 2)

1. Phase 1 (dependencies + spike) and Phase 2 (foundation)
2. US1 → validate online logging on a device → release build
3. US2 → validate offline + background sync (the constitution requires offline logging, so the
   MVP is US1 + US2)

### Incremental Delivery

US3 (faster logging) → US4 (corrections) → US5 (past workouts); each merged with a green CI run
gives a release APK the user can try.

---

## Notes

- [P] tasks = different files, no dependencies on incomplete tasks
- Never change the reference spreadsheet structure (no new columns, tabs or metadata per row)
- Never delete or rewrite rows other than the targets of the user's own edits and deletes
- Verify tests fail before implementing (red CI run), then green
