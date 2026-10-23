package com.workoutrec.monitoring

import com.workoutrec.monitoring.IssueSeverity.ERROR
import com.workoutrec.monitoring.IssueSeverity.INFO
import com.workoutrec.monitoring.IssueSeverity.WARNING
import org.junit.Assert.assertEquals
import org.junit.Test

// Feature 005 contracts/issues.md: stable keys, added never renamed
class NonFatalIssueCatalogTest {

    @Test
    fun `keys and severities match the catalog`() {
        val expected = mapOf(
            NonFatalIssue.SYNC_FAILED to ("sync-failed" to ERROR),
            NonFatalIssue.SYNC_TIMEOUT to ("sync-timeout" to WARNING),
            NonFatalIssue.SPREADSHEET_MISMATCH to ("spreadsheet-mismatch" to WARNING),
            NonFatalIssue.TOKEN_FAILED to ("token-failed" to ERROR),
            NonFatalIssue.DIAGNOSTICS_TEST to ("diagnostics-test" to INFO),
            NonFatalIssue.MONEY_REFRESH_FAILED to ("money-refresh-failed" to ERROR),
            NonFatalIssue.SETUP_STEP_FAILED to ("setup-step-failed" to ERROR),
            NonFatalIssue.SETUP_CHECK_FAILED to ("setup-check-failed" to WARNING),
            NonFatalIssue.SIGN_IN_FAILED to ("sign-in-failed" to ERROR),
            NonFatalIssue.AUTHORIZATION_FAILED to ("authorization-failed" to ERROR),
            NonFatalIssue.SYNC_UNEXPECTED to ("sync-unexpected" to ERROR),
            NonFatalIssue.SIGN_OUT_CLEANUP_FAILED to ("sign-out-cleanup-failed" to WARNING),
            NonFatalIssue.SYNC_LOG_FAILED to ("sync-log-failed" to WARNING),
            NonFatalIssue.SPREADSHEET_CREATE_FALLBACK to ("spreadsheet-create-fallback" to WARNING),
            NonFatalIssue.BAD_SHEET_DATA to ("bad-sheet-data" to WARNING),
            NonFatalIssue.BAD_SAVED_STATE to ("bad-saved-state" to WARNING),
            NonFatalIssue.NO_CONNECTION to ("no-connection" to INFO),
            // Feature 006
            NonFatalIssue.WORKOUT_TAB_MISSING to ("workout-tab-missing" to WARNING),
            NonFatalIssue.SCRIPT_UPGRADE_FAILED to ("script-upgrade-failed" to WARNING),
        )
        assertEquals(expected, NonFatalIssue.entries.associateWith { it.key to it.severity })
    }

    @Test
    fun `keys are unique kebab-case`() {
        val keys = NonFatalIssue.entries.map { it.key }
        assertEquals(keys.size, keys.toSet().size)
        keys.forEach { assert(Regex("[a-z]+(-[a-z]+)*").matches(it)) { it } }
    }
}
