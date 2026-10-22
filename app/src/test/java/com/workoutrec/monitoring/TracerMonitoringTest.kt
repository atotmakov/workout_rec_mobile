package com.workoutrec.monitoring

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// Feature 005 SC-005, FR-009: Send diagnostics off sends nothing; a failing send changes nothing
class TracerMonitoringTest {

    private val sent = mutableListOf<Pair<IssueSeverity, String>>()
    private val limiter = NonFatalLimiter()

    private fun monitoring(on: Boolean, send: (IssueSeverity, Throwable, String) -> Unit = { s, _, key -> sent += s to key }) =
        TracerMonitoring(isEnabled = { on }, limiter = limiter, send = send)

    @Test
    fun `nothing is sent while switched off and the limiter is untouched`() {
        val off = monitoring(on = false)
        NonFatalIssue.entries.forEach { off.nonFatal(it, IllegalStateException(), "x=y") }
        assertEquals(emptyList<Pair<IssueSeverity, String>>(), sent)
        NonFatalIssue.entries.filter { it.severity != IssueSeverity.INFO }.take(5).forEach { assertTrue(limiter.allow(it)) }
    }

    @Test
    fun `one send per allowed report`() {
        val on = monitoring(on = true)
        on.nonFatal(NonFatalIssue.MONEY_REFRESH_FAILED, IllegalStateException(), null)
        on.nonFatal(NonFatalIssue.MONEY_REFRESH_FAILED, IllegalStateException(), null)
        assertEquals(listOf(IssueSeverity.ERROR to "money-refresh-failed"), sent)
    }

    @Test
    fun `a failing send does not reach the caller`() {
        val on = monitoring(on = true, send = { _, _, _ -> throw RuntimeException("SDK broke") })
        on.nonFatal(NonFatalIssue.SYNC_FAILED, IllegalStateException(), "operation=sync")
    }
}
