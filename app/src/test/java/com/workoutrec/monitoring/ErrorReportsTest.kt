package com.workoutrec.monitoring

import com.workoutrec.fakes.FakeMonitoring
import com.workoutrec.google.ApiError
import com.workoutrec.google.ApiErrorMapper
import com.workoutrec.google.MissingTabException
import java.io.IOException
import java.net.UnknownHostException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Test

// Feature 005 research R1, R10, contracts/issues.md "No-connection rule"
class ErrorReportsTest {

    private val monitoring = FakeMonitoring()

    private fun reportedOnce(): FakeMonitoring.NonFatal = monitoring.nonFatals.single()

    @Test
    fun `no-connection failures go to NO_CONNECTION with the operation`() {
        listOf(ApiError.Offline, ApiError.ServiceUnavailable(503), IOException("reset"), UnknownHostException("x")).forEach { error ->
            monitoring.nonFatals.clear()
            monitoring.failure(NonFatalIssue.MONEY_REFRESH_FAILED, error, "money-refresh", "reason=other")
            assertEquals(NonFatalIssue.NO_CONNECTION, reportedOnce().issue)
            assertEquals("operation=money-refresh", reportedOnce().details)
        }
    }

    @Test
    fun `other failures go to the caller's issue`() {
        listOf(ApiError.Unexpected(500, "boom"), MissingTabException("money"), IllegalStateException("bug")).forEach { error ->
            monitoring.nonFatals.clear()
            monitoring.failure(NonFatalIssue.MONEY_REFRESH_FAILED, error, "money-refresh")
            assertEquals(NonFatalIssue.MONEY_REFRESH_FAILED, reportedOnce().issue)
            assertEquals("operation=money-refresh", reportedOnce().details)
        }
    }

    @Test
    fun `caller details follow the operation`() {
        monitoring.failure(NonFatalIssue.SETUP_STEP_FAILED, IllegalStateException(), "setup", "step=Creating")
        assertEquals("operation=setup; step=Creating", reportedOnce().details)
    }

    @Test
    fun `Google's error text is not sent`() {
        val body = """{"error":{"code":400,"message":"Invalid value 'Bench press' at 'data.values[0]'","status":"INVALID_ARGUMENT"}}"""
        val error = ApiErrorMapper.fromResponse(400, body)
        monitoring.failure(NonFatalIssue.SYNC_FAILED, error, "sync")
        val sent = reportedOnce().error
        assertEquals("HTTP 400 INVALID_ARGUMENT", sent.message)
        generateSequence(sent) { it.cause }.forEach { assertFalse(it.message.orEmpty().contains("Bench press")) }
        assertEquals(error.stackTrace.toList(), sent.stackTrace.toList())
    }

    @Test
    fun `a raw error body that is not JSON is not sent`() {
        val error = ApiErrorMapper.fromResponse(400, "<html>Bench press 1234.50</html>")
        assertEquals("HTTP 400", reportable(error).message)
        val denied = ApiErrorMapper.fromResponse(403, """{"error":{"message":"Bench press","status":"PERMISSION_DENIED"}}""")
        assertEquals("Access denied PERMISSION_DENIED", reportable(denied).message)
    }

    @Test
    fun `errors with fixed messages are sent as they are`() {
        val error = IllegalStateException("bug")
        assertSame(error, reportable(error))
        assertSame(ApiError.NotFound, reportable(ApiError.NotFound))
    }

    @Test
    fun `a bad value names the value, not its content`() {
        val bad = BadValue("sheet-time-zone")
        assertEquals("Unreadable sheet-time-zone", bad.message)
        assertEquals(null, bad.cause)
    }
}
