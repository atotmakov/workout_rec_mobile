# Workout Rec: engineering

User guide: [English](README.md) · [Русский](README.ru.md)

Android app (Kotlin, Jetpack Compose) for logging workouts into the user's own Google Sheets
spreadsheet. The spreadsheet calculates the balance and each day's duration itself; an Apps Script
attached to it is an optional extra (spec [007](specs/007-optional-automation/spec.md)). No custom
backend. Specs live in [`specs/`](specs/); project rules in
[`.specify/memory/constitution.md`](.specify/memory/constitution.md).

## Repository layout

| Path | What |
|------|------|
| `app/` | Android app (single Gradle module) |
| `apps-script/` | Script the app uploads into each user's spreadsheet; tests in `apps-script/tests/` |
| `specs/` | Spec Kit feature specs, plans, contracts, tasks |
| `.github/workflows/ci.yml` | CI: script tests, build + unit tests + lint, emulator UI tests |

## Build and test

CI (GitHub Actions) runs everything on every push, so no local JDK or Android SDK is required.

| Check | Local command | CI job |
|-------|---------------|--------|
| Script tests | `node --test "apps-script/tests/*.test.mjs"` (Node.js 22+) | `script-tests` |
| Build, unit tests, lint | `./gradlew assembleDebug testDebugUnitTest lintDebug` (JDK 17 + Android SDK) | `unit-tests` |
| Compose UI tests | `./gradlew connectedDebugAndroidTest` (device or emulator) | `ui-tests` (API 34 emulator) |

The debug APK is attached to each successful `unit-tests` run as the `app-debug-apk` artifact.
When every job is green on `master`, the `publish` job creates a GitHub Release `build-<run>` with
`workout-rec-build-<run>.apk` attached (debug-signed; `versionCode` = run number, so newer builds
install over older ones).

Optional repository secret `DEBUG_KEYSTORE_BASE64` (a base64-encoded debug keystore, alias
`androiddebugkey`, passwords `android`): CI then signs every build with the same key. Without it
each run uses a fresh debug key, so an installed build must be uninstalled before installing the
next one, and Google sign-in only works for the key whose SHA-1 is registered in Google Cloud.

Optional repository secrets for Google Play: `UPLOAD_KEYSTORE_BASE64` and
`UPLOAD_KEYSTORE_PASSWORD` (the upload key, PKCS12, alias `upload`). With them CI also builds
`workout-rec-build-<run>.aab` and attaches it to the release.

Optional repository secrets for app monitoring (feature 003): `TRACER_APP_TOKEN` and
`TRACER_PLUGIN_TOKEN` from the Tracer project (apptracer.ru) for `com.workoutrec`. CI passes them
only to the build that is published, never to the emulator tests. Builds without them (local
builds, forks, the emulator job) have Tracer disabled and send nothing. Locally they can be set
as `tracer.appToken` and `tracer.pluginToken` in `local.properties`. In the app, **Send
diagnostics** in the account menu turns monitoring off; a long-press on it opens test actions
that produce one report of each kind.

Every handled error is reported (constitution Principle V, feature 005): catch it and call
`monitoring.failure(issue, e, operation)` (no-connection failures become one info issue) or
`monitoring.nonFatal(...)`, with a key from `NonFatalIssue`. A catch that is a normal outcome
(a user cancel, typed text, an error handed to a caller that reports it) goes in
`app/src/test/resources/normal-outcomes.txt` with a reason. `CatchSiteGuardTest` fails the unit
tests on any catch site that does neither.

## Google Cloud setup

Needed only to sign in on a real device (task T007; research R12 in
`specs/001-google-account-setup/research.md`):

1. Create a Google Cloud project; enable **Google Drive API**, **Google Sheets API**,
   **Apps Script API**.
2. OAuth consent screen: External, publishing status Testing, add your Google account as a test
   user; scopes `drive.file`, `script.projects`, `script.deployments`.
3. Create OAuth clients: **Android** (package `com.workoutrec`, SHA-1 of the signing key) and
   **Web application**.
4. Put the Web client ID in `local.properties` as `google.webClientId=...` (local builds) or in
   the repository secret `GOOGLE_WEB_CLIENT_ID` (CI builds).

## First run on a phone

1. Choose a Google account and allow access (the app only sees files it creates).
2. The app creates `workout_rec_database_` with the tabs log, drills, rec, money, workout, balance,
   and opens Today. That's all.

