package com.workoutrec.exercises

import com.workoutrec.workout.DisplaySet
import com.workoutrec.workout.Reps
import com.workoutrec.workout.SetKey
import com.workoutrec.workout.SetRef
import com.workoutrec.workout.ShownExercise
import com.workoutrec.workout.SyncState
import com.workoutrec.workout.Weight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// Feature 004 FR-008, FR-008a, FR-008b; data-model.md value types ExerciseName, MuscleGroup
class ExerciseRulesTest {

    private val shown = listOf(
        ShownExercise("Bench press", "Chest", pending = false, deletable = false),
        ShownExercise("Face pull", "Shoulders", pending = true, deletable = true),
    )

    private fun check(name: String, group: String = "Back", renaming: String? = null) =
        ExerciseRules.check(name, group, shown, renaming)

    @Test
    fun nameAndGroupAreTrimmed() =
        assertEquals(ExerciseCheck.Ok("Pull-up", "Back"), check("  Pull-up ", " Back  "))

    @Test
    fun nameIsRequired() = assertEquals(ExerciseCheck.Invalid(ExerciseProblem.NAME_EMPTY), check("   "))

    @Test
    fun nameUpTo100Characters() {
        assertEquals(ExerciseCheck.Ok("a".repeat(100), "Back"), check("a".repeat(100)))
        assertEquals(ExerciseCheck.Invalid(ExerciseProblem.NAME_TOO_LONG), check("a".repeat(101)))
    }

    @Test
    fun groupMayBeEmptyAndUpTo50Characters() {
        assertEquals(ExerciseCheck.Ok("Plank", ""), check("Plank", group = " "))
        assertEquals(ExerciseCheck.Invalid(ExerciseProblem.GROUP_TOO_LONG), check("Plank", group = "g".repeat(51)))
    }

    @Test
    fun duplicateNamesAreRefusedIgnoringCaseAndSpaces() =
        assertEquals(ExerciseCheck.Invalid(ExerciseProblem.DUPLICATE), check(" bench PRESS "))

    @Test
    fun aRenameMayKeepItsOwnName() {
        assertEquals(ExerciseCheck.Ok("Bench Press", "Upper body"), check("Bench Press", "Upper body", renaming = "Bench press"))
        assertEquals(ExerciseCheck.Invalid(ExerciseProblem.DUPLICATE), check("face pull", renaming = "Bench press"))
    }

    private fun set(exercise: String) =
        SetKey(1, exercise, Weight.ofHundredths(2000), Reps(10)).let { DisplaySet(it, SyncState.SYNCED, SetRef.Row(1, it)) }

    @Test
    fun deletableOnlyWithoutSets() {
        assertTrue(ExerciseRules.deletable("Face pull", listOf(set("Bench press"))))
        assertFalse(ExerciseRules.deletable("Bench press", listOf(set("Bench press"))))
    }
}
