package com.workoutrec.auth

import com.workoutrec.fakes.FakeMonitoring
import com.workoutrec.monitoring.NonFatalIssue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// Feature 005 US1: clearing saved credentials on sign-out
class SignOutCleanupTest {

    private val monitoring = FakeMonitoring()

    @Test
    fun `a failure does not stop sign-out and is reported`() = runTest {
        SignOutCleanup(clear = { throw IllegalStateException("no provider") }, monitoring = monitoring)()
        assertEquals(listOf(NonFatalIssue.SIGN_OUT_CLEANUP_FAILED to null), monitoring.nonFatals.map { it.issue to it.details })
    }

    @Test
    fun `cancellation is passed on and not reported`() = runTest {
        val thrown = runCatching { SignOutCleanup(clear = { throw CancellationException("stopped") }, monitoring = monitoring)() }.exceptionOrNull()
        assertTrue(thrown is CancellationException)
        assertEquals(0, monitoring.nonFatals.size)
    }

    @Test
    fun `success is not reported`() = runTest {
        var cleared = false
        SignOutCleanup(clear = { cleared = true }, monitoring = monitoring)()
        assertTrue(cleared)
        assertEquals(0, monitoring.nonFatals.size)
    }
}
