package com.workoutrec

import com.workoutrec.auth.AuthOutcome
import com.workoutrec.data.SelectedAccount
import com.workoutrec.setup.SetupMessage

sealed interface StartDestination {
    data object Home : StartDestination
    data class Setup(val message: SetupMessage?) : StartDestination
}

/** Where the app opens (FR-001, FR-004): home only for a stored account that still has access. */
object StartDestinationResolver {
    fun resolve(account: SelectedAccount?, auth: AuthOutcome?): StartDestination = when {
        account == null -> StartDestination.Setup(message = null)
        // Offline: keep the stored account and open the app; access is re-checked when online.
        auth is AuthOutcome.Granted || auth is AuthOutcome.Unavailable -> StartDestination.Home
        else -> StartDestination.Setup(message = SetupMessage.AccessRevoked)
    }
}
