package com.workoutrec.workout

import com.workoutrec.workout.ExerciseChangeKind.ADD
import com.workoutrec.workout.ExerciseChangeKind.DELETE
import com.workoutrec.workout.ExerciseChangeKind.RENAME
import org.junit.Assert.assertEquals
import org.junit.Test

// Feature 004 data-model.md `pending_exercise_change` coalescing table, research R3
class ExerciseCoalescerTest {

    private val cached = listOf(Exercise("Bench press", "Chest"), Exercise("Squat", "Legs"))

    private fun added(id: String = "a") = ExerciseChange(id, ADD, "Face pull", "Shoulders", createdAt = 1)

    private fun renamed(id: String = "r") =
        ExerciseChange(id, RENAME, "Bench", "Chest", lastSeenName = "Bench press", lastSeenGroup = "Chest", createdAt = 1)

    private fun set(id: String, kind: ChangeKind, exercise: String) =
        PendingChange(id, kind, SetKey(1, exercise, Weight.ofHundredths(2000), Reps(10)), lastSeen = SetKey(1, "old", Weight.ofHundredths(2000), Reps(10)).takeIf { kind != ChangeKind.NEW }, createdAt = 1)

    @Test
    fun addIsANewChange() =
        assertEquals(ExerciseChange("n", ADD, "Face pull", "Shoulders", createdAt = 5), ExerciseCoalescer.add("Face pull", "Shoulders", "n", 5))

    @Test
    fun renamingACachedExerciseRemembersWhatTheAppSaw() {
        val result = ExerciseCoalescer.rename("Bench press", cached, emptyList(), emptyList(), "Bench", "Chest", "n", 5)
        assertEquals(
            ExerciseUpdate.Upsert(ExerciseChange("n", RENAME, "Bench", "Chest", lastSeenName = "Bench press", lastSeenGroup = "Chest", createdAt = 5)),
            result.update,
        )
    }

    @Test
    fun deletingACachedExerciseRemembersWhatTheAppSaw() =
        assertEquals(
            ExerciseUpdate.Upsert(ExerciseChange("n", DELETE, "Squat", "Legs", lastSeenName = "Squat", lastSeenGroup = "Legs", createdAt = 5)),
            ExerciseCoalescer.delete("Squat", cached, emptyList(), "n", 5),
        )

    @Test
    fun renamingAnAddedExerciseChangesTheAdd() =
        assertEquals(
            ExerciseUpdate.Upsert(added().copy(name = "Face pulls", muscleGroup = "Back")),
            ExerciseCoalescer.rename("Face pull", cached, listOf(added()), emptyList(), "Face pulls", "Back", "n", 5).update,
        )

    @Test
    fun deletingAnAddedExerciseRemovesTheAdd() =
        assertEquals(ExerciseUpdate.Remove("a"), ExerciseCoalescer.delete("Face pull", cached, listOf(added()), "n", 5))

    @Test
    fun renamingARenamedExerciseKeepsWhatTheAppFirstSaw() =
        assertEquals(
            ExerciseUpdate.Upsert(renamed().copy(name = "Flat bench", muscleGroup = "Upper")),
            ExerciseCoalescer.rename("Bench", cached, listOf(renamed()), emptyList(), "Flat bench", "Upper", "n", 5).update,
        )

    @Test
    fun deletingARenamedExerciseDeletesTheOriginalRow() =
        assertEquals(
            ExerciseUpdate.Upsert(renamed().copy(kind = DELETE, name = "Bench press", muscleGroup = "Chest")),
            ExerciseCoalescer.delete("Bench", cached, listOf(renamed()), "n", 5),
        )

    @Test
    fun aRenameAlsoRenamesTheWaitingNewAndEditedSets() {
        val sets = listOf(set("1", ChangeKind.NEW, "Bench press"), set("2", ChangeKind.EDIT, "Bench press"), set("3", ChangeKind.NEW, "Squat"), set("4", ChangeKind.DELETE, "Bench press"))
        val result = ExerciseCoalescer.rename("Bench press", cached, emptyList(), sets, "Bench", "Chest", "n", 5)
        assertEquals(listOf("1", "2"), result.sets.map { it.id })
        assertEquals(listOf("Bench", "Bench"), result.sets.map { it.key.exercise })
        // An edit still finds its row by the values the app saw.
        assertEquals("old", result.sets[1].lastSeen?.exercise)
    }
}
