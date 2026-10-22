---

description: "Task list for Bottom Tab Navigation"
---

# Tasks: Bottom Tab Navigation

**Input**: Design documents from `/specs/004-bottom-tab-navigation/`

**Prerequisites**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md),
[data-model.md](data-model.md), [contracts/screens.md](contracts/screens.md),
[contracts/sheets.md](contracts/sheets.md), [quickstart.md](quickstart.md)

**Tests**: Required. Constitution II (Test-First, non-negotiable): every test task is written
first and must be seen failing (red CI or local run) before its implementation task.

**Organization**: by user story (spec.md): US1 bottom tabs (P1), US2 History (P1), US3 Exercises
(P2), US4 Money (P2).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: can run in parallel (different files, no dependency on unfinished tasks)
- **[Story]**: user story from spec.md

## Path Conventions

Single Android module. Abbreviations used below:
`main/` = `app/src/main/java/com/workoutrec/`, `test/` = `app/src/test/java/com/workoutrec/`,
`androidTest/` = `app/src/androidTest/java/com/workoutrec/`,
`debug/` = `app/src/debug/java/com/workoutrec/`, `res/` = `app/src/main/res/`.

---

## Phase 1: Setup

**Purpose**: Make the database upgrade safe and add shared resources.

- [X] T001 Verify `app/schemas/com.workoutrec.workout.data.WorkoutDatabase/1.json` matches the shipped schema: check out the `0c1ed55` sources in a temporary worktree, run `./gradlew kspDebugKotlin`, and diff the produced `1.json` with the current file (identity hash must be equal); then commit `app/schemas/` (research R9)
- [X] T002 [P] Add tab icons as vector drawables (Material Symbols paths, 24 dp, tinted by the bar) `res/drawable/ic_tab_today.xml`, `ic_tab_history.xml`, `ic_tab_exercises.xml` (fitness_center), `ic_tab_money.xml` (payments) (research R1)
- [X] T003 [P] Add tab label strings `tab_today` ("Today" / "Сегодня"), `tab_history` ("History" / "История"), `tab_exercises` ("Exercises" / "Упражнения"), `tab_money` ("Money" / "Деньги") in `res/values/strings.xml` and `res/values-ru/strings.xml` (FR-001, FR-011)

---

## Phase 2: Foundational (blocking)

**Purpose**: Database v2, sheet IDs and the waiting count used by US3 and US4.

**⚠️ CRITICAL**: US3 and US4 need all of Phase 2. US1 and US2 only need T001–T003 and can start in
parallel with this phase.

