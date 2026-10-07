package com.workoutrec.sync

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// research R7: refresh on app start and resume at most every 5 minutes; changes always sync
class RefreshPolicyTest {

    private val now = 10_000_000L

    @Test
    fun `refreshes when it never synced`() {
        assertTrue(RefreshPolicy.shouldSync(lastSuccessAt = null, now = now, pendingCount = 0))
    }

    @Test
    fun `does not refresh again within five minutes`() {
        assertFalse(RefreshPolicy.shouldSync(lastSuccessAt = now - 4 * 60_000, now = now, pendingCount = 0))
    }

    @Test
    fun `refreshes after five minutes`() {
        assertTrue(RefreshPolicy.shouldSync(lastSuccessAt = now - 5 * 60_000, now = now, pendingCount = 0))
    }

    @Test
    fun `always syncs when sets are waiting`() {
        assertTrue(RefreshPolicy.shouldSync(lastSuccessAt = now - 1_000, now = now, pendingCount = 1))
    }
}
