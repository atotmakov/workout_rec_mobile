package com.workoutrec.log

import com.workoutrec.workout.DisplaySet
import com.workoutrec.workout.Record
import com.workoutrec.workout.Reps
import com.workoutrec.workout.SetKey
import com.workoutrec.workout.SetRef
import com.workoutrec.workout.SyncState
import com.workoutrec.workout.Weight
import java.time.LocalDate
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

// US3 acceptance scenarios 1-4, 6; FR-011, FR-012; SC-001 (pre-filled set = one tap, analysis C1)
@OptIn(ExperimentalCoroutinesApi::class)
class ExerciseLoggingPrefillTest {

    private val zone = ZoneOffset.UTC
    private fun t(local: String) = LocalDateTime.parse(local).toEpochSecond(zone)
    private fun key(time: String, kg: Int, reps: Int) = SetKey(t(time), "squat", Weight.ofHundredths(kg * 100L), Reps(reps))

    private val lastTime = listOf(key("2026-01-09T18:00:00", 40, 6), key("2026-01-09T18:03:00", 40, 8), key("2026-01-09T18:06:00", 35, 12))

    private val store = object : LoggingStore {
        override val displaySets = MutableStateFlow(lastTime.map { DisplaySet(it, SyncState.SYNCED, SetRef.Row(1, it)) })
        val confirmed = mutableListOf<SetKey>()
        override suspend fun confirmSet(key: SetKey): String {
            confirmed += key
            return "p"
        }
    }

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun vm() = ExerciseLoggingViewModel(
        exercise = "squat",
        store = store,
        requestSync = {},
        clockMillis = { t("2026-01-12T18:30:00") * 1000 },
        zone = { zone },
    )

    @Test
    fun `last time, the record and the suggested set count are shown`() = runTest {
        val state = vm().state.value
        assertEquals(LocalDate.of(2026, 1, 9), state.lastTime!!.date)
        assertEquals(lastTime, state.lastTime!!.sets)
        assertEquals(Record(Weight.ofHundredths(4000), Reps(8)), state.record)
        assertEquals(3, state.suggestedSetCount)
    }

    @Test
    fun `planned rows are pre-filled from last time and marked as suggestions`() = runTest {
        val vm = vm()
        vm.plan(4)
        val rows = vm.state.value.rows
        assertEquals(listOf("40", "40", "35", "35"), rows.map { it.weightText })
        assertEquals(listOf("6", "8", "12", "12"), rows.map { it.repsText })
        assertTrue(rows.all { it.weightSuggested && it.repsSuggested && it.canConfirm })
    }

    @Test
    fun `a pre-filled set is saved with one tap`() = runTest {
        val vm = vm()
        vm.plan(3)
        vm.confirm(0)
        assertEquals(listOf(Pair(Weight.ofHundredths(4000), Reps(6))), store.confirmed.map { it.weight to it.reps })
    }

    @Test
    fun `changing a value makes only that value an entered one`() = runTest {
        val vm = vm()
        vm.plan(2)
        vm.setWeight(0, "42,5")
        vm.stepReps(1, +1)
        val rows = vm.state.value.rows
        assertFalse(rows[0].weightSuggested)
        assertTrue(rows[0].repsSuggested)
        assertTrue(rows[1].weightSuggested)
        assertFalse(rows[1].repsSuggested)
        assertEquals("9", rows[1].repsText)
    }

    @Test
    fun `without history the rows are empty and three sets are suggested`() = runTest {
        store.displaySets.value = emptyList()
        val vm = vm()
        assertEquals(null, vm.state.value.lastTime)
        assertEquals(null, vm.state.value.record)
        assertEquals(3, vm.state.value.suggestedSetCount)
        vm.plan(3)
        assertTrue(vm.state.value.rows.all { it.weightText.isEmpty() && !it.weightSuggested })
    }
}
