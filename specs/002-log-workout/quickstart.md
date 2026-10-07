# Quickstart: Validate "Log New Workout"

## Automated (CI)

All Android builds and tests run in GitHub Actions (no local JDK): push the branch and watch the
`ci.yml` run.

| Job | Covers |
|-----|--------|
| Build, lint, unit tests | parsing and validation (`Weight`, `Reps`), serial ↔ epoch conversion, display model and coalescing (R9), last time / record / pre-fill (R11), past-workout times (R10), sync algorithm against fake Sheets (key matching, conflicts, ordering, errors), Sheets client requests and parsing against MockWebServer ([sheets-log.md](contracts/sheets-log.md)) |
| Compose UI tests (emulator) | screens in [screens.md](contracts/screens.md); Room DAO and coalescing transactions; WorkManager worker with `work-testing` |
| Apps Script tests | unchanged (feature 001) |

## Spike (before building sync) — research R4

On a throw-away spreadsheet with the reference structure (e.g. a backup made by feature 001's
rewrite, renamed), using the Sheets API Explorer ("Try this API" on `spreadsheets.batchUpdate`):

1. Call 22 with one `appendCells` row on an empty log table → the row is row 2, inside the log
   table (it has the table colours and the Drill drop-down).
2. Add rows in the web UI, append again → lands right after the last filled row.
3. Fill to row 1000 and append → still written (row 1001).

If 1 or 2 fails, switch to the `updateCells` fallback in R4 before continuing.

## Manual on a device (latest GitHub release APK)

Prerequisite: account set up by feature 001; a few exercises in the drills tab.

| # | Scenario | Expected |
|---|----------|----------|
| 1 | Add exercise → pick → plan 3 sets → confirm each | Log tab gets 3 rows within 1 min, with the confirm times (SC-003) |
| 2 | Same exercise next day | Last time and record shown; planned count 3; values gray until changed; − / + change by 0.5 kg and 1 rep |
| 3 | Airplane mode, log 2 exercises, close app, reboot, airplane mode off, don't open the app | Rows appear within 15 min, once each, in order (SC-003, SC-004) |
| 4 | Kill the app during a sync (airplane mode toggled mid-sync) | No duplicate and no missing rows (FR-009) |
| 5 | Edit reps of a synced set, delete another | Only those rows change / disappear in the log tab |
| 6 | Edit a set in the app offline; change the same row in the web UI; go online | Web value kept; app shows a notice for that set (FR-015) |
| 7 | Past day from the web UI: open in Workouts, change a weight | The row in the log tab changes; no other row changes |
| 8 | Enter a past workout: yesterday 18:00, 60 min, 5 sets → Save | 5 rows dated yesterday, first 18:00:00, last 19:00:00; next daily run adds a 60-minute workout row (SC-006, SC-009) |
| 9 | Delete the first set of a past day that has a workout row | Notice: workout row not updated |
| 10 | Sign out with unsynced sets offline | Dialog shows how many sets will be lost |
| 11 | Empty drills tab | Picker shows the empty message and opens the drills tab |
| 12 | Russian phone language | All new screens in Russian |
| 13 | 200 sets offline (scripted with the stepper) | No blocking error; all synced later (SC-005) |

Record results in `quickstart-results.md`, as for feature 001.
