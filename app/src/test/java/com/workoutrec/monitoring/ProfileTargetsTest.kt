package com.workoutrec.monitoring

import com.workoutrec.fakes.FakeMonitoring
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

// FR-011, data-model.md "ProfileTarget", research R9
class ProfileTargetsTest {

    private class MapStore : BooleanStore {
        val values = mutableMapOf<String, Boolean>()
        override fun get(key: String, default: Boolean) = values[key] ?: default
        override fun put(key: String, value: Boolean) {
            values[key] = value
        }
    }

    private val rolledWith = mutableListOf<Int>()
    private var nextRoll = 0
    private val store = MapStore()
    private val sampler = ProfileSampler(store) { n -> rolledWith += n; nextRoll }

    @Test
    fun `each target is profiled on its share of runs`() {
        nextRoll = 0
        ProfileTarget.entries.forEach { assertTrue(it.name, sampler.selected(it)) }
        assertEquals(listOf(3, 10, 5), rolledWith)
        nextRoll = 1
        ProfileTarget.entries.forEach { assertFalse(it.name, sampler.selected(it)) }
    }

    @Test
    fun `profile next app start selects the next app start once`() {
        nextRoll = 1
        sampler.forceNextAppStart()
        assertTrue(ProfileSampler(store) { 1 }.selected(ProfileTarget.APP_START))
        assertFalse(ProfileSampler(store) { 1 }.selected(ProfileTarget.APP_START))
    }

    @Test
    fun `forcing the app start does not select other targets`() {
        sampler.forceNextAppStart()
        nextRoll = 1
        assertFalse(sampler.selected(ProfileTarget.SYNC))
    }

    private val fake = FakeMonitoring()
    private val gate = ProfileGate()
    private var enabled = true
    private val starter = ProfileStarter({ enabled }, ProfileSampler(MapStore()) { 0 }, gate, fake::beginProfile)

    @Test
    fun `a selected run starts a profile`() {
        val profile = starter.begin(ProfileTarget.SYNC)
        assertEquals(listOf(ProfileTarget.SYNC), fake.profiles.map { it.target })
        profile.commit()
        assertTrue(fake.profiles.single().committed)
    }

    @Test
    fun `only one profile runs at a time`() {
        val first = starter.begin(ProfileTarget.SYNC)
        assertSame(NoProfile, starter.begin(ProfileTarget.TODAY_OPEN))
        first.abort()
        starter.begin(ProfileTarget.TODAY_OPEN)
        assertEquals(listOf(ProfileTarget.SYNC, ProfileTarget.TODAY_OPEN), fake.profiles.map { it.target })
    }

    @Test
    fun `ending a profile twice frees the gate once`() {
        val first = starter.begin(ProfileTarget.SYNC)
        first.commit()
        first.abort()
        starter.begin(ProfileTarget.SYNC)
        assertSame(NoProfile, starter.begin(ProfileTarget.SYNC))
    }

    @Test
    fun `nothing is profiled with monitoring off`() {
        enabled = false
        assertSame(NoProfile, starter.begin(ProfileTarget.SYNC))
        assertTrue(fake.profiles.isEmpty())
    }

    @Test
    fun `a screen shown after entering commits its profile once`() {
        val screen = ScreenProfile { fake.beginProfile(ProfileTarget.TODAY_OPEN) }
        screen.entered()
        screen.shown()
        screen.shown()
        screen.left()
        assertTrue(fake.profiles.single().committed)
    }

    @Test
    fun `a screen left before it is shown drops its profile`() {
        val screen = ScreenProfile { fake.beginProfile(ProfileTarget.TODAY_OPEN) }
        screen.entered()
        screen.left()
        assertTrue(fake.profiles.single().aborted)
    }
}
