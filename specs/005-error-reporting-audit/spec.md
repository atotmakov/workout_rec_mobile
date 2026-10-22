# Feature Specification: Report Every Handled Error to AppTracer

**Feature Branch**: `005-error-reporting-audit`

**Created**: 2026-10-22

**Status**: Draft

**Input**: User description: "Report every handled error to AppTracer: audit catch sites (money refresh, exercise changes, setup, database) and add non-fatal reports with tests."

## Context

Constitution v1.1.0 Principle V requires every error in the app to reach AppTracer. Feature 003
(app monitoring) already sends crashes and freezes, plus four kinds of handled errors: a failed
sync run, a sync run that timed out, a spreadsheet that fails the structure check, and a failed
sign-in token request. An audit of the app on 2026-10-22 found about 35 places that catch an
error; most of them handle it quietly, so the developer never learns that it happened.

Audit findings (the places this feature changes or confirms):

| Area | What happens today | Reported today? |
|------|--------------------|-----------------|
| Money tab refresh | Failure shows "couldn't load" on the Money tab | No |
| Spreadsheet setup steps (find, create, rename, check, attach automation) | Step error screen with Retry | No |
| Setup status check on app start | Any failure is ignored | No |
| Google account picker | Back to the sign-in screen ("account required"), the same as a cancel; when switching accounts, the previous screen stays | No |
| Google access authorization | Error or "no connection" message | Partly (token request only) |
| Sync run requested from the app | Unexpected failures outside sync's own handling are ignored | No |
| Sign-out credential clearing | Failure ignored | No |
| On-phone sync log write/read | Failure ignored | No |
| Spreadsheet create, rejected in full | Falls back to a two-step create | No |
| Sheet time zone or automation timestamp unreadable | Falls back to a default | No |
| Saved app state unreadable (sync phase, money error, automation status) | Falls back to a default | No |
| Exercise changes and payments | No separate failure path: they are saved on the phone and sent by sync, whose failures are already reported; refused or dropped changes are normal conflict notices | Covered by sync |
| Phone database | No handled failures: a database failure crashes the app, and crashes are already reported | Covered by crash reports |
| User cancelled sign-in, typed date/time not valid, work cancelled by Android, "file not found" answer during the existence check, Apps Script API switched off (a setup instruction) | Normal outcomes | No, and must stay so |

## Clarifications

### Session 2026-10-22

- Q: Should failures caused only by no connection be reported? → A: Yes, at the lowest
  severity (info), under their own issue name, so they can be filtered out in the console
  (FR-011).

## User Scenarios & Testing *(mandatory)*

The "user" in these stories is the developer maintaining the app, who reads the AppTracer
console. The app's users see no change.

### User Story 1 - Failures that vanish today become visible (Priority: P1)

Some failures currently leave no trace anywhere: the setup status check on app start, an
unexpected failure around a sync run requested from the app, the Money tab refresh, sign-out
credential clearing, and the on-phone sync log. When one of these happens on a tester's phone,
the developer sees it in the AppTracer console under its own issue name, with the stack trace
and the recent diagnostic log lines.

**Why this priority**: these are the failures nobody can find today, not even by asking the user
to share the sync log. They include the Money tab, which was released this week.

**Independent Test**: in automated tests, force each of these failures and verify exactly one
report under the expected issue name; verify the app behaves as before.

**Acceptance Scenarios**:

1. **Given** the money tab has been removed from the spreadsheet, **When** the Money tab
   refreshes, **Then** the tab shows the same message as today and one "money refresh failed"
   report is sent, naming the reason (missing tab).
2. **Given** an unexpected error happens while a sync run is requested from the app, **When**
   the run ends, **Then** the waiting changes stay on the phone as today and one report is sent.
3. **Given** the setup status check fails on app start, **When** the app opens, **Then** the app
   opens as today and one "setup check failed" report is sent.
4. **Given** clearing saved credentials fails on sign-out, **When** the user signs out, **Then**
   sign-out completes as today and one report is sent.

---

### User Story 2 - Errors the user sees are reported (Priority: P2)

