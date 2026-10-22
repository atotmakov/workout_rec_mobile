package com.workoutrec.diag

import com.workoutrec.fakes.FakeMonitoring
import com.workoutrec.monitoring.NonFatalIssue
import org.junit.Assert.assertEquals
import org.junit.Test

// quickstart-results.md issue 1 (3e): the log must show whether the app's network is a VPN
class DeviceStateTest {

    @Test
    fun `the network says whether it is a VPN`() {
        assertEquals("internet (validated), vpn", DeviceState.network(internet = true, validated = true, vpn = true))
        assertEquals("internet (not validated)", DeviceState.network(internet = true, validated = false, vpn = false))
        assertEquals("no internet, vpn", DeviceState.network(internet = false, validated = false, vpn = true))
    }

    // Feature 005 analysis C1: the check needs a permission every install has; failing is an error
    @Test
    fun `a refused network check reads unknown and is reported`() {
        val monitoring = FakeMonitoring()
        assertEquals("unknown", DeviceState.network(monitoring) { throw SecurityException("no ACCESS_NETWORK_STATE") })
        assertEquals(listOf(NonFatalIssue.SYNC_LOG_FAILED to "action=device-state"), monitoring.nonFatals.map { it.issue to it.details })
    }

    @Test
    fun `a working network check is not reported`() {
        val monitoring = FakeMonitoring()
        assertEquals("none", DeviceState.network(monitoring) { "none" })
        assertEquals(0, monitoring.nonFatals.size)
    }
}
