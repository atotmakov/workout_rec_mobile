# Contract: `workout_rec_database_` spreadsheet

Shared by the Android app (creates and checks it) and the attached script (reads and writes it).
Structure version: **1** (developer metadata `workout_rec.structure_version`).

## Spreadsheet properties

| Property | Value |
|----------|-------|
| Title | `workout_rec_database_` |
| Locale | `ru_RU` (decimal comma, as in the reference) |
| Time zone | Phone's IANA time zone at creation (e.g., `Europe/Moscow`) |
| Tabs, in order | `log`, `drills`, `rec`, `money`, `workout`, `balance` |

## Tabs

| Tab | Cell(s) | Content at creation | Type / format |
|-----|---------|--------------------|---------------|
| log | A1:D1 | `Date`, `Drill`, `W`, `R` | Column A number format `dd.MM.yyyy` (values are date-times) |
| drills | A1:B1 | `mscl`, `drill` | text |
| rec | A1 | empty | Data validation: drop-down `ONE_OF_RANGE` `=drills!$B:$B`, strict = false, show drop-down |
| rec | A2 | `=FILTER(log!A:Z, log!B:B=A1)` | formula |
| rec | D1 | `FILTER(log!A:Z, log!B:B=A1)` | text |
| money | A1:C1 | `date`, `workouts`, `sum` | Column A format `dd.MM.yyyy` |
| workout | A1:C1 | `date`, `duration, min`, `work alone` | Column A format `dd.MM.yyyy` |
| balance | A1 | `0` | number, no header |

## Conditional formatting (tab `rec`)

| # | Range | Custom formula | Format |
|---|-------|----------------|--------|
| 1 | `C2:C1000` | `=C2=MAX($C$2:$C)` | background `#B7E1CD` |
| 2 | `D2:D1000` | `=AND($C2=MAX($C:$C), $D2=MAXIFS($D:$D, $C:$C, MAX($C:$C)))` | background `#B7E1CD` |

## Developer metadata (spreadsheet level, visibility `DOCUMENT`)

| Key | Written by | Value |
|-----|-----------|-------|
| `workout_rec.structure_version` | app, at creation | `1` |
| `workout_rec.script_id` | app, after attaching the script | Apps Script project ID |
| `workout_rec.enable_url` | app, after deployment | "Enable automation" web app URL |
| `workout_rec.automation_enabled_at` | script `doGet` | ISO-8601 UTC timestamp |
| `workout_rec.last_daily_run` | script `dailyJob` | ISO-8601 UTC timestamp |

Each key appears at most once; writers replace the existing entry. The markers are not visible in
the Sheets UI; to inspect them, run the script function `logMetadata` from the script editor
(see [apps-script.md](apps-script.md)).

## Structure match rule

A spreadsheet matches when, ignoring data rows, extra tabs, and extra columns after the listed
ones:

1. all six tabs exist (order is not checked);
2. row 1 of `log`, `drills`, `money`, `workout` starts with the listed headers (exact text);
3. `rec!A2` contains the formula above (whitespace-insensitive);
4. `rec!A1` has a drop-down from `drills!$B:$B`;
5. `rec` has both conditional rules (formula and range; color not compared).

Script presence and automation state are not part of the match (see `AutomationStatus` in
[../data-model.md](../data-model.md)).
