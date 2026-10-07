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
import com.workoutrec.workout.History
import java.time.DateTimeException
import java.time.LocalDate
import java.time.ZoneId
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import com.workoutrec.workout.DisplaySet
import com.workoutrec.workout.Parsed
import com.workoutrec.workout.Reps
import com.workoutrec.workout.SetValues
import com.workoutrec.workout.Weight
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.launch

/** Navigation routes of the logging screens (contracts/screens.md "Navigation"). */
object LogRoutes {
    const val PICKER = "picker"
    const val WORKOUTS = "workouts"
    const val DAY = "day/{date}"
    fun day(date: LocalDate) = "day/$date"
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
    onOpenWorkouts: () -> Unit = {},
    header: @Composable () -> Unit = {},
) {
    val sets by container.workoutRepository.displaySets.collectAsState(initial = emptyList())
    val status by container.syncStatusStore.status.collectAsState(initial = SyncStatus())
    val zone = status.zone()
    val groups = remember(sets, zone) { DisplayModel.today(sets, LocalDate.now(zone), zone) }
    LaunchedEffect(Unit) { container.syncScheduler.requestSync() }
    val notices by container.workoutRepository.notices.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    TodayScreen(groups, onAddExercise, onOpenExercise, modifier) {
        header()
        SyncNoticeList(notices, onDismiss = { id -> scope.launch { container.workoutRepository.dismissNotice(id) } })
        TextButton(onClick = onOpenWorkouts, modifier = Modifier.testTag(TodayTags.WORKOUTS)) { Text(stringResource(R.string.menu_workouts)) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutsRoute(container: AppContainer, onBack: () -> Unit, onOpenDay: (LocalDate) -> Unit) {
    val sets by container.workoutRepository.displaySets.collectAsState(initial = emptyList())
    val status by container.syncStatusStore.status.collectAsState(initial = SyncStatus())
    val zone = status.zone()
    val days = remember(sets, zone) { DisplayModel.pastDays(sets, LocalDate.now(zone), zone) }
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.past_days_title)) }, navigationIcon = { TextButton(onClick = onBack) { Text("←") } }) }) { padding ->
        PastDaysScreen(days, onOpenDay, Modifier.padding(padding))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayRoute(container: AppContainer, date: LocalDate, onBack: () -> Unit) {
    val sets by container.workoutRepository.displaySets.collectAsState(initial = emptyList())
    val status by container.syncStatusStore.status.collectAsState(initial = SyncStatus())
    val zone = status.zone()
    val groups = remember(sets, date, zone) { DisplayModel.day(sets, date, zone) }
    var editing by remember { mutableStateOf<DisplaySet?>(null) }
    val locale = LocalConfiguration.current.locales[0]
    Scaffold(topBar = { TopAppBar(title = { Text(date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))) }, navigationIcon = { TextButton(onClick = onBack) { Text("←") } }) }) { padding ->
        DayDetailScreen(groups, onEditSet = { editing = it }, modifier = Modifier.padding(padding))
    }
    editing?.let { set -> EditSetRoute(container, set, zone, onDone = { editing = null }) }
}

/** The edit dialog wired to the repository; invalid values keep the dialog open (FR-004). */
@Composable
fun EditSetRoute(container: AppContainer, set: DisplaySet, zone: ZoneId, onDone: () -> Unit) {
    val exercises by container.workoutRepository.exercises.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    EditSetDialog(
        set = set,
        exercises = exercises,
        zone = zone,
        onSave = { exercise, weightText, repsText ->
            val weight = (Weight.parse(weightText) as? Parsed.Ok)?.value
            val reps = (Reps.parse(repsText) as? Parsed.Ok)?.value
            if (weight != null && reps != null) {
                scope.launch {
                    container.workoutRepository.editSet(set.ref, SetValues(exercise, weight, reps))
                    container.syncScheduler.requestSync()
                }
                onDone()
            }
        },
        onDelete = {
            scope.launch {
                container.workoutRepository.deleteSet(set.ref)
                container.syncScheduler.requestSync()
            }
            onDone()
        },
        onDismiss = onDone,
    )
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
        val status by container.syncStatusStore.status.collectAsState(initial = SyncStatus())
        val zone = status.zone()
        val initial = remember(sets, exercise, zone) {
            History.prefill(History.lastTime(sets, exercise, LocalDate.now(zone), zone)).plannedCount
        }
        PlanSetsDialog(initial = initial, onConfirm = { onStart(exercise, it); picked = null }, onDismiss = { picked = null })
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
    var editing by remember { mutableStateOf<DisplaySet?>(null) }
    val syncStatus by container.syncStatusStore.status.collectAsState(initial = SyncStatus())
    editing?.let { set -> EditSetRoute(container, set, syncStatus.zone(), onDone = { editing = null }) }
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
                override fun onEditDone(index: Int) { editing = state.done.getOrNull(index)?.set }
            },
            modifier = Modifier.padding(padding),
        )
    }
}