When the user sees an error, the same error reaches the console: a failed spreadsheet setup step
(with the step's name), a failed Google account pick, and a failed authorization.

**Why this priority**: an error the user sees but the developer doesn't is the most common source
of "it didn't work" messages from testers with no detail.

**Independent Test**: force each setup step to fail in tests, and force picker and authorization
failures; verify one report per failure, with the step name, and the same error screen as today.

**Acceptance Scenarios**:

1. **Given** creating the spreadsheet fails, **When** setup shows the step error screen, **Then**
   one "setup step failed" report is sent, naming the step.
2. **Given** the user cancels the Google account picker, **When** setup returns to the start,
   **Then** no report is sent.
3. **Given** the account picker fails for another reason, **When** the sign-in screen returns, **Then**
   one "sign-in failed" report is sent.

---

### User Story 3 - Fallbacks on bad data are reported as warnings (Priority: P3)

When the app silently replaces bad data with a default, such as an unreadable sheet time zone, an
unreadable automation timestamp, an unreadable saved state, or a rejected full spreadsheet create
that falls back to a two-step create, it sends a warning, so the developer learns that data or
Google's behavior changed before it causes a visible problem.

**Why this priority**: these fallbacks work today, so nothing is broken for the user; the
warnings only shorten diagnosis later.

**Independent Test**: feed each fallback bad input in tests; verify the fallback result is
unchanged and one warning is sent.

**Acceptance Scenarios**:

1. **Given** the spreadsheet's time zone is not a known zone, **When** sync reads the log,
   **Then** times are read in the fallback zone as today and one "bad sheet data" warning is sent.
2. **Given** Google rejects the full spreadsheet create, **When** the two-step create succeeds,
   **Then** setup continues as today and one warning is sent.

---

### User Story 4 - New error handling cannot skip reporting (Priority: P3)

An automated check in the test suite lists every place in the app code that catches an error.
Each place must either report the error or appear on a reviewed list of normal outcomes, with a
one-line reason. A new place that does neither fails the check, so future features keep to
Principle V without another audit.

**Why this priority**: it keeps the result of this audit true; without it the gap reopens with
the next feature.

**Independent Test**: add a catch site that neither reports nor is listed; the check fails and
names the file and line. List it with a reason, or report from it; the check passes.

**Acceptance Scenarios**:

1. **Given** a new catch site that does not report and is not listed, **When** the tests run,
   **Then** the check fails and names the site.
2. **Given** every catch site reports or is listed, **When** the tests run, **Then** the check
   passes.

### Edge Cases

- **Reporting itself fails**: the original outcome is unchanged, and the app does not crash or
  retry the report in a loop.
- **The sync log write fails**: it is reported without writing to the sync log again, so the
  failure cannot repeat itself.
- **The same error is caught twice** (an inner step rethrows, the outer step handles it): it is
  reported once, at the place that decides what the user sees.
- **A failure repeats every few seconds** (for example the Money tab refreshing while the money
  tab is missing): the existing per-issue rate limit applies; a repeating error cannot use up the
  service's daily limit.
- **Send diagnostics is off**: nothing is sent, as today.
- **Background work while the app is closed**: reports are stored and sent later, as for crashes.
- **Builds without monitoring credentials and automated test runs**: nothing is sent; tests check
  reports through a recording stand-in.

## Requirements *(mandatory)*

### Functional Requirements

**Coverage**

- **FR-001**: Every handled error in the app MUST be reported to AppTracer as a non-fatal event,
  unless it is a normal outcome listed under FR-004.
- **FR-002**: The app MUST report at least these new issue kinds, each under its own stable issue
  name and severity:
  - money refresh failed (error), with the reason: missing tab or other (no-connection
    failures go to FR-011);
  - setup step failed (error), with the step name;
  - setup check failed (warning);
  - sign-in failed (error), for account-picker failures other than cancel;
  - authorization failed (error), for authorization failures not already reported as a failed
    token request;
  - sync run failed unexpectedly (error), for failures caught around a sync run rather than
    inside it;
  - sign-out cleanup failed (warning);
  - sync log failed (warning);
  - spreadsheet create fallback (warning);
  - bad sheet data (warning): unreadable time zone or automation timestamp;
  - bad saved state (warning): an unreadable value saved on the phone.
- **FR-003**: The four existing issue names (sync failed, sync timeout, spreadsheet mismatch,
  token failed) MUST keep their names and behavior, except that no-connection sync failures move
  to the "no connection" issue (FR-011). Issue names are added, never renamed.

**Normal outcomes**

- **FR-004**: These outcomes MUST NOT be reported: the user cancelling sign-in or consent; text
  the user typed that is not a valid date or time; work cancelled by Android or replaced by newer
  work; a "not found" answer when the app only checks whether a file still exists; the Apps
  Script API being switched off (a setup instruction for the user); sync and exercise conflict
  notices (a change refused or dropped because the spreadsheet changed). Also not reported where
  they are caught, because the error is reported elsewhere or cannot be: a site that turns the
  error into a result or another error and hands it to a caller that reports it (account picker,
  authorization); a fallback whose bad value is already reported at another site (the time zone
  shown in screens; sync reports it); reading Google's error reply when it is not in the expected
  format (the error itself is reported by its caller); a failure inside the reporting code
  itself. Every such site is on the reviewed list (FR-012) with its reason.
- **FR-005**: Exercise changes, payments and set changes MUST stay covered through sync
  reporting; this feature adds no separate reports for them.

**Content and behavior**

- **FR-006**: Each report MUST name the failed operation (and the setup step where there is one)
  and carry the error type, message and stack trace, and the recent diagnostic log lines as
  feature 003 defines.
- **FR-007**: Reports MUST NOT contain workout or money data (exercise names, weights, reps, set
  dates, payment amounts, balance), the Google account email, or access tokens. Bad-data warnings
  name which value was unreadable, not its content. Error messages in reports MUST NOT include
  the text of Google's error replies; only the HTTP code and Google's error status (for example
  `INVALID_ARGUMENT`) are sent.
- **FR-008**: What the user sees MUST NOT change: the same messages, fallbacks, retries and
  screens as before this feature.
- **FR-009**: Reporting MUST never block, slow down or crash the app, and a failed report MUST
  NOT change the outcome of the operation. The Send diagnostics switch and the per-issue rate
  limit MUST apply to all new issue kinds.

**Tests and guard**

- **FR-010**: Each newly reported failure MUST have an automated test that forces it and checks
  for exactly one report with the right issue name; each normal outcome in FR-004 whose class
  has access to monitoring MUST have a test that checks that no report is sent; sites in classes
  without monitoring are covered by the guard (FR-012).
- **FR-011**: Failures caused only by no connection (no network, or Google's service
  temporarily unavailable) MUST be reported at info severity under one "no connection" issue
  kind, naming the operation (sync, money refresh, setup step, authorization), instead of under
  that operation's error issue. This also applies to sync: its no-connection failures move from
  "sync failed" to "no connection". A sync run that times out stays "sync timeout".
- **FR-012**: An automated check MUST list every error-catching site in the app code and fail
  when a site neither reports nor appears on the reviewed normal-outcome list with a reason. The
  list MUST be kept in the repository next to the check.

### Key Entities

- **Issue kind**: one stable name under which the console groups reports, with a severity
  (error, warning, info) and the operation it belongs to.
- **Normal-outcome entry**: one catch site that is allowed not to report, with the file, what it
  catches, and the reason.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of the error-catching sites in the app code either report or are on the
  normal-outcome list with a reason, verified by the automated check on every pull request.
- **SC-002**: Each of the 11 new issue kinds in FR-002 and the "no connection" kind in FR-011,
  when forced in a test, produces exactly one report under its issue name and severity.
- **SC-003**: 0 reports produced by the tests contain workout or money data, the account email or
  an access token.
- **SC-004**: All existing app tests pass with no changes to what they check on screen.
- **SC-005**: With Send diagnostics off, forcing every failure kind sends 0 reports.
- **SC-006**: On a tester's phone, removing the money tab from the spreadsheet and opening the
  Money tab produces a "money refresh failed" issue in the console after the next launch with a
  connection.

## Assumptions

- AppTracer reporting from feature 003 (the monitoring interface, the Send diagnostics switch,
  the per-issue rate limit, the log-line guard and stored reports) is reused as is.
- Phone database failures stay crashes, which are already reported; turning them into handled
  errors with messages is out of scope.
- Exercise changes and payments have no failure path of their own: they are stored first and
  their failures surface as sync failures, which are already reported.
- Debug-only code (screen gallery and diagnostics test actions) is excluded from the check.
- Code that catches an error only to convert it and throw it again (for example turning a network
  exception into a Google error) is not a handled error; it is reported where it is finally
  handled.
- The privacy policy already names AppTracer and the kinds of diagnostic data sent (feature 003
  FR-014); the new reports send the same kind of data, so the policy needs no change.
