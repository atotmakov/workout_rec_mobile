package com.workoutrec.setup

import com.workoutrec.auth.AccountRepository
import com.workoutrec.auth.AuthOutcome
import com.workoutrec.auth.PickResult
import com.workoutrec.automation.AutomationStatus
import com.workoutrec.data.SelectedAccount
import com.workoutrec.data.SpreadsheetBinding
import com.workoutrec.fakes.FakeAccountPicker
import com.workoutrec.fakes.FakeApiAuthorizer
import com.workoutrec.fakes.FakeMonitoring
import com.workoutrec.fakes.FakeScriptInstaller
import com.workoutrec.fakes.FakeSettingsStore
import com.workoutrec.fakes.FakeSpreadsheetSetupService
import com.workoutrec.google.ApiError
import com.workoutrec.google.DriveFile
import com.workoutrec.monitoring.NonFatalIssue
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

// US2 acceptance scenarios; data-model.md "Setup flow"
@OptIn(ExperimentalCoroutinesApi::class)
class SetupViewModelSpreadsheetTest {

    private val account = SelectedAccount("a@example.com", "Alex T", null)
    private val now = Instant.parse("2026-10-05T12:00:00Z")
    private val log = mutableListOf<String>()
    private val diagnostics = mutableListOf<String>()
    private val monitoring = FakeMonitoring()
    private val setup = FakeSpreadsheetSetupService(log = log)
    private val installer = FakeScriptInstaller(log = log)
    private lateinit var store: FakeSettingsStore

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    /**
     * Without [binding]: first launch, the user picks [account] and grants access.
     * With [binding]: a later launch with the account and binding already stored.
     */
    private fun pickedAccount(binding: SpreadsheetBinding? = null): SetupViewModel {
        store = FakeSettingsStore(account = if (binding != null) account else null, binding = binding)
        val flow = SpreadsheetSetupFlow(setup, installer, store, timeZone = { "Europe/Moscow" }, now = { now }, log = { diagnostics += it }, monitoring = monitoring)
        val vm = SetupViewModel(AccountRepository(FakeApiAuthorizer(AuthOutcome.Granted("t")), store), flow)
        vm.start()
        if (binding == null) vm.chooseAccount(FakeAccountPicker(PickResult.Picked(account)))
        return vm
    }

    private val enabledNow = mapOf(
        "workout_rec.script_id" to "s1",
        "workout_rec.enable_url" to "https://enable",
        "workout_rec.automation_enabled_at" to "2026-10-05T10:00:00Z",
    )

    // Feature 007 FR-001: setup ends at Today; the script steps are an optional action.
    @Test
    fun `no spreadsheet - creates it and opens Today, no script attached`() = runTest {
        val vm = pickedAccount()
        assertEquals(listOf("find", "create", "tables:created"), log)
        assertEquals(SetupState.Ready, vm.state.value)
        assertEquals(SpreadsheetBinding(account.email, "created", null, null), store.binding.first())
    }

    @Test
    fun `failed table creation - Retry adds the tables to the same spreadsheet, no second create`() = runTest {
        setup.failNext["ensureTables"] = ApiError.ServiceUnavailable(503)
        val vm = pickedAccount()
        assertEquals(SetupState.Error(SetupStep.Creating, ApiError.ServiceUnavailable(503)), vm.state.value)
        assertEquals("created", store.binding.first()?.spreadsheetId)
        log.clear()
        vm.onAction(SetupAction.Retry)
        assertEquals(listOf("tables:created"), log)
        assertEquals(SetupState.Ready, vm.state.value)
    }

    @Test
    fun `matching spreadsheet with automation on - Ready, nothing written`() = runTest {
        setup.found = DriveFile("existing")
        setup.metadata = enabledNow
        val vm = pickedAccount()
        assertEquals(SetupState.Ready, vm.state.value)
        assertEquals(emptyList<String>(), setup.writeCalls)
        assertEquals("existing", store.binding.first()?.spreadsheetId)
    }

    @Test
    fun `matching spreadsheet without script - Ready, nothing attached`() = runTest {
        setup.found = DriveFile("existing")
        val vm = pickedAccount()
        assertEquals(listOf("find", "check:existing"), log)
        assertEquals(SetupState.Ready, vm.state.value)
    }

