package com.workoutrec.sync

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

// quickstart-results.md issue 1: make the background sync visible on the phone
@OptIn(ExperimentalCoroutinesApi::class)
class BackgroundRunTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `each outcome has a short name`() {
        assertEquals("synced", BackgroundRun.describe(SyncOutcome.Synced(wrote = true)))
        assertEquals("nothing to sync", BackgroundRun.describe(SyncOutcome.Synced(wrote = false)))
        assertEquals("failed: NETWORK", BackgroundRun.describe(SyncOutcome.Failed(SyncPhase.Failing(FailReason.NETWORK))))
        assertEquals("failed: sign in", BackgroundRun.describe(SyncOutcome.Failed(SyncPhase.NeedsSignIn)))
        assertEquals("no spreadsheet", BackgroundRun.describe(SyncOutcome.NoSpreadsheet))
        assertEquals("error", BackgroundRun.describe(null))
    }

    @Test
    fun `the last background run is stored with its time`() = runTest(UnconfinedTestDispatcher()) {
        val dataStore = PreferenceDataStoreFactory.create(scope = backgroundScope) { File(tmp.root, "sync.preferences_pb") }
        val store = DataStoreSyncStatusStore(dataStore)
        assertNull(store.status.first().background)
        store.recordBackground(BackgroundRun(at = 123L, result = "started"))
        store.recordBackground(BackgroundRun(at = 456L, result = "synced"))
        assertEquals(BackgroundRun(456L, "synced"), store.status.first().background)
        assertEquals(SyncPhase.Idle, store.status.first().phase)
    }
}
