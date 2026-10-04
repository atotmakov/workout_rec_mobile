package com.workoutrec.setup

import com.workoutrec.auth.AccountRepository
import com.workoutrec.auth.AuthOutcome
import com.workoutrec.auth.PickResult
import com.workoutrec.automation.AutomationStatus
import com.workoutrec.data.SelectedAccount
import com.workoutrec.data.SpreadsheetBinding
import com.workoutrec.fakes.FakeAccountPicker
import com.workoutrec.fakes.FakeApiAuthorizer
import com.workoutrec.fakes.FakeScriptInstaller
import com.workoutrec.fakes.FakeSettingsStore
import com.workoutrec.fakes.FakeSpreadsheetSetupService
import com.workoutrec.google.ApiError
import com.workoutrec.google.DriveFile
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
        val flow = SpreadsheetSetupFlow(setup, installer, store, timeZone = { "Europe/Moscow" }, now = { now })
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

    @Test
    fun `no spreadsheet - creates it, attaches the script, shows step 2`() = runTest {
        val vm = pickedAccount()
        assertEquals(listOf("find", "create", "tables:created", "install:created"), log)
        assertEquals(SetupState.NeedsEnable("https://enable"), vm.state.value)
        assertEquals(SpreadsheetBinding(account.email, "created", "script-1", "https://enable"), store.binding.first())
    }

    @Test
    fun `failed table creation - Retry adds the tables to the same spreadsheet, no second create`() = runTest {
        setup.failNext["ensureTables"] = ApiError.ServiceUnavailable(503)
        val vm = pickedAccount()
        assertEquals(SetupState.Error(SetupStep.Creating, ApiError.ServiceUnavailable(503)), vm.state.value)
        assertEquals("created", store.binding.first()?.spreadsheetId)
        log.clear()
        vm.onAction(SetupAction.Retry)
        assertEquals(listOf("tables:created", "check:created", "install:created"), log)
        assertEquals(SetupState.NeedsEnable("https://enable"), vm.state.value)
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
    fun `matching spreadsheet without script - attaches it`() = runTest {
        setup.found = DriveFile("existing")
        val vm = pickedAccount()
        assertEquals(listOf("find", "check:existing", "install:existing"), log)
        assertEquals(SetupState.NeedsEnable("https://enable"), vm.state.value)
    }

    @Test
    fun `matching spreadsheet with script but not enabled - step 2`() = runTest {
        setup.found = DriveFile("existing")
        setup.metadata = mapOf("workout_rec.script_id" to "s1", "workout_rec.enable_url" to "https://e2")
        val vm = pickedAccount()
        assertEquals(SetupState.NeedsEnable("https://e2"), vm.state.value)
        assertTrue(log.none { it.startsWith("install") })
    }

    @Test
    fun `mismatch asks to rewrite`() = runTest {
        setup.found = DriveFile("existing")
        setup.matches = false
        val vm = pickedAccount()
        assertEquals(SetupState.AskRewrite("existing"), vm.state.value)
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
    fun `rewrite Yes renames, creates, attaches`() = runTest {
        setup.found = DriveFile("existing")
        setup.matches = false
        val vm = pickedAccount()
        log.clear()
        vm.onAction(SetupAction.AnswerRewrite(yes = true))
        assertEquals(listOf("rename:existing", "create", "tables:rewritten", "install:rewritten"), log)
        assertEquals(SetupState.NeedsEnable("https://enable"), vm.state.value)
        assertEquals("rewritten", store.binding.first()?.spreadsheetId)
    }

    @Test
    fun `Apps Script API off - step 1, then recheck attaches`() = runTest {
        installer.apiDisabled = true
        val vm = pickedAccount()
        assertEquals(SetupState.NeedsApiSetting, vm.state.value)
        installer.apiDisabled = false
        vm.onAction(SetupAction.Recheck)
        assertEquals(SetupState.NeedsEnable("https://enable"), vm.state.value)
    }

    @Test
    fun `step 2 recheck with automation on - Ready`() = runTest {
        val vm = pickedAccount()
        setup.metadata = enabledNow
        vm.onAction(SetupAction.Recheck)
        assertEquals(SetupState.Ready, vm.state.value)
    }

    @Test
    fun `step 2 recheck while still not enabled stays on step 2`() = runTest {
        val vm = pickedAccount()
        setup.metadata = mapOf("workout_rec.script_id" to "s1", "workout_rec.enable_url" to "https://enable")
        vm.onAction(SetupAction.Recheck)
        assertEquals(SetupState.NeedsEnable("https://enable"), vm.state.value)
    }

    @Test
    fun `continue for now - Ready with a reminder`() = runTest {
        val vm = pickedAccount()
        vm.onAction(SetupAction.ContinueForNow)
        assertEquals(SetupState.Ready, vm.state.value)
        setup.existing = setOf("created")
        setup.metadata = mapOf("workout_rec.script_id" to "script-1", "workout_rec.enable_url" to "https://enable")
        vm.refreshStatus()
        assertEquals(Reminder(AutomationStatus.NotEnabled, "https://enable"), vm.reminder.value)
    }

    @Test
    fun `failure shows the step and Retry repeats it`() = runTest {
        setup.failNext["find"] = ApiError.Offline
        val vm = pickedAccount()
        assertEquals(SetupState.Error(SetupStep.Finding, ApiError.Offline), vm.state.value)
        vm.onAction(SetupAction.Retry)
        assertEquals(listOf("find", "find", "create", "tables:created", "install:created"), log)
        assertEquals(SetupState.NeedsEnable("https://enable"), vm.state.value)
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
        assertEquals(listOf("exists:stored", "find", "create", "tables:created", "install:created"), log)
        assertEquals("created", store.binding.first()?.spreadsheetId)
        assertEquals(SetupState.NeedsEnable("https://enable"), vm.state.value)
    }

    @Test
    fun `refresh - existing spreadsheet updates the reminder`() = runTest {
        setup.existing = setOf("stored")
        setup.metadata = mapOf("workout_rec.script_id" to "s1", "workout_rec.enable_url" to "https://e3")
        val vm = pickedAccount(binding = SpreadsheetBinding(account.email, "stored", "s1", "https://e3"))
        vm.refreshStatus()
        assertEquals(Reminder(AutomationStatus.NotEnabled, "https://e3"), vm.reminder.value)
        assertEquals(SetupState.Ready, vm.state.value)
    }

    @Test
    fun `reminder action for a missing script retries attaching`() = runTest {
        setup.existing = setOf("stored")
        val vm = pickedAccount(binding = SpreadsheetBinding(account.email, "stored", null, null))
        vm.refreshStatus()
        assertEquals(AutomationStatus.ScriptMissing, vm.reminder.value?.status)
        vm.onAction(SetupAction.FixAutomation)
        assertTrue("install:stored" in log)
        assertEquals(SetupState.NeedsEnable("https://enable"), vm.state.value)
    }
}
