package com.workoutrec.google

import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// contracts/google-apis.md "Error mapping"
class ApiErrorMapperTest {

    private fun googleError(code: Int, message: String, status: String) =
        """{"error":{"code":$code,"message":"$message","status":"$status"}}"""

    @Test
    fun `network failures map to Offline`() {
        assertEquals(ApiError.Offline, ApiErrorMapper.fromException(UnknownHostException("www.googleapis.com")))
        assertEquals(ApiError.Offline, ApiErrorMapper.fromException(SocketTimeoutException("timeout")))
        assertEquals(ApiError.Offline, ApiErrorMapper.fromException(IOException("connection reset")))
    }

    @Test
    fun `401 maps to TokenExpired`() {
        val error = ApiErrorMapper.fromResponse(401, googleError(401, "Request had invalid authentication credentials.", "UNAUTHENTICATED"))
        assertEquals(ApiError.TokenExpired, error)
    }

    @Test
    fun `403 about the disabled Apps Script API maps to AppsScriptApiDisabled`() {
        val body = googleError(
            403,
            "User has not enabled the Apps Script API. Enable it by visiting https://script.google.com/home/usersettings then retry.",
            "PERMISSION_DENIED",
        )
        assertTrue(ApiErrorMapper.fromResponse(403, body) is ApiError.AppsScriptApiDisabled)
    }

    @Test
    fun `other 403 maps to AccessDenied`() {
        val body = googleError(403, "The caller does not have permission", "PERMISSION_DENIED")
        assertTrue(ApiErrorMapper.fromResponse(403, body) is ApiError.AccessDenied)
    }

    @Test
    fun `403 with a non-JSON body maps to AccessDenied`() {
        assertTrue(ApiErrorMapper.fromResponse(403, "Forbidden") is ApiError.AccessDenied)
    }

    @Test
    fun `404 maps to NotFound`() {
        assertEquals(ApiError.NotFound, ApiErrorMapper.fromResponse(404, googleError(404, "File not found: abc.", "NOT_FOUND")))
    }

    @Test
    fun `429 and 5xx map to ServiceUnavailable`() {
        for (code in listOf(429, 500, 502, 503)) {
            assertEquals(ApiError.ServiceUnavailable(code), ApiErrorMapper.fromResponse(code, ""))
        }
    }

    @Test
    fun `other codes map to Unexpected with the Google message`() {
        val error = ApiErrorMapper.fromResponse(400, googleError(400, "Invalid requests[0]", "INVALID_ARGUMENT"))
        assertEquals(ApiError.Unexpected(400, "Invalid requests[0]"), error)
    }

    @Test
    fun `retryable errors are Offline and ServiceUnavailable only`() {
        assertTrue(ApiError.Offline.isRetryable)
        assertTrue(ApiError.ServiceUnavailable(503).isRetryable)
        assertEquals(false, ApiError.NotFound.isRetryable)
        assertEquals(false, ApiError.AccessDenied("x").isRetryable)
    }
}
