package com.workoutrec.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

// data-model.md "Stored on the phone"
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val account = SelectedAccount(email = "a@example.com", displayName = "Alex T", photoUrl = "https://p/a.png")
    private val binding = SpreadsheetBinding(accountEmail = "a@example.com", spreadsheetId = "sheet1", scriptId = "script1", enableUrl = "https://s/exec")

    private fun TestScope.newStore(): SettingsStore {
        val dataStore = PreferenceDataStoreFactory.create(scope = backgroundScope) {
            File(tmp.root, "settings_${System.nanoTime()}.preferences_pb")
        }
        return DataStoreSettingsStore(dataStore)
    }

    @Test
    fun `saves and loads the selected account`() = runTest(UnconfinedTestDispatcher()) {
        val store = newStore()
        assertNull(store.account.first())
        store.saveAccount(account)
        assertEquals(account, store.account.first())
    }

    @Test
    fun `displayName and photoUrl may be missing`() = runTest(UnconfinedTestDispatcher()) {
        val store = newStore()
        val bare = SelectedAccount(email = "b@example.com", displayName = null, photoUrl = null)
        store.saveAccount(bare)
        assertEquals(bare, store.account.first())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `email is required and non-empty`() {
        SelectedAccount(email = " ", displayName = null, photoUrl = null)
    }

    @Test
    fun `saves and loads the spreadsheet binding`() = runTest(UnconfinedTestDispatcher()) {
        val store = newStore()
        store.saveAccount(account)
        store.saveBinding(binding)
        assertEquals(binding, store.binding.first())
    }

    @Test
    fun `optional binding fields may be missing`() = runTest(UnconfinedTestDispatcher()) {
        val store = newStore()
        store.saveAccount(account)
        val partial = binding.copy(scriptId = null, enableUrl = null)
        store.saveBinding(partial)
        assertEquals(partial, store.binding.first())
    }

    @Test
    fun `binding for another account is discarded`() = runTest(UnconfinedTestDispatcher()) {
        val store = newStore()
        store.saveAccount(account)
        store.saveBinding(binding)
        store.saveAccount(SelectedAccount(email = "other@example.com", displayName = null, photoUrl = null))
        assertNull(store.binding.first())
    }

    @Test
    fun `clearBinding keeps the account`() = runTest(UnconfinedTestDispatcher()) {
        val store = newStore()
        store.saveAccount(account)
        store.saveBinding(binding)
        store.clearBinding()
        assertNull(store.binding.first())
        assertEquals(account, store.account.first())
    }

    @Test
    fun `clear removes everything`() = runTest(UnconfinedTestDispatcher()) {
        val store = newStore()
        store.saveAccount(account)
        store.saveBinding(binding)
        store.clear()
        assertNull(store.account.first())
        assertNull(store.binding.first())
    }
}
