package com.workoutrec.home

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.workoutrec.R

/** FR-012: signing out requires confirmation. */
@Composable
fun SignOutDialog(onConfirm: () -> Unit, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        text = { Text(stringResource(R.string.sign_out_question)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.sign_out_confirm)) } },
        dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(R.string.sign_out_cancel)) } },
    )
}