    @Test
    fun `matching spreadsheet with script but not enabled - Ready`() = runTest {
        setup.found = DriveFile("existing")
        setup.metadata = mapOf("workout_rec.script_id" to "s1", "workout_rec.enable_url" to "https://e2")
        val vm = pickedAccount()
        assertEquals(SetupState.Ready, vm.state.value)
        assertTrue(log.none { it.startsWith("install") })
    }

    @Test
    fun `mismatch asks to rewrite`() = runTest {
        setup.found = DriveFile("existing")
        setup.matches = false
        val vm = pickedAccount()
        assertEquals(SetupState.AskRewrite("existing"), vm.state.value)
    }

    // Sign out, sign in: the rewrite question for a spreadsheet the user considers fine. The shared
    // sync log must say which structure rule failed.
    @Test
    fun `a mismatch writes its reasons to the diagnostic log`() = runTest {
        setup.found = DriveFile("existing")
        setup.matches = false
        pickedAccount()
        val line = diagnostics.single { it.startsWith("setup: spreadsheet existing does not match") }
        assertTrue(line, "MissingTab(tab=log)" in line && "MissingTab(tab=balance)" in line)
    }

    @Test
    fun `a match is written to the diagnostic log`() = runTest {
        setup.found = DriveFile("existing")
        setup.metadata = enabledNow
        pickedAccount()
        assertTrue(diagnostics.toString(), "setup: spreadsheet existing matches" in diagnostics)
    }

    // Feature 003 FR-005: a mismatch also reaches monitoring, with the failed rules.
    @Test
    fun `a mismatch reports spreadsheet-mismatch with its reasons`() = runTest {
        setup.found = DriveFile("existing")
        setup.matches = false
        pickedAccount()
        val event = monitoring.nonFatals.single()
        assertEquals(NonFatalIssue.SPREADSHEET_MISMATCH, event.issue)
        assertTrue(event.error.message.orEmpty(), "MissingTab(tab=balance)" in event.error.message.orEmpty())
    }

    @Test
    fun `a match reports nothing`() = runTest {
        setup.found = DriveFile("existing")
        setup.metadata = enabledNow
        pickedAccount()
        assertTrue(monitoring.nonFatals.isEmpty())
    }

    @Test
    fun `rewrite No returns to the account chooser and changes nothing`() = runTest {
        setup.found = DriveFile("existing")
        setup.matches = false
        val vm = pickedAccount()
        vm.onAction(SetupAction.AnswerRewrite(yes = false))
        assertEquals(SetupState.SignedOut(message = null), vm.state.value)
        assertEquals(emptyList<String>(), setup.writeCalls)
        assertNull(store.account.first())
    }

    @Test
    fun `rewrite Yes renames and creates, then Ready`() = runTest {
        setup.found = DriveFile("existing")
        setup.matches = false
        val vm = pickedAccount()
        log.clear()
        vm.onAction(SetupAction.AnswerRewrite(yes = true))
        assertEquals(listOf("rename:existing", "create", "tables:rewritten"), log)
        assertEquals(SetupState.Ready, vm.state.value)
        assertEquals("rewritten", store.binding.first()?.spreadsheetId)
    }

    // Feature 007 FR-012, FR-013: the automation entry starts the same two steps.
    @Test
    fun `start automation with the Apps Script API off - step 1, then recheck attaches`() = runTest {
        val vm = pickedAccount()
        installer.apiDisabled = true
        vm.onAction(SetupAction.StartAutomation)
        assertEquals(SetupState.NeedsApiSetting, vm.state.value)
        installer.apiDisabled = false
        vm.onAction(SetupAction.Recheck)
        assertEquals(SetupState.NeedsEnable("https://enable"), vm.state.value)
    }

    @Test
    fun `start automation for a missing script attaches it and shows step 2`() = runTest {
        setup.existing = setOf("stored")
        val vm = pickedAccount(binding = SpreadsheetBinding(account.email, "stored", null, null))
        vm.onAction(SetupAction.StartAutomation)
        assertTrue("install:stored" in log)
        assertEquals(SetupState.NeedsEnable("https://enable"), vm.state.value)
    }

