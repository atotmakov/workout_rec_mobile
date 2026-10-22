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
    open suspend fun replaceCaches(exercises: List<ExerciseEntity>, rows: List<LogRowEntity>) {
        clearExercises()
        clearLogRows()
        insertExercises(exercises)
        insertLogRows(rows)
    }

    /** Sign out or account switch (research R13); feature 004 tables too. */
    @Transaction
    open suspend fun clearAll() {
        clearExercises()
        clearLogRows()
        clearPending()
        clearNotices()
        clearDrafts()
        clearExerciseChanges()
        clearPayments()
        clearPaymentRows()
        clearMoneySummary()
    }

    /**
     * One sync result (data-model.md "Sync run"): new caches, handled changes gone, notices added;
     * feature 004: handled exercise changes gone, a dropped rename's waiting sets renamed back.
     */
    @Transaction
    open suspend fun commitSync(
        exercises: List<ExerciseEntity>,
        rows: List<LogRowEntity>,
        doneIds: List<String>,
        notices: List<SyncNoticeEntity>,
        doneExerciseIds: List<String> = emptyList(),
        renamesBack: Map<String, String> = emptyMap(),
        donePaymentIds: List<String> = emptyList(),
    ) {
        replaceCaches(exercises, rows)
        if (donePaymentIds.isNotEmpty()) deletePayments(donePaymentIds)
        if (doneIds.isNotEmpty()) deletePending(doneIds)
        if (notices.isNotEmpty()) insertNotices(notices)
        if (doneExerciseIds.isNotEmpty()) deleteExerciseChanges(doneExerciseIds)
        renamesBack.forEach { (from, to) -> renamePendingSets(from, to) }
    }

    /** Waiting new and edited sets of [from] get [to] (feature 004 research R3, R5). */
    @Query("UPDATE pending_change SET exercise = :to WHERE exercise = :from AND kind != 'DELETE'")
    abstract suspend fun renamePendingSets(from: String, to: String)

    /** A rename: the exercise change and the renamed waiting sets in one transaction (research R3). */
    @Transaction
    open suspend fun renameExercise(change: PendingExerciseChangeEntity, sets: List<PendingChangeEntity>) {
        upsertExerciseChange(change)
        upsertPendingAll(sets)
    }

    @Query("DELETE FROM pending_exercise_change WHERE kind != 'ADD'")
    protected abstract suspend fun clearExerciseRenamesAndDeletes()

    @Query("DELETE FROM pending_change WHERE kind != 'NEW'")
    protected abstract suspend fun clearEditsAndDeletes()

    /** The bound spreadsheet changed (research R13): keep only new sets. */
    @Transaction
    open suspend fun keepOnlyNewSets() {
        clearExercises()
        clearLogRows()
        clearNotices()
        clearEditsAndDeletes()
        // Feature 004: new exercises still apply; renames and deletes were of the old drills tab.
        clearExerciseRenamesAndDeletes()
    }

    @Upsert
    abstract suspend fun upsertPendingAll(changes: List<PendingChangeEntity>)

    /** All sets of a past workout at once (research R10). */
    @Transaction
    open suspend fun insertSets(changes: List<PendingChangeEntity>) = upsertPendingAll(changes)

    // Feature 004 data-model.md

    @Query("SELECT * FROM pending_exercise_change ORDER BY createdAt")
    abstract fun exerciseChanges(): Flow<List<PendingExerciseChangeEntity>>

    @Query("SELECT * FROM pending_exercise_change ORDER BY createdAt")
    abstract suspend fun exerciseChangesNow(): List<PendingExerciseChangeEntity>

    @Upsert
    abstract suspend fun upsertExerciseChange(change: PendingExerciseChangeEntity)

    @Query("DELETE FROM pending_exercise_change WHERE id IN (:ids)")
    abstract suspend fun deleteExerciseChanges(ids: List<String>)

    @Query("SELECT * FROM pending_payment ORDER BY createdAt")
    abstract fun pendingPayments(): Flow<List<PendingPaymentEntity>>

    @Query("SELECT * FROM pending_payment ORDER BY createdAt")
    abstract suspend fun pendingPaymentsNow(): List<PendingPaymentEntity>

    @Insert
    abstract suspend fun insertPayment(payment: PendingPaymentEntity)

    @Query("DELETE FROM pending_payment WHERE id IN (:ids)")
    abstract suspend fun deletePayments(ids: List<String>)

    @Query("SELECT * FROM payment_row ORDER BY rowIndex")
    abstract fun paymentRows(): Flow<List<PaymentRowEntity>>

    @Query("SELECT * FROM money_summary WHERE id = 1")
    abstract fun moneySummary(): Flow<MoneySummaryEntity?>

    @Query("DELETE FROM pending_exercise_change")
    protected abstract suspend fun clearExerciseChanges()

    @Query("DELETE FROM pending_payment")
    protected abstract suspend fun clearPayments()

    @Query("DELETE FROM payment_row")
    protected abstract suspend fun clearPaymentRows()

    @Query("DELETE FROM money_summary")
    protected abstract suspend fun clearMoneySummary()

    @Insert
    protected abstract suspend fun insertPaymentRows(rows: List<PaymentRowEntity>)

    @Upsert
    protected abstract suspend fun upsertMoneySummary(summary: MoneySummaryEntity)

    @Query("SELECT * FROM money_summary WHERE id = 1")
    abstract suspend fun moneySummaryNow(): MoneySummaryEntity?

    /** A failed money download keeps what is stored and records why (research R6). */
    @Transaction
    open suspend fun moneyFailed(error: String) {
        upsertMoneySummary(
            moneySummaryNow()?.copy(error = error)
                ?: MoneySummaryEntity(balance = null, lastDailyRun = null, automation = "ScriptMissing", downloadedAt = 0, error = error),
        )
    }

    /** One money download (feature 004 research R6): rows and summary together. */
    @Transaction
    open suspend fun replacePayments(rows: List<PaymentRowEntity>, summary: MoneySummaryEntity) {
        clearPaymentRows()
        insertPaymentRows(rows)
        upsertMoneySummary(summary)
    }
}
