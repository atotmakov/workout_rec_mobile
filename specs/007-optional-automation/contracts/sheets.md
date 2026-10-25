# Contract: Sheets Calls for the Calculated Form

Changes to calls 4, 22 and 23 and new calls 24–25. Token, scope and error mapping as in
[002 sheets-log.md](../../002-log-workout/contracts/sheets-log.md) and
[006 sheets.md](../../006-workout-paid-status/contracts/sheets.md). Research: R1–R6.

## Call 4 (create): calculated from the start (FR-010)

- `balance` row 1: `{"userEnteredValue": {"formulaValue": BALANCE_FORMULA}}` instead of `0`.
  This also applies to the fallback `completionRequests`.
- Document metadata `workout_rec.structure_version = "3"`.

## Call 22: the day-rows step (R3)

The `workout` groups now come after the log, drills and money groups. All of them are in one
`batchUpdate`:

1. Status updates (same as 006): `updateCells` on C of a row.
2. Removals, in descending row order:

   ```json
   {"deleteDimension": {"range": {"sheetId": WORKOUT, "dimension": "ROWS",
     "startIndex": ROW, "endIndex": ROW + 1}}}
   ```

   `ROW` is the 0-based grid index (`rowIndex` from call 21). A removal never shifts the rows in
   group 1, because the updates come first.
3. One `appendCells`, in date order:

   ```json
   {"appendCells": {"sheetId": WORKOUT, "fields": "userEnteredValue",
     "rows": [{"values": [{"userEnteredValue": {"numberValue": SERIAL}},
                          {},
                          {"userEnteredValue": {"numberValue": 0|1}}]}]}}
   ```

   B is left empty: the form step (calls 24–25) writes the duration formula in the spreadsheet's
   locale right after (research R1 "Locale").

### What gets written

| Case | Action |
|------|--------|
| Waiting status for D: the same 006 table (update, append, done, dropped) | as in 006; an append now carries the formula |
| A date with sets after this run, no row, no waiting status | append with the default status |
| A row whose date has no set after this run, and the log read is not empty | delete |
| A waiting status for a date with no sets | none; it stays waiting (006) |
| `workout` tab missing | no `workout` groups; `WORKOUT_TAB_MISSING` when statuses are waiting (006) |

## Call 24 (new): read the calculated cells

```text
GET v4/spreadsheets/{id}?ranges=workout!A2:B&ranges=balance!A1
    &fields=properties(locale),developerMetadata(metadataId,metadataKey,metadataValue),
            sheets(properties(title),data(startRow,rowData(values(userEnteredValue,effectiveValue))))
```

- `workout` row `i` → grid row `startRow + i`. A row needs B when its A is a number. For B,
  `formulaValue` is compared with `DURATION_FORMULA` after `StructureValidator`'s normalisation;
  any other `userEnteredValue`, or one that is missing, is "not the formula". A B whose
  `effectiveValue.errorValue.type` is `ERROR` (parse error) also needs the formula; it is not
  reported as overwritten when its text matches.
- `balance` A1 is compared in the same way with `BALANCE_FORMULA`.
- The metadata gives `structure_version`, `script_version` and the automation keys (for the gate).
- Called only after a successful sync run, and only when both `workoutSheetId` and
  `balanceSheetId` are known.

## Call 25 (new): repair, and mark the conversion

Called only when call 24 found something to write. One `batchUpdate`:

- For each contiguous run of rows that need B, a `repeatCell` over B of those rows with
  `{"userEnteredValue": {"formulaValue": durationFormula(locale)}}`, `fields: "userEnteredValue"`.
  In a locale with a decimal comma, such as `ru_RU`, the `,` separators are written as `;`.
- `balance!A1` gets the formula when it needs it and the gate (R6) is open.
- `upsertMetadata(structure_version = "3")` is added when the gate is open and the version is
  below 3. It uses the existing `SheetsClient.upsertMetadata` request shape, with IDs from call 24.

Errors: a failure of call 24 or 25 is reported as `CALCULATION_CHECK_FAILED` and does not change
the outcome of the sync run.

## Call 23 (Money): unchanged request

`MoneyRead.metadata[structure_version] >= 3` → `MoneySummary.calculated = true`.
