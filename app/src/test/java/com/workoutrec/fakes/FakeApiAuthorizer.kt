package com.workoutrec.fakes

import android.content.Intent
import com.workoutrec.auth.ApiAuthorizer
import com.workoutrec.auth.AuthOutcome

/** Returns queued outcomes in order; repeats the last one when the queue runs out. */
class FakeApiAuthorizer(vararg outcomes: AuthOutcome) : ApiAuthorizer {
    private val queue = ArrayDeque(outcomes.toList())
    private var last: AuthOutcome = AuthOutcome.Granted("token")
    val authorizedEmails = mutableListOf<String>()
    var consentResult: AuthOutcome = AuthOutcome.Granted("token-after-consent")

    override suspend fun authorize(email: String): AuthOutcome {
        authorizedEmails += email
        if (queue.isNotEmpty()) last = queue.removeFirst()
        return last
    }

    override fun resultFromConsent(data: Intent?): AuthOutcome = consentResult
}
