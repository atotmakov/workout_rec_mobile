package com.workoutrec.exercises

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.workoutrec.R
import com.workoutrec.log.LastTimeAndRecord
import com.workoutrec.workout.LastTime
import com.workoutrec.workout.Record
import com.workoutrec.workout.ShownExercise

/** Test tags of feature 004 contracts/screens.md "Exercises". */
object ExercisesTags {
    const val SCREEN = "exercises_screen"
    const val SEARCH = "exercises_search"
    const val ADD = "exercises_add"
    const val REFRESH = "exercises_refresh"
    const val EMPTY = "exercises_empty"
    const val DETAIL = "exercise_detail"
    const val LOG_TODAY = "exercise_log_today"
    const val RENAME = "exercise_rename"
    const val DELETE = "exercise_delete"
    const val DELETE_CONFIRM = "exercise_delete_confirm"
    const val IN_USE_NOTE = "exercise_in_use_note"
    const val DIALOG = "exercise_dialog"
    const val GROUP = "exercise_group"
    const val NAME = "exercise_name"
    const val SAVE = "exercise_save"
    fun item(name: String) = "exercises_item_$name"
    fun waiting(name: String) = "exercises_waiting_$name"
}

/** The Exercises tab's first screen: the drills list by muscle group, with search (FR-007). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExercisesScreen(
    exercises: List<ShownExercise>,
    onOpen: (String) -> Unit,
    onAdd: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    refreshing: Boolean = false,
    /** The sync icon and the account avatar. */
    actions: @Composable () -> Unit = {},
) {
    var query by rememberSaveable { mutableStateOf("") }
    Scaffold(
        modifier = modifier.testTag(ExercisesTags.SCREEN),
        topBar = { TopAppBar(title = { Text(stringResource(R.string.tab_exercises)) }, actions = { actions() }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAdd,
                icon = { Icon(painterResource(R.drawable.ic_add), contentDescription = null) },
                text = { Text(stringResource(R.string.exercise_add)) },
                modifier = Modifier.testTag(ExercisesTags.ADD),
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = onRefresh,
            modifier = Modifier.padding(padding).fillMaxSize().testTag(ExercisesTags.REFRESH),
        ) {
            if (exercises.isEmpty()) {
                // Scrollable, so pulling down also works on an empty list.
                LazyColumn(Modifier.fillMaxSize().padding(16.dp)) {
                    item { Text(stringResource(R.string.exercises_empty), modifier = Modifier.testTag(ExercisesTags.EMPTY)) }
                }
                return@PullToRefreshBox
            }
            val other = stringResource(R.string.picker_other_group)
            val filtered = exercises.filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }
            val groups = filtered.groupBy { it.muscleGroup.ifBlank { other } }
            LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                item {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text(stringResource(R.string.picker_search)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp).testTag(ExercisesTags.SEARCH),
                    )
                }
                groups.forEach { (group, items) ->
                    item(key = "group:$group") {
                        Text(group, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
                    }
                    items(items, key = { "item:${it.name}" }) { exercise -> ExerciseRow(exercise, onOpen) }
                }
                // Room for the add button over the last rows.
                item { Text("", modifier = Modifier.padding(bottom = 72.dp)) }
            }
        }
    }
}

@Composable
private fun ExerciseRow(exercise: ShownExercise, onOpen: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onOpen(exercise.name) }.padding(vertical = 12.dp).testTag(ExercisesTags.item(exercise.name)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(exercise.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        if (exercise.pending) {
            val waiting = stringResource(R.string.exercise_waiting)
            Icon(
                painterResource(R.drawable.ic_sync_waiting),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp).semantics { contentDescription = waiting }.testTag(ExercisesTags.waiting(exercise.name)),
            )
        }
    }
}

