package com.workoutrec.automation

import androidx.annotation.StringRes
import com.workoutrec.R

/** The one-word status for the account menu item (feature 007 contracts/screens.md "Automation entry"). */
@StringRes
fun automationStatusText(status: AutomationStatus?): Int = when (status) {
    AutomationStatus.On -> R.string.automation_status_on
    AutomationStatus.Stopped -> R.string.automation_status_stopped
    AutomationStatus.NotEnabled, AutomationStatus.ScriptMissing, null -> R.string.automation_status_off
}
