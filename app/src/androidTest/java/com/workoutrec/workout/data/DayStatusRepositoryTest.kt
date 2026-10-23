package com.workoutrec.workout.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.workoutrec.sync.StatusCommit
import com.workoutrec.workout.DayStatus
import com.workoutrec.workout.KnownStatus
import com.workoutrec.workout.PendingStatus
import com.workoutrec.workout.Reps
import com.workoutrec.workout.SetKey
import com.workoutrec.workout.Weight
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

// Feature 006 data-model "Writes", "Waiting count"; spec US1, FR-004, FR-009, FR-010
@RunWith(AndroidJUnit4::class)
class DayStatusRepositoryTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: WorkoutDatabase
    private lateinit var repository: WorkoutRepository
    private var now = 1_000L

    private val oct20 = LocalDate.of(2026, 10, 20)

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, WorkoutDatabase::class.java).build()
        repository = WorkoutRepository(db.dao(), clock = { now++ })
    }

    @After
    fun tearDown() = db.close()

    private fun setOn(date: LocalDate) = SetKey(date.atTime(18, 0).toEpochSecond(ZoneOffset.UTC), "squat", Weight.ofHundredths(2000), Reps(10))

    private suspend fun cache(vararg statuses: KnownStatus) =
        repository.commit(emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyMap(), emptyList(), StatusCommit(cached = statuses.toList()))

    @Test
    fun aChoiceWaitsAndRemembersTheSheetValue() = runTest {
        cache(KnownStatus(oct20, DayStatus.PAID, false))
        repository.setStatus(oct20, DayStatus.FREE)
        assertEquals(listOf(KnownStatus(oct20, DayStatus.FREE, pending = true)), repository.statuses.first())
        assertEquals(listOf(PendingStatus(oct20, DayStatus.FREE, lastSeen = DayStatus.PAID)), repository.statusesNow())
    }

    @Test
    fun severalChoicesKeepOneRowWithTheLastStatus() = runTest {
        repository.setStatus(oct20, DayStatus.FREE)
        repository.setStatus(oct20, DayStatus.PAID)
        repository.setStatus(oct20, DayStatus.FREE)
        assertEquals(listOf(PendingStatus(oct20, DayStatus.FREE, lastSeen = null)), repository.statusesNow())
    }

    @Test
    fun aWaitingStatusCountsOnlyOnceItsDayHasASet() = runTest {
        repository.setStatus(oct20, DayStatus.FREE)
        assertEquals(0, repository.waitingCount.first())
        repository.confirmSet(setOn(oct20))
        // The set and the status.
        assertEquals(2, repository.waitingCount.first())
    }

    @Test
    fun aSyncResultMarksWrittenStatusesAndReplacesTheCache() = runTest {
        repository.setStatus(oct20, DayStatus.FREE)
        repository.setStatus(oct20.plusDays(1), DayStatus.PAID)
        repository.commit(
            emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyMap(), emptyList(),
            StatusCommit(synced = listOf(oct20), dropped = listOf(oct20.plusDays(1)), cached = listOf(KnownStatus(oct20, DayStatus.FREE, false), KnownStatus(oct20.plusDays(1), DayStatus.FREE, false))),
        )
        assertEquals(
            listOf(KnownStatus(oct20, DayStatus.FREE, false), KnownStatus(oct20.plusDays(1), DayStatus.FREE, false)),
            repository.statuses.first(),
        )
        assertEquals(emptyList<PendingStatus>(), repository.statusesNow())
    }

    // Feature 006 research R2, spec US2, FR-003, FR-015: the default is fixed when a day starts
    @Test
    fun theFirstSetOfADayStoresThePreviousDaysStatus() = runTest {
        cache(KnownStatus(oct20.minusDays(2), DayStatus.FREE, false), KnownStatus(oct20.minusDays(1), DayStatus.PAID, false))
        repository.confirmSet(setOn(oct20))
        assertEquals(listOf(PendingStatus(oct20, DayStatus.PAID, lastSeen = null)), repository.statusesNow())
    }

    @Test
    fun theFirstWorkoutEverIsFree() = runTest {
        repository.confirmSet(setOn(oct20))
        assertEquals(listOf(PendingStatus(oct20, DayStatus.FREE, lastSeen = null)), repository.statusesNow())
    }

    @Test
    fun laterSetsAndAChosenStatusAreKept() = runTest {
        repository.confirmSet(setOn(oct20))
        repository.setStatus(oct20, DayStatus.PAID)
        repository.confirmSet(setOn(oct20))
        assertEquals(listOf(PendingStatus(oct20, DayStatus.PAID, lastSeen = null)), repository.statusesNow())
    }

    @Test
    fun aDayAlreadyInTheSheetKeepsItsStatus() = runTest {
        cache(KnownStatus(oct20.minusDays(1), DayStatus.PAID, false), KnownStatus(oct20, DayStatus.FREE, false))
        repository.confirmSet(setOn(oct20))
        assertEquals(emptyList<PendingStatus>(), repository.statusesNow())
    }

    @Test
    fun changingAnEarlierDayDoesNotChangeALaterOne() = runTest {
        repository.confirmSet(setOn(oct20))
        repository.confirmSet(setOn(oct20.plusDays(1)))
        repository.setStatus(oct20, DayStatus.PAID)
        assertEquals(DayStatus.FREE, repository.statuses.first().single { it.date == oct20.plusDays(1) }.status)
    }

    @Test
    fun theDayIsTheSpreadsheetsDay() = runTest {
        val moscow = WorkoutRepository(db.dao(), clock = { now++ }, zone = flowOf(ZoneOffset.ofHours(3)))
        // 22:30 UTC on Oct 20 is 01:30 on Oct 21 in the spreadsheet's time zone.
        moscow.confirmSet(SetKey(oct20.atTime(22, 30).toEpochSecond(ZoneOffset.UTC), "squat", Weight.ofHundredths(2000), Reps(10)))
        assertEquals(listOf(oct20.plusDays(1)), moscow.statusesNow().map { it.date })
    }

    @Test
    fun aSavedPastWorkoutStoresTheDefaultForItsDate() = runTest {
        cache(KnownStatus(oct20.minusDays(1), DayStatus.PAID, false))
        repository.confirmSets(listOf(setOn(oct20), setOn(oct20)))
        assertEquals(listOf(PendingStatus(oct20, DayStatus.PAID, lastSeen = null)), repository.statusesNow())
    }
}
