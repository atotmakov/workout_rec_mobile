# Contract: Reading and Writing the Log (Sheets API v4)

All calls use the access token from `ApiAuthorizer` (scope `drive.file`, already granted by
feature 001) and the existing `GoogleHttp` error mapping (`ApiError`, feature 001
[google-apis.md](../../001-google-account-setup/contracts/google-apis.md)). Base URL
`https://sheets.googleapis.com/v4/spreadsheets/{id}`. Numbering continues feature 001's calls.

| # | Purpose | Request | Response used |
|---|---------|---------|---------------|
| 20 | Time zone and log sheet ID | `GET {id}?fields=properties.timeZone,sheets.properties(sheetId,title)` | `properties.timeZone`; `sheetId` of the sheet titled `log` |
| 21 | Download log and exercises (R7) | `GET {id}/values:batchGet?ranges=log!A2:D&ranges=drills!A2:B&valueRenderOption=UNFORMATTED_VALUE&dateTimeRenderOption=SERIAL_NUMBER&majorDimension=ROWS` | `valueRanges[0].values` (log rows, index *i* → `rowIndex = i + 1`), `valueRanges[1].values` (drills) |
| 22 | Write changes (R4, R6) | `POST {id}:batchUpdate` with the requests below, in this order | success / error only |

Call 20 runs once per spreadsheet ID (cached in `sync.timeZone` and with the sheet ID).
Missing tab `log` or `drills` in call 20 → sync failure `Structure` (not an `ApiError`): sync
stops with `Failing(structure)`, and the main screen offers to re-run the spreadsheet check from
feature 001.

## Call 22 request order

1. For each pending `DELETE` and `EDIT`, ordered by **descending** target row index:
   - `DELETE`:

     ```json
     {"deleteDimension": {"range": {"sheetId": LOG, "dimension": "ROWS",
       "startIndex": ROW, "endIndex": ROW + 1}}}
     ```

   - `EDIT` (columns B–D; the date-time is never written by an edit):

     ```json
     {"updateCells": {"start": {"sheetId": LOG, "rowIndex": ROW, "columnIndex": 1},
       "rows": [{"values": [
         {"userEnteredValue": {"stringValue": "EXERCISE"}},
         {"userEnteredValue": {"numberValue": WEIGHT}},
         {"userEnteredValue": {"numberValue": REPS}}]}],
       "fields": "userEnteredValue"}}
     ```

2. One `appendCells` with all pending `NEW` sets (no `draftId`) in `createdAt` order:

   ```json
   {"appendCells": {"sheetId": LOG, "fields": "userEnteredValue", "rows": [
     {"values": [
       {"userEnteredValue": {"numberValue": SERIAL}},
       {"userEnteredValue": {"stringValue": "EXERCISE"}},
       {"userEnteredValue": {"numberValue": WEIGHT}},
       {"userEnteredValue": {"numberValue": REPS}}]}]}}
   ```

`SERIAL` = (local date-time in the spreadsheet time zone − 1899-12-30T00:00) in days, from whole
seconds (R8). No formats are written; the column formats and table types of the reference
structure apply. Requests whose list is empty are omitted; when nothing is pending the call is
skipped.

## Sync algorithm (one run, under the sync mutex)

1. Authorize (R3). Consent needed → `NeedsSignIn`, stop.
2. Call 20 if the time zone or sheet ID for this spreadsheet is not cached.
3. Call 21 → parse rows into `SetKey`s with row indexes.
4. Match pending changes (R5):
   - `NEW` whose key is in the log → mark done (already written).
   - `EDIT`/`DELETE` → find a row with key = `lastSeen`; several → closest to `rowHint`; none →
     conflict: drop + `sync_notice`.
5. If anything is left, call 22. On success call 21 again.
6. In one transaction: replace `exercise` and `log_row` caches with the last read, delete the
   pending changes handled in 4–5, add notices, set `sync.lastSuccessAt`, state `Idle`.

## Errors

| Failure (feature 001 `ApiError` mapping) | Sync state | Retry |
|------------------------------------------|------------|-------|
| `Offline`, `ServiceUnavailable` (429, 5xx) | `Failing(network)` | WorkManager backoff |
| `TokenExpired` | — | re-authorize once and repeat the call; then as `AccessDenied` |
| `AccessDenied`, or consent required (R3) | `NeedsSignIn` | after the user opens the app |
| `NotFound` (spreadsheet trashed or gone) | `Failing(spreadsheet)` | after feature 001's spreadsheet check runs again (FR-017) |
| `Structure` (no `log`/`drills` tab, call 20) | `Failing(structure)` | after the spreadsheet check |
| `Unexpected` (other 4xx) | `Failing(other)` | next trigger; pending changes are kept |

Pending changes are never deleted because of an error; only steps 4 and 6 remove them.
