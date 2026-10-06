package com.workoutrec.home

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.workoutrec.R

/** FR-012: signing out requires confirmation; FR-014: it says how many unsynced sets are lost. */
@Composable
fun SignOutDialog(onConfirm: () -> Unit, onCancel: () -> Unit, unsyncedCount: Int = 0) {
    AlertDialog(
        onDismissRequest = onCancel,
        text = {
            Column {
                Text(stringResource(R.string.sign_out_question))
                UnsyncedWarning(unsyncedCount)
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.sign_out_confirm)) } },
        dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(R.string.sign_out_cancel)) } },
    )
}

/** Shown only when sets are not synced yet (FR-014, analysis fix U3). */
@Composable
fun SwitchAccountDialog(unsyncedCount: Int, onConfirm: () -> Unit, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.switch_question)) },
        text = { UnsyncedWarning(unsyncedCount) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.switch_confirm)) } },
        dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(R.string.switch_cancel)) } },
    )
}

@Composable
private fun UnsyncedWarning(count: Int) {
    if (count > 0) {
        Text(
            pluralStringResource(R.plurals.unsynced_warning, count, count),
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.testTag(UnsyncedWarningTag),
        )
    }
}