- [X] T004 Write failing migration test in `androidTest/workout/data/MigrationTest.kt` with `MigrationTestHelper`: create a v1 database with 2 `log_row`, 2 `pending_change` (one with `draftId`), 1 `sync_notice`, migrate to v2, assert all rows are unchanged and the tables `pending_exercise_change`, `pending_payment`, `payment_row`, `money_summary` exist and are empty (research R9)
- [X] T005 Add Room entities in `main/workout/data/Entities.kt` per data-model.md: `PendingExerciseChangeEntity` (`id` text PK, `kind` "ADD" | "RENAME" | "DELETE", `name`, `muscleGroup`, `lastSeenName` nullable, `lastSeenGroup` nullable, `createdAt`), `PendingPaymentEntity` (`id` text PK, `date` ISO `yyyy-MM-dd`, `workouts` int, `sumHundredths` long, `createdAt`), `PaymentRowEntity` (`rowIndex` int PK, `date` ISO, `workouts` double nullable, `sumHundredths` long nullable), `MoneySummaryEntity` (`id` int PK = 1, `balance` double nullable, `lastDailyRun` long nullable, `automation` "On" | "Stopped" | "NotEnabled" | "ScriptMissing", `downloadedAt` long, `error` "NONE" | "MISSING_TAB" | "NETWORK" | "OTHER")
- [X] T006 Bump `main/workout/data/WorkoutDatabase.kt` to `version = 2` with the 4 new entities and `autoMigrations = [AutoMigration(from = 1, to = 2)]`; build to export `app/schemas/…/2.json` and commit it; T004 passes on the CI emulator
- [X] T007 Write failing DAO tests in `androidTest/workout/data/WorkoutDaoV2Test.kt`: insert/observe/delete for pending exercise changes and pending payments; `replacePayments(rows, summary)` replaces `payment_row` and `money_summary` in one transaction; `clearAll()` also empties the 4 new tables (002-R13)
- [X] T008 Implement the DAO methods in `main/workout/data/WorkoutDao.kt` and extend `clearAll` so T007 passes
- [X] T009 [P] Write failing tests in `test/google/SheetsLogClientTest.kt` (MockWebServer) for call 20: returns sheet IDs of `log`, `drills` (required, `MissingTabException` otherwise) and `rec`, `money`, `balance` as nullable; and call 21: drills rows keep `rowIndex` (row 2 → 1), empty names skipped, and `money!A2:C` is requested only when asked for (contracts/sheets.md)
- [X] T010 Implement call 20/21 changes in `main/google/SheetsLogClient.kt` (`LogSheetInfo` with `drillsSheetId`, `recSheetId?`, `moneySheetId?`, `balanceSheetId?`; `SheetDrill.rowIndex`; `readLogAndDrills(id, withMoney: Boolean)` returning money rows) and store the IDs in `SheetInfoCache` in `main/sync/SyncStatusStore.kt` (existing cached entries without the new IDs are refreshed by re-running call 20) so T009 passes
- [X] T011 [P] Write failing tests in `test/workout/WaitingCountTest.kt`: waiting count = pending set changes without `draftId` + pending exercise changes + pending payments (research R10)
- [X] T012 [P] Update `androidTest/log/SyncStatusButtonTest.kt` and `androidTest/home/SignOutDialogTest.kt` to expect the "changes" plurals and a count that includes exercise changes and payments; see them fail (FR-012)
- [X] T013 Implement the waiting count in `main/workout/DisplayModel.kt` and `main/workout/data/WorkoutRepository.kt` (`waitingCount: Flow<Int>`), use it in `main/log/LogRoutes.kt` `SyncStatusAction` and in the sign-out / switch-account warning in `main/home/SignOutDialog.kt`; change plurals `sync_pending` and `unsynced_warning` to "changes" (EN: "%d change(s) not in the spreadsheet yet"; RU: "%d изменение / изменения / изменений ещё не в таблице") in both `strings.xml` so T011 and T012 pass (FR-012)

**Checkpoint**: v2 database migrates; sync knows all sheet IDs; the icon counts every kind of change.

---

## Phase 3: User Story 1 — Bottom tabs (Priority: P1) 🎯 MVP

**Goal**: A four-tab bar; each tab keeps its place; Back and reselect behave as specified; the bar
hides on logging.

**Independent Test**: Open the app → Today selected; tap History → past days; open a day; tap
Money, then History → same day; tap History → past days; Back → Today; open logging → no bar.

### Tests for User Story 1 ⚠️ write first, see them fail

- [X] T014 [P] [US1] Write failing Compose UI tests in `androidTest/nav/BottomBarTest.kt` using a `TestNavHostController` and fake screens per tab: start on `bar_tab_today` (selected semantics); tap each `bar_tab_*` shows its start screen; state kept (navigate inside History, switch to Money and back → inner screen shown; scroll past days so item 10 is the first visible, switch to Money and back → item 10 is still the first visible, SC-003); reselect pops to start; Back from a non-Today tab start → Today, Back on Today → `activity.isFinishing`; `bottom_bar` absent on the logging and past-workout-editor routes (FR-001–FR-005, contracts/screens.md "Bottom bar")
- [X] T015 [P] [US1] Write failing JVM test in `test/nav/TabsTest.kt`: `Tab.entries` order Today, History, Exercises, Money; `barVisible(route)` false for `LogRoutes.LOGGING`, the past workout editor route and `Routes.SETUP`, true otherwise; `tabOf(route)` maps every existing route to its tab (research R2)
- [X] T016 [P] [US1] Write a failing test in `androidTest/nav/MainActivityTabsTest.kt`: launch `MainActivity` with a set-up fake account, assert `bottom_bar` is shown and `bar_tab_today` is selected, tap `bar_tab_history` and see `past_days` (FR-001, FR-002) — *Done as part of `BottomBarTest`: `MainActivity` cannot start in CI without a real Google sign-in, so the test drives the same `mainTabs` graph builder and route constants `MainActivity` uses.*

### Implementation for User Story 1

