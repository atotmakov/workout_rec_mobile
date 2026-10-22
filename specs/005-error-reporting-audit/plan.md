# Implementation Plan: Report Every Handled Error to AppTracer

**Branch**: `005-error-reporting-audit` | **Date**: 2026-10-22 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/005-error-reporting-audit/spec.md`

## Summary

Every handled error now reaches AppTracer through feature 003's `Monitoring` interface. Twelve
issue kinds are added. A helper, `Monitoring.failure`, sends no-connection failures as one info
issue (`no-connection`) and everything else under the caller's issue. Info reports get their own
daily budget so they cannot use up the error budget. Each catch site from the audit reports in
the one place that decides what the user sees. Pure parsers report bad values through a
callback, and `runCatching` gives way to `try`/`catch` that lets cancellation through. A JVM
test reads the app's source and fails on any catch site that neither reports, rethrows, nor
appears on a reviewed list of normal outcomes. The user sees no change. Details:
[research.md](research.md).

## Technical Context

**Language/Version**: Kotlin 2.1.0, AGP 8.7.3, compileSdk/targetSdk 35 (unchanged)

**Primary Dependencies**: existing only: Tracer SDK behind `Monitoring` (feature 003). No new
dependency.

**Storage**: none changed (no Room, DataStore or sheet changes)

**Testing**: JUnit 4 + coroutines-test + MockWebServer with `FakeMonitoring` (JVM); the guard
is a JVM test over `app/src/main/java`; existing UI tests on the CI emulator unchanged

**Target Platform**: Android 8.0+ (minSdk 26)

**Project Type**: mobile-app (Android); no Apps Script change

**Performance Goals**: reporting adds no blocking work; `nonFatal` stays fire-and-forget

**Constraints**: no user-visible change (FR-008); no sheet data, email or tokens in reports
(FR-007); Send diagnostics and rate limits respected (FR-009)

**Scale/Scope**: about 35 catch sites; about 20 report or change, about 10 listed as normal
outcomes; 12 new issue kinds

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Check | Result |
|-----------|-------|--------|
| I. Native Android App | Kotlin only; no UI change | PASS |
| II. Test-First | Failing test first for each reporting site, the helper, the limiter budgets and the guard (R8); `LogSyncErrorsTest` and `AccountRepositoryTest` updated red first for the two changed reports (R9) | PASS |
| III. Hybrid Data Architecture | No data or sync change; offline behavior unchanged; conflict notices stay normal outcomes (FR-004) | PASS |
| IV. Maximize Use of Google Services | No backend; AppTracer is the existing third-party reporting service | PASS |
| V. Error Reporting to AppTracer | This feature implements it: every catch site reports through `Monitoring` (`nonFatal`/`failure`) with stable keys, or is a listed normal outcome; no direct Tracer calls; privacy rule in details (R3); Send diagnostics honored; a test for every new error path; the guard keeps it true | PASS |
| Technology Constraints | No new dependency | PASS |
| Workflow & Quality Gates | Spec Kit flow; the guard runs in the unit test job on every PR | PASS |

**Post-design re-check (after Phase 1)**: PASS. Every site in research R4 has one owner, so
nothing is reported twice. Details are built only from fixed values (contracts/issues.md), so
the privacy rule holds by construction.

## Project Structure

### Documentation (this feature)

```text
specs/005-error-reporting-audit/
├── plan.md              # This file
├── research.md          # Phase 0: R1–R9 (R4 = per-site decisions)
├── data-model.md        # Issue kinds, details, BadValue, normal-outcome list, limiter
├── quickstart.md        # Automated and device validation
├── contracts/
│   └── issues.md        # Issue catalog, no-connection rule, rate limits
├── checklists/requirements.md
└── tasks.md             # Phase 2 (/speckit-tasks)
```

### Source Code (repository root)

```text
app/src/main/java/com/workoutrec/
├── monitoring/
│   ├── Monitoring.kt                 # + 12 NonFatalIssue values
│   ├── ErrorReports.kt               # NEW: Monitoring.failure, isNoConnection, BadValue
│   └── NonFatalLimiter.kt            # separate info budget
├── AppContainer.kt                   # inject Monitoring; sign-out cleanup try/catch
├── auth/AccountPicker.kt             # SIGN_IN_FAILED
├── auth/AccountRepository.kt         # TOKEN_FAILED vs NO_CONNECTION
├── auth/ApiAuthorizer.kt             # failure outcome keeps the status code
├── automation/AutomationStatus.kt    # evaluate(..., onBadValue)
├── diag/DiagnosticLog.kt             # SYNC_LOG_FAILED
├── money/MoneyRefresher.kt           # MONEY_REFRESH_FAILED / NO_CONNECTION, bad timestamp
├── setup/SetupViewModel.kt           # SETUP_CHECK_FAILED, AUTHORIZATION_FAILED
├── setup/SpreadsheetSetupFlow.kt     # SETUP_STEP_FAILED
├── spreadsheet/SpreadsheetSetupService.kt # SPREADSHEET_CREATE_FALLBACK
├── sync/LogSync.kt                   # failure(), BAD_SHEET_DATA
├── sync/SyncScheduler.kt             # SYNC_UNEXPECTED
├── sync/SyncStatusStore.kt           # BAD_SAVED_STATE
└── workout/data/WorkoutRepository.kt # BAD_SAVED_STATE
app/src/test/java/com/workoutrec/
├── monitoring/{ErrorReportsTest, NonFatalLimiterTest, CatchSiteGuardTest, ReportPrivacyTest}.kt
└── {money,setup,auth,sync,diag,spreadsheet,automation,workout}/…ReportsTest.kt
app/src/test/resources/
├── normal-outcomes.txt               # reviewed list (data-model.md)
└── guard-fixture/…                   # sources for the guard's own tests
```

**Structure Decision**: Same single `app` module. The rule (`failure`, limiter) is plain
Kotlin in `monitoring/`. Call sites get `Monitoring` by constructor, as `LogSync` and
`AccountRepository` already do, and `AppContainer` wires it.

## Implementation order and risks

1. Issue kinds, `ErrorReports.failure`, limiter budgets (with tests).
2. The guard and the normal-outcome list, with the current unreported sites marked as the gap
   to close (the test is red until step 5).
3. US1: money refresh, setup check, sync scheduler, sign-out cleanup, sync log.
4. US2: setup steps, sign-in, authorization; sync and token no-connection change.
5. US3: bad sheet data, bad saved state, create fallback. The guard goes green.
6. Privacy test; quickstart on a device.

| Risk | Impact | Mitigation |
|------|--------|------------|
| A report is sent while the user is offline all day | Info budget used up | Separate info budget (R2); 30-min gap per issue |
| `BAD_SAVED_STATE` fires on every database read of a bad row | Flood | Per-issue gap and the daily budget; one report per 30 min |
| Brace matching in the guard misreads a string containing `{` | False result | Skip string and comment text while matching; fixture tests cover it |
| Changing `SYNC_FAILED` and `TOKEN_FAILED` hides offline cases in old console filters | Old dashboards change | Noted in the PR; the console groups `no-connection` separately |
| Injecting `Monitoring` into more constructors breaks tests and the gallery | Build errors | Default parameter `NoMonitoring`, as `LogSync` and `AccountRepository` do |

## Complexity Tracking

No constitution violations; nothing to justify.
