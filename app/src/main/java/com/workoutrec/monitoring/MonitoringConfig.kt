package com.workoutrec.monitoring

/** One Tracer module and its settings (research R2, R6–R9). */
sealed interface TracerModule {
    /** The GitHub APK is a debug build, which Tracer does not upload from by default. */
    data class Core(val debugUpload: Boolean) : TracerModule
    data class CrashReport(val sendAnr: Boolean, val nonFatalSoftLimit: Boolean) : TracerModule
    data object CrashFree : TracerModule
    data object HeapDumps : TracerModule
    data class DiskUsage(val interestingSizeBytes: Long, val probability: Long) : TracerModule
    data object SamplingProfiler : TracerModule
    data object SystraceProfiler : TracerModule
}

/** What to configure at process start, and whether to drop reports stored but not sent. */
data class ConfigPlan(val modules: List<TracerModule>, val deleteStoredReports: Boolean)

/** Research R3: the switch decides the configuration list read once per process. */
object MonitoringConfig {
    fun plan(tokenPresent: Boolean, sendDiagnostics: Boolean): ConfigPlan = ConfigPlan(emptyList(), false)
}
