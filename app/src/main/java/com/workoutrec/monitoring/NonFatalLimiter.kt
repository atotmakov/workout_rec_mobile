package com.workoutrec.monitoring

import java.time.ZoneId

/** FR-007, data-model.md "NonFatalLimiter": in memory, per process. */
class NonFatalLimiter(
    private val now: () -> Long = System::currentTimeMillis,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
) {
    fun allow(issue: NonFatalIssue): Boolean = true
}
