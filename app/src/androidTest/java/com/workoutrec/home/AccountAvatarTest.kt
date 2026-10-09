package com.workoutrec.home

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.workoutrec.R
import com.workoutrec.data.SelectedAccount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

// US1 #2, #5; FR-003, FR-005
@RunWith(AndroidJUnit4::class)
class AccountAvatarTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val account = SelectedAccount("alex@example.com", "Alexey Totmakov", photoUrl = null)

    @Test
    fun avatarIsInTheTopRightCorner() {
        compose.setContent { MainScreen(account = account, onSwitchAccount = {}, onSignOut = {}) }
        val avatar = compose.onNodeWithTag(AccountAvatarTag).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val root = compose.onNodeWithTag(MainScreenTag).fetchSemanticsNode().boundsInRoot
        assertTrue("avatar should be at the right edge", root.right - avatar.right < with(compose.density) { 32.dp.toPx() })
        assertTrue("avatar should be at the top", avatar.top - root.top < with(compose.density) { 80.dp.toPx() })
    }

    @Test
    fun initialsAreShownWithoutPhoto() {
        compose.setContent { AccountAvatar(account = account, onClick = {}) }
        compose.onNodeWithText("AT").assertIsDisplayed()
    }

    @Test
    fun tappingAvatarShowsNameEmailAndActions() {
        compose.setContent { MainScreen(account = account, onSwitchAccount = {}, onSignOut = {}) }
        compose.onNodeWithTag(AccountAvatarTag).performClick()
        compose.onNodeWithText("Alexey Totmakov").assertIsDisplayed()
        compose.onNodeWithText("alex@example.com").assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.account_switch)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.account_sign_out)).assertIsDisplayed()
    }

    // quickstart-results.md issue 1: the sync log is shared from the account menu.
    @Test
    fun theMenuSharesTheSyncLog() {
        var shared = 0
        compose.setContent { MainScreen(account = account, onSwitchAccount = {}, onSignOut = {}, onShareLog = { shared++ }) }
        compose.onNodeWithTag(AccountAvatarTag).performClick()
        compose.onNodeWithText(context.getString(R.string.account_share_log)).assertIsDisplayed().performClick()
        assertEquals(1, shared)
    }
}
