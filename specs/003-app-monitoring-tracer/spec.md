# Feature Specification: App Monitoring with Tracer

**Feature Branch**: `003-app-monitoring-tracer`

**Created**: 2026-10-13

**Status**: Draft

## Clarifications

### Session 2026-10-13

- Q: Can monitoring be turned off? → A: A **Send diagnostics** switch in the account menu, on by default (FR-012, User Story 6).

**Input**: User description: "Add support for apptracer.ru for collecting crashes and ANR, Memory Leak detection, Disk Usage control, Systrace and Sampling profiling, Logging"

## Context

Today the only way to diagnose a problem on the phone is the sync log: the user reproduces the
problem, taps **Share sync log** and sends the file to the developer (features 001/002, issue 1
of feature 002). Crashes and freezes leave no trace at all, and nothing tells the developer how
fast the app starts or syncs on a real device.

This feature connects the app to **Tracer** (apptracer.ru, by OK.TECH), a monitoring service that
receives crash, freeze (ANR), memory, disk and performance reports from the app and shows them
in its web console. The person using the app and the developer are the same person here, but
the stories keep the two roles apart: the **user** runs the app on the phone, the **developer**
reads the reports in the Tracer console.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Crashes and freezes are reported automatically (Priority: P1)

When the app crashes or freezes (the system's "App isn't responding" dialog), a report with the
stack trace, app version and device details reaches the Tracer console without the user doing
anything. A report that could not be sent (no network, app killed) is sent on a later launch.

**Why this priority**: crashes and freezes are the problems the user cannot report with the sync
log today, and they are the most damaging to a workout being logged.

**Independent Test**: install a build that contains a hidden test crash trigger (debug-only),
trigger a crash and a main-thread freeze, relaunch the app with network, and see both events in
the Tracer console within minutes, with readable stack traces for the app's own code.

**Acceptance Scenarios**:

1. **Given** the app is running, **When** an unhandled error ends the app, **Then** a crash
   report with the stack trace, app version (build number) and device model appears in the
   Tracer console.
2. **Given** the app's main thread is blocked long enough for the system to consider it not
   responding, **When** the freeze happens, **Then** a freeze (ANR) report with the main-thread
   stack appears in the console.
3. **Given** a crash happened with no network, **When** the user next opens the app with
   network, **Then** the stored report is sent.
4. **Given** reports from several builds, **When** the developer opens the console, **Then**
   reports can be filtered by build number and the console shows the share of crash-free users
   per version.

---

### User Story 2 - Errors the app handles come with recent log lines (Priority: P2)

Errors the app catches and survives — a failed sync, a failed spreadsheet check, a Google sign-in
failure — are reported as non-fatal events. Every crash, freeze and non-fatal report carries the
most recent lines of the app's diagnostic log (the same lines as the sync log today), so the
developer sees what led up to the problem without asking the user for the log file.

**Why this priority**: sync and setup problems were the hardest bugs so far (issue 1 of 002,
the spreadsheet check of 001); having them arrive on their own removes the "reproduce and share
the log" round trip.

**Independent Test**: put the phone offline-but-connected (e.g. a network without internet) so
a sync fails, then check that a non-fatal "sync failed" event with the preceding log lines
appears in the console.

**Acceptance Scenarios**:

1. **Given** a sync run fails, **When** the app records the failure in its log, **Then** a
   non-fatal event grouped under a stable "sync failed" issue appears in the console with the
   recent log lines attached.
2. **Given** the spreadsheet check reports a mismatch, **When** the setup asks the rewrite
   question, **Then** a non-fatal event with the mismatch reasons appears in the console.
3. **Given** any crash or freeze, **When** its report is viewed, **Then** it shows the most
   recent diagnostic log lines from before the event.
4. **Given** the same failure repeats many times, **When** events are sent, **Then** they are
   grouped into one issue and their number is limited so the service's daily limits are not
   exhausted by one repeating error.

---

### User Story 3 - Memory leaks and out-of-memory crashes are diagnosed (Priority: P3)

When the app runs out of memory, a memory snapshot is taken, cleaned of its content on the
phone, and sent in the background when the phone is idle; the console then shows which
screens or objects leaked.

**Why this priority**: the app has not shown memory problems yet; this guards against them as
history grows, but is less urgent than crashes and sync errors.

**Independent Test**: in a debug-only test build, trigger a manual memory snapshot and check that
a memory report appears in the console after the phone has been idle.

**Acceptance Scenarios**:

1. **Given** the app crashes with an out-of-memory error, **When** the phone is next idle with
   enough free space, **Then** a cleaned memory snapshot is sent and leak results appear in the
   console.
2. **Given** the phone does not have enough free storage for a snapshot, **When** an
   out-of-memory crash happens, **Then** the crash itself is still reported and no snapshot is
   kept.

---

### User Story 4 - The app's storage use is watched (Priority: P3)

The app checks how much storage it uses (database, logs, caches) about once a day in the
background, and sends a disk-usage report when it exceeds a limit suitable for this app, so a
growing log or database is noticed before it fills the phone.

**Why this priority**: the app keeps a local database and log files; growth problems are slow and
cheap to catch early, but not urgent.

**Independent Test**: in a debug-only test build with a very low limit, force a check and see a
disk-usage report listing the app's largest files and folders.

**Acceptance Scenarios**:

1. **Given** the app's storage use exceeds the limit, **When** the daily background check runs,
   **Then** a disk-usage report with the largest files and folders appears in the console.
2. **Given** the storage use is under the limit, **When** the check runs, **Then** no report is
   sent.

---

### User Story 5 - Slow operations can be profiled (Priority: P4)

The developer can see how long key operations take on a real phone and which methods take the
time: app start until the workout screen is shown, a sync run, and opening the Today screen.
These operations are profiled on a share of runs and the results appear in the console grouped
by operation and app version.

**Why this priority**: there is no performance complaint today; this provides numbers before
the history grows and before Play users (internal testing) appear.

**Independent Test**: with profiling forced on in a debug-only test build, open the app and run a
sync; profiles for "app start" and "sync" appear in the console with per-method timings.

**Acceptance Scenarios**:

1. **Given** profiling is selected for this run of an operation, **When** the operation
   finishes, **Then** a profile tagged with the operation's name is sent.
2. **Given** an operation is abandoned (for example, the app is closed mid-sync), **When** it
   ends, **Then** its partial profile is discarded, not sent.
3. **Given** profiling is not selected for a run, **When** the operation runs, **Then** it is
   not slowed down by profiling.

---

### User Story 6 - The user can turn diagnostics off (Priority: P2)

The account menu has a **Send diagnostics** switch, on by default. When the user turns it off,
the app stops collecting and sending reports and drops the ones not yet sent; turning it back
on resumes monitoring.

**Why this priority**: it is the user's control over data leaving the phone, needed before the
app reaches internal testers through Google Play and to describe monitoring honestly in the
privacy policy.

**Independent Test**: turn the switch off, trigger a test crash, relaunch with network, and check
that nothing new appears in the console; turn it on, trigger again, and see the report.

**Acceptance Scenarios**:

1. **Given** a fresh install, **When** the user opens the account menu, **Then** **Send
   diagnostics** is on.
2. **Given** the switch is off, **When** the app crashes or a sync fails, **Then** nothing is
   sent, now or later.
3. **Given** the switch was turned off, **When** the app is restarted or updated, **Then** it
   stays off.

---

### Edge Cases

- **No network for a long time**: reports are kept on the phone (bounded number and age) and
  sent later; the oldest are dropped first, and storage use stays bounded.
- **Monitoring service unreachable or rejecting reports**: the app works normally; monitoring
  failures never show errors to the user and never block logging or sync.
- **Monitoring turned off** with the **Send diagnostics** switch (FR-012): nothing is collected
  or sent, including reports stored before it was turned off. A crash that happens while it is
  off is never reported, even after it is turned back on.
- **Crash during app start, before monitoring is ready**: as much as the platform allows is
  reported; an app that crashes on start must still be diagnosable from the console.
- **Builds without the monitoring credentials** (local builds, forks, pull requests from
  others): the app builds and runs, with monitoring silently off.
- **Automated test runs in CI**: they must not send events to the console.
- **Daily service limits** (events, memory snapshots, profiles): repeated events are grouped
  and limited so one bad build does not hide everything else.
- **Two app variants** (GitHub APK and Google Play build): reports from both reach the same
  console and can be told apart.

## Requirements *(mandatory)*

### Functional Requirements

**Crashes and freezes**

- **FR-001**: The app MUST report unhandled crashes, with stack trace, app version (build
  number), Android version and device model, to the Tracer console.
- **FR-002**: The app MUST report freezes (ANR) with the main-thread stack.
- **FR-003**: Reports that cannot be sent at once MUST be stored on the phone (bounded in number
  and age) and sent on a later launch.
- **FR-004**: Stack traces of the app's own code MUST be readable in the console for every
  published build.

**Non-fatal errors and logging**

- **FR-005**: The app MUST report as non-fatal events at least: a failed sync run, a failed
  spreadsheet structure check (with its mismatch reasons), and a failed Google sign-in or token
  request. Each kind MUST be grouped under its own stable issue name.
- **FR-006**: Every crash, freeze and non-fatal report MUST carry the most recent diagnostic log
  lines (the lines the sync log records today), bounded in size.
- **FR-007**: Non-fatal events MUST be rate-limited so a repeating error cannot exhaust the
  service's daily event limit.
- **FR-008**: The existing on-phone sync log and its **Share sync log** buttons MUST keep
  working unchanged.

**Memory and storage**

- **FR-009**: On an out-of-memory crash the app MUST capture a memory snapshot, have it cleaned
  of content on the phone, and send it only when the phone is idle; the console MUST show leak
  results from it.
- **FR-010**: The app MUST check its own storage use at most about once a day in the background
  and report when it exceeds a limit set for this app, listing the largest files and folders,
  without uploading file contents.

**Profiling**

- **FR-011**: The app MUST be able to profile app start (until the workout screen is shown), a
  sync run, and opening the Today screen, on a configured share of runs, sending each finished
  profile under the operation's name and discarding abandoned ones.

**Privacy and control**

- **FR-012**: The account menu MUST have a **Send diagnostics** switch, on by default. Turning it
  off stops all collection and sending at once and discards reports not yet sent; turning it on
  again resumes from that moment. The choice MUST survive app restarts and updates.
- **FR-013**: Reports MUST NOT contain workout data (exercises, weights, reps, dates of sets),
  the Google account email, or access tokens. Log lines attached to reports MUST be limited to
  the diagnostic log, which already holds none of these.
- **FR-014**: The privacy policy page MUST be updated before the first build with monitoring is
  published, naming Tracer, what is sent, and why.
- **FR-015**: Monitoring MUST never block or slow down workout logging or sync, and MUST never
  show errors to the user.

**Builds**

- **FR-016**: Monitoring MUST be active in the builds the user installs (the GitHub APK and the
  Google Play build), and the two MUST be distinguishable in the console.
- **FR-017**: Builds made without the monitoring credentials, and automated test runs, MUST
  build and run normally without sending anything.

### Key Entities

- **Crash / freeze report**: one failure of the app — type (crash or freeze), stack trace, app
  version, device and Android version, time, attached recent log lines.
- **Non-fatal event**: one handled error — issue name (e.g. "sync failed"), severity, error
  details, attached recent log lines.
- **Memory snapshot**: a cleaned picture of the app's memory taken on out-of-memory, analyzed
  for leaks by the service.
- **Disk-usage report**: the app's storage use by folder with the largest items, sent when over
  the limit.
- **Profile**: timings of one profiled operation (name, app version, duration, per-method time).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A crash or freeze on the phone is visible in the Tracer console within 10 minutes
  of the next app launch with network, with no action from the user.
- **SC-002**: For every reported crash of a published build, the app's own code in the stack
  trace is readable (class and method names, not obfuscated or missing).
- **SC-003**: The next sync or setup problem can be diagnosed from the console alone, without
  asking the user to share the sync log.
- **SC-004**: App start and sync take no more than 5% longer with monitoring on than with it
  off, measured on the test phone.
- **SC-005**: No report in the console contains workout data, the account email or a token
  (checked on a sample of every report type during device testing).
- **SC-006**: Automated test runs in CI add zero events to the console.

## Assumptions

- **Service**: Tracer (apptracer.ru) is used as offered by its Android SDK: crash/ANR,
  non-fatal events with attached logs, memory snapshots on out-of-memory with server-side leak
  analysis, disk-usage checks, sampling and section (systrace) profiling. Its daily limits apply
  (about 1 million events, 500 memory snapshots, 500 profiles per project) and it keeps data for
  90 days. "Logging" in the request means app log lines attached to reports; Tracer's own
  internal SDK logging is only used, if at all, while integrating.
- **Account**: the developer creates the Tracer organization and Android project and provides
  the project's credentials; like the existing Google client ID and signing keys, they are kept
  out of the repository and supplied to CI as secrets.
- **Constitution**: Tracer is a monitoring service, not a backend for workout data — all workout
  and spreadsheet data still goes only through Google APIs (principle IV). It is a new
  third-party dependency and is justified in the plan.
- **Leak detection scope**: the service finds simple leaks (screens and components kept alive);
  leaks in the app's own logic may not be found. Memory snapshots are taken on out-of-memory,
  plus on demand in debug-only test builds.
- **Profiling share**: with a single user, a small sampling rate would produce no data; the share
  of profiled runs is chosen in the plan (high enough to give data for one user, low enough to
  meet SC-004) and can be changed without a new feature.
- **Storage limit**: the disk-usage limit is set for this app's expected size (a local database
  and logs in the tens of megabytes), not the service default of 10 GB.
- **Identity**: no user ID or email is attached to reports; the service's own anonymous
  per-install counting is enough for one user and a few internal testers.
- **Play disclosure**: the Play Console "Data safety" form will declare crash logs and
  diagnostics when the app is published there (tracked with the Play setup, not this feature).
