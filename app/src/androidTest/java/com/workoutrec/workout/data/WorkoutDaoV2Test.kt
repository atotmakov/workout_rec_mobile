package com.workoutrec.workout.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

// Feature 004 data-model.md: the tables added in schema 2
@RunWith(AndroidJUnit4::class)
class WorkoutDaoV2Test {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: WorkoutDatabase
    private val dao get() = db.dao()

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, WorkoutDatabase::class.java).build()
    }

    @After
    fun tearDown() = db.close()

    private fun exerciseChange(id: String, createdAt: Long) =
        PendingExerciseChangeEntity(id, "ADD", "Face pull", "Shoulders", lastSeenName = null, lastSeenGroup = null, createdAt = createdAt)

    private fun payment(id: String, createdAt: Long) = PendingPaymentEntity(id, "2026-10-20", 8, 800_000, createdAt)

    private fun summary(balance: Double?) = MoneySummaryEntity(1, balance, lastDailyRun = 10, automation = "On", downloadedAt = 20, error = "NONE")

    @Test
    fun pendingExerciseChangesAreKeptInOrderAndRemovedById() = runTest {
        dao.upsertExerciseChange(exerciseChange("b", 2))
        dao.upsertExerciseChange(exerciseChange("a", 1))
        assertEquals(listOf("a", "b"), dao.exerciseChanges().first().map { it.id })
        dao.deleteExerciseChanges(listOf("a"))
        assertEquals(listOf("b"), dao.exerciseChangesNow().map { it.id })
    }

    @Test
    fun pendingPaymentsAreKeptInOrderAndRemovedById() = runTest {
        dao.insertPayment(payment("y", 2))
        dao.insertPayment(payment("x", 1))
        assertEquals(listOf("x", "y"), dao.pendingPayments().first().map { it.id })
        dao.deletePayments(listOf("y"))
        assertEquals(listOf("x"), dao.pendingPaymentsNow().map { it.id })
    }

    @Test
    fun replacePaymentsReplacesRowsAndSummaryTogether() = runTest {
        dao.replacePayments(listOf(PaymentRowEntity(1, "2026-01-10", 8.0, 800_000), PaymentRowEntity(2, "2026-02-10", null, null)), summary(3.0))
        dao.replacePayments(listOf(PaymentRowEntity(1, "2026-03-10", 12.0, 1_200_000)), summary(-1.0))
        assertEquals(listOf("2026-03-10"), dao.paymentRows().first().map { it.date })
        assertEquals(-1.0, dao.moneySummary().first()?.balance)
    }

    @Test
    fun clearAllAlsoEmptiesTheNewTables() = runTest {
        dao.upsertExerciseChange(exerciseChange("a", 1))
        dao.insertPayment(payment("x", 1))
        dao.replacePayments(listOf(PaymentRowEntity(1, "2026-01-10", 8.0, 800_000)), summary(3.0))
        dao.clearAll()
        assertEquals(0, dao.exerciseChangesNow().size)
        assertEquals(0, dao.pendingPaymentsNow().size)
        assertEquals(0, dao.paymentRows().first().size)
        assertNull(dao.moneySummary().first())
    }

    private fun set(id: String, kind: String, exercise: String) = PendingChangeEntity(
        id = id, kind = kind, time = 1, exercise = exercise, weightHundredths = 2000, reps = 10,
        lastSeenTime = null, lastSeenExercise = null, lastSeenWeightHundredths = null, lastSeenReps = null,
        rowHint = null, createdAt = 1, draftId = null,
    )

    // Research R13 with feature 004: a new spreadsheet keeps new exercises, not renames or deletes.
    @Test
    fun aNewSpreadsheetKeepsOnlyAddedExercises() = runTest {
        dao.upsertExerciseChange(exerciseChange("a", 1))
        dao.upsertExerciseChange(PendingExerciseChangeEntity("r", "RENAME", "Bench", "Chest", "Bench press", "Chest", 2))
        dao.upsertExerciseChange(PendingExerciseChangeEntity("d", "DELETE", "Plank", "Core", "Plank", "Core", 3))
        dao.keepOnlyNewSets()
        assertEquals(listOf("a"), dao.exerciseChangesNow().map { it.id })
    }

    // Research R5: a dropped rename gives the waiting sets their old name back; handled changes go.
    @Test
    fun commitRemovesHandledExerciseChangesAndRenamesSetsBack() = runTest {
        dao.upsertExerciseChange(exerciseChange("a", 1))
        dao.upsertPendingAll(listOf(set("n", "NEW", "Bench"), set("e", "EDIT", "Bench"), set("x", "DELETE", "Bench")))
        dao.commitSync(emptyList(), emptyList(), emptyList(), emptyList(), doneExerciseIds = listOf("a"), renamesBack = mapOf("Bench" to "Bench press"))
        assertEquals(0, dao.exerciseChangesNow().size)
        assertEquals(mapOf("n" to "Bench press", "e" to "Bench press", "x" to "Bench"), dao.pendingNow().associate { it.id to it.exercise })
    }
}
