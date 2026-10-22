package com.workoutrec.monitoring

import com.workoutrec.google.ApiError
import java.io.IOException

/** Feature 005 research R1: offline, Google briefly unavailable, or a network exception. */
fun Throwable.isNoConnection(): Boolean =
    this is ApiError.Offline || this is ApiError.ServiceUnavailable || this is IOException

/**
 * Reports a handled failure of [operation]: no-connection failures as one info issue
 * ([NonFatalIssue.NO_CONNECTION]), everything else under [issue] (contracts/issues.md).
 */
fun Monitoring.failure(issue: NonFatalIssue, error: Throwable, operation: String, details: String? = null) {
    if (error.isNoConnection()) {
        nonFatal(NonFatalIssue.NO_CONNECTION, reportable(error), "operation=$operation")
    } else {
        nonFatal(issue, reportable(error), listOfNotNull("operation=$operation", details).joinToString("; "))
    }
}

/** Research R10: Google's error text can quote cell values, so only the code and status are sent. */
fun reportable(error: Throwable): Throwable = when (error) {
    is ApiError.Unexpected -> ReportedApiError(listOfNotNull("HTTP ${error.code}", error.status).joinToString(" "), error)
    is ApiError.AccessDenied -> ReportedApiError(listOfNotNull("Access denied", error.status).joinToString(" "), error)
    is ApiError.AppsScriptApiDisabled -> ReportedApiError("Apps Script API disabled", error)
    else -> error
}

/** A Google error without Google's text; keeps where it was thrown. */
class ReportedApiError(message: String, original: Throwable) : Exception(message) {
    init {
        stackTrace = original.stackTrace
    }
}

/** A value the app could not read and replaced with a default; names the value, never its content. */
class BadValue(val value: String) : Exception("Unreadable $value")