- [X] T017 [US1] Implement `main/nav/Tabs.kt`: `enum class Tab(route, icon, label)` with graph routes `tab/today`, `tab/history`, `tab/exercises`, `tab/money`; `barVisible(route)`; `tabOf(route)` so T015 passes
- [X] T018 [US1] Implement `main/nav/BottomBar.kt`: Material 3 `NavigationBar` (tag `bottom_bar`), one `NavigationBarItem` per tab (tags `bar_tab_<tab>`, icon + single-line label with ellipsis); select = `navigate(tab.route) { popUpTo(graph.findStartDestination().id) { saveState = true }; launchSingleTop = true; restoreState = true }`; reselect = `popBackStack(tab start, inclusive = false)` (research R1)
- [X] T019 [US1] Implement `main/nav/TabGraphs.kt` with four nested `navigation(...)` graphs: today (Today start = existing `Routes.HOME` content, picker, plan sets, logging), history (past days start, day detail, past workout dialog and editor), exercises and money (placeholder start screens with the tab title and the text `tab_placeholder` "Coming soon" / "Скоро", replaced by US3/US4); keep existing route strings for the moved destinations (plan risk "routes")
- [X] T020 [US1] Restructure `main/MainActivity.kt`: setup stays outside the tabs; after setup a `Scaffold(bottomBar = { if (barVisible(current)) BottomBar(...) })` hosts the tab graphs; `CompositionLocalProvider(LocalSyncSignIn …)` kept; sign-out still navigates to setup so T014 and T016 pass
- [X] T021 [US1] Update `androidTest/` tests that navigate from Today to past days via `menu_workouts` (e.g. `androidTest/log/CorrectionFlowTest.kt` or whichever uses it) to use `bar_tab_history` — *No instrumented test used `menu_workouts`; only the gallery did (updated in T028).*
- [X] T022 [P] [US1] Add gallery shots `40-tabs-today`, `41-tabs-history`, with the bar, in `debug/gallery/ScreenGallery.kt` and render in light / dark / dark RU — *Shots: Today and History carry the bar in their existing shots; new `28a-history-empty`, `40-exercises-coming-soon`, `41-money-coming-soon`.*

**Checkpoint**: US1 shippable: four tabs, two of them placeholders.

---

## Phase 4: User Story 2 — History tab (Priority: P1)

**Goal**: History is the past days list with "Enter a past workout"; Today loses its links.

**Independent Test**: History lists days newest first; tap a day → details; "Enter a past workout"
→ dialog → editor (no bar) → Save → back in History with the day listed; Today shows no links.

### Tests for User Story 2 ⚠️

- [X] T023 [P] [US2] Write failing Compose UI tests in `androidTest/log/HistoryTabTest.kt`: `past_days` is History's start screen; top bar action `history_past_workout` opens the past workout dialog; empty state `past_days_empty` shows the same button; after Save in the editor the current route is the past days list (spec US2 scenarios 1–3)
- [X] T024 [P] [US2] Extend `androidTest/home/MainScreenTest.kt` (or the Today screen test): `menu_workouts` and `menu_past_workout` do not exist (FR-006) — *Added to `androidTest/log/TodayScreenTest.kt` as a regression guard: the links lived in `TodayRoute`, so it could not fail first.*

### Implementation for User Story 2

- [X] T025 [US2] In `main/log/LogRoutes.kt` make `WorkoutsRoute` the History start: title "History" (`tab_history`), no back arrow on the tab start, top bar actions = `history_past_workout` icon button + `SyncStatusAction`; empty state with the "Enter a past workout" button in `main/log/CorrectionScreens.kt` (`PastDaysScreen`)
- [X] T026 [US2] Move `PAST_WORKOUT` routes into the history graph in `main/nav/TabGraphs.kt`; on Save pop to the past days start
- [X] T027 [US2] Remove the "Workouts" and "Enter a past workout" links and their callbacks (`onOpenWorkouts`, `onPastWorkout`) from `main/log/TodayScreen.kt`, `main/home/MainScreen.kt` and `main/MainActivity.kt`; remove the empty gap they left; delete unused strings in both `strings.xml`
- [X] T028 [P] [US2] Update the gallery in `debug/gallery/ScreenGallery.kt`: Today shots without links; History start with data and empty; render
- [X] T029 [US2] Update feature 002 docs that reference the old navigation: `specs/002-log-workout/contracts/screens.md` "Navigation" and "Main (Today)" (menu row) point to this feature's [contracts/screens.md](contracts/screens.md); reword `specs/002-log-workout/spec.md` FR-010 and FR-014 from "sets" to "changes" with a pointer to 004 FR-012

