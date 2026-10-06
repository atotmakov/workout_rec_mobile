package com.workoutrec.workout

import org.junit.Assert.assertEquals
import org.junit.Test

// FR-006 live sets; research R5 (unique seconds)
class LiveSetTimeTest {

    @Test
    fun `a live set gets the confirm time in whole seconds`() {
        assertEquals(1_000L, LiveSetTime.next(nowMillis = 1_000_999, previous = null))
        assertEquals(1_005L, LiveSetTime.next(nowMillis = 1_005_000, previous = 1_000))
    }

    @Test
    fun `a set confirmed in the same second as the previous one gets one second more`() {
        assertEquals(1_001L, LiveSetTime.next(nowMillis = 1_000_400, previous = 1_000))
    }

    @Test
    fun `times keep increasing if the clock moved back`() {
        assertEquals(2_001L, LiveSetTime.next(nowMillis = 1_500_000, previous = 2_000))
    }
}
