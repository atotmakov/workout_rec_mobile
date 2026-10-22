package com.workoutrec.nav

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.workoutrec.R
import com.workoutrec.Routes
import com.workoutrec.log.LogRoutes

/** Routes of the tab graphs and of the screens this feature adds (feature 004 research R1). */
object TabRoutes {
    /** The graph holding the four tabs; setup stays outside it. */
    const val MAIN = "main"
    const val EXERCISES = "exercises"
    const val EXERCISE = "exercise/{name}"
    fun exercise(name: String) = "exercise/${android.net.Uri.encode(name)}"
    const val MONEY = "money"
}

/** The bottom bar's tabs, in bar order (FR-001). */
enum class Tab(
    /** Route of the tab's nested graph. */
    val route: String,
    /** The tab's first screen. */
    val startRoute: String,
    @DrawableRes val icon: Int,
    @StringRes val label: Int,
) {
    TODAY("tab/today", Routes.HOME, R.drawable.ic_tab_today, R.string.tab_today),
    HISTORY("tab/history", LogRoutes.WORKOUTS, R.drawable.ic_tab_history, R.string.tab_history),
    EXERCISES("tab/exercises", TabRoutes.EXERCISES, R.drawable.ic_tab_exercises, R.string.tab_exercises),
    MONEY("tab/money", TabRoutes.MONEY, R.drawable.ic_tab_money, R.string.tab_money),
    ;

    val testTag: String get() = "bar_tab_${name.lowercase()}"
}

/** Screens that get the full height: logging and the past workout editor; setup has no tabs (FR-005). */
private val HIDDEN_BAR = setOf(LogRoutes.LOGGING, LogRoutes.PAST_WORKOUT, Routes.SETUP)

fun barVisible(route: String?): Boolean = route != null && route !in HIDDEN_BAR

/** Which tab a screen belongs to (research R2); null outside the tabs. */
fun tabOf(route: String?): Tab? = when (route) {
    Routes.HOME, LogRoutes.PICKER, LogRoutes.LOGGING -> Tab.TODAY
    LogRoutes.WORKOUTS, LogRoutes.DAY, LogRoutes.PAST_WORKOUT -> Tab.HISTORY
    TabRoutes.EXERCISES, TabRoutes.EXERCISE -> Tab.EXERCISES
    TabRoutes.MONEY -> Tab.MONEY
    else -> null
}
