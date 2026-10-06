package com.workoutrec.log

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.workoutrec.R
import com.workoutrec.sync.FailReason
import com.workoutrec.sync.SyncPhase
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

// contracts/screens.md "Main (Today)" sync status line; FR-010
@RunWith(AndroidJUnit4::class)
class SyncStatusTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun hiddenWhenEverythingIsSynced() {
        compose.setContent { SyncStatusLine(pendingCount = 0, phase = SyncPhase.Idle, onSignIn = {}) }
        compose.onAllNodesWithTag(SyncTags.STATUS).assertCountEquals(0)
    }

    @Test
    fun showsHowManySetsWait() {
        compose.setContent { SyncStatusLine(pendingCount = 3, phase = SyncPhase.Idle, onSignIn = {}) }
        compose.onNodeWithTag(SyncTags.STATUS).assertTextContains(context.resources.getQuantityString(R.plurals.sync_pending, 3, 3))
    }

    @Test
    fun showsWhySyncIsFailing() {
        compose.setContent { SyncStatusLine(pendingCount = 2, phase = SyncPhase.Failing(FailReason.NETWORK), onSignIn = {}) }
        compose.onNodeWithText(context.getString(R.string.sync_failing_network)).assertIsDisplayed()
    }

    @Test
    fun signInAgainOpensConsent() {
        var signIns = 0
        compose.setContent { SyncStatusLine(pendingCount = 1, phase = SyncPhase.NeedsSignIn, onSignIn = { signIns++ }) }
        compose.onNodeWithText(context.getString(R.string.sync_sign_in)).performClick()
        assertEquals(1, signIns)
    }
}
