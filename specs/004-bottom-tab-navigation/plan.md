# Implementation Plan: Bottom Tab Navigation

**Branch**: `004-bottom-tab-navigation` | **Date**: 2026-10-22 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/004-bottom-tab-navigation/spec.md`

## Summary

A Material 3 bottom bar with four tabs (Today, History, Exercises, Money) replaces the links on
Today. Each tab is a nested graph in the existing Navigation Compose host, with saved and
restored back stacks. History takes the 002 past days and past workout screens. Exercises shows
the drills list and lets the user add, rename (past sets renamed too) and delete never-logged
exercises. Money shows `balance!A1` with a once-a-day note, the payments and totals, and adds
payments. Exercise changes and payments are saved on the phone first, as sets are, and written by
the existing sync run in the same atomic `batchUpdate`. Renames use `findReplace` on the log and
`rec!A1`. Money data comes from one new read call that never affects the set sync. Details:
[research.md](research.md).

## Technical Context

**Language/Version**: Kotlin 2.1.0, AGP 8.7.3, compileSdk/targetSdk 35 (unchanged)

**Primary Dependencies**: existing only: Compose Material 3 (`NavigationBar`,
`PullToRefreshBox`, `DatePicker`), Navigation Compose 2.8.5, Room 2.6.1, WorkManager, OkHttp +
kotlinx.serialization. No new dependency (research R1: own vector icons).

**Storage**: Google Sheets drills, log, rec, money and balance tabs; Room `workout.db` schema
v2 with 4 new tables via auto-migration ([data-model.md](data-model.md), R9); DataStore sheet
info cache gains 4 sheet IDs

**Testing**: JUnit 4 + coroutines-test + MockWebServer (JVM); Compose UI, Room migration and DAO
tests on the CI emulator; Roborazzi screen gallery

**Target Platform**: Android 8.0+ (minSdk 26)

**Project Type**: mobile-app (Android); no Apps Script change

**Performance Goals**: tab switch ≤ 0.5 s from local data, also offline (SC-002); exercise or
payment in the sheet within 1 min online (SC-004, SC-008); a sync stays 2 reads + 1 write

**Constraints**: offline-first; one atomic write per sync; the app never writes `balance`; a
broken money or balance tab must not stop the set sync; RU/EN, light/dark; 360 dp at the largest
font

**Scale/Scope**: drills ≤ ~300 rows, money ≤ ~200 rows, log ~20,000 rows; 4 tabs, 3 new screens,
2 dialogs

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Check | Result |
|-----------|-------|--------|
| I. Native Android App | Kotlin + Compose; Material 3 bottom navigation with system Back as the platform defines it (R1) | PASS |
| II. Test-First | Failing tests first for every unit: exercise rules and coalescing, rename mapping, sync planning (each row of R5's table) with a fake Sheets API, calls 21–23 with MockWebServer, payment validation, balance note, migration 1→2, DAOs, bar navigation and tab screens (R11) | PASS |
| III. Hybrid Data Architecture | Exercises and payments stay in the sheet (drills, money) and remain editable in the web UI; changes are saved on the phone first and synced with sets. **Conflict resolution** (R5): append-only for new exercises and payments with content matching for exactly-once; rename/delete apply only if the drills row still has the values the app saw, otherwise the sheet wins and a notice is shown; a delete is refused at sync if the log uses the name. The balance stays computed by the sheet's script only | PASS |
| IV. Maximize Use of Google Services | Sheets API only (`findReplace`, `appendCells`, `updateCells`, `deleteDimension`, one GET); same token and scope | PASS |
| Technology Constraints | No new dependency; Room auto-migration within the chosen library | PASS |
| Workflow & Quality Gates | Spec Kit flow; this table | PASS |

**Post-design re-check (after Phase 1)**: PASS. Every new write path is in the single atomic
`batchUpdate`. Every conflict ends in "sheet wins" plus a notice, or in "already done". Money
reads are isolated from the set sync.

## Project Structure

### Documentation (this feature)

```text
specs/004-bottom-tab-navigation/
├── plan.md              # This file
├── research.md          # Phase 0: R1–R11
├── data-model.md        # Phase 1: new tables, coalescing, display model, sync transitions
├── quickstart.md        # Phase 1: automated and device validation
├── contracts/
│   ├── screens.md       # Bottom bar, tabs, screens, dialogs, test tags
│   └── sheets.md        # Calls 20–22 changes, new call 23
├── checklists/requirements.md
└── tasks.md             # Phase 2 (/speckit-tasks)
```

### Source Code (repository root)

```text
app/schemas/…/1.json, 2.json              # commit v1 (untracked today) + exported v2
app/src/main/java/com/workoutrec/
├── MainActivity.kt                       # NavHost → tab graphs + Scaffold with the bar
├── nav/                                  # NEW
│   ├── Tabs.kt                           # Tab enum, routes, icons, labels
│   ├── BottomBar.kt                      # NavigationBar, reselect, visibility rule
│   └── TabGraphs.kt                      # today/history/exercises/money nested graphs
├── home/MainScreen.kt                    # Today without the Workouts / past workout links
├── log/                                  # past days → History start screen (+ top bar action)
├── exercises/                            # NEW
│   ├── ExerciseRules.kt                  # name rules, duplicates, deletable (JVM)
│   ├── ExercisesScreen.kt, ExerciseDetailScreen.kt, ExerciseDialog.kt
│   └── ExercisesViewModel.kt
├── money/                                # NEW
│   ├── PaymentValues.kt, BalanceNote.kt  # validation, note rules (JVM)
│   ├── MoneyScreen.kt, PaymentDialog.kt, MoneyViewModel.kt
│   └── MoneyRefresher.kt                 # call 23 policy (open / pull / app start / after payment)
├── workout/
│   ├── ExerciseCoalescer.kt              # data-model coalescing (JVM)
│   ├── DisplayModel.kt                   # + exercise list, name mapping, payments, waiting count
│   └── data/                             # + 4 entities, DAO methods, version 2, auto-migration
├── google/SheetsLogClient.kt             # calls 20–22 changes; MoneyClient.kt call 23
├── sync/LogSync.kt                       # + exercise and payment planning (R5)
└── res/drawable/ic_tab_*.xml, values{,-ru}/strings.xml
app/src/test/…/{exercises,money,workout,sync,google}/   # JVM tests
app/src/androidTest/…/{nav,exercises,money,workout/data}/ # UI, migration, DAO tests
app/src/debug/…/gallery/ScreenGallery.kt  # + tab shots
```

**Structure Decision**: Same single `app` module and layering as 002. Rules, coalescing,
planning and parsing are plain Kotlin with JVM tests. Room, navigation and Compose are thin
layers tested on the CI emulator.

## Implementation order and risks

1. Commit schema v1; Room v2 auto-migration and the migration test.
2. Bottom bar and tab graphs with existing screens: Today, History (US1, US2). Shippable alone.
3. Exercises: rules, coalescing, display, screens (US3); sync planning, calls 20–22 changes.
4. Money: call 23, cache, note, screen (US4); payment dialog and sync append.
5. Waiting count and strings; gallery; device quickstart.

| Risk | Impact | Mitigation |
|------|--------|------------|
| `app/schemas/1.json` never committed and differs from the shipped DB | Auto-migration fails on update | Generate it from the 0.1.152 tag's code, compare it with the current file, commit it, and add a migration test from a v1 DB (R9) |
| `findReplace` also renames a cell that equals the old name in another column | Wrong data | Range limited to log column B and rec A1; `matchEntireCell` + `matchCase` |
| Rename conflicts leave phone sets with the new name | Name not in drills | Notice to the user; log column accepts free text (R5) |
| Nested graphs change routes used by tests and the gallery | Broken tests | Keep route names; update tests in step 2 |
| Labels "Упражнения" / "Финансы" too long at the largest font | Overlap | Single-line labels with ellipsis; checked in the gallery and quickstart #12 |
| Money tab edited with odd values | Wrong totals | Non-numbers shown as "—" and not counted (R8) |

## Complexity Tracking

No constitution violations; nothing to justify.
