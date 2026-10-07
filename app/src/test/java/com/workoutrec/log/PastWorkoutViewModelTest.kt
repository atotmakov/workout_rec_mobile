package com.workoutrec.log

import com.workoutrec.workout.DisplaySet
import com.workoutrec.workout.PastWorkoutError
import com.workoutrec.workout.Reps
import com.workoutrec.workout.SetKey
import com.workoutrec.workout.SetRef
import com.workoutrec.workout.SetValues
import com.workoutrec.workout.SyncState
import com.workoutrec.workout.Weight
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

// US5 acceptance scenarios 1-4; FR-006, FR-006a
@OptIn(ExperimentalCoroutinesApi::class)
class PastWorkoutViewModelTest {

    private val zone = ZoneOffset.UTC
    private fun t(local: String) = LocalDateTime.parse(local).toEpochSecond(zone)
    private val yesterday = LocalDate.of(2026, 1, 11)

    private val store = object : PastWorkoutStore {
        override val displaySets = MutableStateFlow<List<DisplaySet>>(emptyList())
        val saved = mutableListOf<SetKey>()
        override suspend fun confirmSets(keys: List<SetKey>) {
            saved += keys
        }
    }
    private var syncRequests = 0

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun vm() = PastWorkoutViewModel(
        store = store,
        requestSync = { syncRequests++ },
        clockMillis = { t("2026-01-12T12:00:00") * 1000 },
        zone = { zone },
    )

    private fun squat(kg: Int, reps: Int) = SetValues("squat", Weight.ofHundredths(kg * 100L), Reps(reps))

    @Test
    fun `a future date or an impossible duration is refused`() = runTest {
        val vm = vm()
        assertEquals(PastWorkoutError.FUTURE_DATE, vm.setup(LocalDate.of(2026, 1, 13), LocalTime.of(18, 0), 60))
        assertEquals(PastWorkoutError.DURATION, vm.setup(yesterday, LocalTime.of(18, 0), 0))
        assertEquals(null, vm.state.value.date)
    }

    @Test
    fun `a day that already has sets is flagged`() = runTest {
        val existing = SetKey(t("2026-01-11T09:00:00"), "row", Weight.ofHundredths(3000), Reps(10))
        store.displaySets.value = listOf(DisplaySet(existing, SyncState.SYNCED, SetRef.Row(1, existing)))
        val vm = vm()
        vm.setup(yesterday, LocalTime.of(18, 0), 60)
        assertTrue(vm.state.value.dayHasSets)
    }

    @Test
    fun `sets show times spread over the workout and are recalculated`() = runTest {
        val vm = vm()
        assertEquals(null, vm.setup(yesterday, LocalTime.of(18, 0), 60))
        assertFalse(vm.state.value.dayHasSets)
        repeat(3) { vm.addSet(squat(20, 10 - it)) }
        assertEquals(listOf(t("2026-01-11T18:00:00"), t("2026-01-11T18:30:00"), t("2026-01-11T19:00:00")), vm.state.value.sets.map { it.time })
        vm.removeSet(1)
        assertEquals(listOf(t("2026-01-11T18:00:00"), t("2026-01-11T19:00:00")), vm.state.value.sets.map { it.time })
    }

    @Test
    fun `nothing is saved before Save`() = runTest {
        val vm = vm()
        vm.setup(yesterday, LocalTime.of(18, 0), 60)
        vm.addSet(squat(20, 10))
        assertTrue(store.saved.isEmpty())
        assertEquals(0, syncRequests)
    }

    @Test
    fun `save stores all sets with their times at once and syncs`() = runTest {
        val vm = vm()
        vm.setup(yesterday, LocalTime.of(18, 0), 60)
        vm.addSet(squat(20, 10))
        vm.addSet(squat(25, 8))
        assertTrue(vm.save())
        assertEquals(
            listOf(
                SetKey(t("2026-01-11T18:00:00"), "squat", Weight.ofHundredths(2000), Reps(10)),
                SetKey(t("2026-01-11T19:00:00"), "squat", Weight.ofHundredths(2500), Reps(8)),
            ),
            store.saved,
        )
        assertEquals(1, syncRequests)
        assertEquals(null, vm.state.value.date)
    }

    @Test
    fun `cancel discards the draft`() = runTest {
        val vm = vm()
        vm.setup(yesterday, LocalTime.of(18, 0), 60)
        vm.addSet(squat(20, 10))
        vm.cancel()
        assertTrue(store.saved.isEmpty())
        assertTrue(vm.state.value.sets.isEmpty())
        assertFalse(vm.save())
    }
}
