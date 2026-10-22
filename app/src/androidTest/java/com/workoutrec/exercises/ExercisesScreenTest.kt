package com.workoutrec.exercises

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.workoutrec.R
import com.workoutrec.log.LoggingTags
import com.workoutrec.workout.LastTime
import com.workoutrec.workout.Record
import com.workoutrec.workout.Reps
import com.workoutrec.workout.SetKey
import com.workoutrec.workout.ShownExercise
import com.workoutrec.workout.Weight
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

// Feature 004 US3, FR-007, FR-008 (contracts/screens.md "Exercises")
@RunWith(AndroidJUnit4::class)
class ExercisesScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun s(id: Int) = context.getString(id)

    private val bench = ShownExercise("Bench press", "Chest", pending = false, deletable = false)
    private val facePull = ShownExercise("Face pull", "Shoulders", pending = true, deletable = true)
    private val squat = ShownExercise("Squat", "Legs", pending = false, deletable = true)

    @Test
    fun listsExercisesByGroupAndSearches() {
        var opened: String? = null
        compose.setContent { ExercisesScreen(listOf(bench, facePull, squat), onOpen = { opened = it }, onAdd = {}, onRefresh = {}) }
        compose.onNodeWithText("Chest").assertIsDisplayed()
        compose.onNodeWithText("Shoulders").assertIsDisplayed()
        compose.onNodeWithTag(ExercisesTags.SEARCH).performTextInput("SQU")
        compose.onNodeWithTag(ExercisesTags.item("Bench press")).assertDoesNotExist()
        compose.onNodeWithTag(ExercisesTags.item("Squat")).performClick()
        assertEquals("Squat", opened)
    }

    @Test
    fun waitingExercisesAreMarked() {
        compose.setContent { ExercisesScreen(listOf(bench, facePull), onOpen = {}, onAdd = {}, onRefresh = {}) }
        compose.onNodeWithTag(ExercisesTags.waiting("Face pull"), useUnmergedTree = true).assertExists()
        compose.onNodeWithTag(ExercisesTags.waiting("Bench press"), useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun emptyListOffersToAdd() {
        var added = 0
        compose.setContent { ExercisesScreen(emptyList(), onOpen = {}, onAdd = { added++ }, onRefresh = {}) }
        compose.onNodeWithTag(ExercisesTags.EMPTY).assertIsDisplayed()
        compose.onNodeWithTag(ExercisesTags.ADD).performClick()
        assertEquals(1, added)
    }

    @Test
    fun pullingDownRefreshesOnce() {
        var refreshed = 0
        compose.setContent { ExercisesScreen(listOf(bench), onOpen = {}, onAdd = {}, onRefresh = { refreshed++ }) }
        compose.onNodeWithTag(ExercisesTags.REFRESH).performTouchInput { swipeDown() }
        compose.waitForIdle()
        assertEquals(1, refreshed)
    }

    @Test
    fun dialogSuggestsGroupsAndRefusesInvalidNames() {
        var saved: Pair<String, String>? = null
        val shown = listOf(bench, squat)
        compose.setContent {
            ExerciseDialog(
                title = "Add",
                initialName = "",
                initialGroup = "",
                groups = listOf("Chest", "Legs"),
                check = { name, group -> ExerciseRules.check(name, group, shown) },
                onSave = { name, group -> saved = name to group },
                onDismiss = {},
            )
        }
        compose.onNodeWithTag(ExercisesTags.SAVE).assertIsNotEnabled()
        compose.onNodeWithTag(ExercisesTags.GROUP).performClick()
        compose.onNodeWithText("Legs").performClick()
        compose.onNodeWithTag(ExercisesTags.NAME).performTextInput("squat")
        compose.onNodeWithText(s(R.string.exercise_exists)).assertIsDisplayed()
        compose.onNodeWithTag(ExercisesTags.SAVE).assertIsNotEnabled()
        compose.onNodeWithTag(ExercisesTags.NAME).performTextClearance()
        compose.onNodeWithTag(ExercisesTags.NAME).performTextInput("Lunge")
        compose.onNodeWithTag(ExercisesTags.SAVE).assertIsEnabled().performClick()
        assertEquals("Lunge" to "Legs", saved)
    }

    private val lastTime = LastTime(LocalDate.of(2026, 10, 14), listOf(SetKey(1, "Bench press", Weight.ofHundredths(6000), Reps(8))))

    @Test
    fun detailShowsHistoryAndActions() {
        var log = 0
        var rename = 0
        compose.setContent {
            ExerciseDetailScreen(
                exercise = bench,
                lastTime = lastTime,
                record = Record(Weight.ofHundredths(7500), Reps(5)),
                onLogToday = { log++ },
                onRename = { rename++ },
                onDelete = {},
                onBack = {},
            )
        }
        compose.onNodeWithTag(LoggingTags.LAST_TIME).assertIsDisplayed()
        compose.onNodeWithTag(LoggingTags.RECORD).assertIsDisplayed()
        compose.onNodeWithTag(ExercisesTags.LOG_TODAY).performClick()
        compose.onNodeWithTag(ExercisesTags.RENAME).performClick()
        assertEquals(1 to 1, log to rename)
        // Bench press has sets: no Delete, a note instead (FR-008b).
        compose.onNodeWithTag(ExercisesTags.DELETE).assertDoesNotExist()
        compose.onNodeWithTag(ExercisesTags.IN_USE_NOTE).assertIsDisplayed()
    }

    @Test
    fun deleteAsksFirst() {
        var deleted = 0
        compose.setContent {
            ExerciseDetailScreen(exercise = facePull, lastTime = null, record = null, onLogToday = {}, onRename = {}, onDelete = { deleted++ }, onBack = {})
        }
        compose.onNodeWithTag(ExercisesTags.IN_USE_NOTE).assertDoesNotExist()
        compose.onNodeWithTag(ExercisesTags.DELETE).performClick()
        assertEquals(0, deleted)
        compose.onNodeWithTag(ExercisesTags.DELETE_CONFIRM).performClick()
        assertEquals(1, deleted)
    }
}
