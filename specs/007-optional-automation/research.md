# Research: Optional Spreadsheet Automation

Feature: [spec.md](spec.md). Decisions R1–R10; each has the decision, why, and what else was
considered.

## R1. Duration: one formula per `workout` row, the same text in every row

**Decision**: Column B of each `workout` data row holds the same formula, which finds its own date
with `INDEX(A:A, ROW())`:

```text
=LET(d, INDEX(A:A, ROW()), IF(d = "", "",
  ROUND((MAXIFS(log!A:A, log!A:A, ">=" & INT(d), log!A:A, "<" & INT(d) + 1)
       - MINIFS(log!A:A, log!A:A, ">=" & INT(d), log!A:A, "<" & INT(d) + 1)) * 1440)))
```

(one line in the code: `ReferenceStructure.DURATION_FORMULA`). `INT(d)` makes rows the old
script added with the first set's date-time work like rows the app added with a whole date. Days
are split by the serial's whole part, which is the spreadsheet's local time, as `SheetTime` and
the script's `toDayKey` do. `ROUND(x * 1440)` matches the script's `Math.round(ms / 60000)`.

**Why**: Because the text is the same for every row, it can be written without knowing the row
number: in `appendCells` (app), in `appendRow` (script), and in one `repeatCell` over a range
(conversion). The `workout` tab is a Google Sheets table, and per-cell formulas work in tables.

**Locale (found in the device check, 2026-10-25)**: the Sheets API parses `formulaValue` text in
the spreadsheet's locale. In `ru_RU` (the locale the app creates) arguments are separated by `;`,
so the text above with `,` was stored unparsed (`#ERROR!`, "Formula parse error"), and the
balance, which reads column B, showed the same error. So:

- the app writes the formula through call 25 only, as `ReferenceStructure.durationFormula(locale)`:
  `,` becomes `;` when the locale's decimal separator is a comma. Call 24 reads `properties.locale`;
- call 22 appends leave B empty, and the form step right after the sync fills it;
- call 24 also reads `effectiveValue`, and a B whose error type is `ERROR` (parse error) is
  rewritten even when its text matches, without a `CALCULATION_RESTORED` report. This repairs
  spreadsheets converted by build 184;
- script v4 appends `[date, '', flag]` and then uses `setFormula`. **Wrong assumption, found in
  the overnight device check 2026-10-27:** `setFormula` also parses in the spreadsheet's locale, so
  in `ru_RU` the English text gave `#ERROR!` in B and in `balance!A1`. Script v5 tries the English
  text on the first cell of the run; when it reads back `#ERROR!`, it uses `;` for the rest of the
  run. It also rewrites durations that show `#ERROR!`.

The balance formula has no argument separators, so it parses in every locale.

**Alternatives**: a single `ARRAYFORMULA`/`MAP` in B2 that spills down. It would cover hand-added
rows automatically, but a spill is broken by any typed value in its range, Sheets tables may
refuse spills, and the empty spilled cells can confuse `appendCells`/`appendRow`, which append
after the last row with content. Rejected. Relative A1 references (`A5`) need the row number at
write time, which `appendCells` does not give. Rejected.

## R2. Balance: one formula in `balance!A1`

**Decision**:

```text
=SUM(money!B2:B) - SUMPRODUCT(((workout!A2:A <> "") + (workout!B2:B <> "") + (workout!C2:C <> "") > 0)
                                * (TO_TEXT(workout!C2:C) <> "1"))
```

`ReferenceStructure.BALANCE_FORMULA`. `TO_TEXT` treats a number `1` and a text `"1"` alike, as the
script's `String(flag) !== '1'` does. A row counts when any of A–C is non-empty, exactly like the
script's `r.join('') !== ''` (analysis I2), so a converted balance differs only by removed rows.

**Why**: Same rule as `Logic.gs balance` (FR-003), in the same cell, so call 23 and anything else
that reads A1 keeps working.

**Difference from the script, accepted** (spec Assumptions): `SUM` ignores text such as
`"2 pcs"`, which the script's `parseFloat` counted as 2. The `money` table's column B is typed
DOUBLE, so the app and the web UI store numbers there.

## R3. Which rows exist: the app keeps `workout` in step with `log` on every sync

