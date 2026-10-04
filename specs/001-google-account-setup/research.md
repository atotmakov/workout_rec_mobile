# Research: Google Account Setup

**Feature**: `001-google-account-setup` | **Date**: 2026-10-03 | **Plan**: [plan.md](plan.md)

Each section records a decision, why it was chosen, and what else was considered.

## R1. UI toolkit and app architecture

- **Decision**: Kotlin, Jetpack Compose, single activity, one ViewModel per screen with
  `StateFlow` state, manual dependency injection through an `AppContainer` created in the
  `Application` class. Single Gradle module `app`.
- **Rationale**: Compose is the current native Android UI toolkit (constitution Principle I). The
  feature has three screens; a DI framework (Hilt) and multi-module setup add build complexity
  with no benefit at this size.
- **Alternatives considered**: Hilt (rejected for now; can be introduced when the app grows);
  XML views (legacy); multi-module (premature).

## R2. Choosing the Google account and showing it

- **Decision**: Credential Manager with "Sign in with Google" (`GetGoogleIdOption`,
  `filterByAuthorizedAccounts = false`, `autoSelectEnabled = false`). The returned
  `GoogleIdTokenCredential` gives email (`id`), display name, and profile picture URL, which
  are stored locally and shown as the top-right avatar (image loaded with Coil; initials when no
  picture).
- **Rationale**: This is Google's current recommended account picker on Android; the legacy
  `GoogleSignInClient` is deprecated. It shows the system account chooser, including "add
  account".
- **Alternatives considered**: Legacy Google Sign-In (deprecated); `AccountManager` picker
  (no profile picture, no consent integration).

## R3. Getting API access (OAuth scopes)

- **Decision**: After the account is chosen, request access with the Authorization API
  (`Identity.getAuthorizationClient(...).authorize(...)`) for that account with exactly:
  - `https://www.googleapis.com/auth/drive.file`: create, find, rename, and read only files
    this app created (clarification Q1).
  - `https://www.googleapis.com/auth/script.projects`: create the script attached to the
    spreadsheet and upload its code.
  - `https://www.googleapis.com/auth/script.deployments`: publish the script's
    "Enable automation" page.
  An access token is obtained by calling `authorize()` again at the start of each online
  session (it returns silently once granted). Tokens are kept in memory only.
- **Rationale**: Minimal set that satisfies FR-002 and FR-013; the Sheets API accepts
  `drive.file` for spreadsheets the app created. The two script scopes are sensitive, which is
  acceptable for an app in Google "Testing" mode used by its owner (see R12).
