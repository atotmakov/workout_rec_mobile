package com.workoutrec.money

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasTestTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.workoutrec.R
import com.workoutrec.automation.AutomationEntry
import com.workoutrec.automation.AutomationEntryTag
import com.workoutrec.automation.AutomationStatus
import com.workoutrec.workout.MoneyTotals
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

// Feature 007 contracts/screens.md "Automation entry"; spec US4 #1, #2, FR-012
@RunWith(AndroidJUnit4::class)
class AutomationEntryTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun s(id: Int) = context.getString(id)

    @Test
    fun offSaysWhatItAdds() {
        compose.setContent { AutomationEntry(AutomationStatus.NotEnabled, onClick = {}) }
        compose.onNodeWithTag(AutomationEntryTag).assertTextContains(s(R.string.automation_entry_title), substring = true)
        compose.onNodeWithTag(AutomationEntryTag).assertTextContains(s(R.string.automation_entry_off), substring = true)
    }

    @Test
    fun aMissingScriptIsOffToo() {
        compose.setContent { AutomationEntry(AutomationStatus.ScriptMissing, onClick = {}) }
        compose.onNodeWithTag(AutomationEntryTag).assertTextContains(s(R.string.automation_entry_off), substring = true)
    }

    @Test
    fun onAndStopped() {
        val status = mutableStateOf(AutomationStatus.On)
        compose.setContent { AutomationEntry(status.value, onClick = {}) }
        compose.onNodeWithTag(AutomationEntryTag).assertTextContains(s(R.string.automation_entry_on), substring = true)
        status.value = AutomationStatus.Stopped
        compose.onNodeWithTag(AutomationEntryTag).assertTextContains(s(R.string.automation_entry_stopped), substring = true)
    }

    @Test
    fun tappingStartsTheSteps() {
        var taps = 0
        compose.setContent { AutomationEntry(AutomationStatus.NotEnabled, onClick = { taps++ }) }
        compose.onNodeWithTag(AutomationEntryTag).performClick()
        assertEquals(1, taps)
    }

    @Test
    fun theMoneyTabShowsTheEntryEvenWhenTheDownloadFailed() {
        var taps = 0
        val state = MoneyState(
            balance = null,
            note = BalanceNoteState(BalanceNote.Calculated, waiting = false),
            downloadedAt = null,
            error = MoneyError.NETWORK,
            payments = emptyList(),
            totals = MoneyTotals(0.0, 0),
        )
        compose.setContent {
            MoneyScreen(state, ZoneOffset.UTC, onAdd = {}, onRefresh = {}, onEnableAutomation = { taps++ }, automation = AutomationStatus.NotEnabled)
        }
        compose.onNode(hasScrollAction()).performScrollToNode(hasTestTag(AutomationEntryTag))
        compose.onNodeWithTag(AutomationEntryTag).assertIsDisplayed().performClick()
        assertEquals(1, taps)
    }
}
