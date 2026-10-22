package com.workoutrec.sync

/** How [SyncPhase] is saved (feature 002); an unreadable saved value is named (feature 005 US3). */
object SyncPhaseCodec {

    fun encode(phase: SyncPhase): String = when (phase) {
        SyncPhase.Idle -> "idle"
        SyncPhase.Running -> "running"
        SyncPhase.NeedsSignIn -> "sign_in"
        is SyncPhase.Failing -> "failing:${phase.reason.name}"
    }

    fun decode(value: String?, onBadValue: (String) -> Unit = {}): SyncPhase = when {
        value == null || value == "idle" -> SyncPhase.Idle
        value == "running" -> SyncPhase.Idle // a run cannot survive the process
        value == "sign_in" -> SyncPhase.NeedsSignIn
        value.startsWith("failing:") -> SyncPhase.Failing(reason(value.removePrefix("failing:"), onBadValue))
        else -> SyncPhase.Idle
    }

    private fun reason(name: String, onBadValue: (String) -> Unit): FailReason = try {
        FailReason.valueOf(name)
    } catch (e: IllegalArgumentException) {
        onBadValue("sync-phase")
        FailReason.OTHER
    }
}
