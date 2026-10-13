package com.workoutrec

import android.app.Application
import android.content.Context
import androidx.work.Configuration
import androidx.work.WorkManager
import com.workoutrec.diag.DeviceState
import com.workoutrec.monitoring.MonitoringSettings
import com.workoutrec.monitoring.SharedPreferencesStore
import com.workoutrec.sync.BackgroundRun
import com.workoutrec.sync.SyncWork
import com.workoutrec.sync.SyncWorkerFactory

class WorkoutRecApplication : Application(), Configuration.Provider {
    lateinit var container: AppContainer
        private set

    /** Feature 003: the Send diagnostics switch, read before Tracer starts (research R3). */
    lateinit var monitoringSettings: MonitoringSettings
        private set

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        monitoringSettings = MonitoringSettings(
            SharedPreferencesStore(base.getSharedPreferences(MonitoringSettings.FILE, Context.MODE_PRIVATE)),
        )
    }

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this, monitoringSettings)
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
