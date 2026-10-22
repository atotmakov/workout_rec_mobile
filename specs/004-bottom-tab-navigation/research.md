# Research: Bottom Tab Navigation

Decisions for [spec.md](spec.md). Feature 002's decisions ([research](../002-log-workout/research.md),
R1–R14) still apply; references like "002-R5" point there.

## R1. Bottom bar and per-tab navigation

- **Decision**: Material 3 `NavigationBar` with four `NavigationBarItem`s inside the existing
  Navigation Compose 2.8.5 `NavHost`. Each tab is a nested graph (`tab/today`, `tab/history`,
  `tab/exercises`, `tab/money`) with its own start screen. A tab tap uses the standard pattern
  `navigate(tab) { popUpTo(graph.findStartDestination().id) { saveState = true };
  launchSingleTop = true; restoreState = true }`, which keeps each tab's stack and scroll state
  (FR-003). Tapping the selected tab pops that tab to its start (FR-003). Today's graph is the
  start graph, so system Back from another tab's first screen lands on Today and Back on Today
  finishes the activity (FR-004).
- **Bar visibility**: shown when the current destination is not in the hidden set: exercise
  logging, past workout editor, setup (FR-005). Dialogs draw over the bar anyway. The bar hides
  without animation so logging gets the full height at once.
- **Icons**: four vector drawables in `res/drawable` (Material Symbols paths: today, history,
  fitness_center, payments), the same way as the sync icons; no `material-icons-extended`
  dependency (≈ 30 MB of icons for four).
- **Rationale**: the platform pattern the user asked for ("like YouTube"); no new library.
- **Alternatives**: a `Scaffold` per tab with separate `NavHost`s (four controllers, harder deep
  links between tabs, e.g. Exercises → "Log today"); a custom bar (no reason).

## R2. Where the existing screens go

- Today tab: Today (start), exercise picker, plan sets dialog, exercise logging (bar hidden).
- History tab: past days (start, former "Workouts" screen) with an "Enter a past workout" action
  in its top bar and empty state, day detail, edit set dialog, past workout dialog and editor
  (bar hidden in the editor). After Save the editor pops back to past days (spec US2 scenario 2).
- Today loses the "Workouts" and "Enter a past workout" links (FR-006), which also removes the
  empty gap noted after redesign 1a.
- The account avatar and the sync icon stay in the top bar of every tab's start screen (spec edge
  case); the account menu stays where it is.

## R3. Exercise changes on the phone

- **Decision**: a new table `pending_exercise_change` with kinds `ADD`, `RENAME`, `DELETE`
  ([data-model.md](data-model.md)). The `exercise` cache stays a pure copy of the drills tab; the
  list the app shows = cache with pending changes applied (like 002-R9 for sets).
- **Coalescing** (one transaction, like 002-R9): ADD then RENAME → the ADD gets the new values;
  ADD then DELETE → both gone; RENAME then RENAME → one RENAME from the first old values to the
  last new values; RENAME then DELETE → impossible (a renamed exercise with sets cannot be
  deleted; one without sets: DELETE keeps the RENAME's `lastSeen`, so the original drills row is
  removed).
- **Duplicates**: names are compared trimmed and case-insensitive against the shown list (FR-008,
  FR-008a). Muscle groups are free text; the field offers the existing groups.
- **Rename and the phone's sets**: in the same transaction as the RENAME, pending `NEW`/`EDIT`
  set changes with the old name get the new name, so they are written with it. Cached log rows
  are not touched; the display model maps old → new names while the RENAME is pending, so history,
  last time and the record follow the exercise at once (spec US3 scenario 6).
- **Delete allowed** (FR-008b) when no displayed set (cache + pending) uses the name. The check is
  repeated during sync against the fresh log read (R5).

## R4. Writing exercise changes and payments in the same sync

- **Decision**: extend the existing sync run (002 contracts "Sync algorithm") instead of a second
  worker. Call 21 reads `drills!A2:B` already; it now keeps the row index of each drill. The single
  call 22 `batchUpdate` gets more requests, still in one atomic write:
  1. log row deletes and edits, bottom-up (as now);
  2. drills: deletes bottom-up and renames (`updateCells` A:B of the located row);
  3. for each applied RENAME: `findReplace` of the old name with the new one,
     `matchEntireCell = true`, `matchCase = true`, on log column B and on `rec!A1`;
  4. `appendCells` of new sets to log (as now), of new exercises to drills, of new payments to
     money.
  Requests on different tabs do not shift each other's row indexes; within a tab the order above
  keeps indexes valid.
- **`findReplace` for the log**: one request renames every log row with the old name, including
  rows the web UI added, without the app having to list hundreds of row indexes; it never matches
  other names because the whole cell must equal the old name. `rec!A1` is included so the record
  view keeps its exercise (spec edge case).
- **Rationale**: one write keeps the "all or nothing" property of 002-R6; new exercises, renames
  and payments reach the sheet as soon as sets do (SC-004, SC-008) and show in the same sync icon.
