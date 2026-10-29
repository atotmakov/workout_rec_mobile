package com.workoutrec.setup

import com.workoutrec.automation.AutomationStatus
import com.workoutrec.automation.AutomationStatusEvaluator
import com.workoutrec.data.SelectedAccount
import com.workoutrec.data.SpreadsheetBinding
import com.workoutrec.fakes.FakeMonitoring
import com.workoutrec.fakes.FakeScriptInstaller
import com.workoutrec.fakes.FakeSettingsStore
import com.workoutrec.fakes.FakeSpreadsheetSetupService
import com.workoutrec.google.ApiError
import com.workoutrec.monitoring.NonFatalIssue
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

// Feature 006 research R7: the app start's background check updates an older attached script
class ScriptUpgradeRefreshTest {

    private val account = SelectedAccount("user@example.com", null, null)
    private val enabled = mapOf(
        AutomationStatusEvaluator.SCRIPT_ID to "script-1",
        AutomationStatusEvaluator.ENABLE_URL to "https://enable",
        AutomationStatusEvaluator.ENABLED_AT to "1970-01-01T00:00:00Z", AutomationStatusEvaluator.DAILY_TRIGGER to "head",
        AutomationStatusEvaluator.LAST_DAILY_RUN to "1970-01-01T00:00:00Z",
    )
    private val installer = FakeScriptInstaller()
    private val monitoring = FakeMonitoring()

    private fun flowWith(metadata: Map<String, String>): SpreadsheetSetupFlow {
        val setup = FakeSpreadsheetSetupService(metadata = metadata, existing = setOf("s1"))
        val store = FakeSettingsStore(account, SpreadsheetBinding(account.email, "s1", "script-1", "https://enable"))
        return SpreadsheetSetupFlow(setup, installer, store, timeZone = { "Europe/Moscow" }, now = { Instant.EPOCH }, monitoring = monitoring)
    }

    @Test
    fun `a script without a recorded version is upgraded`() = runTest {
        val result = flowWith(enabled).refresh(account)
        assertEquals(listOf("upgrade:s1:script-1"), installer.log)
        assertEquals(RefreshResult.Status(AutomationStatus.On), result)
    }

    @Test
    fun `a version 1 script is upgraded`() = runTest {
        flowWith(enabled + (AutomationStatusEvaluator.SCRIPT_VERSION to "1")).refresh(account)
        assertEquals(listOf("upgrade:s1:script-1"), installer.log)
    }

    @Test
    fun `a current script is left alone`() = runTest {
        flowWith(enabled + (AutomationStatusEvaluator.SCRIPT_VERSION to "6")).refresh(account)
        assertEquals(emptyList<String>(), installer.log)
    }

    // Feature 007 FR-015, T042: a 006 script is replaced; automation stays on, nothing is attached again.
    @Test
    fun `a version 2 script is upgraded and automation stays on`() = runTest {
        val result = flowWith(enabled + (AutomationStatusEvaluator.SCRIPT_VERSION to "2")).refresh(account)
        assertEquals(listOf("upgrade:s1:script-1"), installer.log)
        assertEquals(RefreshResult.Status(AutomationStatus.On), result)
    }

    @Test
    fun `a script attached but never enabled is upgraded too`() = runTest {
        flowWith(mapOf(AutomationStatusEvaluator.SCRIPT_ID to "script-1", AutomationStatusEvaluator.SCRIPT_VERSION to "2")).refresh(account)
        assertEquals(listOf("upgrade:s1:script-1"), installer.log)
    }

    @Test
    fun `no script means nothing to upgrade`() = runTest {
        flowWith(emptyMap()).refresh(account)
        assertEquals(emptyList<String>(), installer.log)
    }

    @Test
    fun `a failed upgrade changes nothing for the user, is reported, and is tried again`() = runTest {
        val flow = flowWith(enabled)
        installer.failNext = ApiError.Unexpected(500, "boom")
        assertEquals(RefreshResult.Status(AutomationStatus.On), flow.refresh(account))
        val report = monitoring.nonFatals.single()
        assertEquals(NonFatalIssue.SCRIPT_UPGRADE_FAILED, report.issue)
        assertEquals("operation=script-upgrade", report.details)
        flow.refresh(account)
        assertEquals(listOf("upgrade:s1:script-1", "upgrade:s1:script-1"), installer.log)
    }

    @Test
    fun `no connection is reported as no-connection`() = runTest {
        installer.failNext = ApiError.Offline
        flowWith(enabled).refresh(account)
        assertEquals(NonFatalIssue.NO_CONNECTION, monitoring.nonFatals.single().issue)
        assertEquals("operation=script-upgrade", monitoring.nonFatals.single().details)
    }

    @Test
    fun `the Apps Script API switched off is reported, since the old script stays`() = runTest {
        installer.apiDisabled = true
        flowWith(enabled).refresh(account)
        assertEquals(NonFatalIssue.SCRIPT_UPGRADE_FAILED, monitoring.nonFatals.single().issue)
        assertEquals("Apps Script API disabled", monitoring.nonFatals.single().error.message)
    }
}
