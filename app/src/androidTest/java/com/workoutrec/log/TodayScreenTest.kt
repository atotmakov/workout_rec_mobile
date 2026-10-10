package com.workoutrec.log

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.workoutrec.workout.DisplaySet
import com.workoutrec.workout.ExerciseGroup
import com.workoutrec.workout.Reps
import com.workoutrec.workout.SetKey
import com.workoutrec.workout.SetRef
import com.workoutrec.workout.SyncState
import com.workoutrec.workout.Weight
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

// contracts/screens.md "Main (Today)"; FR-000
@RunWith(AndroidJUnit4::class)
class TodayScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private fun set(t: Long, kg: Long, reps: Int, exercise: String) =
        SetKey(t, exercise, Weight.ofHundredths(kg), Reps(reps)).let { DisplaySet(it, SyncState.SYNCED, SetRef.Row(1, it)) }

    @Test
    fun emptyDayShowsTheEmptyStateAndAddExercise() {
        var added = 0
        compose.setContent { TodayScreen(groups = emptyList(), onAddExercise = { added++ }, onOpenExercise = {}) }
        compose.onNodeWithTag(TodayTags.EMPTY).assertIsDisplayed()
        compose.onNodeWithTag(TodayTags.ADD_EXERCISE).performClick()
        assertEquals(1, added)
    }

    @Test
    fun exercisesAreListedWithTheirSetsAndOpen() {
        var opened: String? = null
        val groups = listOf(
            ExerciseGroup("squat", listOf(set(1, 1750, 15, "squat"), set(2, 2000, 12, "squat"))),
            ExerciseGroup("bench", listOf(set(3, 4000, 8, "bench"))),
        )
        compose.setContent { TodayScreen(groups = groups, onAddExercise = {}, onOpenExercise = { opened = it }) }
        compose.onNodeWithTag(TodayTags.exercise(0)).assertTextContains("squat", substring = true)
        compose.onNodeWithTag(TodayTags.exercise(0)).assertTextContains("× 15", substring = true)
        compose.onNodeWithTag(TodayTags.exercise(1)).assertTextContains("bench", substring = true)
        compose.onNodeWithTag(TodayTags.exercise(1)).performClick()
        assertEquals("bench", opened)
    }

    // Rows deleted in the web UI stayed until the 5-minute refresh (research R7); pulling down syncs now.
    @Test
    fun pullingDownRefreshes() {
        var refreshed = 0
        val groups = listOf(ExerciseGroup("squat", listOf(set(1_768_240_800, 2000, 10, "squat"))))
        compose.setContent { TodayScreen(groups = groups, onAddExercise = {}, onOpenExercise = {}, onRefresh = { refreshed++ }) }
        compose.onNodeWithTag(TodayTags.SCREEN).performTouchInput { swipeDown() }
        compose.waitForIdle()
        assertEquals(1, refreshed)
    }

    @Test
    fun pullingDownRefreshesAnEmptyDay() {
        var refreshed = 0
        compose.setContent { TodayScreen(groups = emptyList(), onAddExercise = {}, onOpenExercise = {}, onRefresh = { refreshed++ }) }
        compose.onNodeWithTag(TodayTags.SCREEN).performTouchInput { swipeDown() }
        compose.waitForIdle()
        assertEquals(1, refreshed)
    }
}
