package com.workoutrec.sync

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Runs [LogSync] one run at a time (research R2). `requestSync` is called after every local change
 * and on app start; the background WorkManager part comes with US2.
 */
class SyncScheduler(
    private val sync: LogSync,
    private val scope: CoroutineScope,
) {
    private val mutex = Mutex()

    fun requestSync() {
        scope.launch { runNow() }
    }

    /** One run under the process-wide lock; failures leave pending changes in place. */
    suspend fun runNow(): SyncOutcome? = mutex.withLock {
        try {
            sync.run()
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            null
        }
    }
}