/** One exercise: last time and record, Log today, Rename, and Delete when it has no sets (FR-008b). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseDetailScreen(
    exercise: ShownExercise,
    lastTime: LastTime?,
    record: Record?,
    onLogToday: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable () -> Unit = {},
) {
    val locale = LocalConfiguration.current.locales[0]
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        modifier = modifier.testTag(ExercisesTags.DETAIL),
        topBar = {
            TopAppBar(
                title = { Text(exercise.name) },
                navigationIcon = { TextButton(onClick = onBack) { Text("←") } },
                actions = { actions() },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            exercise.muscleGroup.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
            LastTimeAndRecord(lastTime, record, locale)
            Button(onClick = onLogToday, modifier = Modifier.fillMaxWidth().testTag(ExercisesTags.LOG_TODAY)) {
                Text(stringResource(R.string.exercise_log_today))
            }
            OutlinedButton(onClick = onRename, modifier = Modifier.fillMaxWidth().testTag(ExercisesTags.RENAME)) {
                Text(stringResource(R.string.exercise_rename))
            }
            if (exercise.deletable) {
                TextButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth().testTag(ExercisesTags.DELETE)) {
                    Text(stringResource(R.string.edit_delete), color = MaterialTheme.colorScheme.error)
                }
            } else {
                Text(
                    stringResource(R.string.exercise_in_use),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag(ExercisesTags.IN_USE_NOTE),
                )
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.exercise_delete_question, exercise.name)) },
            text = { Text(stringResource(R.string.exercise_delete_text)) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete() }, modifier = Modifier.testTag(ExercisesTags.DELETE_CONFIRM)) {
                    Text(stringResource(R.string.edit_delete))
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.edit_cancel)) } },
        )
    }
}

/** Add or rename: muscle group (existing ones offered) and name; Save only when [check] passes (FR-008, FR-008a). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseDialog(
    title: String,
    initialName: String,
    initialGroup: String,
    groups: List<String>,
    check: (name: String, group: String) -> ExerciseCheck,
    onSave: (name: String, group: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    var group by rememberSaveable { mutableStateOf(initialGroup) }
    var nameTouched by rememberSaveable { mutableStateOf(false) }
    var groupsOpen by rememberSaveable { mutableStateOf(false) }
    val result = check(name, group)
    val problem = (result as? ExerciseCheck.Invalid)?.problem?.takeIf { it != ExerciseProblem.NAME_EMPTY || nameTouched }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag(ExercisesTags.DIALOG),
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ExposedDropdownMenuBox(expanded = groupsOpen, onExpandedChange = { groupsOpen = it }) {
                    OutlinedTextField(
                        value = group,
                        onValueChange = { group = it },
                        label = { Text(stringResource(R.string.exercise_group_label)) },
                        singleLine = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = groupsOpen) },
                        isError = problem == ExerciseProblem.GROUP_TOO_LONG,
                        supportingText = if (problem == ExerciseProblem.GROUP_TOO_LONG) {
                            { Text(stringResource(R.string.exercise_group_too_long)) }
                        } else {
                            null
                        },
                        modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryEditable).testTag(ExercisesTags.GROUP),
                    )
                    val offered = groups.filter { group.isBlank() || it.contains(group.trim(), ignoreCase = true) }
                    if (offered.isNotEmpty()) {
                        ExposedDropdownMenu(expanded = groupsOpen, onDismissRequest = { groupsOpen = false }) {
                            offered.forEach { option ->
                                DropdownMenuItem(text = { Text(option) }, onClick = { group = option; groupsOpen = false })
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; nameTouched = true },
                    label = { Text(stringResource(R.string.exercise_name_label)) },
                    singleLine = true,
                    isError = problem != null && problem != ExerciseProblem.GROUP_TOO_LONG,
                    supportingText = nameError(problem)?.let { id -> { Text(stringResource(id)) } },
                    modifier = Modifier.fillMaxWidth().testTag(ExercisesTags.NAME),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { (result as? ExerciseCheck.Ok)?.let { onSave(it.name, it.muscleGroup) } },
                enabled = result is ExerciseCheck.Ok,
                modifier = Modifier.testTag(ExercisesTags.SAVE),
            ) { Text(stringResource(R.string.edit_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.edit_cancel)) } },
    )
}

private fun nameError(problem: ExerciseProblem?): Int? = when (problem) {
    ExerciseProblem.NAME_EMPTY -> R.string.exercise_name_empty
    ExerciseProblem.NAME_TOO_LONG -> R.string.exercise_name_too_long
    ExerciseProblem.DUPLICATE -> R.string.exercise_exists
    ExerciseProblem.GROUP_TOO_LONG, null -> null
}
