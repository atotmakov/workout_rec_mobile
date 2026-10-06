package com.workoutrec.log

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.workoutrec.workout.Exercise

/** Test tags of contracts/screens.md "Exercise picker". */
object PickerTags {
    const val SCREEN = "exercise_picker"
    const val SEARCH = "exercise_search"
    const val RECENT = "exercise_recent"
    const val EMPTY = "exercise_list_empty"
    fun item(name: String) = "exercise_item_$name"
    fun recentItem(name: String) = "exercise_recent_item_$name"
}

/** Exercises of the drills tab, grouped by muscle group, with search and recent ones (FR-001). */
@Composable
fun ExercisePicker(
    exercises: List<Exercise>,
    recent: List<String>,
    onPick: (String) -> Unit,
    onOpenDrills: () -> Unit,
    modifier: Modifier = Modifier,
): Unit = TODO()
