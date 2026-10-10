package com.workoutrec.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.workoutrec.R

/** First-launch account chooser (US1, FR-001); explains access before asking (FR-002). */
@Composable
fun ChooseAccountScreen(message: SetupMessage?, onChooseAccount: () -> Unit, onShareLog: () -> Unit = {}) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.setup_title), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.setup_access_explanation), style = MaterialTheme.typography.bodyMedium)
        when (message) {
            SetupMessage.AccountRequired -> ErrorText(stringResource(R.string.setup_account_required))
            SetupMessage.AccessRevoked -> ErrorText(stringResource(R.string.setup_access_revoked))
            null -> Unit
        }
        Button(onClick = onChooseAccount) {
            Text(stringResource(if (message == null) R.string.setup_choose_account else R.string.setup_choose_again))
        }
    }
}

@Composable
private fun ErrorText(text: String) {
    Text(text, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
}
