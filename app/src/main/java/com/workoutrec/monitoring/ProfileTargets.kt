package com.workoutrec.monitoring

/**
 * Which runs of a [ProfileTarget] are profiled (FR-011, research R9): 1 in [ProfileTarget.oneIn],
 * or the next app start when forced from the test actions. [roll] returns 0 until n - 1.
 */
class ProfileSampler(private val store: BooleanStore, private val roll: (n: Int) -> Int) {
    fun forceNextAppStart() {}

    fun selected(target: ProfileTarget): Boolean = false

    companion object {
        const val FORCE_APP_START_KEY = "profile_next_app_start"
    }
}

/** One profile at a time (research R9). */
class ProfileGate {
    fun tryAcquire(): Boolean = false

    fun release() {}
}

/** Starts a profile when monitoring is on, the run is selected and no other profile runs. */
class ProfileStarter(
    private val enabled: () -> Boolean,
    private val sampler: ProfileSampler,
    private val gate: ProfileGate,
    private val start: (ProfileTarget) -> Profile,
) {
    fun begin(target: ProfileTarget): Profile = NoProfile
}

/** A profile from entering a screen until it is first shown; leaving earlier drops it. */
class ScreenProfile(private val begin: () -> Profile) {
    fun entered() {}

    fun shown() {}

    fun left() {}
}
