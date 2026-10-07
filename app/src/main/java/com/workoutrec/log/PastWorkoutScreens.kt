package com.workoutrec.log

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.workoutrec.R
import com.workoutrec.workout.Exercise
import com.workoutrec.workout.Parsed
import com.workoutrec.workout.PastWorkoutError
import com.workoutrec.workout.PastWorkoutTimes
import com.workoutrec.workout.Reps
import com.workoutrec.workout.SetKey
import com.workoutrec.workout.SetValues
import com.workoutrec.workout.Weight
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

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

private val TIME = DateTimeFormatter.ofPattern("HH:mm")

/** Date (not in the future), start time and duration (FR-006a). */
@Composable
fun PastWorkoutDialog(
    initialDate: LocalDate,
    onStart: (date: LocalDate, start: LocalTime, durationMinutes: Int) -> PastWorkoutError?,
    onDismiss: () -> Unit,
) {
    var dateText by rememberSaveable { mutableStateOf(initialDate.toString()) }
    var startText by rememberSaveable { mutableStateOf(DEFAULT_START.format(TIME)) }
    var durationText by rememberSaveable { mutableStateOf(PastWorkoutTimes.DEFAULT_DURATION.toString()) }
    var error by remember { mutableStateOf<PastWorkoutError?>(null) }
    var badFormat by remember { mutableStateOf(false) }
    AlertDialog(
        modifier = Modifier.testTag(PastWorkoutTags.DIALOG),
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.past_workout_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Field(dateText, { dateText = it }, stringResource(R.string.past_workout_date), KeyboardType.Number)
                Field(startText, { startText = it }, stringResource(R.string.past_workout_start_time), KeyboardType.Number)
                Field(durationText, { durationText = it }, stringResource(R.string.past_workout_duration), KeyboardType.Number)
                if (badFormat) Text(stringResource(R.string.past_workout_error_format), color = MaterialTheme.colorScheme.error)
                error?.let {
                    Text(
                        stringResource(if (it == PastWorkoutError.FUTURE_DATE) R.string.past_workout_error_future else R.string.past_workout_error_duration),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                modifier = Modifier.testTag(PastWorkoutTags.START),
                onClick = {
                    val date = try { LocalDate.parse(dateText.trim()) } catch (e: DateTimeParseException) { null }
                    val start = try { LocalTime.parse(startText.trim(), TIME) } catch (e: DateTimeParseException) { null }
                    val duration = durationText.trim().toIntOrNull()
                    badFormat = date == null || start == null
                    error = when {
                        badFormat -> null
                        duration == null -> PastWorkoutError.DURATION
                        else -> onStart(date!!, start!!, duration)
                    }
                },
            ) { Text(stringResource(R.string.past_workout_start)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.past_workout_cancel)) } },
    )
}

/** The draft's sets with their computed times; Save makes them pending (US5). */
@Composable
fun PastWorkoutEditor(
    state: PastWorkoutState,
    exercises: List<Exercise>,
    onAddSet: (SetValues) -> Unit,
    onRemoveSet: (Int) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    zone: ZoneId = ZoneId.systemDefault(),
) {
    val locale = LocalConfiguration.current.locales[0]
    var exercise by rememberSaveable { mutableStateOf(exercises.firstOrNull()?.name.orEmpty()) }
    var weight by rememberSaveable { mutableStateOf("") }
    var reps by rememberSaveable { mutableStateOf("") }
    var pickerOpen by remember { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp).testTag(PastWorkoutTags.EDITOR), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (state.dayHasSets) {
            item {
                Text(
                    stringResource(R.string.past_workout_day_has_sets),
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.testTag(PastWorkoutTags.DAY_HAS_SETS),
                )
            }
        }
        itemsIndexed(state.sets) { i, set ->
            Row(
                Modifier.fillMaxWidth().testTag(PastWorkoutTags.SET).semantics(mergeDescendants = true) {},
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                val time = Instant.ofEpochSecond(set.time).atZone(zone).toLocalTime().format(TIME)
                val key = SetKey(set.time, set.values.exercise, set.values.weight, set.values.reps)
                Text("$time  ${set.values.exercise}  ${setText(key, locale)}")
                TextButton(onClick = { onRemoveSet(i) }) { Text(stringResource(R.string.past_workout_remove)) }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Column {
                    OutlinedButton(onClick = { pickerOpen = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(exercise.ifEmpty { stringResource(R.string.edit_exercise) })
                    }
                    DropdownMenu(expanded = pickerOpen, onDismissRequest = { pickerOpen = false }) {
                        exercises.forEach { option ->
                            DropdownMenuItem(text = { Text(option.name) }, onClick = { exercise = option.name; pickerOpen = false })
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Field(weight, { weight = it }, stringResource(R.string.logging_weight), KeyboardType.Decimal, Modifier.weight(1f))
                    Field(reps, { reps = it }, stringResource(R.string.logging_reps), KeyboardType.Number, Modifier.weight(1f))
                }
                val parsedWeight = (Weight.parse(weight) as? Parsed.Ok)?.value
                val parsedReps = (Reps.parse(reps) as? Parsed.Ok)?.value
                OutlinedButton(
                    enabled = exercise.isNotEmpty() && parsedWeight != null && parsedReps != null,
                    onClick = { onAddSet(SetValues(exercise, parsedWeight!!, parsedReps!!)) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.past_workout_add)) }
                Button(onClick = onSave, enabled = state.sets.isNotEmpty(), modifier = Modifier.fillMaxWidth().testTag(PastWorkoutTags.SAVE)) {
                    Text(stringResource(R.string.past_workout_save))
                }
                TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.past_workout_cancel)) }
            }
        }
    }
}

@Composable
private fun Field(value: String, onChange: (String) -> Unit, label: String, keyboard: KeyboardType, modifier: Modifier = Modifier.fillMaxWidth()) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        modifier = modifier,
    )
}

/** Usual evening start; the user changes it in the dialog. */
private val DEFAULT_START: LocalTime = LocalTime.of(18, 0)
