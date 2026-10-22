# Research: Report Every Handled Error to AppTracer

Feature: [spec.md](spec.md) · Date: 2026-10-22

The audit baseline is the table in the spec's Context section. Catch sites were found with
`catch (` and `runCatching` in `app/src/main` (debug source set excluded).

## R1. One reporting helper that sorts out no-connection failures

**Decision**: add `monitoring/ErrorReports.kt` with
`Monitoring.failure(issue, error, operation, details = null)`. If the error is a no-connection
failure (`ApiError.Offline`, `ApiError.ServiceUnavailable`, or an `IOException`), it sends
`NO_CONNECTION` (info) with details `operation=<operation>`. Otherwise it sends `issue`, with
details `operation=<operation>` followed by `details`. Call sites that never see network errors
(bad data, sync log, sign-out cleanup) call `nonFatal` directly.

**Rationale**: FR-011 needs the same rule in sync, money refresh, setup and authorization. One
function keeps it identical and is tested once. `phaseOf` in `LogSync` already uses the same two
`ApiError` types for `FailReason.NETWORK`.

**Alternatives**: classify in each catch block (four copies of one rule); classify inside
`TracerMonitoring` (it would have to know about `ApiError`, and `FakeMonitoring` would not apply
the rule in tests).

## R2. Info reports must not use up the error budget

**Decision**: `NonFatalLimiter` keeps the 30-minute gap per issue but gets a separate daily
budget for info-severity issues (`MAX_INFO_PER_DAY = 5`). The existing `MAX_PER_DAY = 20` then
counts only error and warning reports.

**Rationale**: a phone without connection for a day would send a `NO_CONNECTION` report every 30
minutes (about 16 in 8 hours) and use up nearly all of today's shared budget of 20, hiding real
errors. Spec edge case: "a repeating error cannot use up the service's daily limit".

**Alternatives**: one shared budget (the problem above); a longer gap for info only (still
shares the budget).

## R3. Issue kinds and details

**Decision**: add 12 values to `NonFatalIssue` (keys are stable and only ever added, per
contracts/monitoring.md from 003). The full catalog is in
[contracts/issues.md](contracts/issues.md). Details are short `key=value` pairs separated by
`; ` (for example `operation=money-refresh; reason=missing-tab`, `step=Creating`,
`value=sheet-time-zone`), and never carry sheet content, email or tokens (FR-007).

**Rationale**: the console groups by issue name; the details distinguish the cases within it.
Fixed keys and values are safe by construction: no free text from the sheet goes in.

## R4. Where each failure is reported (one place each)

