package com.workoutrec.setup

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.workoutrec.StartDestination
import com.workoutrec.StartDestinationResolver
import com.workoutrec.auth.AccountPicker
import com.workoutrec.auth.AccountRepository
import com.workoutrec.auth.AuthOutcome
import com.workoutrec.auth.PickResult
import com.workoutrec.data.SelectedAccount
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Drives the setup flow (data-model.md "Setup flow"). */
class SetupViewModel(
    private val accounts: AccountRepository,
    private val postAuth: PostAuthStep,
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
                PickResult.Cancelled, is PickResult.Failed ->
                    _state.value = SetupState.SignedOut(SetupMessage.AccountRequired)
            }
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

    private suspend fun handleAuthorization(account: SelectedAccount, outcome: AuthOutcome) {
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
            AuthOutcome.Denied, AuthOutcome.Unavailable -> {
                pendingAccount = null
                accounts.forgetAccount()
                _state.value = SetupState.SignedOut(SetupMessage.AccountRequired)
            }
        }
    }
}
