---

description: "Task list for Optional Spreadsheet Automation"
---

# Tasks: Optional Spreadsheet Automation

**Input**: Design documents from `/specs/007-optional-automation/`

**Prerequisites**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md),
[data-model.md](data-model.md), [contracts/sheets.md](contracts/sheets.md),
[contracts/apps-script.md](contracts/apps-script.md), [contracts/screens.md](contracts/screens.md),
[quickstart.md](quickstart.md)

**Tests**: Required. Constitution II (Test-First) applies to the app and the script, and V (each
new error path has a test) applies too. Write every test task first and watch it fail before its
implementation task.

**Organization**: Tasks follow the user stories in spec.md:

- US1: first run without the script steps (P1)
- US2: balance and day rows always up to date (P1)
- US3: existing spreadsheets convert once (P1)
- US4: turn on automation when wanted (P2)
- US5: spreadsheets with automation keep working (P2)

## Format: `[ID] [P?] [Story] Description`

- **[P]**: can run in parallel (different files, no dependency on unfinished tasks)
- **[Story]**: user story from spec.md

## Path Conventions

Single Android module plus the attached script. Abbreviations used below:

- `main/` = `app/src/main/java/com/workoutrec/`
- `test/` = `app/src/test/java/com/workoutrec/`
- `uitest/` = `app/src/androidTest/java/com/workoutrec/`
- `res/` = `app/src/main/res/`
- `script/` = `apps-script/`

Reports in tests are checked with `test/fakes/FakeMonitoring.kt`.

---

## Phase 1: Setup

**Purpose**: A known-good baseline, and the formulas proven in a real spreadsheet before anything
depends on them (plan risk 1).

- [X] T001 Run `./gradlew :app:testDebugUnitTest :app:lintDebug` and `node --test "apps-script/tests/*.test.mjs"` on the branch before any change, and record the result under T001 in this file (expected: green). **Result 2026-10-24**: green (unit tests, lint; script tests 30/30)
- [X] T002 Paste `DURATION_FORMULA` and `BALANCE_FORMULA` from research R1/R2 into a copy of a test spreadsheet (ru_RU locale; the `workout` and `money` tables in place) through the Sheets API (`updateCells` formulaValue, e.g. with the API Explorer). Check four things: the duration of a 3-set day is right; a row whose A is a date-time from an old script works; the balance equals the script's value; an `appendCells` after a formula row lands on the next row. Record the outcome under T002. If it fails, switch to the R1 fallback before T007 **2026-10-24**: deferred to the device check (quickstart #2); no Google access from the build machine. Formulas implemented as in research R1/R2. **2026-10-25**: build 184 failed in ru_RU (`#ERROR!` parse error: the API parses `formulaValue` in the spreadsheet's locale); fixed in PR #39 (research R1 "Locale"). Build 187 on the device: every `workout` B is the `;` formula with numbers (0 for old midnight-only days, 41–166 for October), `balance!A1` is the formula and shows 2.

---

## Phase 2: Foundational (blocking all stories)

**Purpose**: Everything the stories share:

- the two formulas and structure version 3
- the shared day-rows rule with its fixture
- the client support for deletes, formula appends and calls 24/25
- the new monitoring keys

### Tests (write first, see them fail)

- [X] T003 [P] Create the shared fixture `script/tests/fixtures/day-rows.json` (contracts/apps-script.md "Shared cases"). It must have at least these cases: the first day ever is Free; the default comes from the latest earlier row; a planned append feeds the next append; a row with no log day is removed; an empty log removes nothing; a duplicate row for a date with sets adds nothing and keeps both; a date-time in A from an old script counts as that day
- [X] T004 [P] Write failing tests in `test/workout/DayRowsPlanTest.kt` (data-model "Workout day row: rules", contracts/sheets.md "What gets written"):
  - waiting statuses follow the 006 table: update, append, done, dropped when `lastSeen` differs, and still waiting when the date has no sets
  - dates with sets and no row are appended with the default; a waiting status overrides the default
  - rows of dates without sets are deleted, as 0-based grid indexes in descending order
  - nothing is deleted when the log is empty
  - a date already having a row is never appended
