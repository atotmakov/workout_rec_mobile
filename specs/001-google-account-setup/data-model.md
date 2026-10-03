# Data Model: Google Account Setup

**Feature**: `001-google-account-setup` | **Date**: 2026-10-03 | **Plan**: [plan.md](plan.md)

The spreadsheet itself is the source of truth (constitution Principle III). The phone stores only
the selected account and setup progress. The spreadsheet layout is defined in
[contracts/spreadsheet.md](contracts/spreadsheet.md).

## Stored on the phone (DataStore)

### SelectedAccount

| Field | Type | Rules |
|-------|------|-------|
| email | String | Required; non-empty; identifies the account |
| displayName | String? | From Google; may be missing |
| photoUrl | String? | From Google; when missing the avatar shows initials |

- Exactly zero or one at a time. Cleared on sign-out (FR-012) or revoked access (edge case).
- Initials: first letter of the first two words of `displayName`, else the first letter of
  `email`, upper-cased.

### SpreadsheetBinding

| Field | Type | Rules |
|-------|------|-------|
| accountEmail | String | Must equal `SelectedAccount.email`; binding is discarded when the account changes |
| spreadsheetId | String | Drive file ID of `workout_rec_database_` |
| scriptId | String? | Set after the script is attached |
| enableUrl | String? | "Enable automation" page URL; set after deployment |

- Cache only; on reinstall it is rebuilt from Drive search plus spreadsheet developer metadata.

## Read from the spreadsheet (not stored)

### SpreadsheetSnapshot

Result of the single structure read (research R6): sheet titles in order, row 1 values per tab,
`rec!A1:D2` values, formulas, and drop-down, `rec` conditional format rules, and developer
metadata (key → value).

### StructureCheckResult

- `Match`
- `Mismatch(reasons: List<MismatchReason>)`: for example `MissingTab("money")`,
  `WrongHeader(tab, column, expected, actual)`, `MissingRecFormula`, `MissingRecDropDown`,
  `MissingConditionalRule(index)`.
- Ignored: data rows, extra tabs, extra columns after the expected ones, values of `rec!A1` and
  `balance!A1`, and anything about the script (that is `AutomationStatus`).

### AutomationStatus (research R10)

| Status | Condition (developer metadata) | App shows |
|--------|-------------------------------|-----------|
| ScriptMissing | no `workout_rec.script_id` | Reminder: attach script (step 1 if the Apps Script API setting is off) |
| NotEnabled | script id present, no `workout_rec.automation_enabled_at` | Reminder: open "Enable automation" (step 2) |
| On | enabled, and the newer of `last_daily_run` / `automation_enabled_at` is ≤ 48 h old | No reminder |
| Stopped | enabled, but both timestamps are > 48 h old | Reminder: automation stopped, open "Enable automation" again |

## Setup flow (state machine)

```text
SignedOut
  └─ choose account ─▶ Authorizing ──denied──▶ SignedOut (message + "choose again")
                         │ granted
                         ▼
                      FindingSpreadsheet ──none──▶ Creating ──▶ AttachingScript
                         │ found
                         ▼
                      CheckingStructure ──Match──▶ AttachingScript (only if ScriptMissing) / Ready
                         │ Mismatch
                         ▼
                      AskRewrite ──No──▶ SignedOut (account chooser, nothing changed)
                         │ Yes
                         ▼
                      RenamingToBackup ──▶ Creating

AttachingScript ──Apps Script API off──▶ NeedsApiSetting ──(user returns)──▶ AttachingScript
AttachingScript ──attached──▶ NeedsEnable ──(user returns, status On)──▶ Ready
NeedsApiSetting / NeedsEnable ──"Continue for now"──▶ Ready (with reminder, FR-015)
Any network step ──failure──▶ Error(step, reason) ──Retry──▶ same step
```

- **Ready** shows the main screen with the avatar; on later launches the app goes straight to
  Ready using the stored account (FR-004), refreshes the token silently, and re-reads
  `AutomationStatus` in the background when online.
- **Idempotence (FR-009)**: Creating is one all-or-nothing call; a crash before it finishes
  creates nothing. A crash after it is recovered by FindingSpreadsheet, which finds the new
  file, so no duplicate is made. AttachingScript first reads `workout_rec.script_id` and skips
  creation when present.
- **Switch account**: clears `SpreadsheetBinding`, goes to Authorizing for the new account.
- **Sign out**: confirm, clear credential state and local data, go to SignedOut.

## Naming rules

- Spreadsheet: `workout_rec_database_` (exact).
- Backup: `workout_rec_database_backup_<yyyy-MM-dd>` in the phone's local date; when taken,
  `_2`, `_3`, … are appended in order.
- Script project title: `workout_rec_automation`.