- **Alternatives considered**: `drive` / `drive.readonly` (restricted scopes; rejected by Q1);
  `spreadsheets` (gives access to all of the user's spreadsheets; not needed).

## R4. Finding an existing spreadsheet

- **Decision**: Drive API `files.list` with
  `q = name = 'workout_rec_database_' and mimeType = 'application/vnd.google-apps.spreadsheet'
  and trashed = false`, `orderBy = modifiedTime desc`. With `drive.file`, only app-created files
  are returned, which is exactly the Q1 behavior. The first result is checked (edge case "more
  than one").
- **Rationale**: Name-based lookup works after reinstall, because `drive.file` access to files
  the app created persists for the same OAuth client.
- **Alternatives considered**: Storing only the ID locally (lost on reinstall).

## R5. Creating the spreadsheet with the reference structure

- **Decision**: One Sheets API `spreadsheets.create` call with the complete spreadsheet resource:
  title, `locale = ru_RU`, `timeZone =` the phone's time zone, the six sheets in order, header
  rows and fixed cells (`rec!A2` formula, `rec!D1` note, `balance!A1 = 0`), date number format
  `dd.MM.yyyy` on date columns, the `rec!A1` drop-down (`ONE_OF_RANGE =drills!$B:$B`), the two
  `rec` conditional formatting rules, and developer metadata
  `workout_rec.structure_version = 1`. If the create call rejects any part, the same requests are
  sent in one `spreadsheets.batchUpdate` right after creation (also all-or-nothing).
- **Rationale**: A single call means setup can never leave a half-built spreadsheet (FR-009).
  `ru_RU` reproduces the reference's decimal comma; the explicit date pattern guarantees
  `dd.MM.yyyy` whatever the phone language. Using the phone's time zone makes "Daily workouts"
  group sets by the user's real day.
- **Alternatives considered**: Copying a template spreadsheet (needs read access to a file the
  app did not create, impossible with `drive.file`; see the planning note in spec
  Clarifications); building it in many small calls (can be interrupted halfway).

## R6. Checking the structure of an existing spreadsheet

- **Decision**: Two `spreadsheets.get` reads: (a) all tab titles and spreadsheet developer
  metadata, without `ranges`; (b) only for the expected tabs that exist: row 1 values of
  log/drills/money/workout, `rec!A1:D2` with `dataValidation` and formulas
  (`userEnteredValue`), and `rec` conditional formats. Requesting a range on a missing or renamed
  tab would fail the whole call ("Unable to parse range"); this way it is reported as
  `MissingTab` and leads to the rewrite question. A pure
  Kotlin `StructureValidator` compares the result with `ReferenceStructure` and returns
  `Match` or `Mismatch(reasons)`. Extra tabs and columns after the expected ones are ignored.
- **Rationale**: Two small reads that cannot fail on a changed structure; validator logic is fully
  unit-testable without the network.
- **Alternatives considered**: Comparing only tab names (too weak for the spec's definition).

## R7. Rewrite with backup

- **Decision**: Drive `files.update` renames the old file to
  `workout_rec_database_backup_<yyyy-MM-dd>`; if that name already exists among app-visible
  files, append `_2`, `_3`, … Then create the new spreadsheet as in R5. Nothing is deleted.
- **Rationale**: Matches clarification Q2 and FR-010.

## R8. Attaching the script (needs the user's Apps Script API setting)

- **Decision**: Apps Script API, in order:
  1. `projects.create { title: "workout_rec_automation", parentId: <spreadsheetId> }`, which
     creates a script attached to the spreadsheet. If it fails with 403 and the message says
     the Apps Script API is not enabled for the user, the app shows step 1 of the guide and
     opens `https://script.google.com/home/usersettings` in a Custom Tab. When the user
     returns, the app retries.
  2. `projects.updateContent`: `appsscript.json`, `Code.gs`, `Logic.gs`, `Enable.html`, and a
     generated `Config.gs` containing the spreadsheet ID and script version.
  3. `projects.versions.create`, then `projects.deployments.create` publishes the
     "Enable automation" page as a web app (execute as the user, access "only myself").
  4. The app saves `scriptId`, `deploymentId`, and the web app URL locally and as spreadsheet
     developer metadata (`workout_rec.script_id`, `workout_rec.enable_url`), so a reinstall
     finds them.
- **Rationale**: This is the only way to attach a script to an app-created spreadsheet without
  broad Drive access (user choice A during planning). A bound script keeps the simple `onEdit`
  and `onSelectionChange` triggers working exactly like the reference spreadsheet.
- **Alternatives considered**: A shared standalone web app (loses "follow selection"); the app
  doing the daily work itself (rejected in Q3).
- **Risk to verify first (spike)**: `projects.create` with `parentId` on a spreadsheet created
  with `drive.file`, and `deployments.create` producing a web app URL, using these scopes only.
  If attaching needs extra Drive access, stop and ask the user before widening scopes.

## R9. "Enable automation" page and the daily schedule

- **Decision**: The script's `doGet(e)` (web app) runs as the user. Opening it in a Custom Tab
  triggers Google's one-time approval screen (Google shows "Google hasn't verified this app"
  for personal scripts; the guide explains "Advanced → Go to workout_rec_automation"). After
  approval `doGet`:
  - installs one time-driven trigger `dailyJob`, `everyDays(1).atHour(3)` in the spreadsheet's
    time zone, if not already installed (repeat visits are harmless);
  - writes developer metadata `workout_rec.automation_enabled_at`;
  - returns a short Russian/English page (`?lang=ru|en`) saying automation is on.
  `dailyJob` runs "Daily workouts" and then "Balance" (in that order, so the balance includes the
  rows just added) and writes `workout_rec.last_daily_run`.
- **Rationale**: Web app pages work in the phone browser, unlike spreadsheet menus. One trigger
  with a fixed order avoids the reference spreadsheet's dependency on two independent triggers
  running in the right order.
- **Alternatives considered**: Custom menu `onOpen` (not shown on phones); two separate triggers
  (order not guaranteed).

## R10. Telling whether the steps are done (FR-013, FR-015)

- **Decision**: `AutomationStatus` is derived from spreadsheet developer metadata read with the
  structure check:
  - no `workout_rec.script_id` → `ScriptMissing` (step 1 or attach still needed)
  - script, no `automation_enabled_at` → `NotEnabled` (step 2 needed)
  - enabled and (`last_daily_run` or `automation_enabled_at`) within the last 48 h → `On`
  - otherwise → `Stopped` (e.g., approval revoked; shown as a reminder to open the page again)
- **Rationale**: Uses only the Sheets API and `drive.file`; no extra scopes to inspect triggers.
  The 48 h window tolerates one missed daily run.

## R11. Script source, versioning, and tests

- **Decision**: The script lives in the repository under `apps-script/` and is copied into the
  app's assets at build time (Gradle copy task). Business rules (`dailyWorkoutRows`,
  `balance`, `autoFillPlan`) are pure functions in `Logic.gs`; `Code.gs` only adapts them to
  `SpreadsheetApp`. `Logic.gs` is tested with Node's built-in test runner (`node --test`),
  loading the file in a `vm` context. `Config.gs` carries `SCRIPT_VERSION` for future upgrades.
- **Rationale**: Satisfies test-first for the script (constitution Principle II) with no extra
  npm dependencies; a single source of truth for the code the app uploads.
- **Alternatives considered**: clasp plus GAS unit test libraries (needs deployment to test);
  untested script (violates the constitution).

## R12. Google Cloud project and app verification

- **Decision**: One Google Cloud project with the Drive, Sheets, and Apps Script APIs enabled;
  OAuth consent screen "External", publishing status "Testing", with the user's account as a
  test user; an Android OAuth client (package name + signing SHA-1) and a Web OAuth client
  (its client ID is the `serverClientId` for Credential Manager), stored in
  `local.properties` and exposed via `BuildConfig`.
- **Rationale**: Testing mode allows sensitive scopes for listed test users without Google
  verification, which fits a personal app.
- **Note**: Publishing to other users later requires Google's sensitive-scope verification.

## R13. Networking, local storage, localization

- **Decision**: OkHttp + kotlinx.serialization with thin hand-written clients for the ~10 REST
  calls in [contracts/google-apis.md](contracts/google-apis.md); MockWebServer for contract
  tests. Jetpack DataStore (Preferences) for the selected account and setup progress.
  `res/values` (English) and `res/values-ru` (Russian) string resources; the system picks the
  language (FR-014).
- **Rationale**: The Google Java client libraries are large and not designed for Android
  coroutines; ten endpoints are simpler to own directly and to test with MockWebServer.
- **Alternatives considered**: Google API Client Libraries for Java (size, legacy HTTP stack);
  Retrofit (extra layer for so few calls); Room (no relational data yet).

## R14. Opening Google pages

- **Decision**: AndroidX Browser Custom Tabs for the Apps Script settings page and the
  "Enable automation" page; on return (`onResume`) the app re-checks status.
- **Rationale**: Custom Tabs share the browser's Google sign-in. A plain `ACTION_VIEW` link to a
  Google page may open a Google app instead of the browser.
- **Multiple accounts (found on the first device test, 2026-10-04)**: when the browser is signed
  into several Google accounts, script.google.com opens pages as the browser's default account;
  for the "Enable automation" page of a script owned by another account this shows "Sorry, unable
  to open the file at this time". Both step links therefore add `authuser=<app account email>`,
  which script.google.com honors (verified: the owner's email shows the authorization screen,
  another account's email reproduces the error). Implemented in `setup/GuideLinks.kt`.
