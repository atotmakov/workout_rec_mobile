---

description: "Task list for App Monitoring with Tracer"
---

# Tasks: App Monitoring with Tracer

**Input**: Design documents from `/specs/003-app-monitoring-tracer/`

**Prerequisites**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md),
[data-model.md](data-model.md), [contracts/monitoring.md](contracts/monitoring.md),
[quickstart.md](quickstart.md)

**Tests**: Required. Constitution Principle II (Test-First, non-negotiable). There is no local
JDK, so "observe failing" means pushing the tests with compiling stubs and seeing the CI run red,
then pushing the implementation and seeing it green. Tracer itself can't run in CI (no token
there, by design: R2). Its behaviour is checked on the phone in [quickstart.md](quickstart.md).

**Organization**: Tasks are grouped by user story so each story can be implemented and tested
independently. Phases follow spec priorities: US1 (P1), US2 and US6 (P2), US3 and US4 (P3),
US5 (P4).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies on incomplete tasks)
- **[Story]**: Which user story this task belongs to (US1–US6)

## Path Conventions

- App code: `app/src/main/java/com/workoutrec/` (below: `main/…`)
- JVM tests: `app/src/test/java/com/workoutrec/` (below: `test/…`)
- Instrumented tests (CI emulator): `app/src/androidTest/java/com/workoutrec/` (below: `androidTest/…`)

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Tracer in the build, with CI unchanged when there is no token.

- [ ] T001 User: create the Tracer organization and an Android project for `com.workoutrec` at apptracer.ru, and set the repo secrets `TRACER_APP_TOKEN` and `TRACER_PLUGIN_TOKEN` (quickstart prerequisites 1–2). It doesn't block T002–T004, which must also be green without the secrets.
- [ ] T002 Add `tracer = "1.4.0"`, the plugin `ru-ok-tracer` (id `ru.ok.tracer`), the BOM `tracer-platform` and the modules `tracer-crash-report`, `tracer-heap-dumps`, `tracer-disk-usage`, `tracer-profiler-sampling` and `tracer-profiler-systrace` to `gradle/libs.versions.toml` (research R1).
- [ ] T003 In `app/build.gradle.kts`:
  - Apply the plugin and add `implementation(platform(libs.tracer.platform))` plus the five modules.
  - Set `buildFeatures.resValues = true`.
  - Read `tracer.appToken` and `tracer.pluginToken` from `local.properties`, or `TRACER_APP_TOKEN` and `TRACER_PLUGIN_TOKEN` from the environment.
  - Add the `tracer {}` block: `defaultConfig` with the tokens, `uploadMapping = false`, `dontFailOnUploadFailure = true` and `isDisabled = appToken.isBlank()`; `debug` with `environment = "github"`; `release` with `environment = "play"` (R2, contract "Project and environments").
- [ ] T004 Pass `TRACER_APP_TOKEN` and `TRACER_PLUGIN_TOKEN` as env **only** to the "Assemble, unit tests, lint" and "Release bundle for Google Play" steps of the `unit-tests` job in `.github/workflows/ci.yml`. Never pass them to the `ui-tests` job (SC-006). Push and confirm CI is green, with and without the secrets.

**Checkpoint**: the app builds with the Tracer SDK on the classpath, and nothing is initialized yet.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: the `Monitoring` seam, the switch value and the configuration choice that every story uses.

### Tests (write first, push red)

- [ ] T005 [P] Add `FakeMonitoring` to `test/fakes/FakeMonitoring.kt`. It records `logs`, `nonFatals` (issue, error, details) and `profiles` (target, committed/aborted) and has a settable `enabled`.
- [ ] T006 [P] Add tests in `test/monitoring/MonitoringSettingsTest.kt` against a fake key-value store:
  - `sendDiagnostics` defaults to `true` on a fresh install.
  - It persists after setting.
  - `startedWith` is captured once at process start and doesn't change when the switch changes.
  - `appliesAfterRestart` is true only when `sendDiagnostics && !startedWith` (data-model "MonitoringSetting").
- [ ] T007 [P] Add tests in `test/monitoring/MonitoringConfigTest.kt`:
  - Switch on with a token → the plan lists the modules `core` (debug upload on), `crash` (ANR on, non-fatal soft limit on), `crash-free`, `heap-dumps`, `disk-usage` (50 MB, probability 1), `sampling` and `systrace`.
  - Switch off → an empty list, plus a request to delete stored reports.
  - No token → an empty list and no delete (R2, R3, R8).

### Implementation (make green)

