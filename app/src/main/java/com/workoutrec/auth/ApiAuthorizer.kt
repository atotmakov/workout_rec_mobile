package com.workoutrec.auth

import android.accounts.Account
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Scope
import java.io.IOException
import kotlinx.coroutines.tasks.await

/** Wraps a consent screen to launch; [pendingIntent] is null only in tests. */
class ConsentRequest(val pendingIntent: PendingIntent?)

sealed interface AuthOutcome {
    data class Granted(val accessToken: String) : AuthOutcome
    data class NeedsUserConsent(val consent: ConsentRequest) : AuthOutcome
    data object Denied : AuthOutcome
    data object Unavailable : AuthOutcome
}

/** Gets API access for the chosen account (research R3). */
interface ApiAuthorizer {
    suspend fun authorize(email: String): AuthOutcome

    /** Result after the user returns from the consent screen. */
    fun resultFromConsent(data: Intent?): AuthOutcome
}

/** Thin wrapper over the Authorization API; every decision lives in [AuthorizationMapper]. */
class GoogleApiAuthorizer(context: Context) : ApiAuthorizer {

    private val client = Identity.getAuthorizationClient(context)

    override suspend fun authorize(email: String): AuthOutcome {
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(AuthorizationMapper.SCOPES.map { Scope(it) })
            .setAccount(Account(email, "com.google"))
            .build()
        return try {
            toOutcome(client.authorize(request).await())
        } catch (e: ApiException) {
            AuthorizationMapper.decideFailure(e.statusCode == CommonStatusCodes.NETWORK_ERROR).toOutcome(null)
        } catch (e: IOException) {
            AuthorizationMapper.decideFailure(networkError = true).toOutcome(null)
        }
    }

    override fun resultFromConsent(data: Intent?): AuthOutcome = try {
        toOutcome(client.getAuthorizationResultFromIntent(data))
    } catch (e: ApiException) {
        AuthorizationMapper.decideFailure(e.statusCode == CommonStatusCodes.NETWORK_ERROR).toOutcome(null)
    }

    private fun toOutcome(result: AuthorizationResult): AuthOutcome =
        AuthorizationMapper.decide(result.accessToken, result.hasResolution())
            .toOutcome(result.pendingIntent)

    private fun AuthDecision.toOutcome(pendingIntent: PendingIntent?): AuthOutcome = when (this) {
        is AuthDecision.Granted -> AuthOutcome.Granted(accessToken)
        AuthDecision.NeedsUserConsent -> AuthOutcome.NeedsUserConsent(ConsentRequest(pendingIntent))
        AuthDecision.Denied -> AuthOutcome.Denied
        AuthDecision.Unavailable -> AuthOutcome.Unavailable
    }
}
