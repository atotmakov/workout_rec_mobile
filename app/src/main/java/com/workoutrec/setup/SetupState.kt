package com.workoutrec.setup

import com.workoutrec.auth.ConsentRequest
import com.workoutrec.data.SelectedAccount

/** Messages shown on the account chooser. */
enum class SetupMessage { AccountRequired, AccessRevoked }

/** Setup flow states (data-model.md "Setup flow"); later stories add spreadsheet states. */
sealed interface SetupState {
    data object Starting : SetupState
    data class SignedOut(val message: SetupMessage?) : SetupState
    data object Authorizing : SetupState
    data class NeedsConsent(val consent: ConsentRequest) : SetupState
    data object Ready : SetupState
}

/** What happens after the account is authorized (US2 plugs the spreadsheet setup in here). */
fun interface PostAuthStep {
    suspend fun run(account: SelectedAccount): SetupState
}
