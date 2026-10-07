package com.workoutrec.log

import androidx.compose.runtime.Composable
import com.workoutrec.workout.Exercise
import com.workoutrec.workout.PastWorkoutError
import com.workoutrec.workout.SetValues
import java.time.LocalDate
import java.time.LocalTime

/** Test tags of contracts/screens.md "Past workout dialog and editor". */
object PastWorkoutTags {
    const val MENU = "menu_past_workout"
    const val DIALOG = "past_workout"
    const val START = "past_workout_start"
    const val EDITOR = "past_workout_editor"
    const val DAY_HAS_SETS = "past_workout_day_has_sets"
    const val SET = "past_workout_set"
    const val SAVE = "past_workout_save"
}

/** Date (not in the future), start time and duration (FR-006a). */
@Composable
fun PastWorkoutDialog(
    initialDate: LocalDate,
    onStart: (date: LocalDate, start: LocalTime, durationMinutes: Int) -> PastWorkoutError?,
    onDismiss: () -> Unit,
): Unit = TODO()

/** The draft's sets with their computed times; Save makes them pending (US5). */
@Composable
fun PastWorkoutEditor(
    state: PastWorkoutState,
    exercises: List<Exercise>,
    onAddSet: (SetValues) -> Unit,
    onRemoveSet: (Int) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
): Unit = TODO()
