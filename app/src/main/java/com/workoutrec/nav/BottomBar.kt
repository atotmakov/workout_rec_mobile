package com.workoutrec.nav

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState

object BarTags {
    const val BAR = "bottom_bar"
}

/**
 * The screens with the bottom bar under them; [content] gets a modifier that keeps it above the
 * bar, and the screens' own scaffolds then skip the navigation bar inset (FR-005).
 */
@Composable
fun TabScaffold(navController: NavHostController, content: @Composable (Modifier) -> Unit) {
    val entry by navController.currentBackStackEntryAsState()
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        bottomBar = { if (barVisible(entry?.destination?.route)) BottomBar(navController) },
    ) { padding ->
        content(Modifier.padding(padding).consumeWindowInsets(padding))
    }
}

/** Four tabs with icon and label (FR-001, FR-002), following [navController]. */
@Composable
fun BottomBar(navController: NavHostController) {
    val entry by navController.currentBackStackEntryAsState()
    val destination = entry?.destination
    val selected = Tab.entries.firstOrNull { tab -> destination?.hierarchy?.any { it.route == tab.route } == true }
    TabBar(selected, onSelect = { tab -> navController.selectTab(tab, reselect = tab == selected) })
}

/** The bar itself; [selected] is highlighted. */
@Composable
fun TabBar(selected: Tab?, onSelect: (Tab) -> Unit) {
    NavigationBar(Modifier.testTag(BarTags.BAR)) {
        Tab.entries.forEach { tab ->
            NavigationBarItem(
                selected = tab == selected,
                onClick = { onSelect(tab) },
                icon = { Icon(painterResource(tab.icon), contentDescription = null) },
                label = { Text(stringResource(tab.label), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                modifier = Modifier.testTag(tab.testTag),
            )
        }
    }
}

/**
 * Research R1: every tab keeps its own stack (FR-003); tapping the selected tab goes back to its
 * first screen. Popping up to Today's first screen makes Back from any other tab land on Today
 * (FR-004).
 */
fun NavController.selectTab(tab: Tab, reselect: Boolean) {
    if (reselect) {
        popBackStack(tab.startRoute, inclusive = false)
        return
    }
    val main = graph.findNode(TabRoutes.MAIN) as NavGraph
    navigate(tab.route) {
        popUpTo(main.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
