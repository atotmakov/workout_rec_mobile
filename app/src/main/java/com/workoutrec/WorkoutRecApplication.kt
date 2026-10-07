package com.workoutrec

import android.app.Application
import androidx.work.Configuration
import androidx.work.WorkManager
import com.workoutrec.sync.BackgroundRun
import com.workoutrec.sync.SyncWork
import com.workoutrec.sync.SyncWorkerFactory

class WorkoutRecApplication : Application(), Configuration.Provider {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }

    /** Background sync runs through the app's own sync (research R2). */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(
                SyncWorkerFactory(
                    runSync = { container.syncScheduler.runNow() },
                    retryLater = { SyncWork.enqueue(WorkManager.getInstance(this), delayMinutes = 1) },
                    record = { result -> container.syncStatusStore.recordBackground(BackgroundRun(System.currentTimeMillis(), result)) },
                ),
            )
            .build()
}
