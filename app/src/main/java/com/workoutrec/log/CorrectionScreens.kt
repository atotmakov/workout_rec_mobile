package com.workoutrec.log

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.workoutrec.sync.SyncNotice
import com.workoutrec.workout.DaySummary
import com.workoutrec.workout.DisplaySet
import com.workoutrec.workout.Exercise
import com.workoutrec.workout.ExerciseGroup
import java.time.LocalDate

/** Test tags of contracts/screens.md "Past days", "Day detail", "Edit set dialog" and notices. */
object CorrectionTags {
    const val PAST_DAYS = "past_days"
    const val DAY_DETAIL = "day_detail"
    const val EDIT_SET = "edit_set"
    const val EDIT_EXERCISE = "edit_exercise"
    const val EDIT_WEIGHT = "edit_weight"
    const val EDIT_REPS = "edit_reps"
    const val EDIT_SAVE = "edit_save"
    const val EDIT_DELETE = "edit_delete"
    const val DELETE_CONFIRM = "delete_set_confirm"
    fun pastDay(date: LocalDate) = "past_day_$date"
    fun daySet(group: Int, set: Int) = "day_set_${group}_$set"
    fun notice(id: Long) = "sync_notice_$id"
    fun noticeDismiss(id: Long) = "sync_notice_dismiss_$id"
}

/** Workout days before today, newest first (FR-013). */
@Composable
fun PastDaysScreen(days: List<DaySummary>, onOpenDay: (LocalDate) -> Unit, modifier: Modifier = Modifier): Unit = TODO()

/** One day's exercises and sets; tap a set to edit it (FR-013). */
@Composable
fun DayDetailScreen(groups: List<ExerciseGroup>, onEditSet: (DisplaySet) -> Unit, modifier: Modifier = Modifier): Unit = TODO()

/** Change exercise, weight and reps, or delete; the date-time is read-only (FR-006, FR-013). */
@Composable
fun EditSetDialog(
    set: DisplaySet,
    exercises: List<Exercise>,
    onSave: (exercise: String, weight: String, reps: String) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
): Unit = TODO()

/** Conflict and workout-row notices with a dismiss button (FR-015, spec edge cases). */
@Composable
fun SyncNoticeList(notices: List<Pair<Long, SyncNotice>>, onDismiss: (Long) -> Unit, modifier: Modifier = Modifier): Unit = TODO()
