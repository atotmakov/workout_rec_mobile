package com.workoutrec.log

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.workoutrec.workout.Exercise
import com.workoutrec.workout.Reps
import com.workoutrec.workout.SetValues
import com.workoutrec.workout.Weight
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

// contracts/screens.md "Past workout dialog and editor"; US5
@RunWith(AndroidJUnit4::class)
class PastWorkoutTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun dialogStartsWithTheChosenValues() {
        var started: Triple<LocalDate, LocalTime, Int>? = null
        compose.setContent {
            PastWorkoutDialog(
                initialDate = LocalDate.of(2026, 1, 11),
                onStart = { date, start, duration -> started = Triple(date, start, duration); null },
                onDismiss = {},
            )
        }
        compose.onNodeWithTag(PastWorkoutTags.DIALOG).assertIsDisplayed()
        compose.onNodeWithTag(PastWorkoutTags.START).performClick()
        assertEquals(Triple(LocalDate.of(2026, 1, 11), LocalTime.of(18, 0), 60), started)
    }

    @Test
    fun editorShowsTimesWarningAndSaves() {
        var saved = 0
        val state = PastWorkoutState(
            date = LocalDate.of(2026, 1, 11),
            dayHasSets = true,
            sets = listOf(
                DraftSet(SetValues("squat", Weight.ofHundredths(2000), Reps(10)), time = 1_768_154_400),
                DraftSet(SetValues("squat", Weight.ofHundredths(2500), Reps(8)), time = 1_768_158_000),
            ),
        )
        compose.setContent {
            PastWorkoutEditor(
                state = state,
                exercises = listOf(Exercise("squat", "legs")),
                onAddSet = {},
                onRemoveSet = {},
                onSave = { saved++ },
                onCancel = {},
            )
        }
        compose.onNodeWithTag(PastWorkoutTags.DAY_HAS_SETS).assertIsDisplayed()
        compose.onAllNodesWithTag(PastWorkoutTags.SET).assertCountEquals(2)
        compose.onNodeWithTag(PastWorkoutTags.EDITOR).assertTextContains("× 8", substring = true)
        compose.onNodeWithTag(PastWorkoutTags.SAVE).performClick()
        assertEquals(1, saved)
    }
}
