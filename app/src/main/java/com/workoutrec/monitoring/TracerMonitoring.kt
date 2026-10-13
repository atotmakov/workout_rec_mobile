package com.workoutrec.monitoring

import android.os.Build
import android.os.Trace
import ru.ok.tracer.CoreTracerConfiguration
import ru.ok.tracer.Severity
import ru.ok.tracer.Tracer
import ru.ok.tracer.TracerConfiguration
import ru.ok.tracer.crash.report.CrashFreeConfiguration
import ru.ok.tracer.crash.report.CrashReportConfiguration
import ru.ok.tracer.crash.report.TracerCrashReport
import ru.ok.tracer.disk.usage.DiskUsage
import ru.ok.tracer.disk.usage.DiskUsageConfiguration
import ru.ok.tracer.heap.dumps.HeapDumpConfiguration
import ru.ok.tracer.heap.dumps.HeapDumps
import ru.ok.tracer.profiler.sampling.SamplingProfiler
import ru.ok.tracer.profiler.sampling.SamplingProfilerConfiguration
import ru.ok.tracer.profiler.systrace.SystraceProfiler
import ru.ok.tracer.profiler.systrace.SystraceProfilerConfiguration

/**
 * Monitoring through the Tracer SDK 1.4.0 (research R1, "SDK API as published"). The only class
 * that imports `ru.ok.tracer`; a thin adapter, validated on the phone (quickstart.md). Every call
 * checks [enabled] first and never lets an SDK error reach the app (FR-015).
 */
class TracerMonitoring(
    private val isEnabled: () -> Boolean,
    private val limiter: NonFatalLimiter = NonFatalLimiter(),
    /** Null: profiling off (research R9). */
    sampler: ProfileSampler? = null,
) : Monitoring {

    private val profiles = sampler?.let { ProfileStarter({ enabled }, it, ProfileGate(), ::startProfile) }

    override val enabled: Boolean get() = isEnabled()

    override fun log(line: String) {
        if (enabled) safely { TracerCrashReport.log(line) }
    }

    override fun nonFatal(issue: NonFatalIssue, error: Throwable, details: String?) {
        if (!enabled || !limiter.allow(issue)) return
        val reported = if (details == null) error else ReportedError(details, error)
        safely { TracerCrashReport.report(issue.severity.toTracer(), reported, issue.key) }
    }

    override fun beginProfile(target: ProfileTarget): Profile = profiles?.begin(target) ?: NoProfile

    /** Starts the target's profiler for at most its max duration; commits with the suffix `done`. */
    private fun startProfile(target: ProfileTarget): Profile {
        var started = false
        safely {
            when (target.kind) {
                ProfilerKind.SAMPLING -> SamplingProfiler.start(target.tag, target.maxDurationMs)
                ProfilerKind.SYSTRACE -> SystraceProfiler.start(target.tag, target.maxDurationMs)
            }
            started = true
        }
        return if (started) TracerProfile(target.kind) else NoProfile
    }

    private class TracerProfile(private val kind: ProfilerKind) : Profile {
        private var cookie = 0

        /** Async sections: a sync step may resume on another thread (API 29+; none before). */
        override fun section(name: String): AutoCloseable {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return AutoCloseable { }
            val id = ++cookie
            Trace.beginAsyncSection(name, id)
            return AutoCloseable { Trace.endAsyncSection(name, id) }
        }

        override fun commit() = safely {
            when (kind) {
                ProfilerKind.SAMPLING -> SamplingProfiler.commit("done")
                ProfilerKind.SYSTRACE -> SystraceProfiler.commit("done")
            }
        }

        override fun abort() = safely {
            when (kind) {
                ProfilerKind.SAMPLING -> SamplingProfiler.abort()
                ProfilerKind.SYSTRACE -> SystraceProfiler.abort()
            }
        }
    }

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

        /** A memory snapshot now, processed and sent when the phone is idle (US3, R7). */
        fun heapDumpNow(tag: String) = safely { HeapDumps.runNow(tag) }

        /** One disk-usage check now (US4, R8). */
        fun diskCheckNow() = safely { DiskUsage.runNow() }

        /** Stops Tracer for the rest of this process (Send diagnostics turned off, R3 update). */
        fun disableNow() = safely { Tracer.disable() }

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
