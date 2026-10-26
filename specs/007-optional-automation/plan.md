# Implementation Plan: Optional Spreadsheet Automation

**Branch**: `007-optional-automation` | **Date**: 2026-10-24 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/007-optional-automation/spec.md`

## Summary

The spreadsheet now calculates its own numbers. Each `workout` row's duration is a formula. The
same formula text goes in every row, and it finds its own date through `INDEX(A:A, ROW())`. The
balance in `balance!A1` is a formula with the same rule as the script's.

The app keeps the `workout` tab in step with the `log` on every sync, in the same atomic
`batchUpdate` as the sets:

- it adds a row for every date with sets (the waiting status or the 006 default, with the
  formula in B);
- it removes rows whose date has no sets;
- it updates statuses as in 006.

After each successful sync, a form step reads the calculated cells and repairs them (call 24, plus
call 25 when something needs writing). The first time it runs, it converts an old spreadsheet and
marks it `structure_version = 3`. The balance formula waits while an old script would still
overwrite it at night.

Setup ends at Today with no script steps, and the reminder is gone. The automation steps are
reached from a new item in the account menu. Script v3 adds and removes rows
and writes formulas. It never writes durations, and it writes the balance only into an
unconverted spreadsheet. A shared fixture keeps the app's and the script's day-row rules equal.

Details are in [research.md](research.md).

## Technical Context

**Language/Version**: Kotlin 2.1.0, AGP 8.7.3, compileSdk/targetSdk 35. Apps Script V8.

**Primary Dependencies**: Existing ones only: Compose Material 3, Navigation Compose, Room,
OkHttp, kotlinx.serialization, and the Sheets API v4 and Apps Script API clients. No new
dependency.

**Storage**: Room schema 3 → 4 (`money_summary.calculated`, auto-migration). In the spreadsheet:
formulas in `workout!B` and `balance!A1`, and metadata `structure_version` 3 and `script_version` 3.

**Testing**: JUnit 4, coroutines-test and MockWebServer with the existing fakes (JVM). A Room
migration test. Compose UI tests on the CI emulator. `node --test` for the script. One shared
JSON fixture is used by both rule implementations.

**Target Platform**: Android 8.0+ (minSdk 26).

**Project Type**: mobile app (Android) plus the attached Apps Script.

**Performance Goals**: The balance and durations update as soon as the change is in the sheet
(one sync). There is one extra GET per sync run, plus one batchUpdate only when a repair is
needed.

**Constraints**:
- Offline-first (constitution III).
- No tab, header or table changes, so `StructureValidator` is untouched.
- Reports carry no spreadsheet contents (constitution V).
- Formulas are written with the API's English function names and comma separators. They are
  compared after the `;` → `,` normalisation (ru_RU locale).

**Scale/Scope**:
- About one `workout` row per day (hundreds per year). The formula scans `log!A:A` (thousands of
  rows), which is acceptable.
- About 6 screens or components touched.
- 2 new `NonFatalIssue` keys.
- Script version 2 → 3.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Check | Result |
|-----------|-------|--------|
| I. Native Android App | Kotlin and Compose only | PASS |
| II. Test-First | Failing tests come first for `DayRowsPlan` (with the shared fixture), `CalculatedForm`, the LogSync day-rows step, `FormCheck`, the setup flow without attach, `BalanceNotes`, migration 3→4, the UI entries, and script v3 (Node) | PASS |
| III. Hybrid Data Architecture | Everything stays in the sheet and is editable in the web UI. Formulas recalculate on web edits. Logging stays offline-first. The conflict rule is in research R3: the log decides which days exist, statuses follow 006 R4, and calculated cells are never written as values | PASS |
| IV. Maximize Use of Google Services | Sheets formulas, the Sheets API and Apps Script only. No backend | PASS |
| V. Error Reporting to AppTracer | `CALCULATION_RESTORED` and `CALCULATION_CHECK_FAILED` go through `Monitoring`, with fixed keys and counts only. Day-row failures are sync failures, already reported. Each new catch has a test | PASS |
| Technology Constraints | No new dependency. Room and Compose as chosen before | PASS |
| Workflow & Quality Gates | Spec Kit flow. CI runs the JVM, UI and script tests | PASS |

**Post-design re-check (after Phase 1)**: PASS.
- The new write paths are the deletes in call 22 and call 25. They use the existing scope and
  client, they are atomic, and they are covered by tests.
- The empty-log guard (R3) limits the one destructive rule.
- The gate (R6) prevents a fight with v1/v2 scripts.

## Project Structure

### Documentation (this feature)

```text
specs/007-optional-automation/
├── plan.md              # This file
├── research.md          # Phase 0: R1–R10
├── data-model.md        # rows, calculated cells, form states, schema 4
├── quickstart.md        # automated and device validation
├── contracts/
│   ├── sheets.md        # call 4, 22 changes; new calls 24–25
│   ├── apps-script.md   # script v3, shared fixture
│   └── screens.md       # first run, Money note and entry, account menu
├── checklists/requirements.md
└── tasks.md             # Phase 2 (/speckit-tasks)
```

### Source Code (repository root)

```text
apps-script/
├── Logic.gs                              # workoutRowsPlan v3 (appends, removals, formulaRows)
├── Code.gs                               # dailyJob v3, balance fallback, ENABLE_TEXTS
└── tests/{logic,code}.test.mjs, tests/fixtures/day-rows.json   # NEW fixture
app/src/main/java/com/workoutrec/
├── spreadsheet/ReferenceStructure.kt     # DURATION_FORMULA, BALANCE_FORMULA, STRUCTURE_VERSION "3", balance formula on create
├── spreadsheet/CalculatedForm.kt         # NEW: pure repair/marker/report decision (R5, R6)
├── workout/DayRowsPlan.kt                # NEW: pure day-rows rule (R3), replaces LogSync.planStatuses' core
├── google/SheetsLogClient.kt             # WorkoutWrite deletes + formula appends; calls 24, 25
├── sync/LogSync.kt                       # day-rows step; no WORKOUT_ROW_NOT_UPDATED; FormCheck after commit
├── sync/FormCheck.kt                     # NEW: calls 24/25, gate inputs, reporting
├── automation/ScriptInstaller.kt         # SCRIPT_VERSION 3, DURATION_FORMULA in Config.gs
├── setup/SpreadsheetSetupFlow.kt         # Ready without attach; refresh → automation status
├── setup/SetupState.kt, SetupViewModel.kt# StartAutomation, RefreshResult.Status(automation), no Reminder
├── home/AutomationReminder.kt            # DELETED
├── automation/AutomationStatusText.kt    # NEW: the account menu item's status text
├── home/MainScreen.kt, MainActivity.kt   # menu item, banner removed, return after the guide
├── money/BalanceNote.kt, MoneyScreens.kt, MoneyRoute.kt, MoneyRefresher.kt  # notes R9, entry
├── workout/data/{Entities,WorkoutDatabase}.kt  # money_summary.calculated, version 4
├── monitoring/Monitoring.kt              # CALCULATION_RESTORED, CALCULATION_CHECK_FAILED
└── res/values{,-ru}/strings.xml          # entry, notes; removed reminder/off strings
app/schemas/…/4.json
app/src/test/java/com/workoutrec/
├── workout/DayRowsPlanTest.kt, workout/DayRowsFixtureTest.kt
├── spreadsheet/CalculatedFormTest.kt, ReferenceStructureTest (formulas on create)
├── sync/LogSyncDayRowsTest.kt, sync/FormCheckTest.kt, google/SheetsLogClient (calls 22/24/25)
├── setup/SpreadsheetSetupFlowTest (no attach, StartAutomation), money/BalanceNotesTest, MigrationTest
app/src/androidTest/java/com/workoutrec/{money/AutomationEntryTest.kt, home/AccountMenuTest, log/TodayNoReminderTest}
README.md, user guide doc                 # shorter first run
```

**Structure Decision**: This keeps the single `app` module and the `apps-script/` folder. The new
rules are pure objects next to the existing ones: `DayRowsPlan` next to `DayStatusRules`, and
`CalculatedForm` next to `StructureValidator`. `FormCheck` is a small collaborator that `LogSync`
calls after commit, so the existing run keeps its shape.

## Implementation order and risks

1. **Formulas**: `ReferenceStructure` formulas and a create-time test. Check by hand on one test
   spreadsheet that the formulas evaluate in ru_RU and inside the tables (quickstart #2).
2. **Rule and fixture**: the shared fixture, `DayRowsPlan` and script v3's `workoutRowsPlan`,
   developed test-first against the same cases.
3. **Call 22 day-rows step**: the step in `LogSync`, and the client deletes and formula appends.
4. **Form step**: `CalculatedForm`, `FormCheck`, calls 24/25, the gate, and monitoring.
5. **Script v3**: `dailyJob` and the installer at version 3.
6. **Setup and UI**: setup without attach, removing the reminder, the automation item (account
   menu), Money notes, and schema 4.
7. **Docs and device**: README and user-guide "Getting started", then device quickstart #1–#13.

| Risk | Impact | Mitigation |
|------|--------|------------|
| A formula behaves differently in a Sheets table or the ru_RU locale | Durations or balance show an error | Verified first (order step 1). Fallback: write plain A1 references per row on append (row known after the re-read) |
| Removing rows deletes real history (a log read that misses rows) | Lost day rows, changed balance | Atomic read and write per run. Empty-log guard. Version history keeps the rows (spec assumption). Removed only for dates with zero sets |
| The web UI inserts a row between the read and the write | Wrong row deleted or updated | Same window as 002's log deletes (milliseconds). The next run re-adds missing days |
| An old script overwrites the balance before the upgrade | Balance flips between value and formula; noisy reports | Gate R6. The upgrade runs at app start before the first sync |
| The app and a v3 script add the same day at once | Duplicate row; the day counts twice | Same as 006 (very unlikely, 03:00). Neither adds when a row exists |
| An extra GET per sync | Quota or battery | One small request. Quota is 60 per minute per user. Background sync is already coalesced |

## Complexity Tracking

There are no constitution violations, so nothing needs justifying.
