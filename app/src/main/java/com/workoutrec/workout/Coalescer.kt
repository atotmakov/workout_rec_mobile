package com.workoutrec.workout

/** New values for a set; its date-time is never edited (FR-006). */
data class SetValues(val exercise: String, val weight: Weight, val reps: Reps)

/** What happens to the pending changes when the user edits or deletes a set. */
sealed interface PendingUpdate {
    data class Upsert(val change: PendingChange) : PendingUpdate
    data class Remove(val id: String) : PendingUpdate
}

/** The coalescing table of data-model.md (research R9, FR-013a). */
object Coalescer {
    fun edit(target: SetRef, pending: List<PendingChange>, values: SetValues, newId: String, now: Long): PendingUpdate = TODO()

    fun delete(target: SetRef, pending: List<PendingChange>, newId: String, now: Long): PendingUpdate = TODO()
}
