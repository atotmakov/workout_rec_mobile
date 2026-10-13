package com.workoutrec.fakes

import com.workoutrec.monitoring.Monitoring
import com.workoutrec.monitoring.NonFatalIssue
import com.workoutrec.monitoring.Profile
import com.workoutrec.monitoring.ProfileTarget

/** Records what the app would send to monitoring. */
class FakeMonitoring(override var enabled: Boolean = true) : Monitoring {
    data class NonFatal(val issue: NonFatalIssue, val error: Throwable, val details: String?)

    class FakeProfile(val target: ProfileTarget) : Profile {
        val sections = mutableListOf<String>()
        var committed = false
            private set
        var aborted = false
            private set

        override fun section(name: String): AutoCloseable {
            sections += name
            return AutoCloseable { }
        }

        override fun commit() {
            if (!committed && !aborted) committed = true
        }

        override fun abort() {
            if (!committed && !aborted) aborted = true
        }
    }

    val logs = mutableListOf<String>()
    val nonFatals = mutableListOf<NonFatal>()
    val profiles = mutableListOf<FakeProfile>()

    override fun log(line: String) {
        logs += line
    }

    override fun nonFatal(issue: NonFatalIssue, error: Throwable, details: String?) {
        nonFatals += NonFatal(issue, error, details)
    }

    override fun beginProfile(target: ProfileTarget): Profile = FakeProfile(target).also { profiles += it }
}
