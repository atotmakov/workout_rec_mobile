package com.workoutrec.diag

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
}
