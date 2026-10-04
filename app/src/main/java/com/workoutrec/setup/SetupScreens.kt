package com.workoutrec.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.workoutrec.R
import com.workoutrec.google.ApiError

const val SetupProgressTag = "setup_progress"

enum class GuideStep { ApiSetting, Enable }

/** Progress while setting up; on failure a clear message and Retry (FR-011). */
@Composable
fun SetupProgressScreen(error: ApiError?, onRetry: () -> Unit) {
    CenteredColumn {
        if (error == null) {
            CircularProgressIndicator(modifier = Modifier.testTag(SetupProgressTag))
            Text(stringResource(R.string.setup_working))
        } else {
            Text(
                stringResource(
                    when (error) {
                        ApiError.Offline -> R.string.setup_error_offline
                        is ApiError.ServiceUnavailable -> R.string.setup_error_service
                        is ApiError.AccessDenied, ApiError.TokenExpired -> R.string.setup_error_access
                        else -> R.string.setup_error_other
                    },
                ),
                color = MaterialTheme.colorScheme.error,
            )
            Button(onClick = onRetry) { Text(stringResource(R.string.setup_retry)) }
        }
    }
}

/** FR-008 rewrite question; says the old data is kept as a backup (clarification Q2). */
@Composable
fun RewriteDialog(onAnswer: (Boolean) -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text(stringResource(R.string.rewrite_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.rewrite_question))
                Text(stringResource(R.string.rewrite_backup_note), style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = { onAnswer(true) }) { Text(stringResource(R.string.rewrite_yes)) } },
        dismissButton = { TextButton(onClick = { onAnswer(false) }) { Text(stringResource(R.string.rewrite_no)) } },
    )
}

/**
 * The two one-time browser steps (FR-013); each can be skipped for now (FR-015).
 *
 * Step 2 must run as the browser's only/first account (see [GuideLinks]): with [privateTab] the
 * page opens in a private in-app tab; otherwise the user copies the link into an incognito tab.
 */
@Composable
fun AutomationGuideScreen(
    step: GuideStep,
    onOpen: () -> Unit,
    onContinue: () -> Unit,
    accountEmail: String? = null,
    privateTab: Boolean = true,
    onCopyLink: (() -> Unit)? = null,
) {
    val account = accountEmail.orEmpty()
    val copyMode = step == GuideStep.Enable && !privateTab && onCopyLink != null
    val title = if (step == GuideStep.ApiSetting) R.string.guide_step1_title else R.string.guide_step2_title
    val text = when {
        step == GuideStep.ApiSetting -> stringResource(R.string.guide_step1_text)
        copyMode -> stringResource(R.string.guide_step2_copy_text, account)
        else -> stringResource(R.string.guide_step2_private_text, account)
    }
    CenteredColumn {
        Text(stringResource(title), style = MaterialTheme.typography.headlineSmall)
        Text(text, style = MaterialTheme.typography.bodyMedium)
        if (copyMode) {
            Button(onClick = { onCopyLink?.invoke() }) { Text(stringResource(R.string.guide_copy_link)) }
        } else {
            Button(onClick = onOpen) { Text(stringResource(R.string.guide_open)) }
        }
        TextButton(onClick = onContinue) { Text(stringResource(R.string.guide_continue)) }
    }
}

@Composable
private fun CenteredColumn(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) { content() }
}
