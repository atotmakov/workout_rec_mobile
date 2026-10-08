package com.workoutrec.fakes

import android.content.Intent
import com.workoutrec.auth.ApiAuthorizer
import com.workoutrec.auth.AuthOutcome
import kotlinx.coroutines.awaitCancellation

/** Returns queued outcomes in order; repeats the last one when the queue runs out. */
class FakeApiAuthorizer(vararg outcomes: AuthOutcome) : ApiAuthorizer {
    private val queue = ArrayDeque(outcomes.toList())
    private var last: AuthOutcome = AuthOutcome.Granted("token")
    val authorizedEmails = mutableListOf<String>()
    var consentResult: AuthOutcome = AuthOutcome.Granted("token-after-consent")

    /** Never answers, like Google Play services waiting for a network that does not come. */
    var hang = false

    override suspend fun authorize(email: String): AuthOutcome {
        authorizedEmails += email
        if (hang) awaitCancellation()
        if (queue.isNotEmpty()) last = queue.removeFirst()
        return last
    }

    override fun resultFromConsent(data: Intent?): AuthOutcome = consentResult
}
