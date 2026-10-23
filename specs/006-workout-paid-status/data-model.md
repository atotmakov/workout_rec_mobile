# Data Model: Paid or Free Workouts

Changes to the phone database (feature 002 data-model, feature 004 schema 2) and to the
spreadsheet's use of the `workout` tab. Research references: [research.md](research.md).

## Domain

```text
enum DayStatus { PAID, FREE }

/** A date's status as the app knows it; pending = chosen on the phone, not in the sheet yet. */
data class KnownStatus(val date: LocalDate, val status: DayStatus, val pending: Boolean)

/** A `workout` row as read by call 21 (0-based row index; row 2 → 1). */
data class SheetWorkoutRow(val rowIndex: Int, val date: LocalDate, val status: DayStatus)
```

`DayStatusRules` (pure, R3):

| Function | Rule |
|----------|------|
| `fromSheet(cell)` | `1` or `"1"` → FREE; anything else (0, empty, text) → PAID |
| `toSheet(status)` | FREE → `1`, PAID → `0` |
| `defaultFor(date, known)` | status of the latest `known` date before `date`; none → FREE |
| `shown(date, known)` | `known[date]` if present, else `defaultFor(date, known)` |

## Room schema 3 (`AutoMigration(2, 3)`, R9)

### New table `day_status`

| Column | Type | Rule |
|--------|------|------|
| `date` | TEXT, primary key | ISO `yyyy-MM-dd` |
| `free` | INTEGER (0/1) | the status |
| `pending` | INTEGER (0/1) | 1 = chosen on the phone and not written yet |
| `updatedAt` | INTEGER | epoch millis of the last change, for ordering only |
| `lastSeenFree` | INTEGER, nullable | pending rows: the spreadsheet's value for this date when the phone change was made (null = no row then); used by the conflict rule (research R4) |

Writes:

| Event | Effect |
|-------|--------|
| User changes a date's status (Today, day details, past-workout dialog save) | upsert `(date, free, pending = 1, lastSeenFree = cached value)`; a date that is already pending keeps its first `lastSeenFree` |
| First set of a date confirmed on the phone (Today logging, past workout save), date has no row | insert `(date, default, pending = 1, lastSeenFree = null)` |
| Sync wrote a pending status | that row → `pending = 0` (same transaction as the other commits) |
| Sync found the spreadsheet value changed since `lastSeenFree` | the pending row is dropped (the cache then holds the sheet's value); notice `DAY_STATUS_DROPPED` |
| Sync read the `workout` tab | delete all `pending = 0` rows, insert one per first row of each date, skipping dates with a `pending = 1` row |
| Spreadsheet rewritten (002 R13, `keepOnlyNewSets`) | delete `pending = 0` rows |
| Sign out / switch account (`clearAll`) | delete all |

### `past_workout_draft`: new column

| Column | Type | Rule |
|--------|------|------|
| `free` | INTEGER, nullable | the dialog's choice; null in drafts saved before schema 3 (then the default is used at save) |

## Waiting count (spec FR-009)

`waitingCount` adds the `pending = 1` statuses whose date has at least one set on the phone
(cached log rows or pending new sets): those sync can send (R4).

## Sync status cache

`SheetInfoCache` gains `workoutSheetId: Int?` and `version: Int` (2). A cache with an older
version makes the next run read call 20 again (R6).

## Spreadsheet

No structure change (`contracts/spreadsheet.md` of feature 001 stays at structure version 2).

| Where | What changes |
|-------|--------------|
| `workout!C` | the app writes `0`/`1` for dates it has a status for |
| `workout` rows | the app appends `[date serial, empty, 0/1]` for a date with sets and no row |
| `workout!B` | the script (v2) fills empty durations of rows whose day is in the log |
| developer metadata | new key `workout_rec.script_version` (app, after every script upload): `2` |

## State of one date's status

```text
            (no row) ── first set / user choice ──▶ pending
                ▲                                     │ sync writes
  sheet read    │                                     ▼
  (no row for   └──── cached (pending = 0) ◀──── written
   this date)          │   ▲
                       │   └── sheet read (value may change in the web UI)
                       └── user choice ──▶ pending
```