**Checkpoint**: US1 + US2 = the MVP release (bar, Today, History).

---

## Phase 5: User Story 3 — Exercises tab (Priority: P2)

**Goal**: List, search, add, rename (with past sets), delete never-logged exercises; synced to
drills / log / rec.

**Independent Test**: Add "Face pull" / "Shoulders" offline → in the list and in "Add exercise";
online → drills row; rename an exercise with history → history shows the new name, log rows
renamed after sync; delete is offered only without sets.

### Tests for User Story 3 ⚠️

- [ ] T030 [P] [US3] Write failing tests in `test/exercises/ExerciseRulesTest.kt`: name trimmed, 1–100 characters; group trimmed, 0–50 characters; duplicate check case-insensitive against the shown list (excluding the exercise being renamed); `deletable` false when any displayed set uses the name (FR-008, FR-008a, FR-008b)
- [ ] T031 [P] [US3] Write failing tests in `test/workout/ExerciseCoalescerTest.kt` for every row of the data-model coalescing table (none + add/rename/delete; ADD + rename/delete; RENAME + rename/delete), and that a rename returns the pending set changes (`NEW`, `EDIT`) to rewrite to the new name
- [ ] T032 [P] [US3] Write failing tests in `test/workout/DisplayModelExercisesTest.kt`: exercise list = cache + ADD (in its group) + RENAME (replaced) − DELETE, each with `pending` and `deletable`; name mapping shows cached log rows of `old` as `new` while a RENAME is pending (history, last time, record); groups ordered as in drills, new groups last
- [ ] T033 [P] [US3] Write failing sync tests in `test/sync/LogSyncExercisesTest.kt` with the fake Sheets API, one test per research R5 row for ADD / RENAME / DELETE, plus: a RENAME produces drills `updateCells` + `findReplace` on log B and rec A1 (rec skipped when `recSheetId` is null); chain A→B→C is one A→C replace; request order in the single `batchUpdate` per contracts/sheets.md; notices `EXERCISE_CHANGE_DROPPED`, `EXERCISE_IN_USE`; a dropped rename renames the waiting set changes back to the old name, so appended sets use the old name; spreadsheet ID change keeps ADD and drops RENAME/DELETE
- [ ] T034 [P] [US3] Write failing request-building tests in `test/google/SheetsLogClientTest.kt` for `writeLog` with drills deletes/renames, `findReplace` (exact JSON from contracts/sheets.md: `matchCase`, `matchEntireCell`, ranges) and drills `appendCells`
- [ ] T035 [P] [US3] Write failing Compose UI tests in `androidTest/exercises/ExercisesScreenTest.kt`: grouped list and `exercises_search`; `exercises_add` → `exercise_dialog` with group suggestions, Save disabled while invalid, duplicate error; detail shows `last_time`, `record`, `exercise_rename`; `exercise_delete` only when deletable with `exercise_delete_confirm`; `exercise_in_use_note` otherwise; waiting mark on pending exercises; `exercises_empty`; pull on `exercises_refresh` calls the sync once (fake scheduler)
- [ ] T036 [P] [US3] Write a failing test in `androidTest/nav/LogTodayFlowTest.kt`: exercise detail → `exercise_log_today` selects `bar_tab_today` and shows the plan sets dialog for that exercise; OK opens logging with no `bottom_bar` (spec US3 scenario 5)
- [ ] T037 [P] [US3] Write a failing test in `androidTest/log/TodayNoticesTest.kt`: `EXERCISE_CHANGE_DROPPED` and `EXERCISE_IN_USE` notices show their texts with the exercise name and can be dismissed (contracts/screens.md "Sync icon and sign out")

### Implementation for User Story 3