- **Alternatives**: one `updateCells` per renamed log row (large requests, misses web rows added
  after the read); a separate payments sync (a second schedule and status for little gain).

## R5. Exactly-once and conflicts for exercises and payments

Same principle as 002-R5/R6: a change is applied only when the sheet still looks as the app saw
it; otherwise the sheet wins and the user gets a notice.

| Pending | Found in the fresh read | Result |
|---------|-------------------------|--------|
| `ADD name` | a drills row with that name (any case) | done, nothing written (written earlier, or added in the web UI) |
| `ADD name` | none | append |
| `RENAME old → new` | drills row `old` with the group the app saw | update row + `findReplace` |
| `RENAME old → new` | no `old`, but `new` exists | done (an interrupted run applied it) |
| `RENAME old → new` | neither | dropped + notice `EXERCISE_CHANGE_DROPPED` (changed in the web UI) |
| `DELETE name` | drills row and no log row with the name | delete row |
| `DELETE name` | log rows with the name exist | dropped + notice `EXERCISE_IN_USE` (spec edge case) |
| `DELETE name` | no drills row | done |
| payment | key (date, workouts, sum) found in money more times than other pending payments with the key | done (an earlier run wrote it) |
| payment | otherwise | append |

A dropped RENAME also renames the waiting set changes back to the old name in the same
transaction (undoing R3's rewrite), so they are written under the name the drills tab still has;
the user is told by the notice.

Payments are matched by content, so the sync reads `money!A2:C` in call 21 only when payments are
pending, and only if the money tab exists (call 20 lists the tabs).

## R6. Reading money and balance (call 23)

- **Decision**: one request, `GET {id}?ranges=money!A2:C&ranges=balance!A1&includeGridData=true
  &fields=developerMetadata(metadataKey,metadataValue),sheets(properties(title),data(rowData(values(effectiveValue))))`.
  It returns the payments, the balance and the metadata (`workout_rec.last_daily_run`,
  automation keys) together ([contracts/sheets.md](contracts/sheets.md)).
- **When**: on opening Money when the data is older than 5 minutes (as 002-R7), on pull-down, once
  per app start in the background (FR-010), and after a sync that wrote payments. It runs outside
  the sync mutex and never blocks logging.
- **Not part of the set sync**: a missing or broken money or balance tab must never stop sets
  from syncing; call 23's errors only affect the Money tab.
- **Rationale**: one small call; the money tab has tens of rows.

## R7. The balance note (FR-009a)

- The value is `balance!A1`, never computed by the app (spec clarification Q3).
- The note uses feature 001's `AutomationStatusEvaluator` on the metadata from call 23:
  `On` → "Calculated once a day · last at {time}"; `Stopped` → "Last calculated {date}; the daily
  update seems stopped" with the existing "Enable automation" link; `NotEnabled` /
  `ScriptMissing` → "Not updated: daily automation is off" with the link.
- When a payment is pending or was written after `last_daily_run`, the note adds "New payments
  are counted at the next daily calculation" (spec US4 scenario 7).

## R8. Payment values

- Date: local date, not after today in the spreadsheet time zone, default today; written as the
  serial of midnight (002-R8), so the column format `dd.MM.yyyy` shows it.
- Workouts: whole number 1–999. Sum: decimal ≥ 0 with up to 2 decimals, typed with the locale's
  separator, no currency (the money tab has none).
- Read rows: a row is a payment when column A is a date serial; non-numeric B or C are shown as
  "—" and left out of the total (spec edge case).

## R9. Database migration

- **Decision**: Room schema version 2 with an `@AutoMigration(from = 1, to = 2)`: four new tables
  only, no change to existing ones, so Room can generate it. `app/schemas/…/1.json` (currently not
  committed) is committed, and a `MigrationTestHelper` test on the CI emulator opens a version 1
  database with sets and pending changes and checks they survive.
- Sign out / switch account (002-R13) clears the new tables with the rest.

## R10. Sync icon count

- The icon's count becomes "changes waiting": pending sets + exercise changes + payments. Strings
  change from "N sets not in the spreadsheet yet" to "N changes not in the spreadsheet yet"
  (EN/RU); the sign-out warning counts all of them too, since all are lost on sign-out.

## R11. Testing

- JVM: exercise rules (duplicates, deletable), coalescing, display mapping of renames, sync
  planning against a fake Sheets API (every row of the R5 table), request building and parsing
  for calls 21–23 with MockWebServer, payment validation, balance note.
- Instrumented (CI emulator): Room migration 1 → 2, DAO for the new tables, Compose UI tests for
  the bar (tab switch, state kept, reselect, Back, hidden on logging), Exercises and Money screens.
- Screen gallery: new shots for each tab and dialog, light / dark / dark RU.
- Manual on a device ([quickstart.md](quickstart.md)): rename with history synced, payment
  appended, balance note with automation on and off.
