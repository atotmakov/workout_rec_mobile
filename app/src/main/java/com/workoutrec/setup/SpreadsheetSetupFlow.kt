package com.workoutrec.setup

import com.workoutrec.automation.ScriptInstall
import com.workoutrec.data.SelectedAccount
import com.workoutrec.data.SettingsStore
import com.workoutrec.spreadsheet.SpreadsheetSetup
import java.time.Instant

/** US2: find or create the spreadsheet, attach the script, guide the two automation steps. */
class SpreadsheetSetupFlow(
    private val setup: SpreadsheetSetup,
    private val installer: ScriptInstall,
    private val store: SettingsStore,
    private val timeZone: () -> String,
    private val now: () -> Instant,
) : PostAuthStep {
    override suspend fun run(account: SelectedAccount): SetupState = TODO()
    override suspend fun onAction(account: SelectedAccount, current: SetupState, action: SetupAction): SetupState = TODO()
    override suspend fun refresh(account: SelectedAccount): RefreshResult = TODO()
}
