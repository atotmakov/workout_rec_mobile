package com.workoutrec.log

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.workoutrec.R
import com.workoutrec.workout.ExerciseGroup
import com.workoutrec.workout.SetKey
import java.util.Locale

/** Test tags of contracts/screens.md "Main (Today)". */
object TodayTags {
    const val SCREEN = "today_screen"
    const val EMPTY = "today_empty"
    const val ADD_EXERCISE = "add_exercise"
    const val WORKOUTS = "menu_workouts"
    fun exercise(index: Int) = "today_exercise_$index"
}

/** Today's workout with "Add exercise" (FR-000, FR-013). */
@Composable
fun TodayScreen(
    groups: List<ExerciseGroup>,
    onAddExercise: () -> Unit,
    onOpenExercise: (String) -> Unit,
    modifier: Modifier = Modifier,
    header: @Composable () -> Unit = {},
) {
    val locale = LocalConfiguration.current.locales[0]
    Column(modifier.fillMaxSize().padding(16.dp).testTag(TodayTags.SCREEN), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        header()
        if (groups.isEmpty()) {
            Text(stringResource(R.string.today_empty), modifier = Modifier.testTag(TodayTags.EMPTY))
        }
        LazyColumn(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(groups) { index, group ->
                Card(
                    Modifier.fillMaxWidth()
                        .testTag(TodayTags.exercise(index))
                        .semantics(mergeDescendants = true) {}
                        .clickable { onOpenExercise(group.exercise) },
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(group.exercise, style = MaterialTheme.typography.titleMedium)
                        Text(group.sets.map { setText(it.key, locale) }.joinToString("   "))
                    }
                }
            }
        }
        Button(onClick = onAddExercise, modifier = Modifier.fillMaxWidth().testTag(TodayTags.ADD_EXERCISE)) {
            Text(stringResource(R.string.today_add_exercise))
        }
    }
}

/** "17,5 × 15" (contracts/screens.md). */
@Composable
fun setText(key: SetKey, locale: Locale): String =
    stringResource(R.string.set_weight_reps, key.weight.format(locale), key.reps.value)
