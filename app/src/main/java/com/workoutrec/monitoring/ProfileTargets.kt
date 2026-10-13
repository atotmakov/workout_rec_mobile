package com.workoutrec.monitoring

import java.util.concurrent.atomic.AtomicBoolean

/**
 * Which runs of a [ProfileTarget] are profiled (FR-011, research R9): 1 in [ProfileTarget.oneIn],
 * or the next app start when forced from the test actions. [roll] returns 0 until n - 1.
 */
class ProfileSampler(private val store: BooleanStore, private val roll: (n: Int) -> Int) {
    /** Kept across restarts: the start to profile is the next one. */
    fun forceNextAppStart() = store.put(FORCE_APP_START_KEY, true)

    fun selected(target: ProfileTarget): Boolean {
        if (target == ProfileTarget.APP_START && store.get(FORCE_APP_START_KEY, false)) {
            store.put(FORCE_APP_START_KEY, false)
            return true
        }
        return roll(target.oneIn) == 0
    }

    companion object {
        const val FORCE_APP_START_KEY = "profile_next_app_start"
    }
}

/** One profile at a time (research R9). */
class ProfileGate {
    private val busy = AtomicBoolean(false)

    fun tryAcquire(): Boolean = busy.compareAndSet(false, true)

    fun release() = busy.set(false)
}

/** Starts a profile when monitoring is on, the run is selected and no other profile runs. */
class ProfileStarter(
    private val enabled: () -> Boolean,
    private val sampler: ProfileSampler,
    private val gate: ProfileGate,
    private val start: (ProfileTarget) -> Profile,
) {
    fun begin(target: ProfileTarget): Profile {
        if (!enabled() || !sampler.selected(target) || !gate.tryAcquire()) return NoProfile
        return GatedProfile(start(target), gate)
    }

    /** Frees the gate when the profile ends, once. */
    private class GatedProfile(private val inner: Profile, private val gate: ProfileGate) : Profile {
        private val ended = AtomicBoolean(false)

        override fun section(name: String): AutoCloseable = inner.section(name)

        override fun commit() {
            if (ended.compareAndSet(false, true)) {
                inner.commit()
                gate.release()
            }
        }

        override fun abort() {
            if (ended.compareAndSet(false, true)) {
                inner.abort()
                gate.release()
            }
        }
    }
}

/** A profile from entering a screen until it is first shown; leaving earlier drops it. */
class ScreenProfile(private val begin: () -> Profile) {
    private var profile: Profile? = null

    fun entered() {
        profile = begin()
    }

    fun shown() {
        profile?.commit()
        profile = null
    }

    fun left() {
        profile?.abort()
        profile = null
    }
}
