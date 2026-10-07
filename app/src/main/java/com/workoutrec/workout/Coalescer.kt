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

    fun edit(target: SetRef, pending: List<PendingChange>, values: SetValues, newId: String, now: Long): PendingUpdate =
        when (target) {
            is SetRef.Row -> PendingUpdate.Upsert(
                PendingChange(newId, ChangeKind.EDIT, target.key.with(values), lastSeen = target.key, rowHint = target.rowIndex, createdAt = now),
            )
            is SetRef.Pending -> {
                val existing = pending.first { it.id == target.id }
                PendingUpdate.Upsert(existing.copy(key = existing.key.with(values)))
            }
        }

    fun delete(target: SetRef, pending: List<PendingChange>, newId: String, now: Long): PendingUpdate =
        when (target) {
            is SetRef.Row -> PendingUpdate.Upsert(
                PendingChange(newId, ChangeKind.DELETE, target.key, lastSeen = target.key, rowHint = target.rowIndex, createdAt = now),
            )
            is SetRef.Pending -> {
                val existing = pending.first { it.id == target.id }
                when (existing.kind) {
                    ChangeKind.NEW -> PendingUpdate.Remove(existing.id)
                    else -> PendingUpdate.Upsert(existing.copy(kind = ChangeKind.DELETE, key = existing.lastSeen ?: existing.key))
                }
            }
        }

    private fun SetKey.with(values: SetValues) = copy(exercise = values.exercise, weight = values.weight, reps = values.reps)
}
