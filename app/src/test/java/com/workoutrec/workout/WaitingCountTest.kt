package com.workoutrec.workout

import org.junit.Assert.assertEquals
import org.junit.Test

// Feature 004 FR-012, research R10: the sync icon and the sign-out warning count every waiting change
class WaitingCountTest {

    private fun set(id: String, draftId: String? = null) =
        PendingChange(id, ChangeKind.NEW, SetKey(1, "squat", Weight.ofHundredths(2000), Reps(10)), createdAt = 1, draftId = draftId)

    @Test
    fun countsSetsExerciseChangesAndPayments() =
        assertEquals(6, DisplayModel.waitingCount(listOf(set("a"), set("b"), set("c")), exerciseChanges = 2, payments = 1))

    @Test
    fun setsOfAnUnsavedPastWorkoutDoNotCount() =
        assertEquals(1, DisplayModel.waitingCount(listOf(set("a"), set("b", draftId = "d")), exerciseChanges = 0, payments = 0))

    @Test
    fun nothingWaiting() = assertEquals(0, DisplayModel.waitingCount(emptyList(), exerciseChanges = 0, payments = 0))
}
