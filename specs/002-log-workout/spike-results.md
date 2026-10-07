# Spike Results: Writing Rows with `appendCells` (research R4)

**Date**: 2026-10-06 · **Account**: owner's account · **Tool**: Google OAuth 2.0 Playground
(scope `https://www.googleapis.com/auth/spreadsheets`, granted by the owner for this test; the
Playground's access expires after 7 days)

**Target**: "Copy of workout_rec_database_" — a copy (Drive → Make a copy) of the spreadsheet
created by build 36, so it has the reference structure version 2 including the empty `log` table
(rows 1–1000, sheet ID 0). The real spreadsheet was not touched.

## Requests

1. `POST …/spreadsheets/{copy}:batchUpdate` with one `appendCells` on sheet 0, row
   `[46301.5, "SPIKE 1", 17.5, 15]`, `fields: userEnteredValue` (as in contracts/sheets-log.md
   call 22) → `200 OK`.
2. One `batchUpdate` with three requests: `appendCells` `[46301.51, "SPIKE 2", 20, 10]`;
   `updateCells` at row index 999 (sheet row 1000) `[46301.52, "SPIKE row 1000", 1, 1]`;
   `appendCells` `[46301.53, "SPIKE 3", 30, 5]` → `200 OK`.
3. `GET …/spreadsheets/{copy}?ranges=log!B1:B1005&fields=sheets(tables(name,range),data(…formattedValue))`
   to read back where the rows landed.

## Results

| Case (quickstart "Spike") | Result |
|---------------------------|--------|
| 1. Append to the empty log table | **Pass** — row 2 (`SPIKE 1`), inside the table; the Date cell shows as a date |
| 2. Append after filled rows | **Pass** — row 3 (`SPIKE 2`), right after the last filled row; empty table rows below are not "data" |
| 3. Append when row 1000 (the table's last row) is filled | **Pass** — row 1001 (`SPIKE 3`); the log table grew to `endRowIndex 1001`, so the row is still part of the table |

**Decision**: keep research R4 as written (`appendCells` with `sheetId`); the `updateCells`
fallback is not needed. Case 3 also settles the spec edge case "Log tab table full": the table
extends itself.

## Clean-up for the owner

- The copy "Copy of workout_rec_database_" can be deleted from Drive.
- The Playground's access can be removed now at Google Account → Security → Third-party apps
  ("Google OAuth 2.0 Playground"); otherwise it expires after 7 days.
