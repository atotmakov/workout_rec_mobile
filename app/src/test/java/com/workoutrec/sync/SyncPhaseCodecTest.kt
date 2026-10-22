package com.workoutrec.sync

import org.junit.Assert.assertEquals
import org.junit.Test

// Feature 005 US3: the saved sync phase; an unreadable value falls back and is named
class SyncPhaseCodecTest {

    private val bad = mutableListOf<String>()

    @Test
    fun `an unknown failure reason reads as OTHER and is reported`() {
        assertEquals(SyncPhase.Failing(FailReason.OTHER), SyncPhaseCodec.decode("failing:BOGUS") { bad += it })
        assertEquals(listOf("sync-phase"), bad)
    }

    @Test
    fun `valid values round-trip without reports`() {
        val phases = listOf(SyncPhase.Idle, SyncPhase.NeedsSignIn) + FailReason.entries.map { SyncPhase.Failing(it) }
        phases.forEach { assertEquals(it, SyncPhaseCodec.decode(SyncPhaseCodec.encode(it)) { v -> bad += v }) }
        assertEquals(SyncPhase.Idle, SyncPhaseCodec.decode(null) { bad += it })
        assertEquals(SyncPhase.Idle, SyncPhaseCodec.decode(SyncPhaseCodec.encode(SyncPhase.Running)) { bad += it })
        assertEquals(emptyList<String>(), bad)
    }
}
