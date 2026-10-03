package com.workoutrec.setup

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.workoutrec.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

// FR-002 (explain before asking), US1 #4 (choose again)
@RunWith(AndroidJUnit4::class)
class ChooseAccountScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun explanationIsVisibleBeforeAsking() {
        var clicks = 0
        compose.setContent { ChooseAccountScreen(message = null, onChooseAccount = { clicks++ }) }
        compose.onNodeWithText(context.getString(R.string.setup_access_explanation)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.setup_choose_account)).assertIsDisplayed()
        assertEquals(0, clicks)
        compose.onNodeWithText(context.getString(R.string.setup_choose_account)).performClick()
        assertEquals(1, clicks)
    }

    @Test
    fun accountRequiredMessageOffersChooseAgain() {
        compose.setContent { ChooseAccountScreen(message = SetupMessage.AccountRequired, onChooseAccount = {}) }
        compose.onNodeWithText(context.getString(R.string.setup_account_required)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.setup_choose_again)).assertIsDisplayed()
    }

    @Test
    fun accessRevokedMessageIsShown() {
        compose.setContent { ChooseAccountScreen(message = SetupMessage.AccessRevoked, onChooseAccount = {}) }
        compose.onNodeWithText(context.getString(R.string.setup_access_revoked)).assertIsDisplayed()
    }
}
