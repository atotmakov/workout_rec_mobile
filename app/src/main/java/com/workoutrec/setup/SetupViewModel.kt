package com.workoutrec.setup

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.workoutrec.StartDestination
import com.workoutrec.StartDestinationResolver
import com.workoutrec.auth.AccountPicker
import com.workoutrec.auth.AccountRepository
import com.workoutrec.auth.AuthOutcome
import com.workoutrec.auth.AuthorizationFailed
import com.workoutrec.auth.PickFailed
import com.workoutrec.auth.PickResult
import com.workoutrec.automation.AutomationStatus
import com.workoutrec.data.SelectedAccount
import com.workoutrec.monitoring.Monitoring
import com.workoutrec.monitoring.NoMonitoring
import com.workoutrec.monitoring.NonFatalIssue
import com.workoutrec.monitoring.failure
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Drives the setup flow (data-model.md "Setup flow"). */
class SetupViewModel(
    private val accounts: AccountRepository,
    private val postAuth: PostAuthStep,
    /** Feature 005: handled failures as non-fatal events (contracts/issues.md). */
    private val monitoring: Monitoring = NoMonitoring,
) : ViewModel() {

    private val _state = MutableStateFlow<SetupState>(SetupState.Starting)
    val state: StateFlow<SetupState> = _state

    /** The account waiting for the consent screen to return. */
    private var pendingAccount: SelectedAccount? = null

    fun start() {
        viewModelScope.launch {
            val account = accounts.currentAccount.first()
            if (account == null) {
                _state.value = SetupState.SignedOut(message = null)
                return@launch
            }
            _state.value = SetupState.Authorizing
            val outcome = accounts.authorizeOnLaunch()
            if (outcome != null) report(outcome)
            _state.value = when (val destination = StartDestinationResolver.resolve(account, outcome)) {
                StartDestination.Home ->
                    if (outcome is AuthOutcome.Granted) postAuth.run(account) else SetupState.Ready
                is StartDestination.Setup ->
                    if (outcome is AuthOutcome.NeedsUserConsent) {
                        pendingAccount = account
                        SetupState.NeedsConsent(outcome.consent)
                    } else {
                        SetupState.SignedOut(destination.message)
                    }
            }
        }
    }

    fun chooseAccount(picker: AccountPicker) {
        viewModelScope.launch {
            _state.value = SetupState.Authorizing
            when (val picked = accounts.chooseAccount(picker)) {
                is PickResult.Picked -> handleAuthorization(picked.account, accounts.authorize(picked.account))
                PickResult.Cancelled -> _state.value = SetupState.SignedOut(SetupMessage.AccountRequired)
                is PickResult.Failed -> {
                    report(picked)
                    _state.value = SetupState.SignedOut(SetupMessage.AccountRequired)
                }
            }
        }
    }

    private val _automation = MutableStateFlow<AutomationStatus?>(null)

    /** Feature 007: the automation status for the automation entry; null until the first check. */
    val automation: StateFlow<AutomationStatus?> = _automation

    fun onAction(action: SetupAction) {
        viewModelScope.launch {
            val account = accounts.currentAccount.first() ?: return@launch
            val current = _state.value
            _state.value = SetupState.Working
            val next = postAuth.onAction(account, current, action)
            // "No" to the rewrite question returns to the chooser without changing the sheet.
            if (next is SetupState.SignedOut) accounts.forgetAccount()
            _state.value = next
            // Feature 007: back from the automation steps, the entry shows the new status.
            if (next == SetupState.Ready && (action == SetupAction.StartAutomation || current.isAutomationStep)) refreshStatus()
        }
    }

    private val SetupState.isAutomationStep get() = this == SetupState.NeedsApiSetting || this is SetupState.NeedsEnable

    /** Background check on launch: spreadsheet still there? automation on? (data-model.md Ready) */
    fun refreshStatus() {
        viewModelScope.launch {
            val account = accounts.currentAccount.first() ?: return@launch
            val result = try {
                postAuth.refresh(account)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                monitoring.failure(NonFatalIssue.SETUP_CHECK_FAILED, e, "setup-check")
                RefreshResult.NoChange
            }
            when (result) {
                RefreshResult.NoChange -> Unit
                is RefreshResult.Status -> _automation.value = result.automation
                RefreshResult.SpreadsheetGone -> {
                    _automation.value = null
                    _state.value = SetupState.Working
                    _state.value = postAuth.run(account)
                }
            }
        }
    }

    /** US3: switching runs authorization and the spreadsheet setup for the new account. */
    fun switchAccount(picker: AccountPicker) {
        viewModelScope.launch {
            val previous = _state.value
            _state.value = SetupState.Authorizing
            when (val picked = accounts.switchAccount(picker)) {
                is PickResult.Picked -> {
                    _automation.value = null
                    handleAuthorization(picked.account, accounts.authorize(picked.account))
                }
                PickResult.Cancelled -> _state.value = previous
                is PickResult.Failed -> {
                    report(picked)
                    _state.value = previous
                }
            }
        }
    }

    /** FR-012: called after the user confirmed; returns to the first-launch chooser. */
    fun signOut() {
        viewModelScope.launch {
            accounts.signOut()
            _automation.value = null
            _state.value = SetupState.SignedOut(message = null)
        }
    }

    fun onConsentResult(data: Intent?) {
        viewModelScope.launch {
            val account = pendingAccount ?: accounts.currentAccount.first() ?: run {
                _state.value = SetupState.SignedOut(SetupMessage.AccountRequired)
                return@launch
            }
            handleAuthorization(account, accounts.resultFromConsent(data))
        }
    }

    /** Feature 005: a failed pick, and authorization failures other than a refusal or cancel. */
    private fun report(picked: PickResult.Failed) =
        monitoring.nonFatal(NonFatalIssue.SIGN_IN_FAILED, PickFailed(picked.type), "type=${picked.type}")

    private fun report(outcome: AuthOutcome) = when (outcome) {
        is AuthOutcome.Failed -> monitoring.nonFatal(
            NonFatalIssue.AUTHORIZATION_FAILED,
            AuthorizationFailed(outcome),
            "operation=authorization; outcome=Failed; status=${outcome.statusCode}",
        )
        AuthOutcome.Unavailable ->
            monitoring.nonFatal(NonFatalIssue.NO_CONNECTION, AuthorizationFailed(outcome), "operation=authorization")
        is AuthOutcome.Granted, is AuthOutcome.NeedsUserConsent, AuthOutcome.Denied -> Unit
    }

    private suspend fun handleAuthorization(account: SelectedAccount, outcome: AuthOutcome) {
        report(outcome)
        when (outcome) {
            is AuthOutcome.Granted -> {
                pendingAccount = null
                _state.value = SetupState.Authorizing
                _state.value = postAuth.run(account)
            }
            is AuthOutcome.NeedsUserConsent -> {
                pendingAccount = account
                _state.value = SetupState.NeedsConsent(outcome.consent)
            }
            AuthOutcome.Denied, AuthOutcome.Unavailable, is AuthOutcome.Failed -> {
                pendingAccount = null
                accounts.forgetAccount()
                _state.value = SetupState.SignedOut(SetupMessage.AccountRequired)
            }
        }
    }
}
