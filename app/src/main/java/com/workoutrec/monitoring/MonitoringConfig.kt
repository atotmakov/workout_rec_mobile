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
    /** R8: the app is a database and a small log; more than this means real growth. */
    const val DISK_LIMIT_BYTES = 50L * 1024 * 1024

    private val ALL = listOf(
        TracerModule.Core(debugUpload = true),
        TracerModule.CrashReport(sendAnr = true, nonFatalSoftLimit = true),
        TracerModule.CrashFree,
        TracerModule.HeapDumps,
        TracerModule.DiskUsage(interestingSizeBytes = DISK_LIMIT_BYTES, probability = 1),
        TracerModule.SamplingProfiler,
        TracerModule.SystraceProfiler,
    )

    fun plan(tokenPresent: Boolean, sendDiagnostics: Boolean): ConfigPlan = when {
        !tokenPresent -> ConfigPlan(emptyList(), deleteStoredReports = false)
        sendDiagnostics -> ConfigPlan(ALL, deleteStoredReports = false)
        else -> ConfigPlan(emptyList(), deleteStoredReports = true)
    }
}
