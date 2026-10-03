package com.workoutrec

import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.workoutrec.auth.AccountRepository
import com.workoutrec.auth.ApiAuthorizer
import com.workoutrec.auth.GoogleApiAuthorizer
import com.workoutrec.automation.ScriptAssets
import com.workoutrec.automation.ScriptInstaller
import com.workoutrec.data.DataStoreSettingsStore
import com.workoutrec.data.SettingsStore
import com.workoutrec.google.DriveClient
import com.workoutrec.google.GoogleHttp
import com.workoutrec.google.ScriptClient
import com.workoutrec.google.SheetsClient
import com.workoutrec.setup.SpreadsheetSetupFlow
import com.workoutrec.spreadsheet.SpreadsheetSetupService
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** Manual dependency injection (research R1). */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val settingsStore: SettingsStore = DataStoreSettingsStore(appContext.settingsDataStore)

    val apiAuthorizer: ApiAuthorizer = GoogleApiAuthorizer(appContext)

    val accountRepository = AccountRepository(apiAuthorizer, settingsStore, clearCredentials = {
        runCatching { CredentialManager.create(appContext).clearCredentialState(ClearCredentialStateRequest()) }
    })

    val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val googleHttp = GoogleHttp(okHttpClient, accountRepository)
    private val sheetsClient = SheetsClient(googleHttp)

    val spreadsheetSetupFlow = SpreadsheetSetupFlow(
        setup = SpreadsheetSetupService(DriveClient(googleHttp), sheetsClient),
        installer = ScriptInstaller(ScriptClient(googleHttp), sheetsClient, ScriptAssets.from(appContext.assets)),
        store = settingsStore,
        timeZone = { ZoneId.systemDefault().id },
        now = { Instant.now() },
    )
}
