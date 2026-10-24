# Contract: Attached Script, Version 3

This contract describes changes to [006 apps-script.md](../../006-workout-paid-status/contracts/apps-script.md).
Research: R4 and R7. The upgrade mechanism is unchanged; `upgrade` runs when
`script_version < 3`.

## Files

| File | Change |
|------|--------|
| `Config.gs` (generated) | `const SCRIPT_VERSION = 3;` and `const DURATION_FORMULA = "…";`, the app's `ReferenceStructure.DURATION_FORMULA` |
| `Logic.gs` | `workoutRowsPlan` returns appends, removals and formula rows; `balance` is kept for the fallback |
| `Code.gs` | `dailyJob` as below; `ENABLE_TEXTS` no longer mentions the balance |
| `Enable.html`, `appsscript.json` | unchanged |
| `tests/fixtures/day-rows.json` | new; the shared cases (R4) |

## `workoutRowsPlan(logRows, workoutRows, toDayKey)`

The function is pure. `workoutRows` are the data rows `[date, duration, flag]`, starting at row 2.
It returns:

- `appends: [[date, flag]]`: for days with sets and no row, in date order. The flag rule is the
  same as in v2. `date` is the day's first set, as in v2.
- `removals: [row]`: sheet row numbers (header = 1), in descending order, for rows whose A is a
  Date with no log day. The list is empty when `logRows` has no Date at all (the empty-log guard).
- `formulaRows: [row]`: rows whose A is a Date and whose B is `''`, excluding the removals. Their
  row numbers are the ones after the removals are applied.

## `dailyJob()`

1. Plan from `log` and `workout`.
2. `deleteRow(row)` for each removal, in descending order.
3. `setFormula(DURATION_FORMULA)` on `B<row>` for each formula row.
4. `appendRow([date, DURATION_FORMULA, flag])` for each append.
5. If `balance!A1.getFormula() === ''` (the app has not converted the spreadsheet yet), write
   `balance(...)` as in v2. Otherwise leave the cell alone.
6. Write `workout_rec.last_daily_run`.

The job never writes a duration value. It never writes the balance into a calculated
spreadsheet (FR-014).

## Shared cases: `day-rows.json`

```json
[{ "name": "…",
   "logDays": ["2026-10-01", "2026-10-03"],
   "workoutRows": [{ "date": "2026-10-01", "free": false }, { "date": "2026-10-02", "free": true }],
   "expected": { "appends": [{ "date": "2026-10-03", "free": false }], "removals": ["2026-10-02"] } }]
```

The set must cover at least these cases:

- the first day ever, which is Free;
- the default copied from the latest earlier row;
- a planned append feeding the next append;
- a row with no log day, which is removed;
- an empty log, which removes nothing;
- a duplicate row for one date: nothing is added, and both rows are kept while the date has sets;
- a date-time in A from an old script append.

`logic.test.mjs` maps each case to `workoutRowsPlan`. `DayRowsFixtureTest` (JVM) maps each case to
`DayRowsPlan`.
