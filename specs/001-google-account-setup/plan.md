# Implementation Plan: Google Account Setup

**Branch**: `001-google-account-setup` | **Date**: 2026-10-03 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/001-google-account-setup/spec.md`

## Summary

On first launch the user picks a Google account (Credential Manager); its photo or initials are
shown top-right. The app then gets narrow API access (`drive.file` plus two Apps Script scopes),
finds or creates the `workout_rec_database_` spreadsheet with the reference structure in one
all-or-nothing Sheets API call, checks the structure of an existing one, and on mismatch offers
a rewrite that keeps the old file as a dated backup. Finally it attaches a script to the
spreadsheet through the Apps Script API (after the user turns on the "Google Apps Script API"
setting once) and opens the script's "Enable automation" page, where the user approves the
daily schedule once. Setup status lives in spreadsheet developer metadata, so the app can show
reminders and survive reinstalls. Details: [research.md](research.md).

## Technical Context

**Language/Version**: Kotlin 2.x (latest stable, pinned in `gradle/libs.versions.toml`); Google
Apps Script V8 runtime (JavaScript) for the attached script

**Primary Dependencies**: Jetpack Compose (BOM), Navigation Compose, Lifecycle ViewModel;
AndroidX Credential Manager + `googleid` (account picker); Play services Auth (Authorization
API); OkHttp + kotlinx.serialization (Google REST calls); Jetpack DataStore; Coil (avatar);
AndroidX Browser (Custom Tabs)

**Storage**: Google Sheets spreadsheet (source of truth); DataStore Preferences on the phone for
the selected account and setup cache

**Testing**: JUnit 4 + kotlinx-coroutines-test + MockWebServer (JVM unit and API contract
tests); Compose UI tests (instrumented); Node.js built-in test runner for `apps-script/Logic.gs`

**Target Platform**: Android 8.0+ (minSdk 26), targetSdk/compileSdk = latest stable; devices
with Google Play services

**Project Type**: mobile-app (Android) + attached Apps Script (no custom backend)

**Performance Goals**: First launch to main screen < 1 min, both automation steps < 3 min
(SC-001); structure check is one API read

**Constraints**: Online required for setup only; minimal OAuth scopes (`drive.file`,
`script.projects`, `script.deployments`); never delete user files or data (FR-010); RU/EN UI

**Scale/Scope**: One user per install, one spreadsheet, ~4 screens/dialogs, ~10 REST endpoints

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Check | Result |
|-----------|-------|--------|
| I. Native Android App | Kotlin + Jetpack Compose; no cross-platform UI; Android conventions (Credential Manager, Custom Tabs, system back, resources-based localization) | PASS |
| II. Test-First | Unit and contract tests are written before each component (validator, state machine, API clients, naming, status); Compose UI tests for screens; `Logic.gs` covered by `node --test`; OAuth and real Google flows covered by the manual [quickstart](quickstart.md) | PASS |
| III. Hybrid Data Architecture | Sheet is the source of truth; phone keeps only account + cache. Offline logging is not part of this feature (setup requires network, per spec). **Conflict resolution for this feature**: the app writes to the sheet only during setup (create, backup rename, developer metadata) and never edits user data; when the sheet and the phone cache disagree, the sheet wins and the cache is rebuilt | PASS |
| IV. Maximize Use of Google Services | Only Google APIs (Drive, Sheets, Apps Script); backend logic is an Apps Script attached to the user's own spreadsheet; no custom server | PASS |
| Technology Constraints | Kotlin; each new dependency justified below | PASS |
| Workflow & Quality Gates | Spec Kit flow followed; this table is the Constitution Check | PASS |

**Dependency justification** (Technology Constraints):

| Dependency | Why needed |
|------------|-----------|
| Credential Manager + googleid | Google's supported account picker (R2) |
| Play services Auth (Authorization API) | OAuth access tokens for chosen scopes (R3) |
| OkHttp + kotlinx.serialization | Small, testable REST clients for ~10 calls (R13) |
| DataStore | Persist selected account and setup cache (R13) |
| Coil | Load the account photo for the avatar |
| AndroidX Browser | Custom Tabs for the two one-time Google pages (R14) |
| MockWebServer (test only) | API contract tests |

**Post-design re-check (after Phase 1)**: PASS, no new violations. The two-step browser setup
and sensitive Apps Script scopes are product consequences recorded in the spec (clarification
revised 2026-10-03), not constitution deviations.

## Project Structure

### Documentation (this feature)

```text
specs/001-google-account-setup/
├── plan.md              # This file
├── research.md          # Phase 0: decisions R1–R14
├── data-model.md        # Phase 1: local data, status, setup state machine
├── quickstart.md        # Phase 1: automated + manual validation
├── contracts/
│   ├── spreadsheet.md   # Spreadsheet layout + developer metadata
│   ├── google-apis.md   # REST calls and error mapping
│   └── apps-script.md   # Attached script interface
└── tasks.md             # Phase 2 (/speckit-tasks, not created here)
```

### Source Code (repository root)

```text
settings.gradle.kts
build.gradle.kts
gradle/libs.versions.toml
app/
├── build.gradle.kts                  # copies ../apps-script/*.gs|json|html into assets
└── src/
    ├── main/
    │   ├── AndroidManifest.xml
    │   ├── java/com/workoutrec/
    │   │   ├── WorkoutRecApplication.kt     # creates AppContainer
    │   │   ├── AppContainer.kt              # manual DI
    │   │   ├── MainActivity.kt
    │   │   ├── auth/                        # AccountPicker, ApiAuthorizer, AccountRepository
    │   │   ├── google/                      # DriveClient, SheetsClient, ScriptClient, ApiError
    │   │   ├── spreadsheet/                 # ReferenceStructure, StructureValidator,
    │   │   │                                #   SpreadsheetSetupService, BackupNamer
    │   │   ├── automation/                  # ScriptInstaller, AutomationStatusEvaluator
    │   │   ├── setup/                       # SetupState, SetupViewModel, setup screens/dialogs
    │   │   ├── home/                        # MainScreen, AccountAvatar, AccountMenu, reminder
    │   │   └── data/                        # SettingsStore (DataStore)
    │   └── res/
    │       ├── values/strings.xml           # English
    │       └── values-ru/strings.xml        # Russian
    ├── test/java/com/workoutrec/            # JVM unit + MockWebServer contract tests
    │   └── resources/fixtures/              # recorded Google API responses
    └── androidTest/java/com/workoutrec/     # Compose UI tests
