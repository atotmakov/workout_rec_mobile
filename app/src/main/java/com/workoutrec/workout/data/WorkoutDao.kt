package com.workoutrec.workout.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
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
        clearStatuses()
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
        syncedStatusDates: List<String> = emptyList(),
        droppedStatusDates: List<String> = emptyList(),
        cachedStatuses: List<DayStatusEntity>? = null,
    ) {
        replaceCaches(exercises, rows)
        // Feature 006: written statuses stop waiting; then the fresh workout tab replaces the cache.
        if (syncedStatusDates.isNotEmpty()) markStatusesSynced(syncedStatusDates)
        if (droppedStatusDates.isNotEmpty()) dropPendingStatuses(droppedStatusDates)
        cachedStatuses?.let { replaceCachedStatuses(it) }
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
        // Feature 006: chosen statuses still apply; the cache was of the old workout tab.
        clearCachedStatuses()
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

    // Feature 006 data-model.md "Writes"

    @Query("SELECT * FROM day_status ORDER BY date")
    abstract fun statuses(): Flow<List<DayStatusEntity>>

    @Query("SELECT * FROM day_status ORDER BY date")
    abstract suspend fun statusesNow(): List<DayStatusEntity>

    @Query("SELECT * FROM day_status WHERE date = :date")
    abstract suspend fun statusFor(date: String): DayStatusEntity?

    @Upsert
    protected abstract suspend fun upsertStatus(status: DayStatusEntity)

    /** Skips dates that already have a row: a pending one, or the cache. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertStatusesIfMissing(statuses: List<DayStatusEntity>)

    /**
     * The user picked a status: it waits for sync and remembers the tab's value it replaced; a
     * date already waiting keeps the value seen before its first change (research R4).
     */
    @Transaction
    open suspend fun chooseStatus(date: String, free: Boolean, updatedAt: Long) {
        val current = statusFor(date)
        val lastSeen = if (current?.pending == true) current.lastSeenFree else current?.free
        upsertStatus(DayStatusEntity(date, free, pending = true, updatedAt = updatedAt, lastSeenFree = lastSeen))
    }

    /** A day's first set: the default waits for sync unless the date has a status (research R2). */
    open suspend fun insertDefaultStatus(date: String, free: Boolean, updatedAt: Long) =
        insertStatusesIfMissing(listOf(DayStatusEntity(date, free, pending = true, updatedAt = updatedAt, lastSeenFree = null)))

    /** New sets and their days' default statuses in one transaction (feature 006 research R2). */
    @Transaction
    open suspend fun insertSetsWithDefaults(changes: List<PendingChangeEntity>, defaults: List<DayStatusEntity>) {
        upsertPendingAll(changes)
        insertStatusesIfMissing(defaults)
    }

    /** A fresh read of the workout tab; pending dates keep the phone's choice. */
    @Transaction
    open suspend fun replaceCachedStatuses(statuses: List<DayStatusEntity>) {
        clearCachedStatuses()
        insertStatusesIfMissing(statuses)
    }

    @Query("UPDATE day_status SET pending = 0, lastSeenFree = NULL WHERE date IN (:dates)")
    abstract suspend fun markStatusesSynced(dates: List<String>)

    @Query("DELETE FROM day_status WHERE pending = 1 AND date IN (:dates)")
    abstract suspend fun dropPendingStatuses(dates: List<String>)

    @Query("DELETE FROM day_status WHERE pending = 0")
    protected abstract suspend fun clearCachedStatuses()

    @Query("DELETE FROM day_status")
    protected abstract suspend fun clearStatuses()
}
