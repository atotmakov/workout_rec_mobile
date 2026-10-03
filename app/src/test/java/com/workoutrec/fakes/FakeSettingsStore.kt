package com.workoutrec.fakes

import com.workoutrec.data.SelectedAccount
import com.workoutrec.data.SettingsStore
import com.workoutrec.data.SpreadsheetBinding
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine

/** In-memory [SettingsStore] with the same binding rules as the real one. */
class FakeSettingsStore(
    account: SelectedAccount? = null,
    binding: SpreadsheetBinding? = null,
) : SettingsStore {
    val accountState = MutableStateFlow(account)
    val bindingState = MutableStateFlow(binding)
    var clearCalls = 0

    override val account: Flow<SelectedAccount?> = accountState
    override val binding: Flow<SpreadsheetBinding?> =
        combine(accountState, bindingState) { a, b -> b?.takeIf { a != null && it.accountEmail == a.email } }

    override suspend fun saveAccount(account: SelectedAccount) {
        if (accountState.value?.email != account.email) bindingState.value = null
        accountState.value = account
    }

    override suspend fun saveBinding(binding: SpreadsheetBinding) {
        bindingState.value = binding
    }

    override suspend fun clearBinding() {
        bindingState.value = null
    }

    override suspend fun clear() {
        clearCalls++
        accountState.value = null
        bindingState.value = null
    }
}
