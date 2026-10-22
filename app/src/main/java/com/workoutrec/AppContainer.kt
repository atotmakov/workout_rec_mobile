package com.workoutrec

import com.workoutrec.google.MoneyClient
import com.workoutrec.money.MoneyRefresher
import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.work.WorkManager
import com.workoutrec.auth.AccountRepository
import com.workoutrec.auth.ApiAuthorizer
import com.workoutrec.auth.GoogleApiAuthorizer
import com.workoutrec.auth.SignOutCleanup
import com.workoutrec.automation.ScriptAssets
import com.workoutrec.automation.ScriptInstaller
import com.workoutrec.data.DataStoreSettingsStore
import com.workoutrec.data.SettingsStore
import com.workoutrec.diag.DiagnosticLog
import com.workoutrec.google.DriveClient
import com.workoutrec.google.GoogleHttp
import com.workoutrec.google.ScriptClient
import com.workoutrec.google.SheetsClient
import com.workoutrec.google.SheetsLogClient
import com.workoutrec.monitoring.DiagnosticsTestActions
import com.workoutrec.monitoring.Monitoring
import com.workoutrec.monitoring.MonitoringControl
import com.workoutrec.monitoring.MonitoringSettings
import com.workoutrec.monitoring.NoMonitoring
import com.workoutrec.monitoring.ProfileSampler
import com.workoutrec.monitoring.SharedPreferencesStore
import com.workoutrec.monitoring.StoredReportsCleaner
import com.workoutrec.monitoring.TracerMonitoring
import com.workoutrec.setup.SpreadsheetSetupFlow
import com.workoutrec.spreadsheet.SpreadsheetSetupService
import com.workoutrec.sync.DataStoreSyncStatusStore
import com.workoutrec.sync.LogSync
import com.workoutrec.sync.SyncScheduler
import com.workoutrec.sync.SyncStatusStore
import com.workoutrec.sync.SyncWork
import com.workoutrec.workout.data.WorkoutDatabase
import com.workoutrec.workout.data.WorkoutRepository
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import okhttp3.OkHttpClient

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")
private val Context.syncDataStore: DataStore<Preferences> by preferencesDataStore(name = "sync")

/** Manual dependency injection (research R1). */
class AppContainer(context: Context, val monitoringSettings: MonitoringSettings) {
    private val appContext = context.applicationContext

    /**
     * Feature 003: Tracer when this build has its token and the process started with Send
     * diagnostics on; the switch is read again on every call (research R3).
     */
    /** Research R9: which runs are profiled; "Profile next app start" is kept across restarts. */
    val profileSampler = ProfileSampler(
        SharedPreferencesStore(appContext.getSharedPreferences(MonitoringSettings.FILE, Context.MODE_PRIVATE)),
        roll = { n -> Random.nextInt(n) },
    )

    val monitoring: Monitoring =
        if (BuildConfig.TRACER_ENABLED && monitoringSettings.startedWith) {
            TracerMonitoring(isEnabled = { monitoringSettings.sendDiagnostics }, sampler = profileSampler)
        } else {
            NoMonitoring
        }

    /** The Send diagnostics switch: off stops Tracer at once and drops unsent reports (US6). */
    val monitoringControl = MonitoringControl(
        monitoringSettings,
        disableTracer = { if (monitoring is TracerMonitoring) TracerMonitoring.disableNow() },
        deleteStoredReports = StoredReportsCleaner(appContext.cacheDir)::deleteStoredReports,
    )

    /** Hidden test actions behind a long-press on Send diagnostics (research R10). */
    val diagnosticsTestActions = DiagnosticsTestActions(
        runOnMain = { Handler(Looper.getMainLooper()).post(it) },
        sleep = Thread::sleep,
        monitoring = monitoring,
        takeHeapDump = { tag -> if (monitoring.enabled) TracerMonitoring.heapDumpNow(tag) },
        runDiskCheck = { if (monitoring.enabled) TracerMonitoring.diskCheckNow() },
        forceProfileNextStart = profileSampler::forceNextAppStart,
    )

    val settingsStore: SettingsStore = DataStoreSettingsStore(appContext.settingsDataStore)

    val apiAuthorizer: ApiAuthorizer = GoogleApiAuthorizer(appContext)

    /** Background sync log, shared from the account menu (quickstart-results.md issue 1). */
    val diagnosticLog = DiagnosticLog(File(appContext.filesDir, "logs/sync.log"), monitoring = monitoring)

    val accountRepository = AccountRepository(
        apiAuthorizer,
        settingsStore,
        clearCredentials = SignOutCleanup(
            clear = { CredentialManager.create(appContext).clearCredentialState(ClearCredentialStateRequest()) },
            monitoring = monitoring,
        )::invoke,
        log = diagnosticLog::write,
        monitoring = monitoring,
    )

    val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val googleHttp = GoogleHttp(okHttpClient, accountRepository)
    private val sheetsClient = SheetsClient(googleHttp)

    val spreadsheetSetupFlow = SpreadsheetSetupFlow(
        setup = SpreadsheetSetupService(DriveClient(googleHttp), sheetsClient, monitoring = monitoring),
        installer = ScriptInstaller(ScriptClient(googleHttp), sheetsClient, ScriptAssets.from(appContext.assets)),
        store = settingsStore,
        timeZone = { ZoneId.systemDefault().id },
        now = { Instant.now() },
        log = diagnosticLog::write,
        monitoring = monitoring,
    )

    // Feature 002: workout logging (plan.md).
    val workoutRepository = WorkoutRepository(WorkoutDatabase.create(appContext).dao(), monitoring = monitoring)
    val syncStatusStore: SyncStatusStore = DataStoreSyncStatusStore(appContext.syncDataStore, monitoring)
    val sheetsLogClient = SheetsLogClient(googleHttp)

    /** Feature 004 US4: the money and balance tabs (call 23), apart from the set sync. */
    val moneyRefresher: MoneyRefresher by lazy {
        MoneyRefresher(
            MoneyClient(googleHttp),
            workoutRepository,
            sheet = { syncStatusStore.current().sheet?.takeIf { it.spreadsheetId == settingsStore.binding.first()?.spreadsheetId } },
            monitoring = monitoring,
        )
    }

    /** Lives as long as the process; sync runs outlive screens (research R2). */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val syncScheduler = SyncScheduler(
        LogSync(
            store = workoutRepository,
            sheets = sheetsLogClient,
            status = syncStatusStore,
            spreadsheetId = { settingsStore.binding.first()?.spreadsheetId },
            log = diagnosticLog::write,
            monitoring = monitoring,
            // Feature 004 research R6: the Money tab shows the new payments' rows.
            onPaymentsWritten = { moneyRefresher.refresh() },
        ),
        appScope,
        enqueueBackground = {
            diagnosticLog.write("background run queued")
            SyncWork.enqueue(WorkManager.getInstance(appContext))
        },
        monitoring = monitoring,
    )
}
