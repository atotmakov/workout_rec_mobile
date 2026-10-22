package com.workoutrec.auth

import org.junit.Assert.assertEquals
import org.junit.Test

// research.md R3: exactly three scopes; result decisions
class AuthorizationMapperTest {

    @Test
    fun `requests exactly the three narrow scopes`() {
        assertEquals(
            listOf(
                "https://www.googleapis.com/auth/drive.file",
                "https://www.googleapis.com/auth/script.projects",
                "https://www.googleapis.com/auth/script.deployments",
            ),
            AuthorizationMapper.SCOPES,
        )
    }

    @Test
    fun `result with an access token is Granted`() {
        assertEquals(AuthDecision.Granted("tok"), AuthorizationMapper.decide(accessToken = "tok", hasResolution = false))
    }

    @Test
    fun `result with a pending intent needs user consent`() {
        assertEquals(AuthDecision.NeedsUserConsent, AuthorizationMapper.decide(accessToken = null, hasResolution = true))
    }

    @Test
    fun `consent takes priority over a stale token`() {
        assertEquals(AuthDecision.NeedsUserConsent, AuthorizationMapper.decide(accessToken = "tok", hasResolution = true))
    }

    @Test
    fun `result with neither token nor pending intent is Denied`() {
        assertEquals(AuthDecision.Denied, AuthorizationMapper.decide(accessToken = null, hasResolution = false))
        assertEquals(AuthDecision.Denied, AuthorizationMapper.decide(accessToken = "", hasResolution = false))
    }

    @Test
    fun `failure or cancel is Denied`() {
        assertEquals(AuthDecision.Denied, AuthorizationMapper.decideFailure(networkError = false))
    }

    @Test
    fun `network failure is Unavailable, not Denied, so the stored account is kept`() {
        assertEquals(AuthDecision.Unavailable, AuthorizationMapper.decideFailure(networkError = true))
    }

    // Feature 005: a Google error keeps its status so it can be reported; a cancel stays Denied
    @Test
    fun `a Google error status is kept, a cancelled consent is denied`() {
        assertEquals(AuthDecision.Failed(10), AuthorizationMapper.decideFailure(networkError = false, statusCode = 10))
        assertEquals(AuthDecision.Denied, AuthorizationMapper.decideFailure(networkError = false, statusCode = 16))
        assertEquals(AuthDecision.Unavailable, AuthorizationMapper.decideFailure(networkError = true, statusCode = 7))
    }
}
