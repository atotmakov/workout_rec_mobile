package com.workoutrec.fakes

import com.workoutrec.sync.FailReason
import com.workoutrec.sync.LogSyncStore
import com.workoutrec.sync.SheetInfoCache
import com.workoutrec.sync.SyncNotice
import com.workoutrec.sync.SyncPhase
import com.workoutrec.sync.SyncStatus
import com.workoutrec.sync.SyncStatusStore
import com.workoutrec.workout.Exercise
import com.workoutrec.workout.LogRow
import com.workoutrec.workout.PendingChange
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** In-memory phone data for sync tests. */
class FakeLogSyncStore(
    val pending: MutableList<PendingChange> = mutableListOf(),
) : LogSyncStore {
    var exercises: List<Exercise> = emptyList()
    var rows: List<LogRow> = emptyList()
    val notices = mutableListOf<SyncNotice>()
    var commits = 0
    var keptOnlyNew = 0

    override suspend fun pendingNow(): List<PendingChange> = pending.toList()

    override suspend fun commit(exercises: List<Exercise>, rows: List<LogRow>, doneIds: Collection<String>, notices: List<SyncNotice>) {
        commits++
        this.exercises = exercises
        this.rows = rows
        pending.removeAll { it.id in doneIds }
        this.notices += notices
    }

    override suspend fun keepOnlyNewSets() {
        keptOnlyNew++
        exercises = emptyList()
        rows = emptyList()
        notices.clear()
        pending.removeAll { it.kind != com.workoutrec.workout.ChangeKind.NEW }
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

    fun failing(reason: FailReason) = SyncPhase.Failing(reason)
}
