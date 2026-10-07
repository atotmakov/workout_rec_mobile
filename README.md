# Workout Rec

Android app (Kotlin, Jetpack Compose) for logging workouts into the user's own Google Sheets
spreadsheet, with an Apps Script attached to the spreadsheet for daily automation. No custom
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
2. The app creates `workout_rec_database_` with the tabs log, drills, rec, money, workout, balance.
3. **Step 1 (once per Google account):** the app opens script.google.com/home/usersettings — turn
   on **Google Apps Script API**, then return to the app.
4. **Step 2 (once per spreadsheet):** the app opens the script's "Enable automation" page. Google
   shows "Google hasn't verified this app" because the script is your own: tap **Advanced →
   Go to workout_rec_automation → Allow**, then return to the app.

Either step can be skipped for now; a reminder stays on the main screen until both are done.

## Logging workouts

The main screen is today's workout (spec [002](specs/002-log-workout/spec.md)).

- **Add exercise** lists the exercises of the `drills` tab (muscle group in column A, exercise in
  column B); add exercises there in the web UI. Pick one, choose how many sets, then confirm each
  set. Weight and reps start from your previous workout with that exercise (shown in gray until
  you change them); − / + change the weight by 0.5 kg and the reps by 1.
- Every confirmed set is saved on the phone at once and appears as a row in the `log` tab (date
  and time, exercise, weight, reps) — right away when online, otherwise as soon as the phone is
  online again, even if the app is closed. The main screen shows how many sets are waiting.
- **Workouts** lists earlier days; tap a set (there, or in today's exercise) to change or delete
  it, also when it is already in the spreadsheet or was typed in the web UI.
- **Enter a past workout** adds a forgotten workout: date, start time and duration; the set times
  are spread over the duration, so the daily job computes the right workout length.

Troubleshooting:

| Message | Meaning |
|---------|---------|
| "Sign in again to sync" | Google access needs confirming; tap it and allow access |
| "Can't sync: no connection" | Sets are safe on the phone; they sync when online |
| A set "was changed in the spreadsheet…" | You and the web UI changed the same row; the spreadsheet version was kept |
| "…its row there was not updated" | You deleted the first or last set of a day the daily job already counted; fix the `workout` row by hand if needed |

## Inspecting the automation status

The app and the script keep small status markers (developer metadata) on the spreadsheet; they
are not visible in the Sheets UI. To see them: open the spreadsheet → **Extensions → Apps
Script** → select `logMetadata` → **Run** → open the **Execution log**. Example:

```
workout_rec.automation_enabled_at = 2026-10-03T09:12:44Z
workout_rec.enable_url = https://script.google.com/macros/s/…/exec
workout_rec.last_daily_run = 2026-10-04T00:03:10Z
workout_rec.script_id = 1AbC…
workout_rec.structure_version = 1
```

The daily job (`dailyJob`) runs at about 03:00 in the spreadsheet's time zone: it adds a row to
`workout` for each new day in `log`, then recalculates `balance!A1`.
