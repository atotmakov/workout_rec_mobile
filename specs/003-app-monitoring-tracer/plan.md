# Implementation Plan: App Monitoring with Tracer

**Branch**: `003-app-monitoring-tracer` | **Date**: 2026-10-14 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/003-app-monitoring-tracer/spec.md`

## Summary

The app reports crashes, freezes, handled errors, out-of-memory snapshots, its storage use and
profiles of key operations to Tracer (apptracer.ru), which shows them in its web console. The
Tracer SDK 1.4.0 is configured in the `Application`. The app's own code only talks to a small
`Monitoring` interface, so the decisions are unit-tested without the SDK: which errors to
report, how often, which runs to profile, and which log lines may leave the phone.

The existing diagnostic log keeps writing its file and also feeds Tracer's log buffer, which is
attached to every report. A **Send diagnostics** switch in the account menu (on by default) turns
everything off. Builds without the Tracer token, including the CI emulator job, run with Tracer
disabled. Details: [research.md](research.md).

## Technical Context

**Language/Version**: Kotlin 2.1.0, AGP 8.10.1, compileSdk/targetSdk 36, minSdk 26 (unchanged)

**Primary Dependencies**: existing — Compose Material 3, WorkManager 2.10.5, Room, DataStore,
OkHttp; new — Tracer SDK 1.4.0: Gradle plugin `ru.ok.tracer`, BOM `tracer-platform`, modules
`tracer-crash-report`, `tracer-heap-dumps`, `tracer-disk-usage`, `tracer-profiler-sampling`,
`tracer-profiler-systrace` (R1)

**Storage**: one `SharedPreferences` value (`monitoring/send_diagnostics`, R3); Tracer's own
on-device report store; nothing new in the Sheet or Room

**Testing**: JUnit 4 + kotlinx-coroutines-test on the JVM for the `Monitoring` logic with a fake
(gating, limiter, sampler, gate, guard, the reporting call sites); Compose UI tests on the CI
emulator for the switch and the test-actions dialog; the Tracer SDK itself is validated on the
phone ([quickstart.md](quickstart.md))

**Target Platform**: Android 8.0+ (minSdk 26) with Google Play services

**Project Type**: mobile-app (Android); no backend; no Apps Script change

**Performance Goals**: app start and sync at most 5% slower on average with monitoring on (SC-004);
monitoring work never runs on the main thread except the SDK's own handlers

**Constraints**: nothing sent with the switch off or without the token; no workout data, email or
token in reports (FR-013); a monitoring failure never fails a build or shows an error (FR-015)

**Scale/Scope**: one user plus a few internal testers; far below Tracer's daily limits (1 M
events, 500 snapshots, 500 profiles)

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Notes |
|---|---|---|
| I. Native Android (Kotlin) | ✅ | Kotlin; the Tracer SDK is a native Android library |
| II. Test-first | ✅ | Every app-side decision sits behind `Monitoring` and is unit-tested red→green; UI tests for the switch; the SDK calls are a thin adapter, validated on the device |
| III. Hybrid data | ✅ | Logging, sync and conflict handling are unchanged; monitoring only observes. No new sheet data, so no new conflict rules |
| IV. Google services, no custom backend | ✅ | Workout and spreadsheet data still go only through Google APIs. Tracer is a third-party diagnostics service, not a backend of ours (R12) |
| Tech constraints: new dependency justified | ✅ | Tracer SDK: requested by the user; covers crashes, ANR, leaks, disk use and profiling in one SDK; alternatives in R1 |
| Workflow: spec → plan → tasks → implement | ✅ | This plan |

Post-design re-check (after data-model and contracts): unchanged, all ✅.

## Project Structure

### Documentation (this feature)

```text
specs/003-app-monitoring-tracer/
├── plan.md              # This file
├── research.md          # R1–R12
├── data-model.md        # setting, interface, issues, limiter, profile targets, guard
├── quickstart.md        # device validation with the Tracer console
├── contracts/
│   └── monitoring.md    # environments, events, never-sent list, the switch UI
└── tasks.md             # /speckit-tasks
```

### Source Code (repository root)

```text
gradle/libs.versions.toml            # tracer = "1.4.0", plugin + BOM + modules
app/build.gradle.kts                 # plugin, tracer {} block (tokens, isDisabled, environment), resValues
.github/workflows/ci.yml             # TRACER_* secrets only for the "Build, lint, unit tests" job
docs/privacy.html                    # Diagnostics section (FR-014)

app/src/main/java/com/workoutrec/
├── WorkoutRecApplication.kt         # HasTracerConfiguration: list from MonitoringConfig; app-start profile
├── AppContainer.kt                  # builds Monitoring; passes it to DiagnosticLog, LogSync, setup, auth
├── monitoring/                      # new
│   ├── Monitoring.kt                # interface + NoMonitoring
│   ├── TracerMonitoring.kt          # the only file importing ru.ok.tracer (adapter)
│   ├── MonitoringConfig.kt          # switch → configuration list; deletes stored reports when off
│   ├── MonitoringSettings.kt        # SharedPreferences switch, startedWith
│   ├── NonFatalIssue.kt             # issue keys + severities
│   ├── NonFatalLimiter.kt
│   ├── ProfileTargets.kt            # targets, rates, durations; ProfileSampler; ProfileGate
│   ├── LogLineGuard.kt
│   └── DiagnosticsTestActions.kt    # crash, freeze, test error, snapshot, disk check, profile next start
├── diag/DiagnosticLog.kt            # also forwards guarded lines to Monitoring.log
├── sync/LogSync.kt                  # nonFatal sync-failed / sync-timeout; sync profile + sections
├── setup/SpreadsheetSetupFlow.kt    # nonFatal spreadsheet-mismatch
├── auth/AccountRepository.kt        # nonFatal token-failed
├── log/LogRoutes.kt                 # today-open profile
└── home/MainScreen.kt               # Send diagnostics switch + long-press test dialog

app/src/test/java/com/workoutrec/monitoring/   # FakeMonitoring + unit tests for each class above
app/src/test/java/com/workoutrec/...            # call-site tests in existing LogSync/setup/auth tests
app/src/androidTest/java/com/workoutrec/home/   # switch + dialog UI tests
```

**Structure Decision**: single Android app module as in features 001/002; all monitoring code in
a new `com.workoutrec.monitoring` package; existing classes get a `Monitoring` constructor
parameter defaulting to `NoMonitoring`, as `log: (String) -> Unit = {}` was added before.

## Phasing (for /speckit-tasks)

1. **Setup**: Tracer secrets (user); version catalog, plugin and `tracer {}` block with
   `isDisabled` when the token is missing; CI env only in the publishing job. Check that CI is
   still green without the token.
2. **Foundation**: the `Monitoring` interface, the fake, the settings, and `MonitoringConfig`
   (switch → configuration list).
3. **US1 (P1) crashes/ANR**: Tracer configured in `Application`; device scenario 1–3.
4. **US2 (P2) non-fatals + logs**: guard, limiter, the four call sites, `DiagnosticLog` forwarding.
5. **US6 (P2) switch**: UI, persistence, "Applies after restart", deleting stored reports. Until
   quickstart scenario 0 confirms where Tracer stores unsent reports, the app writes the top-level
   entries of `filesDir`, `cacheDir` and `noBackupFilesDir` to the diagnostic log at start.
6. **US3/US4 (P3)**: heap dump and disk usage configuration; test actions.
7. **US5 (P4) profiling**: sampler, gate, three targets.
8. **Polish**: privacy policy, quickstart run, results file.

## Complexity Tracking

No constitution violations to justify.