- [ ] T008 [P] Create `main/monitoring/Monitoring.kt`:
  - The `Monitoring` interface (`enabled`, `log(line)`, `nonFatal(issue, error, details)`, `profile(target, block)`).
  - `NoMonitoring`.
  - The `NonFatalIssue` enum with keys `sync-failed` (ERROR), `sync-timeout` (WARNING), `spreadsheet-mismatch` (WARNING), `token-failed` (ERROR) and `diagnostics-test` (INFO). Keys come from contracts/monitoring.md "Events" and never change.
  - The `ProfileTarget` enum with `app-start`, `sync` and `today-open`.
- [ ] T009 [P] Implement `main/monitoring/MonitoringSettings.kt` over `SharedPreferences` file `monitoring`, key `send_diagnostics`, default `true`. Read it synchronously, and set `startedWith` once from `Application.attachBaseContext` (R3).
- [ ] T010 Implement `main/monitoring/MonitoringConfig.kt`. It is a pure decision (`ConfigPlan`: module list + `deleteStoredReports`), and a separate function maps the plan to Tracer `TracerConfiguration` builders. That mapping is the only SDK-touching part, and it goes in `main/monitoring/TracerMonitoring.kt`.
- [ ] T011 Implement `TracerMonitoring` in `main/monitoring/TracerMonitoring.kt`. It holds the adapter calls `TracerCrashReport.log`, `TracerCrashReport.report(e, issueKey, severity)`, `SamplingProfiler`/`SystraceProfiler` `start`/`commit`/`abort` and `android.os.Trace` sections. Every call checks `enabled` first and wraps SDK exceptions so they never reach the caller (FR-015). This is the only file importing `ru.ok.tracer`.
- [ ] T012 In `main/AppContainer.kt`, create `MonitoringSettings` and pick the `Monitoring`: `TracerMonitoring` when the token is present and `startedWith`, otherwise `NoMonitoring`. Expose it as `container.monitoring`.

**Checkpoint**: green CI. Every later class receives a `Monitoring` (default `NoMonitoring`) as its constructor parameter.

---

## Phase 3: User Story 1 - Crashes and freezes are reported automatically (Priority: P1) 🎯 MVP

**Goal**: crashes and ANRs reach the Tracer console without any action from the user.

**Independent Test**: quickstart scenarios 1–3. Use Diagnostics test → Crash now, Freeze for 10 s, and an offline crash. Each event appears within 10 min of reopening with network, in env `github`, with readable `com.workoutrec` frames.

### Tests for User Story 1 (write first, push red)

- [ ] T013 [P] [US1] Add tests in `test/WorkoutRecApplicationTracerTest.kt` (JVM, no SDK). With a token and the switch on, the application's configuration plan comes from `MonitoringConfig` with crash and ANR on. With the switch off, the plan is empty.
- [ ] T014 [P] [US1] Add a test in `test/monitoring/DiagnosticsTestActionsTest.kt`: `crashNow()` throws a `DiagnosticsTestCrash` from the main thread and `freeze(10 s)` blocks the main thread for the given duration. Use an injected main-thread runner and clock fakes.

### Implementation for User Story 1

- [ ] T015 [US1] Make `main/WorkoutRecApplication.kt` implement `HasTracerConfiguration`. Capture `startedWith` in `attachBaseContext`. `tracerConfiguration` returns `TracerMonitoring.configurations(MonitoringConfig.plan(...))`, which is read once per process (R3).
- [ ] T016 [US1] Implement `crashNow()` and `freeze(duration)` in `main/monitoring/DiagnosticsTestActions.kt` (R10).
- [ ] T017 [US1] Run quickstart scenarios 1–3 on the phone with the first master build after T001, and record the results in `specs/003-app-monitoring-tracer/quickstart-results.md`.

