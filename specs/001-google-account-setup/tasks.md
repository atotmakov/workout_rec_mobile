---

description: "Task list for Google Account Setup"
---

# Tasks: Google Account Setup

**Input**: Design documents from `/specs/001-google-account-setup/`

**Prerequisites**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md),
[data-model.md](data-model.md), [contracts/](contracts/), [quickstart.md](quickstart.md)

**Tests**: Required. The constitution (Principle II, Test-First, non-negotiable) requires tests to
be written and observed failing before implementation, for both the Android app and the
Apps Script code.

**Organization**: Tasks are grouped by user story so each story can be implemented and tested
independently.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies on incomplete tasks)
- **[Story]**: Which user story this task belongs to (US1, US2, US3)

## Path Conventions

- App code: `app/src/main/java/com/workoutrec/`
- JVM unit and contract tests: `app/src/test/java/com/workoutrec/`; fixtures in
  `app/src/test/resources/fixtures/`
- Instrumented Compose UI tests: `app/src/androidTest/java/com/workoutrec/`
- Script: `apps-script/`; script tests: `apps-script/tests/`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Project initialization and basic structure

- [ ] T001 Create the root Gradle project: `settings.gradle.kts` (single module `:app`), root `build.gradle.kts`, Gradle wrapper, and `gradle/libs.versions.toml` with latest stable versions of Kotlin 2.x, Android Gradle Plugin, Compose BOM, Navigation Compose, Lifecycle ViewModel Compose, AndroidX Credentials (+ play-services-auth bridge), `com.google.android.libraries.identity.googleid`, Play services Auth, OkHttp, OkHttp MockWebServer, kotlinx-serialization-json, DataStore Preferences, Coil Compose, AndroidX Browser, kotlinx-coroutines-test, JUnit 4, Compose UI test (per plan.md Technical Context)
- [ ] T002 Create `app/build.gradle.kts`: namespace and applicationId `com.workoutrec`, `minSdk = 26`, compile/target SDK latest stable, Compose enabled, kotlinx-serialization plugin, `buildConfigField` `WEB_CLIENT_ID` read from `local.properties` key `google.webClientId`, and a Gradle task that copies `../apps-script/{appsscript.json,Code.gs,Logic.gs,Enable.html}` into generated assets under `apps-script/` before `preBuild`
- [ ] T003 [P] Create `app/src/main/AndroidManifest.xml` (INTERNET permission, `WorkoutRecApplication`, `MainActivity`), `app/src/main/java/com/workoutrec/WorkoutRecApplication.kt`, `app/src/main/java/com/workoutrec/MainActivity.kt` (empty Compose content), and theme in `app/src/main/java/com/workoutrec/ui/theme/Theme.kt`
- [ ] T004 [P] Create `app/src/main/res/values/strings.xml` (English) and `app/src/main/res/values-ru/strings.xml` (Russian) with `app_name`; all later user-visible text goes into both files (FR-014)
- [ ] T005 [P] Create the script skeleton per [contracts/apps-script.md](contracts/apps-script.md): `apps-script/appsscript.json` (`runtimeVersion` `V8`, `oauthScopes` `https://www.googleapis.com/auth/spreadsheets` and `https://www.googleapis.com/auth/script.scriptapp`, `webapp` `{ "executeAs": "USER_DEPLOYING", "access": "MYSELF" }`; `timeZone` placeholder replaced at upload), empty `apps-script/Logic.gs`, `apps-script/Code.gs`, `apps-script/Enable.html`, and `apps-script/tests/load-logic.mjs` that loads `Logic.gs` into a `node:vm` context and exports its functions
- [ ] T006 [P] Update `.gitignore` (`local.properties`, `build/`, `.gradle/`, `.idea/`, `*.iml`) and add `README.md` with build/test commands from [quickstart.md](quickstart.md) and the Google Cloud setup steps from [research.md](research.md) R12

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Infrastructure every story needs, plus the spike that validates the OAuth scopes
used everywhere.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [ ] T007 Google Cloud setup (manual, owner's account), per [research.md](research.md) R12: create the project; enable Google Drive API, Google Sheets API, Apps Script API; OAuth consent screen External/Testing with the owner as test user and scopes `drive.file`, `script.projects`, `script.deployments`; Android OAuth client (package `com.workoutrec`, debug SHA-1 from `./gradlew signingReport`) and Web OAuth client; put the web client ID in `local.properties` as `google.webClientId`; record non-secret steps in `README.md`
- [ ] T008 Spike (manual, blocks T049–T053): in Google OAuth 2.0 Playground with only `drive.file`, `script.projects`, `script.deployments` (plus `openid email profile`), verify: Sheets `spreadsheets.create` works; Apps Script `projects.create` with `parentId` = that spreadsheet; `projects.updateContent` with a minimal `doGet`; `projects.versions.create`; `projects.deployments.create` returns a `WEB_APP` entry point URL; opening the URL installs a time trigger; the 403 message when the user's Apps Script API setting is off. Record request/response samples and results in `specs/001-google-account-setup/spike-results.md`. **If any step needs more access, stop and ask the user before changing scopes.**
- [ ] T009 [P] Write failing tests for error mapping per [contracts/google-apis.md](contracts/google-apis.md) "Error mapping" table (network failure → `Offline`; 401 → `TokenExpired`; 403 containing "Apps Script API" and "enable" → `AppsScriptApiDisabled`; other 403 → `AccessDenied`; 404 → `NotFound`; 429/5xx → `ServiceUnavailable`) in `app/src/test/java/com/workoutrec/google/ApiErrorMapperTest.kt`
- [ ] T010 Implement `app/src/main/java/com/workoutrec/google/ApiError.kt` (sealed class) and `app/src/main/java/com/workoutrec/google/GoogleHttp.kt` (OkHttp client with bearer-token interceptor fed by a suspend token provider, one silent re-authorize and retry on 401, up to 3 retries with backoff on 429/5xx, kotlinx `Json { ignoreUnknownKeys = true }`, error mapping) so T009 passes
- [ ] T011 [P] Write failing tests for `SettingsStore` in `app/src/test/java/com/workoutrec/data/SettingsStoreTest.kt`: saves/loads `SelectedAccount` (email "Required; non-empty", displayName "may be missing", photoUrl "may be missing"); saves `SpreadsheetBinding`; binding is discarded when it does not match "`accountEmail` must equal `SelectedAccount.email`"; `clear()` removes everything
- [ ] T012 Implement `app/src/main/java/com/workoutrec/data/SettingsStore.kt` (DataStore Preferences) and model classes `SelectedAccount` and `SpreadsheetBinding` in `app/src/main/java/com/workoutrec/data/Models.kt` per [data-model.md](data-model.md) so T011 passes
- [ ] T013 [P] Implement `app/src/main/java/com/workoutrec/auth/ApiAuthorizer.kt`: interface plus implementation using `Identity.getAuthorizationClient(context).authorize(...)` for the chosen account with exactly `https://www.googleapis.com/auth/drive.file`, `https://www.googleapis.com/auth/script.projects`, `https://www.googleapis.com/auth/script.deployments` (research R3); returns `Granted(token)`, `NeedsUserConsent(pendingIntent)`, or `Denied`; tokens kept in memory only
- [ ] T014 Implement `app/src/main/java/com/workoutrec/AppContainer.kt` (manual DI: `SettingsStore`, `GoogleHttp`, `ApiAuthorizer`, later clients/services) created in `WorkoutRecApplication.kt`
- [ ] T015 Add navigation skeleton in `app/src/main/java/com/workoutrec/MainActivity.kt` with routes `setup` and `home` and test fakes `FakeSettingsStore`, `FakeApiAuthorizer` in `app/src/test/java/com/workoutrec/fakes/`

**Checkpoint**: Foundation ready; spike confirms the scopes; user stories can begin.

---

## Phase 3: User Story 1 - Choose a Google account on first launch (Priority: P1) 🎯 MVP

**Goal**: First launch asks for a Google account and access; the main screen shows the
account's photo or initials top-right; later launches skip the prompt.

**Independent Test**: Fresh install on a phone with two accounts → choose the second → its
photo/initials top-right → relaunch opens the main screen directly (spec US1).

### Tests for User Story 1 ⚠️

> **NOTE: Write these tests FIRST, ensure they FAIL before implementation**

- [ ] T016 [P] [US1] Unit test for initials in `app/src/test/java/com/workoutrec/home/InitialsTest.kt`: "first letter of the first two words of `displayName`, else the first letter of `email`, upper-cased" (data-model.md), including null/blank displayName and single-word names
- [ ] T017 [P] [US1] ViewModel tests in `app/src/test/java/com/workoutrec/setup/SetupViewModelAccountTest.kt` with fakes (`FakeAccountPicker` in `app/src/test/java/com/workoutrec/fakes/`): no stored account → `SignedOut`; pick + grant → account saved and flow continues; pick cancelled or access denied → `SignedOut` with "account required" message and choose-again action (US1 #4); stored account + silent grant → `Ready` without prompt (FR-004); stored account + access revoked → `SignedOut` with message (edge case)
- [ ] T018 [P] [US1] Compose UI tests in `app/src/androidTest/java/com/workoutrec/home/AccountAvatarTest.kt`: avatar in the top-right of `MainScreen`; initials shown when photoUrl is null; tapping the avatar opens a menu showing name, email, "Switch account", and "Sign out" (US1 #5, FR-005)

### Implementation for User Story 1

- [ ] T019 [P] [US1] Implement `app/src/main/java/com/workoutrec/home/Initials.kt` so T016 passes
- [ ] T020 [P] [US1] Implement `app/src/main/java/com/workoutrec/auth/AccountPicker.kt`: Credential Manager `GetGoogleIdOption` (`filterByAuthorizedAccounts = false`, `autoSelectEnabled = false`, `serverClientId = BuildConfig.WEB_CLIENT_ID`); maps `GoogleIdTokenCredential` to `SelectedAccount` (`id` → email, `displayName`, `profilePictureUri` → photoUrl); returns `Cancelled` on user cancel (research R2)
- [ ] T021 [US1] Implement `app/src/main/java/com/workoutrec/auth/AccountRepository.kt`: `chooseAccount()`, `authorize()` (via `ApiAuthorizer`), `currentAccount` flow from `SettingsStore`, silent re-authorization on launch (depends on T012, T013, T020)
- [ ] T022 [US1] Implement `app/src/main/java/com/workoutrec/setup/SetupState.kt` (sealed states from data-model.md "Setup flow") and the account part of `app/src/main/java/com/workoutrec/setup/SetupViewModel.kt` (`SignedOut` → `Authorizing` → next / back to `SignedOut`) so T017 passes
- [ ] T023 [US1] Implement `app/src/main/java/com/workoutrec/setup/ChooseAccountScreen.kt`: explains why Google access is needed before asking (FR-002), "Choose account" button, launches the consent `PendingIntent` from `NeedsUserConsent`, shows the "account required, choose again" message
- [ ] T024 [US1] Implement `app/src/main/java/com/workoutrec/home/AccountAvatar.kt` (Coil image, initials fallback), `app/src/main/java/com/workoutrec/home/AccountMenu.kt` (name, email, "Switch account", "Sign out"; actions are no-ops until US3), and `app/src/main/java/com/workoutrec/home/MainScreen.kt` with the avatar in the top-right app bar, so T018 passes
- [ ] T025 [US1] Wire start destination in `app/src/main/java/com/workoutrec/MainActivity.kt`: stored account and silent grant → `home`; otherwise → `setup` (FR-001, FR-004)
- [ ] T026 [US1] Add all US1 texts to `app/src/main/res/values/strings.xml` and `app/src/main/res/values-ru/strings.xml`

**Checkpoint**: User Story 1 works on its own: account chosen, avatar shown, relaunch skips
the prompt.

---

## Phase 4: User Story 2 - Create the workout spreadsheet in the user's Google account (Priority: P1)

**Goal**: After the account is chosen, the app finds or creates `workout_rec_database_` with
the reference structure, offers rewrite-with-backup on mismatch, attaches the script, and guides
the user through the two one-time automation steps, with reminders until done.

**Independent Test**: With an account that has no app-created spreadsheet, finish setup, then
check Drive on the web: exactly one `workout_rec_database_` with six tabs, headers, drop-down,
formula, highlighting; automation works after the two steps (spec US2, quickstart rows 2–6a).

### Tests for User Story 2 ⚠️

> **NOTE: Write these tests FIRST, ensure they FAIL before implementation**

- [ ] T027 [P] [US2] Contract tests in `app/src/test/java/com/workoutrec/google/DriveClientTest.kt` (MockWebServer) for [contracts/google-apis.md](contracts/google-apis.md) calls 1–3: exact `q`, `orderBy=modifiedTime desc`, `fields`, PATCH body `{"name": ...}`; parse fixtures in `app/src/test/resources/fixtures/drive/`
- [ ] T028 [P] [US2] Contract tests in `app/src/test/java/com/workoutrec/google/SheetsClientTest.kt` for calls 4, 4b, 5, 6 (create body, batchUpdate body, `ranges` and `fields` of the structure read, developer metadata create/update); fixtures in `app/src/test/resources/fixtures/sheets/` (use samples from `spike-results.md`)
- [ ] T029 [P] [US2] Contract tests in `app/src/test/java/com/workoutrec/google/ScriptClientTest.kt` for calls 7–10 (project create with `parentId`, content upload file list and types, version create, deployment create and reading `entryPoints[type=WEB_APP].webApp.url`), plus 403 "Apps Script API not enabled" → `AppsScriptApiDisabled`; fixtures in `app/src/test/resources/fixtures/script/`
- [ ] T030 [P] [US2] Unit test in `app/src/test/java/com/workoutrec/spreadsheet/ReferenceStructureTest.kt`: the create request built for time zone `Europe/Moscow` has title `workout_rec_database_`, locale `ru_RU`, tabs `log, drills, rec, money, workout, balance` in order, every header/cell/format/validation/conditional rule/metadata value from [contracts/spreadsheet.md](contracts/spreadsheet.md) (including `balance!A1 = 0`, `dd.MM.yyyy` on log/money/workout column A, background `#B7E1CD`, `workout_rec.structure_version = 1`)
- [ ] T031 [P] [US2] Unit tests in `app/src/test/java/com/workoutrec/spreadsheet/StructureValidatorTest.kt` for the five "Structure match rule" items in contracts/spreadsheet.md: exact reference → `Match`; each missing tab, wrong header, missing/altered `rec!A2` formula (whitespace-insensitive), missing drop-down, missing conditional rule → `Mismatch` with that reason; extra tabs, extra columns after the listed ones, data rows, `rec!A1`/`balance!A1` values, tab order, rule color, and script metadata → still `Match`
- [ ] T032 [P] [US2] Unit tests in `app/src/test/java/com/workoutrec/spreadsheet/BackupNamerTest.kt`: `workout_rec_database_backup_<yyyy-MM-dd>` in local date; when taken, `_2`, then `_3`
- [ ] T033 [P] [US2] Unit tests in `app/src/test/java/com/workoutrec/automation/AutomationStatusEvaluatorTest.kt` for the four statuses in data-model.md (`ScriptMissing`, `NotEnabled`, `On`, `Stopped`), including the 48 h boundary using the newer of `last_daily_run` / `automation_enabled_at`
- [ ] T034 [P] [US2] ViewModel tests in `app/src/test/java/com/workoutrec/setup/SetupViewModelSpreadsheetTest.kt` with fakes (`FakeSpreadsheetSetupService`, `FakeScriptInstaller` in `app/src/test/java/com/workoutrec/fakes/`): none found → `Creating` → `AttachingScript`; found + `Match` + script present and `On` → `Ready`; found + `Match` + `ScriptMissing` → `AttachingScript`; `Mismatch` → `AskRewrite`; "No" → `SignedOut` with no write calls; "Yes" → rename then create; `AppsScriptApiDisabled` → `NeedsApiSetting`; attached → `NeedsEnable`; return with status `On` → `Ready`; "Continue for now" → `Ready` with reminder; failures → `Error(step)` and Retry repeats that step; stored spreadsheet ID 404 → `FindingSpreadsheet`
- [ ] T035 [P] [US2] Unit tests in `app/src/test/java/com/workoutrec/automation/ScriptInstallerTest.kt` with a fake `ScriptClient`: call order create → content → version → deployment → metadata (`workout_rec.script_id`, `workout_rec.enable_url`); uploaded files are `appsscript` (JSON, `timeZone` = spreadsheet time zone), `Config`, `Code`, `Logic`, `Enable`; generated `Config.gs` equals `const SPREADSHEET_ID = '<id>'; const SCRIPT_VERSION = 1;`; skips project creation when `workout_rec.script_id` already exists
- [ ] T036 [P] [US2] Script tests in `apps-script/tests/logic.test.mjs` (`node --test`) for [contracts/apps-script.md](contracts/apps-script.md) pure functions: `autoFillPlan` (fills consecutive empty rows above, stops at first non-empty or header row); `dailyWorkoutRows` (one `[date, durationMinutes, 0]` row per new day, `durationMinutes = round((max - min) / 60000)`, skips existing day keys, ordered by date, exactly three values); `balance` (sum of numeric `workouts` minus non-empty workout rows whose flag is not `1`); `formatMetadata` (sorted `key = value` lines for `workout_rec.*` only; `(no workout_rec metadata)` when none)
- [ ] T037 [P] [US2] Compose UI tests in `app/src/androidTest/java/com/workoutrec/setup/SetupScreensTest.kt`: rewrite dialog shows "Spreadsheet with name workout_rec_database_ already exists in the account, want to rewrite it?" plus where old data will be kept, with Yes/No; step 1 and step 2 guide screens show their instructions and "Open" / "Continue for now"; reminder on `MainScreen` names the missing step

### Implementation for User Story 2

- [ ] T038 [P] [US2] Implement `app/src/main/java/com/workoutrec/google/DriveClient.kt` (calls 1–3) so T027 passes
- [ ] T039 [P] [US2] Implement `app/src/main/java/com/workoutrec/google/SheetsClient.kt` (calls 4, 4b, 5, 6) and `SpreadsheetSnapshot` parsing in `app/src/main/java/com/workoutrec/spreadsheet/SpreadsheetSnapshot.kt` so T028 passes
- [ ] T040 [P] [US2] Implement `app/src/main/java/com/workoutrec/google/ScriptClient.kt` (calls 7–10) so T029 passes
- [ ] T041 [P] [US2] Implement `app/src/main/java/com/workoutrec/spreadsheet/ReferenceStructure.kt` (single source for the contract values and the create request) so T030 passes
- [ ] T042 [US2] Implement `app/src/main/java/com/workoutrec/spreadsheet/StructureValidator.kt` returning `Match` / `Mismatch(reasons)` so T031 passes (depends on T039, T041)
- [ ] T043 [P] [US2] Implement `app/src/main/java/com/workoutrec/spreadsheet/BackupNamer.kt` so T032 passes
- [ ] T044 [P] [US2] Implement `app/src/main/java/com/workoutrec/automation/AutomationStatusEvaluator.kt` so T033 passes
- [ ] T045 [P] [US2] Implement `apps-script/Logic.gs` (`autoFillPlan`, `dailyWorkoutRows`, `balance`, `formatMetadata`; no Google services) so T036 passes
- [ ] T046 [US2] Implement `apps-script/Code.gs` per contracts/apps-script.md: `onEdit` (log column B: apply `autoFillPlan`, fill A with now where empty, set `rec!A1`), `onSelectionChange` (log column B → `rec!A1`), `doGet` (install a single `dailyJob` trigger `everyDays(1).atHour(3)` if none, write `workout_rec.automation_enabled_at`, render `Enable.html` in `e.parameter.lang` `ru`/`en`), `dailyJob` (append rows from `dailyWorkoutRows` with three values, then write `balance` to `balance!A1`, then write `workout_rec.last_daily_run`), `logMetadata` (read-only, `console.log(formatMetadata(...))`); all spreadsheet access via `SpreadsheetApp.openById(SPREADSHEET_ID)`; and `apps-script/Enable.html` with Russian and English texts (depends on T045)
- [ ] T047 [US2] Implement `app/src/main/java/com/workoutrec/spreadsheet/SpreadsheetSetupService.kt`: find (first of `orderBy=modifiedTime desc`), create via one `spreadsheets.create` with `batchUpdate` fallback, read snapshot, validate, rename to backup (never delete, FR-010) (depends on T038, T039, T041, T042, T043)
- [ ] T048 [US2] Implement `app/src/main/java/com/workoutrec/automation/ScriptInstaller.kt`: reads script files from assets `apps-script/`, generates `Config.gs`, sets manifest `timeZone`, runs calls 7–10, writes metadata via call 6 so T035 passes (depends on T039, T040)
- [ ] T049 [US2] Extend `app/src/main/java/com/workoutrec/setup/SetupViewModel.kt` with the spreadsheet and automation states from data-model.md and persist `SpreadsheetBinding` (spreadsheetId, scriptId, enableUrl) so T034 passes (depends on T044, T047, T048, T008)
- [ ] T050 [US2] Implement `app/src/main/java/com/workoutrec/setup/RewriteDialog.kt` and `app/src/main/java/com/workoutrec/setup/SetupProgressScreen.kt` (progress, error message with Retry for `Offline`/`ServiceUnavailable`/`AccessDenied`, FR-011)
- [ ] T051 [US2] Implement `app/src/main/java/com/workoutrec/setup/AutomationGuideScreen.kt`: step 1 opens `https://script.google.com/home/usersettings` in a Custom Tab with instructions to turn on "Google Apps Script API"; step 2 opens `enableUrl?lang=<ru|en>` with instructions for Google's "unverified app" screen (Advanced → Go to workout_rec_automation); re-check on `ON_RESUME`; "Continue for now" (research R9, R14)
- [ ] T052 [US2] Implement `app/src/main/java/com/workoutrec/home/AutomationReminder.kt` shown on `MainScreen` and in `AccountMenu` when status is not `On`, naming the missing step and opening it; refresh status in the background on launch when online (FR-015) so T037 passes
- [ ] T053 [US2] Add all US2 texts (setup progress, errors, rewrite dialog, guide steps, reminders) to `app/src/main/res/values/strings.xml` and `app/src/main/res/values-ru/strings.xml`

**Checkpoint**: User Stories 1 and 2 work: a new account ends with a working spreadsheet and
automation; existing spreadsheets are reused or rewritten with a backup.

---

## Phase 5: User Story 3 - Switch or sign out of the Google account (Priority: P3)

**Goal**: From the avatar menu the user can switch accounts (spreadsheet check repeats for the
new account) or sign out (back to the first-launch chooser).

**Independent Test**: Choose account A, switch to B → B's avatar and B's spreadsheet; sign out
→ account chooser (spec US3).

### Tests for User Story 3 ⚠️

> **NOTE: Write these tests FIRST, ensure they FAIL before implementation**

- [ ] T054 [P] [US3] ViewModel tests in `app/src/test/java/com/workoutrec/setup/SetupViewModelAccountSwitchTest.kt`: switch clears `SpreadsheetBinding` and runs `Authorizing` → `FindingSpreadsheet` for the new account; sign out requires confirmation, clears credential state and `SettingsStore`, ends in `SignedOut`
- [ ] T055 [P] [US3] Compose UI test in `app/src/androidTest/java/com/workoutrec/home/SignOutDialogTest.kt`: "Sign out" shows a confirmation dialog; Cancel keeps the account; Confirm returns to the account chooser

### Implementation for User Story 3

- [ ] T056 [US3] Add `switchAccount()` and `signOut()` to `app/src/main/java/com/workoutrec/auth/AccountRepository.kt` (`CredentialManager.clearCredentialState`, `SettingsStore.clear()`) and handle them in `SetupViewModel.kt` so T054 passes
- [ ] T057 [US3] Implement `app/src/main/java/com/workoutrec/home/SignOutDialog.kt` and wire "Switch account" / "Sign out" in `app/src/main/java/com/workoutrec/home/AccountMenu.kt` so T055 passes
- [ ] T058 [US3] Add US3 texts to `app/src/main/res/values/strings.xml` and `app/src/main/res/values-ru/strings.xml`

**Checkpoint**: All user stories are independently functional.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Improvements that affect multiple user stories

- [ ] T059 [P] Enable Android Lint `HardcodedText` and missing-translation checks as errors in `app/build.gradle.kts`, and fix any findings (FR-014)
- [ ] T060 Run `./gradlew testDebugUnitTest`, `./gradlew connectedDebugAndroidTest`, and `node --test apps-script/tests`; all must pass
- [ ] T061 Run manual scenarios 1–13 and 6a from [quickstart.md](quickstart.md) on a device with a real account; record results in `specs/001-google-account-setup/quickstart-results.md`
- [ ] T062 [P] Update `README.md` with final setup, test commands, and how to inspect status markers with `logMetadata`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies; start immediately
- **Foundational (Phase 2)**: Depends on Setup; blocks all user stories. T007 (Google Cloud)
  must be done before T008 (spike) and before any on-device run
- **User Stories (Phase 3+)**: Depend on Foundational
- **Polish (Phase 6)**: Depends on all user stories

### User Story Dependencies

- **US1 (P1)**: Starts after Foundational; no dependency on other stories
- **US2 (P1)**: Starts after Foundational; its screens are reached after US1's account choice,
  but its services and tests (T027–T048) can be built in parallel with US1. T049–T053 need the
  spike result (T008) and US1's `SetupViewModel` (T022)
- **US3 (P3)**: Needs US1's `AccountMenu` (T024) and `SetupViewModel` (T022); independent of US2
  except that switching re-runs US2's spreadsheet check when US2 exists

### Within Each User Story

- Tests are written first and must fail before implementation (constitution Principle II)
- Clients and pure logic before services; services before ViewModel; ViewModel before screens
- Strings tasks close each story

### Parallel Opportunities

- Phase 1: T003, T004, T005, T006 in parallel after T001–T002
- Phase 2: T009 and T011 in parallel; T013 in parallel with T010/T012
- US1: T016–T018 in parallel; T019 and T020 in parallel
- US2: all tests T027–T037 in parallel; T038–T041, T043–T045 in parallel
- US3: T054 and T055 in parallel

---

## Parallel Example: User Story 2

```bash
# Write all US2 tests together (they must fail first):
Task: "Contract tests DriveClient in app/src/test/java/com/workoutrec/google/DriveClientTest.kt"
Task: "Contract tests SheetsClient in app/src/test/java/com/workoutrec/google/SheetsClientTest.kt"
Task: "Contract tests ScriptClient in app/src/test/java/com/workoutrec/google/ScriptClientTest.kt"
Task: "StructureValidator tests in app/src/test/java/com/workoutrec/spreadsheet/StructureValidatorTest.kt"
Task: "Script logic tests in apps-script/tests/logic.test.mjs"

# Then the independent implementations together:
Task: "DriveClient in app/src/main/java/com/workoutrec/google/DriveClient.kt"
Task: "SheetsClient in app/src/main/java/com/workoutrec/google/SheetsClient.kt"
Task: "ScriptClient in app/src/main/java/com/workoutrec/google/ScriptClient.kt"
Task: "Logic.gs in apps-script/Logic.gs"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1 (Setup) and Phase 2 (Foundational, including the spike)
2. Complete Phase 3 (US1)
3. **STOP and VALIDATE**: quickstart rows 1, 7 (account choice, avatar, relaunch)

### Incremental Delivery

1. Setup + Foundational → foundation and confirmed scopes
2. US1 → account and avatar (MVP)
3. US2 → spreadsheet, rewrite with backup, automation steps (the app becomes useful for later
   features)
4. US3 → switch account and sign out
5. Polish → lint, full test run, quickstart on device

---

## Notes

- [P] tasks = different files, no dependencies on incomplete tasks
- Every test task must be seen failing before its implementation task starts
- Commit after each task or logical group
- Never widen OAuth scopes without asking the user (spec FR-002, clarification Q1)
