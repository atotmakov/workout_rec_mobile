# Contract: Non-fatal Issue Catalog

Extends feature 003 `contracts/monitoring.md` "Events". Keys are stable: values are added,
never renamed or removed. Details are `key=value` pairs separated by `; `, built only from the
fixed values below. They never contain sheet content, the account email or tokens (spec FR-007).

## Existing (feature 003)

| Enum | Key | Severity | Change in 005 |
|------|-----|----------|---------------|
| `SYNC_FAILED` | `sync-failed` | ERROR | No-connection failures go to `NO_CONNECTION` (FR-011) |
| `SYNC_TIMEOUT` | `sync-timeout` | WARNING | none |
| `SPREADSHEET_MISMATCH` | `spreadsheet-mismatch` | WARNING | none |
| `TOKEN_FAILED` | `token-failed` | ERROR | No answer or `Unavailable` goes to `NO_CONNECTION` (`operation=token`) |
| `DIAGNOSTICS_TEST` | `diagnostics-test` | INFO | none |

## New (feature 005)

| Enum | Key | Severity | Details | Reported from (research R4) |
|------|-----|----------|---------|-----------------------------|
| `MONEY_REFRESH_FAILED` | `money-refresh-failed` | ERROR | `operation=money-refresh; reason=missing-tab\|other` | `MoneyRefresher.refresh` |
| `SETUP_STEP_FAILED` | `setup-step-failed` | ERROR | `operation=setup; step=Finding\|Creating\|Checking\|Renaming\|AttachingScript` | `SpreadsheetSetupFlow.guarded` |
| `SETUP_CHECK_FAILED` | `setup-check-failed` | WARNING | `operation=setup-check` | `SetupViewModel.refreshStatus` |
| `SIGN_IN_FAILED` | `sign-in-failed` | ERROR | `type=<GetCredentialException type>` | `SetupViewModel` on `PickResult.Failed` |
| `AUTHORIZATION_FAILED` | `authorization-failed` | ERROR | `operation=authorization; outcome=<AuthOutcome type>; status=<code>` | `SetupViewModel.handleAuthorization` |
| `SYNC_UNEXPECTED` | `sync-unexpected` | ERROR | `operation=sync-request` | `SyncScheduler.runNow` |
| `SIGN_OUT_CLEANUP_FAILED` | `sign-out-cleanup-failed` | WARNING | none | `AppContainer` sign-out cleanup |
| `SYNC_LOG_FAILED` | `sync-log-failed` | WARNING | `action=write\|read\|device-state` | `DiagnosticLog`, `DeviceState` |
| `SPREADSHEET_CREATE_FALLBACK` | `spreadsheet-create-fallback` | WARNING | `code=400` | `SpreadsheetSetupService.create` |
| `BAD_SHEET_DATA` | `bad-sheet-data` | WARNING | `value=sheet-time-zone\|automation-timestamp` | `LogSync.zoneOf`, `AutomationStatusEvaluator` callers |
| `BAD_SAVED_STATE` | `bad-saved-state` | WARNING | `value=sync-phase\|automation-status\|money-error` | `DataStoreSyncStatusStore.decode`, `WorkoutRepository` money summary |
| `NO_CONNECTION` | `no-connection` | INFO | `operation=sync\|money-refresh\|setup\|setup-check\|authorization\|token\|script-upgrade` | `Monitoring.failure` (R1), `AccountRepository`, `SetupViewModel` |
| `WORKOUT_TAB_MISSING` | `workout-tab-missing` | WARNING | `operation=sync` | `LogSync` (feature 006 R6): statuses wait while the spreadsheet has no `workout` tab |
| `SCRIPT_UPGRADE_FAILED` | `script-upgrade-failed` | WARNING | `operation=script-upgrade` | `SpreadsheetSetupFlow.refresh` (feature 006 R7) |

`ApiError` messages are replaced by `HTTP <code> <status>` before sending (research R10).

`<GetCredentialException type>` and `<AuthOutcome type>` are class or type names from the
platform or the app, not user data. `<code>` is a Google status code number.

## No-connection rule (`Monitoring.failure`)

| Error | Reported as |
|-------|-------------|
| `ApiError.Offline`, `ApiError.ServiceUnavailable`, any `IOException` | `NO_CONNECTION`, `operation=<operation>` |
| anything else | the caller's issue, `operation=<operation>` plus the caller's details |

## Rate limits (`NonFatalLimiter`, research R2)

| Rule | Value |
|------|-------|
| Gap between two reports of the same issue | 30 min (unchanged) |
| Error and warning reports per day | 20 (unchanged; info no longer counts) |
| Info reports per day | 5 (new) |
| Send diagnostics off | nothing sent (unchanged) |
