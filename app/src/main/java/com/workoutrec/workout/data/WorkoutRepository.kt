package com.workoutrec.workout.data

import com.workoutrec.workout.ChangeKind
import com.workoutrec.workout.DisplayModel
import com.workoutrec.workout.DisplaySet
import com.workoutrec.workout.Exercise
import com.workoutrec.workout.LogRow
import com.workoutrec.workout.PendingChange
import com.workoutrec.workout.Reps
import com.workoutrec.workout.SetKey
import com.workoutrec.workout.Weight
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/** The phone's workout data for screens and sync (data-model.md). */
class WorkoutRepository(
    private val dao: WorkoutDao,
    private val clock: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {

    val exercises: Flow<List<Exercise>> = dao.exercises().map { list -> list.map { Exercise(it.name, it.muscleGroup) } }

    val pending: Flow<List<PendingChange>> = dao.pending().map { list -> list.map { it.toDomain() } }

    /** Cached log with pending changes applied (research R9). */
    val displaySets: Flow<List<DisplaySet>> =
        combine(dao.logRows(), pending) { rows, changes -> DisplayModel.sets(rows.map { it.toDomain() }, changes) }

    /** Saves a confirmed set before any network activity (FR-005). Returns its pending ID. */
    suspend fun confirmSet(key: SetKey): String {
        val id = newId()
        dao.upsertPending(PendingChange(id, ChangeKind.NEW, key, createdAt = clock()).toEntity())
        return id
    }

    suspend fun replaceCaches(exercises: List<Exercise>, rows: List<LogRow>) =
        dao.replaceCaches(exercises.toEntities(), rows.map { it.toEntity() })

    suspend fun clearAll() = dao.clearAll()
}

internal fun List<Exercise>.toEntities(): List<ExerciseEntity> =
    distinctBy { it.name }.mapIndexed { i, e -> ExerciseEntity(e.name, e.muscleGroup, i) }

internal fun LogRowEntity.toDomain() = LogRow(rowIndex, SetKey(time, exercise, Weight.ofHundredths(weightHundredths), Reps(reps)))

internal fun LogRow.toEntity() =
    LogRowEntity(rowIndex, key.time, key.exercise, key.weight.hundredths, key.reps.value)

internal fun PendingChangeEntity.toDomain(): PendingChange {
    val lastSeen = if (lastSeenTime != null && lastSeenExercise != null && lastSeenWeightHundredths != null && lastSeenReps != null) {
        SetKey(lastSeenTime, lastSeenExercise, Weight.ofHundredths(lastSeenWeightHundredths), Reps(lastSeenReps))
    } else {
        null
    }
    return PendingChange(
        id = id,
        kind = ChangeKind.valueOf(kind),
        key = SetKey(time, exercise, Weight.ofHundredths(weightHundredths), Reps(reps)),
        lastSeen = lastSeen,
        rowHint = rowHint,
        createdAt = createdAt,
        draftId = draftId,
    )
}

internal fun PendingChange.toEntity() = PendingChangeEntity(
    id = id,
    kind = kind.name,
    time = key.time,
    exercise = key.exercise,
    weightHundredths = key.weight.hundredths,
    reps = key.reps.value,
    lastSeenTime = lastSeen?.time,
    lastSeenExercise = lastSeen?.exercise,
    lastSeenWeightHundredths = lastSeen?.weight?.hundredths,
    lastSeenReps = lastSeen?.reps?.value,
    rowHint = rowHint,
    createdAt = createdAt,
    draftId = draftId,
)
