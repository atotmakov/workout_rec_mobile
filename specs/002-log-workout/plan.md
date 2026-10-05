# Implementation Plan: Log New Workout

**Branch**: `002-log-workout` | **Date**: 2026-10-05 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/002-log-workout/spec.md`

## Summary

The main screen becomes today's workout. The user adds an exercise from the drills list, plans
sets, and confirms each set (weight and reps pre-filled from last time, − / + steppers), seeing
last time and the record. Every confirmed set is saved first in a local Room database and synced
to the log tab by a WorkManager job that also runs after the app is closed. Each sync reads the
log once, matches the app's pending changes to rows by content key (date-time to the second +
exercise + weight + reps), and applies deletes, edits and appends in one atomic `batchUpdate`
(`appendCells` lets the server place new rows after the last filled row). The same read refreshes
the local cache used for history, past days, last time and the record. Past workouts are drafts
whose set times are spread over the entered duration on save. Details: [research.md](research.md).

## Technical Context

**Language/Version**: Kotlin 2.1.0, AGP 8.7.3, compileSdk/targetSdk 35 (as in feature 001)

**Primary Dependencies**: existing — Jetpack Compose (Material 3), Navigation Compose, Lifecycle
ViewModel, OkHttp + kotlinx.serialization, DataStore, Play services Authorization API; new —
Room 2.6.1 (KSP 2.1.0-1.0.29), WorkManager 2.10.0 (R14)

**Storage**: Google Sheets log and drills tabs (source of truth for synced sets); Room database on
the phone for caches, pending changes, drafts and notices ([data-model.md](data-model.md));
DataStore for sync status

**Testing**: JUnit 4 + kotlinx-coroutines-test + MockWebServer (JVM: domain logic, sync algorithm
with fake Sheets, Sheets client contract); instrumented on the CI emulator: Compose UI tests,
Room DAO tests (`room-testing`, in-memory DB), worker tests (`work-testing`)

**Target Platform**: Android 8.0+ (minSdk 26) with Google Play services

**Project Type**: mobile-app (Android), no backend; no Apps Script change

**Performance Goals**: confirming a set saves locally in < 100 ms and never waits for the network;
pre-filled set in ≤ 2 taps (SC-001); online sets in the log within 1 min, offline within 15 min
of reconnecting (SC-003); one sync = 2 reads + 1 write

**Constraints**: offline-first (constitution III); exactly-once writes (FR-009); never overwrite
or reorder web-UI rows (FR-015); no change to the reference spreadsheet structure; `drive.file`
scope only; RU/EN

**Scale/Scope**: one user; log of up to ~20,000 rows; ≥ 200 sets per day offline (SC-005);
~8 screens/dialogs; 3 Sheets calls

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Check | Result |
|-----------|-------|--------|
| I. Native Android App | Kotlin, Compose, WorkManager and Room (Android Jetpack); system back and navigation conventions | PASS |
| II. Test-First | Each component starts with failing tests: value parsing, serial conversion, coalescing, display model, last time / record / pre-fill, past-workout times, sync algorithm (fake Sheets), Sheets client (MockWebServer), DAO and worker (instrumented), screens (Compose UI tests). Red then green CI runs per [CI workflow](../../README.md). Only the real Google round trip and the R4 spike are manual ([quickstart](quickstart.md)) | PASS |
| III. Hybrid Data Architecture | Sets are saved on the phone first and synced in the background as often as possible (after each set and on reconnect). **Conflict resolution**: new rows are only appended (server-positioned); edits/deletes apply only if the row still has the values the app last saw, otherwise the web-UI version wins and the user is notified; caches are replaced from the sheet on each sync; the remaining read-then-write window (~1 s) is accepted as best effort (R6) | PASS |
| IV. Maximize Use of Google Services | Only the Sheets API with the existing token; no server; no new scope | PASS |
| Technology Constraints | Room, KSP and WorkManager justified in R14; consistent with feature 001's choices | PASS |
| Workflow & Quality Gates | Spec Kit flow; this table | PASS |

**Post-design re-check (after Phase 1)**: PASS. The design keeps the reference structure (no id
column), and every conflict path ends in "sheet wins" or a user-visible notice.

## Project Structure

### Documentation (this feature)

```text
specs/002-log-workout/
├── plan.md              # This file
├── research.md          # Phase 0: decisions R1–R14
├── data-model.md        # Phase 1: Room tables, display model, sync transitions
├── quickstart.md        # Phase 1: automated, spike and device validation
├── contracts/
│   ├── sheets-log.md    # Sheets calls 20–22 and the sync algorithm
│   └── screens.md       # Screens, actions, test tags
└── tasks.md             # Phase 2 (/speckit-tasks)
```

### Source Code (repository root)

```text
gradle/libs.versions.toml                # + room, ksp, work
app/build.gradle.kts                     # + ksp plugin, room schema dir, deps
app/schemas/                             # Room schema JSON (exported, versioned)
app/src/main/java/com/workoutrec/
├── AppContainer.kt                      # + database, repositories, sync scheduler
├── MainActivity.kt                      # + navigation to new screens
├── google/SheetsClient.kt               # + calls 20–22 (readLogAndDrills, logSheetInfo, batchUpdate reuse)
├── workout/                             # NEW — pure domain, JVM-tested
│   ├── Values.kt                        # Weight, Reps parsing/validation, SetKey
│   ├── SheetTime.kt                     # serial ↔ epoch in a time zone
│   ├── DisplayModel.kt                  # cache + pending → DisplaySet, today, past days
│   ├── History.kt                       # last time, record, pre-fill
│   ├── PastWorkoutTimes.kt              # R10
│   └── Coalescer.kt                     # pending-change coalescing table
├── workout/data/                        # NEW — Room
│   ├── WorkoutDatabase.kt, Entities.kt, WorkoutDao.kt
│   └── WorkoutRepository.kt             # transactions used by screens and sync
├── sync/                                # NEW
│   ├── LogSync.kt                       # algorithm in contracts/sheets-log.md (JVM-tested)
│   ├── SyncWorker.kt                    # WorkManager CoroutineWorker → LogSync
│   ├── SyncScheduler.kt                 # enqueue unique work, in-process run, mutex
│   └── SyncStatusStore.kt               # DataStore keys
├── log/                                 # NEW — UI
│   ├── TodayScreen.kt, TodayViewModel.kt
│   ├── ExercisePicker.kt, PlanSetsDialog.kt
│   ├── ExerciseLoggingScreen.kt, ExerciseLoggingViewModel.kt, SetRow.kt
│   ├── PastDaysScreen.kt, DayDetailScreen.kt, EditSetDialog.kt
│   └── PastWorkoutDialog.kt, PastWorkoutEditor.kt
├── home/MainScreen.kt, SignOutDialog.kt # Today as content; unsynced warning
└── res/values{,-ru}/strings.xml         # new strings
app/src/test/java/com/workoutrec/{workout,sync,google}/   # JVM tests + fakes
app/src/androidTest/java/com/workoutrec/{log,workout/data,sync}/  # UI, DAO, worker tests
```

**Structure Decision**: Same single `app` module. Pure logic lives in `workout/` and `sync/`
without Android types, so most behaviour is covered by fast JVM tests; Room, WorkManager and
Compose are thin layers tested on the CI emulator.

## Implementation order and risks

1. **Spike R4** (manual, quickstart) — decides `appendCells` vs fallback.
2. Domain (`workout/`): values, time, coalescing, display model, history, past-workout times.
3. Room layer and repository; Sheets client calls 20–22.
4. `LogSync` + worker + scheduler (US2), sync status.
5. UI: Today + picker + logging (US1, US3), Workouts/day detail/edit (US4), past workout (US5),
   sign-out warning; RU/EN strings.
6. Device run of the quickstart.

| Risk | Impact | Mitigation |
|------|--------|------------|
| `appendCells` places rows after row 1000 of an empty table | Gaps in the log | Spike first; `updateCells` fallback (R4) |
| Web edit lands in the ~1 s between read and write | A web value overwritten | Accepted best effort (R6); window kept minimal (read immediately before write) |
| Background token not available (consent revoked) | Sets wait | `NeedsSignIn` status; consent on next app open (R3) |
| Duplicate content keys from web rows with identical time and values | Wrong row edited | Closest-to-`rowHint` rule; unique keys for app-written sets (R5) |
| KSP / Room version mismatch with Kotlin 2.1.0 | Build fails | Versions pinned in R14; CI catches it in the setup task |
| Large logs make each sync slow | Battery / data | ~400 KB per 10k rows; refresh at most every 5 min except after writes (R7) |

## Complexity Tracking

No constitution violations; nothing to justify.
