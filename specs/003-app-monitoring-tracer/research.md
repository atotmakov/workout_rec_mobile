# Research: App Monitoring with Tracer

Sources: Tracer documentation at apptracer.ru/doc (Android quick start, configuration, Crash and
ANR, heap dumps, disk usage, sampling and systrace profilers, sideload, logging, migration), read
2026-10-13. Facts the documentation does not state are marked **to verify** and are checked on
the device (see [quickstart.md](quickstart.md)).

## R1. SDK version and modules

**Decision**: Tracer SDK **1.4.0**: Gradle plugin `ru.ok.tracer` 1.4.0 and BOM
`ru.ok.tracer:tracer-platform:1.4.0`, from Maven Central. Modules: `tracer-crash-report`
(crashes, ANR, non-fatals, attached logs, crash-free), `tracer-heap-dumps`, `tracer-disk-usage`,
`tracer-profiler-sampling`, `tracer-profiler-systrace`. Not used: `tracer-crash-report-native`
(the app has no native code of its own).

**Rationale**: these are the modules for every capability in the spec. 1.3.x+ needs AGP 8.2.0+ and
Gradle 8.2.1+; the project has AGP 8.10.1 and Gradle 8.11.1. The modules are independent, so the
unneeded native one stays out.

**Alternatives considered**: Sentry SDK pointed at Tracer (Tracer accepts it) has no heap dumps,
disk usage or Tracer's profilers. Firebase Crashlytics covers only crashes and needs a Firebase
project; the user asked for Tracer.

**Risk**: the SDK may be built with a newer Kotlin than 2.1.0. Kotlin reads metadata one version
ahead, so 2.2 is fine; if CI reports a metadata error, the Kotlin bump is a separate PR.

## R2. Builds, credentials, and CI test runs (FR-016, FR-017, SC-006)

**Decision**:
- `TRACER_APP_TOKEN` and `TRACER_PLUGIN_TOKEN` are repo secrets, set by the user, given **only**
  to the CI step that builds the published APK and bundle (job "Build, lint, unit tests"). Locally
  they can come from `local.properties` (`tracer.appToken`, `tracer.pluginToken`), like
  `google.webClientId`.
- In the `tracer {}` Gradle block: `isDisabled = appToken.isBlank()`, so builds without the token
  (local builds, forks, the emulator job "Compose UI tests") never start Tracer.
- `uploadMapping = false` and `dontFailOnUploadFailure = true`: R8 is off, so there is no mapping
  to upload, and a monitoring hiccup never fails a build.
- `environment = "github"` for the debug build type (the GitHub APK) and `"play"` for release (the
  Play bundle).
- `CoreTracerConfiguration.setDebugUpload(true)`: the GitHub APK is a **debug** build, and Tracer
  does not upload from debug builds by default.
- `buildFeatures.resValues = true` explicitly (Tracer injects resources; AGP 9 turns it off).

**Rationale**: the emulator job builds its own debug APK without the secrets, so instrumented
tests can't send events (SC-006). JVM unit tests never start the `Application`. Stack traces are
readable because the code is not obfuscated (FR-004, SC-002); if R8 is enabled later,
`uploadMapping` is turned on in that change.

**Alternatives considered**: a separate build type for published builds would change the GitHub
APK's package/signing story for no gain. Turning Tracer off in tests by code would mean every
test has to remember it.

## R3. The "Send diagnostics" switch (FR-012, User Story 6)

Tracer reads `HasTracerConfiguration.tracerConfiguration` **once per process**, after
`attachBaseContext` and before `onCreate`. The docs describe no runtime on/off API.

**Decision**:
- The switch is stored in `SharedPreferences` (`monitoring`, key `send_diagnostics`, default
  `true`), so it can be read synchronously at that early point. DataStore (used by
  `SettingsStore`) is asynchronous and isn't ready there.
- Switch **on** at process start: the full configuration list. Switch **off**: an empty list, so
  Tracer starts nothing (**to verify**: an empty list means no collection and no upload), and the
  app deletes Tracer's stored, unsent reports (**to verify**: their location on disk — list
  `filesDir`, `cacheDir` and `noBackupFilesDir` on the device in the first quickstart run).
- Turning the switch off **while the app runs**: the app's own calls (logs, non-fatals,
  profiles) stop immediately, because they check the switch on every call. The SDK's crash and
  ANR handlers stay installed until the process ends. A crash in that window may be stored, but
  the next start (switch off) deletes it before anything is sent. This meets "nothing is sent"
  (spec edge case) without killing the process.
- Turning it **on** while running: takes effect from the next app start. The switch shows "Applies
  after restart" under it in that case.

**Alternatives considered**: restarting the process on toggle is abrupt and could interrupt a
sync. Keeping the SDK running and filtering uploads isn't possible without an API.

## R4. Wrapping Tracer for test-first development

