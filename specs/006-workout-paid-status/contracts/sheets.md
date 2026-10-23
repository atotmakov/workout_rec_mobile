# Contract: Sheets Calls for Day Statuses

Changes to calls 20–22 ([002 sheets-log.md](../../002-log-workout/contracts/sheets-log.md),
[004 sheets.md](../../004-bottom-tab-navigation/contracts/sheets.md)). Same token, scope and error
mapping as there. Research: [R4, R6](../research.md).

| # | Change | Request | Response used |
|---|--------|---------|---------------|
| 20 | also keeps the `workout` sheet ID | unchanged | `sheetId` of `workout` (optional) |
| 21 | reads the `workout` tab when it exists | `…values:batchGet?ranges=log!A2:D&ranges=drills!A2:B[&ranges=money!A2:C][&ranges=workout!A2:C]&…` | `workout` rows → `SheetWorkoutRow` |
| 22 | day statuses in the same `batchUpdate` | see below | success / error only |

## Call 21 parsing (`workout` range)

The value ranges come back in request order; the parser tracks which optional ranges were asked
for. Row `i` (0-based in the range) → `rowIndex = i + 1`:

- `A` must be a number (date serial, `UNFORMATTED_VALUE` + `SERIAL_NUMBER`), else the row is
  skipped; the date is `floor(serial)` days after 1899-12-30, no time zone shift.
- `B` is ignored (the script owns the duration).
- `C`: `DayStatusRules.fromSheet` (number `1` or text `"1"` → FREE, else PAID).
- Several rows with one date: the first is used, the others are ignored.

## Call 22 additions

Placed after the existing groups (log, drills, money: 004 "Call 22 request order"). The
`workout` sheet is separate, so its row indexes are not affected by log deletes.

1. Updates, any order (one cell each):

   ```json
   {"updateCells": {"start": {"sheetId": WORKOUT, "rowIndex": ROW, "columnIndex": 2},
     "rows": [{"values": [{"userEnteredValue": {"numberValue": 0|1}}]}],
     "fields": "userEnteredValue"}}
   ```

2. Appends, one `appendCells` in date order:

   ```json
   {"appendCells": {"sheetId": WORKOUT, "fields": "userEnteredValue",
     "rows": [{"values": [{"userEnteredValue": {"numberValue": SERIAL}}, {},
                          {"userEnteredValue": {"numberValue": 0|1}}]}]}}
   ```

   `SERIAL` = whole days from 1899-12-30 to the date (as for payments); column A's
   `dd.MM.yyyy` format shows it as a date; the empty cell B is left for the script (R5).

## What gets written (sync step, R4)

Checked top to bottom; the first matching row applies.

| Pending status for date D | Fresh read | Action |
|---------------------------|-----------|--------|
| the status has a `lastSeenFree` and the first `workout` row for D now holds a different value | – | none; pending dropped; notice `DAY_STATUS_DROPPED` |
| D has no set in the log and none is appended in this run | – | none; stays pending |
| first `workout` row for D has the same status | – | done, no write |
| first `workout` row for D differs | – | update C of that row |
| no `workout` row for D | – | append |
| `workout` tab missing | – | none; stays pending; `WORKOUT_TAB_MISSING` reported |

## Errors

As for the run: any failure of call 21 or 22 fails the run (002 "Errors"); pending statuses stay
and the next run tries again.
