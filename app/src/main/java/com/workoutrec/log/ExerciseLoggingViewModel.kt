package com.workoutrec.log

import androidx.lifecycle.ViewModel
import com.workoutrec.workout.DisplaySet
import com.workoutrec.workout.InvalidReason
import com.workoutrec.workout.SetKey
import com.workoutrec.workout.SyncState
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** What the logging screen needs from the phone data. */
interface LoggingStore {
    val displaySets: Flow<List<DisplaySet>>
    suspend fun confirmSet(key: SetKey): String
}

/** A set row being filled (contracts/screens.md "Exercise logging"). */
data class SetRowState(
    val weightText: String,
    val repsText: String,
    val weightError: InvalidReason?,
    val repsError: InvalidReason?,
    val canConfirm: Boolean,
)

/** A set of this exercise already logged today. */
data class DoneSet(val key: SetKey, val syncState: SyncState)

data class LoggingState(
    val exercise: String,
    val done: List<DoneSet>,
    val rows: List<SetRowState>,
)

/** Logging sets of one exercise (US1; FR-003–FR-006). */
class ExerciseLoggingViewModel(
    private val exercise: String,
    private val store: LoggingStore,
    private val requestSync: () -> Unit,
    private val clockMillis: () -> Long = System::currentTimeMillis,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
) : ViewModel() {

    val state: StateFlow<LoggingState> get() = TODO()

    fun plan(count: Int): Unit = TODO()
    fun addSet(): Unit = TODO()
    fun setWeight(index: Int, text: String): Unit = TODO()
    fun setReps(index: Int, text: String): Unit = TODO()
    fun stepWeight(index: Int, direction: Int): Unit = TODO()
    fun stepReps(index: Int, direction: Int): Unit = TODO()
    fun confirm(index: Int): Unit = TODO()
}
