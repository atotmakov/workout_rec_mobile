package com.workoutrec.workout

import java.time.LocalDate

/** A set in the downloaded log tab; [rowIndex] is the 0-based sheet row (row 2 → 1). */
data class LogRow(val rowIndex: Int, val key: SetKey)

/** A drills-tab exercise. */
data class Exercise(val name: String, val muscleGroup: String)

enum class ChangeKind { NEW, EDIT, DELETE }

/**
 * A change made in the app and not in the sheet yet (data-model.md `pending_change`).
 * [key] holds the values to write (NEW, EDIT; an edit keeps [lastSeen]'s time) or, for DELETE,
 * equals [lastSeen].
 */
data class PendingChange(
    val id: String,
    val kind: ChangeKind,
    val key: SetKey,
    val lastSeen: SetKey? = null,
    val rowHint: Int? = null,
    val createdAt: Long,
    val draftId: String? = null,
)

enum class SyncState { SYNCED, NOT_SYNCED }

/** What an edit or delete of a displayed set targets. */
sealed interface SetRef {
    data class Row(val rowIndex: Int, val key: SetKey) : SetRef
    data class Pending(val id: String) : SetRef
}

data class DisplaySet(val key: SetKey, val syncState: SyncState, val ref: SetRef)

data class ExerciseGroup(val exercise: String, val sets: List<DisplaySet>)

data class DaySummary(val date: LocalDate, val exerciseCount: Int, val setCount: Int)
