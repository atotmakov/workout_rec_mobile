package com.workoutrec.log

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.workoutrec.workout.DayStatus
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

// Feature 006 contracts/screens.md "Day status control"; spec US1, US2 scenarios 1-3
@RunWith(AndroidJUnit4::class)
class DayStatusControlTest {

    @get:Rule
    val compose = createComposeRule()

    private val tag = "status"

    @Test
    fun showsBothOptionsWithTheStatusSelected() {
        compose.setContent { DayStatusControl(DayStatus.PAID, onChange = {}, tag = tag) }
        compose.onNodeWithTag("${tag}_paid").assertIsSelected()
        compose.onNodeWithTag("${tag}_free").assertIsNotSelected()
    }

    @Test
    fun tappingTheOtherOptionChangesItOnce() {
        val changes = mutableListOf<DayStatus>()
        compose.setContent {
            var status by mutableStateOf(DayStatus.PAID)
            DayStatusControl(status, onChange = { changes += it; status = it }, tag = tag)
        }
        compose.onNodeWithTag("${tag}_free").performClick()
        compose.onNodeWithTag("${tag}_free").assertIsSelected()
        assertEquals(listOf(DayStatus.FREE), changes)
    }

    @Test
    fun tappingTheSelectedOptionDoesNothing() {
        val changes = mutableListOf<DayStatus>()
        compose.setContent { DayStatusControl(DayStatus.FREE, onChange = { changes += it }, tag = tag) }
        compose.onNodeWithTag("${tag}_free").performClick()
        assertEquals(emptyList<DayStatus>(), changes)
    }

    @Test
    fun todayShowsTheControlAboveTheExercises() {
        val today = LocalDate.of(2026, 10, 24)
        var changed: DayStatus? = null
        compose.setContent {
            TodayScreen(groups = emptyList(), onAddExercise = {}, onOpenExercise = {}, today = today, statuses = mapOf(today to DayStatus.FREE), onDayStatus = { changed = it })
        }
        compose.onNodeWithTag(TodayTags.DAY_STATUS).assertIsDisplayed()
        compose.onNodeWithTag("${TodayTags.DAY_STATUS}_free").assertIsSelected()
        compose.onNodeWithTag("${TodayTags.DAY_STATUS}_paid").performClick()
        assertEquals(DayStatus.PAID, changed)
    }

    // Spec US2 scenarios 1-3 (analysis F1: written before Today shows the default)
    @Test
    fun todayShowsThePreviousDaysStatusUntilChosen() {
        val today = LocalDate.of(2026, 10, 24)
        compose.setContent {
            todayWith(today, mapOf(today.minusDays(3) to DayStatus.FREE, today.minusDays(1) to DayStatus.PAID))
        }
        compose.onNodeWithTag("${TodayTags.DAY_STATUS}_paid").assertIsSelected()
    }

    @Test
    fun theFirstWorkoutIsFree() {
        compose.setContent { todayWith(LocalDate.of(2026, 10, 24), emptyMap()) }
        compose.onNodeWithTag("${TodayTags.DAY_STATUS}_free").assertIsSelected()
    }

    @Test
    fun todaysOwnStatusWinsOverTheDefault() {
        val today = LocalDate.of(2026, 10, 24)
        compose.setContent {
            todayWith(today, mapOf(today.minusDays(1) to DayStatus.PAID, today to DayStatus.FREE))
        }
        compose.onNodeWithTag("${TodayTags.DAY_STATUS}_free").assertIsSelected()
    }

    @androidx.compose.runtime.Composable
    private fun todayWith(today: LocalDate, statuses: Map<LocalDate, DayStatus>) =
        TodayScreen(groups = emptyList(), onAddExercise = {}, onOpenExercise = {}, today = today, statuses = statuses, onDayStatus = {})
}
