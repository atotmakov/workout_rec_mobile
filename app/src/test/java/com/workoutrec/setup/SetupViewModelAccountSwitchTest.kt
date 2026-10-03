package com.workoutrec.setup

import com.workoutrec.auth.AccountRepository
import com.workoutrec.auth.AuthOutcome
import com.workoutrec.auth.PickResult
import com.workoutrec.data.SelectedAccount
import com.workoutrec.data.SpreadsheetBinding
import com.workoutrec.fakes.FakeAccountPicker
import com.workoutrec.fakes.FakeApiAuthorizer
import com.workoutrec.fakes.FakeScriptInstaller
import com.workoutrec.fakes.FakeSettingsStore
import com.workoutrec.fakes.FakeSpreadsheetSetupService
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
import org.junit.Before
import org.junit.Test

// US3: switch account, sign out (FR-005, FR-012)
@OptIn(ExperimentalCoroutinesApi::class)
class SetupViewModelAccountSwitchTest {

    private val a = SelectedAccount("a@example.com", "Account A", null)
    private val b = SelectedAccount("b@example.com", "Account B", null)
    private val log = mutableListOf<String>()
    private val setup = FakeSpreadsheetSetupService(log = log)
    private val store = FakeSettingsStore(account = a, binding = SpreadsheetBinding(a.email, "sheet-a", "s1", "https://e"))
    private val authorizer = FakeApiAuthorizer(AuthOutcome.Granted("t"))
    private var credentialCleared = 0
    private lateinit var vm: SetupViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val flow = SpreadsheetSetupFlow(setup, FakeScriptInstaller(log = log), store, { "Europe/Moscow" }, { Instant.parse("2026-10-05T12:00:00Z") })
        val repo = AccountRepository(authorizer, store, clearCredentials = { credentialCleared++ })
        vm = SetupViewModel(repo, flow)
        vm.start()
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `starts Ready with account A`() {
        assertEquals(SetupState.Ready, vm.state.value)
    }

    @Test
    fun `switching to B authorizes B and runs the spreadsheet setup for B`() = runTest {
        setup.found = DriveFile("sheet-b")
        setup.metadata = mapOf(
            "workout_rec.script_id" to "sb",
            "workout_rec.enable_url" to "https://eb",
            "workout_rec.automation_enabled_at" to "2026-10-05T11:00:00Z",
        )
        vm.switchAccount(FakeAccountPicker(PickResult.Picked(b)))
        assertEquals(b, store.account.first())
        assertEquals(listOf(a.email, b.email), authorizer.authorizedEmails)
        assertEquals(listOf("find", "check:sheet-b"), log)
        assertEquals(SpreadsheetBinding(b.email, "sheet-b", "sb", "https://eb"), store.binding.first())
        assertEquals(SetupState.Ready, vm.state.value)
    }

    @Test
    fun `cancelled switch keeps account A and its binding`() = runTest {
        vm.switchAccount(FakeAccountPicker(PickResult.Cancelled))
        assertEquals(a, store.account.first())
        assertEquals("sheet-a", store.binding.first()?.spreadsheetId)
        assertEquals(SetupState.Ready, vm.state.value)
    }

    @Test
    fun `sign out clears credential state and local data, then shows the chooser`() = runTest {
        vm.signOut()
        assertEquals(1, credentialCleared)
        assertNull(store.account.first())
        assertNull(store.binding.first())
        assertEquals(SetupState.SignedOut(message = null), vm.state.value)
    }
}
