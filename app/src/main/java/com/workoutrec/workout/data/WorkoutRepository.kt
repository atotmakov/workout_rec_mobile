package com.workoutrec.workout.data

import com.workoutrec.automation.AutomationStatus
import com.workoutrec.log.LoggingStore
import com.workoutrec.log.PastWorkoutStore
import com.workoutrec.money.MoneyError
import com.workoutrec.money.MoneyStore
import com.workoutrec.money.MoneySummary
import com.workoutrec.monitoring.Monitoring
import com.workoutrec.monitoring.NoMonitoring
import com.workoutrec.monitoring.NonFatalIssue
import com.workoutrec.monitoring.badValue
import com.workoutrec.sync.LogSyncStore
import com.workoutrec.sync.NoticeKind
import com.workoutrec.sync.StatusCommit
import com.workoutrec.sync.SyncNotice
import com.workoutrec.workout.ChangeKind
import com.workoutrec.workout.Coalescer
import com.workoutrec.workout.DayStatus
import com.workoutrec.workout.DayStatusRules
import com.workoutrec.workout.DisplayModel
import com.workoutrec.workout.DisplaySet
import com.workoutrec.workout.Exercise
import com.workoutrec.workout.ExerciseChange
import com.workoutrec.workout.ExerciseChangeKind
import com.workoutrec.workout.ExerciseCoalescer
import com.workoutrec.workout.ExerciseUpdate
import com.workoutrec.workout.KnownStatus
import com.workoutrec.workout.LogRow
import com.workoutrec.workout.PaymentRow
import com.workoutrec.workout.PendingChange
import com.workoutrec.workout.PendingPayment
import com.workoutrec.workout.PendingStatus
import com.workoutrec.workout.PendingUpdate
import com.workoutrec.workout.Reps
import com.workoutrec.workout.SetKey
import com.workoutrec.workout.SetRef
import com.workoutrec.workout.SetValues
import com.workoutrec.workout.SheetTime
import com.workoutrec.workout.ShownExercise
import com.workoutrec.workout.ShownPayment
import com.workoutrec.workout.Weight
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/** The phone's workout data for screens and sync (data-model.md). */
class WorkoutRepository(
    private val dao: WorkoutDao,
    private val clock: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
    /** Feature 005 US3: unreadable saved values are reported. */
    private val monitoring: Monitoring = NoMonitoring,
    /** Feature 006: days are the spreadsheet's days (research R8 of 002). */
    private val zone: Flow<ZoneId> = flowOf(ZoneOffset.UTC),
) : LoggingStore, LogSyncStore, PastWorkoutStore, MoneyStore {

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

    /** Feature 006: every date's known status, the phone's waiting choices included. */
    val statuses: Flow<List<KnownStatus>> = dao.statuses().map { list -> list.map { it.toKnown() } }

    private val waitingStatuses: Flow<Int> = combine(dao.statuses(), displaySets, zone) { rows, sets, zone ->
        DisplayModel.waitingStatuses(rows.filter { it.pending }.map { it.toPending() }, sets, zone)
    }

    /** Sets, exercise changes, payments and day statuses not in the spreadsheet yet (feature 004 FR-012, 006 FR-009). */
    val waitingCount: Flow<Int> = combine(pending, dao.exerciseChanges(), dao.pendingPayments(), waitingStatuses) { sets, exercises, payments, statuses ->
        DisplayModel.waitingCount(sets, exercises.size, payments.size, statuses)
    }

    /** Feature 006: the user picked [status] for [date] (Today, a day's details); it waits for sync. */
    suspend fun setStatus(date: LocalDate, status: DayStatus) = dao.chooseStatus(date.toString(), status == DayStatus.FREE, clock())

    override suspend fun statusesNow(): List<PendingStatus> = dao.statusesNow().filter { it.pending }.map { it.toPending() }

    /**
     * Saves a confirmed set before any network activity (FR-005). Returns its pending ID. Feature
     * 006: a day's first set also fixes the day's status to the default (research R2).
     */
    override suspend fun confirmSet(key: SetKey): String {
        val id = newId()
        val created = clock()
        dao.insertSetsWithDefaults(listOf(PendingChange(id, ChangeKind.NEW, key, createdAt = created).toEntity()), defaultsFor(listOf(key), created))
        return id
    }

    override suspend fun confirmSets(keys: List<SetKey>, status: DayStatus?) {
        val created = clock()
        val zone = zone.first()
        dao.insertSetsWithDefaults(
            keys.mapIndexed { i, key -> PendingChange(newId(), ChangeKind.NEW, key, createdAt = created + i).toEntity() },
            defaultsFor(keys, created),
            // Feature 006 FR-014: a past workout is one day; the dialog's choice is that day's status.
            chosen = status?.let { s ->
                keys.firstOrNull()?.let { DayStatusEntity(SheetTime.workoutDay(it.time, zone).toString(), s == DayStatus.FREE, pending = true, updatedAt = created, lastSeenFree = null) }
            },
        )
    }

    /** One default status per day of [keys]; the DAO skips days that already have one. */
    private suspend fun defaultsFor(keys: List<SetKey>, created: Long): List<DayStatusEntity> {
        val zone = zone.first()
        val known = dao.statusesNow().associate { LocalDate.parse(it.date) to if (it.free) DayStatus.FREE else DayStatus.PAID }
        return keys.map { SheetTime.workoutDay(it.time, zone) }.distinct().map { date ->
            DayStatusEntity(date.toString(), DayStatusRules.defaultFor(date, known) == DayStatus.FREE, pending = true, updatedAt = created, lastSeenFree = null)
        }
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

    // Feature 004 US4: payments and the downloaded money tab.

    /** Downloaded and waiting payments, newest first (FR-009). */
    val payments: Flow<List<ShownPayment>> = combine(dao.paymentRows(), dao.pendingPayments()) { rows, pending ->
        DisplayModel.payments(rows.map { it.toDomain() }, pending.map { it.toDomain() })
    }

    val hasPendingPayment: Flow<Boolean> = dao.pendingPayments().map { it.isNotEmpty() }

    val moneySummary: Flow<MoneySummary?> = dao.moneySummary().map { it?.toDomain(::badSavedValue) }

    private fun badSavedValue(value: String) = monitoring.badValue(NonFatalIssue.BAD_SAVED_STATE, value)

    /** Saved on the phone first, appended to the money tab by the sync (FR-009b). */
    suspend fun addPayment(date: LocalDate, workouts: Int, sumHundredths: Long) =
        dao.insertPayment(PendingPaymentEntity(newId(), date.toString(), workouts, sumHundredths, clock()))

    override suspend fun paymentsNow(): List<PendingPayment> = dao.pendingPaymentsNow().map { it.toDomain() }

    override suspend fun moneySummaryNow(): MoneySummary? = dao.moneySummaryNow()?.toDomain()

    override suspend fun replaceMoney(rows: List<PaymentRow>, summary: MoneySummary) =
        dao.replacePayments(
            rows.map { PaymentRowEntity(it.rowIndex, it.date.toString(), it.workouts, it.sumHundredths) },
            MoneySummaryEntity(1, summary.balance, summary.lastDailyRun, summary.automation.name, summary.downloadedAt, summary.error.name),
        )

    override suspend fun moneyFailed(error: MoneyError) = dao.moneyFailed(error.name)

    override suspend fun commit(
        exercises: List<Exercise>,
        rows: List<LogRow>,
        doneIds: Collection<String>,
        notices: List<SyncNotice>,
        doneExerciseIds: Collection<String>,
        renamesBack: Map<String, String>,
        donePaymentIds: Collection<String>,
        statuses: StatusCommit,
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
            donePaymentIds.toList(),
            statuses.synced.map { it.toString() },
            statuses.dropped.map { it.toString() },
            statuses.cached?.map { DayStatusEntity(it.date.toString(), it.status == DayStatus.FREE, pending = false, updatedAt = created, lastSeenFree = null) },
        )
    }

    override suspend fun keepOnlyNewSets() = dao.keepOnlyNewSets()
}

internal fun DayStatusEntity.toKnown() = KnownStatus(LocalDate.parse(date), if (free) DayStatus.FREE else DayStatus.PAID, pending)

internal fun DayStatusEntity.toPending() = PendingStatus(
    LocalDate.parse(date),
    if (free) DayStatus.FREE else DayStatus.PAID,
    lastSeenFree?.let { if (it) DayStatus.FREE else DayStatus.PAID },
)

internal fun PaymentRowEntity.toDomain() = PaymentRow(rowIndex, LocalDate.parse(date), workouts, sumHundredths)

internal fun PendingPaymentEntity.toDomain() = PendingPayment(id, LocalDate.parse(date), workouts, sumHundredths, createdAt)

internal fun MoneySummaryEntity.toDomain(onBadValue: (String) -> Unit = {}) = MoneySummary(
    balance,
    lastDailyRun,
    try {
        AutomationStatus.valueOf(automation)
    } catch (e: IllegalArgumentException) {
        onBadValue("automation-status")
        AutomationStatus.ScriptMissing
    },
    downloadedAt,
    try {
        MoneyError.valueOf(error)
    } catch (e: IllegalArgumentException) {
        onBadValue("money-error")
        MoneyError.OTHER
    },
)

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
