package com.workoutrec.auth

import com.workoutrec.data.SelectedAccount
import com.workoutrec.data.SettingsStore
import com.workoutrec.google.TokenProvider
import kotlinx.coroutines.flow.Flow

class AccountRepository(
    private val authorizer: ApiAuthorizer,
    private val store: SettingsStore,
) : TokenProvider {
    val currentAccount: Flow<SelectedAccount?> get() = TODO()
    suspend fun chooseAccount(picker: AccountPicker): PickResult = TODO()
    suspend fun authorize(account: SelectedAccount): AuthOutcome = TODO()
    suspend fun authorizeOnLaunch(): AuthOutcome? = TODO()
    fun resultFromConsent(data: android.content.Intent?): AuthOutcome = TODO()
    suspend fun forgetAccount(): Unit = TODO()
    override suspend fun accessToken(forceRefresh: Boolean): String = TODO()
}
