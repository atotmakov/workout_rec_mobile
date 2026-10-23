package com.workoutrec.fakes

import com.workoutrec.sync.BackgroundRun
import com.workoutrec.sync.FailReason
import com.workoutrec.sync.LogSyncStore
import com.workoutrec.sync.SheetInfoCache
import com.workoutrec.sync.StatusCommit
import com.workoutrec.sync.SyncNotice
import com.workoutrec.sync.SyncPhase
import com.workoutrec.sync.SyncStatus
import com.workoutrec.sync.SyncStatusStore
import com.workoutrec.workout.Exercise
import com.workoutrec.workout.ExerciseChange
import com.workoutrec.workout.LogRow
import com.workoutrec.workout.PendingChange
import com.workoutrec.workout.KnownStatus
import com.workoutrec.workout.PendingStatus
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** In-memory phone data for sync tests. */
class FakeLogSyncStore(
    val pending: MutableList<PendingChange> = mutableListOf(),
    val exerciseChanges: MutableList<ExerciseChange> = mutableListOf(),
    val payments: MutableList<com.workoutrec.workout.PendingPayment> = mutableListOf(),
    /** Feature 006: waiting day statuses. */
    val statuses: MutableList<PendingStatus> = mutableListOf(),
) : LogSyncStore {
    /** The last commit's cache of the workout tab; null = not read. */
    var cachedStatuses: List<KnownStatus>? = null
    val syncedStatusDates = mutableListOf<LocalDate>()
    val droppedStatusDates = mutableListOf<LocalDate>()

    override suspend fun statusesNow(): List<PendingStatus> = statuses.toList()

    var exercises: List<Exercise> = emptyList()
    var rows: List<LogRow> = emptyList()
    val notices = mutableListOf<SyncNotice>()
    var commits = 0
    var keptOnlyNew = 0

    override suspend fun pendingNow(): List<PendingChange> = pending.toList()

    override suspend fun exerciseChangesNow(): List<ExerciseChange> = exerciseChanges.toList()

    override suspend fun paymentsNow(): List<com.workoutrec.workout.PendingPayment> = payments.toList()

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
        syncedStatusDates += statuses.synced
        droppedStatusDates += statuses.dropped
        this.statuses.removeAll { it.date in statuses.synced || it.date in statuses.dropped }
        cachedStatuses = statuses.cached
        payments.removeAll { it.id in donePaymentIds }
        commits++
        this.exercises = exercises
        this.rows = rows
        pending.removeAll { it.id in doneIds }
        pending.replaceAll { c -> renamesBack[c.key.exercise]?.takeIf { c.kind != com.workoutrec.workout.ChangeKind.DELETE }?.let { c.copy(key = c.key.copy(exercise = it)) } ?: c }
        exerciseChanges.removeAll { it.id in doneExerciseIds }
        this.notices += notices
    }

    override suspend fun keepOnlyNewSets() {
        keptOnlyNew++
        exercises = emptyList()
        rows = emptyList()
        notices.clear()
        pending.removeAll { it.kind != com.workoutrec.workout.ChangeKind.NEW }
        exerciseChanges.removeAll { it.kind != com.workoutrec.workout.ExerciseChangeKind.ADD }
    }
}

class FakeSyncStatusStore(initial: SyncStatus = SyncStatus()) : SyncStatusStore {
    private val flow = MutableStateFlow(initial)
    override val status: StateFlow<SyncStatus> = flow
    val phases = mutableListOf<SyncPhase>()

    override suspend fun setPhase(phase: SyncPhase) {
        phases += phase
        flow.value = flow.value.copy(phase = phase)
    }

    override suspend fun markSuccess(at: Long) {
        phases += SyncPhase.Idle
        flow.value = flow.value.copy(phase = SyncPhase.Idle, lastSuccessAt = at)
    }

    override suspend fun setSheet(sheet: SheetInfoCache?) {
        flow.value = flow.value.copy(sheet = sheet)
    }

    override suspend fun clear() {
        flow.value = SyncStatus()
    }

    override suspend fun recordBackground(run: BackgroundRun) {
        flow.value = flow.value.copy(background = run)
    }

    fun failing(reason: FailReason) = SyncPhase.Failing(reason)
}
