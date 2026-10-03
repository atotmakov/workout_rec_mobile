package com.workoutrec.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.workoutrec.R
import com.workoutrec.automation.AutomationStatus

/** Visible reminder naming the missing automation step (FR-015). Nothing is shown when On. */
@Composable
fun AutomationReminder(status: AutomationStatus, onFix: () -> Unit, modifier: Modifier = Modifier) {
    val message = when (status) {
        AutomationStatus.ScriptMissing -> R.string.reminder_script_missing
        AutomationStatus.NotEnabled -> R.string.reminder_not_enabled
        AutomationStatus.Stopped -> R.string.reminder_stopped
        AutomationStatus.On -> return
    }
    Card(
        modifier = modifier.fillMaxWidth().padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                stringResource(message),
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onFix) { Text(stringResource(R.string.reminder_fix)) }
        }
    }
}
