# Data Model: App Monitoring with Tracer

Nothing new goes into the Google Sheet or the Room database. The monitoring state lives on the
phone (one preference, plus in-memory state); reports are owned by the Tracer SDK until they are
sent.

## MonitoringSetting

The **Send diagnostics** switch (FR-012).

| Field | Type | Rule |
|---|---|---|
| `sendDiagnostics` | Boolean | Default `true` on a fresh install; kept across restarts and updates |
| `startedWith` | Boolean | The value read when this process started (Tracer configured from it) |

Stored in `SharedPreferences` file `monitoring`, key `send_diagnostics` (research R3): it must be
readable synchronously before `Application.onCreate`.

State, as the user sees it:

```text
           turn off (immediate for the app's own events)
   ON  ─────────────────────────────────────────────▶  OFF
    ▲                                                   │
    └──── turn on ("Applies after restart" until next start) ┘
```

- `sendDiagnostics = false` → `Monitoring.enabled = false` at once; on the next start Tracer is
  not configured and its stored reports are deleted.
- `sendDiagnostics = true` while `startedWith = false` → the switch shows "Applies after restart".

## Monitoring (interface, not stored)

The app's only door to Tracer (research R4).

| Member | Meaning |
|---|---|
| `enabled` | `startedWith && sendDiagnostics && token present` — checked on every call |
| `log(line)` | Adds a diagnostic log line to the buffer attached to reports (R5) |
| `nonFatal(issue, error, details)` | Reports a handled error through `NonFatalLimiter` (R6) |
| `beginProfile(target)` | A `Profile` (sections, commit, abort) if `ProfileSampler` selects this run and `ProfileGate` is free, else `NoProfile` (R9); `profiled(target) { }` wraps a block |

Implementations: `TracerMonitoring` (SDK), `NoMonitoring` (no token, or switched off), and a
`FakeMonitoring` for tests.

## NonFatalIssue

| Value | Severity | Source |
|---|---|---|
| `sync-failed` | ERROR | `LogSync` failure (not cancellation) |
| `sync-timeout` | WARNING | `LogSync` "no answer in 120 s" |
| `spreadsheet-mismatch` | WARNING | `SpreadsheetSetupFlow.checkExisting` mismatch, reasons in details |
| `token-failed` | ERROR | `AccountRepository.accessToken` failure outcome |

The key is stable across versions (FR-005); new issues add values, they never rename old ones.

## NonFatalLimiter (in memory, per process)

| Field | Rule |
|---|---|
| `lastSent[issue]` | Time of the last event sent for this issue |
| `sentToday`, `day` | Count of events sent on the current local day |

`allow(issue, now)` is true only when `now - lastSent[issue] ≥ 30 min` and `sentToday < 20`. It
resets `sentToday` when the day changes.

## ProfileTarget

| Value | Profiler | Share of runs | Max duration | Commit point | Abort point |
|---|---|---|---|---|---|
| `app-start` | Sampling | 1/3 | 10 s | first frame of the start screen | timeout, process end |
| `sync` | Systrace (sections `info`, `read`, `write`, `commit`) | 1/10 | 120 s | sync done | cancelled, failed |
| `today-open` | Systrace | 1/5 | 5 s | Today list first drawn | screen left first |

`ProfileSampler(store, roll)` takes the random roll as a parameter, so tests are
deterministic; "Profile next app start" sets `profile_next_app_start` in the same preferences file,
used once. `ProfileGate` allows one active profile at a time; `ScreenProfile` handles
begin-on-enter, commit-when-shown, abort-when-left for app start and Today.

## LogLineGuard

A diagnostic log line is copied to monitoring only when it contains neither `@` (email) nor
`ya29.` (Google access token) (FR-013). Blocked lines stay in the on-phone log.

## Reports (owned by the Tracer SDK)

Crash/freeze report, non-fatal event, memory snapshot, disk-usage report, profile, as in the
spec's Key Entities. On the phone they are stored by the SDK until sent (bounded by the SDK).
When the app starts with the switch off they are deleted (research R3).
