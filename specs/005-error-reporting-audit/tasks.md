---

description: "Task list for Report Every Handled Error to AppTracer"
---

# Tasks: Report Every Handled Error to AppTracer

**Input**: Design documents from `/specs/005-error-reporting-audit/`

**Prerequisites**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md),
[data-model.md](data-model.md), [contracts/issues.md](contracts/issues.md),
[quickstart.md](quickstart.md)

**Tests**: Required. Spec FR-010 and Constitution II (Test-First) and V (each new error path has
a test). Every test task is written first and must be seen failing before its implementation
task.

**Organization**: by user story (spec.md): US1 failures that vanish today (P1), US2 errors the
user sees (P2), US3 fallbacks on bad data (P3), US4 the catch-site guard (P3).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: can run in parallel (different files, no dependency on unfinished tasks)
- **[Story]**: user story from spec.md

## Path Conventions

Single Android module. Abbreviations used below:
`main/` = `app/src/main/java/com/workoutrec/`, `test/` = `app/src/test/java/com/workoutrec/`,
`testres/` = `app/src/test/resources/`. Reports in tests are checked with
`test/fakes/FakeMonitoring.kt` (`nonFatals`: issue, error, details).

---

## Phase 1: Setup

**Purpose**: A known-good baseline.

- [X] T001 Run `./gradlew :app:testDebugUnitTest :app:lintDebug` on the branch before any change and note the result in this file under T001 (expected: green; `LargeLogTest` is instrumented and not part of this run). **Result 2026-10-22**: green (unit tests and lint)

---

## Phase 2: Foundational (blocking all stories)

**Purpose**: Issue kinds, the no-connection rule, the limiter budgets, and `BadValue`
(contracts/issues.md, research R1–R3, R5).

**⚠️ CRITICAL**: no story work starts before this phase is done.

