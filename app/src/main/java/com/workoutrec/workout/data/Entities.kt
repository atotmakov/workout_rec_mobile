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
