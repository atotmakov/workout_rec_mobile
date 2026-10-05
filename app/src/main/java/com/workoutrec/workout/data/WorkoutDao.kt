package com.workoutrec.workout.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
abstract class WorkoutDao {

    @Query("SELECT * FROM exercise ORDER BY sheetOrder")
    abstract fun exercises(): Flow<List<ExerciseEntity>>

    @Query("SELECT * FROM log_row ORDER BY rowIndex")
    abstract fun logRows(): Flow<List<LogRowEntity>>

    @Query("SELECT * FROM log_row ORDER BY rowIndex")
    abstract suspend fun logRowsNow(): List<LogRowEntity>

    @Query("SELECT * FROM pending_change ORDER BY createdAt")
    abstract fun pending(): Flow<List<PendingChangeEntity>>

    @Query("SELECT * FROM pending_change ORDER BY createdAt")
    abstract suspend fun pendingNow(): List<PendingChangeEntity>

    @Upsert
    abstract suspend fun upsertPending(change: PendingChangeEntity)

    @Query("DELETE FROM pending_change WHERE id IN (:ids)")
    abstract suspend fun deletePending(ids: List<String>)

    @Query("SELECT * FROM sync_notice ORDER BY createdAt")
    abstract fun notices(): Flow<List<SyncNoticeEntity>>

    @Insert
    abstract suspend fun insertNotices(notices: List<SyncNoticeEntity>)

    @Query("DELETE FROM sync_notice WHERE id = :id")
    abstract suspend fun deleteNotice(id: Long)

    @Upsert
    abstract suspend fun upsertDraft(draft: PastWorkoutDraftEntity)

    @Query("SELECT * FROM past_workout_draft WHERE id = :id")
    abstract suspend fun draft(id: String): PastWorkoutDraftEntity?

    @Query("DELETE FROM past_workout_draft WHERE id = :id")
    abstract suspend fun deleteDraft(id: String)

    @Query("DELETE FROM exercise")
    protected abstract suspend fun clearExercises()

    @Query("DELETE FROM log_row")
    protected abstract suspend fun clearLogRows()

    @Query("DELETE FROM pending_change")
    protected abstract suspend fun clearPending()

    @Query("DELETE FROM sync_notice")
    protected abstract suspend fun clearNotices()

    @Query("DELETE FROM past_workout_draft")
    protected abstract suspend fun clearDrafts()

    @Insert
    protected abstract suspend fun insertExercises(exercises: List<ExerciseEntity>)

    @Insert
    protected abstract suspend fun insertLogRows(rows: List<LogRowEntity>)

    /** Replaces the drills and log caches in one transaction (research R7). */
    @Transaction
    open suspend fun replaceCaches(exercises: List<ExerciseEntity>, rows: List<LogRowEntity>): Unit = TODO()

    /** Sign out or account switch (research R13). */
    @Transaction
    open suspend fun clearAll(): Unit = TODO()
}
