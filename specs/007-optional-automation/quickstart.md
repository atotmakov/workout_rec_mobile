# Quickstart: Validate Optional Automation

Feature: [spec.md](spec.md). The contracts are in [contracts/](contracts/), and the rules are in
[data-model.md](data-model.md).

## Automated (local and CI)

Use the same environment as in 006: JDK 17 in `JAVA_HOME` and the SDK in `ANDROID_HOME`.

```bash
./gradlew testDebugUnitTest            # DayRowsPlan, DayRowsFixtureTest, CalculatedForm, LogSync day rows,
                                       # FormCheck, setup flow, BalanceNotes, migration 3→4
cd apps-script && node --test tests/   # workoutRowsPlan v3 + shared fixture, dailyJob v3
./gradlew connectedDebugAndroidTest    # Money entry, account menu item, no reminder on Today
```

Expected result: all tests pass. `DayRowsFixtureTest` and `logic.test.mjs` both run every case
in `apps-script/tests/fixtures/day-rows.json`.

## Device (test spreadsheets only)

Use the release build from internal testing, or a debug build. Use test Google accounts.

| # | Steps | Expected |
|---|-------|----------|
| 1 | New account, clean install, choose the account | Progress, then Today. No guide screens and no banner (SC-001, US1) |
| 2 | Open the new spreadsheet | `balance!A1` and the `workout` B cells are formulas. Metadata `structure_version` is 3 (FR-010) |
| 3 | Log 3 sets 10 minutes apart and wait for sync | One `workout` row with today's date, B = 20 and the default status. The Money balance went down by 1 if the day is Paid (US2 #1, #3) |
| 4 | **Conversion**: copy a real spreadsheet with automation on. Write down the balance and every duration. Remove one day's sets in the web UI so that one row loses them. Open it with the new version | Durations of days with sets are unchanged. The row without sets is gone. The balance differs only by that row (US3, SC-003). Metadata `structure_version` is 3 and `script_version` is 3 |
| 5 | Repeat #4 but turn on airplane mode during the first sync. Then go back online | The conversion completes. A second run changes nothing (US3 #3) |
| 6 | Type a number over a duration cell and over `balance!A1`. Sync | Both are formulas again. AppTracer shows `CALCULATION_RESTORED`, with no values (FR-011) |
| 7 | In the web UI, add a `workout` row for a date without sets. Sync | The row is removed (FR-007a) |
| 8 | In the web UI, type sets for a new date. Sync | Its row is added with the default status (US2 #5) |
| 9 | Delete all sets of one day in the app. Sync | That day's row is removed and the balance goes up by 1 if it was Paid |
| 10 | Airplane mode. Log a set and add a payment. Open Money | The last balance is shown with "Updates after your changes sync" (FR-005a) |
| 11 | Money → Spreadsheet automation (Off). Complete both steps | The entry says On. The account menu item says On (US4) |
| 12 | With #11 on, type sets for a new date in the web UI, keep the app closed overnight | The next morning the row exists, with formula B. `balance!A1` is still a formula (US4 #3, #4) |
| 13 | An old v2 spreadsheet with automation on, and the Apps Script API setting turned off afterwards. Open the app | Durations become formulas. The balance stays a value, the Money note is the daily one, and there is no `CALCULATION_RESTORED` (gate R6) |

## Done when

- Every automated suite is green in CI.
- #1–#12 pass on the test phone. #13 passes, or it is recorded as a known limitation.
