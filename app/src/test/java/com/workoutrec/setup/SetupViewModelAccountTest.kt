package com.workoutrec.setup

import com.workoutrec.auth.AccountRepository
import com.workoutrec.auth.AuthOutcome
import com.workoutrec.auth.ConsentRequest
import com.workoutrec.auth.PickResult
import com.workoutrec.data.SelectedAccount
import com.workoutrec.fakes.FakeAccountPicker
import com.workoutrec.fakes.FakeApiAuthorizer
import com.workoutrec.fakes.FakeSettingsStore
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

// US1 acceptance scenarios, FR-001, FR-004
@OptIn(ExperimentalCoroutinesApi::class)
class SetupViewModelAccountTest {

    private val account = SelectedAccount("a@example.com", "Alex T", null)
    private val afterAuth = RecordingPostAuthStep()

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(store: FakeSettingsStore, authorizer: FakeApiAuthorizer) =
        SetupViewModel(AccountRepository(authorizer, store), afterAuth)

    @Test
    fun `no stored account shows the account chooser`() = runTest {
        val vm = viewModel(FakeSettingsStore(), FakeApiAuthorizer())
        vm.start()
        assertEquals(SetupState.SignedOut(message = null), vm.state.value)
    }

    @Test
    fun `pick and grant saves the account and continues`() = runTest {
        val store = FakeSettingsStore()
        val vm = viewModel(store, FakeApiAuthorizer(AuthOutcome.Granted("t")))
        vm.start()
        vm.chooseAccount(FakeAccountPicker(PickResult.Picked(account)))
        assertEquals(account, store.account.first())
        assertEquals(listOf(account), afterAuth.accounts)
        assertEquals(SetupState.Ready, vm.state.value)
    }

    @Test
    fun `cancelled pick asks to choose again`() = runTest {
        val vm = viewModel(FakeSettingsStore(), FakeApiAuthorizer())
        vm.start()
        vm.chooseAccount(FakeAccountPicker(PickResult.Cancelled))
        assertEquals(SetupState.SignedOut(SetupMessage.AccountRequired), vm.state.value)
        assertTrue(afterAuth.accounts.isEmpty())
    }

    @Test
    fun `denied access asks to choose again and forgets the account`() = runTest {
        val store = FakeSettingsStore()
        val vm = viewModel(store, FakeApiAuthorizer(AuthOutcome.Denied))
        vm.start()
        vm.chooseAccount(FakeAccountPicker(PickResult.Picked(account)))
        assertEquals(SetupState.SignedOut(SetupMessage.AccountRequired), vm.state.value)
        assertNull(store.account.first())
    }

    @Test
    fun `consent screen then grant continues`() = runTest {
        val consent = ConsentRequest(null)
        val authorizer = FakeApiAuthorizer(AuthOutcome.NeedsUserConsent(consent))
        val vm = viewModel(FakeSettingsStore(), authorizer)
        vm.start()
        vm.chooseAccount(FakeAccountPicker(PickResult.Picked(account)))
        assertEquals(SetupState.NeedsConsent(consent), vm.state.value)
        authorizer.consentResult = AuthOutcome.Granted("t")
        vm.onConsentResult(data = null)
        assertEquals(SetupState.Ready, vm.state.value)
    }

    @Test
    fun `consent screen then denial asks to choose again`() = runTest {
        val authorizer = FakeApiAuthorizer(AuthOutcome.NeedsUserConsent(ConsentRequest(null)))
        val vm = viewModel(FakeSettingsStore(), authorizer)
        vm.start()
        vm.chooseAccount(FakeAccountPicker(PickResult.Picked(account)))
        authorizer.consentResult = AuthOutcome.Denied
        vm.onConsentResult(data = null)
        assertEquals(SetupState.SignedOut(SetupMessage.AccountRequired), vm.state.value)
    }

    @Test
    fun `stored account with silent grant is Ready without asking`() = runTest {
        val picker = FakeAccountPicker(PickResult.Cancelled)
        val vm = viewModel(FakeSettingsStore(account), FakeApiAuthorizer(AuthOutcome.Granted("t")))
        vm.start()
        assertEquals(SetupState.Ready, vm.state.value)
        assertEquals(0, picker.calls)
        assertEquals(listOf(account), afterAuth.accounts)
    }

    @Test
    fun `stored account with revoked access shows the chooser with a message`() = runTest {
        val store = FakeSettingsStore(account)
        val vm = viewModel(store, FakeApiAuthorizer(AuthOutcome.Denied))
        vm.start()
        assertEquals(SetupState.SignedOut(SetupMessage.AccessRevoked), vm.state.value)
        assertNull(store.account.first())
    }

    @Test
    fun `stored account while offline is Ready`() = runTest {
        val vm = viewModel(FakeSettingsStore(account), FakeApiAuthorizer(AuthOutcome.Unavailable))
        vm.start()
        assertEquals(SetupState.Ready, vm.state.value)
    }

    private class RecordingPostAuthStep : PostAuthStep {
        val accounts = mutableListOf<SelectedAccount>()
        override suspend fun run(account: SelectedAccount): SetupState {
            accounts += account
            return SetupState.Ready
        }
    }
}
