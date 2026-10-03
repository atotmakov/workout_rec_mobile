package com.workoutrec

import com.workoutrec.auth.AuthOutcome
import com.workoutrec.data.SelectedAccount
import com.workoutrec.setup.SetupMessage

sealed interface StartDestination {
    data object Home : StartDestination
    data class Setup(val message: SetupMessage?) : StartDestination
}

object StartDestinationResolver {
    fun resolve(account: SelectedAccount?, auth: AuthOutcome?): StartDestination = TODO()
}
