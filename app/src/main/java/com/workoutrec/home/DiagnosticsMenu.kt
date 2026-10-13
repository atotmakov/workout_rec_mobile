package com.workoutrec.home

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
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

/** The Send diagnostics row; a long-press opens the test dialog. */
@Composable
fun SendDiagnosticsItem(state: DiagnosticsMenuState, onTestAction: (DiagnosticsTestAction) -> Unit) {}
