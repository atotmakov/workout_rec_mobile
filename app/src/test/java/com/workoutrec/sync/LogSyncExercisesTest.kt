package com.workoutrec.sync

import com.workoutrec.fakes.FakeLogSyncStore
import com.workoutrec.fakes.FakeSheetsLogApi
import com.workoutrec.fakes.FakeSyncStatusStore
import com.workoutrec.google.DrillRename
import com.workoutrec.google.NameReplace
import com.workoutrec.google.SheetDrill
import com.workoutrec.workout.ChangeKind
import com.workoutrec.workout.Exercise
import com.workoutrec.workout.ExerciseChange
import com.workoutrec.workout.ExerciseChangeKind.ADD
import com.workoutrec.workout.ExerciseChangeKind.DELETE
import com.workoutrec.workout.ExerciseChangeKind.RENAME
import com.workoutrec.workout.PendingChange
import com.workoutrec.workout.Reps
import com.workoutrec.workout.SetKey
import com.workoutrec.workout.Weight
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Feature 004 research R4, R5 (exercise rows); contracts/sheets.md "Call 22 request order"
class LogSyncExercisesTest {

    private val sheets = FakeSheetsLogApi(
        timeZone = "UTC",
        logSheetId = 3,
        drillsSheetId = 4,
        recSheetId = 5,
        drills = listOf(SheetDrill("Chest", "Bench press"), SheetDrill("Legs", "Squat"), SheetDrill("Core", "Plank")),
    )
    private val store = FakeLogSyncStore()
    private val sync = LogSync(store, sheets, FakeSyncStatusStore(), spreadsheetId = { "s1" }, now = { 99L })

    private fun logRow(exercise: String) = FakeSheetsLogApi.Cells(46000.5, exercise, 20.0, 10)

    private fun add(name: String, group: String, createdAt: Long = 1) = ExerciseChange("add-$name", ADD, name, group, createdAt = createdAt)

    private fun rename(old: String, oldGroup: String, name: String, group: String = oldGroup) =
        ExerciseChange("ren-$old", RENAME, name, group, lastSeenName = old, lastSeenGroup = oldGroup, createdAt = 2)

    private fun delete(name: String, group: String) = ExerciseChange("del-$name", DELETE, name, group, lastSeenName = name, lastSeenGroup = group, createdAt = 3)

    @Test
    fun `a new exercise is appended to drills`() = runTest {
        store.exerciseChanges += add("Face pull", "Shoulders")
        sync.run()
        assertEquals(listOf(SheetDrill("Shoulders", "Face pull")), sheets.writes.single().drills?.appends)
        assertTrue(store.exerciseChanges.isEmpty())
        assertTrue(Exercise("Face pull", "Shoulders") in store.exercises)
    }

    @Test
    fun `a new exercise already in drills is not written again`() = runTest {
        store.exerciseChanges += add("bench PRESS", "Chest")
        sync.run()
        assertTrue(sheets.writes.isEmpty())
        assertTrue(store.exerciseChanges.isEmpty())
    }

    @Test
    fun `a rename updates the drills row, the log and the record view in one write`() = runTest {
        sheets.log += logRow("Bench press")
        sheets.log += logRow("Squat")
        sheets.recA1 = "Bench press"
        store.exerciseChanges += rename("Bench press", "Chest", "Bench", "Upper")
        sync.run()
        val drills = sheets.writes.single().drills!!
        assertEquals(listOf(DrillRename(1, "Upper", "Bench")), drills.renames)
        assertEquals(listOf(NameReplace("Bench press", "Bench")), drills.replaces)
        assertEquals(listOf("Bench", "Squat"), sheets.log.map { it.exercise })
        assertEquals("Bench", sheets.recA1)
        assertEquals(SheetDrill("Upper", "Bench"), sheets.drills[0])
        assertTrue(store.exerciseChanges.isEmpty())
    }

