# Research: Log New Workout

Decisions for [spec.md](spec.md). Builds on feature 001 ([research](../001-google-account-setup/research.md)):
Kotlin + Compose, manual DI (`AppContainer`), OkHttp + kotlinx.serialization REST clients,
`ApiAuthorizer` access tokens, `drive.file` scope, CI-only builds.

## R1. Local storage on the phone

- **Decision**: Room (SQLite) for the exercise list cache, the log cache, pending changes, past
  workout drafts and sync notices. DataStore (already used) keeps small sync status values.
- **Rationale**: FR-005 requires every confirmed set to survive app close, restart and update;
  the log cache can hold thousands of rows and must be replaced atomically on refresh; history
  and "last time" need indexed queries by exercise and day. Room gives transactions, indexes,
  migrations and a `Flow` API for Compose, and is the Android-standard choice.
- **Alternatives**: DataStore/JSON file (no transactions or queries, whole file rewritten per
  set); raw SQLite (more code, no compile-time query checks); SQLDelight (fine, but Room is the
  platform default and works with KSP on Kotlin 2.1).

## R2. Background sync

- **Decision**: WorkManager unique one-time work `log-sync` with a `CONNECTED` network
  constraint, enqueued with `ExistingWorkPolicy.APPEND_OR_REPLACE` after every local change and on
  app start, so a set saved while a run is in progress always gets another run (analysis fix I1).
  The worker always finishes as success; after a network failure it queues a fresh run one minute
  later instead of using WorkManager backoff, which can grow to hours (analysis fix U1, SC-003). When online, the app also runs the same sync in-process right after a set is
  confirmed, so SC-003's "within 1 minute" holds. A process-wide mutex serializes runs, so the
  in-process and WorkManager runs never overlap.
- **Rationale**: Enqueued work persists across app close and reboot and runs when the network
  returns without the app being open (US2 scenario 3, SC-003). It is the Android-recommended API
  for deferrable guaranteed work.
- **Alternatives**: Foreground service (needs a notification, wasteful); `AlarmManager` (no
  network constraint); sync only while the app is open (fails US2 scenario 3).

## R3. Access token in the background

- **Decision**: The sync worker calls the existing `ApiAuthorizer.authorize(email)`. When it
  returns `Granted`, sync proceeds; when it returns `NeedsUserConsent`, the worker stops with
  status `NeedsSignIn` (shown on the main screen, FR-010) and the next app open resolves consent
  in the foreground as in feature 001.
- **Rationale**: The Authorization API returns a cached token without UI when access was granted
  before; consent can only be shown from an Activity.

## R4. Where and how new sets are written

- **Decision**: One `spreadsheets.batchUpdate` per sync with an `appendCells` request on the log
  sheet (`sheetId`), rows in logged order, each row `[Date serial, Drill, W, R]` as typed values
  (`numberValue`, `stringValue`, `numberValue`, `numberValue`), `fields:
  userEnteredValue`. Formats are not written; the log column formats and table types apply.
- **Rationale**: `appendCells` positions the rows on the server after the last row that has
  data, so a row added in the web UI between the app's read and write is never overwritten
  (FR-007, FR-015). Empty table rows (format and drop-down only) are not "data", so in a new
  spreadsheet the first set lands in row 2 inside the log table.
- **Spike (first task)**: confirm with a real spreadsheet that `appendCells` (a) lands in row 2
  of an empty log table, (b) lands after the last filled row inside the table, (c) still works
  past row 1000 (table end). If (a) or (b) fails, fallback: write with `updateCells` at the row
  after the last filled row found by the read in R5, accepting a race window of about one second
  (documented as best effort under constitution III).
- **Alternatives**: `values.append` (its "table detection" heuristic can pick the wrong range
  with Sheets tables); `appendCells` with `tableId` (appends after the table's last row, i.e.
  after row 1000 of an empty table); a hidden id column E (changes the reference structure and
  shows up in the rec `FILTER`).

## R5. Exactly-once writes and finding a row again (set identity)

- **Decision**: A set's identity in the sheet is its **content key**: date-time to the second +
  exercise name + weight + reps. Before writing, every sync reads `log!A2:D` (one
  `values.get` with `valueRenderOption=UNFORMATTED_VALUE`, `dateTimeRenderOption=SERIAL_NUMBER`)
  and:
  - skips a pending new set whose key is already in the log (a previous sync wrote it but the
    response was lost) and marks it synced (FR-009);
  - locates the target row of a pending edit or delete by the key it had when last downloaded
    (`lastSeen`); when several rows match, the one closest to the remembered row index is used;
    when none matches, the row was changed or removed in the web UI → conflict (FR-015).
  The app guarantees unique keys for its own sets: a live set confirmed in the same second as
  the previous set gets +1 s; past-workout times are spread at whole-second steps.
- **Rationale**: No change to the reference structure; keys are effectively unique because live
  times have seconds and web auto-fill also writes the current date-time; one read per sync
  doubles as the history refresh (R7).
- **Alternatives**: Row-level developer metadata with a set id (moves with the row, but the
  spreadsheet-wide 30,000-character metadata limit is reached after a few hundred sets); hidden id
  column (see R4); trusting the row index (shifts when the user inserts or deletes rows in the web
  UI).

