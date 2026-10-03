package com.workoutrec.auth

/** What the app should do with an authorization attempt. */
sealed interface AuthDecision {
    data class Granted(val accessToken: String) : AuthDecision
    data object NeedsUserConsent : AuthDecision
    data object Denied : AuthDecision

    /** Network failure: keep the stored account and try again later. */
    data object Unavailable : AuthDecision
}

/** Pure decisions for [ApiAuthorizer] (research R3), kept separate so they are unit-tested. */
object AuthorizationMapper {

    val SCOPES: List<String> = listOf(
        "https://www.googleapis.com/auth/drive.file",
        "https://www.googleapis.com/auth/script.projects",
        "https://www.googleapis.com/auth/script.deployments",
    )

    fun decide(accessToken: String?, hasResolution: Boolean): AuthDecision = when {
        hasResolution -> AuthDecision.NeedsUserConsent
        !accessToken.isNullOrEmpty() -> AuthDecision.Granted(accessToken)
        else -> AuthDecision.Denied
    }

    fun decideFailure(networkError: Boolean): AuthDecision =
        if (networkError) AuthDecision.Unavailable else AuthDecision.Denied
}
