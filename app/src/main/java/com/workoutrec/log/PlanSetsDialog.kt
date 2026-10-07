package com.workoutrec.log

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.workoutrec.R

/** Test tags of contracts/screens.md "Plan sets dialog". */
object PlanTags {
    const val DIALOG = "plan_sets"
    const val MINUS = "plan_sets_minus"
    const val PLUS = "plan_sets_plus"
    const val VALUE = "plan_sets_value"
    const val OK = "plan_sets_ok"
}

/** How many sets to log, 1–20 (FR-003). */
@Composable
fun PlanSetsDialog(initial: Int, onConfirm: (Int) -> Unit, onDismiss: () -> Unit) {
    var count by rememberSaveable { mutableIntStateOf(initial.coerceIn(1, 20)) }
    AlertDialog(
        modifier = Modifier.testTag(PlanTags.DIALOG),
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.plan_title)) },
        text = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                StepButton(minus = true, label = stringResource(R.string.plan_minus), tag = PlanTags.MINUS) { count = (count - 1).coerceAtLeast(1) }
                Text(count.toString(), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.testTag(PlanTags.VALUE))
                StepButton(minus = false, label = stringResource(R.string.plan_plus), tag = PlanTags.PLUS) { count = (count + 1).coerceAtMost(20) }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(count) }, modifier = Modifier.testTag(PlanTags.OK)) { Text(stringResource(R.string.plan_ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.plan_cancel)) } },
    )
}
