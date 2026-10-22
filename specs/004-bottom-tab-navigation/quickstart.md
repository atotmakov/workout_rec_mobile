# Quickstart: Validating Bottom Tab Navigation

How to prove the feature works. Contracts: [screens.md](contracts/screens.md),
[sheets.md](contracts/sheets.md); data: [data-model.md](data-model.md).

## Prerequisites

- Local toolchain and emulator as for features 001–002 (README "Build"); a test account with a
  set-up spreadsheet, automation enabled, a few drills, some logged days, and two rows in the money
  tab.
- CI green on the branch (unit tests, lint, instrumented tests on the CI emulator).

## Automated

```sh
./gradlew testDebugUnitTest                      # rules, coalescing, sync planning, calls 21–23, balance note
./gradlew connectedDebugAndroidTest              # migration 1→2, DAOs, bar and tab screens
./gradlew testDebugUnitTest --tests '*ScreenGalleryTest*' -Proborazzi.test.record=true
```

Expected: all pass; the gallery in `app/build/outputs/gallery/{light,dark,dark_ru}` has the four
tabs, exercise detail and dialogs, Money with positive, negative and "automation off" balance.

## Device scenarios

| # | Steps | Expected |
|---|-------|----------|
| 1 | Install over the previous build with unsynced sets; open | Sets still there (migration); Today selected; no "Workouts" link |
| 2 | History → a day → switch to Money → back to History | Same day open, same scroll (FR-003); each switch shows the content within 0.5 s, also in airplane mode (screen recording, SC-002) |
| 3 | In History's day, tap History again; then Back from History | Past days list; then Today; Back again closes the app |
| 4 | Today → Add exercise → plan → logging | Bar hidden on logging, back on leaving |
| 5 | Exercises → Add "Face pull" / "Shoulders" offline; log one set of it on Today; go online | Exercise and set appear at once; within 1 min a drills row and a log row (SC-004) |
| 6 | Exercises → an exercise with history → Rename; check History and the record | New name everywhere at once; after sync drills row, all its log rows and rec!A1 (if chosen) renamed (SC-007) |
| 7 | Rename an exercise in the web UI, then rename the same one in the app before syncing | Notice "was changed in the spreadsheet"; web name kept |
| 8 | Exercise without sets → Delete; exercise with sets | Deleted (drills row gone after sync); the other has no Delete, shows the note |
| 9 | Money: compare with the spreadsheet | Balance = balance!A1; payments and totals match (SC-005); note shows last calculation time |
| 10 | Add payment offline; go online | Shown at once as waiting, counted in the sync icon; money row within 1 min (SC-008); balance unchanged, note says it is counted at the next calculation |
| 11 | Disable the script trigger (or use a spreadsheet without automation); open Money | Note says the balance is not updated, with "Enable automation" |
| 12 | Largest font size, Russian, 360 dp | Bar labels fit, no overlap (SC-006) |
