package com.workoutrec.log

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.workoutrec.workout.ExerciseGroup

/** Test tags of contracts/screens.md "Main (Today)". */
object TodayTags {
    const val SCREEN = "today_screen"
    const val EMPTY = "today_empty"
    const val ADD_EXERCISE = "add_exercise"
    fun exercise(index: Int) = "today_exercise_$index"
}

/** Today's workout with "Add exercise" (FR-000, FR-013). */
@Composable
fun TodayScreen(
    groups: List<ExerciseGroup>,
    onAddExercise: () -> Unit,
    onOpenExercise: (String) -> Unit,
    modifier: Modifier = Modifier,
): Unit = TODO()
