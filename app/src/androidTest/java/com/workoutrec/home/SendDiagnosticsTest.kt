package com.workoutrec.home

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.workoutrec.R
import com.workoutrec.data.SelectedAccount
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

// Feature 003 User Story 6, FR-012; contracts/monitoring.md "UI"
@RunWith(AndroidJUnit4::class)
class SendDiagnosticsTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val account = SelectedAccount("alex@example.com", "Alexey Totmakov", photoUrl = null)

    private fun openMenu(state: DiagnosticsMenuState) {
        compose.setContent { MainScreen(account = account, onSwitchAccount = {}, onSignOut = {}, diagnostics = state) }
        compose.onNodeWithTag(AccountAvatarTag).performClick()
    }

    @Test
    fun theSwitchIsOnByDefault() {
        openMenu(DiagnosticsMenuState())
        compose.onNodeWithText(context.getString(R.string.account_send_diagnostics)).assertIsDisplayed()
        compose.onNodeWithTag(SendDiagnosticsTag).assertIsOn()
    }

    @Test
    fun turningItOffReportsTheChoice() {
        val changes = mutableListOf<Boolean>()
        openMenu(DiagnosticsMenuState(sendDiagnostics = true, onSendDiagnosticsChange = { changes += it }))
        compose.onNodeWithTag(SendDiagnosticsTag).performClick()
        assertEquals(listOf(false), changes)
    }

    @Test
    fun anOffSwitchShowsOff() {
        openMenu(DiagnosticsMenuState(sendDiagnostics = false))
        compose.onNodeWithTag(SendDiagnosticsTag).assertIsOff()
    }

    @Test
    fun turnedOnAfterStartingOffSaysItAppliesAfterRestart() {
        openMenu(DiagnosticsMenuState(sendDiagnostics = true, appliesAfterRestart = true))
        compose.onNodeWithText(context.getString(R.string.account_send_diagnostics_restart)).assertIsDisplayed()
    }

    @Test
    fun aLongPressOpensTheTestActions() {
        val actions = mutableListOf<DiagnosticsTestAction>()
        openMenu(DiagnosticsMenuState(onTestAction = { actions += it }))
        compose.onNodeWithTag(SendDiagnosticsTag).performTouchInput { longClick() }
        compose.onNodeWithTag(DiagnosticsTestDialogTag).assertIsDisplayed()
        DiagnosticsTestAction.entries.forEach { compose.onNodeWithText(context.getString(it.label)).assertIsDisplayed() }
        compose.onNodeWithText(context.getString(R.string.diagnostics_test_error)).performClick()
        assertEquals(listOf(DiagnosticsTestAction.TEST_ERROR), actions)
    }
}
