package com.workoutrec.monitoring

import ru.ok.tracer.CoreTracerConfiguration
import ru.ok.tracer.Severity
import ru.ok.tracer.TracerConfiguration
import ru.ok.tracer.crash.report.CrashFreeConfiguration
import ru.ok.tracer.crash.report.CrashReportConfiguration
import ru.ok.tracer.crash.report.TracerCrashReport
import ru.ok.tracer.disk.usage.DiskUsageConfiguration
import ru.ok.tracer.heap.dumps.HeapDumpConfiguration
import ru.ok.tracer.profiler.sampling.SamplingProfilerConfiguration
import ru.ok.tracer.profiler.systrace.SystraceProfilerConfiguration

/**
 * Monitoring through the Tracer SDK 1.4.0 (research R1, "SDK API as published"). The only class
 * that imports `ru.ok.tracer`; a thin adapter, validated on the phone (quickstart.md). Every call
 * checks [enabled] first and never lets an SDK error reach the app (FR-015).
 */
class TracerMonitoring(private val isEnabled: () -> Boolean) : Monitoring {

    override val enabled: Boolean get() = isEnabled()

    override fun log(line: String) {
        if (enabled) safely { TracerCrashReport.log(line) }
    }

    override fun nonFatal(issue: NonFatalIssue, error: Throwable, details: String?) {
        if (!enabled) return
        val reported = if (details == null) error else ReportedError(details, error)
        safely { TracerCrashReport.report(issue.severity.toTracer(), reported, issue.key) }
    }

    override fun beginProfile(target: ProfileTarget): Profile = NoProfile

    /** Carries [details] in the event's message, with the original error as its cause. */
    private class ReportedError(details: String, cause: Throwable) : Exception(details, cause)

    companion object {
        /** Maps a [ConfigPlan] to the configuration list Tracer reads once per process (R3). */
        fun configurations(plan: ConfigPlan): List<TracerConfiguration> = plan.modules.map { module ->
            when (module) {
                is TracerModule.Core -> CoreTracerConfiguration.Builder().setDebugUpload(module.debugUpload).build()
                is TracerModule.CrashReport -> CrashReportConfiguration.Builder()
                    .setSendAnr(module.sendAnr)
                    .setExperimentalNonFatalRateLimitEnabled(module.nonFatalSoftLimit)
                    .build()
                TracerModule.CrashFree -> CrashFreeConfiguration.Builder().build()
                TracerModule.HeapDumps -> HeapDumpConfiguration.Builder().build()
                is TracerModule.DiskUsage -> DiskUsageConfiguration.Builder()
                    .setInterestingSize(module.interestingSizeBytes)
                    .setProbability(module.probability)
                    .build()
                TracerModule.SamplingProfiler -> SamplingProfilerConfiguration.Builder().build()
                TracerModule.SystraceProfiler -> SystraceProfilerConfiguration.Builder().build()
            }
        }

        private fun IssueSeverity.toTracer(): Severity = when (this) {
            IssueSeverity.ERROR -> Severity.ERROR
            IssueSeverity.WARNING -> Severity.WARNING
            IssueSeverity.INFO -> Severity.INFO
        }

        private inline fun safely(block: () -> Unit) {
            try {
                block()
            } catch (e: Throwable) {
                // Monitoring must never break the app (FR-015).
            }
        }
    }
}
