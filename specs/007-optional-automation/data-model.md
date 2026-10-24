# Data Model: Optional Spreadsheet Automation

Feature: [spec.md](spec.md). Research: [research.md](research.md).

## Spreadsheet

| Where | Before | After |
|-------|--------|-------|
| `workout` row (data rows) | A date, B duration value (script), C "work alone" | A date, **B duration formula** (R1), C unchanged |
| `balance!A1` | number written by the nightly run | **balance formula** (R2) |
| metadata `workout_rec.structure_version` | `2` | `3` once in the calculated form (R5); new spreadsheets start at `3` |
| metadata `workout_rec.script_version` | `2` | `3` after the upgrade (R7) |

No tab, header, table or column type changes; `StructureValidator` is unchanged (it ignores data
rows and `balance`).

### Workout day row: rules

- Exists **only** for a date with at least one set in `log`, and **for every** such date (FR-006,
  FR-007a). Date = whole part of the serial (`floor`), spreadsheet local time.
- At most one row is added per date; existing duplicates are kept and all counted (spec edge case);
  the app reads statuses from the first row per date (006).
- Added with: A = date serial (whole days), B = `DURATION_FORMULA`, C = status (`1` Free, `0` Paid).
- Status of an added row: the waiting phone choice for that date, else the 006 default (latest
  earlier day's status among existing rows and rows planned in the same run; Free when none).
- Removed when its date has no set after the run's writes, unless the log read is empty (R3 guard).

### Calculated cells: states

| Cell state (call 24) | Version 3 already? | Action (call 25) | Report |
|----------------------|--------------------|------------------|--------|
| formula equal to the reference (normalised) | – | none | – |
| empty | – | write formula | – |
| other value or formula | no (converting) | write formula | – |
| other value or formula | yes | write formula | `CALCULATION_RESTORED` |
| `balance!A1`, gate R6 closed (automation On, script < 3) | no | leave; durations still repaired; no marker | – |

After the first call 25 with the balance formula: `structure_version = 3`.

## Spreadsheet form (per spreadsheet)

```text
Uncalculated (version ≤ 2) ──first form step with gate open──▶ Calculated (version 3)
        │                                                          │
        └── gate closed: durations only, retried each sync ────────┘ (checks + repairs each sync)
```

## App (Room schema 3 → 4)

| Table | Change |
|-------|--------|
| `money_summary` | new column `calculated INTEGER NOT NULL DEFAULT 0`: call 23 metadata `structure_version >= 3` |

Auto-migration `AutoMigration(3, 4)`; exported `app/schemas/…/4.json`.

No other stored data changes. `day_status` (006) keeps working: its cache now also loses dates
whose rows the run removed (next read).

### Kotlin types

| Type | Package | Purpose |
|------|---------|---------|
| `DayRowsPlan` (object) + `DayRowsResult` | `workout` | pure rule of R3: updates, deletes, appends, synced/written/dropped statuses |
| `WorkoutWrite` | `google` | gains `deletes: List<Int>`; appends carry the formula |
| `CalculatedForm` (object) | `spreadsheet` | pure: call-24 cells + version + gate → repairs, marker, report |
| `FormCheck` | `sync` | runs calls 24/25 after a successful sync, reports failures |
| `MoneySummary.calculated` | `money` | for `BalanceNote.Calculated` |
| `BalanceNote` | `money` | `Calculated`, `Daily`, `Stopped`; `Off` removed; `waiting` flag (R9) |
| `SetupAction.StartAutomation` | `setup` | renamed `FixAutomation` |
| `RefreshResult.Status(automation: AutomationStatus)` | `setup` | replaces `Reminder` |

## Validation rules from requirements

- FR-004: duration = round((last − first) × 1440) minutes; one set → 0.
- FR-003: balance = Σ numeric `money!B` − count(rows with A non-empty and C ≠ "1").
- FR-007: never append a date that has a row (fresh read + planned appends).
- FR-009: every step is idempotent; the marker is written in the same `batchUpdate` as the
  balance formula.
- FR-016: reports carry fixed keys and counts only.
