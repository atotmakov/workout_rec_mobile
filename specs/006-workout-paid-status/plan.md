# Implementation Plan: Paid or Free Workouts

**Branch**: `006-workout-paid-status` | **Date**: 2026-10-23 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/006-workout-paid-status/spec.md`

## Summary

Each workout day gets a Paid/Free status, stored in the spreadsheet's existing `workout` tab
column C "work alone" (`1` = Free), which the nightly balance already uses. On the phone a new
`day_status` table holds the chosen statuses and a cache of the tab. A day takes the default (the
previous workout day's status, Free for the first) when its first set is logged, so later changes
to earlier days never ripple forward. The existing sync run reads `workout!A2:C` with the log and
writes statuses in the same atomic `batchUpdate` as sets: update column C of the day's row, or
append `[date, empty, flag]`. The attached script (version 2) fills the empty durations at night
and gives days typed in the web UI the same default. Because `install` never updates an existing
script, the app gains an upgrade step: on app start it uploads the new script when the
spreadsheet's `workout_rec.script_version` is older. Today, a day's details and the past-workout
dialog get a Paid | Free segmented button; the History list shows each day's status. Details:
[research.md](research.md).

## Technical Context

**Language/Version**: Kotlin 2.1.0, AGP 8.7.3, compileSdk/targetSdk 35; Apps Script V8 (unchanged)

**Primary Dependencies**: existing only: Compose Material 3, Navigation Compose 2.8.5, Room,
OkHttp, kotlinx.serialization; Sheets API v4 and Apps Script API via the existing clients. No new
dependency.

**Storage**: Room schema 2 → 3 (new table `day_status`, nullable column on `past_workout_draft`,
auto-migration); spreadsheet: `workout` tab values only, new metadata key
`workout_rec.script_version`

**Testing**: JUnit 4 + coroutines-test + MockWebServer with the existing fakes (JVM); Room
migration test; Compose UI tests on the CI emulator; `node --test` for the script

**Target Platform**: Android 8.0+ (minSdk 26)

**Project Type**: mobile-app (Android) plus the attached Apps Script

**Performance Goals**: a status change shows at once (one local write); it reaches the sheet in
the next sync run, which costs no extra request (same call 21 and 22)

**Constraints**: offline-first (constitution III); no spreadsheet structure change; failures
reported without sheet contents (constitution V)

**Scale/Scope**: about one `workout` row per day (hundreds per year); 4 screens touched; 2 new
`NonFatalIssue` kinds; script version 1 → 2

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Check | Result |
|-----------|-------|--------|
| I. Native Android App | Kotlin and Compose only | PASS |
| II. Test-First | Failing tests first for the rules, sync, client, repository, upgrade, migration, UI and the script (R10) | PASS |
| III. Hybrid Data Architecture | Status kept on the phone first, works offline, synced with sets; the `workout` tab stays editable in the web UI and its edits reach the app; conflict rule: a pending phone choice wins, otherwise the sheet (R4) | PASS |
| IV. Maximize Use of Google Services | Sheets API and Apps Script API only; the script is the existing attached one | PASS |
| V. Error Reporting to AppTracer | `WORKOUT_TAB_MISSING` and `SCRIPT_UPGRADE_FAILED` through `Monitoring` (`failure` splits no-connection); details are fixed values, no dates or statuses; each path has a test; the catch-site guard covers the new catches | PASS |
| Technology Constraints | No new dependency; Room and Compose as chosen in 002/004 | PASS |
| Workflow & Quality Gates | Spec Kit flow; CI runs JVM, UI and script tests | PASS |

**Post-design re-check (after Phase 1)**: PASS. The only new outbound write path (script upload)
uses the existing scope and client; the conflict rule is in research R4 and contracts/sheets.md.

## Project Structure

### Documentation (this feature)

```text
specs/006-workout-paid-status/
├── plan.md              # This file
├── research.md          # Phase 0: R1–R10
├── data-model.md        # day_status, rules, schema 3, sheet use
├── quickstart.md        # Automated and device validation
├── contracts/
│   ├── sheets.md        # calls 20–22 changes
│   ├── apps-script.md   # script v2 and the upgrade
│   └── screens.md       # Paid | Free control, History label
├── checklists/requirements.md
└── tasks.md             # Phase 2 (/speckit-tasks)
```

### Source Code (repository root)

```text
apps-script/
├── Logic.gs                          # workoutRowsPlan replaces dailyWorkoutRows
├── Code.gs                           # dailyJob applies appends and durations
└── tests/{logic,code}.test.mjs
app/src/main/java/com/workoutrec/
├── workout/DayStatus.kt              # NEW: DayStatus, KnownStatus, DayStatusRules
├── workout/data/Entities.kt          # DayStatusEntity; PastWorkoutDraftEntity.free
├── workout/data/WorkoutDao.kt        # day_status queries, commit/clear changes
├── workout/data/WorkoutDatabase.kt   # version 3, AutoMigration(2, 3)
├── workout/data/WorkoutRepository.kt # statuses flow, setStatus, default on first set, waiting count
├── google/SheetsLogClient.kt         # workout sheet ID, range, updates, appends
├── sync/LogSync.kt                   # status step (R4), WORKOUT_TAB_MISSING
├── sync/SyncStatusStore.kt           # SheetInfoCache.workoutSheetId, version
├── automation/ScriptInstaller.kt     # SCRIPT_VERSION 2, upgrade(), script_version metadata
├── automation/AutomationStatus.kt    # SCRIPT_VERSION key constant
├── setup/SpreadsheetSetupFlow.kt     # refresh → upgrade when older
├── monitoring/Monitoring.kt          # WORKOUT_TAB_MISSING, SCRIPT_UPGRADE_FAILED
├── log/DayStatusControl.kt           # NEW: Paid | Free segmented button
├── log/LogRoutes.kt                  # Today, Day route wiring
├── log/CorrectionScreens.kt          # History list label, day details control
├── log/PastWorkoutScreens.kt         # dialog control
├── log/PastWorkoutViewModel.kt       # status in setup/save
└── res/values{,-ru}/strings.xml      # day_status_*
app/schemas/…/3.json                  # exported schema
app/src/test/java/com/workoutrec/
├── workout/DayStatusRulesTest.kt, workout/data/DayStatusRepositoryTest.kt, MigrationTest
├── sync/LogSyncDayStatusTest.kt, google/SheetsLogClient… (workout cases)
└── automation/ScriptUpgradeTest.kt, setup/… (refresh upgrade)
app/src/androidTest/java/com/workoutrec/log/DayStatusControlTest.kt (+ History, past workout cases)
```

**Structure Decision**: Same single `app` module and `apps-script/` folder. The rule is pure
Kotlin in `workout/`, like `History` and `Coalescer`; the sync step lives in `LogSync` next to
payments, which follow the same read–match–write pattern.

## Implementation order and risks

1. `DayStatusRules`, schema 3 and the repository (default on first set, waiting count).
2. Client and sync step (reads and writes), `WORKOUT_TAB_MISSING`.
3. Script v2 (`workoutRowsPlan`, `dailyJob`) with Node tests; installer upgrade and
   `script_version`; refresh calls it.
4. UI: Today control (US1/US2 done), History label and day details (US3/US4), past-workout dialog.
5. Device quickstart #6–#15 on a test spreadsheet.

| Risk | Impact | Mitigation |
|------|--------|------------|
| Time trigger keeps running the old code after `updateContent` | Durations of app-added rows stay empty; web-only days stay Paid | Verified on the device (quickstart #9); fallback: create a new version and re-point the trigger in the upgrade |
| Upgrade can't run (Apps Script API setting off, offline) | Same as above until it runs | Retried on every start; reported (`SCRIPT_UPGRADE_FAILED`), visible in AppTracer |
| App and the 03:00 script add a row for the same day at the same moment | Two rows; the balance counts the day twice | Very unlikely (sync at 03:00 on the same day); the app reads and writes only the first row; documented edge case |
| A day with an app row and the old script (v1) | Row has no duration until the upgrade | Upgrade on the next online start |
| Day status shown before the sheet is read the first time after update | Days show the default | Same as any cache before its first read; one sync fixes it |

## Complexity Tracking

No constitution violations; nothing to justify.