- [ ] T038 [P] [US3] Implement `main/exercises/ExerciseRules.kt` so T030 passes
- [ ] T039 [P] [US3] Implement `main/workout/ExerciseCoalescer.kt` so T031 passes
- [ ] T040 [US3] Extend `main/workout/DisplayModel.kt` (exercise list, name mapping) and `main/workout/data/WorkoutRepository.kt` (`exercises: Flow<List<ShownExercise>>`, `addExercise`, `renameExercise` — coalesce + rewrite pending set names in one transaction —, `deleteExercise`) so T032 passes; the picker in `main/log/ExercisePicker.kt` uses the shown list
- [ ] T041 [US3] Extend `main/google/SheetsLogClient.kt` `writeLog` with drills deletes/renames, `findReplace` and drills appends in the contract order so T034 passes
- [ ] T042 [US3] Extend `main/sync/LogSync.kt` and `LogSyncStore` with exercise planning per research R5 and new `NoticeKind` values so T033 passes; `main/workout/data/WorkoutRepository.kt` `commit` also removes handled exercise changes and `keepOnlyNewSets` also keeps ADD
- [ ] T043 [US3] Implement `main/exercises/ExercisesViewModel.kt`, `ExercisesScreen.kt` (grouped list, search, FAB, `PullToRefreshBox` → `syncScheduler.runNow()`), `ExerciseDetailScreen.kt` (last time and record via existing `History`, buttons per contracts/screens.md), `ExerciseDialog.kt`; wire them into the exercises graph in `main/nav/TabGraphs.kt` so T035 passes
- [ ] T044 [US3] "Log today": from exercise detail switch to the Today tab and open the plan sets dialog for that exercise, then logging (contracts/screens.md "Navigation") so T036 passes
- [ ] T045 [US3] Show the two new notices on Today (`main/log/TodayScreen.kt` notices) with strings `notice_exercise_change_dropped`, `notice_exercise_in_use` (EN/RU) so T037 passes
- [ ] T046 [US3] Strings for Exercises (titles, buttons, errors "This exercise already exists", "Exercises with logged sets can't be deleted", empty state) in both `strings.xml`
- [ ] T047 [P] [US3] Gallery shots `50-exercises`, `51-exercise-detail`, `52-exercise-detail-in-use`, `53-exercise-dialog-error` in `debug/gallery/ScreenGallery.kt`; render

**Checkpoint**: Exercises fully usable offline and synced.

---

## Phase 6: User Story 4 — Money tab (Priority: P2)

**Goal**: Balance with the once-a-day note, payments and totals, add payment.

**Independent Test**: Numbers match the spreadsheet; a payment added offline appears as waiting
and lands in the money tab after sync; the balance note reflects the automation state.

### Tests for User Story 4 ⚠️

- [ ] T048 [P] [US4] Write failing tests in `test/money/PaymentValuesTest.kt`: date not after today in the spreadsheet time zone, default today; workouts whole number 1–999; sum decimal ≥ 0, ≤ 9,999,999.99, up to 2 decimals, "1 500,50" and "1500.5" parsed; date → serial (whole days from 1899-12-30) (research R8)
- [ ] T049 [P] [US4] Write failing tests in `test/money/BalanceNoteTest.kt`: `On` → last calculation time; `Stopped` → date + stopped + link; `NotEnabled` / `ScriptMissing` → not updated + link; extra line when a pending payment exists or a payment date is after `lastDailyRun` (research R7)
- [ ] T050 [P] [US4] Write failing tests in `test/google/MoneyClientTest.kt` (MockWebServer) for call 23: exact query (ranges, `includeGridData`, `fields`); only existing tabs requested, missing → `MissingTab`; payment rows with `rowIndex`, non-numbers → null; balance number or null; metadata map; error mapping per contracts/sheets.md
- [ ] T051 [P] [US4] Write failing tests in `test/workout/DisplayModelMoneyTest.kt`: payments = cache + pending (waiting), newest date first, same date pending first then higher row first; totals count only numbers
- [ ] T052 [P] [US4] Write failing sync tests in `test/sync/LogSyncPaymentsTest.kt`: money read only with pending payments and an existing money tab; content matching per research R5 (two identical pending payments vs one existing row → one append); `appendCells` to money with serial, workouts, sum; money tab missing → payments kept, sets still synced; after a write a money refresh is requested
- [ ] T053 [P] [US4] Write failing tests in `test/money/MoneyRefresherTest.kt`: refresh on open when older than 5 min, on pull always, once per app start, after a sync that wrote payments; never under the sync mutex; failures stored in `money_summary.error` with cached values kept
- [ ] T054 [P] [US4] Write failing Compose UI tests in `androidTest/money/MoneyScreenTest.kt`: `money_balance` positive and negative ("workouts owed", error colour), `money_balance_note` variants with link, `money_downloaded`, `money_totals`, `money_payment_<i>` order and waiting mark, `money_empty`, `money_error`; `money_add` → `payment_dialog` with defaults (today, last workouts/sum), future dates disabled, Save disabled while invalid

