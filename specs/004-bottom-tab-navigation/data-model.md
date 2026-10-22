# Data Model: Bottom Tab Navigation

Additions to feature 002's Room database `workout.db`
([002 data-model](../002-log-workout/data-model.md)); schema version 1 → 2 by auto-migration
(research R9). Existing tables are unchanged.

## Value types

| Type | Definition | Rules |
|------|------------|-------|
| `ExerciseName` | text | trimmed; 1–100 characters; compared case-insensitively for duplicates (FR-008) |
| `MuscleGroup` | text | trimmed; 0–50 characters; empty → shown under "Other" (as in 002) |
| `PaymentDate` | local date | not after today in the spreadsheet time zone; default today (R8) |
| `PaidWorkouts` | whole number | 1–999 |
| `PaymentSum` | decimal, scale 2 | 0–9,999,999.99; stored as hundredths |

## New tables

### `pending_exercise_change` (FR-008, FR-008a, FR-008b)

| Field | Type | Notes |
|-------|------|-------|
| `id` | text (UUID), PK | |
| `kind` | `ADD` \| `RENAME` \| `DELETE` | |
| `name`, `muscleGroup` | text | ADD/RENAME: the new values; DELETE: unused |
| `lastSeenName`, `lastSeenGroup` | text, nullable | RENAME/DELETE: the drills values when the user acted |
| `createdAt` | epoch millis | write order |

Coalescing (research R3), in one transaction:

| Existing pending for the exercise | Action | Result |
|-----------------------------------|--------|--------|
| none | add | `ADD` |
| none | rename | `RENAME` with `lastSeen` = cached values |
| none | delete | `DELETE` with `lastSeen` = cached values |
| `ADD` | rename | same `ADD`, values replaced |
| `ADD` | delete | removed |
| `RENAME` | rename | same `RENAME`, new values replaced, `lastSeen` kept |
| `RENAME` | delete | `DELETE` with the same `lastSeen` |

A rename also rewrites, in the same transaction, the `exercise` of pending set changes (`NEW`,
`EDIT`) that use the old name.

### `pending_payment` (FR-009b)

| Field | Type | Notes |
|-------|------|-------|
| `id` | text (UUID), PK | |
| `date` | ISO `yyyy-MM-dd` | `PaymentDate` |
| `workouts` | int | `PaidWorkouts` |
| `sumHundredths` | long | `PaymentSum` |
| `createdAt` | epoch millis | write order |

Payments cannot be edited or deleted in the app (spec assumption).

### `payment_row` — cache of the money tab (FR-009)

| Field | Type | Notes |
|-------|------|-------|
| `rowIndex` | int, PK | 0-based sheet row (row 2 → 1) |
| `date` | ISO `yyyy-MM-dd` | column A as a date serial; rows without a date are skipped |
| `workouts` | double, nullable | column B; null when not a number |
| `sumHundredths` | long, nullable | column C; null when not a number |

Replaced as a whole by each call 23.

### `money_summary` — one row (FR-009, FR-009a)

| Field | Type | Notes |
|-------|------|-------|
| `id` | int, PK = 1 | single row |
| `balance` | double, nullable | `balance!A1`; null when empty or not a number |
| `lastDailyRun` | epoch millis, nullable | metadata `workout_rec.last_daily_run` |
| `automation` | `On` \| `Stopped` \| `NotEnabled` \| `ScriptMissing` | feature 001 `AutomationStatusEvaluator` |
| `downloadedAt` | epoch millis | when call 23 succeeded |
| `error` | `NONE` \| `MISSING_TAB` \| `NETWORK` \| `OTHER` | last call 23 result, for the Money tab's message |

## Changes to existing data

- `sync_notice.kind` gets two values: `EXERCISE_CHANGE_DROPPED`, `EXERCISE_IN_USE` (research R5);
  for these, `exercise` holds the name and the set fields are 0.
- `SheetInfoCache` (DataStore, 002) also stores the sheet IDs of `drills`, `rec`, `money` and `balance`
  (`recSheetId`, `moneySheetId`, `balanceSheetId` nullable: those tabs may be missing).

## Display model additions (computed, not stored)

- **Exercise list** = `exercise` cache with pending exercise changes applied: ADD appended to its
  group, RENAME replaces name/group, DELETE hides. Each shown exercise has `pending: Boolean`
  and `deletable: Boolean` (no displayed set uses its name, FR-008b).
- **Name mapping**: while a `RENAME old → new` is pending, cached log rows named `old` are shown
  as `new` (history, day detail, last time, record).
- **Payments** = `payment_row` + `pending_payment` (marked waiting), newest date first; same date:
  pending first, then higher row index first.
- **Total paid** = sum of numeric `sumHundredths` and of numeric `workouts` over all shown
  payments.
- **Balance note** (research R7) = f(`automation`, `lastDailyRun`, any pending payment or payment
  row newer than `lastDailyRun` by date).
- **Waiting count** for the sync icon (research R10) = pending set changes (without `draftId`) +
  pending exercise changes + pending payments.

## Sync run — new transitions

```text
ADD ──(name in drills)───────────────────────────────▶ removed
ADD ──(appended)─────────────────────────────────────▶ removed; exercise cache refreshed
RENAME ──(old row found, batch succeeded)────────────▶ removed; log + rec!A1 renamed
RENAME ──(old gone, new present)─────────────────────▶ removed
RENAME ──(neither)───────────────────────────────────▶ removed + waiting sets renamed back + notice EXERCISE_CHANGE_DROPPED
DELETE ──(row found, no log row uses it, succeeded)──▶ removed
DELETE ──(log rows use it)───────────────────────────▶ removed + notice EXERCISE_IN_USE
DELETE ──(no row)────────────────────────────────────▶ removed
payment ──(already in money, R5)─────────────────────▶ removed
payment ──(appended)─────────────────────────────────▶ removed; call 23 scheduled
payment ──(money tab missing)────────────────────────▶ kept; Money tab shows "money tab not found"
any ──(network / auth errors)────────────────────────▶ unchanged (as in 002)
spreadsheet ID changed ──────────────────────────────▶ RENAME/DELETE cleared; ADD and payments kept (as 002-R13 keeps new sets)
```

As in 002, pending rows are removed only in the transaction that stores the fresh caches read
after the successful write.
