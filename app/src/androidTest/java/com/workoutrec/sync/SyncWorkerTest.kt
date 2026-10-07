package com.workoutrec.sync

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.WorkManager
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

// research R2; analysis fixes U1 (no growing backoff) and I1 (a change during a run is not lost)
@RunWith(AndroidJUnit4::class)
class SyncWorkerTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private var retriesLater = 0

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
    }

    private val recorded = mutableListOf<String>()

    private fun worker(outcome: SyncOutcome?) = TestListenableWorkerBuilder<SyncWorker>(context)
        .setWorkerFactory(SyncWorkerFactory(runSync = { outcome }, retryLater = { retriesLater++ }, record = { recorded += it }))
        .build()

    // quickstart-results.md issue 1: background sync never ran on a Pixel 10a.
    @Test
    fun aRunRecordsThatItStartedAndHowItEnded() = runTest {
        worker(SyncOutcome.Failed(SyncPhase.Failing(FailReason.NETWORK))).doWork()
        assertEquals(listOf("started", "failed: NETWORK"), recorded)
    }

    @Test
    fun theAppStartsWorkManagerWithItsOwnWorkerFactory() {
        val provider = context.applicationContext as Configuration.Provider
        assertTrue(provider.workManagerConfiguration.workerFactory is SyncWorkerFactory)
    }

    @Test
    fun theDefaultWorkManagerInitializerIsRemoved() {
        val info = context.packageManager.getProviderInfo(
            ComponentName(context, "androidx.startup.InitializationProvider"),
            PackageManager.GET_META_DATA,
        )
        assertFalse(info.metaData?.containsKey("androidx.work.WorkManagerInitializer") == true)
    }

    @Test
    fun backgroundWorkWaitsForAConnection() {
        val workManager = WorkManager.getInstance(context)
        val now = SyncWork.request()
        val later = SyncWork.request(delayMinutes = 1)
        workManager.enqueue(listOf(now, later)).result.get()
        val nowInfo = workManager.getWorkInfoById(now.id).get()!!
        assertEquals(NetworkType.CONNECTED, nowInfo.constraints.requiredNetworkType)
        assertEquals(0L, nowInfo.initialDelayMillis)
        assertEquals(60_000L, workManager.getWorkInfoById(later.id).get()!!.initialDelayMillis)
    }

    @Test
    fun aSuccessfulRunFinishes() = runTest {
        assertEquals(ListenableWorker.Result.success(), worker(SyncOutcome.Synced(wrote = true)).doWork())
        assertEquals(0, retriesLater)
    }

    @Test
    fun aNetworkFailureFinishesAndSchedulesAFreshRunInsteadOfBackoff() = runTest {
        assertEquals(ListenableWorker.Result.success(), worker(SyncOutcome.Failed(SyncPhase.Failing(FailReason.NETWORK))).doWork())
        assertEquals(1, retriesLater)
    }

    @Test
    fun failuresThatNeedTheUserDoNotRetry() = runTest {
        assertEquals(ListenableWorker.Result.success(), worker(SyncOutcome.Failed(SyncPhase.NeedsSignIn)).doWork())
        assertEquals(ListenableWorker.Result.success(), worker(SyncOutcome.Failed(SyncPhase.Failing(FailReason.STRUCTURE))).doWork())
        assertEquals(0, retriesLater)
    }

    @Test
    fun aRequestDuringAPendingRunIsKept() {
        val workManager = WorkManager.getInstance(context)
        SyncWork.enqueue(workManager)
        SyncWork.enqueue(workManager)
        val infos = workManager.getWorkInfosForUniqueWork(SyncWork.NAME).get()
        assertTrue(infos.isNotEmpty())
        assertTrue(infos.none { it.state.isFinished })
    }
}
