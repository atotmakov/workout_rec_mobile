package com.workoutrec.log

import com.workoutrec.sync.FailReason
import com.workoutrec.sync.SyncPhase
import org.junit.Assert.assertEquals
import org.junit.Test

// Redesign brief 1a: which of the three sync icons shows
class SyncIndicatorTest {

    @Test
    fun syncedWhenNothingWaits() = assertEquals(SyncIndicator.SYNCED, syncIndicator(0, SyncPhase.Idle))

    @Test
    fun waitingWhenSetsWait() = assertEquals(SyncIndicator.WAITING, syncIndicator(2, SyncPhase.Idle))

    @Test
    fun syncingWhileARunIsOn() = assertEquals(SyncIndicator.SYNCING, syncIndicator(2, SyncPhase.Running))

    @Test
    fun waitingWhenStuckEvenWithNothingPending() {
        assertEquals(SyncIndicator.WAITING, syncIndicator(0, SyncPhase.Failing(FailReason.SPREADSHEET)))
        assertEquals(SyncIndicator.WAITING, syncIndicator(0, SyncPhase.NeedsSignIn))
    }
}
