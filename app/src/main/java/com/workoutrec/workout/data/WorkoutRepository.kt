package com.workoutrec.workout.data

import kotlinx.coroutines.flow.first
import com.workoutrec.workout.ShownExercise
import com.workoutrec.workout.ExerciseUpdate
import com.workoutrec.workout.ExerciseCoalescer
import com.workoutrec.workout.ExerciseChangeKind
import com.workoutrec.workout.ExerciseChange
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

    private val cachedExercises: Flow<List<Exercise>> = dao.exercises().map { list -> list.map { Exercise(it.name, it.muscleGroup) } }

    private val exerciseChanges: Flow<List<ExerciseChange>> = dao.exerciseChanges().map { list -> list.map { it.toDomain() } }

    val pending: Flow<List<PendingChange>> = dao.pending().map { list -> list.map { it.toDomain() } }

    /** Cached log with pending changes applied (research R9) and waiting renames (feature 004). */
    override val displaySets: Flow<List<DisplaySet>> =
        combine(dao.logRows(), pending, exerciseChanges) { rows, changes, exerciseChanges ->
            DisplayModel.sets(rows.map { it.toDomain() }, changes, DisplayModel.renames(exerciseChanges))
        }

    /** The drills list with waiting exercise changes applied (feature 004 FR-007). */
    val shownExercises: Flow<List<ShownExercise>> =
        combine(cachedExercises, exerciseChanges, displaySets) { cache, changes, sets -> DisplayModel.exercises(cache, changes, sets) }

    /** What the picker and the edit dialog offer: the shown list (FR-001, feature 004 SC-004). */
    val exercises: Flow<List<Exercise>> = shownExercises.map { list -> list.map { Exercise(it.name, it.muscleGroup) } }

    /** Sets, exercise changes and payments not in the spreadsheet yet (feature 004 FR-012). */
    val waitingCount: Flow<Int> = combine(pending, dao.exerciseChanges(), dao.pendingPayments()) { sets, exercises, payments ->
        DisplayModel.waitingCount(sets, exercises.size, payments.size)
    }

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

    // Feature 004 US3: exercise changes, saved on the phone first (FR-008, FR-008a, FR-008b).

    suspend fun addExercise(name: String, muscleGroup: String) =
        dao.upsertExerciseChange(ExerciseCoalescer.add(name, muscleGroup, newId(), clock()).toEntity())

    suspend fun renameExercise(current: String, name: String, muscleGroup: String) {
        val result = ExerciseCoalescer.rename(
            current, cachedNow(), exerciseChangesNow(), pendingNow(), name, muscleGroup, newId(), clock(),
        )
        when (val update = result.update) {
            is ExerciseUpdate.Upsert -> dao.renameExercise(update.change.toEntity(), result.sets.map { it.toEntity() })
            is ExerciseUpdate.Remove -> dao.deleteExerciseChanges(listOf(update.id))
        }
    }

    suspend fun deleteExercise(current: String) =
        when (val update = ExerciseCoalescer.delete(current, cachedNow(), exerciseChangesNow(), newId(), clock())) {
            is ExerciseUpdate.Upsert -> dao.upsertExerciseChange(update.change.toEntity())
            is ExerciseUpdate.Remove -> dao.deleteExerciseChanges(listOf(update.id))
        }

    private suspend fun cachedNow(): List<Exercise> = cachedExercises.first()

    override suspend fun exerciseChangesNow(): List<ExerciseChange> = dao.exerciseChangesNow().map { it.toDomain() }

    override suspend fun commit(
        exercises: List<Exercise>,
        rows: List<LogRow>,
        doneIds: Collection<String>,
        notices: List<SyncNotice>,
        doneExerciseIds: Collection<String>,
        renamesBack: Map<String, String>,
    ) {
        val created = clock()
        dao.commitSync(
            exercises.toEntities(),
            rows.map { it.toEntity() },
            doneIds.toList(),
            notices.map {
                SyncNoticeEntity(0, it.kind.name, it.key.time, it.key.exercise, it.key.weight.hundredths, it.key.reps.value, created)
            },
            doneExerciseIds.toList(),
            renamesBack,
        )
    }

    override suspend fun keepOnlyNewSets() = dao.keepOnlyNewSets()
}

internal fun PendingExerciseChangeEntity.toDomain() =
    ExerciseChange(id, ExerciseChangeKind.valueOf(kind), name, muscleGroup, lastSeenName, lastSeenGroup, createdAt)

internal fun ExerciseChange.toEntity() =
    PendingExerciseChangeEntity(id, kind.name, name, muscleGroup, lastSeenName, lastSeenGroup, createdAt)

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
