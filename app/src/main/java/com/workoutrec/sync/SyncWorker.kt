package com.workoutrec.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters

/** Background sync that also runs after the app was closed (research R2). */
class SyncWorker(
    context: Context,
    params: WorkerParameters,
    private val runSync: suspend () -> SyncOutcome?,
    private val retryLater: () -> Unit,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = TODO()
}

/** Creates [SyncWorker] with the app's sync (no reflection-based default factory). */
class SyncWorkerFactory(
    private val runSync: suspend () -> SyncOutcome?,
    private val retryLater: () -> Unit,
) : WorkerFactory() {
    override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters): ListenableWorker? =
        TODO()
}

/** The unique background sync work. */
object SyncWork {
    const val NAME = "log-sync"

    fun request(delayMinutes: Long = 0): OneTimeWorkRequest = TODO()

    fun enqueue(workManager: WorkManager, delayMinutes: Long = 0): Unit = TODO()
}
