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
    private val takeHeapDump: (tag: String) -> Unit = {},
    private val runDiskCheck: () -> Unit = {},
    private val forceProfileNextStart: () -> Unit = {},
) {
    /** An unhandled crash on the main thread (US1, quickstart scenario 1). */
    fun crashNow() = runOnMain { throw DiagnosticsTestCrash() }

    /** Blocks the main thread so the system reports a freeze (US1, scenario 2). */
    fun freeze(durationMs: Long) = runOnMain { sleep(durationMs) }

    /** One non-fatal event, grouped under `diagnostics-test` (US2). */
    fun sendTestError() = monitoring.nonFatal(NonFatalIssue.DIAGNOSTICS_TEST, DiagnosticsTestError())

    /** A memory snapshot, sent when the phone is idle (US3, scenario 6). */
    fun memorySnapshotNow() = takeHeapDump("manual")

    /** One disk-usage check (US4, scenario 7). */
    fun diskCheckNow() = runDiskCheck()

    /** The next app start is profiled whatever the share of runs (US5, scenario 8). */
    fun profileNextAppStart() = forceProfileNextStart()
}