- [X] T005 [P] Write a failing `test/workout/DayRowsFixtureTest.kt` that loads `../apps-script/tests/fixtures/day-rows.json` (path relative to the `app` module) and checks that `DayRowsPlan` gives each case's expected appends and removals
- [X] T006 [P] Write failing client tests in `test/google/SheetsLogClientTest.kt`:
  - call 22 with `WorkoutWrite.deletes`: `updateCells` first, then one `deleteDimension` per row in descending order (`startIndex = row`, `endIndex = row + 1`, `dimension = ROWS`), then `appendCells`, whose B cell is `{"userEnteredValue": {"formulaValue": DURATION_FORMULA}}`
  - call 24 (`readCalculatedCells`): request ranges and `fields` exactly as in contracts/sheets.md; parses `startRow`, A numbers, the B `formulaValue` or other `userEnteredValue`, the balance A1 cell, and the metadata keys with their IDs
  - call 25 (`repairCalculatedCells`): one `repeatCell` per contiguous row run, then the optional balance `updateCells`, then the optional `structure_version = "3"` metadata request; no request at all when there is nothing to write

### Implementation

- [X] T007 Add `DURATION_FORMULA` and `BALANCE_FORMULA` (research R1/R2, single-line texts) and `STRUCTURE_VERSION = "3"` to `main/spreadsheet/ReferenceStructure.kt`. Do not change the create request yet (US1)
- [X] T008 [P] Create `main/workout/DayRowsPlan.kt`: an `object DayRowsPlan` with `plan(rows: List<SheetWorkoutRow>, daysWithSets: Set<LocalDate>, logEmpty: Boolean, waiting: List<PendingStatus>): DayRowsResult(updates, deletes, appends, synced, written, dropped, notices)`. Use `DayStatusRules.defaultFor` for the default, computed over existing first rows plus planned appends. This makes T004 and T005 pass
- [X] T009 Extend `main/google/SheetsLogClient.kt`:
  - `WorkoutWrite` gains `deletes: List<Int>` (included in `isEmpty`)
  - `appendWorkouts` writes the formula in B
  - request order as in T006
  - add `readCalculatedCells(spreadsheetId): CalculatedCells` (call 24) and `repairCalculatedCells(spreadsheetId, repair: CalculatedRepair)` (call 25) to `SheetsLogApi`, with the data classes in the same file
  - update the fakes in `test/fakes/`
  - This makes T006 pass
- [X] T010 [P] Add `CALCULATION_RESTORED` and `CALCULATION_CHECK_FAILED` to `NonFatalIssue` in `main/monitoring/Monitoring.kt`, with the stable keys `calculation-restored` and `calculation-check-failed` (WARNING). Extend the catch-site guard test list if it enumerates keys

**Checkpoint**: The rule, the formulas and the client are ready, and both rule implementations agree on the fixture.

---

## Phase 3: User Story 1 - Start logging without the script steps (Priority: P1) 🎯 MVP

**Goal**: Choosing an account leads straight to Today. There are no guide screens and no reminder, and new spreadsheets are created already calculated (FR-001, FR-002, FR-010).

**Independent Test**: Quickstart #1–#2. On a new account, go from the chooser to Today without any guide screen or banner. The new spreadsheet has the formulas and `structure_version` 3.

### Tests (write first, see them fail)

- [X] T011 [P] [US1] Write failing tests in `test/spreadsheet/ReferenceStructureTest.kt`: `createRequest` and `completionRequests` put `BALANCE_FORMULA` as `formulaValue` in `balance` A1, not `numberValue` 0; the create metadata has `structure_version = "3"`
- [X] T012 [P] [US1] Write failing tests in `test/setup/SpreadsheetSetupFlowTest.kt`: — *written as rewrites of `test/setup/SetupViewModelSpreadsheetTest.kt` and `SetupStepReportsTest.kt`, which drive `SpreadsheetSetupFlow` through the view model*
  - create, rewrite, retry-creating and match return `Ready` with no `installer.install` call, whether or not a script exists
  - `refresh` returns `RefreshResult.Status(automation)` with the evaluated status and builds no reminder
  - `StartAutomation` with no script → attach → `NeedsEnable`, or `NeedsApiSetting` on `AppsScriptApiDisabled`
  - `StartAutomation` with automation On → `Ready`
  - `ContinueForNow` → `Ready`
