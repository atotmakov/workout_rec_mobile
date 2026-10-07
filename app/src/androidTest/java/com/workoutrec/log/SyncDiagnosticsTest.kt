package com.workoutrec.log

import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.workoutrec.sync.BackgroundRun
import java.time.ZoneOffset
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

// quickstart-results.md issue 1: show the background sync's state while sets are waiting
@RunWith(AndroidJUnit4::class)
class SyncDiagnosticsTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun showsQueuedWorkAndTheLastRun() {
        compose.setContent {
            SyncDiagnosticsLine(workStates = listOf("ENQUEUED", "BLOCKED"), lastRun = BackgroundRun(1_768_240_800_000, "failed: NETWORK"), zone = ZoneOffset.UTC)
        }
        compose.onNodeWithTag(SyncTags.DETAILS).assertTextContains("ENQUEUED, BLOCKED", substring = true)
        compose.onNodeWithTag(SyncTags.DETAILS).assertTextContains("18:00", substring = true)
        compose.onNodeWithTag(SyncTags.DETAILS).assertTextContains("failed: NETWORK", substring = true)
    }

    @Test
    fun saysWhenNothingIsQueuedAndNoRunHappened() {
        compose.setContent { SyncDiagnosticsLine(workStates = emptyList(), lastRun = null, zone = ZoneOffset.UTC) }
        compose.onNodeWithTag(SyncTags.DETAILS).assertTextContains("—", substring = true)
    }
}
