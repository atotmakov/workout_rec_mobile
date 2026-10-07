package com.workoutrec.log

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.workoutrec.R
import com.workoutrec.workout.InvalidReason
import com.workoutrec.workout.Reps
import com.workoutrec.workout.SetKey
import com.workoutrec.workout.SyncState
import com.workoutrec.workout.Weight
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

// contracts/screens.md "Plan sets dialog", "Exercise logging"; FR-003, FR-004
@RunWith(AndroidJUnit4::class)
class ExerciseLoggingScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private val events = mutableListOf<String>()
    private val actions = object : LoggingActions {
        override fun onWeightChange(index: Int, text: String) { events += "w$index=$text" }
        override fun onRepsChange(index: Int, text: String) { events += "r$index=$text" }
        override fun onStepWeight(index: Int, direction: Int) { events += "sw$index$direction" }
        override fun onStepReps(index: Int, direction: Int) { events += "sr$index$direction" }
        override fun onConfirm(index: Int) { events += "c$index" }
        override fun onAddSet() { events += "add" }
    }

    private fun row(weight: String, reps: String, canConfirm: Boolean, weightError: InvalidReason? = null) =
        SetRowState(weightText = weight, repsText = reps, weightError = weightError, repsError = null, canConfirm = canConfirm)

    @Test
    fun planDialogStepsWithinOneToTwenty() {
        var planned = 0
        compose.setContent { PlanSetsDialog(initial = 20, onConfirm = { planned = it }, onDismiss = {}) }
        compose.onNodeWithTag(PlanTags.PLUS).performClick()
        compose.onNodeWithTag(PlanTags.VALUE).assertTextContains("20")
        compose.onNodeWithTag(PlanTags.MINUS).performClick()
        compose.onNodeWithTag(PlanTags.OK).performClick()
        assertEquals(19, planned)
    }

    @Test
    fun rowsShowValuesSteppersAndConfirm() {
        val state = LoggingState(
            exercise = "squat",
            done = emptyList(),
            rows = listOf(row("17,5", "15", canConfirm = true), row("-1", "", canConfirm = false, weightError = InvalidReason.NEGATIVE)),
        )
        compose.setContent { ExerciseLoggingScreen(state = state, actions = actions) }
        compose.onNodeWithTag(LoggingTags.TITLE).assertTextContains("squat")
        compose.onNodeWithTag(LoggingTags.weight(0)).assertTextContains("17,5")
        compose.onNodeWithTag(LoggingTags.confirm(0)).assertIsEnabled()
        compose.onNodeWithTag(LoggingTags.confirm(1)).assertIsNotEnabled()
        compose.onNodeWithText(context.getString(R.string.error_weight_negative)).assertIsDisplayed()

        compose.onNodeWithTag(LoggingTags.weightPlus(0)).performClick()
        compose.onNodeWithTag(LoggingTags.weightMinus(0)).performClick()
        compose.onNodeWithTag(LoggingTags.repsPlus(0)).performClick()
        compose.onNodeWithTag(LoggingTags.repsMinus(0)).performClick()
        compose.onNodeWithTag(LoggingTags.confirm(0)).performClick()
        compose.onNodeWithTag(LoggingTags.ADD_SET).performClick()
        assertEquals(listOf("sw01", "sw0-1", "sr01", "sr0-1", "c0", "add"), events)
    }

    @Test
    fun doneSetsShowTheirSyncState() {
        val key = SetKey(1, "squat", Weight.ofHundredths(4000), Reps(8))
        val state = LoggingState(exercise = "squat", done = listOf(DoneSet(key, SyncState.NOT_SYNCED)), rows = emptyList())
        compose.setContent { ExerciseLoggingScreen(state = state, actions = actions) }
        compose.onNodeWithTag(LoggingTags.done(0)).assertTextContains("× 8", substring = true)
        compose.onNodeWithText(context.getString(R.string.set_not_synced)).assertIsDisplayed()
    }
}
