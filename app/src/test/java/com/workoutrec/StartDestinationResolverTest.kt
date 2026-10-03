package com.workoutrec

import com.workoutrec.auth.AuthOutcome
import com.workoutrec.auth.ConsentRequest
import com.workoutrec.data.SelectedAccount
import com.workoutrec.setup.SetupMessage
import org.junit.Assert.assertEquals
import org.junit.Test

// FR-001, FR-004, edge case "access is revoked later"
class StartDestinationResolverTest {

    private val account = SelectedAccount("a@example.com", "Alex", null)

    @Test
    fun `no stored account goes to setup`() {
        assertEquals(StartDestination.Setup(message = null), StartDestinationResolver.resolve(account = null, auth = null))
    }

    @Test
    fun `stored account with silent grant goes home`() {
        assertEquals(StartDestination.Home, StartDestinationResolver.resolve(account, AuthOutcome.Granted("t")))
    }

    @Test
    fun `stored account with denied access goes to setup with access revoked`() {
        assertEquals(
            StartDestination.Setup(message = SetupMessage.AccessRevoked),
            StartDestinationResolver.resolve(account, AuthOutcome.Denied),
        )
    }

    @Test
    fun `stored account needing consent again goes to setup`() {
        val outcome = AuthOutcome.NeedsUserConsent(ConsentRequest(null))
        assertEquals(StartDestination.Setup(message = SetupMessage.AccessRevoked), StartDestinationResolver.resolve(account, outcome))
    }

    @Test
    fun `stored account while offline still goes home`() {
        assertEquals(StartDestination.Home, StartDestinationResolver.resolve(account, AuthOutcome.Unavailable))
    }
}
