package com.workoutrec

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.workoutrec.auth.AccountRepository
import com.workoutrec.auth.ApiAuthorizer
import com.workoutrec.auth.GoogleApiAuthorizer
import com.workoutrec.data.DataStoreSettingsStore
import com.workoutrec.data.SettingsStore
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** Manual dependency injection (research R1). */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val settingsStore: SettingsStore = DataStoreSettingsStore(appContext.settingsDataStore)

    val apiAuthorizer: ApiAuthorizer = GoogleApiAuthorizer(appContext)

    val accountRepository = AccountRepository(apiAuthorizer, settingsStore)

    val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
}
