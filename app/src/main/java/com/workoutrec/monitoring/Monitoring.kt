package com.workoutrec.monitoring

/**
 * The app's only door to app monitoring (feature 003, research R4). Call sites depend on this
 * interface; [TracerMonitoring] is the one implementation that talks to the Tracer SDK.
 */
interface Monitoring {
    /** False when switched off, started switched off, or built without a Tracer token. */
    val enabled: Boolean

    /** A diagnostic log line for the buffer attached to the next report (FR-006). */
    fun log(line: String)

    /** A handled error, grouped under [issue] (FR-005); rate-limited by the implementation. */
    fun nonFatal(issue: NonFatalIssue, error: Throwable, details: String? = null)

    /** Starts profiling [target] if this run is selected; otherwise returns [NoProfile]. */
    fun beginProfile(target: ProfileTarget): Profile
}

/** One profiling run: commit to send it, abort to drop it. Calls after the first end are ignored. */
interface Profile {
    fun section(name: String): AutoCloseable
    fun commit()
    fun abort()
}

/** A profile that records nothing (run not selected, or monitoring off). */
object NoProfile : Profile {
    private val noSection = AutoCloseable { }
    override fun section(name: String): AutoCloseable = noSection
    override fun commit() {}
    override fun abort() {}
}

/** Monitoring off: every call does nothing. */
object NoMonitoring : Monitoring {
    override val enabled = false
    override fun log(line: String) {}
    override fun nonFatal(issue: NonFatalIssue, error: Throwable, details: String?) {}
    override fun beginProfile(target: ProfileTarget): Profile = NoProfile
}

/** Runs [block] as one [target] profile: committed when it returns, aborted when it throws. */
suspend fun <T> Monitoring.profiled(target: ProfileTarget, block: suspend (Profile) -> T): T {
    val profile = beginProfile(target)
    return try {
        block(profile).also { profile.commit() }
    } catch (e: Throwable) {
        profile.abort()
        throw e
    }
}

/** contracts/monitoring.md "Events": stable issue keys; add values, never rename. */
enum class NonFatalIssue(val key: String, val severity: IssueSeverity) {
    SYNC_FAILED("sync-failed", IssueSeverity.ERROR),
    SYNC_TIMEOUT("sync-timeout", IssueSeverity.WARNING),
    SPREADSHEET_MISMATCH("spreadsheet-mismatch", IssueSeverity.WARNING),
    TOKEN_FAILED("token-failed", IssueSeverity.ERROR),
    DIAGNOSTICS_TEST("diagnostics-test", IssueSeverity.INFO),
}

enum class IssueSeverity { ERROR, WARNING, INFO }

enum class ProfilerKind { SAMPLING, SYSTRACE }

/** data-model.md "ProfileTarget": tag, profiler, share of runs (1 in [oneIn]), max duration. */
enum class ProfileTarget(val tag: String, val kind: ProfilerKind, val oneIn: Int, val maxDurationMs: Long) {
    APP_START("app-start", ProfilerKind.SAMPLING, oneIn = 3, maxDurationMs = 10_000),
    SYNC("sync", ProfilerKind.SYSTRACE, oneIn = 10, maxDurationMs = 120_000),
    TODAY_OPEN("today-open", ProfilerKind.SYSTRACE, oneIn = 5, maxDurationMs = 5_000),
}
