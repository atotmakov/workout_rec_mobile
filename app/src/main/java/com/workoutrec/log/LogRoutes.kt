package com.workoutrec.log

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.workoutrec.AppContainer
import com.workoutrec.R
import com.workoutrec.setup.Browser
import com.workoutrec.sync.SyncStatus
import com.workoutrec.workout.DisplayModel
import java.time.DateTimeException
import java.time.LocalDate
import java.time.ZoneId

/** Navigation routes of the logging screens (contracts/screens.md "Navigation"). */
object LogRoutes {
    const val PICKER = "picker"
    const val LOGGING = "log/{exercise}?sets={sets}"
    fun logging(exercise: String, sets: Int) = "log/${android.net.Uri.encode(exercise)}?sets=$sets"
}

/** Day grouping uses the spreadsheet's time zone once known (research R8). */
fun SyncStatus.zone(): ZoneId =
    sheet?.timeZone?.let { tz -> try { ZoneId.of(tz) } catch (e: DateTimeException) { null } } ?: ZoneId.systemDefault()

/** Today's workout as the main screen content (FR-000). */
@Composable
fun TodayRoute(
    container: AppContainer,
    onAddExercise: () -> Unit,
    onOpenExercise: (String) -> Unit,
    modifier: Modifier = Modifier,
    header: @Composable () -> Unit = {},
) {
    val sets by container.workoutRepository.displaySets.collectAsState(initial = emptyList())
    val status by container.syncStatusStore.status.collectAsState(initial = SyncStatus())
    val zone = status.zone()
    val groups = remember(sets, zone) { DisplayModel.today(sets, LocalDate.now(zone), zone) }
    LaunchedEffect(Unit) { container.syncScheduler.requestSync() }
    TodayScreen(groups, onAddExercise, onOpenExercise, modifier, header)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PickerRoute(container: AppContainer, onBack: () -> Unit, onStart: (exercise: String, sets: Int) -> Unit) {
    val context = LocalContext.current
    val exercises by container.workoutRepository.exercises.collectAsState(initial = emptyList())
    val sets by container.workoutRepository.displaySets.collectAsState(initial = emptyList())
    val binding by container.settingsStore.binding.collectAsState(initial = null)
    var picked by rememberSaveable { mutableStateOf<String?>(null) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.picker_title)) },
                navigationIcon = { TextButton(onClick = onBack) { Text("←") } },
            )
        },
    ) { padding ->
        ExercisePicker(
            exercises = exercises,
            recent = remember(sets) { DisplayModel.recentExercises(sets) },
            onPick = { picked = it },
            onOpenDrills = {
                binding?.spreadsheetId?.let { Browser.open(context, "https://docs.google.com/spreadsheets/d/$it/edit") }
            },
            modifier = Modifier.padding(padding),
        )
    }
    picked?.let { exercise ->
        PlanSetsDialog(initial = DEFAULT_SETS, onConfirm = { onStart(exercise, it); picked = null }, onDismiss = { picked = null })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoggingRoute(container: AppContainer, exercise: String, plannedSets: Int, onBack: () -> Unit) {
    val viewModel: ExerciseLoggingViewModel = viewModel(
        key = "logging:$exercise",
        factory = viewModelFactory {
            initializer {
                ExerciseLoggingViewModel(
                    exercise = exercise,
                    store = container.workoutRepository,
                    requestSync = container.syncScheduler::requestSync,
                )
            }
        },
    )
    var planned by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!planned && plannedSets > 0) viewModel.plan(plannedSets)
        planned = true
    }
    val state by viewModel.state.collectAsState()
    Scaffold(
        topBar = { TopAppBar(title = { Text(exercise) }, navigationIcon = { TextButton(onClick = onBack) { Text("←") } }) },
    ) { padding ->
        ExerciseLoggingScreen(
            state = state,
            actions = object : LoggingActions {
                override fun onWeightChange(index: Int, text: String) = viewModel.setWeight(index, text)
                override fun onRepsChange(index: Int, text: String) = viewModel.setReps(index, text)
                override fun onStepWeight(index: Int, direction: Int) = viewModel.stepWeight(index, direction)
                override fun onStepReps(index: Int, direction: Int) = viewModel.stepReps(index, direction)
                override fun onConfirm(index: Int) = viewModel.confirm(index)
                override fun onAddSet() = viewModel.addSet()
            },
            modifier = Modifier.padding(padding),
        )
    }
}

/** Planned sets when there is no history yet (FR-012). */
const val DEFAULT_SETS = 3
