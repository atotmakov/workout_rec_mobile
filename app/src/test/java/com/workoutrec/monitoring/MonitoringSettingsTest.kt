package com.workoutrec.monitoring

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// data-model.md "MonitoringSetting", FR-012
class MonitoringSettingsTest {

    private class MapStore : BooleanStore {
        val values = mutableMapOf<String, Boolean>()
        override fun get(key: String, default: Boolean) = values[key] ?: default
        override fun put(key: String, value: Boolean) {
            values[key] = value
        }
    }

    @Test
    fun `send diagnostics is on for a fresh install`() {
        assertTrue(MonitoringSettings(MapStore()).sendDiagnostics)
    }

    @Test
    fun `the choice is kept for the next process`() {
        val store = MapStore()
        MonitoringSettings(store).sendDiagnostics = false
        assertFalse(MonitoringSettings(store).sendDiagnostics)
        assertEquals(false, store.values[MonitoringSettings.KEY])
    }

    @Test
    fun `started with is captured once at process start`() {
        val settings = MonitoringSettings(MapStore())
        settings.sendDiagnostics = false
        assertTrue(settings.startedWith)
    }

    @Test
    fun `applies after restart only when turned on after starting off`() {
        val store = MapStore().apply { put(MonitoringSettings.KEY, false) }
        val settings = MonitoringSettings(store)
        assertFalse(settings.startedWith)
        assertFalse(settings.appliesAfterRestart)
        settings.sendDiagnostics = true
        assertTrue(settings.appliesAfterRestart)
    }

    @Test
    fun `turning off a process that started on needs no restart note`() {
        val settings = MonitoringSettings(MapStore())
        settings.sendDiagnostics = false
        assertFalse(settings.appliesAfterRestart)
    }
}
