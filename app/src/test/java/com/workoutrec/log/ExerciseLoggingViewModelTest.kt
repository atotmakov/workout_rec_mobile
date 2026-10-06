package com.workoutrec.log

import com.workoutrec.workout.DisplaySet
import com.workoutrec.workout.InvalidReason
import com.workoutrec.workout.Reps
import com.workoutrec.workout.SetKey
import com.workoutrec.workout.SetRef
import com.workoutrec.workout.SyncState
import com.workoutrec.workout.Weight
import java.time.LocalDateTime
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

// US1 acceptance scenarios 2-4, 6; FR-003, FR-004, FR-005, FR-006
@OptIn(ExperimentalCoroutinesApi::class)
class ExerciseLoggingViewModelTest {

    private val zone = ZoneOffset.UTC
    private fun t(local: String) = LocalDateTime.parse(local).toEpochSecond(zone)
    private var nowMillis = t("2026-01-12T18:30:00") * 1000

    private class FakeLoggingStore : LoggingStore {
        override val displaySets = MutableStateFlow<List<DisplaySet>>(emptyList())
        val confirmed = mutableListOf<SetKey>()
        override suspend fun confirmSet(key: SetKey): String {
            confirmed += key
            val id = "p${confirmed.size}"
            displaySets.value = displaySets.value + DisplaySet(key, SyncState.NOT_SYNCED, SetRef.Pending(id))
            return id
        }
    }

    private val store = FakeLoggingStore()
    private var syncRequests = 0

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun vm(exercise: String = "squat") = ExerciseLoggingViewModel(
        exercise = exercise,
        store = store,
        requestSync = { syncRequests++ },
        clockMillis = { nowMillis },
        zone = { zone },
    )

    private fun key(time: String, exercise: String = "squat", kg: Int = 20, reps: Int = 10) =
        SetKey(t(time), exercise, Weight.ofHundredths(kg * 100L), Reps(reps))

    @Test
    fun `planning creates that many empty set rows, 1 to 20`() = runTest {
        val vm = vm()
        vm.plan(3)
        assertEquals(3, vm.state.value.rows.size)
        assertFalse(vm.state.value.rows[0].canConfirm)
        vm.plan(25)
        assertEquals(20, vm.state.value.rows.size)
        vm.plan(0)
        assertEquals(1, vm.state.value.rows.size)
    }

    @Test
    fun `a valid row can be confirmed and is saved at once with the confirm time`() = runTest {
        val vm = vm()
        vm.plan(2)
        vm.setWeight(0, "17,5")
        vm.setReps(0, "15")
        assertTrue(vm.state.value.rows[0].canConfirm)
        vm.confirm(0)
        assertEquals(listOf(SetKey(t("2026-01-12T18:30:00"), "squat", Weight.ofHundredths(1750), Reps(15))), store.confirmed)
        assertEquals(1, syncRequests)
        // The confirmed row moves to the done list; one planned row is left.
        assertEquals(1, vm.state.value.rows.size)
        assertEquals(listOf(SyncState.NOT_SYNCED), vm.state.value.done.map { it.syncState })
    }

    @Test
    fun `invalid values block confirming and say why`() = runTest {
        val vm = vm()
        vm.plan(1)
        vm.setWeight(0, "-1")
        vm.setReps(0, "0")
        val row = vm.state.value.rows[0]
        assertFalse(row.canConfirm)
        assertEquals(InvalidReason.NEGATIVE, row.weightError)
        assertEquals(InvalidReason.TOO_SMALL, row.repsError)
        vm.confirm(0)
        assertTrue(store.confirmed.isEmpty())
    }

    @Test
    fun `steppers change weight by half a kilo and reps by one`() = runTest {
        val vm = vm()
        vm.plan(1)
        vm.setWeight(0, "17,5")
        vm.setReps(0, "10")
        vm.stepWeight(0, +1)
        vm.stepReps(0, -1)
        assertEquals("18", vm.state.value.rows[0].weightText)
        assertEquals("9", vm.state.value.rows[0].repsText)
        // From an empty field the stepper starts at the minimum.
        vm.plan(1)
        vm.stepWeight(0, +1)
        vm.stepReps(0, +1)
        assertEquals("0.5", vm.state.value.rows[0].weightText.replace(',', '.'))
        assertEquals("2", vm.state.value.rows[0].repsText)
    }

    @Test
    fun `add set copies the previous row`() = runTest {
        val vm = vm()
        vm.plan(1)
        vm.setWeight(0, "40")
        vm.setReps(0, "8")
        vm.addSet()
        assertEquals(listOf("40", "40"), vm.state.value.rows.map { it.weightText })
        assertEquals(listOf("8", "8"), vm.state.value.rows.map { it.repsText })
    }

    @Test
    fun `add set after all planned sets are done copies the last done set`() = runTest {
        val vm = vm()
        vm.plan(1)
        vm.setWeight(0, "40")
        vm.setReps(0, "8")
        vm.confirm(0)
        vm.addSet()
        assertEquals("40", vm.state.value.rows.single().weightText)
        assertEquals("8", vm.state.value.rows.single().repsText)
    }

    @Test
    fun `two sets confirmed in the same second get different times`() = runTest {
        val vm = vm()
        vm.plan(2)
        repeat(2) { vm.setWeight(it, "20"); vm.setReps(it, "10") }
        vm.confirm(0)
        vm.confirm(0)
        assertEquals(listOf(t("2026-01-12T18:30:00"), t("2026-01-12T18:30:01")), store.confirmed.map { it.time })
    }

    @Test
    fun `today's earlier sets of the exercise are shown as done`() = runTest {
        store.displaySets.value = listOf(
            DisplaySet(key("2026-01-11T18:00:00"), SyncState.SYNCED, SetRef.Row(1, key("2026-01-11T18:00:00"))),
            DisplaySet(key("2026-01-12T18:00:00"), SyncState.SYNCED, SetRef.Row(2, key("2026-01-12T18:00:00"))),
            DisplaySet(key("2026-01-12T18:05:00", exercise = "bench"), SyncState.SYNCED, SetRef.Row(3, key("2026-01-12T18:05:00", "bench"))),
        )
        val vm = vm()
        assertEquals(listOf(key("2026-01-12T18:00:00")), vm.state.value.done.map { it.key })
    }

    @Test
    fun `unconfirmed rows are never saved`() = runTest {
        val vm = vm()
        vm.plan(3)
        vm.setWeight(0, "20")
        vm.setReps(0, "10")
        assertTrue(store.confirmed.isEmpty())
        assertEquals(0, syncRequests)
    }
}
