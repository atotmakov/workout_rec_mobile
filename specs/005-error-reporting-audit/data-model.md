# Data Model: Report Every Handled Error to AppTracer

No database or spreadsheet changes. The "entities" are an enum, a file in the repository and a
limiter's in-memory state.

## Issue kind (`NonFatalIssue`)

| Field | Type | Rules |
|-------|------|-------|
| name | enum constant | one per kind in [contracts/issues.md](contracts/issues.md) |
| key | string | kebab-case, unique, never changed once released |
| severity | `IssueSeverity` | ERROR, WARNING or INFO; only `NO_CONNECTION` and `DIAGNOSTICS_TEST` are INFO |

Existing four keys keep their values (spec FR-003). Twelve values are added (spec FR-002, FR-011).

## Report details

A string of `key=value` pairs joined by `; `. Keys: `operation`, `reason`, `step`, `type`,
`outcome`, `status`, `action`, `code`, `value`. Values come only from enums, class names or
numbers (contracts/issues.md). Free text from the sheet, the account, or an exception message
MUST NOT be put in details. The exception itself carries its message and stack trace, as in 003.

## Bad value (`BadValue`)

A small exception for fallbacks without a thrown error (time zone, timestamp, saved enum value).

| Field | Rules |
|-------|-------|
| value | one of `sheet-time-zone`, `automation-timestamp`, `sync-phase`, `automation-status`, `money-error` |
| message | `"Unreadable <value>"`; never the unreadable content |
| cause | none: a parsing exception's message can contain the unreadable text (FR-007); the stack trace is the report site's |

## Normal-outcome entry (`app/src/test/resources/normal-outcomes.txt`)

One line per catch site that is allowed not to report (spec FR-004, FR-012):

```text
# path (under app/src/main/java/com/workoutrec/) | caught type | count | reason
auth/AccountPicker.kt | GetCredentialCancellationException | 1 | user cancelled sign-in
```

| Field | Rules |
|-------|-------|
| path | relative to `app/src/main/java/com/workoutrec/`, forward slashes |
| caught type | simple name as written after `catch (e: `, or `runCatching` |
| count | how many such sites in the file; must equal the number found |
| reason | required, non-empty, one line |

Validation by the guard (research R7): every unreported site matches an entry; every entry
matches exactly `count` sites; a site that reports or rethrows must not be listed.

Initial entries (from research R4 and the audit):

| Path | Caught type | Reason |
|------|-------------|--------|
| `auth/AccountPicker.kt` | `GetCredentialCancellationException` | user cancelled sign-in |
| `auth/AccountPicker.kt` | `GetCredentialException` | converted to `PickResult.Failed`; `SetupViewModel` reports |
| `auth/ApiAuthorizer.kt` | `ApiException` ×2, `IOException` | converted to an outcome; the caller reports |
| `log/PastWorkoutScreens.kt` | `DateTimeParseException` ×2 | text the user typed; the form shows it |
| `log/LogRoutes.kt` | `DateTimeException` | same value reported by sync (`LogSync.zoneOf`) |
| `spreadsheet/SpreadsheetSetupService.kt` | `ApiError.NotFound` | "file gone" is the answer the check asks for |
| `setup/SpreadsheetSetupFlow.kt` | `ApiError.AppsScriptApiDisabled` | a setup instruction shown to the user |
| `google/ApiError.kt` | `runCatching` | error body is not JSON; the raw body is used in the message |
| `monitoring/TracerMonitoring.kt` | `Throwable` | reporting itself failed; cannot report |

Sites ending in `throw` (cancellation and conversion) are exempt automatically and not listed.

## Limiter state (`NonFatalLimiter`, in memory per process)

| Field | Change |
|-------|--------|
| `lastSent` per issue | unchanged |
| `day`, `sentToday` | now counts only ERROR and WARNING |
| `infoToday` | new: INFO reports today; at most `MAX_INFO_PER_DAY = 5` |
