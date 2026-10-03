package com.workoutrec.setup

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.workoutrec.R
import com.workoutrec.automation.AutomationStatus
import com.workoutrec.home.AutomationReminder
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

// US2 #4-#9, FR-008 rewrite question, FR-013 two steps, FR-015 reminder
@RunWith(AndroidJUnit4::class)
class SetupScreensTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun s(id: Int) = context.getString(id)

    @Test
    fun rewriteDialogAsksAndSaysWhereOldDataGoes() {
        var answers = mutableListOf<Boolean>()
        compose.setContent { RewriteDialog(onAnswer = { answers += it }) }
        compose.onNodeWithText(
            "Spreadsheet with name workout_rec_database_ already exists in the account, want to rewrite it?",
        ).assertIsDisplayed()
        compose.onNodeWithText(s(R.string.rewrite_backup_note)).assertIsDisplayed()
        compose.onNodeWithText(s(R.string.rewrite_yes)).performClick()
        assertEquals(listOf(true), answers)
    }

    @Test
    fun rewriteDialogNo() {
        var answers = mutableListOf<Boolean>()
        compose.setContent { RewriteDialog(onAnswer = { answers += it }) }
        compose.onNodeWithText(s(R.string.rewrite_no)).performClick()
        assertEquals(listOf(false), answers)
    }

    @Test
    fun step1GuideExplainsTheSettingAndOpensIt() {
        var opened = 0
        var continued = 0
        compose.setContent { AutomationGuideScreen(step = GuideStep.ApiSetting, onOpen = { opened++ }, onContinue = { continued++ }) }
        compose.onNodeWithText(s(R.string.guide_step1_title)).assertIsDisplayed()
        compose.onNodeWithText(s(R.string.guide_step1_text)).assertIsDisplayed()
        compose.onNodeWithText(s(R.string.guide_open)).performClick()
        compose.onNodeWithText(s(R.string.guide_continue)).performClick()
        assertEquals(1, opened)
        assertEquals(1, continued)
    }

    @Test
    fun step2GuideExplainsTheUnverifiedAppScreen() {
        compose.setContent { AutomationGuideScreen(step = GuideStep.Enable, onOpen = {}, onContinue = {}) }
        compose.onNodeWithText(s(R.string.guide_step2_title)).assertIsDisplayed()
        compose.onNodeWithText(s(R.string.guide_step2_text)).assertIsDisplayed()
        compose.onNodeWithText(s(R.string.guide_continue)).assertIsDisplayed()
    }

    @Test
    fun reminderNamesTheMissingStep() {
        var opened = 0
        compose.setContent { AutomationReminder(status = AutomationStatus.NotEnabled, onFix = { opened++ }) }
        compose.onNodeWithText(s(R.string.reminder_not_enabled)).assertIsDisplayed()
        compose.onNodeWithText(s(R.string.reminder_fix)).performClick()
        assertEquals(1, opened)
    }

    @Test
    fun reminderForMissingScriptAndStoppedAutomation() {
        compose.setContent { AutomationReminder(status = AutomationStatus.ScriptMissing, onFix = {}) }
        compose.onNodeWithText(s(R.string.reminder_script_missing)).assertIsDisplayed()
    }

    @Test
    fun reminderForStoppedAutomation() {
        compose.setContent { AutomationReminder(status = AutomationStatus.Stopped, onFix = {}) }
        compose.onNodeWithText(s(R.string.reminder_stopped)).assertIsDisplayed()
    }
}