- [X] T013 [P] [US1] Write a failing UI test in `uitest/log/TodayNoReminderTest.kt`: with automation status NotEnabled and ScriptMissing, Today shows no `AutomationReminder` tag or text — *done by deleting `AutomationReminder` and the Today `header` slot it used; the reminder UI tests in `uitest/setup/SetupScreensTest.kt` were removed with it, so nothing can render a reminder*

### Implementation

- [X] T014 [US1] In `main/spreadsheet/ReferenceStructure.kt`, write `formulaCell(BALANCE_FORMULA)` for `balance` in `fullRows` and `completionRequests`. This makes T011 pass
- [X] T015 [US1] In `main/setup/SpreadsheetSetupFlow.kt`:
  - `afterMatch` → `Ready`
  - rename `addTablesThenAttach` to `addTables`, returning `Ready`
  - `retryCreating` → `Ready`
  - `refresh` returns `Status(automation)`
  - rename `FixAutomation` to `StartAutomation`
  - keep `attach`/`attachToBound`/`recheckEnabled` for the action
  - `SetupStep.AttachingScript` stays for errors that occur while starting automation
- [X] T016 [US1] In `main/setup/SetupState.kt`, remove `Reminder`, change `RefreshResult.Status` to `(automation: AutomationStatus)`, and rename the action. In `main/setup/SetupViewModel.kt`, replace `reminder` with `automation: StateFlow<AutomationStatus?>`. This makes T012 pass
- [X] T017 [US1] Delete `main/home/AutomationReminder.kt` and its banner block in `main/MainActivity.kt` (around the `viewModel.reminder` collector). Remove the reminder strings from `res/values/strings.xml` and `res/values-ru/strings.xml` that nothing references any more. This makes T013 pass

**Checkpoint**: A newcomer reaches Today in 3 steps (SC-001).

---

## Phase 4: User Story 2 - Balance and workout days always up to date (Priority: P1)

**Goal**: Every sync keeps one `workout` row per date with sets, and only for those dates (FR-006, FR-007, FR-007a). The Money tab shows the spreadsheet's balance with an "updates after sync" note (FR-005a).

**Independent Test**: Quickstart #3, #7–#10.

### Tests (write first, see them fail)

- [X] T018 [P] [US2] Write failing tests in `test/sync/LogSyncDayRowsTest.kt`, using the fake API:
  - a new day's sets and its waiting status go out in one call 22 with the row appended (formula in B)
  - a date typed only in the sheet gets a row with the default
  - a hand-added row for a date without sets is deleted
  - deleting a day's last set in the app deletes its row in the same call 22
  - an empty log read deletes nothing
  - no `WORKOUT_ROW_NOT_UPDATED` notice is produced any more
  - a missing `workout` tab still reports `WORKOUT_TAB_MISSING` only when statuses are waiting
  - a run with no other changes but a missing row still writes
- [X] T019 [P] [US2] Write failing tests in `test/money/BalanceNotesTest.kt` (contracts/screens.md "Balance card"):
  - `calculated` → `Calculated`
  - not calculated + On → `Daily(lastRun)`; + Stopped → `Stopped`; + otherwise → `Calculated`
  - `waiting` is true when any set, day status or payment is waiting
  - there is no `Off` state
- [X] T020 [P] [US2] Write a failing migration test in `uitest/workout/data/MigrationTest.kt`: schema 3 with a `money_summary` row migrates to 4 and keeps its values, with `calculated = 0`
- [X] T021 [P] [US2] Write a failing test in `test/money/MoneyRefresherTest.kt`: metadata `structure_version` "3" (and "4") → `calculated = true`; "2" or missing → false. Analysis C1: a formula error in `balance!A1` (e.g. `#REF!` because a tab was renamed) gives `balance = null` and is reported as `BAD_SHEET_DATA` with no value

### Implementation

