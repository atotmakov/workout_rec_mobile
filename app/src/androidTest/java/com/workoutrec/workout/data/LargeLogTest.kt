package com.workoutrec.workout.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.workoutrec.workout.ChangeKind
import com.workoutrec.workout.LogRow
import com.workoutrec.workout.PendingChange
import com.workoutrec.workout.Reps
import com.workoutrec.workout.SetKey
import com.workoutrec.workout.Weight
import kotlin.system.measureTimeMillis
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

// plan.md Performance Goals; SC-005 (200 sets offline), large logs (research R7)
@RunWith(AndroidJUnit4::class)
class LargeLogTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: WorkoutDatabase
    private lateinit var repository: WorkoutRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, WorkoutDatabase::class.java).build()
        repository = WorkoutRepository(db.dao())
    }

    @After
    fun tearDown() = db.close()

    private fun key(i: Int) = SetKey(1_600_000_000L + i * 60L, "exercise ${i % 40}", Weight.ofHundredths(2000L + i % 50), Reps(1 + i % 20))

    @Test
    fun aLogOf20000RowsIsCachedQuickly() = runTest {
        val rows = (1..20_000).map { LogRow(it, key(it)) }
        val ms = measureTimeMillis { repository.replaceCaches(emptyList(), rows) }
        assertEquals(20_000, db.dao().logRowsNow().size)
        assertTrue("replaceCaches took $ms ms", ms < 2_000)
    }

    @Test
    fun confirmingASetStaysFastWith200PendingSets() = runTest {
        repeat(200) { db.dao().upsertPending(PendingChange("p$it", ChangeKind.NEW, key(it), createdAt = it.toLong()).toEntity()) }
        val ms = measureTimeMillis { repository.confirmSet(key(500)) }
        assertEquals(201, repository.pending.first().size)
        assertTrue("confirmSet took $ms ms", ms < 100)
    }
}
