package com.workoutrec.exercises

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.workoutrec.AppContainer
import com.workoutrec.R
import com.workoutrec.log.zone
import com.workoutrec.sync.SyncStatus
import com.workoutrec.workout.History
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The Exercises tab's first screen with the add dialog (feature 004 US3). */
@Composable
fun ExercisesRoute(container: AppContainer, onOpen: (String) -> Unit, modifier: Modifier = Modifier, actions: @Composable () -> Unit = {}) {
    val exercises by container.workoutRepository.shownExercises.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var adding by rememberSaveable { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    ExercisesScreen(
        exercises = exercises,
        onOpen = onOpen,
        onAdd = { adding = true },
        // Pulling down re-reads the drills tab with a sync (spec US3 scenario 4).
        onRefresh = {
            scope.launch {
                refreshing = true
                try {
                    withContext(Dispatchers.Default) {
                        container.diagnosticLog.write("exercises pulled to refresh")
                        container.syncScheduler.runNow()
                    }
                } finally {
                    refreshing = false
                }
            }
        },
        modifier = modifier,
        refreshing = refreshing,
        actions = actions,
    )
    if (adding) {
        ExerciseDialog(
            title = stringResource(R.string.exercise_add_title),
            initialName = "",
            initialGroup = "",
            groups = exercises.map { it.muscleGroup }.filter { it.isNotBlank() }.distinct(),
            check = { name, group -> ExerciseRules.check(name, group, exercises) },
            onSave = { name, group ->
                adding = false
                scope.launch {
                    container.workoutRepository.addExercise(name, group)
                    container.syncScheduler.requestSync()
                }
            },
            onDismiss = { adding = false },
        )
    }
}

/** One exercise with rename and delete (feature 004 US3 scenarios 5–9). */
@Composable
fun ExerciseDetailRoute(
    container: AppContainer,
    name: String,
    onBack: () -> Unit,
    onLogToday: (String) -> Unit,
    onRenamed: (String) -> Unit,
    actions: @Composable () -> Unit = {},
) {
    val exercises by container.workoutRepository.shownExercises.collectAsState(initial = null)
    val sets by container.workoutRepository.displaySets.collectAsState(initial = emptyList())
    val status by container.syncStatusStore.status.collectAsState(initial = SyncStatus())
    val scope = rememberCoroutineScope()
    var renaming by rememberSaveable { mutableStateOf(false) }
    val exercise = exercises?.firstOrNull { it.name == name } ?: return
    val zone = status.zone()
    val lastTime = remember(sets, name, zone) { History.lastTime(sets, name, LocalDate.now(zone), zone) }
    val record = remember(sets, name) { History.record(sets, name) }
    ExerciseDetailScreen(
        exercise = exercise,
        lastTime = lastTime,
        record = record,
        onLogToday = { onLogToday(name) },
        onRename = { renaming = true },
        onDelete = {
            scope.launch {
                container.workoutRepository.deleteExercise(name)
                container.syncScheduler.requestSync()
            }
            onBack()
        },
        onBack = onBack,
        actions = actions,
    )
    if (renaming) {
        val all = exercises.orEmpty()
        ExerciseDialog(
            title = stringResource(R.string.exercise_rename_title),
            initialName = exercise.name,
            initialGroup = exercise.muscleGroup,
            groups = all.map { it.muscleGroup }.filter { it.isNotBlank() }.distinct(),
            check = { newName, group -> ExerciseRules.check(newName, group, all, renaming = name) },
            onSave = { newName, group ->
                renaming = false
                scope.launch {
                    container.workoutRepository.renameExercise(name, newName, group)
                    container.syncScheduler.requestSync()
                    if (newName != name) onRenamed(newName)
                }
            },
            onDismiss = { renaming = false },
        )
    }
}