**Decision**: The sync run (`LogSync`) gets a day-rows step that replaces 006's status-only plan.
It uses a new pure `DayRowsPlan` in `workout/`. The input is the `workout` rows from call 21, the
dates that have sets once this run's writes are applied (read rows, minus deletes, plus appends),
and the waiting statuses. The output goes into call 22:

- **Updates**: waiting statuses, using the 006 rules unchanged (R4 of 006).
- **Deletes**: every row whose date has no set (FR-007a). This includes days whose last set this
  run deletes.
- **Appends**: one row for each date with sets and no row. The status is the waiting one if there
  is one, otherwise the 006 default (the latest earlier day, Free when none). Column B gets the
  R1 formula.

Request order in call 22: log groups (as today), then the `workout` updates, then the `workout`
deletes in descending row order, then one `appendCells`. All of it is one atomic `batchUpdate`.

**Safety guard**: when the `log` read has no data rows at all, the step deletes nothing. An empty
log read is far more likely to be a mistake (a renamed table or a filter) than a real request to
remove every day. A user who really deletes all sets keeps the rows until the first new set.

**Why**: The rows follow the log in the same atomic write that changes the log, so the tab is
never out of step after a successful run (US2, SC-004).

**Conflict rule (constitution III)**: The sheet's log decides which days exist. Statuses follow
006 R4: a pending phone choice wins unless the sheet's value changed since the phone last saw it.
Durations and the balance are never written by the app as values. A row typed in the web UI is
kept if its date has sets and removed if not. Its typed duration is replaced by the formula (R5).
As with log deletes since 002, a row inserted in the web UI between the read and the write can
shift indexes. The window is a few hundred milliseconds, and the next run repairs the result.

**Alternatives**: removing rows only in the nightly script. That needs automation, which US1
makes optional. Rejected.

## R4. The rule is shared with the script through one set of test cases

**Decision**: `workoutRowsPlan` (script) and `DayRowsPlan` (app) implement the same rule: which
days get rows, the default status, and which rows go. Each is tested against
`apps-script/tests/fixtures/day-rows.json`, a list of `{log days, workout rows, expected appends,
removals}`. The Node tests and a JVM test (`DayRowsFixtureTest`, which reads the file through the
project path) both load it.

**Why**: These are the only two places where logic is still duplicated after option 2. Shared
cases keep them from drifting apart. This is what was agreed in the discussion before /specify.

**Alternatives**: one implementation only, with the script calling the app. That is impossible,
because the script runs alone at night. A Kotlin-to-JS build is a big new toolchain for about 30
lines. Rejected.

## R5. Calculated form: checked and repaired after each sync, recorded as structure version 3

**Decision**: After a successful run, a form step makes one read (call 24: `spreadsheets.get` with
`workout!A2:B`, `balance!A1` and the developer metadata). It compares the cells with R1/R2, using
the formula normalisation from `StructureValidator`. If needed, it makes one write (call 25):

- Rows with a date in A whose B is not the duration formula get the formula, in one `repeatCell`
  per contiguous run.
- `balance!A1` gets the balance formula, unless the gate in R6 holds.
- On the first successful repair, the metadata `workout_rec.structure_version` is set to `3`
  (FR-009).

The conversion of an existing spreadsheet is therefore the first form step plus the removals
that R3 already makes. Repeating it changes nothing, and an interrupted conversion is completed
by the next run. Another phone sees version 3 and only checks.

**Reporting (FR-011, FR-016)**: `CALCULATION_RESTORED` is reported only when the spreadsheet was
already at version 3 and the cell held a different non-empty value or formula. An empty B (a row
added by hand or by an old script) is filled silently. A failure of call 24 or 25 is reported as
`CALCULATION_CHECK_FAILED` (`failure` splits no-connection off). It does not fail the sync: the
sets are already written, and the next run tries again.

**Why**: A second step after the commit keeps call 22's row arithmetic as it is. One extra GET per
run is well within quota (60 reads per minute per user). `spreadsheets.get` with `fields`
returns the formulas (`userEnteredValue.formulaValue`) and the metadata IDs that `upsertMetadata`
needs in one call. `values.batchGet` has one render option per call and no metadata.

**Alternatives**: checking only at app start. That would miss FR-011's "next sync" and the rows
added in the web UI during the session. Folding the repairs into call 22 would need its row
indexes to be shifted by the deletes. Both rejected.