### Implementation for User Story 4

- [ ] T055 [P] [US4] Implement `main/money/PaymentValues.kt` so T048 passes
- [ ] T056 [P] [US4] Implement `main/money/BalanceNote.kt` (uses `main/automation/AutomationStatus.kt`) so T049 passes
- [ ] T057 [P] [US4] Implement `main/google/MoneyClient.kt` (call 23) so T050 passes
- [ ] T058 [US4] Extend `main/workout/DisplayModel.kt` and `main/workout/data/WorkoutRepository.kt` (`payments`, `moneySummary` flows, `addPayment`) so T051 passes
- [ ] T059 [US4] Extend `main/sync/LogSync.kt` and `main/google/SheetsLogClient.kt` with payment matching and money `appendCells` so T052 passes
- [ ] T060 [US4] Implement `main/money/MoneyRefresher.kt` and start it from `main/AppContainer.kt` (app start) so T053 passes
- [ ] T061 [US4] Implement `main/money/MoneyViewModel.kt`, `MoneyScreen.kt`, `PaymentDialog.kt` (Material 3 `DatePicker` with future dates disabled); "Enable automation" link reuses `main/home/AutomationReminder.kt`'s action; wire into the money graph in `main/nav/TabGraphs.kt` so T054 passes
- [ ] T062 [US4] Strings for Money (balance, owed, notes, totals, dialog labels and errors, empty and error states) in both `strings.xml`
- [ ] T063 [P] [US4] Gallery shots `60-money`, `61-money-owed`, `62-money-automation-off`, `63-payment-dialog`, `64-money-empty` in `debug/gallery/ScreenGallery.kt`; render

**Checkpoint**: all four tabs complete.

---

## Phase 7: Polish & Cross-Cutting

- [ ] T064 [P] Check the bar at 360 dp and font scale 2.0 in RU and EN in the gallery (add a large-font shot `42-tabs-large-font-ru`) (SC-006)
- [ ] T065 [P] Update `README.md` (app overview: tabs, adding exercises and payments in the app) and `design/redesign-brief.md` (idea F done → point 2 "Bottom tabs")
- [ ] T066 Publish the before/after gallery as an artifact for review
- [ ] T067 Run the device scenarios in [quickstart.md](quickstart.md) on the emulator and the test phone, including the SC-002 timing in scenario 2; record results in `specs/004-bottom-tab-navigation/quickstart-results.md`
- [ ] T068 Final CI green; merge; confirm the internal-track build and the 1 → 2 upgrade from the Play build on the phone

---

## Dependencies & Execution Order

- **Setup (T001–T003)** → everything. T001 before T006.
- **Foundational (T004–T013)** → US3, US4. US1/US2 do not need it.
- **US1 (T014–T022)** → US2 (History graph lives in the tab graphs), US3 and US4 (their graphs).
- **US2** after US1; **US3** and **US4** after US1 + Foundational, independent of each other and
  of US2.
- Within a story: tests → domain → data → sync → UI → gallery.
- `main/sync/LogSync.kt`, `main/workout/DisplayModel.kt`, `WorkoutRepository.kt`,
  `SheetsLogClient.kt` and both `strings.xml` are shared by US3 and US4: do their tasks one story
  at a time, not in parallel.

## Parallel Examples

```text
Setup:        T002, T003 together (T001 alone, needs a build)
Foundational: T009, T011 and T012 while T004–T008 run
US1:          T014 + T015 + T016 together; then T017 → T018 → T019 → T020
US3 tests:    T030–T037 together
US3 impl:     T038 + T039 together, then T040 → T041 → T042 → T043
US4 tests:    T048–T054 together
US4 impl:     T055 + T056 + T057 together, then T058 → T059 → T060 → T061
```

## Implementation Strategy

1. **MVP = US1 + US2** (T001–T003, T014–T029): one PR, shipped to internal testing. The bar,
   Today without links, History; Exercises and Money show a "Coming soon" start screen
   (`tab_placeholder`) until US3 and US4 land.
2. Foundational + **US3** as the second PR.
3. **US4** as the third PR.
4. Polish with each PR (gallery) and at the end (quickstart, README).
