package com.workoutrec.monitoring

import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// FR-007, data-model.md "NonFatalLimiter": at most 1 event per issue per 30 min and 20 per day
class NonFatalLimiterTest {

    private var now = 1_768_240_800_000L // 2026-01-12 18:00:00 UTC
    private val limiter = NonFatalLimiter(now = { now }, zone = { ZoneOffset.UTC })
    private val minute = 60_000L

    @Test
    fun `the first event of an issue is sent`() {
        assertTrue(limiter.allow(NonFatalIssue.SYNC_FAILED))
    }

    @Test
    fun `the same issue waits 30 minutes`() {
        assertTrue(limiter.allow(NonFatalIssue.SYNC_FAILED))
        now += 29 * minute
        assertFalse(limiter.allow(NonFatalIssue.SYNC_FAILED))
        now += minute
        assertTrue(limiter.allow(NonFatalIssue.SYNC_FAILED))
    }

    @Test
    fun `different issues do not wait for each other`() {
        assertTrue(limiter.allow(NonFatalIssue.SYNC_FAILED))
        assertTrue(limiter.allow(NonFatalIssue.TOKEN_FAILED))
    }

    private val errorsAndWarnings = NonFatalIssue.entries.filter { it.severity != IssueSeverity.INFO }

    @Test
    fun `no more than 20 error and warning events a day in total`() {
        now = 1_768_176_000_000L // 2026-01-12 00:00 UTC
        val sent = (1..21).count {
            limiter.allow(errorsAndWarnings[it % errorsAndWarnings.size]).also { now += 31 * minute }
        }
        assertEquals(20, sent)
    }

    @Test
    fun `the daily count starts again on a new day`() {
        now = 1_768_176_000_000L // 2026-01-12 00:00 UTC
        repeat(20) {
            limiter.allow(errorsAndWarnings[it % errorsAndWarnings.size])
            now += 31 * minute
        }
        assertFalse(limiter.allow(NonFatalIssue.SYNC_FAILED))
        now = 1_768_262_400_000L + minute // 2026-01-13 00:01 UTC
        assertTrue(limiter.allow(NonFatalIssue.SYNC_FAILED))
    }

    // Feature 005 research R2: info events have their own budget
    @Test
    fun `no more than 5 info events a day`() {
        now = 1_768_176_000_000L // 2026-01-12 00:00 UTC
        val sent = (1..6).count { limiter.allow(NonFatalIssue.NO_CONNECTION).also { now += 31 * minute } }
        assertEquals(5, sent)
    }

    @Test
    fun `info events do not use the error budget`() {
        now = 1_768_176_000_000L // 2026-01-12 00:00 UTC
        repeat(5) {
            limiter.allow(NonFatalIssue.NO_CONNECTION)
            now += 31 * minute
        }
        val sent = (1..21).count {
            limiter.allow(errorsAndWarnings[it % errorsAndWarnings.size]).also { now += 31 * minute }
        }
        assertEquals(20, sent)
    }

    @Test
    fun `the info count starts again on a new day`() {
        now = 1_768_176_000_000L // 2026-01-12 00:00 UTC
        repeat(5) {
            limiter.allow(NonFatalIssue.NO_CONNECTION)
            now += 31 * minute
        }
        assertFalse(limiter.allow(NonFatalIssue.NO_CONNECTION))
        now = 1_768_262_400_000L + minute // 2026-01-13 00:01 UTC
        assertTrue(limiter.allow(NonFatalIssue.NO_CONNECTION))
    }
}
