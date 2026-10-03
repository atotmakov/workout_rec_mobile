package com.workoutrec.auth

sealed interface AuthDecision {
    data class Granted(val accessToken: String) : AuthDecision
    data object NeedsUserConsent : AuthDecision
    data object Denied : AuthDecision
    data object Unavailable : AuthDecision
}

object AuthorizationMapper {
    val SCOPES: List<String> = emptyList()
    fun decide(accessToken: String?, hasResolution: Boolean): AuthDecision = TODO()
    fun decideFailure(networkError: Boolean): AuthDecision = TODO()
}