## R6. Edits and deletes of rows already in the sheet

- **Decision**: In the same `batchUpdate` as R4, before `appendCells`:
  `updateCells` for edited rows (columns B–D only; the date-time is never changed) and
  `deleteDimension` (ROWS) for deleted rows, ordered by descending row index so earlier requests
  do not shift later ones. Targets come from the read in R5 taken immediately before. A pending
  change whose target is not found is dropped and recorded as a sync notice (FR-015, US4
  scenario 6).
- **Rationale**: `batchUpdate` is atomic, so either all changes of a sync apply or none.
  Deleting the row (not clearing it) keeps the log free of gaps; an empty row in the middle
  would also be picked up by the web auto-fill script ("fills empty rows above").
- **Known limit**: Sheets has no conditional write; a web edit landing between the read and the
  `batchUpdate` (about one second) can still be overwritten. Accepted as best effort
  (constitution III).

## R7. Reading the log and the exercise list (cache refresh)

- **Decision**: One `values:batchGet` for `log!A2:D` and `drills!A2:B` (unformatted, serial
  dates) plus the spreadsheet time zone (`spreadsheets.get` with
  `fields=properties.timeZone`, cached). Refresh runs at the start of every sync, on app start
  and resume (at most every 5 minutes), and after a sync that wrote something. The cache is
  replaced in one Room transaction.
- **Rationale**: The spec requires the spreadsheet to win over the cache (FR-015). A full read is
  simple and cheap: 10,000 log rows are about 400 KB and one request; real logs grow by ~50 rows
  per workout.
- **Alternatives**: Incremental reads by row range (breaks when rows are inserted or deleted in
  the web UI); Drive change notifications (needs a server).

## R8. Date-time conversion

- **Decision**: Sheet date-times are serial numbers (days since 1899-12-30, local time of the
  spreadsheet's time zone). The app stores sets as epoch seconds; conversion uses the spreadsheet
  time zone; serials read from the sheet are rounded to the nearest second. "Today" and day
  grouping use the spreadsheet time zone (normally the phone's, since feature 001 creates the
  spreadsheet with the phone's zone).
- **Rationale**: Matches how the daily script groups days and computes durations (SC-006); rows
  entered as dates without time (old web data) become 00:00:00 and still group correctly.

## R9. Display model: cache plus pending changes

- **Decision**: What the user sees = cached log rows with pending changes applied on top
  (pending new sets added, pending edits replace values, pending deletes hide rows). A pending
  change is coalesced per set: edit + edit → one edit with final values; edit + delete → delete
  of the original key; new + edit → new with final values; new + delete → nothing (FR-013a).
- **Rationale**: One source of truth for history, "last time", record, today's workout and the
  past days list; pending state gives the per-set sync state (FR-010).

## R10. Past workout entry

- **Decision**: A past workout is a draft (date, start, duration, sets in entry order) stored in
  Room and not synced until the user taps Save; times are computed on save:
  `t_i = start + round(i × duration / (n − 1))` seconds for `n > 1`, `t_0 = start` for `n = 1`,
  then made strictly increasing by +1 s where rounding collides. Dates in the future are refused.
- **Rationale**: Recalculation while editing (US5 scenario 3) is only safe before the sets
  exist in the sheet, because a set's date-time is not editable afterwards (FR-006).

## R11. Record and "last time"

- **Decision**: Pure functions over the display model: last time = the sets of the most recent
  day before today that has the exercise, in time order; record = the max weight, and the max
  reps among sets with that weight (rec tab rule). Pre-fill per FR-012.
- **Rationale**: Pure Kotlin, fully unit-testable; matches the rec tab's conditional formatting.

## R12. Weight and reps input

- **Decision**: Weight parsed from text with comma or dot, at most 2 decimals, ≥ 0, ≤ 999.99;
  steppers ±0.5 (clamped at 0) and reps ±1 (1–999). Values stored as `BigDecimal` scale 2 for
  weight to avoid float artefacts in comparisons and keys; written as `numberValue`.
- **Rationale**: Content keys (R5) compare weights exactly; `17.5` must equal `17,5`.

## R13. Sign out, switching account and spreadsheet changes

- **Decision**: Sign out / switch first runs one sync attempt (when online, up to 10 s), then
  the dialog shows the number of still-pending sets (FR-014); confirming clears the Room
  database. When the bound spreadsheet ID changes (rewrite or re-creation by feature 001),
  the caches and pending edits/deletes are cleared, and pending new sets are kept and synced to
  the new spreadsheet (spec edge case).

## R14. New dependencies

| Dependency | Version (pin in `libs.versions.toml`) | Why |
|------------|----------------------------------------|-----|
| Room (`room-runtime`, `room-ktx`, `room-compiler` via KSP; `room-testing` for tests) | 2.6.1 | R1 |
| KSP Gradle plugin | 2.1.0-1.0.29 (matches Kotlin 2.1.0) | Room annotation processing |
| WorkManager (`work-runtime-ktx`; `work-testing` for tests) | 2.10.0 | R2 |

No other new libraries. Compose number fields, steppers and lists use Material 3 already present.
