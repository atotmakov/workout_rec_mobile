package com.workoutrec

import android.app.Application
import android.content.Context
import androidx.work.Configuration
import androidx.work.WorkManager
import com.workoutrec.diag.DeviceState
import com.workoutrec.monitoring.MonitoringConfig
import com.workoutrec.monitoring.MonitoringSettings
import com.workoutrec.monitoring.ProfileTarget
import com.workoutrec.monitoring.ScreenProfile
import com.workoutrec.monitoring.SharedPreferencesStore
import com.workoutrec.monitoring.StoredReportsCleaner
import com.workoutrec.monitoring.TracerMonitoring
import com.workoutrec.sync.BackgroundRun
import com.workoutrec.sync.SyncWork
import com.workoutrec.sync.SyncWorkerFactory
import ru.ok.tracer.HasTracerConfiguration
import ru.ok.tracer.TracerConfiguration

class WorkoutRecApplication : Application(), Configuration.Provider, HasTracerConfiguration {
    lateinit var container: AppContainer
        private set

    /** Feature 003: the Send diagnostics switch, read before Tracer starts (research R3). */
    lateinit var monitoringSettings: MonitoringSettings
        private set

    /** App start until the first screen is drawn (FR-011); MainActivity marks it shown. */
    val appStartProfile = ScreenProfile { container.monitoring.beginProfile(ProfileTarget.APP_START) }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        monitoringSettings = MonitoringSettings(
            SharedPreferencesStore(base.getSharedPreferences(MonitoringSettings.FILE, Context.MODE_PRIVATE)),
        )
        // Started with Send diagnostics off: drop what Tracer stored but has not sent (US6).
        if (MonitoringConfig.plan(BuildConfig.TRACER_ENABLED, monitoringSettings.startedWith).deleteStoredReports) {
            StoredReportsCleaner(base.cacheDir).deleteStoredReports()
        }
    }

    /**
     * Read by Tracer once per process, after [attachBaseContext] and before [onCreate]: every
     * module with Send diagnostics on, nothing with it off or without a token (research R3).
     */
    override val tracerConfiguration: List<TracerConfiguration>
        get() = TracerMonitoring.configurations(MonitoringConfig.plan(BuildConfig.TRACER_ENABLED, monitoringSettings.startedWith))

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this, monitoringSettings)
        appStartProfile.entered()
        container.diagnosticLog.write("app process started; ${DeviceState.describe(this)}")
    }

    /** Background sync runs through the app's own sync (research R2). */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(
                SyncWorkerFactory(
                    runSync = { container.syncScheduler.runNow() },
                    retryLater = {
                        container.diagnosticLog.write("background run: retry queued in 1 min")
                        SyncWork.enqueue(WorkManager.getInstance(this), delayMinutes = 1)
                    },
                    record = { result ->
                        val state = if (result == "started") "; ${DeviceState.describe(this)}" else ""
                        container.diagnosticLog.write("background run: $result$state")
                        container.syncStatusStore.recordBackground(BackgroundRun(System.currentTimeMillis(), result))
                    },
                ),
            )
            .build()
}
