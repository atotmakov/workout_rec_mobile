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
