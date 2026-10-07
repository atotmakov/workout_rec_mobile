package com.workoutrec.log

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.workoutrec.R
import com.workoutrec.workout.Exercise
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

// contracts/screens.md "Exercise picker"; FR-001, FR-002
@RunWith(AndroidJUnit4::class)
class ExercisePickerTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private val exercises = listOf(
        Exercise("pull-up", "back"),
        Exercise("Barbell row", "back"),
        Exercise("bench press", "chest"),
        Exercise("plank", ""),
    )

    @Test
    fun exercisesAreGroupedByMuscleGroup() {
        compose.setContent { ExercisePicker(exercises, recent = emptyList(), onPick = {}, onOpenDrills = {}) }
        compose.onNodeWithText("back").assertIsDisplayed()
        compose.onNodeWithText("chest").assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.picker_other_group)).assertIsDisplayed()
        compose.onNodeWithTag(PickerTags.item("plank")).assertIsDisplayed()
    }

    @Test
    fun searchFiltersByPartOfTheNameIgnoringCase() {
        compose.setContent { ExercisePicker(exercises, recent = listOf("plank"), onPick = {}, onOpenDrills = {}) }
        compose.onNodeWithTag(PickerTags.RECENT).assertIsDisplayed()
        compose.onNodeWithTag(PickerTags.SEARCH).performTextInput("ROW")
        compose.onNodeWithTag(PickerTags.item("Barbell row")).assertIsDisplayed()
        compose.onAllNodesWithTag(PickerTags.item("pull-up")).assertCountEquals(0)
        compose.onAllNodesWithTag(PickerTags.RECENT).assertCountEquals(0)
    }

    @Test
    fun recentExercisesComeFirstAndCanBePicked() {
        var picked: String? = null
        compose.setContent { ExercisePicker(exercises, recent = listOf("bench press"), onPick = { picked = it }, onOpenDrills = {}) }
        compose.onNodeWithTag(PickerTags.recentItem("bench press")).performClick()
        assertEquals("bench press", picked)
    }

    @Test
    fun emptyListOffersToOpenTheDrillsTab() {
        var opened = 0
        compose.setContent { ExercisePicker(emptyList(), recent = emptyList(), onPick = {}, onOpenDrills = { opened++ }) }
        compose.onNodeWithTag(PickerTags.EMPTY).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.picker_open_drills)).performClick()
        assertEquals(1, opened)
    }
}
