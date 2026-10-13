package com.workoutrec.monitoring

/** The crash thrown by "Crash now"; its name says it was a test in the console. */
class DiagnosticsTestCrash : RuntimeException("Diagnostics test crash")

/** The error reported by "Send test error". */
class DiagnosticsTestError : Exception("Diagnostics test error")

/**
 * Hidden actions behind a long-press on Send diagnostics (research R10): each produces one report
 * so every report kind can be checked on the phone. Harmless in every build.
 */
class DiagnosticsTestActions(
    private val runOnMain: (() -> Unit) -> Unit,
    private val sleep: (Long) -> Unit,
    private val monitoring: Monitoring,
) {
    fun crashNow() {}

    fun freeze(durationMs: Long) {}

    fun sendTestError() {}
}
