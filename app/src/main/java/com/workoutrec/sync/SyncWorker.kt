package com.workoutrec.sync

import android.content.Context
import android.os.Build
import android.net.NetworkCapabilities
import android.net.NetworkRequest
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/** Background sync that also runs after the app was closed (research R2). */
class SyncWorker(
    context: Context,
    params: WorkerParameters,
    private val runSync: suspend () -> SyncOutcome?,
    private val retryLater: () -> Unit,
    private val record: suspend (String) -> Unit = {},
) : CoroutineWorker(context, params) {

    /**
     * Always finishes as success: a network failure schedules a fresh run a minute later instead of
     * WorkManager's growing backoff, so sets arrive soon after the connection returns (SC-003,
     * analysis fix U1). Failures that need the user wait for the app to be opened.
     */
    override suspend fun doWork(): Result {
        record("started")
        val outcome = try {
            runSync()
        } catch (e: CancellationException) {
            // Replaced by a newer request, or the network or time limit ended (issue 1 diagnostics).
            withContext(NonCancellable) { record("stopped by Android (reason ${if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) stopReason else "n/a"})") }
            throw e
        }
        record(BackgroundRun.describe(outcome))
        when (outcome) {
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
    private val record: suspend (String) -> Unit = {},
) : WorkerFactory() {
    override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters): ListenableWorker? =
        if (workerClassName == SyncWorker::class.java.name) SyncWorker(appContext, workerParameters, runSync, retryLater, record) else null
}

/** The unique background sync work. */
object SyncWork {
    const val NAME = "log-sync"

    /**
     * Internet that Android has verified works. Plain CONNECTED was met in airplane mode by a network
     * without internet, so runs started offline and hung (quickstart-results.md issue 1, 3d). A VPN is
     * allowed: with an always-on VPN the app's network is the VPN, and the builder's default "not a
     * VPN" requirement kept every run waiting (3e).
     */
    private val VALIDATED_INTERNET: NetworkRequest = NetworkRequest.Builder()
        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        .addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
        .build()

    fun request(delayMinutes: Long = 0): OneTimeWorkRequest =
        OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkRequest(VALIDATED_INTERNET, NetworkType.CONNECTED).build())
            .setInitialDelay(delayMinutes, TimeUnit.MINUTES)
            .build()

    /**
     * Replaces a pending or running sync. Appending (analysis fix I1) left runs BLOCKED forever behind
     * a run that never finished (quickstart-results.md issue 1). Replacing is safe: a cancelled run
     * leaves the pending changes in place, and the next run never writes a set twice (research R5).
     */
    fun enqueue(workManager: WorkManager, delayMinutes: Long = 0) {
        workManager.enqueueUniqueWork(NAME, ExistingWorkPolicy.REPLACE, request(delayMinutes))
    }
}
