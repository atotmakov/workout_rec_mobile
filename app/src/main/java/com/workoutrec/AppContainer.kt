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
import com.workoutrec.google.SheetsLogClient
import com.workoutrec.setup.SpreadsheetSetupFlow
import com.workoutrec.spreadsheet.SpreadsheetSetupService
import com.workoutrec.sync.DataStoreSyncStatusStore
import com.workoutrec.sync.LogSync
import com.workoutrec.sync.SyncScheduler
import com.workoutrec.sync.SyncStatusStore
import com.workoutrec.workout.data.WorkoutDatabase
import com.workoutrec.workout.data.WorkoutRepository
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import okhttp3.OkHttpClient

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")
private val Context.syncDataStore: DataStore<Preferences> by preferencesDataStore(name = "sync")

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

    // Feature 002: workout logging (plan.md).
    val workoutRepository = WorkoutRepository(WorkoutDatabase.create(appContext).dao())
    val syncStatusStore: SyncStatusStore = DataStoreSyncStatusStore(appContext.syncDataStore)
    val sheetsLogClient = SheetsLogClient(googleHttp)

    /** Lives as long as the process; sync runs outlive screens (research R2). */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val syncScheduler = SyncScheduler(
        LogSync(
            store = workoutRepository,
            sheets = sheetsLogClient,
            status = syncStatusStore,
            spreadsheetId = { settingsStore.binding.first()?.spreadsheetId },
        ),
        appScope,
    )
}
