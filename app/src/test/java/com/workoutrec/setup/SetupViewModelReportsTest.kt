package com.workoutrec.setup

import com.workoutrec.auth.AccountRepository
import com.workoutrec.auth.AuthOutcome
import com.workoutrec.auth.PickResult
import com.workoutrec.fakes.FakeAccountPicker
import com.workoutrec.data.SelectedAccount
import com.workoutrec.fakes.FakeApiAuthorizer
import com.workoutrec.fakes.FakeMonitoring
import com.workoutrec.fakes.FakeSettingsStore
import com.workoutrec.google.ApiError
import com.workoutrec.monitoring.NonFatalIssue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

// Feature 005 US1 (setup check) and US2 (sign-in, authorization); contracts/issues.md
@OptIn(ExperimentalCoroutinesApi::class)
class SetupViewModelReportsTest {

    private val account = SelectedAccount("a@example.com", "Alex T", null)
    private val monitoring = FakeMonitoring()

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private class ThrowingRefresh(private val error: Throwable) : PostAuthStep {
        override suspend fun run(account: SelectedAccount) = SetupState.Ready
        override suspend fun refresh(account: SelectedAccount): RefreshResult = throw error
    }

    private fun readyWith(postAuth: PostAuthStep): SetupViewModel {
        val vm = SetupViewModel(AccountRepository(FakeApiAuthorizer(AuthOutcome.Granted("t")), FakeSettingsStore(account)), postAuth, monitoring)
        vm.start()
        return vm
    }

    private fun reports() = monitoring.nonFatals.map { it.issue to it.details }

    @Test
    fun `a failed setup check changes nothing and is reported`() = runTest {
        val vm = readyWith(ThrowingRefresh(ApiError.Unexpected(500, "boom")))
        vm.refreshStatus()
        assertEquals(SetupState.Ready, vm.state.value)
        assertNull(vm.automation.value)
        assertEquals(listOf(NonFatalIssue.SETUP_CHECK_FAILED to "operation=setup-check"), reports())
    }

    @Test
    fun `a setup check without connection is reported as info`() = runTest {
        readyWith(ThrowingRefresh(ApiError.Offline)).refreshStatus()
        assertEquals(listOf(NonFatalIssue.NO_CONNECTION to "operation=setup-check"), reports())
    }

    @Test
    fun `a cancelled setup check is not reported`() = runTest {
        readyWith(ThrowingRefresh(CancellationException("screen closed"))).refreshStatus()
        assertEquals(emptyList<Any>(), reports())
    }

    // US2: sign-in and authorization
    private val readyStep = object : PostAuthStep {
        override suspend fun run(account: SelectedAccount) = SetupState.Ready
    }

    private fun signedOut(vararg outcomes: AuthOutcome): SetupViewModel {
        val vm = SetupViewModel(AccountRepository(FakeApiAuthorizer(*outcomes), FakeSettingsStore(null)), readyStep, monitoring)
        vm.start()
        return vm
    }

    @Test
    fun `a failed account pick returns to the chooser and is reported`() = runTest {
        val vm = signedOut()
        vm.chooseAccount(FakeAccountPicker(PickResult.Failed(type = "TYPE_NO_CREDENTIAL")))
        assertEquals(SetupState.SignedOut(SetupMessage.AccountRequired), vm.state.value)
        assertEquals(listOf(NonFatalIssue.SIGN_IN_FAILED to "type=TYPE_NO_CREDENTIAL"), reports())
    }

    @Test
    fun `a failed account switch keeps the screen and is reported`() = runTest {
        val vm = readyWith(readyStep)
        vm.switchAccount(FakeAccountPicker(PickResult.Failed(type = "TYPE_NO_CREDENTIAL")))
        assertEquals(SetupState.Ready, vm.state.value)
        assertEquals(listOf(NonFatalIssue.SIGN_IN_FAILED to "type=TYPE_NO_CREDENTIAL"), reports())
    }

    @Test
    fun `a cancelled pick or switch is not reported`() = runTest {
        signedOut().chooseAccount(FakeAccountPicker(PickResult.Cancelled))
        readyWith(readyStep).switchAccount(FakeAccountPicker(PickResult.Cancelled))
        assertEquals(emptyList<Any>(), reports())
    }

    @Test
    fun `a Google error while authorizing is reported with its status`() = runTest {
        val vm = signedOut(AuthOutcome.Failed(10))
        vm.chooseAccount(FakeAccountPicker(PickResult.Picked(account)))
        assertEquals(SetupState.SignedOut(SetupMessage.AccountRequired), vm.state.value)
        assertEquals(listOf(NonFatalIssue.AUTHORIZATION_FAILED to "operation=authorization; outcome=Failed; status=10"), reports())
    }

    @Test
    fun `authorizing without connection is reported as info`() = runTest {
        signedOut(AuthOutcome.Unavailable).chooseAccount(FakeAccountPicker(PickResult.Picked(account)))
        assertEquals(listOf(NonFatalIssue.NO_CONNECTION to "operation=authorization"), reports())
    }

    @Test
    fun `a refused or cancelled consent is not reported`() = runTest {
        signedOut(AuthOutcome.Denied).chooseAccount(FakeAccountPicker(PickResult.Picked(account)))
        assertEquals(emptyList<Any>(), reports())
    }

    @Test
    fun `a Google error on launch is reported`() = runTest {
        val vm = SetupViewModel(AccountRepository(FakeApiAuthorizer(AuthOutcome.Failed(8)), FakeSettingsStore(account)), readyStep, monitoring)
        vm.start()
        assertEquals(listOf(NonFatalIssue.AUTHORIZATION_FAILED to "operation=authorization; outcome=Failed; status=8"), reports())
    }
}
