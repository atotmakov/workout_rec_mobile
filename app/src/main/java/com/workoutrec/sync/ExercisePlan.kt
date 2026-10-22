package com.workoutrec.sync

import com.workoutrec.google.DrillRename
import com.workoutrec.google.NameReplace
import com.workoutrec.google.SheetDrill
import com.workoutrec.google.SheetLogRow
import com.workoutrec.workout.ExerciseChange
import com.workoutrec.workout.ExerciseChangeKind

/**
 * What one sync does with the waiting exercise changes (feature 004 research R5): the drills part
 * of the write, the changes it handles, notices, and the renames it gives up (new → old name).
 */
data class ExercisePlan(
    val deletes: List<Int> = emptyList(),
    val renames: List<DrillRename> = emptyList(),
    val replaces: List<NameReplace> = emptyList(),
    val appends: List<SheetDrill> = emptyList(),
    /** Handled without writing (already in the sheet, or given up). */
    val done: List<String> = emptyList(),
    /** Handled once the write succeeds. */
    val written: List<String> = emptyList(),
    val notices: List<SyncNotice> = emptyList(),
    val renamesBack: Map<String, String> = emptyMap(),
) {
    companion object {
        /** [changes] in the order the user made them; [drills] and [log] as just read. */
        fun of(changes: List<ExerciseChange>, drills: List<SheetDrill>, log: List<SheetLogRow>): ExercisePlan {
            val deletes = mutableListOf<Int>()
            val renames = mutableListOf<DrillRename>()
            val replaces = mutableListOf<NameReplace>()
            val appends = mutableListOf<SheetDrill>()
            val done = mutableListOf<String>()
            val written = mutableListOf<String>()
            val notices = mutableListOf<SyncNotice>()
            val renamesBack = mutableMapOf<String, String>()
            val targeted = mutableSetOf<Int>()
            fun free(name: String?) = drills.firstOrNull { it.rowIndex !in targeted && it.name == name }

            changes.forEach { change ->
                when (change.kind) {
                    ExerciseChangeKind.ADD -> {
                        val known = (drills.map { it.name } + appends.map { it.name }).any { it.trim().equals(change.name.trim(), ignoreCase = true) }
                        if (known) {
                            done += change.id
                        } else {
                            appends += SheetDrill(change.muscleGroup, change.name)
                            written += change.id
                        }
                    }
                    ExerciseChangeKind.RENAME -> {
                        val row = free(change.lastSeenName)?.takeIf { it.muscleGroup == change.lastSeenGroup }
                        when {
                            row != null -> {
                                targeted += row.rowIndex
                                renames += DrillRename(row.rowIndex, change.muscleGroup, change.name)
                                if (change.lastSeenName != change.name) replaces += NameReplace(change.lastSeenName!!, change.name)
                                written += change.id
                            }
                            // An interrupted run already renamed it.
                            free(change.lastSeenName) == null && drills.any { it.name == change.name } -> done += change.id
                            // Changed in the web UI: the sheet wins, the user is told.
                            else -> {
                                done += change.id
                                notices += SyncNotice.exercise(NoticeKind.EXERCISE_CHANGE_DROPPED, change.name)
                                change.lastSeenName?.takeIf { it != change.name }?.let { renamesBack[change.name] = it }
                            }
                        }
                    }
                    ExerciseChangeKind.DELETE -> {
                        val row = free(change.lastSeenName)
                        when {
                            row == null -> done += change.id
                            log.any { it.exercise == change.lastSeenName } -> {
                                done += change.id
                                notices += SyncNotice.exercise(NoticeKind.EXERCISE_IN_USE, change.lastSeenName!!)
                            }
                            else -> {
                                targeted += row.rowIndex
                                deletes += row.rowIndex
                                written += change.id
                            }
                        }
                    }
                }
            }
            return ExercisePlan(deletes, renames, replaces, appends, done, written, notices, renamesBack)
        }
    }

    val writes: Boolean get() = deletes.isNotEmpty() || renames.isNotEmpty() || replaces.isNotEmpty() || appends.isNotEmpty()
}
