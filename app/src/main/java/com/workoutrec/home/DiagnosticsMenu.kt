package com.workoutrec.home

import androidx.annotation.StringRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import com.workoutrec.R

/** contracts/monitoring.md "UI": test tags of the switch row and the test dialog. */
const val SendDiagnosticsTag = "account_send_diagnostics"
const val DiagnosticsTestDialogTag = "diagnostics_test_dialog"

/** The six hidden test actions (research R10), in dialog order. */
enum class DiagnosticsTestAction(@StringRes val label: Int) {
    CRASH(R.string.diagnostics_test_crash),
    FREEZE(R.string.diagnostics_test_freeze),
    TEST_ERROR(R.string.diagnostics_test_error),
    HEAP_DUMP(R.string.diagnostics_test_heap_dump),
    DISK_CHECK(R.string.diagnostics_test_disk),
    PROFILE_NEXT_START(R.string.diagnostics_test_profile_start),
}

/** What the account menu shows about Send diagnostics. */
data class DiagnosticsMenuState(
    val sendDiagnostics: Boolean = true,
    val appliesAfterRestart: Boolean = false,
    val onSendDiagnosticsChange: (Boolean) -> Unit = {},
    val onTestAction: (DiagnosticsTestAction) -> Unit = {},
)

/** The Send diagnostics row (FR-012); a long-press opens the test dialog (research R10). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SendDiagnosticsItem(state: DiagnosticsMenuState, onTestAction: (DiagnosticsTestAction) -> Unit) {
    var testDialog by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(SendDiagnosticsTag)
            .combinedClickable(
                onClick = { state.onSendDiagnosticsChange(!state.sendDiagnostics) },
                onLongClick = { testDialog = true },
            )
            .semantics {
                role = Role.Switch
                toggleableState = ToggleableState(state.sendDiagnostics)
            }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.account_send_diagnostics))
            if (state.appliesAfterRestart) {
                Text(
                    stringResource(R.string.account_send_diagnostics_restart),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = state.sendDiagnostics, onCheckedChange = null)
    }
    if (testDialog) {
        AlertDialog(
            modifier = Modifier.testTag(DiagnosticsTestDialogTag),
            onDismissRequest = { testDialog = false },
            title = { Text(stringResource(R.string.diagnostics_test_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    DiagnosticsTestAction.entries.forEach { action ->
                        TextButton(onClick = { testDialog = false; onTestAction(action) }) {
                            Text(stringResource(action.label))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { testDialog = false }) { Text(stringResource(R.string.diagnostics_test_close)) }
            },
        )
    }
}
