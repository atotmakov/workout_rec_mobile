package com.workoutrec.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.flow.Flow

interface SettingsStore {
    val account: Flow<SelectedAccount?>
    val binding: Flow<SpreadsheetBinding?>
    suspend fun saveAccount(account: SelectedAccount)
    suspend fun saveBinding(binding: SpreadsheetBinding)
    suspend fun clearBinding()
    suspend fun clear()
}

class DataStoreSettingsStore(private val dataStore: DataStore<Preferences>) : SettingsStore {
    override val account: Flow<SelectedAccount?> get() = TODO()
    override val binding: Flow<SpreadsheetBinding?> get() = TODO()
    override suspend fun saveAccount(account: SelectedAccount): Unit = TODO()
    override suspend fun saveBinding(binding: SpreadsheetBinding): Unit = TODO()
    override suspend fun clearBinding(): Unit = TODO()
    override suspend fun clear(): Unit = TODO()
}
