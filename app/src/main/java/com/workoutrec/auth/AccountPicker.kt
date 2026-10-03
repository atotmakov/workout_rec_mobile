package com.workoutrec.auth

import com.workoutrec.data.SelectedAccount

sealed interface PickResult {
    data class Picked(val account: SelectedAccount) : PickResult
    data object Cancelled : PickResult
    data class Failed(val reason: String) : PickResult
}

/** Shows Google's account chooser (research R2). */
fun interface AccountPicker {
    suspend fun pick(): PickResult
}
