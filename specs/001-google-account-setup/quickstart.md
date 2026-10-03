# Quickstart: validating Google Account Setup

**Feature**: `001-google-account-setup` | **Plan**: [plan.md](plan.md)

## Prerequisites

- Android Studio (current stable) with an Android 8.0+ (API 26+) device or emulator **with
  Google Play** and at least two Google accounts signed in.
- JDK 17+, Node.js 20+ (for script tests).
- A Google Cloud project set up per [research.md](research.md) R12:
  - APIs enabled: Google Drive API, Google Sheets API, Apps Script API.
  - OAuth consent screen: External, Testing, your accounts added as test users, scopes
    `drive.file`, `script.projects`, `script.deployments`.
  - OAuth clients: Android (package `com.workoutrec`, debug SHA-1 from
    `./gradlew signingReport`) and Web (copy its client ID).
- `local.properties` contains `google.webClientId=<web client id>`.

## Automated checks

```bash
./gradlew testDebugUnitTest          # validator, state machine, naming, contract tests (MockWebServer)
./gradlew connectedDebugAndroidTest  # Compose UI tests (avatar, rewrite dialog, reminders, RU/EN)
node --test apps-script/tests        # Logic.gs + Code.gs (with fake Google services)
```

All three must pass before merge (constitution, Development Workflow).

## Manual end-to-end scenarios (real Google account)

Run on a device. "Account X" has never used the app; delete any app-created
`workout_rec_database_*` files from its Drive first.

| # | Steps | Expected (spec reference) |
|---|-------|---------------------------|
| 1 | Install, launch (start a stopwatch), pick account X, grant access | Account chooser first; main screen shows X's photo or initials top-right in under 1 min (US1, FR-001/003, SC-001) |
| 2 | Continue setup with Apps Script API setting **off** for X | Step 1 guide; Custom Tab opens `script.google.com/home/usersettings`; switch it on, return → script gets attached (US2 #6) |
| 3 | Step 2: "Enable automation" page opens; approve (Advanced → Go to workout_rec_automation) | Page says automation is on; back in app: confirmation, main screen, no reminder; rows 2–3 together take under 3 min (US2 #7–8, SC-001) |
| 4 | Open Drive on the web | Exactly one `workout_rec_database_`, six tabs in order, headers, `rec` drop-down, `rec!A2` formula, both highlight rules, dates `dd.MM.yyyy` ([contracts/spreadsheet.md](contracts/spreadsheet.md)) |
| 5 | In the web UI, type an exercise in `log!B5` with B2:B4 empty | B2:B4 filled with it, A2:A5 get the date-time, `rec!A1` shows it (automation table) |
| 6 | Wait for the daily run (or run `dailyJob` once from the script editor) | `workout` gets one 3-value row per new day; `balance!A1` = paid − counted workouts |
| 6a | In the web UI: Extensions → Apps Script → select `logMetadata` → Run; open Execution log | Lines for `workout_rec.structure_version`, `script_id`, `enable_url`, `automation_enabled_at`, `last_daily_run` (after step 6); spreadsheet unchanged |
| 7 | Close and relaunch the app | Main screen directly, same account, no prompt (FR-004, SC-004) |
| 8 | Uninstall, reinstall, pick X | Existing spreadsheet reused, no new file (US2 #3, SC-003) |
| 9 | In the web UI rename tab `money` to `pay`; relaunch → pick X again via "switch account" | Rewrite question; "No" → account chooser, nothing changed; repeat, "Yes" → old file renamed `workout_rec_database_backup_<today>`, new file created (FR-008, Q2) |
| 10 | Airplane mode on a fresh install | "Internet connection needed" with Retry (FR-011) |
| 11 | Phone language Russian | All app texts in Russian; spreadsheet names and headers unchanged (FR-014) |
| 12 | Account menu → Sign out → confirm | Account chooser shown (FR-012) |
| 13 | Revoke the script's access in Google Account → Security → third-party access; wait > 48 h (or edit `last_daily_run` metadata) | App shows "automation stopped" reminder (FR-015) |

Record results in the PR description.