The spreadsheet calculates the balance (`balance!A1`) and each day's duration (`workout` column B)
with formulas, and the app adds a `workout` row for every day with sets on each sync. An existing
spreadsheet is converted once, on its first sync with this version: rows of days without sets are
removed (they stay in the spreadsheet's version history).

### Optional: spreadsheet automation

**Spreadsheet automation** in the account menu (tap your initials at the top) attaches a script that
adds new days' rows at night while the app is closed, fills in the time when you type a set in the
spreadsheet, and makes the `rec` tab follow the selected exercise. It takes two steps, as before:

1. **Allow scripts (once per Google account):** the app opens script.google.com/home/usersettings —
   turn on **Google Apps Script API**, then return to the app.
2. **Enable automation (once per spreadsheet):** the app opens the script's "Enable automation"
   page. Google shows "Google hasn't verified this app" because the script is your own: tap
   **Advanced → Go to workout_rec_automation → Allow**, then return to the app.

Either step can be left for later with **Continue for now**; nothing reminds you.

## Logging workouts

A bar at the bottom has four tabs (spec [004](specs/004-bottom-tab-navigation/spec.md)): **Today**,
**History**, **Exercises** and **Money**. Today is today's workout (spec
[002](specs/002-log-workout/spec.md)).

- **Add exercise** lists the exercises of the `drills` tab (muscle group in column A, exercise in
  column B). Pick one, choose how many sets, then confirm each
  set. Weight and reps start from your previous workout with that exercise (shown in gray until
  you change them); − / + change the weight by 0.5 kg and the reps by 1.
- Every confirmed set is saved on the phone at once and appears as a row in the `log` tab (date
  and time, exercise, weight, reps) — right away when online, otherwise as soon as the phone is
  online again, even if the app is closed. The cloud icon in the top bar shows how many changes
  are waiting; tap it for details and **Sync now**.
- **History** lists earlier days; tap a set (there, or in today's exercise) to change or delete
  it, also when it is already in the spreadsheet or was typed in the web UI.
- **Enter a past workout** (the + in History) adds a forgotten workout: date, start time and duration; the set times
  are spread over the duration, so the spreadsheet computes the right workout length.
- **Paid | Free** (spec [006](specs/006-workout-paid-status/spec.md)): each workout day is paid
  (with the coach, it uses a prepaid workout) or free (alone). Today has the control; a day's
  details in History and **Enter a past workout** have it too, and History shows each day's
  status. A new day starts with the previous workout day's status (the first one is free). It is
  stored in the `workout` tab's "work alone" column (`1` = free), which the balance already uses.

- **Exercises** adds, renames and deletes exercises in the `drills` tab, also offline. A rename
  also renames the exercise in every `log` row and in the `rec` tab. Only exercises with no
  logged sets can be deleted.
- **Money** shows the paid workouts left (`balance` A1, calculated by the spreadsheet) and the
  payments of the `money` tab; **Add payment** adds a row there. Changes on the phone reach the
  balance once they sync ("Updates after your changes sync" while they wait).

Troubleshooting:

| Message | Meaning |
|---------|---------|
| "Sign in again to sync" | Google access needs confirming; tap it and allow access |
| "Can't sync: no connection" | Sets are safe on the phone; they sync when online |
| A set "was changed in the spreadsheet…" | You and the web UI changed the same row; the spreadsheet version was kept |
| "…was changed in the spreadsheet before your change reached it" (an exercise) | The exercise was renamed or removed in the web UI first; the spreadsheet version was kept |
| "…has sets in the log, so it was not deleted" | The log has sets of that exercise, e.g. typed in the web UI |
| "No balance yet" (Money) | `balance!A1` shows an error, e.g. a tab it reads was renamed; rename it back |

## Inspecting the automation status

The app and the script keep small status markers (developer metadata) on the spreadsheet; they
are not visible in the Sheets UI. To see them: open the spreadsheet → **Extensions → Apps
Script** → select `logMetadata` → **Run** → open the **Execution log**. Example:

```
workout_rec.automation_enabled_at = 2026-10-03T09:12:44Z
workout_rec.daily_trigger = head
workout_rec.enable_url = https://script.google.com/macros/s/…/dev
workout_rec.last_daily_run = 2026-10-04T00:03:10Z
workout_rec.script_id = 1AbC…
workout_rec.structure_version = 3
```

`structure_version = 3` means the spreadsheet calculates durations and the balance. When
automation is on, the daily job (`dailyJob`) runs at about 03:00 in the spreadsheet's time zone: it
adds a row to `workout` for each new day in `log` (with the previous day's paid/free status),
removes rows of days without sets, and puts the duration formula in empty cells; it never writes
durations, nor the balance of a converted spreadsheet. The app updates the attached script on start
when `workout_rec.script_version` is older than its own. The daily job runs that new code because
the Enable page is the project's Head deployment (`/dev`) and its trigger runs Head; a trigger made
before script version 6 ran a fixed version, so without `workout_rec.daily_trigger = head`
automation shows as off until the Enable page is opened again. If a duration or `balance!A1` is typed
over, the next sync puts the formula back.
