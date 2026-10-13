package com.workoutrec.monitoring

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// FR-012, User Story 6, research R3 update
class MonitoringControlTest {

    private class MapStore : BooleanStore {
        val values = mutableMapOf<String, Boolean>()
        override fun get(key: String, default: Boolean) = values[key] ?: default
        override fun put(key: String, value: Boolean) {
            values[key] = value
        }
    }

    private val settings = MonitoringSettings(MapStore())
    private var disabled = 0
    private var cleaned = 0
    private val control = MonitoringControl(settings, disableTracer = { disabled++ }, deleteStoredReports = { cleaned++ })

    @Test
    fun `turning off saves it, stops Tracer at once and drops stored reports`() {
        control.setSendDiagnostics(false)
        assertFalse(settings.sendDiagnostics)
        assertEquals(1, disabled)
        assertEquals(1, cleaned)
    }

    @Test
    fun `turning on saves it and waits for the next start`() {
        control.setSendDiagnostics(false)
        control.setSendDiagnostics(true)
        assertTrue(settings.sendDiagnostics)
        assertEquals(1, disabled)
    }

    @Test
    fun `monitoring stops reporting as soon as the switch is off`() {
        val monitoring = TracerMonitoring(isEnabled = { settings.sendDiagnostics })
        assertTrue(monitoring.enabled)
        control.setSendDiagnostics(false)
        assertFalse(monitoring.enabled)
    }
}
