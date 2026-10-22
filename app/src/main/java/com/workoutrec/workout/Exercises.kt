package com.workoutrec.workout

enum class ExerciseChangeKind { ADD, RENAME, DELETE }

/**
 * An exercise change made in the app and not in the drills tab yet (feature 004 data-model.md
 * `pending_exercise_change`). [name] and [muscleGroup] are the new values (ADD, RENAME); RENAME and
 * DELETE remember the drills values the app saw in [lastSeenName] and [lastSeenGroup].
 */
data class ExerciseChange(
    val id: String,
    val kind: ExerciseChangeKind,
    val name: String,
    val muscleGroup: String,
    val lastSeenName: String? = null,
    val lastSeenGroup: String? = null,
    val createdAt: Long,
)

/** An exercise as the Exercises tab shows it: [pending] while not in the drills tab yet (FR-008b for [deletable]). */
data class ShownExercise(val name: String, val muscleGroup: String, val pending: Boolean, val deletable: Boolean)

sealed interface ExerciseUpdate {
    data class Upsert(val change: ExerciseChange) : ExerciseUpdate
    data class Remove(val id: String) : ExerciseUpdate
}

/** A rename's exercise change and the waiting set changes that get the new name (research R3). */
data class RenameResult(val update: ExerciseUpdate, val sets: List<PendingChange>)

/** The coalescing table of feature 004 data-model.md `pending_exercise_change` (research R3). */
object ExerciseCoalescer {

    fun add(name: String, muscleGroup: String, newId: String, now: Long): ExerciseChange =
        ExerciseChange(newId, ExerciseChangeKind.ADD, name, muscleGroup, createdAt = now)

    /**
     * Renames the exercise shown as [current]. Waiting new and edited sets with that name are renamed
     * too, so they are written under the new name; an edit keeps the values it locates its row by.
     */
    fun rename(
        current: String,
        cached: List<Exercise>,
        pending: List<ExerciseChange>,
        sets: List<PendingChange>,
        name: String,
        muscleGroup: String,
        newId: String,
        now: Long,
    ): RenameResult {
        val update = when (val existing = pending.shownAs(current)) {
            null -> {
                val seen = cached.first { it.name == current }
                ExerciseUpdate.Upsert(
                    ExerciseChange(newId, ExerciseChangeKind.RENAME, name, muscleGroup, seen.name, seen.muscleGroup, createdAt = now),
                )
            }
            else -> ExerciseUpdate.Upsert(existing.copy(name = name, muscleGroup = muscleGroup))
        }
        val renamedSets = sets.filter { it.kind != ChangeKind.DELETE && it.key.exercise == current }
            .map { it.copy(key = it.key.copy(exercise = name)) }
        return RenameResult(update, renamedSets)
    }

    /** Deletes the exercise shown as [current]; a renamed one deletes the drills row it came from. */
    fun delete(current: String, cached: List<Exercise>, pending: List<ExerciseChange>, newId: String, now: Long): ExerciseUpdate =
        when (val existing = pending.shownAs(current)) {
            null -> {
                val seen = cached.first { it.name == current }
                ExerciseUpdate.Upsert(
                    ExerciseChange(newId, ExerciseChangeKind.DELETE, seen.name, seen.muscleGroup, seen.name, seen.muscleGroup, createdAt = now),
                )
            }
            else -> if (existing.kind == ExerciseChangeKind.ADD) {
                ExerciseUpdate.Remove(existing.id)
            } else {
                ExerciseUpdate.Upsert(
                    existing.copy(kind = ExerciseChangeKind.DELETE, name = existing.lastSeenName.orEmpty(), muscleGroup = existing.lastSeenGroup.orEmpty()),
                )
            }
        }

    /** The waiting ADD or RENAME that makes an exercise appear as [name]. */
    private fun List<ExerciseChange>.shownAs(name: String): ExerciseChange? =
        firstOrNull { it.kind != ExerciseChangeKind.DELETE && it.name == name }
}
