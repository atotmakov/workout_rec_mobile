package com.workoutrec.log

import androidx.lifecycle.ViewModel
import com.workoutrec.workout.DisplaySet
import com.workoutrec.workout.PastWorkoutError
import com.workoutrec.workout.SetKey
import com.workoutrec.workout.SetValues
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

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

/** Entering a past workout (US5; FR-006, FR-006a). The draft is kept until Save or Cancel. */
class PastWorkoutViewModel(
    private val store: PastWorkoutStore,
    private val requestSync: () -> Unit,
    private val clockMillis: () -> Long = System::currentTimeMillis,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
) : ViewModel() {

    val state: StateFlow<PastWorkoutState> get() = TODO()

    fun setup(date: LocalDate, start: LocalTime, durationMinutes: Int): PastWorkoutError? = TODO()
    fun addSet(values: SetValues): Unit = TODO()
    fun removeSet(index: Int): Unit = TODO()
    fun save(): Boolean = TODO()
    fun cancel(): Unit = TODO()
}
