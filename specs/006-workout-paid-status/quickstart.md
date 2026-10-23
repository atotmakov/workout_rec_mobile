# Quickstart: Validate Paid or Free Workouts

## Automated (every PR, CI)

```bash
./gradlew :app:testDebugUnitTest          # rules, sync, client, repository, upgrade, migration
./gradlew :app:lintDebug
./gradlew :app:connectedDebugAndroidTest  # Today, History, day details, past-workout dialog
node --test "apps-script/tests/*.test.mjs"  # workoutRowsPlan and dailyJob
```

| # | Check | Expected |
|---|-------|----------|
| 1 | `DayStatusRulesTest` | default = latest earlier date's status; none → Free; `1`/`"1"` Free, anything else Paid |
| 2 | `LogSyncDayStatusTest` | append when no row, update C when different, nothing when equal; no sets → stays pending; missing tab → reported, sets still synced |
| 3 | Script tests | appends use the previous flag (first Free); only empty durations are filled; app flags kept |
| 4 | `ScriptUpgradeTest` | version missing or 1 → upload + metadata 2; version 2 → nothing; failure reported and retried next start |
| 5 | UI tests | each control shows the shown status and saves on tap; History label and waiting mark |

## Device (Play internal build from this branch's merge; a test copy of the spreadsheet)

| # | Scenario | Expected |
|---|----------|----------|
| 6 | Update the app, open it online once | `workout_rec.script_version = 2` (script editor → `logMetadata`); script files show `workoutRowsPlan` |
| 7 | Yesterday Paid in the `workout` tab; open Today | Today shows Paid. Log a set → within 1 minute a row for today: date, empty duration, `0` |
| 8 | Tap Free on Today | today's row C becomes `1` within 1 minute; History list unaffected for past days |
| 9 | Next morning (after 03:00) | today's row has a duration; balance = payments − Paid days (SC-005) |
| 10 | Airplane mode, tap Paid on Today | waiting count +1; online again → C becomes `0` |
| 11 | In the web UI set yesterday's C to `1`; pull to refresh; open History | yesterday shows Free |
| 12 | History → a past day → Free | that day's row C = `1` (row added if missing) |
| 13 | Enter a past workout for a date with no workout, choose Paid, add sets, save | a row for that date with `0`; History shows Paid |
| 14 | Type sets for a new date in the web UI only; wait for the nightly run | the new row's C copies the previous day's flag (not always `0`) |
| 15 | Fresh spreadsheet, first workout | Today shows Free |
