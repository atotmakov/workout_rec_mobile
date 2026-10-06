package com.workoutrec.home

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.workoutrec.R
import com.workoutrec.data.SelectedAccount
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

// FR-014: sign out or switch with unsynced sets warns how many will be lost (analysis fix U3)
@RunWith(AndroidJUnit4::class)
class UnsyncedWarningTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun s(id: Int) = context.getString(id)
    private val account = SelectedAccount("alex@example.com", "Alexey Totmakov", null)
    private var switched = 0
    private var signedOut = 0

    private fun open(unsynced: Int, item: Int) {
        compose.setContent {
            MainScreen(account = account, onSwitchAccount = { switched++ }, onSignOut = { signedOut++ }, unsyncedCount = unsynced)
        }
        compose.onNodeWithTag(AccountAvatarTag).performClick()
        compose.onNodeWithText(s(item)).performClick()
    }

    @Test
    fun signOutWarnsAboutUnsyncedSets() {
        open(unsynced = 3, item = R.string.account_sign_out)
        compose.onNodeWithTag(UnsyncedWarningTag)
            .assertTextContains(context.resources.getQuantityString(R.plurals.unsynced_warning, 3, 3))
    }

    @Test
    fun signOutWithoutUnsyncedSetsHasNoWarning() {
        open(unsynced = 0, item = R.string.account_sign_out)
        compose.onNodeWithText(s(R.string.sign_out_question)).assertIsDisplayed()
        compose.onAllNodesWithTag(UnsyncedWarningTag).assertCountEquals(0)
    }

    @Test
    fun switchingWithUnsyncedSetsAsksFirst() {
        open(unsynced = 2, item = R.string.account_switch)
        compose.onNodeWithTag(UnsyncedWarningTag).assertIsDisplayed()
        assertEquals(0, switched)
        compose.onNodeWithText(s(R.string.switch_confirm)).performClick()
        compose.waitForIdle()
        assertEquals(1, switched)
    }

    @Test
    fun switchingWithoutUnsyncedSetsDoesNotAsk() {
        open(unsynced = 0, item = R.string.account_switch)
        compose.waitForIdle()
        assertEquals(1, switched)
    }
}
