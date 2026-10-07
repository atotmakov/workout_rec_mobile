package com.workoutrec.log

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.workoutrec.workout.DisplaySet
import com.workoutrec.workout.History
import com.workoutrec.workout.InvalidReason
import com.workoutrec.workout.LastTime
import com.workoutrec.workout.LiveSetTime
import com.workoutrec.workout.Parsed
import com.workoutrec.workout.Record
import com.workoutrec.workout.Reps
import com.workoutrec.workout.SetKey
import com.workoutrec.workout.SetRef
import com.workoutrec.workout.SheetTime
import com.workoutrec.workout.SyncState
import com.workoutrec.workout.Weight
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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
    /** Pre-filled from last time and not changed yet; shown in the "suggested" style (FR-012). */
    val weightSuggested: Boolean = false,
    val repsSuggested: Boolean = false,
)

/** A set of this exercise already logged today. */
data class DoneSet(val key: SetKey, val syncState: SyncState, val set: DisplaySet? = null)

data class LoggingState(
    val exercise: String,
    val done: List<DoneSet>,
    val rows: List<SetRowState>,
    val lastTime: LastTime? = null,
    val record: Record? = null,
    /** Default for the plan dialog: last time's set count, or 3 (FR-012). */
    val suggestedSetCount: Int = 3,
)

/** Logging sets of one exercise (US1, US3; FR-003–FR-006, FR-011, FR-012). Unconfirmed rows live only here (FR-003). */
class ExerciseLoggingViewModel(
    private val exercise: String,
    private val store: LoggingStore,
    private val requestSync: () -> Unit,
    private val clockMillis: () -> Long = System::currentTimeMillis,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
) : ViewModel() {

    private data class Draft(
        val weight: String,
        val reps: String,
        val weightSuggested: Boolean = false,
        val repsSuggested: Boolean = false,
    )

    private val drafts = MutableStateFlow<List<Draft>>(emptyList())

    /** The latest display sets, kept for timing, pre-fill and copying the last done set. */
    private var latestSets: List<DisplaySet> = emptyList()

    /** A plan made before the phone data arrived; applied on the first emission so it gets pre-filled. */
    private var loaded = false
    private var waitingPlan: Int? = null

    val state: StateFlow<LoggingState> = combine(store.displaySets, drafts) { sets, rows ->
        latestSets = sets
        if (!loaded) {
            loaded = true
            waitingPlan?.let { waitingPlan = null; applyPlan(it) }
        }
        val lastTime = lastTime(sets)
        LoggingState(
            exercise = exercise,
            done = doneToday(sets),
            rows = rows.map { it.toRow() },
            lastTime = lastTime,
            record = History.record(sets, exercise),
            suggestedSetCount = History.prefill(lastTime).plannedCount,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, LoggingState(exercise, emptyList(), emptyList()))

    /** Planned rows, pre-filled from last time (FR-012). */
    fun plan(count: Int) {
        if (loaded) applyPlan(count) else waitingPlan = count
    }

    private fun applyPlan(count: Int) {
        val prefill = History.prefill(lastTime(latestSets))
        drafts.value = List(count.coerceIn(MIN_SETS, MAX_SETS)) { i ->
            prefill.valuesFor(i)?.let { (weight, reps) -> Draft(weight.format(locale()), reps.value.toString(), true, true) }
                ?: Draft("", "")
        }
    }

    /** A new row copying the previous row, or the last done set when no rows are left. */
    fun addSet() {
        val previous = drafts.value.lastOrNull()
            ?: doneToday(latestSets).lastOrNull()?.let { Draft(it.key.weight.format(locale()), it.key.reps.value.toString()) }
            ?: Draft("", "")
        drafts.value = drafts.value + previous
    }

    fun setWeight(index: Int, text: String) = update(index) { it.copy(weight = text, weightSuggested = false) }

    fun setReps(index: Int, text: String) = update(index) { it.copy(reps = text, repsSuggested = false) }

    /** From an empty or invalid field the stepper starts at the minimum (0 kg, 1 rep). */
    fun stepWeight(index: Int, direction: Int) = update(index) { draft ->
        val current = (Weight.parse(draft.weight) as? Parsed.Ok)?.value ?: Weight.ZERO
        draft.copy(weight = current.step(direction).format(locale()), weightSuggested = false)
    }

    fun stepReps(index: Int, direction: Int) = update(index) { draft ->
        val current = (Reps.parse(draft.reps) as? Parsed.Ok)?.value ?: Reps(Reps.MIN)
        draft.copy(reps = current.step(direction).value.toString(), repsSuggested = false)
    }

    /** Saves the set at once with its confirm time (FR-005, FR-006) and asks for a sync. */
    fun confirm(index: Int) {
        val draft = drafts.value.getOrNull(index) ?: return
        val weight = (Weight.parse(draft.weight) as? Parsed.Ok)?.value ?: return
        val reps = (Reps.parse(draft.reps) as? Parsed.Ok)?.value ?: return
        val time = LiveSetTime.next(clockMillis(), latestSets.maxOfOrNull { it.key.time })
        drafts.value = drafts.value.filterIndexed { i, _ -> i != index }
        val key = SetKey(time, exercise, weight, reps)
        // Keep the next set's time unique even before the store emits the saved set.
        latestSets = latestSets + DisplaySet(key, SyncState.NOT_SYNCED, SetRef.Pending(""))
        viewModelScope.launch {
            store.confirmSet(key)
            requestSync()
        }
    }

    private fun update(index: Int, change: (Draft) -> Draft) {
        drafts.value = drafts.value.mapIndexed { i, draft -> if (i == index) change(draft) else draft }
    }

    private fun today() = SheetTime.workoutDay(Instant.ofEpochMilli(clockMillis()).epochSecond, zone())

    private fun lastTime(sets: List<DisplaySet>): LastTime? = History.lastTime(sets, exercise, today(), zone())

    private fun doneToday(sets: List<DisplaySet>): List<DoneSet> {
        val today = today()
        return sets.filter { it.key.exercise == exercise && SheetTime.workoutDay(it.key.time, zone()) == today }
            .sortedBy { it.key.time }
            .map { DoneSet(it.key, it.syncState, it) }
    }

    private fun Draft.toRow(): SetRowState {
        val weightError = (Weight.parse(weight) as? Parsed.Invalid)?.reason
        val repsError = (Reps.parse(reps) as? Parsed.Invalid)?.reason
        return SetRowState(
            weightText = weight,
            repsText = reps,
            // An empty field is not an error to show; it only blocks confirming.
            weightError = weightError.takeIf { it != InvalidReason.EMPTY },
            repsError = repsError.takeIf { it != InvalidReason.EMPTY },
            canConfirm = weightError == null && repsError == null,
            weightSuggested = weightSuggested,
            repsSuggested = repsSuggested,
        )
    }

    private fun locale(): Locale = Locale.getDefault()

    private companion object {
        const val MIN_SETS = 1
        const val MAX_SETS = 20
    }
}
