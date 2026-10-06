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
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.workoutrec.R
import com.workoutrec.workout.InvalidReason
import com.workoutrec.workout.SyncState

/** Test tags of contracts/screens.md "Exercise logging". */
object LoggingTags {
    const val SCREEN = "exercise_logging"
    const val TITLE = "logging_title"
    const val ADD_SET = "add_set"
    const val LAST_TIME = "last_time"
    const val RECORD = "record"

    /** True on a weight or reps field whose value is a suggestion from last time. */
    val Suggested = SemanticsPropertyKey<Boolean>("suggested")
    fun row(i: Int) = "set_row_$i"
    fun weight(i: Int) = "set_weight_$i"
    fun reps(i: Int) = "set_reps_$i"
    fun weightMinus(i: Int) = "set_weight_minus_$i"
    fun weightPlus(i: Int) = "set_weight_plus_$i"
    fun repsMinus(i: Int) = "set_reps_minus_$i"
    fun repsPlus(i: Int) = "set_reps_plus_$i"
    fun confirm(i: Int) = "set_confirm_$i"
    fun done(i: Int) = "set_done_$i"
}

interface LoggingActions {
    fun onWeightChange(index: Int, text: String)
    fun onRepsChange(index: Int, text: String)
    fun onStepWeight(index: Int, direction: Int)
    fun onStepReps(index: Int, direction: Int)
    fun onConfirm(index: Int)
    fun onAddSet()
}

/** Done sets and set rows of one exercise (FR-003–FR-005). */
@Composable
fun ExerciseLoggingScreen(
    state: LoggingState,
    actions: LoggingActions,
    modifier: Modifier = Modifier,
    header: @Composable () -> Unit = {},
) {
    val locale = LocalConfiguration.current.locales[0]
    LazyColumn(
        modifier.fillMaxSize().padding(16.dp).testTag(LoggingTags.SCREEN),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(state.exercise, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.testTag(LoggingTags.TITLE))
            header()
        }
        itemsIndexed(state.done) { i, done ->
            Row(Modifier.fillMaxWidth().testTag(LoggingTags.done(i)).semantics(mergeDescendants = true) {}, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(setText(done.key, locale), style = MaterialTheme.typography.bodyLarge)
                Text(
                    stringResource(if (done.syncState == SyncState.SYNCED) R.string.set_synced else R.string.set_not_synced),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        itemsIndexed(state.rows) { i, row -> SetRow(i, row, actions) }
        item {
            OutlinedButton(onClick = actions::onAddSet, modifier = Modifier.fillMaxWidth().testTag(LoggingTags.ADD_SET)) {
                Text(stringResource(R.string.logging_add_set))
            }
        }
    }
}

@Composable
private fun SetRow(i: Int, row: SetRowState, actions: LoggingActions) {
    Column(Modifier.fillMaxWidth().testTag(LoggingTags.row(i)), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        // Weight and reps side by side, each "− value +"; fits a 360 dp phone.
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                StepButton(minus = true, label = stringResource(R.string.weight_minus), tag = LoggingTags.weightMinus(i)) { actions.onStepWeight(i, -1) }
                NumberField(row.weightText, stringResource(R.string.logging_weight), KeyboardType.Decimal, LoggingTags.weight(i), Modifier.weight(1f)) {
                    actions.onWeightChange(i, it)
                }
                StepButton(minus = false, label = stringResource(R.string.weight_plus), tag = LoggingTags.weightPlus(i)) { actions.onStepWeight(i, +1) }
            }
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                StepButton(minus = true, label = stringResource(R.string.reps_minus), tag = LoggingTags.repsMinus(i)) { actions.onStepReps(i, -1) }
                NumberField(row.repsText, stringResource(R.string.logging_reps), KeyboardType.Number, LoggingTags.reps(i), Modifier.weight(1f)) {
                    actions.onRepsChange(i, it)
                }
                StepButton(minus = false, label = stringResource(R.string.reps_plus), tag = LoggingTags.repsPlus(i)) { actions.onStepReps(i, +1) }
            }
        }
        listOfNotNull(row.weightError?.let { weightError(it) }, row.repsError?.let { repsError(it) }).forEach {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        Button(onClick = { actions.onConfirm(i) }, enabled = row.canConfirm, modifier = Modifier.fillMaxWidth().testTag(LoggingTags.confirm(i))) {
            Text(stringResource(R.string.logging_confirm))
        }
    }
}

@Composable
private fun NumberField(
    value: String,
    label: String,
    keyboard: KeyboardType,
    tag: String,
    modifier: Modifier,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        modifier = modifier.testTag(tag),
    )
}

/** − / + button used for sets, weight and reps (FR-003, FR-004). */
@Composable
fun StepButton(minus: Boolean, label: String, tag: String, onClick: () -> Unit) {
    FilledTonalIconButton(onClick = onClick, modifier = Modifier.testTag(tag).semantics { contentDescription = label }) {
        Text(if (minus) "−" else "+", style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun weightError(reason: InvalidReason): String = stringResource(
    when (reason) {
        InvalidReason.EMPTY -> R.string.error_empty
        InvalidReason.NEGATIVE -> R.string.error_weight_negative
        InvalidReason.TOO_MANY_DECIMALS -> R.string.error_too_many_decimals
        InvalidReason.TOO_LARGE -> R.string.error_too_large
        else -> R.string.error_not_a_number
    },
)

@Composable
private fun repsError(reason: InvalidReason): String = stringResource(
    when (reason) {
        InvalidReason.EMPTY -> R.string.error_empty
        InvalidReason.NOT_WHOLE -> R.string.error_reps_not_whole
        InvalidReason.TOO_SMALL -> R.string.error_reps_too_small
        InvalidReason.TOO_LARGE -> R.string.error_too_large
        else -> R.string.error_not_a_number
    },
)
