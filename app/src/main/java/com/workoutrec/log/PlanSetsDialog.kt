package com.workoutrec.log

import androidx.compose.runtime.Composable

/** Test tags of contracts/screens.md "Plan sets dialog". */
object PlanTags {
    const val DIALOG = "plan_sets"
    const val MINUS = "plan_sets_minus"
    const val PLUS = "plan_sets_plus"
    const val VALUE = "plan_sets_value"
    const val OK = "plan_sets_ok"
}

/** How many sets to log, 1–20 (FR-003). */
@Composable
fun PlanSetsDialog(initial: Int, onConfirm: (Int) -> Unit, onDismiss: () -> Unit): Unit = TODO()
