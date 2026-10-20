package com.workoutrec.log

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.workoutrec.R
import com.workoutrec.sync.FailReason
import com.workoutrec.sync.SyncPhase
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

object SyncIconTags {
    const val BUTTON = "sync_icon"
    const val DETAILS = "sync_icon_details"
}

/** The three statuses of the top-bar sync icon (redesign brief 1a). */
enum class SyncIndicator { SYNCED, WAITING, SYNCING }

fun syncIndicator(pendingCount: Int, phase: SyncPhase): SyncIndicator = when {
    phase == SyncPhase.Running -> SyncIndicator.SYNCING
    pendingCount > 0 || phase == SyncPhase.NeedsSignIn || phase is SyncPhase.Failing -> SyncIndicator.WAITING
    else -> SyncIndicator.SYNCED
}

/** Why sync is stuck, or null while it works (FR-010). */
private fun SyncPhase.problem(): Int? = when (this) {
    is SyncPhase.Failing -> when (reason) {
        FailReason.NETWORK -> R.string.sync_failing_network
        FailReason.SPREADSHEET -> R.string.sync_failing_spreadsheet
        FailReason.STRUCTURE -> R.string.sync_failing_structure
        FailReason.OTHER -> R.string.sync_failing_other
    }
    SyncPhase.NeedsSignIn -> R.string.sync_sign_in
    else -> null
}

/**
 * Top-bar icon showing whether every set reached the spreadsheet; a tap opens the details with
 * Sync now and, when needed, Sign in. A stuck sync shows the waiting icon in the error colour.
 */
@Composable
fun SyncStatusButton(
    pendingCount: Int,
    phase: SyncPhase,
    lastSuccessAt: Long?,
    zone: ZoneId,
    onSyncNow: () -> Unit,
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val indicator = syncIndicator(pendingCount, phase)
    val problem = phase.problem()
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        IconButton(onClick = { open = true }, modifier = Modifier.testTag(SyncIconTags.BUTTON)) {
            val tint = when {
                problem != null -> MaterialTheme.colorScheme.error
                indicator == SyncIndicator.SYNCED -> MaterialTheme.colorScheme.onSurfaceVariant
                else -> MaterialTheme.colorScheme.primary
            }
            val description = when (indicator) {
                SyncIndicator.SYNCED -> stringResource(R.string.sync_icon_synced)
                SyncIndicator.SYNCING -> stringResource(R.string.sync_icon_running)
                SyncIndicator.WAITING -> pluralStringResource(R.plurals.sync_pending, pendingCount, pendingCount)
            }
            when (indicator) {
                SyncIndicator.SYNCED -> Icon(painterResource(R.drawable.ic_sync_done), description, tint = tint)
                SyncIndicator.SYNCING -> {
                    val turn by rememberInfiniteTransition(label = "sync").animateFloat(
                        initialValue = 0f,
                        targetValue = -360f,
                        animationSpec = infiniteRepeatable(tween(1_000, easing = LinearEasing), RepeatMode.Restart),
                        label = "sync turn",
                    )
                    Icon(painterResource(R.drawable.ic_sync_running), description, tint = tint, modifier = Modifier.rotate(turn))
                }
                SyncIndicator.WAITING -> BadgedBox(
                    badge = {
                        if (pendingCount > 0) {
                            Badge(
                                containerColor = if (problem != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                contentColor = if (problem != null) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimary,
                            ) { Text(if (pendingCount > 99) "99+" else pendingCount.toString()) }
                        }
                    },
                ) { Icon(painterResource(R.drawable.ic_sync_waiting), description, tint = tint) }
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            SyncDetails(
                indicator = indicator,
                pendingCount = pendingCount,
                problem = problem,
                needsSignIn = phase == SyncPhase.NeedsSignIn,
                lastSuccessAt = lastSuccessAt,
                zone = zone,
                onSyncNow = { open = false; onSyncNow() },
                onSignIn = { open = false; onSignIn() },
            )
        }
    }
}

@Composable
private fun SyncDetails(
    indicator: SyncIndicator,
    pendingCount: Int,
    problem: Int?,
    needsSignIn: Boolean,
    lastSuccessAt: Long?,
    zone: ZoneId,
    onSyncNow: () -> Unit,
    onSignIn: () -> Unit,
) {
    Column(Modifier.widthIn(min = 240.dp, max = 300.dp).padding(horizontal = 16.dp, vertical = 8.dp).testTag(SyncIconTags.DETAILS)) {
        Text(
            when {
                indicator == SyncIndicator.SYNCING -> stringResource(R.string.sync_icon_running)
                pendingCount > 0 -> pluralStringResource(R.plurals.sync_pending, pendingCount, pendingCount)
                else -> stringResource(R.string.sync_icon_synced)
            },
            style = MaterialTheme.typography.titleSmall,
        )
        problem?.let {
            Text(stringResource(it), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 4.dp))
        }
        Text(
            lastSuccessAt?.let { stringResource(R.string.sync_last_success, formatSyncTime(it, zone)) } ?: stringResource(R.string.sync_never),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        Row(Modifier.padding(top = 8.dp).align(Alignment.End)) {
            if (needsSignIn) {
                FilledTonalButton(onClick = onSignIn) { Text(stringResource(R.string.sync_icon_sign_in)) }
            } else {
                TextButton(onClick = onSyncNow, enabled = indicator != SyncIndicator.SYNCING) { Text(stringResource(R.string.sync_now)) }
            }
        }
    }
}

/** "09:42" today, otherwise "Oct 14, 2026, 18:30". */
private fun formatSyncTime(at: Long, zone: ZoneId): String {
    val time = Instant.ofEpochMilli(at).atZone(zone)
    return if (time.toLocalDate() == LocalDate.now(zone)) {
        time.format(DateTimeFormatter.ofPattern("HH:mm"))
    } else {
        time.format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT))
    }
}