apps-script/
├── appsscript.json
├── Code.gs
├── Logic.gs
├── Enable.html
└── tests/logic.test.mjs                     # node --test
```

**Structure Decision**: A single Android Gradle project at the repository root with one `app`
module, plus `apps-script/` holding the script that the app uploads to each user's spreadsheet.
`Config.gs` is generated at upload time, not stored in the repo. No backend directory
(constitution Principle IV).

## Implementation order and risks

1. **Spike first (blocks the rest of US2 automation)**: verify research R8/R9 with a real
   account: `projects.create` with `parentId` on a `drive.file` spreadsheet, `updateContent`,
   version, web app deployment, and `doGet` installing the trigger, using only the three
   scopes. If extra access is required, stop and ask the user (it would change FR-002/Q1).
2. US1 (account picker, avatar, persistence, account menu).
3. US2 core (find, create, validate, rewrite with backup), then script attach + two-step guide
   + status reminders.
4. US3 (switch account, sign out).
5. RU/EN strings throughout; quickstart run on a device.

| Risk | Impact | Mitigation |
|------|--------|------------|
| Apps Script API refuses attach/deploy with these scopes | Automation cannot be installed | Spike in step 1; fallback discussed with the user before changing scopes |
| `spreadsheets.create` rejects some parts (e.g., validation or metadata at create time) | Two calls instead of one | Immediate `batchUpdate` fallback; FindingSpreadsheet recovery covers interruption |
| Google's "unverified app" screen confuses the user | Step 2 abandoned | Guide shows exactly which links to tap, in RU/EN |
| Time-zone or locale differences | Wrong day grouping or number format | Spreadsheet time zone set from the phone at creation; `ru_RU` locale fixed |

## Complexity Tracking

No constitution violations; nothing to justify.
