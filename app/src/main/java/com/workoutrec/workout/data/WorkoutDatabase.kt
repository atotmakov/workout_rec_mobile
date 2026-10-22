package com.workoutrec.workout.data

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/** The phone's workout data (data-model.md, research R1). */
@Database(
    entities = [
        ExerciseEntity::class,
        LogRowEntity::class,
        PendingChangeEntity::class,
        PastWorkoutDraftEntity::class,
        SyncNoticeEntity::class,
        PendingExerciseChangeEntity::class,
        PendingPaymentEntity::class,
        PaymentRowEntity::class,
        MoneySummaryEntity::class,
    ],
    version = 2,
    exportSchema = true,
    // Feature 004 research R9: version 2 only adds tables.
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
abstract class WorkoutDatabase : RoomDatabase() {

    abstract fun dao(): WorkoutDao

    companion object {
        fun create(context: Context): WorkoutDatabase =
            Room.databaseBuilder(context, WorkoutDatabase::class.java, "workout.db").build()
    }
}
