# Contract: What the app sends to Tracer

The interface between the app and the Tracer console. A change to an issue key, profile tag or
environment name is a change to this contract (saved console filters and alerts depend on them).

## Project and environments

| Build | Tracer environment | Uploads |
|---|---|---|
| GitHub APK (debug build type, CI with `TRACER_APP_TOKEN`) | `github` | yes (`setDebugUpload(true)`) |
| Google Play bundle (release build type, CI with token) | `play` | yes |
| Any build without `TRACER_APP_TOKEN` (local, forks, CI emulator job) | — | Tracer disabled (`isDisabled`) |

App version in reports = `versionName` (`0.1.N`) and `versionCode` (`N`), where N is the CI build number, so
`0.1.N` in the console is the GitHub release `build-N`.

## Events

| Kind | Key / tag | Severity | Attached |
|---|---|---|---|
| Crash | (by stack) | — | device, OS, version, recent log lines |
| Freeze (ANR) | (by main-thread stack) | — | same |
| Non-fatal | `sync-failed` | ERROR | exception, recent log lines |
| Non-fatal | `sync-timeout` | WARNING | recent log lines |
| Non-fatal | `spreadsheet-mismatch` | WARNING | mismatch reasons, recent log lines |
| Non-fatal | `token-failed` | ERROR | outcome name, recent log lines |
| Non-fatal | `diagnostics-test` | INFO | from the hidden test actions only |
| Memory snapshot | `oom` / `manual` | — | cleaned snapshot (SDK) |
| Disk usage | (SDK) | — | sizes by folder over 50 MB total |
| Profile | `app-start`, `sync`, `today-open` (+ SDK suffix `_done`) | — | per-method timings; `sync` with sections `info`, `read`, `write`, `commit` |

Limits: non-fatals at most 1 per issue per 30 min and 20 per day per process (data-model
`NonFatalLimiter`).

**Feature 005** adds 12 non-fatal keys (money refresh, setup steps and check, sign-in,
authorization, sync around a run, sign-out cleanup, sync log, create fallback, bad sheet data, bad
saved state, and `no-connection` at INFO). Since 005, `sync-failed` and `token-failed` no longer
cover no-connection failures, which go to `no-connection`; Google's error text is never sent; and
info events have their own budget of 5 a day, so the 20 a day count only errors and warnings. See
[005 contracts/issues.md](../../005-error-reporting-audit/contracts/issues.md).

## Never sent (FR-013)

- Workout data: exercise names, weights, reps, set times, drafts.
- The Google account email, access tokens, the OAuth client IDs.
- Log lines containing `@` or `ya29.` (`LogLineGuard`).
- No user ID is set (`Tracer.setUserId` is not called); the SDK counts installs anonymously.

## UI: Send diagnostics (account menu)

| Element | Behavior |
|---|---|
| Switch **Send diagnostics** (RU: «Отправлять диагностику») | Default on; toggling saves at once |
| Supporting text when turned on but the process started with it off | "Applies after restart" (RU: «Включится после перезапуска») |
| Long-press on the switch row | Opens **Diagnostics test** (R10): Crash now · Freeze for 10 s · Send test error · Memory snapshot now · Disk check now · Profile next app start |

Test tags: `account_send_diagnostics`, `diagnostics_test_dialog`.
