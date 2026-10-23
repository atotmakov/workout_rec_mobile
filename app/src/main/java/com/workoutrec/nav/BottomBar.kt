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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.workoutrec.log.LogRoutes

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
    // SC-006: at the largest font sizes four labels no longer fit a 360 dp phone; the labels
    // stop growing at 1.3× so they stay readable, the rest of the app follows the system size.
    val density = LocalDensity.current
    val labelDensity = Density(density.density, fontScale = density.fontScale.coerceAtMost(MAX_LABEL_FONT_SCALE))
    NavigationBar(Modifier.testTag(BarTags.BAR)) {
        Tab.entries.forEach { tab ->
            NavigationBarItem(
                selected = tab == selected,
                onClick = { onSelect(tab) },
                icon = { Icon(painterResource(tab.icon), contentDescription = null) },
                label = {
                    CompositionLocalProvider(LocalDensity provides labelDensity) {
                        Text(stringResource(tab.label), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
                modifier = Modifier.testTag(tab.testTag),
            )
        }
    }
}

private const val MAX_LABEL_FONT_SCALE = 1.3f

/** "Log today" from an exercise (feature 004 US3 scenario 5): Today, with that exercise's plan sets dialog. */
fun NavController.logToday(exercise: String) {
    selectTab(Tab.TODAY, reselect = false)
    navigate(LogRoutes.picker(exercise))
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

/**
 * Leaves the tabs for [route] (setup), and the next account starts on fresh tabs (research R1).
 * The tabs' saved stacks are cleared first: from [route], outside [TabRoutes.MAIN], the nested
 * tab graphs can't be found and `clearBackStack` throws (issue #35).
 */
fun NavController.leaveTabs(route: String) {
    Tab.entries.forEach { clearBackStack(it.route) }
    navigate(route) { popUpTo(TabRoutes.MAIN) { inclusive = true } }
}
