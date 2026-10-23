# Contract: Attached Script, Version 2

Changes to [001 apps-script.md](../../001-google-account-setup/contracts/apps-script.md).
Research: [R5, R7](../research.md).

## Files

| File | Change |
|------|--------|
| `Config.gs` | `const SCRIPT_VERSION = 2;` |
| `Logic.gs` | `dailyWorkoutRows` → `workoutRowsPlan` (below) |
| `Code.gs` | `dailyJob` applies the plan |
| `Enable.html`, `appsscript.json` | unchanged |

## `workoutRowsPlan(logRows, workoutRows, toDayKey)`

Pure. `workoutRows` are the `workout` tab's data rows (`[date, duration, flag]`, row 2 first).

Returns `{ appends: [[firstSet, minutes, flag]], durations: [{ row, minutes }] }`:

- **Days**: log rows with a Date in column A, grouped by `toDayKey`; per day the first set's
  date-time and `minutes = round((last − first) / 60000)` (as before).
- **appends**: days without a `workout` row (any row whose A is a Date with the same key), in
  date order. `flag` = the flag of the latest earlier day among existing rows and the appends
  already planned: `1` if that flag is `1`/`"1"`, else `0`; `1` when there is no earlier day.
- **durations**: for each existing row whose A is a Date, whose B is `''`, and whose day is in the
  log: `{ row: sheet row number (header = 1), minutes }`. Rows with a duration are never changed.

## `dailyJob()`

1. Plan from the `log` and `workout` tabs.
2. `setValue(minutes)` on `B<row>` for each duration; `appendRow` for each append.
3. Balance as before (all non-empty `workout` rows), then `workout_rec.last_daily_run`.

The script still never deletes rows or tabs.

## Upgrading an attached script (app side)

| Step | Call | Note |
|------|------|------|
| 1 | `projects.updateContent(scriptId)` with all five files | same files as at install, `Config.gs` with the bound spreadsheet's ID |
| 2 | `upsertMetadata(workout_rec.script_version = 2)` | same helper as `script_id` / `enable_url` |

Runs from `SpreadsheetSetupFlow.refresh` when `workout_rec.script_id` is set and
`workout_rec.script_version` is missing or below the app's `SCRIPT_VERSION`. `install` writes
`workout_rec.script_version` too. No `createVersion` / `deployWebApp`: the web app keeps its
deployed version. Failures: `SCRIPT_UPGRADE_FAILED` (WARN) via `Monitoring.failure`; tried again
on the next app start.