    @Test
    fun `step 2 recheck with automation on - Ready and the status is On`() = runTest {
        val vm = pickedAccount()
        vm.onAction(SetupAction.StartAutomation)
        setup.existing = setOf("created")
        setup.metadata = enabledNow
        vm.onAction(SetupAction.Recheck)
        assertEquals(SetupState.Ready, vm.state.value)
        assertEquals(AutomationStatus.On, vm.automation.value)
    }

    @Test
    fun `step 2 recheck while still not enabled stays on step 2`() = runTest {
        val vm = pickedAccount()
        vm.onAction(SetupAction.StartAutomation)
        setup.metadata = mapOf("workout_rec.script_id" to "s1", "workout_rec.enable_url" to "https://enable")
        vm.onAction(SetupAction.Recheck)
        assertEquals(SetupState.NeedsEnable("https://enable"), vm.state.value)
    }

    @Test
    fun `not now - back to Ready, automation still off`() = runTest {
        val vm = pickedAccount()
        vm.onAction(SetupAction.StartAutomation)
        setup.existing = setOf("created")
        setup.metadata = mapOf("workout_rec.script_id" to "script-1", "workout_rec.enable_url" to "https://enable")
        vm.onAction(SetupAction.ContinueForNow)
        assertEquals(SetupState.Ready, vm.state.value)
        assertEquals(AutomationStatus.NotEnabled, vm.automation.value)
    }

    @Test
    fun `start automation when it is already on - Ready`() = runTest {
        setup.existing = setOf("stored")
        setup.metadata = enabledNow
        val vm = pickedAccount(binding = SpreadsheetBinding(account.email, "stored", "s1", "https://enable"))
        vm.onAction(SetupAction.StartAutomation)
        assertEquals(SetupState.Ready, vm.state.value)
        assertTrue(log.none { it.startsWith("install") })
    }

    @Test
    fun `failure shows the step and Retry repeats it`() = runTest {
        setup.failNext["find"] = ApiError.Offline
        val vm = pickedAccount()
        assertEquals(SetupState.Error(SetupStep.Finding, ApiError.Offline), vm.state.value)
        vm.onAction(SetupAction.Retry)
        assertEquals(listOf("find", "find", "create", "tables:created"), log)
        assertEquals(SetupState.Ready, vm.state.value)
    }

    @Test
    fun `stored binding - Ready without network on launch`() = runTest {
        val vm = pickedAccount(binding = SpreadsheetBinding(account.email, "stored", "s1", "https://enable"))
        assertEquals(SetupState.Ready, vm.state.value)
        assertTrue(log.isEmpty())
    }

    @Test
    fun `refresh - trashed or missing spreadsheet runs setup again`() = runTest {
        val vm = pickedAccount(binding = SpreadsheetBinding(account.email, "stored", "s1", "https://enable"))
        vm.refreshStatus()
        assertEquals(listOf("exists:stored", "find", "create", "tables:created"), log)
        assertEquals("created", store.binding.first()?.spreadsheetId)
        assertEquals(SetupState.Ready, vm.state.value)
    }

    // Feature 007 FR-002: the status feeds the automation entry; nothing reminds the user.
    @Test
    fun `refresh - existing spreadsheet updates the automation status`() = runTest {
        setup.existing = setOf("stored")
        setup.metadata = mapOf("workout_rec.script_id" to "s1", "workout_rec.enable_url" to "https://e3")
        val vm = pickedAccount(binding = SpreadsheetBinding(account.email, "stored", "s1", "https://e3"))
        vm.refreshStatus()
        assertEquals(AutomationStatus.NotEnabled, vm.automation.value)
        assertEquals(SetupState.Ready, vm.state.value)
    }

    @Test
    fun `refresh - no script is ScriptMissing, nothing attached`() = runTest {
        setup.existing = setOf("stored")
        val vm = pickedAccount(binding = SpreadsheetBinding(account.email, "stored", null, null))
        vm.refreshStatus()
        assertEquals(AutomationStatus.ScriptMissing, vm.automation.value)
        assertTrue(log.none { it.startsWith("install") })
    }
}
