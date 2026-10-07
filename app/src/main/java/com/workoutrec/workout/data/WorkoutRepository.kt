package com.workoutrec.workout.data

import com.workoutrec.log.LoggingStore
import com.workoutrec.log.PastWorkoutStore
import com.workoutrec.sync.LogSyncStore
import com.workoutrec.sync.NoticeKind
import com.workoutrec.sync.SyncNotice
import com.workoutrec.workout.ChangeKind
import com.workoutrec.workout.Coalescer
import com.workoutrec.workout.DisplayModel
import com.workoutrec.workout.DisplaySet
import com.workoutrec.workout.Exercise
import com.workoutrec.workout.LogRow
import com.workoutrec.workout.PendingChange
import com.workoutrec.workout.PendingUpdate
import com.workoutrec.workout.Reps
import com.workoutrec.workout.SetKey
import com.workoutrec.workout.SetRef
import com.workoutrec.workout.SetValues
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
) : LoggingStore, LogSyncStore, PastWorkoutStore {

    val exercises: Flow<List<Exercise>> = dao.exercises().map { list -> list.map { Exercise(it.name, it.muscleGroup) } }

    val pending: Flow<List<PendingChange>> = dao.pending().map { list -> list.map { it.toDomain() } }

    /** Cached log with pending changes applied (research R9). */
    override val displaySets: Flow<List<DisplaySet>> =
        combine(dao.logRows(), pending) { rows, changes -> DisplayModel.sets(rows.map { it.toDomain() }, changes) }

    /** Saves a confirmed set before any network activity (FR-005). Returns its pending ID. */
    override suspend fun confirmSet(key: SetKey): String {
        val id = newId()
        dao.upsertPending(PendingChange(id, ChangeKind.NEW, key, createdAt = clock()).toEntity())
        return id
    }

    override suspend fun confirmSets(keys: List<SetKey>) {
        val created = clock()
        dao.insertSets(keys.mapIndexed { i, key -> PendingChange(newId(), ChangeKind.NEW, key, createdAt = created + i).toEntity() })
    }

    suspend fun replaceCaches(exercises: List<Exercise>, rows: List<LogRow>) =
        dao.replaceCaches(exercises.toEntities(), rows.map { it.toEntity() })

    suspend fun clearAll() = dao.clearAll()

    /** Sync notices with their IDs, oldest first (FR-015). */
    val notices: Flow<List<Pair<Long, SyncNotice>>> = dao.notices().map { list ->
        list.map { it.id to SyncNotice(NoticeKind.valueOf(it.kind), SetKey(it.time, it.exercise, Weight.ofHundredths(it.weightHundredths), Reps(it.reps))) }
    }

    suspend fun dismissNotice(id: Long) = dao.deleteNotice(id)

    /** Edit any set; coalesced with its pending change (FR-013, FR-013a). */
    suspend fun editSet(target: SetRef, values: SetValues) =
        apply(Coalescer.edit(target, dao.pendingNow().map { it.toDomain() }, values, newId(), clock()))

    /** Delete any set; coalesced with its pending change (FR-013, FR-013a). */
    suspend fun deleteSet(target: SetRef) =
        apply(Coalescer.delete(target, dao.pendingNow().map { it.toDomain() }, newId(), clock()))

    private suspend fun apply(update: PendingUpdate) = when (update) {
        is PendingUpdate.Upsert -> dao.upsertPending(update.change.toEntity())
        is PendingUpdate.Remove -> dao.deletePending(listOf(update.id))
    }

    override suspend fun pendingNow(): List<PendingChange> = dao.pendingNow().map { it.toDomain() }

    override suspend fun commit(exercises: List<Exercise>, rows: List<LogRow>, doneIds: Collection<String>, notices: List<SyncNotice>) {
        val created = clock()
        dao.commitSync(
            exercises.toEntities(),
            rows.map { it.toEntity() },
            doneIds.toList(),
            notices.map {
                SyncNoticeEntity(0, it.kind.name, it.key.time, it.key.exercise, it.key.weight.hundredths, it.key.reps.value, created)
            },
        )
    }

    override suspend fun keepOnlyNewSets() = dao.keepOnlyNewSets()
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