- [X] T002 [P] Write failing `test/monitoring/NonFatalIssueCatalogTest.kt`: every `NonFatalIssue` has a unique kebab-case key; the 5 existing keys (`sync-failed`, `sync-timeout`, `spreadsheet-mismatch`, `token-failed`, `diagnostics-test`) are unchanged; the 12 new values have exactly the keys and severities in contracts/issues.md ("only `NO_CONNECTION` and `DIAGNOSTICS_TEST` are INFO")
- [X] T003 [P] Write failing `test/monitoring/ErrorReportsTest.kt` (also: `ApiError.Unexpected(400, "Invalid value 'Bench press' at …")` with Google status `INVALID_ARGUMENT` is reported with message `HTTP 400 INVALID_ARGUMENT`, and no message in the reported error's cause chain contains `Bench press`; research R10) for `Monitoring.failure(issue, error, operation, details)`: `ApiError.Offline`, `ApiError.ServiceUnavailable(503)` and an `IOException` → one `NO_CONNECTION` report with details `operation=<operation>`; `ApiError.Unexpected`, `MissingTabException`, `IllegalStateException` → one report of `issue` with details `operation=<operation>` or `operation=<operation>; <details>`; and `BadValue("sheet-time-zone", cause)` has message `Unreadable sheet-time-zone` and keeps the cause (data-model.md "Bad value")
- [X] T004 [P] Extend `test/monitoring/NonFatalLimiterTest.kt` (failing): 5 info reports of `NO_CONNECTION` spread 30 min apart are allowed and the 6th is refused (`MAX_INFO_PER_DAY = 5`); after 5 info reports, 20 error/warning reports of different issues are still allowed and the 21st refused (`MAX_PER_DAY = 20` counts only ERROR and WARNING); the 30-min gap per issue is unchanged; both counts reset the next day
- [X] T005 [P] Write failing `test/monitoring/TracerMonitoringTest.kt` with a new `send` seam (`(IssueSeverity, Throwable, String) -> Unit`, default calling `TracerCrashReport.report`): with `isEnabled = { false }`, `nonFatal` for every `NonFatalIssue` value calls `send` 0 times and does not consume the limiter (SC-005); with it on, one call per allowed report; with `send` throwing `RuntimeException`, `nonFatal` returns normally (FR-009, edge case "Reporting itself fails"); keep the severity mapping inside the default `send` so the test touches no SDK class
- [X] T006 Add the 12 values to `NonFatalIssue` in `main/monitoring/Monitoring.kt` exactly as contracts/issues.md (keys: `money-refresh-failed`, `setup-step-failed`, `setup-check-failed`, `sign-in-failed`, `authorization-failed`, `sync-unexpected`, `sign-out-cleanup-failed`, `sync-log-failed`, `spreadsheet-create-fallback`, `bad-sheet-data`, `bad-saved-state`, `no-connection`); T002 green
- [X] T007 Create `main/monitoring/ErrorReports.kt`: `Throwable.isNoConnection()` (`ApiError.Offline`, `ApiError.ServiceUnavailable`, `IOException`), `Monitoring.failure(...)` per contracts/issues.md "No-connection rule" sending `reportable(e)`, `reportable(e)` + `ReportedApiError(code, status)` (research R10; `ApiError` keeps Google's error status), and `class BadValue(val value: String, cause: Throwable? = null) : Exception("Unreadable $value", cause)`; T003 green
- [X] T008 Give `main/monitoring/NonFatalLimiter.kt` the separate info budget (`infoToday`, `MAX_INFO_PER_DAY = 5`; `sentToday` counts only ERROR and WARNING), and add the `send` seam to `main/monitoring/TracerMonitoring.kt`, called inside `safely`; T004 and T005 green

**Checkpoint**: the reporting building blocks exist; nothing calls them yet.

---

## Phase 3: User Story 1 — Failures that vanish today become visible (Priority: P1) 🎯 MVP

**Goal**: money refresh, the setup check on app start, unexpected failures around a sync run,
sign-out cleanup and the sync log report to AppTracer, with no change for the user.

**Independent Test**: force each failure in JVM tests; exactly one report with the expected
issue and details; the existing behavior (messages, fallbacks, pending changes) is unchanged.

### Tests for User Story 1 (write first, see them fail)

- [X] T009 [P] [US1] Extend `test/money/MoneyRefresherTest.kt` with a `FakeMonitoring`: missing money tab → `moneyFailed(MISSING_TAB)` as today and one `MONEY_REFRESH_FAILED` with details `operation=money-refresh; reason=missing-tab`; `ApiError.Unexpected(500, …)` → `MONEY_REFRESH_FAILED`, `reason=other`; `ApiError.Offline` → `moneyFailed(NETWORK)` and one `NO_CONNECTION`, `operation=money-refresh`; no sheet info → `moneyFailed(OTHER)` and no report; a cancelled refresh → no report; a successful refresh → no report
- [X] T010 [P] [US1] Write failing `test/setup/SetupViewModelReportsTest.kt` (fakes as in `SetupViewModelSpreadsheetTest.kt`) for `refreshStatus`: `postAuth.refresh` throws `ApiError.Unexpected` → state and reminder unchanged and one `SETUP_CHECK_FAILED`, `operation=setup-check`; throws `ApiError.Offline` → one `NO_CONNECTION`, `operation=setup-check`; the view model's scope is cancelled during the check → no report (research R6)
- [X] T011 [P] [US1] Write failing `test/sync/SyncSchedulerTest.kt`: `runNow` with a `LogSync` whose store throws `IllegalStateException` before the run's own handling → returns `null` as today and one `SYNC_UNEXPECTED`, `operation=sync-request`; a `CancellationException` is rethrown with no report; a normal outcome → no report
- [X] T012 [P] [US1] Write failing `test/auth/SignOutCleanupTest.kt` for the new `main/auth/SignOutCleanup.kt` (`suspend operator fun invoke()` wrapping a `clear: suspend () -> Unit` and `Monitoring`): `clear` throws `IllegalStateException` → no throw and one `SIGN_OUT_CLEANUP_FAILED` with no details; `clear` throws `CancellationException` → rethrown, no report; success → no report
- [X] T013a [P] [US1] Extend `test/diag/DeviceStateTest.kt`: a context whose connectivity service throws `SecurityException` → returns `"unknown"` as today and one `SYNC_LOG_FAILED` with details `action=device-state`
- [X] T013 [P] [US1] Extend `test/diag/DiagnosticLogTest.kt`: a log file under a read-only or file-in-place-of-directory path → `write` does not throw, one `SYNC_LOG_FAILED` with details `action=write`, and `monitoring.log` is still called with the line; `read` failure → returns `""` and one `SYNC_LOG_FAILED`, `action=read`; the report is never written to the sync log itself (spec edge case)

### Implementation for User Story 1

- [X] T014 [US1] `main/money/MoneyRefresher.kt`: add `monitoring: Monitoring = NoMonitoring`; in the `Exception` catch call `monitoring.failure(MONEY_REFRESH_FAILED, e, "money-refresh", "reason=missing-tab")` for `MissingTabException`, `"reason=other"` otherwise; keep the `moneyFailed` mapping; T009 green
- [X] T015 [US1] `main/setup/SetupViewModel.kt`: add `monitoring: Monitoring = NoMonitoring`; replace `runCatching { postAuth.refresh(account) }.getOrElse { … }` in `refreshStatus` with `try`/`catch (e: CancellationException) { throw e }`/`catch (e: Exception) { monitoring.failure(SETUP_CHECK_FAILED, e, "setup-check"); RefreshResult.NoChange }`; T010 green
- [X] T016 [US1] `main/sync/SyncScheduler.kt`: add `monitoring: Monitoring = NoMonitoring`; split the catch into `catch (e: CancellationException) { throw e }` and `catch (e: Exception) { monitoring.nonFatal(SYNC_UNEXPECTED, e, "operation=sync-request"); null }`; T011 green
- [X] T017 [US1] Create `main/auth/SignOutCleanup.kt` (T012); in `main/AppContainer.kt` replace the `runCatching { CredentialManager…clearCredentialState(…) }` with `SignOutCleanup(clear = { CredentialManager.create(appContext).clearCredentialState(ClearCredentialStateRequest()) }, monitoring)`; T012 green
- [X] T018 [US1] `main/diag/DiagnosticLog.kt`: in both `IOException` catches call `monitoring.nonFatal(SYNC_LOG_FAILED, e, "action=write|read")` directly (never `write`); T013 green
- [X] T018a [US1] `main/diag/DeviceState.kt`: take `monitoring: Monitoring = NoMonitoring` and report `SYNC_LOG_FAILED` (`action=device-state`) in the `SecurityException` catch; its construction site passes `monitoring`; T013a green
- [X] T019 [US1] Wire `monitoring`: `main/AppContainer.kt` into `MoneyRefresher(…)` and `SyncScheduler(…)`; `main/MainActivity.kt` line ~118 `SetupViewModel(container.accountRepository, container.spreadsheetSetupFlow, container.monitoring)`; `./gradlew :app:assembleDebug :app:testDebugUnitTest` green

**Checkpoint**: US1 complete; shippable on its own.

---

## Phase 4: User Story 2 — Errors the user sees are reported (Priority: P2)

**Goal**: setup step errors, sign-in failures and authorization failures reach the console;
no-connection failures of sync and the token request move to `NO_CONNECTION` (FR-011).

**Independent Test**: force each failure in JVM tests; one report with the step or type; the
same error state as before; cancel → no report.

### Tests for User Story 2 (write first, see them fail)

- [X] T020 [P] [US2] Extend `test/setup/SetupViewModelSpreadsheetTest.kt` (or the flow's test) with a `FakeMonitoring` passed to `SpreadsheetSetupFlow`: for each `SetupStep` (`Finding`, `Creating`, `Checking`, `Renaming`, `AttachingScript`) a fake throwing `ApiError.Unexpected` → `SetupState.Error(step, e)` as today and one `SETUP_STEP_FAILED` with details `operation=setup; step=<Step>`; `ApiError.Offline` → one `NO_CONNECTION`, `operation=setup`; `ApiError.AppsScriptApiDisabled` while attaching → `NeedsApiSetting` and no report
- [X] T021 [P] [US2] Extend `test/setup/SetupViewModelReportsTest.kt`: picker returns `PickResult.Failed(type = "TYPE_NO_CREDENTIAL")` in `chooseAccount` → same `SignedOut(AccountRequired)` as today, and in `switchAccount` → previous state kept; each sends one `SIGN_IN_FAILED` with details `type=TYPE_NO_CREDENTIAL`; `PickResult.Cancelled` in either → no report; authorization outcome `Denied`/`Failed` with status 10 → one `AUTHORIZATION_FAILED`, details `operation=authorization; outcome=<type>; status=10`; outcome `Unavailable` → one `NO_CONNECTION`, `operation=authorization`; consent cancelled → no report
- [X] T022 [P] [US2] Update `test/sync/LogSyncErrorsTest.kt` (red first): `ApiError.Offline` and `ApiError.ServiceUnavailable` during a run → phase `Failing(NETWORK)` as today and one `NO_CONNECTION`, `operation=sync` (no `SYNC_FAILED`); `ApiError.Unexpected` and `MissingTabException` → `SYNC_FAILED` with details `operation=sync`; timeout → `SYNC_TIMEOUT` unchanged
- [X] T023 [P] [US2] Update `test/auth/AccountRepositoryTest.kt` (red first): authorize returns no answer within the timeout, or `AuthOutcome.Unavailable` → throws `ApiError.Offline` as today and one `NO_CONNECTION`, `operation=token` (no `TOKEN_FAILED`); `Denied` and other failures → `TOKEN_FAILED` as today

### Implementation for User Story 2

- [X] T024 [US2] `main/setup/SpreadsheetSetupFlow.kt` `guarded`: before returning `SetupState.Error(step, e)` call `monitoring.failure(SETUP_STEP_FAILED, e, "setup", "step=${step.name}")`; T020 green
- [X] T025 [US2] `main/auth/AccountPicker.kt`: `PickResult.Failed` carries `type: String` (the `GetCredentialException.type`, or `"unexpected-credential"` for the wrong credential type) instead of free text, keeping what the UI shows today; `main/auth/ApiAuthorizer.kt`/`AuthorizationMapper`: the failure `AuthOutcome` keeps the Google status code (`statusCode: Int?`)
- [X] T026 [US2] `main/setup/SetupViewModel.kt`: report `SIGN_IN_FAILED` (`type=…`) on `PickResult.Failed` in both `chooseAccount` (`SetupViewModel.kt:58`) and `switchAccount` (`:108`), splitting the shared `Cancelled, is Failed ->` branches; in `handleAuthorization` report `AUTHORIZATION_FAILED` (`operation=authorization; outcome=<simple class name>; status=<code>`) for failure outcomes and `NO_CONNECTION` (`operation=authorization`) for `Unavailable`; nothing for cancel; T021 green
- [X] T027 [US2] `main/sync/LogSync.kt`: in the `Exception` catch replace `monitoring.nonFatal(SYNC_FAILED, e)` with `monitoring.failure(SYNC_FAILED, e, "sync")`; T022 green
- [X] T028 [US2] `main/auth/AccountRepository.kt` `accessToken`: no answer or `Unavailable` → `monitoring.nonFatal(NO_CONNECTION, TokenRequestFailed(failure), "operation=token")`; other failures → `TOKEN_FAILED` as today; T023 green

**Note (T025/T026)**: the status code travels as a new `AuthOutcome.Failed(statusCode)` (and
`AuthDecision.Failed`), handled like `Denied` everywhere (same screens, account forgotten as
before). `Denied` now means a refusal or a cancelled consent (status 16) and is not reported.
Authorization on launch reports too (`SetupViewModel.start`).

**Checkpoint**: US1 + US2 complete.

---

## Phase 5: User Story 3 — Fallbacks on bad data are reported as warnings (Priority: P3)

**Goal**: unreadable sheet time zone or automation timestamp, unreadable saved state, and the
spreadsheet create fallback send warnings; the fallback results are unchanged.

**Independent Test**: feed each fallback bad input; same result as before plus one warning.

### Tests for User Story 3 (write first, see them fail)

- [X] T029 [P] [US3] Extend `test/sync/LogSyncErrorsTest.kt` (or `SheetInfoRefreshTest.kt`): sheet time zone `"Mars/Base"` → rows read in UTC as today and one `BAD_SHEET_DATA`, details `value=sheet-time-zone`, error `BadValue` whose message does not contain `Mars/Base`
- [X] T030 [P] [US3] Extend `test/automation/AutomationStatusEvaluatorTest.kt`: `evaluate(metadata with LAST_DAILY_RUN = "yesterday", now, onBadValue)` → same status as today and `onBadValue("automation-timestamp")` called once; valid timestamps → not called; and extend `test/money/MoneyRefresherTest.kt`: an unreadable last-run timestamp → `lastDailyRun = null` as today and one `BAD_SHEET_DATA`, `value=automation-timestamp`
- [X] T031 [P] [US3] Write failing `test/sync/SyncPhaseCodecTest.kt` for the extracted `main/sync/SyncPhaseCodec.kt` (`encode`/`decode(value, onBadValue)` moved out of `DataStoreSyncStatusStore`): `"failing:BOGUS"` → `Failing(OTHER)` as today and `onBadValue("sync-phase")` once; all valid values round-trip with no call
- [X] T032 [P] [US3] Write failing `test/workout/data/MoneySummaryMappingTest.kt`: `MoneySummaryEntity(automation = "Bogus", error = "BOGUS", …).toDomain(onBadValue)` → `ScriptMissing` and `MoneyError.OTHER` as today and `onBadValue` called with `automation-status` and `money-error`; valid values → no call
- [X] T033 [P] [US3] Extend `test/spreadsheet/SpreadsheetSetupServiceTest.kt`: full create answers 400 → minimal create + batchUpdate as today and one `SPREADSHEET_CREATE_FALLBACK` with details `code=400`; other errors → rethrown, no report from the service; `exists` with a `NotFound` reply → false and no report

### Implementation for User Story 3

- [X] T034 [US3] `main/sync/LogSync.kt` `zoneOf`: in the `DateTimeException` catch call `monitoring.nonFatal(BAD_SHEET_DATA, BadValue("sheet-time-zone", e), "value=sheet-time-zone")`; T029 green
- [X] T035 [US3] `main/automation/AutomationStatus.kt`: `evaluate(metadata, now, onBadValue: (String) -> Unit = {})` and `parse` with `try`/`catch (e: DateTimeParseException)` calling `onBadValue("automation-timestamp")`; `main/money/MoneyRefresher.kt` and the four `evaluate` calls in `main/setup/SpreadsheetSetupFlow.kt` pass `{ monitoring.nonFatal(BAD_SHEET_DATA, BadValue(it), "value=$it") }`; MoneyRefresher's own last-run parse uses the same callback; T030 green
- [X] T036 [US3] Create `main/sync/SyncPhaseCodec.kt` from `DataStoreSyncStatusStore.encode/decode` with `try`/`catch (e: IllegalArgumentException)` calling `onBadValue("sync-phase")`; `main/sync/SyncStatusStore.kt` takes `monitoring: Monitoring = NoMonitoring` and passes the callback; T031 green
- [X] T037 [US3] `main/workout/data/WorkoutRepository.kt`: `MoneySummaryEntity.toDomain(onBadValue: (String) -> Unit = {})` with `try`/`catch (e: IllegalArgumentException)` for both `valueOf` calls; the repository takes `monitoring: Monitoring = NoMonitoring` and passes `{ monitoring.nonFatal(BAD_SAVED_STATE, BadValue(it), "value=$it") }`; T032 green
- [X] T038 [US3] `main/spreadsheet/SpreadsheetSetupService.kt`: take `monitoring: Monitoring = NoMonitoring`; in the `ApiError.Unexpected` 400 branch report `SPREADSHEET_CREATE_FALLBACK` (`code=400`) before the fallback; T033 green
- [X] T039 [US3] `main/AppContainer.kt`: pass `monitoring` to `DataStoreSyncStatusStore`, `WorkoutRepository`, `SpreadsheetSetupService`; `./gradlew :app:assembleDebug :app:testDebugUnitTest` green

**Checkpoint**: every audited site reports or is a normal outcome.

---

## Phase 6: User Story 4 — New error handling cannot skip reporting (Priority: P3)

**Goal**: an automated check fails on any catch site that neither reports, rethrows, nor is on
the reviewed normal-outcome list (FR-012, research R7).

**Independent Test**: fixture sources with a bad site, a listed site and a stale entry give the
expected failures; the real app sources pass.

### Tests for User Story 4 (write first, see them fail)

- [X] T040 [P] [US4] Create fixtures in `testres/guard-fixture/` (`.kt.txt` files so they are not compiled): a catch that reports via `nonFatal(`; one via `.failure(`; one via `onBadValue(`; one ending in `throw e`; one ending in `throw Mapper.from(e)`; an unreported `catch (e: IOException) { }`; a `runCatching { }`; a catch block whose body contains a string `"{"` and a `// }` comment; a `flow.catch { }`; a `CoroutineExceptionHandler { _, _ -> }`; and a fixture list with one valid entry and one stale entry
- [X] T041 [US4] Write failing `test/monitoring/CatchSiteGuardTest.kt`: against the fixtures, the scanner finds exactly the expected sites with file and line; reporting and rethrowing sites pass; the unreported `IOException` and `runCatching` are reported as `<file>:<line> <type>` unless listed; the stale entry and a wrong `count` fail; brace matching ignores braces in strings and comments; then one test that runs the scanner over `app/src/main/java` with `testres/normal-outcomes.txt` and expects no problems (SC-001)

### Implementation for User Story 4

- [X] T042 [US4] Implement the scanner in `test/monitoring/CatchSites.kt` (test source set): find `catch\s*\(\s*\w+\s*:\s*([\w.]+)\s*\)`, `runCatching`, `.catch {` and `CoroutineExceptionHandler`, cut the block by brace matching that skips string literals, char literals and comments, classify as reports (`nonFatal(`, `.failure(`, `onBadValue(`), rethrows (last statement starts with `throw`), or unreported; parse the list format `path | caught type | count | reason` with `#` comments; a `reason` is required and non-empty
- [X] T043 [US4] Create `testres/normal-outcomes.txt` with the entries in data-model.md "Initial entries" (`auth/AccountPicker.kt` `GetCredentialCancellationException` and `GetCredentialException`, `auth/ApiAuthorizer.kt` `ApiException` ×2 and `IOException`, `log/PastWorkoutScreens.kt` `DateTimeParseException` ×2, `log/LogRoutes.kt` `DateTimeException`, `spreadsheet/SpreadsheetSetupService.kt` `ApiError.NotFound`, `setup/SpreadsheetSetupFlow.kt` `ApiError.AppsScriptApiDisabled`, `google/ApiError.kt` `runCatching`, `monitoring/TracerMonitoring.kt` `Throwable`), adjusting counts to what the scanner finds; add any site it finds that the audit missed only after deciding report vs. list for it (and record the decision in research.md R4); T041 green

**Note (T043)**: the scan found no site the audit missed. One new site came from this feature:
`google/ApiError.kt` `IllegalArgumentException` (reading Google's error status from a body that is
not JSON), listed as a normal outcome. Probe (quickstart #2): an added `catch (e: Exception) { 0 }`
in `DeviceState.kt` failed the guard with `diag/DeviceState.kt:81 Exception`.

**Checkpoint**: all stories complete; the guard runs on every PR in the unit test job.

---

## Phase 7: Polish & Cross-Cutting Concerns

- [X] T044 [P] Write `test/monitoring/ReportPrivacyTest.kt` (SC-003): run the US1–US3 failure scenarios with fixtures containing the exercise name `Bench press`, sum `1234.50`, email `tester@example.com` and token `ya29.test-token`; add a MockWebServer error reply that repeats the exercise name and sum for sync, money refresh and setup; assert no report's `details` or error messages (whole cause chain) contain any of them
- [X] T045 [P] Update feature 003 `specs/003-app-monitoring-tracer/contracts/monitoring.md` "Events" to point to `specs/005-error-reporting-audit/contracts/issues.md` for the new keys and the `SYNC_FAILED`/`TOKEN_FAILED` change
- [X] T046 [P] README.md "Monitoring" (or the nearest section): one paragraph on the reporting rule (report through `Monitoring.failure`/`nonFatal`, or list the site in `app/src/test/resources/normal-outcomes.txt` with a reason)
- [ ] T047 Run `./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug` locally; open the PR; CI green including `connectedDebugAndroidTest` unchanged (SC-004); merge per the standing rule
- [ ] T048 Device quickstart #8–#12 on a build from master after the Play internal release: money tab renamed → `money-refresh-failed`; offline → one `no-connection`; cancel picker → nothing; test error → `diagnostics-test`; switch off → nothing (SC-006). Record results in `specs/005-error-reporting-audit/quickstart.md`

---

## Dependencies & Execution Order

- **Phase 1 → Phase 2** → US1, US2, US3 (independent of each other once Phase 2 is done; US2
  T026 and US1 T015 both edit `SetupViewModel.kt`, so do them in order) → **US4** (the guard is
  green only when US1–US3 are done) → Polish.
- Within each story: tests (red) → implementation (green) → wiring in `AppContainer.kt`.
- `AppContainer.kt` is edited in T017, T019, T039: sequential.
- `LogSync.kt`: T027 then T034. `MoneyRefresher.kt`: T014 then T035. `SetupViewModel.kt`: T015,
  T026. `LogSyncErrorsTest.kt`: T022 then T029.

## Parallel Examples

```text
Phase 2:  T002, T003, T004, T005 together → T006, T007, T008
US1:      T009, T010, T011, T012, T013 together → T014…T018 (different files) → T019
US2:      T020, T021, T022, T023 together → T024, T025, T027, T028 together → T026
US3:      T029…T033 together → T034…T038 (different files) → T039
US4:      T040 → T041 → T042 → T043
Polish:   T044, T045, T046 together → T047 → T048
```

## Implementation Strategy

1. **MVP = Phase 2 + US1**: the failures nobody can see today (the new Money tab first) reach
   the console. One PR, shippable.
2. **US2** in a second PR: user-visible errors, and the no-connection split for sync and token.
3. **US3 + US4** in a third PR: bad-data warnings, then the guard locks the result in.
4. Polish with the last PR; device quickstart after the Play internal release.
