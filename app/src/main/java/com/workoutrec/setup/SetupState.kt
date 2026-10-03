package com.workoutrec.setup

import com.workoutrec.auth.ConsentRequest
import com.workoutrec.automation.AutomationStatus
import com.workoutrec.data.SelectedAccount
import com.workoutrec.google.ApiError

/** Messages shown on the account chooser. */
enum class SetupMessage { AccountRequired, AccessRevoked }

/** The network step that failed, so Retry can repeat it. */
enum class SetupStep { Finding, Creating, Checking, Renaming, AttachingScript }

/** Setup flow states (data-model.md "Setup flow"). */
sealed interface SetupState {
    data object Starting : SetupState
    data class SignedOut(val message: SetupMessage?) : SetupState
    data object Authorizing : SetupState

    /** Spreadsheet or script work in progress. */
    data object Working : SetupState
    data class NeedsConsent(val consent: ConsentRequest) : SetupState
    data class AskRewrite(val spreadsheetId: String) : SetupState

    /** Step 1: the user's "Google Apps Script API" setting is off. */
    data object NeedsApiSetting : SetupState

    /** Step 2: the "Enable automation" page must be approved. */
    data class NeedsEnable(val enableUrl: String) : SetupState
    data class Error(val step: SetupStep, val error: ApiError) : SetupState
    data object Ready : SetupState
}

sealed interface SetupAction {
    data class AnswerRewrite(val yes: Boolean) : SetupAction

    /** Repeat the failed step. */
    data object Retry : SetupAction

    /** The user came back from the browser; check again. */
    data object Recheck : SetupAction
    data object ContinueForNow : SetupAction

    /** Tapped the reminder: attach the script or open the Enable page, as needed. */
    data object FixAutomation : SetupAction
}

/** Shown on the main screen while automation is not on (FR-015). */
data class Reminder(val status: AutomationStatus, val enableUrl: String?)

sealed interface RefreshResult {
    data object NoChange : RefreshResult
    data class Status(val reminder: Reminder?) : RefreshResult
    data object SpreadsheetGone : RefreshResult
}

/** What happens after the account is authorized (US2 plugs the spreadsheet setup in here). */
fun interface PostAuthStep {
    suspend fun run(account: SelectedAccount): SetupState

    suspend fun onAction(account: SelectedAccount, current: SetupState, action: SetupAction): SetupState = current

    suspend fun refresh(account: SelectedAccount): RefreshResult = RefreshResult.NoChange
}
