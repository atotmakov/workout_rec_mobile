package com.workoutrec.auth

import com.workoutrec.data.SelectedAccount
import com.workoutrec.fakes.FakeAccountPicker
import com.workoutrec.fakes.FakeApiAuthorizer
import com.workoutrec.fakes.FakeMonitoring
import com.workoutrec.fakes.FakeSettingsStore
import com.workoutrec.google.ApiError
import com.workoutrec.monitoring.NonFatalIssue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class AccountRepositoryTest {

    private val account = SelectedAccount("a@example.com", "Alex T", null)

    @Test
    fun `chooseAccount saves the picked account`() = runTest {
        val store = FakeSettingsStore()
        val repo = AccountRepository(FakeApiAuthorizer(), store)
        val result = repo.chooseAccount(FakeAccountPicker(PickResult.Picked(account)))
        assertEquals(PickResult.Picked(account), result)
        assertEquals(account, store.account.first())
    }

    @Test
    fun `cancel saves nothing`() = runTest {
        val store = FakeSettingsStore()
        val repo = AccountRepository(FakeApiAuthorizer(), store)
        assertEquals(PickResult.Cancelled, repo.chooseAccount(FakeAccountPicker(PickResult.Cancelled)))
        assertNull(store.account.first())
    }

    @Test
    fun `authorizeOnLaunch is silent when already granted`() = runTest {
        val store = FakeSettingsStore(account)
        val authorizer = FakeApiAuthorizer(AuthOutcome.Granted("t"))
        val repo = AccountRepository(authorizer, store)
        assertEquals(AuthOutcome.Granted("t"), repo.authorizeOnLaunch())
        assertEquals(listOf("a@example.com"), authorizer.authorizedEmails)
        assertEquals(account, store.account.first())
    }

    @Test
    fun `authorizeOnLaunch without a stored account returns null and asks nothing`() = runTest {
        val authorizer = FakeApiAuthorizer()
        val repo = AccountRepository(authorizer, FakeSettingsStore())
        assertNull(repo.authorizeOnLaunch())
        assertEquals(emptyList<String>(), authorizer.authorizedEmails)
    }

    @Test
    fun `denied on launch clears the stored account (access revoked)`() = runTest {
        val store = FakeSettingsStore(account)
        val repo = AccountRepository(FakeApiAuthorizer(AuthOutcome.Denied), store)
        assertEquals(AuthOutcome.Denied, repo.authorizeOnLaunch())
        assertNull(store.account.first())
    }

    @Test
    fun `unavailable on launch keeps the stored account`() = runTest {
        val store = FakeSettingsStore(account)
        val repo = AccountRepository(FakeApiAuthorizer(AuthOutcome.Unavailable), store)
        assertEquals(AuthOutcome.Unavailable, repo.authorizeOnLaunch())
        assertEquals(account, store.account.first())
    }

    @Test
    fun `signOut clears credential state and all local data`() = runTest {
        var cleared = 0
        val store = FakeSettingsStore(account, com.workoutrec.data.SpreadsheetBinding(account.email, "s", null, null))
        val repo = AccountRepository(FakeApiAuthorizer(), store, clearCredentials = { cleared++ })
        repo.signOut()
        assertEquals(1, cleared)
        assertEquals(1, store.clearCalls)
        assertNull(store.account.first())
        assertNull(store.binding.first())
    }

    @Test
    fun `switchAccount saves the new account and drops the old binding`() = runTest {
        val store = FakeSettingsStore(account, com.workoutrec.data.SpreadsheetBinding(account.email, "s", null, null))
        val repo = AccountRepository(FakeApiAuthorizer(), store)
        val other = SelectedAccount("b@example.com", null, null)
        assertEquals(PickResult.Picked(other), repo.switchAccount(FakeAccountPicker(PickResult.Picked(other))))
        assertEquals(other, store.account.first())
        assertNull(store.binding.first())
    }

    @Test
    fun `switching to the same account still drops the binding so the spreadsheet is checked again`() = runTest {
        val store = FakeSettingsStore(account, com.workoutrec.data.SpreadsheetBinding(account.email, "s", null, null))
        val repo = AccountRepository(FakeApiAuthorizer(), store)
        repo.switchAccount(FakeAccountPicker(PickResult.Picked(account)))
        assertNull(store.binding.first())
    }

    @Test
    fun `access token is cached and refreshed on demand`() = runTest {
        val store = FakeSettingsStore(account)
        val authorizer = FakeApiAuthorizer(AuthOutcome.Granted("t1"), AuthOutcome.Granted("t2"))
        val repo = AccountRepository(authorizer, store)
        assertEquals("t1", repo.accessToken(forceRefresh = false))
        assertEquals("t1", repo.accessToken(forceRefresh = false))
        assertEquals("t2", repo.accessToken(forceRefresh = true))
        assertEquals(2, authorizer.authorizedEmails.size)
    }

    // quickstart-results.md issue 1: a token request that never answers must not block sync forever.
    @Test
    fun `a token request that never answers counts as no connection`() = runTest {
        val authorizer = FakeApiAuthorizer().apply { hang = true }
        val repo = AccountRepository(authorizer, FakeSettingsStore(account))
        try {
            repo.accessToken(forceRefresh = false)
            fail("expected Offline")
        } catch (e: ApiError) {
            assertEquals(ApiError.Offline, e)
        }
        assertTrue(testScheduler.currentTime <= AccountRepository.AUTHORIZE_TIMEOUT_MILLIS)
    }

    // quickstart-results.md issue 1: the shared log shows whether the token request is the step that hangs.
    @Test
    fun `token requests are written to the diagnostic log`() = runTest {
        val logged = mutableListOf<String>()
        val authorizer = FakeApiAuthorizer(AuthOutcome.Granted("t1"))
        val repo = AccountRepository(authorizer, FakeSettingsStore(account), log = { logged += it })
        repo.accessToken(forceRefresh = false)
        repo.accessToken(forceRefresh = false)
        assertEquals(listOf("token: requesting", "token: granted", "token: cached"), logged)
    }

    @Test
    fun `a token request without an answer is written to the diagnostic log`() = runTest {
        val logged = mutableListOf<String>()
        val repo = AccountRepository(FakeApiAuthorizer().apply { hang = true }, FakeSettingsStore(account), log = { logged += it })
        runCatching { repo.accessToken(forceRefresh = false) }
        assertEquals(listOf("token: requesting", "token: no answer in 20 s"), logged)
    }

    // Feature 003 FR-005: a failed token request reaches monitoring.
    // Feature 005 FR-011: no answer or no network is a no-connection failure
    @Test
    fun `a token request without an answer reports no-connection`() = runTest {
        val monitoring = FakeMonitoring()
        val repo = AccountRepository(FakeApiAuthorizer().apply { hang = true }, FakeSettingsStore(account), monitoring = monitoring)
        assertTrue(runCatching { repo.accessToken(forceRefresh = false) }.exceptionOrNull() is ApiError.Offline)
        val event = monitoring.nonFatals.single()
        assertEquals(NonFatalIssue.NO_CONNECTION, event.issue)
        assertEquals("operation=token", event.details)
    }

    @Test
    fun `an unavailable token request reports no-connection`() = runTest {
        val monitoring = FakeMonitoring()
        val repo = AccountRepository(FakeApiAuthorizer(AuthOutcome.Unavailable), FakeSettingsStore(account), monitoring = monitoring)
        runCatching { repo.accessToken(forceRefresh = false) }
        assertEquals(listOf(NonFatalIssue.NO_CONNECTION to "operation=token"), monitoring.nonFatals.map { it.issue to it.details })
    }

    @Test
    fun `a Google error during the token request reports token-failed with the status`() = runTest {
        val monitoring = FakeMonitoring()
        val repo = AccountRepository(FakeApiAuthorizer(AuthOutcome.Failed(10)), FakeSettingsStore(account), monitoring = monitoring)
        runCatching { repo.accessToken(forceRefresh = false) }
        assertEquals(listOf(NonFatalIssue.TOKEN_FAILED to "Failed (status 10)"), monitoring.nonFatals.map { it.issue to it.details })
    }

    @Test
    fun `a refused token request reports token-failed`() = runTest {
        val monitoring = FakeMonitoring()
        val repo = AccountRepository(FakeApiAuthorizer(AuthOutcome.Denied), FakeSettingsStore(account), monitoring = monitoring)
        runCatching { repo.accessToken(forceRefresh = false) }
        assertEquals(listOf(NonFatalIssue.TOKEN_FAILED), monitoring.nonFatals.map { it.issue })
        assertEquals("Denied", monitoring.nonFatals.single().details)
    }

    @Test
    fun `granted and cached tokens report nothing`() = runTest {
        val monitoring = FakeMonitoring()
        val repo = AccountRepository(FakeApiAuthorizer(AuthOutcome.Granted("t1")), FakeSettingsStore(account), monitoring = monitoring)
        repo.accessToken(forceRefresh = false)
        repo.accessToken(forceRefresh = false)
        assertTrue(monitoring.nonFatals.isEmpty())
    }
}
