package com.workoutrec.setup

import com.workoutrec.auth.AccountRepository
import com.workoutrec.auth.AuthOutcome
import com.workoutrec.auth.PickResult
import com.workoutrec.data.SelectedAccount
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
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

// Feature 005 US2: a failed setup step is reported once, naming the step
@OptIn(ExperimentalCoroutinesApi::class)
class SetupStepReportsTest {

    private val account = SelectedAccount("a@example.com", "Alex T", null)
    private val monitoring = FakeMonitoring()
    private val setup = FakeSpreadsheetSetupService()
    private val installer = FakeScriptInstaller()

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun pickedAccount(): SetupViewModel {
        val store = FakeSettingsStore(account = null)
        val flow = SpreadsheetSetupFlow(setup, installer, store, timeZone = { "Europe/Moscow" }, now = { Instant.EPOCH }, monitoring = monitoring)
        val vm = SetupViewModel(AccountRepository(FakeApiAuthorizer(AuthOutcome.Granted("t")), store), flow)
        vm.start()
        vm.chooseAccount(FakeAccountPicker(PickResult.Picked(account)))
        return vm
    }

    private val boom = ApiError.Unexpected(500, "boom")

    private fun assertReported(step: SetupStep, vm: SetupViewModel) {
        assertEquals(step, (vm.state.value as SetupState.Error).step)
        assertEquals(listOf(NonFatalIssue.SETUP_STEP_FAILED to "operation=setup; step=${step.name}"), monitoring.nonFatals.map { it.issue to it.details })
    }

    @Test
    fun `finding fails`() = runTest {
        setup.failNext["find"] = boom
        assertReported(SetupStep.Finding, pickedAccount())
    }

    @Test
    fun `creating fails`() = runTest {
        setup.failNext["create"] = boom
        assertReported(SetupStep.Creating, pickedAccount())
    }

    @Test
    fun `checking fails`() = runTest {
        setup.found = DriveFile("existing")
        setup.failNext["check"] = boom
        assertReported(SetupStep.Checking, pickedAccount())
    }

    @Test
    fun `renaming fails`() = runTest {
        setup.found = DriveFile("existing")
        setup.matches = false
        val vm = pickedAccount()
        monitoring.nonFatals.clear() // the mismatch itself is reported by feature 003
        setup.failNext["rewrite"] = boom
        vm.onAction(SetupAction.AnswerRewrite(yes = true))
        assertReported(SetupStep.Renaming, vm)
    }

    @Test
    fun `attaching the script fails`() = runTest {
        installer.failNext = boom
        assertReported(SetupStep.AttachingScript, pickedAccount())
    }

    @Test
    fun `a step without connection is reported as info`() = runTest {
        setup.failNext["find"] = ApiError.Offline
        pickedAccount()
        assertEquals(listOf(NonFatalIssue.NO_CONNECTION to "operation=setup"), monitoring.nonFatals.map { it.issue to it.details })
    }

    @Test
    fun `the Apps Script API being off is not reported`() = runTest {
        installer.apiDisabled = true
        assertEquals(SetupState.NeedsApiSetting, pickedAccount().state.value)
        assertEquals(0, monitoring.nonFatals.size)
    }
}