**Decision**: a small `Monitoring` interface in `com.workoutrec.monitoring`:
`log(line)`, `nonFatal(issue, error, details)`, `profile(target) { … }`, `enabled`. There are two
implementations: `TracerMonitoring` (thin, calls the SDK) and `NoMonitoring`. The logic around
it is plain Kotlin and unit-tested with a fake: switch gating, the rate limiter, the sampling
decision, and what may be attached. `TracerMonitoring` is the only class that imports
`ru.ok.tracer`.

**Rationale**: constitution II (test-first). The SDK can't run in JVM tests or on the CI
emulator (no token), so the decisions are kept out of it.

## R5. Attached log lines (FR-006, FR-013)

**Decision**: `DiagnosticLog.write` also passes each line to `Monitoring.log`, which calls
`TracerCrashReport.log(line)`. Tracer keeps a 64 KB ring buffer and attaches it to the next crash
or non-fatal report. The on-phone log file and **Share sync log** are unchanged (FR-008).

Lines are already free of workout data, emails and tokens (they hold states, counts, error class
names, and the spreadsheet ID). A unit test feeds the log lines produced in existing tests through
a guard that rejects `@` and `ya29.` (Google access tokens), so a future log line can't leak
them. The guard drops such a line from the monitoring copy only.

## R6. Non-fatal events and rate limiting (FR-005, FR-007)

**Decision**: issues, each reported with `TracerCrashReport.report(e, issueKey, severity)`:

| Issue key | Where | Severity |
|---|---|---|
| `sync-failed` | `LogSync` on `failed: …` (not on `cancelled`, and not on "no answer in 120 s" while offline) | ERROR |
| `sync-timeout` | `LogSync` "no answer in 120 s" with validated internet | WARNING |
| `spreadsheet-mismatch` | `SpreadsheetSetupFlow.checkExisting` on Mismatch (reasons in the message) | WARNING |
| `token-failed` | `AccountRepository.accessToken` on a failure outcome | ERROR |

Rate limit, in our own `NonFatalLimiter`: at most **1 event per issue per 30 min** and **20 per
day** in total, in memory per process. The SDK's soft limit
(`setExperimentalNonFatalRateLimitEnabled(true)`) is also on.

**Rationale**: background sync can fail every 15 minutes for hours when there is no internet. One
event per half hour still shows the problem, while staying far below Tracer's 1 M/day.

## R7. Memory snapshots (FR-009)

**Decision**: `HeapDumpConfiguration` with defaults: a snapshot on OOM, cleaned of content by the
SDK, processed and sent when the device is idle, and skipped when storage is short. The manual
trigger `HeapDumps.runNow("manual")` is only in the hidden test actions (R10).

## R8. Disk usage (FR-010)

**Decision**: `DiskUsageConfiguration`: `setInterestingSize(50 MB)`, `setProbability(1)` (a
check about once a day). The app's expected size is a Room database plus a 256 KB log, well
below 50 MB, so a report means real growth. There are no exclusions. `DiskUsage.runNow()` is in
the test actions.

## R9. Profiling targets and rates (FR-011, SC-004)

**Decision**:

| Target | Profiler | Starts | Ends (commit) | Abort | Share of runs | Max duration |
|---|---|---|---|---|---|---|
| `app-start` | Sampling | `Application.onCreate` | first frame of the start screen | process ends / timeout | 1 in 3 | 10 s |
| `sync` | Systrace + sections `token`, `read`, `write`, `refresh` | `LogSync.run` start | sync done | cancelled / failed | 1 in 10 | 120 s |
| `today-open` | Systrace | Today route entered | list first drawn | left before drawn | 1 in 5 | 5 s |

Only one profile runs at a time (`ProfileGate`); a target that would overlap is skipped for that
run. The rates are constants in `ProfileTargets`, so changing them needs no new feature.

**Rationale**: a single user starts the app a few times a day and syncs often, so these rates give
data within days. Sampling overhead only applies on selected runs, which keeps SC-004 on average.
Systrace sections cost nothing when not profiling.

## R10. Hidden test actions (Independent Tests of stories 1, 3, 4, 5)

**Decision**: long-press on the **Send diagnostics** switch opens "Diagnostics test" with these
actions: crash now, freeze the main thread for 10 s, send a test non-fatal, memory snapshot now,
disk check now, profile the next app start. They exist in all builds and are harmless, because
they only produce reports.

**Rationale**: the published GitHub APK is the debug build, so "debug-only" would mean "in the
user's build" anyway. A long-press keeps them out of the way.

## R11. Privacy policy and Play (FR-014)

**Decision**: `docs/privacy.html` gets a "Diagnostics" section: Tracer by OK.TECH (VK) receives
crash and freeze reports, recent diagnostic log lines, memory snapshots cleaned of content, app
storage sizes and performance timings. There is no workout data, email or token. It's kept for 90
days, and the user can turn it off with the Send diagnostics switch. This must be published
before the first build with monitoring reaches the phone. The Play Data safety form ("Crash logs",
"Diagnostics") is filled in with the Play setup.

## R12. Constitution IV (no custom backend)

Tracer receives diagnostics only. Workout and spreadsheet data still go only through Google APIs,
and no backend of ours is added. It is a new third-party dependency, justified in the plan.
