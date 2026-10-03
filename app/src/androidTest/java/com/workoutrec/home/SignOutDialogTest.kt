package com.workoutrec.home

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
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

// FR-012: signing out requires confirmation
@RunWith(AndroidJUnit4::class)
class SignOutDialogTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun s(id: Int) = context.getString(id)
    private val account = SelectedAccount("alex@example.com", "Alexey Totmakov", null)

    private fun openSignOut(onSignOut: () -> Unit) {
        compose.setContent { MainScreen(account = account, onSwitchAccount = {}, onSignOut = onSignOut) }
        compose.onNodeWithTag(AccountAvatarTag).performClick()
        compose.onNodeWithText(s(R.string.account_sign_out)).performClick()
    }

    @Test
    fun signOutAsksForConfirmation() {
        var signedOut = 0
        openSignOut { signedOut++ }
        compose.onNodeWithText(s(R.string.sign_out_question)).assertIsDisplayed()
        assertEquals(0, signedOut)
    }

    @Test
    fun cancelKeepsTheAccount() {
        var signedOut = 0
        openSignOut { signedOut++ }
        compose.onNodeWithText(s(R.string.sign_out_cancel)).performClick()
        compose.waitForIdle()
        assertEquals(0, signedOut)
    }

    @Test
    fun confirmSignsOut() {
        var signedOut = 0
        openSignOut { signedOut++ }
        compose.onNodeWithText(s(R.string.sign_out_confirm)).performClick()
        compose.waitForIdle()
        assertEquals(1, signedOut)
    }
}
