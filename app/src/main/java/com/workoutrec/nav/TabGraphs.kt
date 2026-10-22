package com.workoutrec.nav

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.navigation

/**
 * The four tabs as nested graphs inside [TabRoutes.MAIN], Today first (research R1). Each builder
 * adds its tab's screens; the first screen must be the tab's [Tab.startRoute].
 */
fun NavGraphBuilder.mainTabs(
    today: NavGraphBuilder.() -> Unit,
    history: NavGraphBuilder.() -> Unit,
    exercises: NavGraphBuilder.() -> Unit,
    money: NavGraphBuilder.() -> Unit,
) {
    navigation(route = TabRoutes.MAIN, startDestination = Tab.TODAY.route) {
        navigation(route = Tab.TODAY.route, startDestination = Tab.TODAY.startRoute, builder = today)
        navigation(route = Tab.HISTORY.route, startDestination = Tab.HISTORY.startRoute, builder = history)
        navigation(route = Tab.EXERCISES.route, startDestination = Tab.EXERCISES.startRoute, builder = exercises)
        navigation(route = Tab.MONEY.route, startDestination = Tab.MONEY.startRoute, builder = money)
    }
}
