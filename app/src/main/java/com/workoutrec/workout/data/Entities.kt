package com.workoutrec.workout.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// Room tables of data-model.md. Weights are stored as hundredths of a kg (research R12).

/** Cache of the drills tab. */
@Entity(tableName = "exercise")
data class ExerciseEntity(
    @PrimaryKey val name: String,
    val muscleGroup: String,
    val sheetOrder: Int,
)

/** Cache of the log tab; [rowIndex] is the 0-based sheet row at download time. */
@Entity(tableName = "log_row")
data class LogRowEntity(
    @PrimaryKey val rowIndex: Int,
    val time: Long,
    val exercise: String,
    val weightHundredths: Long,
    val reps: Int,
)

/** The app's changes not in the sheet yet; `kind` is NEW, EDIT or DELETE. */
@Entity(tableName = "pending_change", indices = [Index("draftId")])
data class PendingChangeEntity(
    @PrimaryKey val id: String,
    val kind: String,
    val time: Long,
    val exercise: String,
    val weightHundredths: Long,
    val reps: Int,
    val lastSeenTime: Long?,
    val lastSeenExercise: String?,
    val lastSeenWeightHundredths: Long?,
    val lastSeenReps: Int?,
    val rowHint: Int?,
    val createdAt: Long,
    val draftId: String?,
)

/** A past workout being entered; [date] is ISO yyyy-MM-dd, [start] is HH:mm. */
@Entity(tableName = "past_workout_draft")
data class PastWorkoutDraftEntity(
    @PrimaryKey val id: String,
    val date: String,
    val start: String,
    val durationMinutes: Int,
)

/** A message about a sync result; `kind` is CONFLICT_DROPPED or WORKOUT_ROW_NOT_UPDATED. */
@Entity(tableName = "sync_notice")
data class SyncNoticeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val kind: String,
    val time: Long,
    val exercise: String,
    val weightHundredths: Long,
    val reps: Int,
    val createdAt: Long,
)

// Feature 004 data-model.md: schema 2.

/** An exercise change not in the drills tab yet; `kind` is ADD, RENAME or DELETE. */
@Entity(tableName = "pending_exercise_change")
data class PendingExerciseChangeEntity(
    @PrimaryKey val id: String,
    val kind: String,
    /** ADD/RENAME: the new values; DELETE: unused. */
    val name: String,
    val muscleGroup: String,
    /** RENAME/DELETE: the drills values when the user acted. */
    val lastSeenName: String?,
    val lastSeenGroup: String?,
    val createdAt: Long,
)

/** A payment not in the money tab yet; [date] is ISO yyyy-MM-dd. */
@Entity(tableName = "pending_payment")
data class PendingPaymentEntity(
    @PrimaryKey val id: String,
    val date: String,
    val workouts: Int,
    val sumHundredths: Long,
    val createdAt: Long,
)

/** Cache of the money tab; [rowIndex] is the 0-based sheet row; null values were not numbers. */
@Entity(tableName = "payment_row")
data class PaymentRowEntity(
    @PrimaryKey val rowIndex: Int,
    val date: String,
    val workouts: Double?,
    val sumHundredths: Long?,
)

/** The balance and what is known about its calculation; one row with [id] 1. */
@Entity(tableName = "money_summary")
data class MoneySummaryEntity(
    @PrimaryKey val id: Int = 1,
    val balance: Double?,
    val lastDailyRun: Long?,
    /** On, Stopped, NotEnabled or ScriptMissing (feature 001 `AutomationStatus`). */
    val automation: String,
    val downloadedAt: Long,
    /** NONE, MISSING_TAB, NETWORK or OTHER: the last download's result. */
    val error: String,
)
