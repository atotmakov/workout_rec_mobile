package com.workoutrec.log

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.workoutrec.R
import com.workoutrec.workout.LastTime
import com.workoutrec.workout.Record
import com.workoutrec.workout.Reps
import com.workoutrec.workout.SetKey
import com.workoutrec.workout.Weight
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

// contracts/screens.md "Exercise logging": last_time, record, suggested values (FR-011, FR-012)
@RunWith(AndroidJUnit4::class)
class PrefillTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private val actions = object : LoggingActions {
        override fun onWeightChange(index: Int, text: String) = Unit
        override fun onRepsChange(index: Int, text: String) = Unit
        override fun onStepWeight(index: Int, direction: Int) = Unit
        override fun onStepReps(index: Int, direction: Int) = Unit
        override fun onConfirm(index: Int) = Unit
        override fun onAddSet() = Unit
    }

    private fun suggested(value: Boolean) = SemanticsMatcher.expectValue(LoggingTags.Suggested, value)

    @Test
    fun lastTimeRecordAndSuggestedValuesAreShown() {
        val last = LastTime(LocalDate.of(2026, 1, 9), listOf(SetKey(1, "squat", Weight.ofHundredths(4000), Reps(8))))
        val state = LoggingState(
            exercise = "squat",
            done = emptyList(),
            rows = listOf(
                SetRowState("40", "8", null, null, canConfirm = true, weightSuggested = true, repsSuggested = false),
            ),
            lastTime = last,
            record = Record(Weight.ofHundredths(4000), Reps(8)),
        )
        compose.setContent { ExerciseLoggingScreen(state = state, actions = actions) }
        compose.onNodeWithTag(LoggingTags.LAST_TIME).assertTextContains("× 8", substring = true)
        compose.onNodeWithTag(LoggingTags.RECORD).assertTextContains("40", substring = true)
        compose.onNodeWithTag(LoggingTags.weight(0)).assert(suggested(true))
        compose.onNodeWithTag(LoggingTags.reps(0)).assert(suggested(false))
    }

    @Test
    fun firstTimeHasNoRecord() {
        val state = LoggingState(exercise = "plank", done = emptyList(), rows = emptyList())
        compose.setContent { ExerciseLoggingScreen(state = state, actions = actions) }
        compose.onNodeWithTag(LoggingTags.LAST_TIME).assertTextContains(context.getString(R.string.logging_first_time))
        compose.onAllNodesWithTag(LoggingTags.RECORD).assertCountEquals(0)
        compose.onNodeWithTag(LoggingTags.TITLE).assertIsDisplayed()
    }
}
