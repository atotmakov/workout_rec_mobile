package com.workoutrec.workout

import java.time.LocalDate
import java.time.ZoneOffset
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

    // Feature 006 data-model "Waiting count", spec FR-009: a status counts only once sync can send it
    private val oct20 = LocalDate.of(2026, 10, 20)

    private fun shown(date: LocalDate) =
        DisplaySet(SetKey(date.atTime(18, 0).toEpochSecond(ZoneOffset.UTC), "squat", Weight.ofHundredths(2000), Reps(10)), SyncState.SYNCED, SetRef.Row(1, SetKey(0, "squat", Weight.ofHundredths(2000), Reps(10))))

    @Test
    fun waitingStatusesCountOnlyForDaysWithSets() {
        val statuses = listOf(PendingStatus(oct20, DayStatus.FREE, null), PendingStatus(oct20.plusDays(1), DayStatus.PAID, null))
        assertEquals(1, DisplayModel.waitingStatuses(statuses, listOf(shown(oct20)), ZoneOffset.UTC))
        assertEquals(0, DisplayModel.waitingStatuses(statuses, emptyList(), ZoneOffset.UTC))
    }

    @Test
    fun waitingStatusesAddToTheCount() =
        assertEquals(3, DisplayModel.waitingCount(listOf(set("a")), exerciseChanges = 1, payments = 0, statuses = 1))
}
