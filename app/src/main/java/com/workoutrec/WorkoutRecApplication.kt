package com.workoutrec

import android.app.Application
import androidx.work.Configuration
import androidx.work.WorkManager
import com.workoutrec.diag.DeviceState
import com.workoutrec.sync.BackgroundRun
import com.workoutrec.sync.SyncWork
import com.workoutrec.sync.SyncWorkerFactory

class WorkoutRecApplication : Application(), Configuration.Provider {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
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
