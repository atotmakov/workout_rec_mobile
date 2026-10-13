# Quickstart: validating app monitoring on the phone

The CI loop (JVM tests and emulator UI tests) covers the app's logic. Tracer itself can only be
checked on the phone with a build that has the token. Record results in
`quickstart-results.md`, as in features 001 and 002.

## Prerequisites

1. A Tracer organization and an Android project for `com.workoutrec` at apptracer.ru. Note the
   project's **app token** and **plugin token** (project Settings).
2. Repo secrets `TRACER_APP_TOKEN` and `TRACER_PLUGIN_TOKEN` set.
3. `docs/privacy.html` updated with the Diagnostics section and published (FR-014).
4. The GitHub APK from a master build after this feature merges, installed on the phone.

## Scenarios

| # | Scenario | Steps | Expected |
|---|---|---|---|
| 0 | Storage location (R3, to verify) | Diagnostics test → Crash now, with the phone in airplane mode; reopen, then share the sync log (its `storage:` line lists the cache folder on start) | The folder where Tracer keeps unsent reports is known; fill it into the code if it differs from the plan |
| 1 | Crash (US1) | Diagnostics test → Crash now; reopen the app with network | Within 10 min: a crash in console env `github`, build N, readable `com.workoutrec` frames, the "Logs" tab with recent lines (SC-001, SC-002) |
| 2 | Freeze (US1) | Diagnostics test → Freeze for 10 s; wait for the "isn't responding" dialog; close the app; reopen | An ANR event with the main-thread stack |
| 3 | Offline crash (US1) | Airplane mode → Crash now → reopen offline → turn network on → reopen | The crash arrives after network returns |
| 4 | Sync failure (US2) | Wi-Fi with no internet, or a revoked token; pull to refresh twice within 30 min | One `sync-failed` / `sync-timeout` event (limiter), with log lines |
| 5 | Spreadsheet mismatch (US2) | Rename the `balance` tab; sign out and in; answer No; rename back | One `spreadsheet-mismatch` event listing `MissingTab(balance)` |
| 6 | Memory snapshot (US3) | Diagnostics test → Memory snapshot now; leave the phone idle and charging overnight | A memory report in "Memory leaks" next day |
| 7 | Disk usage (US4) | Diagnostics test → Disk check now | Nothing (under 50 MB) — expected; optional: the debug-limit check from the docs |
| 8 | Profiling (US5) | Diagnostics test → Profile next app start; restart the app; pull to refresh 10 times | Profiles `app-start` and (with ~1/10 luck per sync) `sync` with sections `info`, `read`, `write`, `commit` |
| 9 | Switch off (US6) | Turn Send diagnostics off; Crash now; reopen with network | Nothing new in the console, now or after a day |
| 10 | Switch on again (US6) | Turn it on ("Applies after restart"); restart; Crash now; reopen | The crash arrives |
| 11 | Privacy (SC-005) | Open one event of each kind in the console | No exercise names, weights, email or token anywhere |
| 12 | Overhead (SC-004) | Time 5 cold starts and 5 syncs with the switch on and 5 with it off (restart after toggling) | Average difference ≤ 5% |
| 13 | CI silence (SC-006) | After a few PR runs, filter the console by build numbers of PR runs | No events from CI |

## Commands

```powershell
# set the Tracer secrets (user)
gh secret set TRACER_APP_TOKEN
gh secret set TRACER_PLUGIN_TOKEN
```

Everything else is done on the phone and in the Tracer console.