- [X] T022 [US2] In `main/sync/LogSync.kt`:
  - replace `planStatuses` with a call to `DayRowsPlan.plan`. `daysWithSets` comes from the read rows minus this run's deletes plus its appends, using `SheetTime.workoutDay`. `logEmpty` = the log read *before* this run's writes has no rows (analysis U1: deleting every set in the app still removes the rows)
  - pass `deletes` in `WorkoutWrite`
  - count a plan with appends or deletes as `wrote`
  - drop `changesWorkoutRow` and the `WORKOUT_ROW_NOT_UPDATED` notice. Keep the enum value and string, per research R10
  - This makes T018 pass
- [X] T023 [US2] Add `calculated: Boolean` to `MoneySummary` (`main/money/MoneyRefresher.kt`). Set it from `read.metadata[ReferenceStructure.STRUCTURE_VERSION_KEY]`: the integer is at least 3. Report a text (error) balance cell as in T021. This makes T021 pass
- [X] T024 [US2] Add `val calculated: Boolean = false` (`@ColumnInfo(defaultValue = "0")`) to `MoneySummaryEntity` in `main/workout/data/Entities.kt`. In `main/workout/data/WorkoutDatabase.kt`, set `version = 4` and add `AutoMigration(from = 3, to = 4)`. Build to export `app/schemas/com.workoutrec.workout.data.WorkoutDatabase/4.json`, and map the field in `main/workout/data/WorkoutRepository.kt`. This makes T020 pass
- [X] T025 [US2] In `main/money/BalanceNote.kt`: — *`waiting` uses the repository's `waitingCount` (sets, statuses, payments and exercise changes)*
  - change `BalanceNote` to `Calculated`, `Daily(lastRun)` and `Stopped(lastRun)`
  - change `BalanceNoteState(note, waiting)`
  - `BalanceNotes.of(calculated, automation, lastDailyRun, waiting)`
  - in `main/money/MoneyRoute.kt`, set `waiting` from the repository's pending sets, statuses and payments flows
  - This makes T019 pass
- [X] T026 [US2] In `main/money/MoneyScreens.kt` `BalanceCard`:
  - note texts as in contracts/screens.md, plus the "Updates after your changes sync." line when `waiting`
  - remove the "Enable automation" `TextButton` and the `onEnableAutomation` parameter from the card
  - strings `money_note_calculated` and `money_note_waiting` in `res/values{,-ru}/strings.xml`
  - remove `money_note_off`, `money_note_new_payments` and `money_enable_automation` if they are unused
  - update the existing Money UI tests in `uitest/money/`

**Checkpoint**: With automation off, rows and the balance follow every change within one sync (SC-002).

---

## Phase 5: User Story 3 - Existing spreadsheets switch over once (Priority: P1)

**Goal**: The form step converts old spreadsheets once, repairs overwritten cells on every sync, and records version 3 (FR-005, FR-008, FR-009, FR-011, FR-016).

**Independent Test**: Quickstart #4–#6.

### Tests (write first, see them fail)

- [X] T027 [P] [US3] Write failing tests in `test/spreadsheet/CalculatedFormTest.kt`, covering the data-model "Calculated cells: states" table row by row:
  - a matching formula → nothing
  - an empty cell → write, no report
  - another value before version 3 → write, no report
  - another value at version 3 → write and report the cell kinds with counts
  - a formula compared after normalising `;` → `,` and whitespace
  - rows without a number in A are ignored
  - contiguous runs merged
  - gate closed (On and script version < 3) → durations only, no balance, no marker
  - gate open below version 3 → balance and marker
  - at version 3 with everything right → no repair
- [X] T028 [P] [US3] Write failing tests in `test/sync/FormCheckTest.kt`:
  - runs call 24 after a successful sync and call 25 only when `CalculatedForm` asks for it
  - skipped when `workoutSheetId` or `balanceSheetId` is unknown
  - a call-24 or call-25 failure reports `CALCULATION_CHECK_FAILED` through `failure` and leaves the outcome `Synced`
  - `CALCULATION_RESTORED` is reported with a details string that has no dates or values
  - `CancellationException` is rethrown