**Checkpoint**: MVP. Crashes and freezes arrive. A temporary way to trigger them is in place until US6 adds the switch dialog (T016 actions can be reached from T035's dialog; until then from a debug intent, documented in the results file).

---

## Phase 4: User Story 2 - Errors the app handles come with recent log lines (Priority: P2)

**Goal**: failed syncs, spreadsheet mismatches and token failures arrive as rate-limited non-fatal events, and every report carries recent log lines.

**Independent Test**: quickstart scenarios 4–5. Two sync failures within 30 min produce one `sync-failed` event with log lines, and a renamed tab produces one `spreadsheet-mismatch` event listing `MissingTab(balance)`.

### Tests for User Story 2 (write first, push red)

- [ ] T018 [P] [US2] Add tests in `test/monitoring/LogLineGuardTest.kt`. A line containing `@` or `ya29.` is not forwarded, and other lines are. Also feed every log line the existing tests produce (`AccountRepositoryTest`, `LogSyncErrorsTest`, `SetupViewModelSpreadsheetTest`) through the guard; all of them pass except any containing an email (FR-013, data-model "LogLineGuard").
- [ ] T019 [P] [US2] Add tests in `test/monitoring/NonFatalLimiterTest.kt` (data-model "NonFatalLimiter"):
  - "at most 1 event per issue per 30 min": the first event is allowed, a second for the same issue within 29 min is blocked, and one at 30 min is allowed.
  - Different issues are independent.
  - "20 per day" in total, and the 21st is blocked.
  - The counter resets on a new local day.
- [ ] T020 [P] [US2] Add a test in `test/diag/DiagnosticLogTest.kt`: `write(line)` still appends to the file **and** passes guarded lines to `Monitoring.log` (FR-006, FR-008).
- [ ] T021 [P] [US2] Add tests in `test/sync/LogSyncErrorsTest.kt`:
  - A failing sync reports `sync-failed` with the exception.
  - "no answer in 120 s" reports `sync-timeout`.
  - Cancellation reports nothing.
  - The `FakeMonitoring` receives these events.
- [ ] T022 [P] [US2] Add a test in `test/setup/SetupViewModelSpreadsheetTest.kt`: a mismatch reports `spreadsheet-mismatch` with the reasons in its details, and a match reports nothing.
- [ ] T023 [P] [US2] Add a test in `test/auth/AccountRepositoryTest.kt`: a failed or timed-out token request reports `token-failed` with the outcome name. A granted token or a cached one reports nothing.

### Implementation for User Story 2

- [ ] T024 [P] [US2] Implement `main/monitoring/LogLineGuard.kt`.
- [ ] T025 [P] [US2] Implement `main/monitoring/NonFatalLimiter.kt` with an injected clock and zone. Use it inside `TracerMonitoring.nonFatal`.
- [ ] T026 [US2] Add a `monitoring: Monitoring = NoMonitoring` parameter to `main/diag/DiagnosticLog.kt`. `write` forwards lines that `LogLineGuard` allows.
- [ ] T027 [US2] Report `sync-failed` and `sync-timeout` from `main/sync/LogSync.kt` at its existing `failed:` and `no answer` branches.
- [ ] T028 [US2] Report `spreadsheet-mismatch` from `main/setup/SpreadsheetSetupFlow.kt` `checkExisting`, with a `SpreadsheetMismatch(reasons)` exception.
- [ ] T029 [US2] Report `token-failed` from `main/auth/AccountRepository.kt` `accessToken` on failure outcomes.
- [ ] T030 [US2] Wire `container.monitoring` into `DiagnosticLog`, `LogSync`, `SpreadsheetSetupFlow` and `AccountRepository` in `main/AppContainer.kt`.
- [ ] T031 [US2] Run quickstart scenarios 4–5 and record the results.

**Checkpoint**: sync and setup problems arrive on their own, with context (SC-003).

---

## Phase 5: User Story 6 - The user can turn diagnostics off (Priority: P2)

**Goal**: the **Send diagnostics** switch in the account menu, on by default, with the hidden test actions.

**Independent Test**: quickstart scenarios 9–10. When the switch is off, a crash never reaches the console. Turn it on and restart, and a crash arrives again.

### Tests for User Story 6 (write first, push red)

- [ ] T032 [P] [US6] Add tests in `androidTest/home/SendDiagnosticsTest.kt`:
  - The account menu shows **Send diagnostics** (test tag `account_send_diagnostics`), on by default.
  - Toggling it off saves `false`.
  - Turning it on when the process started with it off shows "Applies after restart" (RU «Включится после перезапуска»).
  - A long-press opens the dialog tagged `diagnostics_test_dialog` with the six actions from contracts/monitoring.md "UI".
- [ ] T033 [P] [US6] Add tests in `test/monitoring/StoredReportsCleanerTest.kt`. When `ConfigPlan.deleteStoredReports` is true, the cleaner deletes the configured Tracer report folders (a temp-dir fake) and leaves the app's own files (`logs/sync.log`, databases, datastore) alone. When it is false, nothing is deleted.
- [ ] T034 [P] [US6] Add a test in `test/monitoring/MonitoringGateTest.kt`: after `sendDiagnostics` turns off at runtime, `TracerMonitoring.enabled` is false at once, so `log`, `nonFatal` and `profile` become no-ops (R3).

### Implementation for User Story 6

- [ ] T035 [US6] Add the switch row and the long-press **Diagnostics test** dialog to `AccountMenu` in `main/home/MainScreen.kt`. Add the strings to `res/values/strings.xml` and `res/values-ru/strings.xml`: "Send diagnostics" / «Отправлять диагностику», "Applies after restart" / «Включится после перезапуска», "Diagnostics test" / «Проверка диагностики», and the action labels.
- [ ] T036 [US6] Implement `main/monitoring/StoredReportsCleaner.kt` and call it from `WorkoutRecApplication.attachBaseContext` when the plan says so. On every start, until scenario 0 is confirmed, also write the top-level entries of `filesDir`, `cacheDir` and `noBackupFilesDir` to the diagnostic log (plan phase 5).
- [ ] T037 [US6] Make `TracerMonitoring.enabled` read `MonitoringSettings.sendDiagnostics` on every call, in `main/monitoring/TracerMonitoring.kt`.
- [ ] T038 [US6] Run quickstart scenario 0, and set the confirmed Tracer storage folders in `StoredReportsCleaner`. Then run scenarios 9–10 and record the results. Remove the folder-listing log line once the folders are confirmed.

**Checkpoint**: the user controls whether anything leaves the phone.

---

## Phase 6: User Story 3 - Memory leaks and out-of-memory crashes are diagnosed (Priority: P3)

**Goal**: OOM snapshots, cleaned on the phone, are sent when the phone is idle.

**Independent Test**: quickstart scenario 6. Diagnostics test → Memory snapshot now, and the next day a report appears under "Memory leaks".

- [ ] T039 [P] [US3] Add a test in `test/monitoring/MonitoringConfigTest.kt`: the plan with the switch on includes `heap-dumps` enabled. Add another in `test/monitoring/DiagnosticsTestActionsTest.kt`: "Memory snapshot now" calls the adapter's `heapDumpNow("manual")`.
- [ ] T040 [US3] Map `heap-dumps` to `HeapDumpConfiguration.build { }` and implement `heapDumpNow(tag)` with `HeapDumps.runNow(tag)` in `main/monitoring/TracerMonitoring.kt`. Wire the action in `main/monitoring/DiagnosticsTestActions.kt` (R7).
- [ ] T041 [US3] Run quickstart scenario 6 and record the result.

---

## Phase 7: User Story 4 - The app's storage use is watched (Priority: P3)

**Goal**: a daily background check reports when the app uses more than 50 MB.

**Independent Test**: quickstart scenario 7. Diagnostics test → Disk check now; under 50 MB nothing is sent, which is expected.

- [ ] T042 [P] [US4] Add a test in `test/monitoring/MonitoringConfigTest.kt`: `disk-usage` has `interestingSizeBytes = 50 * 1024 * 1024` and `probability = 1`. Add another in `test/monitoring/DiagnosticsTestActionsTest.kt`: "Disk check now" calls the adapter's `diskCheckNow()`.
- [ ] T043 [US4] Map `disk-usage` to `DiskUsageConfiguration.build { setInterestingSize(…); setProbability(1) }` and implement `diskCheckNow()` with `DiskUsage.runNow()` in `main/monitoring/TracerMonitoring.kt`. Wire the action in `main/monitoring/DiagnosticsTestActions.kt` (R8).
- [ ] T044 [US4] Run quickstart scenario 7 and record the result.

---

## Phase 8: User Story 5 - Slow operations can be profiled (Priority: P4)

**Goal**: profiles of app start, sync and opening Today, on a share of runs.

**Independent Test**: quickstart scenario 8. Diagnostics test → Profile next app start, then restart. An `app-start` profile appears, and `sync` profiles appear with the sections `token`, `read`, `write` and `refresh`.

### Tests for User Story 5 (write first, push red)

- [ ] T045 [P] [US5] Add tests in `test/monitoring/ProfileTargetsTest.kt` (data-model "ProfileTarget"):
  - `ProfileSampler.selected` uses "Share of runs" exactly: `app-start` 1/3, `sync` 1/10, `today-open` 1/5, with an injected random.
  - "Profile next app start" forces the next `app-start` to be selected, once.
  - `ProfileGate` allows one active profile, and an overlapping target is skipped.
  - A profile is aborted after its max duration: 10 s, 120 s, 5 s.
- [ ] T046 [P] [US5] Add tests in `test/sync/LogSyncProfileTest.kt`. With `FakeMonitoring`, a sync run is wrapped in `profile(sync)`, which is committed on success and aborted on failure or cancellation. The sections are `token`, `read`, `write` and `refresh`, in that order.
- [ ] T047 [P] [US5] Add a test in `androidTest/log/TodayProfileTest.kt`: opening Today commits `today-open` once the list is first drawn, and leaving before that aborts it (`FakeMonitoring` injected).

### Implementation for User Story 5

- [ ] T048 [P] [US5] Implement `ProfileSampler`, `ProfileGate` and the target table in `main/monitoring/ProfileTargets.kt`.
- [ ] T049 [US5] Implement `profile(target, block)` in `main/monitoring/TracerMonitoring.kt` using the sampler and the gate. `app-start` uses `SamplingProfiler`, while `sync` and `today-open` use `SystraceProfiler` plus `Trace.beginSection`/`endSection` in `try`/`finally`. Commit with the suffix `done`.
- [ ] T050 [US5] Start `app-start` in `WorkoutRecApplication.onCreate`, and commit it in `main/MainActivity.kt` when the start screen draws its first frame (`reportFullyDrawn` point).
- [ ] T051 [US5] Wrap `LogSync.run` and its four phases in `main/sync/LogSync.kt`.
- [ ] T052 [US5] Profile `today-open` in `main/log/LogRoutes.kt` from route entry to the first composition of the list.
- [ ] T053 [US5] Wire "Profile next app start" in `main/monitoring/DiagnosticsTestActions.kt`.
- [ ] T054 [US5] Run quickstart scenarios 8 and 12 (overhead ≤ 5%, SC-004) and record the results.

---

## Phase 9: Polish & Cross-Cutting Concerns

- [ ] T055 [P] Add a "Diagnostics" section to `docs/privacy.html` that names Tracer (OK.TECH) and lists what is sent, what is never sent (contracts/monitoring.md "Never sent"), the 90-day retention, and the Send diagnostics switch (FR-014). It must merge **before** the first master build that has `TRACER_APP_TOKEN`.
- [ ] T056 [P] Update `README.md` with the Tracer secrets and the fact that builds without them have monitoring off.
- [ ] T057 Run quickstart scenarios 11 (privacy sample, SC-005) and 13 (no CI events, SC-006). Then finish `specs/003-app-monitoring-tracer/quickstart-results.md` and mark the feature complete.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: T002–T004 have no dependencies. T001 (user) is needed only for the device scenarios.
- **Foundational (Phase 2)**: depends on Setup and blocks all stories.
- **US1 (Phase 3)**: depends on Foundational. This is the MVP.
- **US2 (Phase 4)** and **US6 (Phase 5)**: depend on Foundational and are independent of each other. T055 (privacy policy) must merge before the first build with the token reaches the phone, so before T017.
- **US3, US4 (Phases 6–7)**: depend on Foundational. Their device runs use the test dialog from US6 (T035).
- **US5 (Phase 8)**: depends on Foundational. "Profile next app start" uses the US6 dialog.
- **Polish (Phase 9)**: T055 early, as above. T057 last.

### Within Each Story

- Tests are pushed first with compiling stubs and must turn CI red. The implementation is then pushed to make CI green.
- Configuration and adapter changes go in `TracerMonitoring` only. The call sites depend only on `Monitoring`.
- Device scenarios run on the master build after the story merges.

### Parallel Opportunities

- T005–T007 (Foundational tests), and then T008–T009.
- Within US2: T018–T023 (tests in different files), then T024–T025.
- US2 and US6 can be built in parallel after Foundational.
- T039, T042 and T045–T047 are in different files.

---

## Parallel Example: User Story 2

```text
# Tests together (one red push):
T018 test/monitoring/LogLineGuardTest.kt
T019 test/monitoring/NonFatalLimiterTest.kt
T020 test/diag/DiagnosticLogTest.kt
T021 test/sync/LogSyncErrorsTest.kt
T022 test/setup/SetupViewModelSpreadsheetTest.kt
T023 test/auth/AccountRepositoryTest.kt

# Then the independent implementations:
T024 main/monitoring/LogLineGuard.kt
T025 main/monitoring/NonFatalLimiter.kt
```

---

## Implementation Strategy

### MVP First (User Story 1)

1. Setup and Foundational. CI stays green without the token.
2. T055 (privacy policy), then US1 together with the `crashNow`/`freeze` actions.
3. After the user sets the token, check crashes and freezes on the phone (T017).

### Incremental Delivery

1. US2 (errors and log lines) and US6 (the switch) next. Together they replace "share the sync log" for most problems and give the user control.
2. US3 and US4 are configuration-sized.
3. US5 (profiling) last.

Each story is one PR to master (merged once green), so every step is installable as a build.
