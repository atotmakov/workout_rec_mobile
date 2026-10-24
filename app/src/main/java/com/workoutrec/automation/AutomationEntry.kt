package com.workoutrec.automation

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.workoutrec.R

const val AutomationEntryTag = "automation_entry"

/**
 * Feature 007 contracts/screens.md "Automation entry": optional spreadsheet automation, its status
 * and what it adds. [status] null = not known yet (shown as off). Tapping starts the two steps.
 */
@Composable
fun AutomationEntry(status: AutomationStatus?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    ListItem(
        headlineContent = { Text(stringResource(R.string.automation_entry_title)) },
        supportingContent = { Text(stringResource(entryText(status))) },
        modifier = modifier.clickable(onClick = onClick).testTag(AutomationEntryTag),
    )
}

@StringRes
private fun entryText(status: AutomationStatus?): Int = when (status) {
    AutomationStatus.On -> R.string.automation_entry_on
    AutomationStatus.Stopped -> R.string.automation_entry_stopped
    AutomationStatus.NotEnabled, AutomationStatus.ScriptMissing, null -> R.string.automation_entry_off
}

/** The one-word status for the account menu item. */
@StringRes
fun automationStatusText(status: AutomationStatus?): Int = when (status) {
    AutomationStatus.On -> R.string.automation_status_on
    AutomationStatus.Stopped -> R.string.automation_status_stopped
    AutomationStatus.NotEnabled, AutomationStatus.ScriptMissing, null -> R.string.automation_status_off
}
