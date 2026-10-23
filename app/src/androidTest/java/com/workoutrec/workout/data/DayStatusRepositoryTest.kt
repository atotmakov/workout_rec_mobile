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
}
