package com.workoutrec.log

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.workoutrec.R
import com.workoutrec.sync.FailReason
import com.workoutrec.sync.SyncPhase
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

// Redesign brief 1a: the top-bar sync icon and its details; FR-010
@RunWith(AndroidJUnit4::class)
class SyncStatusButtonTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun show(pendingCount: Int, phase: SyncPhase, onSyncNow: () -> Unit = {}, onSignIn: () -> Unit = {}) {
        compose.setContent {
            SyncStatusButton(pendingCount, phase, lastSuccessAt = null, zone = ZoneOffset.UTC, onSyncNow = onSyncNow, onSignIn = onSignIn)
        }
    }

    @Test
    fun saysAllSyncedWhenNothingWaits() {
        show(pendingCount = 0, phase = SyncPhase.Idle)
        compose.onNodeWithContentDescription(context.getString(R.string.sync_icon_synced)).assertIsDisplayed()
    }

    @Test
    fun saysHowManySetsWait() {
        show(pendingCount = 3, phase = SyncPhase.Idle)
        compose.onNodeWithContentDescription(context.resources.getQuantityString(R.plurals.sync_pending, 3, 3)).assertIsDisplayed()
        // Feature 004 FR-012: the count includes exercise changes and payments.
        if (context.resources.configuration.locales[0].language == "en") {
            compose.onNodeWithContentDescription("3 changes not in the spreadsheet yet").assertIsDisplayed()
        }
    }

    @Test
    fun detailsShowWhySyncIsFailing() {
        show(pendingCount = 2, phase = SyncPhase.Failing(FailReason.NETWORK))
        compose.onNodeWithTag(SyncIconTags.BUTTON).performClick()
        compose.onNodeWithText(context.getString(R.string.sync_failing_network)).assertIsDisplayed()
    }

    @Test
    fun syncNowRunsASync() {
        var syncs = 0
        show(pendingCount = 1, phase = SyncPhase.Idle, onSyncNow = { syncs++ })
        compose.onNodeWithTag(SyncIconTags.BUTTON).performClick()
        compose.onNodeWithText(context.getString(R.string.sync_now)).performClick()
        assertEquals(1, syncs)
    }

    @Test
    fun signInOpensConsent() {
        var signIns = 0
        show(pendingCount = 1, phase = SyncPhase.NeedsSignIn, onSignIn = { signIns++ })
        compose.onNodeWithTag(SyncIconTags.BUTTON).performClick()
        compose.onNodeWithText(context.getString(R.string.sync_icon_sign_in)).performClick()
        assertEquals(1, signIns)
    }
}
