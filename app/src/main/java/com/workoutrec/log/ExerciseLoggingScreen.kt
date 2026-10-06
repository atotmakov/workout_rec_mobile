package com.workoutrec.log

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Test tags of contracts/screens.md "Exercise logging". */
object LoggingTags {
    const val SCREEN = "exercise_logging"
    const val TITLE = "logging_title"
    const val ADD_SET = "add_set"
    fun row(i: Int) = "set_row_$i"
    fun weight(i: Int) = "set_weight_$i"
    fun reps(i: Int) = "set_reps_$i"
    fun weightMinus(i: Int) = "set_weight_minus_$i"
    fun weightPlus(i: Int) = "set_weight_plus_$i"
    fun repsMinus(i: Int) = "set_reps_minus_$i"
    fun repsPlus(i: Int) = "set_reps_plus_$i"
    fun confirm(i: Int) = "set_confirm_$i"
    fun done(i: Int) = "set_done_$i"
}

interface LoggingActions {
    fun onWeightChange(index: Int, text: String)
    fun onRepsChange(index: Int, text: String)
    fun onStepWeight(index: Int, direction: Int)
    fun onStepReps(index: Int, direction: Int)
    fun onConfirm(index: Int)
    fun onAddSet()
}

/** Done sets and set rows of one exercise (FR-003–FR-005). */
@Composable
fun ExerciseLoggingScreen(state: LoggingState, actions: LoggingActions, modifier: Modifier = Modifier): Unit = TODO()
