package com.workoutrec.workout

import com.workoutrec.workout.ExerciseChangeKind.ADD
import com.workoutrec.workout.ExerciseChangeKind.DELETE
import com.workoutrec.workout.ExerciseChangeKind.RENAME
import org.junit.Assert.assertEquals
import org.junit.Test

// Feature 004 data-model.md "Display model additions": the exercise list and renamed history
class DisplayModelExercisesTest {

    private val cache = listOf(Exercise("Bench press", "Chest"), Exercise("Squat", "Legs"), Exercise("Plank", "Core"))

    private fun row(index: Int, exercise: String) = LogRow(index, SetKey(index.toLong(), exercise, Weight.ofHundredths(2000), Reps(10)))

    private fun change(kind: ExerciseChangeKind, name: String, group: String, lastSeen: String? = null, lastGroup: String? = null) =
        ExerciseChange("id-$name", kind, name, group, lastSeen, lastGroup, createdAt = 1)

    @Test
    fun cachedExercisesInDrillsOrderWithDeletableFlag() {
        val sets = DisplayModel.sets(listOf(row(1, "Squat")), emptyList())
        assertEquals(
            listOf(
                ShownExercise("Bench press", "Chest", pending = false, deletable = true),
                ShownExercise("Squat", "Legs", pending = false, deletable = false),
                ShownExercise("Plank", "Core", pending = false, deletable = true),
            ),
            DisplayModel.exercises(cache, emptyList(), sets),
        )
    }

    @Test
    fun waitingChangesAreApplied() {
        val changes = listOf(
            change(ADD, "Face pull", "Shoulders"),
            change(RENAME, "Bench", "Upper", lastSeen = "Bench press", lastGroup = "Chest"),
            change(DELETE, "Plank", "Core", lastSeen = "Plank", lastGroup = "Core"),
        )
        assertEquals(
            listOf(
                ShownExercise("Bench", "Upper", pending = true, deletable = true),
                ShownExercise("Squat", "Legs", pending = false, deletable = true),
                ShownExercise("Face pull", "Shoulders", pending = true, deletable = true),
            ),
            DisplayModel.exercises(cache, changes, emptyList()),
        )
    }

    @Test
    fun historyFollowsAWaitingRename() {
        val renames = DisplayModel.renames(listOf(change(RENAME, "Bench", "Chest", lastSeen = "Bench press", lastGroup = "Chest")))
        assertEquals(mapOf("Bench press" to "Bench"), renames)
        val sets = DisplayModel.sets(listOf(row(1, "Bench press"), row(2, "Squat")), emptyList(), renames)
        assertEquals(listOf("Bench", "Squat"), sets.map { it.key.exercise })
        // Edits and deletes still target the row as it is in the sheet.
        assertEquals("Bench press", (sets[0].ref as SetRef.Row).key.exercise)
    }

    @Test
    fun aRenamedExerciseWithHistoryIsNotDeletable() {
        val changes = listOf(change(RENAME, "Bench", "Chest", lastSeen = "Bench press", lastGroup = "Chest"))
        val sets = DisplayModel.sets(listOf(row(1, "Bench press")), emptyList(), DisplayModel.renames(changes))
        assertEquals(false, DisplayModel.exercises(cache, changes, sets).first { it.name == "Bench" }.deletable)
    }
}
