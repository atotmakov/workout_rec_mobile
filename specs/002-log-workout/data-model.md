# Data Model: Log New Workout

Local data on the phone (Room database `workout.db`, R1) and how it maps to the spreadsheet.
The spreadsheet log tab stays the source of truth for synced sets (FR-015).

## Value types

| Type | Definition | Rules |
|------|------------|-------|
| `Weight` | decimal, scale 2 | 0 ≤ w ≤ 999.99; parsed from "17,5" or "17.5"; stepper ±0.5, clamped at 0 (FR-004) |
| `Reps` | whole number | 1 ≤ r ≤ 999; stepper ±1, clamped at 1 (FR-004) |
| `SetTime` | epoch seconds | converted to/from sheet serial in the spreadsheet time zone, rounded to the second (R8) |
| `SetKey` | (`SetTime`, exercise name, `Weight`, `Reps`) | identity of a row in the log tab (R5) |
| `WorkoutDay` | local date in the spreadsheet time zone | all sets with that date form one workout |

## Tables

### `exercise` — cache of the drills tab (FR-001, FR-002)

| Field | Type | Notes |
|-------|------|-------|
| `name` | text, PK | drills column B, trimmed; empty names skipped; duplicates keep the first |
| `muscleGroup` | text | drills column A; empty → shown under "Other" |
| `sheetOrder` | int | row order in drills |

Replaced as a whole on each refresh (R7). Recent order is derived from the display model (last
set time per exercise), not stored.

### `log_row` — cache of the log tab (FR-011, FR-013, R7)

| Field | Type | Notes |
|-------|------|-------|
| `rowIndex` | int, PK | 0-based sheet row index at download time (row 2 → 1) |
| `time` | `SetTime` | column A |
| `exercise` | text | column B |
| `weight` | `Weight` | column C; blank → 0 |
| `reps` | `Reps` | column D |

Rows with empty A or B, or a non-numeric C/D, are kept out of the cache (they are not sets) but do
not stop the download. Replaced as a whole in one transaction on each refresh.

### `pending_change` — the app's changes not in the sheet yet (FR-005, FR-008, FR-013a)

| Field | Type | Notes |
|-------|------|-------|
| `id` | text (UUID), PK | |
| `kind` | `NEW` \| `EDIT` \| `DELETE` | |
| `time` | `SetTime` | NEW: the set time; EDIT/DELETE: from `lastSeen` (time is never edited) |
| `exercise`, `weight`, `reps` | | NEW/EDIT: the values to write; DELETE: unused |
| `lastSeen` | `SetKey`, nullable | EDIT/DELETE: the row's key when the user changed it |
| `rowHint` | int, nullable | EDIT/DELETE: `log_row.rowIndex` when the user changed it |
| `createdAt` | epoch millis | write order for NEW (FR-007) |
| `draftId` | text, nullable | set belongs to a past-workout draft, not synced until saved |

**Coalescing (R9)**, applied in one transaction when the user acts:

| Existing pending for the set | User action | Result |
|------------------------------|-------------|--------|
| none (synced row) | edit | `EDIT` with `lastSeen` = row key |
| none (synced row) | delete | `DELETE` with `lastSeen` = row key |
| `NEW` | edit | same `NEW`, values replaced |
| `NEW` | delete | removed |
| `EDIT` | edit | same `EDIT`, values replaced (`lastSeen` kept) |
| `EDIT` | delete | `DELETE` with the same `lastSeen` |

### `past_workout_draft` (FR-006a, US5)

| Field | Type | Notes |
|-------|------|-------|
| `id` | text, PK | |
| `date` | local date | ≤ today |
| `start` | local time | |
| `durationMinutes` | int | 1–600, default 60 |

Its sets are `pending_change` rows with `draftId`; on Save their times are computed (R10) and
`draftId` is cleared in one transaction, which makes them ordinary pending `NEW` sets.

### `sync_notice` (FR-010, FR-015)

| Field | Type | Notes |
|-------|------|-------|
| `id` | int, PK auto | |
| `kind` | `CONFLICT_DROPPED` \| `WORKOUT_ROW_NOT_UPDATED` | |
| `setKey` | `SetKey` | which set the notice is about |
| `createdAt` | epoch millis | shown until dismissed |

### Sync status (DataStore, not Room)

| Key | Values |
|-----|--------|
| `sync.state` | `Idle` \| `Running` \| `Failing(reason)` \| `NeedsSignIn` |
| `sync.lastSuccessAt` | epoch millis |
| `sync.spreadsheetId` | ID the caches belong to (R13) |
| `sync.timeZone` | spreadsheet time zone (R8) |

## Display model (computed, not stored) — R9

`DisplaySet` = `log_row` rows with pending changes applied, plus pending `NEW` without `draftId`:

| Field | Source |
|-------|--------|
| `key` | `SetKey` |
| `syncState` | `Synced` (no pending) \| `NotSynced` (pending NEW/EDIT) — pending DELETE hides the set |
| `ref` | `log_row.rowIndex` or `pending_change.id` (what an edit or delete targets) |

Derived views:

- **Today's workout** (FR-000, FR-013): `DisplaySet`s of today grouped by exercise; exercises in
  order of their first set time; sets in time order.
- **Past days** (FR-013): distinct `WorkoutDay`s before today, newest first, with exercise and
  set counts.
- **Last time** (FR-011, R11): sets of the newest day before today that has the exercise.
- **Record** (FR-011, R11): max weight; max reps among sets with that weight.
- **Pre-fill** (FR-012): planned count = last time's set count (default 3); set *i* = last
  time's set *i*, or its last set when there are fewer; marked `suggested` until changed or
  confirmed.

## Sync run — state transitions

```text
pending NEW ──(key found in sheet read)──────────────▶ removed (already written, FR-009)
pending NEW ──(appendCells succeeded)────────────────▶ removed; log_row refreshed
pending EDIT/DELETE ──(target found, batch succeeded)▶ removed
pending EDIT/DELETE ──(target not found)─────────────▶ removed + sync_notice CONFLICT_DROPPED
any ──(network / 5xx / 429)──────────────────────────▶ unchanged; sync.state = Failing; retry
any ──(consent needed)───────────────────────────────▶ unchanged; sync.state = NeedsSignIn
spreadsheet ID changed ──────────────────────────────▶ caches + EDIT/DELETE cleared, NEW kept (R13)
```

A pending change is removed only in the same transaction that stores the fresh `log_row` cache
read after a successful `batchUpdate`, so a crash in between at worst repeats the key check
(no duplicates, no loss).

**Workout-row notice**: when a synced DELETE removes the first or last set of a day before today
(changing its duration) or its only remaining set, a `WORKOUT_ROW_NOT_UPDATED` notice is added
(spec edge case). Edits never change times, so they never affect the workout row.