## R6. Gate: the balance formula waits while an old script still writes the balance at night

**Decision**: The form step writes the balance formula and the version-3 marker unless
automation is **On** (`AutomationStatusEvaluator`) and `workout_rec.script_version` is below 3.
In that case the step still writes durations, and it retries on every run. The 006 upgrade at app
start normally lifts the gate on the same start, because upgrade (refresh) runs before the first
sync.

**Why**: A v1/v2 nightly run overwrites `balance!A1` with a value every night. Writing the formula
first would produce a nightly "restored" report and a balance that changes twice. Users who
skipped "Enable automation" have no trigger (status NotEnabled or ScriptMissing), so they are
converted at once, even when the Apps Script API setting is off and the upgrade fails.

## R7. Script version 3

**Decision**: `SCRIPT_VERSION = 3`. `Config.gs` (generated by the app) also gets
`DURATION_FORMULA`, so the script and the app write the same text. `workoutRowsPlan` returns
`{ appends: [[date, flag]], removals: [row], formulaRows: [row] }`. `dailyJob` then:

1. deletes the removal rows (descending);
2. sets the formula in empty B cells;
3. appends rows as `[date, DURATION_FORMULA, flag]`;
4. writes the balance value only when `balance!A1` has no formula (a spreadsheet the app has not
   converted yet: R6 the other way round);
5. writes `last_daily_run`.

It never writes a duration value (FR-014). The script uses the same empty-log guard as R3.
`onEdit` and `onSelectionChange` are unchanged. The `Enable.html` text drops "and balance".

**Why**: This keeps US4 and US5 working with the calculated form, and the upgrade path is the
existing 006 R7.

## R8. First run and reminders: setup ends at Ready, automation becomes an action

**Decision**: In `SpreadsheetSetupFlow`, `afterMatch`, `addTablesThenAttach` (renamed
`addTables`) and `retryCreating` return `Ready` without `attach`. `refresh` no longer builds a
`Reminder`; it returns the automation status, which feeds the automation entry. The entry sends
`SetupAction.StartAutomation` (the old `FixAutomation`, renamed). This drives the existing
`NeedsApiSetting` and `NeedsEnable` screens, and `ContinueForNow` returns to the screen the entry
was opened from. `AutomationReminder` and the Today banner are deleted.

**SC-001 verified against the code**: today a first run shows the account chooser, Google's
account and consent screens, setup progress, the "Allow scripts" guide (and a browser), and the
"Enable automation" guide (and a browser with Google's warning). That is 5 app or Google steps
plus 2 browser trips. After this change it shows the chooser, Google's screens and progress, then
Today: 3 steps.

## R9. Money tab: the balance is the spreadsheet's number, with an "updates after sync" note

**Decision**: `BalanceNote` becomes:

- `Calculated`: the spreadsheet is at version 3. Text: "Calculated by your spreadsheet."
- `Daily(lastRun)` and `Stopped(lastRun)`: not converted yet and an old script runs, as today.
  *Removed 2026-10-26 (clarification): the Money tab shows no automation notes, so the note is
  always "Calculated by your spreadsheet."*
- `Waiting`: changes are waiting to sync. Text: "Updates after your changes sync." This is shown
  in addition to the above when any set, day status or payment is waiting.

`Off` and the "balance is not updated" text are removed (FR-002). `MoneySummary` gains
`calculated: Boolean`, taken from call 23's metadata (`structure_version >= 3`). This adds a Room
column (schema 3 → 4, auto-migration, default 0). The old "Enable automation" button in the card
goes; automation is turned on only from the account menu (FR-012, clarification 2026-10-26).

**Why**: FR-005a (clarification Q3): a single source of truth and no balance calculation in the
app.

## R10. Monitoring

New `NonFatalIssue` keys: `CALCULATION_RESTORED` (details `cell=balance|duration`, `rows=<count>`)
and `CALCULATION_CHECK_FAILED` (`reason` from `failure`). There are no dates, values or names.
Failures of the day-rows step are failures of the sync run and are reported as they are today.
`WORKOUT_ROW_NOT_UPDATED` notices are no longer produced, because the duration now follows the
log. The enum value stays so that stored notices still load.
