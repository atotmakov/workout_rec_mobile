package com.workoutrec.log

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.workoutrec.workout.DisplaySet
import com.workoutrec.workout.PastWorkoutError
import com.workoutrec.workout.PastWorkoutTimes
import com.workoutrec.workout.SetKey
import com.workoutrec.workout.SetValues
import com.workoutrec.workout.SheetTime
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What the past-workout editor needs from the phone data. */
interface PastWorkoutStore {
    val displaySets: Flow<List<DisplaySet>>

    /** Saves all sets of the workout in one transaction (research R10). */
    suspend fun confirmSets(keys: List<SetKey>)
}

/** A set of the draft with the time it will get. */
data class DraftSet(val values: SetValues, val time: Long)

data class PastWorkoutState(
    val date: LocalDate? = null,
    val dayHasSets: Boolean = false,
    val sets: List<DraftSet> = emptyList(),
)

/**
 * Entering a past workout (US5; FR-006, FR-006a). The draft lives in this ViewModel until Save or
 * Cancel; nothing reaches the phone database or the sheet before Save.
 */
class PastWorkoutViewModel(
    private val store: PastWorkoutStore,
    private val requestSync: () -> Unit,
    private val clockMillis: () -> Long = System::currentTimeMillis,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
) : ViewModel() {

    private data class Draft(val date: LocalDate, val start: Long, val durationMinutes: Int, val sets: List<SetValues>) {
        fun withTimes(): List<DraftSet> =
            sets.zip(PastWorkoutTimes.times(start, durationMinutes, sets.size)) { values, time -> DraftSet(values, time) }
    }

    private val draft = MutableStateFlow<Draft?>(null)

    val state: StateFlow<PastWorkoutState> = combine(store.displaySets, draft) { sets, d ->
        if (d == null) {
            PastWorkoutState()
        } else {
            PastWorkoutState(
                date = d.date,
                dayHasSets = sets.any { SheetTime.workoutDay(it.key.time, zone()) == d.date },
                sets = d.withTimes(),
            )
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, PastWorkoutState())

    fun setup(date: LocalDate, start: LocalTime, durationMinutes: Int): PastWorkoutError? {
        val today = Instant.ofEpochMilli(clockMillis()).atZone(zone()).toLocalDate()
        PastWorkoutTimes.validate(date, today, durationMinutes)?.let { return it }
        draft.value = Draft(date, LocalDateTime.of(date, start).atZone(zone()).toEpochSecond(), durationMinutes, emptyList())
        return null
    }

    fun addSet(values: SetValues) {
        draft.value = draft.value?.let { it.copy(sets = it.sets + values) }
    }

    fun removeSet(index: Int) {
        draft.value = draft.value?.let { d -> d.copy(sets = d.sets.filterIndexed { i, _ -> i != index }) }
    }

    /** Times are fixed now; afterwards they are never changed (FR-006). */
    fun save(): Boolean {
        val d = draft.value ?: return false
        if (d.sets.isEmpty()) return false
        val keys = d.withTimes().map { SetKey(it.time, it.values.exercise, it.values.weight, it.values.reps) }
        draft.value = null
        viewModelScope.launch {
            store.confirmSets(keys)
            requestSync()
        }
        return true
    }

    fun cancel() {
        draft.value = null
    }
}
