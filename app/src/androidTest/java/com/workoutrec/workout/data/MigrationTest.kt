package com.workoutrec.workout.data

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

// Feature 004 research R9: the phone's data survives the update to schema 2
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), WorkoutDatabase::class.java)

    private val db = "migration-test.db"

    @Test
    fun version1DataSurvivesAndTheNewTablesAreEmpty() {
        helper.createDatabase(db, 1).use { v1 ->
            v1.execSQL("INSERT INTO log_row (rowIndex, time, exercise, weightHundredths, reps) VALUES (1, 100, 'squat', 2000, 10), (2, 200, 'bench', 4000, 8)")
            v1.execSQL(
                "INSERT INTO pending_change (id, kind, time, exercise, weightHundredths, reps, createdAt, draftId) VALUES " +
                    "('a', 'NEW', 300, 'squat', 2000, 9, 1, NULL), ('b', 'NEW', 400, 'squat', 2000, 8, 2, 'draft')",
            )
            v1.execSQL("INSERT INTO sync_notice (kind, time, exercise, weightHundredths, reps, createdAt) VALUES ('CONFLICT_DROPPED', 100, 'squat', 2000, 10, 5)")
        }

        helper.runMigrationsAndValidate(db, 2, true).use { v2 ->
            assertEquals(2, v2.count("log_row"))
            assertEquals(2, v2.count("pending_change"))
            assertEquals(1, v2.count("pending_change WHERE draftId = 'draft'"))
            assertEquals(1, v2.count("sync_notice"))
            listOf("pending_exercise_change", "pending_payment", "payment_row", "money_summary").forEach {
                assertEquals(it, 0, v2.count(it))
            }
        }
    }

    private fun SupportSQLiteDatabase.count(from: String): Int =
        query("SELECT COUNT(*) FROM $from").use { it.moveToFirst(); it.getInt(0) }
}
