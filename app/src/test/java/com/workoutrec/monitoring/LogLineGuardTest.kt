package com.workoutrec.monitoring

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// FR-013, data-model.md "LogLineGuard"
class LogLineGuardTest {

    @Test
    fun `the lines the app writes today pass`() {
        listOf(
            "app process started; network: internet (validated), vpn, idle: no, power saver: off, standby: active",
            "token: requesting",
            "token: granted",
            "token: no answer in 20 s",
            "sync: start",
            "sync: 2 pending",
            "sync: done, wrote 1 new, 0 edited, 0 deleted",
            "sync: failed: Offline: null",
            "setup: spreadsheet 1UZ7IFCXApw8kMyT0L_y5jzFgVojSSeUsDIN8fUK_u4E matches",
            "background run: started",
        ).forEach { assertTrue(it, LogLineGuard.allows(it)) }
    }

    @Test
    fun `a line with an email does not leave the phone`() {
        assertFalse(LogLineGuard.allows("account: a@example.com"))
    }

    @Test
    fun `a line with a Google access token does not leave the phone`() {
        assertFalse(LogLineGuard.allows("token: ya29.a0AfB_byC"))
    }
}