- [X] T029 [P] [US3] Add a case to `test/sync/LogSyncDayRowsTest.kt`: an old spreadsheet with a v2-style row (A date-time, B number) for a day with sets keeps the row, and a row for a day without sets is deleted (US3 #2)

### Implementation

- [X] T030 [US3] Create `main/spreadsheet/CalculatedForm.kt`: an `object CalculatedForm` with `decide(cells: CalculatedCells, automation: AutomationStatus, scriptVersion: Int): FormDecision(repair: CalculatedRepair?, restored: Map<String, Int>)`. It reuses `StructureValidator`'s normalisation, which you should move to an `internal fun normalizeFormula` in `main/spreadsheet/StructureValidator.kt`. This makes T027 pass
- [X] T031 [US3] Create `main/sync/FormCheck.kt`: `class FormCheck(api: SheetsLogApi, monitoring: Monitoring, now: () -> Instant, log: (String) -> Unit)` with `suspend fun run(spreadsheetId, sheet: SheetInfoCache)`. It reads with call 24, evaluates automation from the metadata (`AutomationStatusEvaluator.evaluate`) and `script_version` (default 1), calls `CalculatedForm.decide`, writes with call 25, and reports as in T028. Wire it in `main/AppContainer.kt`
- [X] T032 [US3] In `main/sync/LogSync.kt`, call `formCheck.run(id, sheet)` after step 6 (commit), inside its own `profile.section("form")`. Its failures never change the returned `SyncOutcome`. This makes T028 and T029 pass

**Checkpoint**: A copy of a real spreadsheet converts. Durations are kept, rows without sets go, and the balance changes only by those rows (SC-003).

---

## Phase 6: User Story 4 - Turn on automation when wanted (Priority: P2)

**Goal**: An automation item in the account menu leads to the same two steps (FR-012, FR-013). *(Clarification 2026-10-26: the Money tab entry was removed, see T051.)* The nightly run follows FR-014.

**Independent Test**: Quickstart #11–#12.

### Tests (write first, see them fail)

- [X] T033 [P] [US4] Write failing Node tests in `script/tests/logic.test.mjs`:
  - `workoutRowsPlan` v3 runs every case in `tests/fixtures/day-rows.json`
  - plus `formulaRows`: rows with an empty B, numbered after removals, excluding removed ones
  - removals in descending order
  - empty-log guard
- [X] T034 [P] [US4] Write failing Node tests in `script/tests/code.test.mjs`, using `fakes.mjs`:
  - `dailyJob` deletes removal rows in descending order, calls `setFormula(DURATION_FORMULA)` on empty B cells, and appends `[date, DURATION_FORMULA, flag]`
  - never calls `setValue` on column B
  - writes `balance!A1` only when `getFormula()` returns `''`
  - always writes `last_daily_run`
  - `ENABLE_TEXTS` no longer mention the balance
  - Extend `fakes.mjs` with `deleteRow`, `setFormula` and `getFormula`
- [X] T035 [P] [US4] Write failing tests in `test/automation/ScriptInstallerTest.kt`: `SCRIPT_VERSION == 3`; the generated `Config.gs` contains `const DURATION_FORMULA = ` followed by the JSON-escaped `ReferenceStructure.DURATION_FORMULA`
- [X] T036 [P] [US4] Write failing UI tests in `uitest/money/AutomationEntryTest.kt` and `uitest/home/AccountMenuTest.kt`: — *account menu cases added to `uitest/home/AccountAvatarTest.kt`*
  - the Money tab shows `automation_entry`, with the Off text for NotEnabled and ScriptMissing, "On. Runs every night." for On, and the stopped text for Stopped, in every money state including errors
  - the account menu item shows the same status
  - tapping either sends `StartAutomation`

### Implementation

- [X] T037 [US4] In `script/Logic.gs`, make `workoutRowsPlan` return `{ appends, removals, formulaRows }` (contracts/apps-script.md); keep `balance`. This makes T033 pass
- [X] T038 [US4] In `script/Code.gs`:
  - make `dailyJob` follow contracts/apps-script.md steps 1–6
  - update `ENABLE_TEXTS` in en and ru to say the job adds new days every night, without "and balance"
  - This makes T034 pass
- [X] T039 [US4] In `main/automation/ScriptInstaller.kt`, set `SCRIPT_VERSION = 3` and add `const DURATION_FORMULA = <JSON string>;` to the generated `Config.gs`. This makes T035 pass
- [X] T040 [US4] Create `main/automation/AutomationEntry.kt`: a composable `AutomationEntry(status: AutomationStatus?, onClick)` (a Material 3 `ListItem` with tag `automation_entry`). Add the strings `automation_entry_title`, `automation_entry_off`, `automation_entry_on` and `automation_entry_stopped` to `res/values{,-ru}/strings.xml`, using the texts from contracts/screens.md
- [X] T041 [US4] Wire the entry:
  - below the balance card in `main/money/MoneyScreens.kt` (all states), fed from `SetupViewModel.automation` through `main/money/MoneyRoute.kt`
  - as an item above "Share sync log" in `AccountMenu` in `main/home/MainScreen.kt`
  - in `main/MainActivity.kt`, tapping either sends `SetupAction.StartAutomation`, and when the state returns to `Ready` the app goes back to the tab the user came from instead of Today
  - This makes T036 pass

**Checkpoint**: Automation can be turned on from either place and stays optional.

---

## Phase 7: User Story 5 - Spreadsheets with automation keep working (Priority: P2)

**Goal**: Existing automation stays on, gets upgraded to v3, and never fights the formulas (FR-015, R6).

**Independent Test**: Quickstart #12–#13.

### Tests (write first, see them fail)

- [X] T042 [P] [US5] Add cases to `test/setup/SpreadsheetSetupFlowTest.kt`: `refresh` with a script at version 2 upgrades to 3 (no `install`, no state change); an upgrade failure still reports `SCRIPT_UPGRADE_FAILED` and still returns `Status(automation)`; automation On stays On
- [X] T043 [P] [US5] Add a case to `test/sync/FormCheckTest.kt`: automation On with `script_version` 2 → no balance write, no marker, no `CALCULATION_RESTORED` for a number in `balance!A1`; after the metadata says 3 → balance formula and marker

### Implementation

- [X] T044 [US5] Confirm that `upgradeScript` in `main/setup/SpreadsheetSetupFlow.kt` runs for every attached script whatever the automation status, and that `refresh` runs before the first sync on app start (`main/setup/SetupViewModel.kt`, `main/sync/SyncScheduler.kt`). Fix whatever T042 or T043 shows — *confirmed: `upgradeScript` needs only a script ID; a sync that runs before the start-up upgrade simply keeps the gate closed until the next sync (no wrong write)*

**Checkpoint**: Spreadsheets with automation on show the same balance as before, then switch to the formula once their script is v3.

---

## Phase 8: Polish & Cross-Cutting Concerns

- [X] T045 [P] Update `README.md`: the first run (account → Today), automation as an optional extra, and what the spreadsheet calculates
- [X] T046 [P] Update the screen gallery samples in `app/src/debug/java/com/workoutrec/gallery/ScreenGallery.kt`: Money with the `Calculated` note, the waiting line and the automation entry; the account menu item; no Today banner. Re-record with `./gradlew recordRoborazziDebug --tests '*ScreenGalleryTest*'`
- [X] T047 Run the full checks: `./gradlew :app:testDebugUnitTest :app:lintDebug`, `node --test "apps-script/tests/*.test.mjs"`, and `./gradlew connectedDebugAndroidTest` on the local emulator. Fix what fails **Result 2026-10-24**: unit tests + lint green, script tests 45/45, emulator UI tests 156/156
- [X] T048 Open the PR (title `feat: optional automation, spreadsheet-calculated balance and durations (007)`). Merge it when CI is green, per standing permission, and check that the release reaches internal testing **Result 2026-10-24**: PR #38 merged (a9a9a55); CI green; version code 184 in internal testing.
- [ ] T049 Device quickstart #1–#13 on test spreadsheets (the user's device check). Record the results in this file
- [ ] T051 Remove the automation entry from the Money tab; turn automation on only from the account menu (FR-012, clarification 2026-10-26): `MoneyScreen`/`MoneyRoute` lose `onEnableAutomation` and `automation`, `AutomationEntry` and `uitest/money/AutomationEntryTest.kt` go, `automation_entry_off/on/stopped` strings go, gallery screen `65-money-automation-on` goes. Update the user guide doc's automation section and screenshot
- [ ] T052 Remove the automation notes from the Money tab (clarification 2026-10-26): `BalanceNote.Daily`/`Stopped` and `BalanceNotes` go, `BalanceNoteState` keeps only `waiting`, strings `money_note_daily`, `money_note_daily_unknown`, `money_note_stopped` go, `BalanceNoteTest` and gallery screen `62-money-daily-script` go
- [ ] T053 Script v5 (overnight device check 2026-10-27: `setFormula` parses in the locale, so v4 left `#ERROR!` in `workout` B and `balance!A1` in `ru_RU`): `durationFormulaWriter_` in `apps-script/Code.gs` falls back to `;`, `workoutRowsPlan` also rewrites `#ERROR!` durations, `SCRIPT_VERSION = 5`; Node tests for both
- [ ] T054 Script v6 (overnight check 2026-10-28: the nightly trigger ran "Version 1", so v4/v5 never reached it): the Enable page is the Head deployment (`ScriptApi.headDeploymentId`, URL `/dev`), `doGet` replaces the daily trigger and writes `workout_rec.daily_trigger = head`, the upgrade rewrites `enable_url`, `AutomationStatusEvaluator` treats automation without the marker as NotEnabled; `SCRIPT_VERSION = 6`. Verify overnight on the dev spreadsheet and on the user's phone (step 2 again after the update)
- [ ] T050 After the release, update the user guide doc's "Getting started" and "Paid or Free, and your balance" sections (Claude Docs, https://claude.ai/artifact/MUVVKpyT7g2rcVmAAQtPD2) for the shorter first run and the optional automation

---

## Dependencies & Execution Order

### Phase Dependencies

- Setup (T001–T002) comes first. T002 decides the formula texts.
- Foundational (T003–T010) blocks every story.
- US1 (Phase 3) and US2 (Phase 4) are independent of each other once Foundational is done.
- US3 (Phase 5) needs US2's T022, because removals happen in the day-rows step, and the FormCheck wiring goes into the same `LogSync.kt`.
- US4 (Phase 6) needs US1's `StartAutomation` and `SetupViewModel.automation` (T015–T016).
- US5 (Phase 7) needs US3 (FormCheck) and US4 (script v3).
- Polish comes last.

### User Story Dependencies

```text
Foundational ──▶ US1 ──▶ US4 ──▶ US5
            └──▶ US2 ──▶ US3 ──┘
```

### Within Each Story

Tests come first and must be seen failing, then the implementation, in task order. Tasks that
touch the same file (`LogSync.kt`: T022 → T032; `SpreadsheetSetupFlow.kt`: T015 → T044;
`MoneyScreens.kt`: T026 → T041) run sequentially.

### Parallel Opportunities

- Foundational tests T003–T006 together; then T008 and T010 alongside T007/T009.
- US1 tests T011–T013 together.
- US2 tests T018–T021 together; T023/T024 alongside T022.
- US3 tests T027–T029 together.
- US4 tests T033–T036 together; the script tasks T037–T038 alongside the app tasks T039–T041.
- US1 and US2 can be built in parallel after Foundational.

## Parallel Example: User Story 2

```text
Task: "T018 LogSyncDayRowsTest (day rows in call 22)"
Task: "T019 BalanceNotesTest (Calculated/Daily/Stopped, waiting)"
Task: "T020 MigrationTest 3→4 (money_summary.calculated)"
Task: "T021 MoneyRefresherTest (structure_version → calculated)"
```

## Implementation Strategy

### MVP

US1 alone is not safe to release, because without US2 the balance would stop updating for
newcomers. The first releasable increment is therefore **Foundational + US1 + US2 + US3**, all
of them P1. That gives newcomers a short first run, keeps the rows and balance live, and converts
existing spreadsheets.

### Incremental Delivery

1. Setup and Foundational: the formulas are proven and the rule is shared.
2. US2, then US3: the calculations are live and old spreadsheets convert. This is safe to ship
   even with the old setup.
3. US1: the short first run. This completes the P1 MVP and is one PR and one release.
4. US4 and US5: the optional automation entry and script v3. These can go in the same PR or a
   follow-up one.
5. Polish: README, gallery, device check, user guide.

## Notes

- Each test task names the behaviour to prove. Don't widen a test beyond its task.
- Commit after each phase. Messages end with the Co-Authored-By trailer.
- If T002 shows that a formula fails, update research R1/R2 and both formula constants before
  T007, and record the change in this file.