**Decision**: report at the place that decides what the user sees (spec edge case "caught
twice"):

| Site | Reports | Change |
|------|---------|--------|
| `money/MoneyRefresher.refresh` catch | `MONEY_REFRESH_FAILED` (`reason=missing-tab\|other`) or `NO_CONNECTION` via R1 | inject `Monitoring` |
| `money/MoneyRefresher.refresh`, no sheet info | nothing: it happens before the first sync and is a normal state | listed |
| `setup/SpreadsheetSetupFlow.guarded` | `SETUP_STEP_FAILED` (`step=<SetupStep>`) or `NO_CONNECTION` | already has `Monitoring` |
| `setup/SetupViewModel.refreshStatus` | `SETUP_CHECK_FAILED` or `NO_CONNECTION` | `runCatching` → `try`/`catch` that rethrows cancellation (R6); inject `Monitoring` |
| `auth/AccountPicker` `GetCredentialException` | nothing there: it becomes `PickResult.Failed`, which keeps the exception type; `SetupViewModel` reports `SIGN_IN_FAILED` (`type=<exception type>`) | listed ("converted; caller reports"); testable on the JVM through the view model |
| `auth/AccountPicker` cancellation | nothing | listed |
| `auth/ApiAuthorizer` catches | nothing there: they turn the exception into an `AuthOutcome` and the caller reports | listed ("converted; caller reports"); the failure outcome keeps the Google status code for the report |
| `auth/AccountRepository.accessToken` | `TOKEN_FAILED` as today, except no answer or `Unavailable` → `NO_CONNECTION` (`operation=token`) | FR-011 |
| `setup/SetupViewModel.handleAuthorization` (setup authorization and consent result) | `AUTHORIZATION_FAILED` (`outcome=<type>; status=<code>`), or `NO_CONNECTION` for an unavailable outcome; a cancelled consent sends nothing | report in the view model |
| `sync/LogSync.run` `Exception` catch | `SYNC_FAILED` or `NO_CONNECTION` via R1 | FR-011 |
| `sync/LogSync.run` timeout | `SYNC_TIMEOUT` as today | none |
| `sync/SyncScheduler.runNow` | `SYNC_UNEXPECTED` | inject `Monitoring` |
| `AppContainer` sign-out `clearCredentialState` | `SIGN_OUT_CLEANUP_FAILED` | `runCatching` → `try`/`catch` |
| `diag/DeviceState.network` `SecurityException` | `SYNC_LOG_FAILED` (`action=device-state`); still returns "unknown" | give `DeviceState` the `Monitoring` (default `NoMonitoring`) |
| `diag/DiagnosticLog.write`/`read` | `SYNC_LOG_FAILED` (`action=write\|read`) through `monitoring.nonFatal`, never through `write` | already has `Monitoring` |
| `spreadsheet/SpreadsheetSetupService.create` 400 fallback | `SPREADSHEET_CREATE_FALLBACK` | inject `Monitoring` |
| `sync/LogSync.zoneOf` | `BAD_SHEET_DATA` (`value=sheet-time-zone`) | none |
| `log/LogRoutes` `SyncStatus.zone()` | nothing: same value, already reported by `LogSync.zoneOf`; it runs in composition | listed |
| `automation/AutomationStatusEvaluator.parse`, `MoneyRefresher` last-run parse | `BAD_SHEET_DATA` (`value=automation-timestamp`) | `evaluate(..., onBadValue)` callback, default no-op; callers with `Monitoring` pass it |
| `sync/SyncStatusStore.decode`, `WorkoutRepository` `MoneySummaryEntity.toDomain` | `BAD_SAVED_STATE` (`value=sync-phase\|automation-status\|money-error`) | inject `Monitoring` (store, repository) |
| `google/ApiErrorMapper.googleStatus` (new in R10) | nothing: a body that is not JSON has no status; the error is reported by its caller | listed |

**Alternatives**: report in `ApiAuthorizer` too (the token path would report twice, as
`AUTHORIZATION_FAILED` and `TOKEN_FAILED`).

## R5. Pure code reports through a callback

**Decision**: pure objects (`AutomationStatusEvaluator`, the `toDomain` mappers) get an
`onBadValue: (String) -> Unit = {}` parameter; the caller passes
`{ monitoring.nonFatal(BAD_..., BadValue(it), "value=$it") }`. `BadValue` is a small exception
type naming the value, never its content.

**Rationale**: keeps them pure and JVM-testable and follows the codebase's constructor and
parameter injection; there is no global monitoring object today and this feature adds none.

**Alternatives**: a process-wide reporter (a service locator, unlike the rest of the app);
returning a result type from each parser (larger change to every caller).

## R6. `runCatching` in app code

**Decision**: replace `runCatching` with `try`/`catch` at the sites that must report. The guard
(R7) fails on any `runCatching` in app code that is not on the normal-outcome list.

**Rationale**: `runCatching` also catches `CancellationException`, so a cancelled coroutine is
treated as a failure. In `SetupViewModel.refreshStatus` and the sign-out cleanup that would now
send a false report. It is also hard to tell from source text whether a `runCatching` chain
reports.

## R7. The catch-site guard (FR-012)

**Decision**: a JVM unit test, `monitoring/CatchSiteGuardTest`, reads every `.kt` file under
`app/src/main/java` (Gradle runs unit tests from the module directory) and finds:

- `catch (<name>: <Type>) { … }`: the block is cut out by brace matching;
- `runCatching`;
- `.catch {` (Flow) and `CoroutineExceptionHandler`, classified like `runCatching`: listed, or passing when they contain a reporting call.

A catch block passes when it contains `nonFatal(`, `.failure(` or `onBadValue(`, or when its last
statement is `throw` (a rethrow or conversion is not a handled error; spec assumption). Every
other site must match an entry in `app/src/test/resources/normal-outcomes.txt` by file path and
caught type (or `runCatching`). Entries that match no site also fail the test, so the list
cannot go stale. The failure message lists `file:line` and the caught type.

Format: one entry per line, `path | caught type | count | reason`, with `#` comments
([data-model.md](data-model.md)).

**Rationale**: no new dependency, and it runs in the existing `testDebugUnitTest` job on every
PR. Matching on file and type (not line numbers) survives unrelated edits.

**Alternatives**: a custom Android Lint or detekt rule (a new module or dependency, and slower to
change); a marker comment at each site (FR-012 asks for the list next to the check).

## R8. Tests

**Decision**: one JVM test per row of R4's table, using the existing `FakeMonitoring`, fake
APIs and MockWebServer, each checking exactly one report with the expected issue, severity and
details, or no report for normal outcomes (FR-010). Also tests for `ErrorReports.failure`, the
limiter budgets, the guard (a fixture source tree with a bad site, a listed site and a stale
entry), and a privacy test that checks report details against the sample sheet data, email and
token used in the fixtures (SC-003). `AccountPicker` and `ApiAuthorizer` wrap Android and Play
classes; their reporting is tested through the classes that use them, with a fake picker.

## R9. Existing behavior that changes

Only reporting changes; screens and fallbacks stay the same (FR-008). Two existing reports
change: `SYNC_FAILED` and `TOKEN_FAILED` no longer cover no-connection failures, which now go to
`NO_CONNECTION` (spec FR-003, FR-011). `LogSyncErrorsTest` and `AccountRepositoryTest` are
updated with failing tests first.

## R10. Google's error text stays out of reports

**Decision**: before sending, `reportable(e)` in `ErrorReports.kt` replaces an `ApiError` with
`ReportedApiError(code, status)`, whose message is `HTTP <code> <status>` (status: Google's error
status such as `INVALID_ARGUMENT`, or none). The original's stack trace is kept; its message is
not sent. `Monitoring.failure` and the remaining direct `nonFatal` calls for sync use it.

**Rationale**: a Google error reply can quote cell values (FR-007), and when it is not JSON the
whole raw body becomes the message (`ApiError.kt:44-50`). This also fixes the existing
`SYNC_FAILED` reports.

**Alternatives**: scrubbing known values from the text (needs the sheet's values at report time);
dropping the error entirely (loses the stack trace).
