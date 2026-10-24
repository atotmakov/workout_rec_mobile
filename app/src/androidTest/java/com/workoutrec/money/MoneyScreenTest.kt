package com.workoutrec.money

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.workoutrec.R
import com.workoutrec.workout.MoneyTotals
import com.workoutrec.workout.ShownPayment
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

// Feature 004 US4, FR-009, FR-009a, FR-009b (contracts/screens.md "Money")
@RunWith(AndroidJUnit4::class)
class MoneyScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun s(id: Int) = context.getString(id)
    private val res get() = context.resources

    private val oct = { day: Int -> LocalDate.of(2026, 10, day) }
    private val payments = listOf(
        ShownPayment(oct(21), 4.0, 400_000, pending = true),
        ShownPayment(oct(1), 8.0, 800_000, pending = false),
    )
    private val lastRun = 1_792_551_600_000L

    private fun state(
        balance: Double? = 11.0,
        note: BalanceNoteState = BalanceNoteState(BalanceNote.Calculated, waiting = false),
        error: MoneyError = MoneyError.NONE,
        shown: List<ShownPayment> = payments,
    ) = MoneyState(balance, note, downloadedAt = lastRun, error = error, payments = shown, totals = MoneyTotals(12.0, 1_200_000))

    private fun show(state: MoneyState, onAdd: () -> Unit = {}, onRefresh: () -> Unit = {}, onEnable: () -> Unit = {}) {
        compose.setContent { MoneyScreen(state, ZoneOffset.UTC, onAdd = onAdd, onRefresh = onRefresh, onEnableAutomation = onEnable) }
    }

    @Test
    fun balanceTotalsAndPaymentsNewestFirst() {
        show(state())
        compose.onNodeWithTag(MoneyTags.BALANCE).assertTextContains("11", substring = true)
        compose.onNodeWithTag(MoneyTags.BALANCE).assertTextContains(res.getQuantityString(R.plurals.money_left, 11), substring = true)
        compose.onNodeWithTag(MoneyTags.TOTALS).assertIsDisplayed()
        compose.onNodeWithTag(MoneyTags.payment(0)).assertTextContains("4", substring = true)
        compose.onNodeWithTag(MoneyTags.waiting(0), useUnmergedTree = true).assertExists()
        compose.onNodeWithTag(MoneyTags.waiting(1), useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag(MoneyTags.DOWNLOADED).assertIsDisplayed()
    }

    @Test
    fun aNegativeBalanceIsShownAsWorkoutsOwed() {
        show(state(balance = -2.0))
        compose.onNodeWithTag(MoneyTags.BALANCE).assertTextContains(res.getQuantityString(R.plurals.money_owed, 2), substring = true)
    }

    // Feature 007 research R9, FR-005a
    @Test
    fun noteSaysTheSpreadsheetCalculatesAndWhenChangesAreWaiting() {
        show(state(note = BalanceNoteState(BalanceNote.Calculated, waiting = true)))
        compose.onNodeWithTag(MoneyTags.NOTE).assertTextContains(s(R.string.money_note_calculated), substring = true)
        compose.onNodeWithTag(MoneyTags.NOTE).assertTextContains(s(R.string.money_note_waiting), substring = true)
    }

    @Test
    fun noWaitingLineWhenNothingWaits() {
        show(state())
        compose.onNodeWithTag(MoneyTags.NOTE).assertTextContains(s(R.string.money_note_calculated))
    }

    @Test
    fun anOldScriptKeepsTheDailyNote() {
        show(state(note = BalanceNoteState(BalanceNote.Daily(lastRun), waiting = false)))
        compose.onNodeWithTag(MoneyTags.NOTE).assertTextContains(s(R.string.money_note_daily).substringBefore("%").trim(), substring = true)
    }

    @Test
    fun emptyAndErrorStates() {
        show(state(balance = null, shown = emptyList(), error = MoneyError.MISSING_TAB))
        compose.onNodeWithTag(MoneyTags.EMPTY).assertIsDisplayed()
        compose.onNodeWithTag(MoneyTags.ERROR).assertTextContains(s(R.string.money_error_missing_tab))
    }

    @Test
    fun addAndPullToRefresh() {
        var added = 0
        var refreshed = 0
        show(state(), onAdd = { added++ }, onRefresh = { refreshed++ })
        compose.onNodeWithTag(MoneyTags.ADD).performClick()
        compose.onNodeWithTag(MoneyTags.REFRESH).performTouchInput { swipeDown() }
        compose.waitForIdle()
        assertEquals(1 to 1, added to refreshed)
    }

    @Test
    fun paymentDialogStartsFromTheLastPaymentAndChecksValues() {
        var saved: Triple<LocalDate, Int, Long>? = null
        compose.setContent {
            PaymentDialog(today = oct(22), defaultWorkouts = 8, defaultSum = 800_000, onSave = { d, w, sum -> saved = Triple(d, w, sum) }, onDismiss = {})
        }
        compose.onNodeWithTag(MoneyTags.DATE).assertIsDisplayed()
        compose.onNodeWithTag(MoneyTags.SAVE).assertIsEnabled()
        compose.onNodeWithTag(MoneyTags.WORKOUTS).performTextClearance()
        compose.onNodeWithTag(MoneyTags.SAVE).assertIsNotEnabled()
        compose.onNodeWithTag(MoneyTags.WORKOUTS).performTextInput("12")
        compose.onNodeWithTag(MoneyTags.SUM).performTextClearance()
        compose.onNodeWithTag(MoneyTags.SUM).performTextInput("12000,50")
        compose.onNodeWithTag(MoneyTags.SAVE).performClick()
        assertEquals(Triple(oct(22), 12, 1_200_050L), saved)
    }
}
