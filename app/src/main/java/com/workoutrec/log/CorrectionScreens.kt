package com.workoutrec.log

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.workoutrec.R
import com.workoutrec.sync.NoticeKind
import com.workoutrec.sync.SyncNotice
import com.workoutrec.workout.DaySummary
import com.workoutrec.workout.DisplaySet
import com.workoutrec.workout.Exercise
import com.workoutrec.workout.ExerciseGroup
import com.workoutrec.workout.SyncState
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Test tags of contracts/screens.md "Past days", "Day detail", "Edit set dialog" and notices. */
object CorrectionTags {
    const val PAST_DAYS = "past_days"
    const val DAY_DETAIL = "day_detail"
    const val EDIT_SET = "edit_set"
    const val EDIT_EXERCISE = "edit_exercise"
    const val EDIT_WEIGHT = "edit_weight"
    const val EDIT_REPS = "edit_reps"
    const val EDIT_SAVE = "edit_save"
    const val EDIT_DELETE = "edit_delete"
    const val DELETE_CONFIRM = "delete_set_confirm"
    fun pastDay(date: LocalDate) = "past_day_$date"
    fun daySet(group: Int, set: Int) = "day_set_${group}_$set"
    fun notice(id: Long) = "sync_notice_$id"
    fun noticeDismiss(id: Long) = "sync_notice_dismiss_$id"
}

/** Workout days before today, newest first (FR-013). */
@Composable
fun PastDaysScreen(days: List<DaySummary>, onOpenDay: (LocalDate) -> Unit, modifier: Modifier = Modifier) {
    val locale = LocalConfiguration.current.locales[0]
    val format = remember(locale) { DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale) }
    if (days.isEmpty()) {
        Text(stringResource(R.string.past_days_empty), modifier = modifier.padding(16.dp).testTag(CorrectionTags.PAST_DAYS))
        return
    }
    LazyColumn(modifier.fillMaxSize().padding(16.dp).testTag(CorrectionTags.PAST_DAYS), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(days, key = { it.date.toString() }) { day ->
            Card(
                Modifier.fillMaxWidth()
                    .testTag(CorrectionTags.pastDay(day.date))
                    .semantics(mergeDescendants = true) {}
                    .clickable { onOpenDay(day.date) },
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text(day.date.format(format), style = MaterialTheme.typography.titleMedium)
                    Text(pluralStringResource(R.plurals.past_day_summary, day.exerciseCount, day.exerciseCount, day.setCount))
                }
            }
        }
    }
}

/** One day's exercises and sets; tap a set to edit it (FR-013). */
@Composable
fun DayDetailScreen(groups: List<ExerciseGroup>, onEditSet: (DisplaySet) -> Unit, modifier: Modifier = Modifier) {
    val locale = LocalConfiguration.current.locales[0]
    LazyColumn(modifier.fillMaxSize().padding(16.dp).testTag(CorrectionTags.DAY_DETAIL), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        itemsIndexed(groups) { g, group ->
            Text(group.exercise, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
            group.sets.forEachIndexed { s, set ->
                Row(
                    Modifier.fillMaxWidth()
                        .testTag(CorrectionTags.daySet(g, s))
                        .semantics(mergeDescendants = true) {}
                        .clickable { onEditSet(set) }
                        .padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(setText(set.key, locale))
                    if (set.syncState == SyncState.NOT_SYNCED) {
                        Text(stringResource(R.string.set_not_synced), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

/** Change exercise, weight and reps, or delete; the date-time is read-only (FR-006, FR-013). */
@Composable
fun EditSetDialog(
    set: DisplaySet,
    exercises: List<Exercise>,
    onSave: (exercise: String, weight: String, reps: String) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
    zone: ZoneId = ZoneId.systemDefault(),
) {
    val locale = LocalConfiguration.current.locales[0]
    var exercise by rememberSaveable { mutableStateOf(set.key.exercise) }
    var weight by rememberSaveable { mutableStateOf(set.key.weight.format(locale)) }
    var reps by rememberSaveable { mutableStateOf(set.key.reps.value.toString()) }
    var pickerOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val time = Instant.ofEpochSecond(set.key.time).atZone(zone)
        .format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withLocale(locale))
    AlertDialog(
        modifier = Modifier.testTag(CorrectionTags.EDIT_SET),
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.edit_set_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.edit_set_time, time), style = MaterialTheme.typography.bodySmall)
                Column {
                    OutlinedButton(onClick = { pickerOpen = true }, modifier = Modifier.fillMaxWidth().testTag(CorrectionTags.EDIT_EXERCISE)) {
                        Text(exercise)
                    }
                    DropdownMenu(expanded = pickerOpen, onDismissRequest = { pickerOpen = false }) {
                        exercises.forEach { option ->
                            DropdownMenuItem(text = { Text(option.name) }, onClick = { exercise = option.name; pickerOpen = false })
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = weight,
                        onValueChange = { weight = it },
                        label = { Text(stringResource(R.string.logging_weight)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f).testTag(CorrectionTags.EDIT_WEIGHT),
                    )
                    OutlinedTextField(
                        value = reps,
                        onValueChange = { reps = it },
                        label = { Text(stringResource(R.string.logging_reps)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f).testTag(CorrectionTags.EDIT_REPS),
                    )
                }
                TextButton(onClick = { confirmDelete = true }, modifier = Modifier.testTag(CorrectionTags.EDIT_DELETE)) {
                    Text(stringResource(R.string.edit_delete), color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(exercise, weight, reps) }, modifier = Modifier.testTag(CorrectionTags.EDIT_SAVE)) {
                Text(stringResource(R.string.edit_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.edit_cancel)) } },
    )
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            text = { Text(stringResource(R.string.delete_set_question)) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete() }, modifier = Modifier.testTag(CorrectionTags.DELETE_CONFIRM)) {
                    Text(stringResource(R.string.delete_set_confirm))
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.edit_cancel)) } },
        )
    }
}

/** Conflict and workout-row notices with a dismiss button (FR-015, spec edge cases). */
@Composable
fun SyncNoticeList(notices: List<Pair<Long, SyncNotice>>, onDismiss: (Long) -> Unit, modifier: Modifier = Modifier) {
    val locale = LocalConfiguration.current.locales[0]
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        notices.forEach { (id, notice) ->
            Card(Modifier.fillMaxWidth().testTag(CorrectionTags.notice(id)).semantics(mergeDescendants = true) {}) {
                Column(Modifier.padding(12.dp)) {
                    val set = "${notice.key.exercise} ${setText(notice.key, locale)}"
                    Text(
                        stringResource(
                            if (notice.kind == NoticeKind.CONFLICT_DROPPED) R.string.notice_conflict_dropped else R.string.notice_workout_row,
                            set,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    TextButton(onClick = { onDismiss(id) }, modifier = Modifier.testTag(CorrectionTags.noticeDismiss(id))) {
                        Text(stringResource(R.string.notice_dismiss))
                    }
                }
            }
        }
    }
}
