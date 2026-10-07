package com.workoutrec.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/** Background sync that also runs after the app was closed (research R2). */
class SyncWorker(
    context: Context,
    params: WorkerParameters,
    private val runSync: suspend () -> SyncOutcome?,
    private val retryLater: () -> Unit,
) : CoroutineWorker(context, params) {

    /**
     * Always finishes as success: a network failure schedules a fresh run a minute later instead of
     * WorkManager's growing backoff, so sets arrive soon after the connection returns (SC-003,
     * analysis fix U1). Failures that need the user wait for the app to be opened.
     */
    override suspend fun doWork(): Result {
        when (val outcome = runSync()) {
            null -> retryLater()
            is SyncOutcome.Failed -> if (outcome.phase == SyncPhase.Failing(FailReason.NETWORK)) retryLater()
            else -> Unit
        }
        return Result.success()
    }
}

/** Creates [SyncWorker] with the app's sync (no reflection-based default factory). */
class SyncWorkerFactory(
    private val runSync: suspend () -> SyncOutcome?,
    private val retryLater: () -> Unit,
) : WorkerFactory() {
    override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters): ListenableWorker? =
        if (workerClassName == SyncWorker::class.java.name) SyncWorker(appContext, workerParameters, runSync, retryLater) else null
}

/** The unique background sync work. */
object SyncWork {
    const val NAME = "log-sync"

    fun request(delayMinutes: Long = 0): OneTimeWorkRequest =
        OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInitialDelay(delayMinutes, TimeUnit.MINUTES)
            .build()

    /**
     * Appends after a pending or running sync, so a set saved during a run is never left behind
     * (analysis fix I1); runs never overlap because they share the sync mutex.
     */
    fun enqueue(workManager: WorkManager, delayMinutes: Long = 0) {
        workManager.enqueueUniqueWork(NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request(delayMinutes))
    }
}
