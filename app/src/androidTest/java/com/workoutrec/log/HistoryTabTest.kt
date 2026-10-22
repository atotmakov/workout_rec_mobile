package com.workoutrec.log

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.workoutrec.R
import com.workoutrec.workout.DaySummary
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

// Feature 004 US2, FR-006 (contracts/screens.md "History")
@RunWith(AndroidJUnit4::class)
class HistoryTabTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun listsPastDaysUnderTheHistoryTitle() {
        val day = LocalDate.of(2026, 10, 14)
        var opened: LocalDate? = null
        compose.setContent { HistoryScreen(days = listOf(DaySummary(day, 2, 5)), onOpenDay = { opened = it }, onPastWorkout = {}) }
        compose.onNodeWithText(context.getString(R.string.tab_history)).assertIsDisplayed()
        compose.onNodeWithTag(CorrectionTags.pastDay(day)).performClick()
        assertEquals(day, opened)
    }

    @Test
    fun topBarActionEntersAPastWorkout() {
        var started = 0
        compose.setContent { HistoryScreen(days = listOf(DaySummary(LocalDate.of(2026, 10, 14), 1, 1)), onOpenDay = {}, onPastWorkout = { started++ }) }
        compose.onNodeWithTag(HistoryTags.PAST_WORKOUT).performClick()
        assertEquals(1, started)
    }

    @Test
    fun emptyHistoryOffersAPastWorkout() {
        var started = 0
        compose.setContent { HistoryScreen(days = emptyList(), onOpenDay = {}, onPastWorkout = { started++ }) }
        compose.onNodeWithTag(HistoryTags.EMPTY).assertIsDisplayed()
        compose.onNodeWithTag(HistoryTags.EMPTY_PAST_WORKOUT).performClick()
        assertEquals(1, started)
    }
}
