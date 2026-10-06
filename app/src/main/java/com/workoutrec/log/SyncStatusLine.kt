package com.workoutrec.log

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.workoutrec.sync.SyncPhase

object SyncTags {
    const val STATUS = "sync_status"
}

/** "N sets not synced", why sync is failing, or "Sign in again" (FR-010). Hidden when all is synced. */
@Composable
fun SyncStatusLine(pendingCount: Int, phase: SyncPhase, onSignIn: () -> Unit, modifier: Modifier = Modifier): Unit = TODO()
