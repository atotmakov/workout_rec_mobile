package com.workoutrec.monitoring

/**
 * Turning Send diagnostics on or off (FR-012, research R3 update): off stops Tracer at once and
 * drops the reports it stored but has not sent; on takes effect from the next app start.
 */
class MonitoringControl(
    private val settings: MonitoringSettings,
    private val disableTracer: () -> Unit,
    private val deleteStoredReports: () -> Unit,
) {
    fun setSendDiagnostics(on: Boolean) {}
}
