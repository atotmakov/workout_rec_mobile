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

// Feature 006 data-model.md "Writes": the day_status table added in schema 3
@RunWith(AndroidJUnit4::class)
class WorkoutDaoV3Test {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: WorkoutDatabase
    private val dao get() = db.dao()

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, WorkoutDatabase::class.java).build()
    }

    @After
    fun tearDown() = db.close()

    private fun cached(date: String, free: Boolean) = DayStatusEntity(date, free, pending = false, updatedAt = 0, lastSeenFree = null)

    @Test
    fun aChoiceRecordsTheCachedValueItReplaces() = runTest {
        dao.replaceCachedStatuses(listOf(cached("2026-10-20", free = false)))
        dao.chooseStatus("2026-10-20", free = true, updatedAt = 5)
        assertEquals(DayStatusEntity("2026-10-20", free = true, pending = true, updatedAt = 5, lastSeenFree = false), dao.statusFor("2026-10-20"))
    }

    @Test
    fun aChoiceForADateWithoutARowRecordsNoRow() = runTest {
        dao.chooseStatus("2026-10-21", free = false, updatedAt = 5)
        assertNull(dao.statusFor("2026-10-21")!!.lastSeenFree)
    }

    @Test
    fun aSecondChoiceKeepsTheFirstLastSeenValue() = runTest {
        dao.replaceCachedStatuses(listOf(cached("2026-10-20", free = false)))
        dao.chooseStatus("2026-10-20", free = true, updatedAt = 5)
        dao.chooseStatus("2026-10-20", free = false, updatedAt = 6)
        dao.chooseStatus("2026-10-20", free = true, updatedAt = 7)
        assertEquals(DayStatusEntity("2026-10-20", free = true, pending = true, updatedAt = 7, lastSeenFree = false), dao.statusFor("2026-10-20"))
        assertEquals(1, dao.statusesNow().size)
    }

    @Test
    fun theDefaultIsInsertedOnlyWhenTheDateHasNoRow() = runTest {
        dao.replaceCachedStatuses(listOf(cached("2026-10-20", free = true)))
        dao.insertDefaultStatus("2026-10-20", free = false, updatedAt = 5)
        dao.insertDefaultStatus("2026-10-21", free = false, updatedAt = 5)
        assertEquals(cached("2026-10-20", free = true), dao.statusFor("2026-10-20"))
        assertEquals(DayStatusEntity("2026-10-21", free = false, pending = true, updatedAt = 5, lastSeenFree = null), dao.statusFor("2026-10-21"))
    }

    @Test
    fun replacingTheCacheKeepsPendingDates() = runTest {
        dao.replaceCachedStatuses(listOf(cached("2026-10-18", true), cached("2026-10-19", true)))
        dao.chooseStatus("2026-10-20", free = true, updatedAt = 5)
        dao.replaceCachedStatuses(listOf(cached("2026-10-19", false), cached("2026-10-20", false)))
        assertEquals(
            listOf(cached("2026-10-19", false), DayStatusEntity("2026-10-20", true, true, 5, null)),
            dao.statuses().first(),
        )
    }

    @Test
    fun syncedStatusesStopBeingPendingAndDroppedOnesGo() = runTest {
        dao.chooseStatus("2026-10-20", free = true, updatedAt = 5)
        dao.chooseStatus("2026-10-21", free = true, updatedAt = 5)
        dao.markStatusesSynced(listOf("2026-10-20"))
        dao.dropPendingStatuses(listOf("2026-10-21"))
        assertEquals(listOf(DayStatusEntity("2026-10-20", true, pending = false, updatedAt = 5, lastSeenFree = null)), dao.statusesNow())
    }

    @Test
    fun aRewrittenSpreadsheetKeepsOnlyPendingStatuses() = runTest {
        dao.replaceCachedStatuses(listOf(cached("2026-10-19", true)))
        dao.chooseStatus("2026-10-20", free = true, updatedAt = 5)
        dao.keepOnlyNewSets()
        assertEquals(listOf("2026-10-20"), dao.statusesNow().map { it.date })
    }

    @Test
    fun signOutClearsAllStatuses() = runTest {
        dao.replaceCachedStatuses(listOf(cached("2026-10-19", true)))
        dao.chooseStatus("2026-10-20", free = true, updatedAt = 5)
        dao.clearAll()
        assertEquals(0, dao.statusesNow().size)
    }
}
