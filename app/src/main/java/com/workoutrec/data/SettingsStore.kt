package com.workoutrec.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** The phone's only local state: the selected account and a spreadsheet cache (data-model.md). */
interface SettingsStore {
    val account: Flow<SelectedAccount?>

    /** Null when absent or when it belongs to a different account than [account]. */
    val binding: Flow<SpreadsheetBinding?>

    /** Saving a different account discards the old binding. */
    suspend fun saveAccount(account: SelectedAccount)
    suspend fun saveBinding(binding: SpreadsheetBinding)
    suspend fun clearBinding()
    suspend fun clear()
}

class DataStoreSettingsStore(private val dataStore: DataStore<Preferences>) : SettingsStore {

    override val account: Flow<SelectedAccount?> = dataStore.data.map { it.toAccount() }

    override val binding: Flow<SpreadsheetBinding?> = dataStore.data.map { prefs ->
        val account = prefs.toAccount() ?: return@map null
        val bindingEmail = prefs[BINDING_EMAIL] ?: return@map null
        val spreadsheetId = prefs[SPREADSHEET_ID] ?: return@map null
        if (bindingEmail != account.email) return@map null
        SpreadsheetBinding(
            accountEmail = bindingEmail,
            spreadsheetId = spreadsheetId,
            scriptId = prefs[SCRIPT_ID],
            enableUrl = prefs[ENABLE_URL],
        )
    }

    override suspend fun saveAccount(account: SelectedAccount) {
        dataStore.edit { prefs ->
            if (prefs[EMAIL] != account.email) prefs.removeBinding()
            prefs[EMAIL] = account.email
            prefs.setOrRemove(DISPLAY_NAME, account.displayName)
            prefs.setOrRemove(PHOTO_URL, account.photoUrl)
        }
    }

    override suspend fun saveBinding(binding: SpreadsheetBinding) {
        dataStore.edit { prefs ->
            prefs[BINDING_EMAIL] = binding.accountEmail
            prefs[SPREADSHEET_ID] = binding.spreadsheetId
            prefs.setOrRemove(SCRIPT_ID, binding.scriptId)
            prefs.setOrRemove(ENABLE_URL, binding.enableUrl)
        }
    }

    override suspend fun clearBinding() {
        dataStore.edit { it.removeBinding() }
    }

    override suspend fun clear() {
        dataStore.edit { it.clear() }
    }

    private fun Preferences.toAccount(): SelectedAccount? {
        val email = this[EMAIL]?.takeIf { it.isNotBlank() } ?: return null
        return SelectedAccount(email = email, displayName = this[DISPLAY_NAME], photoUrl = this[PHOTO_URL])
    }

    private fun androidx.datastore.preferences.core.MutablePreferences.removeBinding() {
        remove(BINDING_EMAIL)
        remove(SPREADSHEET_ID)
        remove(SCRIPT_ID)
        remove(ENABLE_URL)
    }

    private fun androidx.datastore.preferences.core.MutablePreferences.setOrRemove(
        key: Preferences.Key<String>,
        value: String?,
    ) {
        if (value == null) remove(key) else this[key] = value
    }

    private companion object {
        val EMAIL = stringPreferencesKey("account_email")
        val DISPLAY_NAME = stringPreferencesKey("account_display_name")
        val PHOTO_URL = stringPreferencesKey("account_photo_url")
        val BINDING_EMAIL = stringPreferencesKey("binding_account_email")
        val SPREADSHEET_ID = stringPreferencesKey("binding_spreadsheet_id")
        val SCRIPT_ID = stringPreferencesKey("binding_script_id")
        val ENABLE_URL = stringPreferencesKey("binding_enable_url")
    }
}
