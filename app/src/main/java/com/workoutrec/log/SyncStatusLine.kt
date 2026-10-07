package com.workoutrec.log

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import com.workoutrec.R
import com.workoutrec.sync.FailReason
import com.workoutrec.sync.SyncPhase

object SyncTags {
    const val STATUS = "sync_status"
}

/** "N sets not synced", why sync is failing, or "Sign in again" (FR-010). Hidden when all is synced. */
@Composable
fun SyncStatusLine(pendingCount: Int, phase: SyncPhase, onSignIn: () -> Unit, modifier: Modifier = Modifier) {
    val problem = when (phase) {
        is SyncPhase.Failing -> when (phase.reason) {
            FailReason.NETWORK -> R.string.sync_failing_network
            FailReason.SPREADSHEET -> R.string.sync_failing_spreadsheet
            FailReason.STRUCTURE -> R.string.sync_failing_structure
            FailReason.OTHER -> R.string.sync_failing_other
        }
        else -> null
    }
    val needsSignIn = phase == SyncPhase.NeedsSignIn
    if (pendingCount == 0 && problem == null && !needsSignIn) return
    Column(modifier.fillMaxWidth().testTag(SyncTags.STATUS).semantics(mergeDescendants = true) {}) {
        if (pendingCount > 0) {
            Text(
                pluralStringResource(R.plurals.sync_pending, pendingCount, pendingCount),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        problem?.let { Text(stringResource(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
        if (needsSignIn) {
            TextButton(onClick = onSignIn) { Text(stringResource(R.string.sync_sign_in)) }
        }
    }
}
