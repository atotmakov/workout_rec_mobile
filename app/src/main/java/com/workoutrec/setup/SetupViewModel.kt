package com.workoutrec.setup

import android.content.Intent
import androidx.lifecycle.ViewModel
import com.workoutrec.auth.AccountPicker
import com.workoutrec.auth.AccountRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class SetupViewModel(
    private val accounts: AccountRepository,
    private val postAuth: PostAuthStep,
) : ViewModel() {
    private val _state = MutableStateFlow<SetupState>(SetupState.Starting)
    val state: StateFlow<SetupState> = _state

    fun start(): Unit = TODO()
    fun chooseAccount(picker: AccountPicker): Unit = TODO()
    fun onConsentResult(data: Intent?): Unit = TODO()
}
