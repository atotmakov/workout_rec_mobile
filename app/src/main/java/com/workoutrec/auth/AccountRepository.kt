package com.workoutrec.auth

import android.content.Intent
import com.workoutrec.data.SelectedAccount
import com.workoutrec.data.SettingsStore
import com.workoutrec.google.ApiError
import com.workoutrec.google.TokenProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** The selected account and its API access; also the token source for Google calls. */
class AccountRepository(
    private val authorizer: ApiAuthorizer,
    private val store: SettingsStore,
    /** Clears Credential Manager state on sign-out (US3). */
    private val clearCredentials: suspend () -> Unit = {},
    /** Diagnostic log for background sync (quickstart-results.md issue 1). */
    private val log: (String) -> Unit = {},
) : TokenProvider {

    private val tokenLock = Mutex()
    private var cachedToken: String? = null

    val currentAccount: Flow<SelectedAccount?> = store.account

    /** Shows the chooser; saves the picked account (FR-001). */
    suspend fun chooseAccount(picker: AccountPicker): PickResult {
        val result = picker.pick()
        if (result is PickResult.Picked) {
            store.saveAccount(result.account)
            cachedToken = null
        }
        return result
    }

    suspend fun authorize(account: SelectedAccount): AuthOutcome =
        authorizer.authorize(account.email).also(::remember)

    /**
     * Silent authorization for the stored account on launch (FR-004). Returns null when no account
     * is stored. Denied means access was revoked, so the stored account is forgotten.
     */
    suspend fun authorizeOnLaunch(): AuthOutcome? {
        val account = store.account.first() ?: return null
        val outcome = authorize(account)
        if (outcome is AuthOutcome.Denied) forgetAccount()
        return outcome
    }

    fun resultFromConsent(data: Intent?): AuthOutcome = authorizer.resultFromConsent(data).also(::remember)

    /**
     * US3: pick another account. The old binding is dropped even for the same account, so the
     * spreadsheet check runs again (US3 #1). Cancel keeps everything as it was.
     */
    suspend fun switchAccount(picker: AccountPicker): PickResult {
        val result = picker.pick()
        if (result is PickResult.Picked) {
            store.clearBinding()
            store.saveAccount(result.account)
            cachedToken = null
        }
        return result
    }

    /** FR-012 (after confirmation): clear Credential Manager state and all local data. */
    suspend fun signOut() {
        clearCredentials()
        forgetAccount()
    }

    suspend fun forgetAccount() {
        cachedToken = null
        store.clear()
    }

    override suspend fun accessToken(forceRefresh: Boolean): String = tokenLock.withLock {
        cachedToken?.takeIf { !forceRefresh }?.let { return@withLock it }
        val account = store.account.first() ?: throw ApiError.AccessDenied("No Google account selected")
        // Play services may wait for a network that does not come; treat that as offline (issue 1).
        when (val outcome = withTimeoutOrNull(AUTHORIZE_TIMEOUT_MILLIS) { authorize(account) } ?: AuthOutcome.Unavailable) {
            is AuthOutcome.Granted -> outcome.accessToken
            AuthOutcome.Unavailable -> throw ApiError.Offline
            else -> throw ApiError.AccessDenied("Google access was not granted")
        }
    }

    private fun remember(outcome: AuthOutcome) {
        if (outcome is AuthOutcome.Granted) cachedToken = outcome.accessToken
    }

    companion object {
        /** quickstart-results.md issue 1. */
        const val AUTHORIZE_TIMEOUT_MILLIS = 20_000L
    }
}
