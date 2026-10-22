package com.workoutrec.exercises

import com.workoutrec.workout.DisplaySet
import com.workoutrec.workout.ShownExercise

enum class ExerciseProblem { NAME_EMPTY, NAME_TOO_LONG, GROUP_TOO_LONG, DUPLICATE }

sealed interface ExerciseCheck {
    /** Trimmed values, ready to save. */
    data class Ok(val name: String, val muscleGroup: String) : ExerciseCheck
    data class Invalid(val problem: ExerciseProblem) : ExerciseCheck
}

/** Feature 004 FR-008, FR-008a, FR-008b and data-model.md `ExerciseName`, `MuscleGroup`. */
object ExerciseRules {
    const val MAX_NAME = 100
    const val MAX_GROUP = 50

    /** [renaming] is the exercise being renamed: keeping its own name is not a duplicate. */
    fun check(name: String, muscleGroup: String, shown: List<ShownExercise>, renaming: String? = null): ExerciseCheck {
        val trimmed = name.trim()
        val group = muscleGroup.trim()
        val problem = when {
            trimmed.isEmpty() -> ExerciseProblem.NAME_EMPTY
            trimmed.length > MAX_NAME -> ExerciseProblem.NAME_TOO_LONG
            group.length > MAX_GROUP -> ExerciseProblem.GROUP_TOO_LONG
            shown.any { it.name != renaming && it.name.trim().equals(trimmed, ignoreCase = true) } -> ExerciseProblem.DUPLICATE
            else -> null
        }
        return problem?.let { ExerciseCheck.Invalid(it) } ?: ExerciseCheck.Ok(trimmed, group)
    }

    /** Only an exercise no set uses can be deleted (FR-008b). */
    fun deletable(name: String, sets: List<DisplaySet>): Boolean = sets.none { it.key.exercise == name }
}
