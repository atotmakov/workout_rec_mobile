package com.workoutrec.auth

/** What the app should do with an authorization attempt. */
sealed interface AuthDecision {
    data class Granted(val accessToken: String) : AuthDecision
    data object NeedsUserConsent : AuthDecision
    data object Denied : AuthDecision

    /** A Google error other than a network error or a cancel (feature 005: reported). */
    data class Failed(val statusCode: Int) : AuthDecision

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

    /** [statusCode] 16 is `CommonStatusCodes.CANCELED`: the user closed the consent screen. */
    fun decideFailure(networkError: Boolean, statusCode: Int? = null): AuthDecision = when {
        networkError -> AuthDecision.Unavailable
        statusCode == null || statusCode == CANCELED -> AuthDecision.Denied
        else -> AuthDecision.Failed(statusCode)
    }

    private const val CANCELED = 16
}
