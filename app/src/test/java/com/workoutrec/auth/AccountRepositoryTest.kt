package com.workoutrec.auth

import com.workoutrec.data.SelectedAccount
import com.workoutrec.fakes.FakeAccountPicker
import com.workoutrec.fakes.FakeApiAuthorizer
import com.workoutrec.fakes.FakeSettingsStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    fun `access token is cached and refreshed on demand`() = runTest {
        val store = FakeSettingsStore(account)
        val authorizer = FakeApiAuthorizer(AuthOutcome.Granted("t1"), AuthOutcome.Granted("t2"))
        val repo = AccountRepository(authorizer, store)
        assertEquals("t1", repo.accessToken(forceRefresh = false))
        assertEquals("t1", repo.accessToken(forceRefresh = false))
        assertEquals("t2", repo.accessToken(forceRefresh = true))
        assertEquals(2, authorizer.authorizedEmails.size)
    }
}
