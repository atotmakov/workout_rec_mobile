# Contract: Sheets Calls for Exercises and Money

Changes to feature 002's calls 20–22 ([sheets-log.md](../../002-log-workout/contracts/sheets-log.md))
and the new call 23. Same token, scope (`drive.file`) and error mapping as there.

| # | Change | Request | Response used |
|---|--------|---------|---------------|
| 20 | also keeps sheet IDs of `drills`, `rec`, `money` and `balance` | unchanged | `sheetId` of `log`, `drills` (required), `rec`, `money`, `balance` (optional) |
| 21 | drills keep row indexes; money read only when payments are pending and the tab exists | `…values:batchGet?ranges=log!A2:D&ranges=drills!A2:B[&ranges=money!A2:C]&…` (rest as in 002) | `valueRanges[1]` rows → `SheetDrill(rowIndex, group, name)`; `valueRanges[2]` → payment keys for research R5 |
| 22 | more requests in the same `batchUpdate` | see "Call 22 request order" | success / error only |
| 23 | NEW: money, balance, metadata | `GET {id}?ranges=money!A2:C&ranges=balance!A1&includeGridData=true&fields=developerMetadata(metadataKey,metadataValue),sheets(properties(title),data(rowData(values(effectiveValue))))` | payment rows, balance value, metadata map |

## Call 22 request order

All lists may be empty; empty groups are omitted; nothing pending → no call.

1. Log: `deleteDimension` / `updateCells` for set deletes and edits, by descending row (as in 002).
2. Drills, by descending row:
   - delete: `deleteDimension` of the drills row (`sheetId = DRILLS`);
   - rename: `updateCells` at `{rowIndex: ROW, columnIndex: 0}`, values `[group, name]` as
     `stringValue`, `fields: "userEnteredValue"`.
3. For each applied rename, two requests:

   ```json
   {"findReplace": {"find": "OLD", "replacement": "NEW", "matchCase": true,
     "matchEntireCell": true, "searchByRegex": false, "includeFormulas": false,
     "range": {"sheetId": LOG, "startRowIndex": 1, "startColumnIndex": 1, "endColumnIndex": 2}}}
   ```

   and the same with `"range": {"sheetId": REC, "startRowIndex": 0, "endRowIndex": 1,
   "startColumnIndex": 0, "endColumnIndex": 1}` (`REC` from call 20's sheet list; skipped when
   the rec tab is missing).
4. Appends, each one `appendCells` with `fields: "userEnteredValue"`:
   - log: new sets (as in 002);
   - drills: new exercises in `createdAt` order, values `[group, name]` as `stringValue`;
   - money: new payments in `createdAt` order, values `[SERIAL, workouts, sum]` as
     `numberValue`; `SERIAL` = days from 1899-12-30 to the payment date (whole number).

Renames are applied in `createdAt` order, so a chain A → B → C coalesced into one RENAME
(data-model) is a single A → C replace.

## Call 23 parsing

- A range naming a missing tab fails the whole request (HTTP 400), so call 23 asks only for the
  tabs that call 20 found; a missing `money` or `balance` tab → result `MissingTab(tab)` without
  reading it (the Money tab shows it; the set sync is not affected). `sheets[]` are matched by
  `properties.title`.
- money `rowData[i]` → `rowIndex = i + 1`; a row is a payment when `values[0].effectiveValue
  .numberValue` exists (a date serial, converted to a local date with no time zone shift);
  `values[1]`, `values[2]` numbers or null.
- balance: `rowData[0].values[0].effectiveValue.numberValue`, else null.
- metadata: `developerMetadata[]` → map `metadataKey → metadataValue`; evaluated with
  `AutomationStatusEvaluator` (feature 001) and `workout_rec.last_daily_run` parsed as ISO-8601.

## Errors

Calls 20–22 keep 002's table. Call 23: `Offline` / `ServiceUnavailable` → `NETWORK`;
`AccessDenied` / consent → shown as the sync icon's sign-in state (same token); `NotFound` →
`OTHER`; other errors → `OTHER`. The cached values stay and are shown with their download time.
