package com.workoutrec.workout.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

// data-model.md tables, FR-005
@RunWith(AndroidJUnit4::class)
class WorkoutDaoTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: WorkoutDatabase
    private val dao get() = db.dao()

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, WorkoutDatabase::class.java).build()
    }

    @After
    fun tearDown() = db.close()

    private fun pending(id: String, createdAt: Long, draftId: String? = null) = PendingChangeEntity(
        id = id, kind = "NEW", time = 1000 + createdAt, exercise = "squat", weightHundredths = 2000, reps = 10,
        lastSeenTime = null, lastSeenExercise = null, lastSeenWeightHundredths = null, lastSeenReps = null,
        rowHint = null, createdAt = createdAt, draftId = draftId,
    )

    @Test
    fun replaceCachesReplacesBothTables() = runTest {
        dao.replaceCaches(
            listOf(ExerciseEntity("old", "legs", 0)),
            listOf(LogRowEntity(1, 10, "old", 100, 5)),
        )
        dao.replaceCaches(
            listOf(ExerciseEntity("pull-up", "back", 0), ExerciseEntity("bench", "chest", 1)),
            listOf(LogRowEntity(1, 20, "bench", 2000, 10), LogRowEntity(2, 30, "bench", 2000, 8)),
        )
        assertEquals(listOf("pull-up", "bench"), dao.exercises().first().map { it.name })
        assertEquals(listOf(1, 2), dao.logRows().first().map { it.rowIndex })
        assertEquals(20L, dao.logRows().first()[0].time)
    }

    @Test
    fun pendingChangesComeInCreationOrder() = runTest {
        dao.upsertPending(pending("b", 2))
        dao.upsertPending(pending("a", 1))
        dao.upsertPending(pending("c", 3, draftId = "d1"))
        assertEquals(listOf("a", "b", "c"), dao.pending().first().map { it.id })
        assertEquals(listOf("a", "b", "c"), dao.pendingNow().map { it.id })
    }

    @Test
    fun upsertReplacesAPendingChange() = runTest {
        dao.upsertPending(pending("a", 1))
        dao.upsertPending(pending("a", 1).copy(reps = 12))
        assertEquals(listOf(12), dao.pendingNow().map { it.reps })
    }

    @Test
    fun deletePendingRemovesOnlyTheGivenIds() = runTest {
        listOf("a", "b", "c").forEachIndexed { i, id -> dao.upsertPending(pending(id, i.toLong())) }
        dao.deletePending(listOf("a", "c"))
        assertEquals(listOf("b"), dao.pendingNow().map { it.id })
    }

    @Test
    fun noticesAndDraftsAreStored() = runTest {
        dao.insertNotices(listOf(SyncNoticeEntity(0, "CONFLICT_DROPPED", 10, "squat", 2000, 10, 99)))
        val notice = dao.notices().first().single()
        assertEquals("CONFLICT_DROPPED", notice.kind)
        dao.deleteNotice(notice.id)
        assertEquals(0, dao.notices().first().size)

        dao.upsertDraft(PastWorkoutDraftEntity("d1", "2026-01-11", "18:00", 60))
        assertEquals(60, dao.draft("d1")?.durationMinutes)
    }

    @Test
    fun clearAllEmptiesEverything() = runTest {
        dao.replaceCaches(listOf(ExerciseEntity("a", "b", 0)), listOf(LogRowEntity(1, 1, "a", 1, 1)))
        dao.upsertPending(pending("p", 1))
        dao.insertNotices(listOf(SyncNoticeEntity(0, "CONFLICT_DROPPED", 1, "a", 1, 1, 1)))
        dao.upsertDraft(PastWorkoutDraftEntity("d1", "2026-01-11", "18:00", 60))
        dao.clearAll()
        assertEquals(0, dao.exercises().first().size + dao.logRows().first().size + dao.pendingNow().size + dao.notices().first().size)
        assertEquals(null, dao.draft("d1"))
    }

    @Test
    fun confirmedSetsSurviveClosingTheDatabase() = runTest {
        val name = "survive-test.db"
        context.deleteDatabase(name)
        val fileDb = Room.databaseBuilder(context, WorkoutDatabase::class.java, name).build()
        fileDb.dao().upsertPending(pending("kept", 1))
        fileDb.close()
        val reopened = Room.databaseBuilder(context, WorkoutDatabase::class.java, name).build()
        assertEquals(listOf("kept"), reopened.dao().pendingNow().map { it.id })
        reopened.close()
        context.deleteDatabase(name)
    }
}