    @Test
    fun `a rename that only changes the group replaces no names`() = runTest {
        store.exerciseChanges += rename("Squat", "Legs", "Squat", "Lower body")
        sync.run()
        val drills = sheets.writes.single().drills!!
        assertEquals(listOf(DrillRename(2, "Lower body", "Squat")), drills.renames)
        assertEquals(emptyList<NameReplace>(), drills.replaces)
    }

    @Test
    fun `a rename already applied by an interrupted run is done`() = runTest {
        sheets.drills = listOf(SheetDrill("Chest", "Bench"), SheetDrill("Legs", "Squat"))
        store.exerciseChanges += rename("Bench press", "Chest", "Bench")
        sync.run()
        assertTrue(sheets.writes.isEmpty())
        assertTrue(store.exerciseChanges.isEmpty())
        assertTrue(store.notices.isEmpty())
    }

    @Test
    fun `a rename of an exercise changed in the web UI is dropped, told, and its sets get the old name back`() = runTest {
        sheets.drills = listOf(SheetDrill("Chest", "Flat bench"), SheetDrill("Legs", "Squat"))
        store.exerciseChanges += rename("Bench press", "Chest", "Bench")
        store.pending += PendingChange("s", ChangeKind.NEW, SetKey(1_700_000_000, "Bench", Weight.ofHundredths(6000), Reps(8)), createdAt = 1)
        sync.run()
        assertEquals(listOf(NoticeKind.EXERCISE_CHANGE_DROPPED), store.notices.map { it.kind })
        assertEquals("Bench", store.notices.single().key.exercise)
        assertTrue(store.exerciseChanges.isEmpty())
        // The set is appended under the name the drills tab still has.
        assertEquals(listOf("Bench press"), sheets.writes.single().appends.map { it.exercise })
        assertNull(sheets.writes.single().drills)
    }

    @Test
    fun `an exercise without sets is deleted from drills`() = runTest {
        store.exerciseChanges += delete("Plank", "Core")
        sync.run()
        assertEquals(listOf(3), sheets.writes.single().drills?.deletes)
        assertEquals(listOf("Bench press", "Squat"), sheets.drills.map { it.name })
        assertTrue(store.exerciseChanges.isEmpty())
    }

    @Test
    fun `an exercise found in the log is not deleted and the user is told`() = runTest {
        sheets.log += logRow("Plank")
        store.exerciseChanges += delete("Plank", "Core")
        sync.run()
        assertTrue(sheets.writes.isEmpty())
        assertEquals(listOf(NoticeKind.EXERCISE_IN_USE), store.notices.map { it.kind })
        assertEquals("Plank", store.notices.single().key.exercise)
        assertTrue(store.exerciseChanges.isEmpty())
    }

    @Test
    fun `deleting an exercise already gone is done`() = runTest {
        store.exerciseChanges += delete("Deadlift", "Back")
        sync.run()
        assertTrue(sheets.writes.isEmpty())
        assertTrue(store.exerciseChanges.isEmpty())
    }

    @Test
    fun `sets and exercise changes go in one write`() = runTest {
        store.pending += PendingChange("s", ChangeKind.NEW, SetKey(1_700_000_000, "Face pull", Weight.ofHundredths(1000), Reps(15)), createdAt = 2)
        store.exerciseChanges += add("Face pull", "Shoulders")
        store.exerciseChanges += delete("Plank", "Core")
        sync.run()
        val write = sheets.writes.single()
        assertEquals(listOf("Face pull"), write.appends.map { it.exercise })
        assertEquals(listOf(SheetDrill("Shoulders", "Face pull")), write.drills?.appends)
        assertEquals(listOf(3), write.drills?.deletes)
        assertEquals(1, sheets.calls.count { it.startsWith("write") })
    }

    @Test
    fun `a missing rec tab is not renamed`() = runTest {
        sheets.recSheetId = null
        store.exerciseChanges += rename("Bench press", "Chest", "Bench")
        sync.run()
        assertNull(sheets.writes.single().drills?.recSheetId)
    }
}
