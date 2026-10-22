package com.workoutrec.auth

import com.workoutrec.monitoring.Monitoring
import com.workoutrec.monitoring.NonFatalIssue
import kotlinx.coroutines.CancellationException

/** Clears saved credentials on sign-out; a failure never stops sign-out (feature 005 US1). */
class SignOutCleanup(
    private val clear: suspend () -> Unit,
    private val monitoring: Monitoring,
) {
    suspend operator fun invoke() {
        try {
            clear()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            monitoring.nonFatal(NonFatalIssue.SIGN_OUT_CLEANUP_FAILED, e)
        }
    }
}
