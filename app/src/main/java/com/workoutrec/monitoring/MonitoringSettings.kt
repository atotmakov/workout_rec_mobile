package com.workoutrec.monitoring

import android.content.SharedPreferences

/** A synchronous boolean store; [SharedPreferencesStore] on the phone (research R3). */
interface BooleanStore {
    fun get(key: String, default: Boolean): Boolean
    fun put(key: String, value: Boolean)
}

class SharedPreferencesStore(private val prefs: SharedPreferences) : BooleanStore {
    override fun get(key: String, default: Boolean) = prefs.getBoolean(key, default)
    override fun put(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
    }
}

/** The Send diagnostics switch (FR-012, data-model "MonitoringSetting"). */
class MonitoringSettings(private val store: BooleanStore) {
    var sendDiagnostics: Boolean
        get() = false
        set(value) {}

    /** The value this process started with; Tracer was configured from it. */
    val startedWith: Boolean = false

    /** Turned on while this process started with it off: Tracer starts on the next launch. */
    val appliesAfterRestart: Boolean get() = false

    companion object {
        const val FILE = "monitoring"
        const val KEY = "send_diagnostics"
    }
}
