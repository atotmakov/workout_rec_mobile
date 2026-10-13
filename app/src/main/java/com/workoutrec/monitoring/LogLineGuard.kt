package com.workoutrec.monitoring

/** FR-013: a diagnostic log line is copied to monitoring only without an email or access token. */
object LogLineGuard {
    fun allows(line: String): Boolean = '@' !in line && "ya29." !in line
}
