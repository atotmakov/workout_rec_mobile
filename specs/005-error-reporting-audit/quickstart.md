# Quickstart: Validate Error Reporting

## Automated (every PR, CI)

```bash
./gradlew :app:testDebugUnitTest      # includes the guard and all report tests
./gradlew :app:lintDebug
./gradlew :app:connectedDebugAndroidTest   # unchanged UI tests must still pass (SC-004)
```

| # | Check | Expected |
|---|-------|----------|
| 1 | `CatchSiteGuardTest` | passes; prints nothing to fix (SC-001) |
| 2 | Add a temporary `try { } catch (e: Exception) { }` in any app file and rerun | fails, naming the file, line and `Exception`; remove it |
| 3 | Report tests (research R8) | each new issue kind produces exactly one report with its issue name, severity and details (SC-002) |
| 4 | Normal-outcome tests | user cancel, typed dates, file-not-found check, Apps Script API off: no report |
| 5 | Privacy test | no report details contain the fixture exercise names, sums, email or token (SC-003) |
| 6 | Send diagnostics off: `TracerMonitoring` with `isEnabled = false`, every issue kind sent | 0 reports reach the SDK (SC-005) |
| 7 | Limiter test | 6 info reports in a day: the 6th dropped; 20 error reports still allowed after them |

## Device (a build from this branch with Tracer credentials, Send diagnostics on)

| # | Scenario | Expected in the Tracer console (after the next launch with connection) |
|---|----------|----------------------------------------------------------------------|
| 8 | Rename the `money` tab in a test spreadsheet, open the Money tab | Tab shows the same message as before; issue `money-refresh-failed`, `reason=missing-tab` (SC-006). Rename it back |
| 9 | Airplane mode, pull to refresh on Money, then on History | Same offline messages as before; one `no-connection` (info) issue, `operation=money-refresh` (later ones within 30 min are dropped by the limiter) |
| 10 | Cancel the Google account picker during setup | no issue |
| 11 | Account menu → Diagnostics → Send test error | `diagnostics-test` as in 003 (reporting still works end to end) |
| 12 | Turn Send diagnostics off, repeat #8 | nothing new in the console |
