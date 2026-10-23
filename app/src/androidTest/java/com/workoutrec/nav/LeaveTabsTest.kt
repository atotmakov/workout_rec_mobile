package com.workoutrec.nav

import androidx.activity.ComponentActivity
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.workoutrec.Routes
import com.workoutrec.log.LogRoutes
import java.time.LocalDate
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Issue #35: leaving the tabs for setup (sign out, switch account) after visiting other tabs
 * crashed. Same graph shape as `MainActivity`: setup next to [mainTabs].
 */
@RunWith(AndroidJUnit4::class)
class LeaveTabsTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var nav: NavHostController

    @Before
    fun show() {
        compose.setContent {
            nav = rememberNavController()
            TabScaffold(nav) { modifier ->
                NavHost(nav, startDestination = TabRoutes.MAIN, modifier = modifier) {
                    composable(Routes.SETUP) {
                        Button(onClick = { nav.navigate(TabRoutes.MAIN) { popUpTo(Routes.SETUP) { inclusive = true } } }) {
                            Text("setup")
                        }
                    }
                    mainTabs(
                        today = { composable(Routes.HOME) { Text("today") } },
                        history = {
                            composable(LogRoutes.WORKOUTS) {
                                LazyColumn(Modifier.testTag("past_days")) {
                                    items((0 until 5).toList()) { i ->
                                        Button(onClick = { nav.navigate(LogRoutes.day(LocalDate.of(2026, 1, 1).plusDays(i.toLong()))) }) { Text("item $i") }
                                    }
                                }
                            }
                            composable(LogRoutes.DAY) { Text("day") }
                        },
                        exercises = { composable(TabRoutes.EXERCISES) { Text("exercises") } },
                        money = { composable(TabRoutes.MONEY) { Text("money") } },
                    )
                }
            }
        }
    }

    private fun tab(tab: Tab) = compose.onNodeWithTag(tab.testTag)

    private fun leaveTabs() {
        compose.waitForIdle()
        compose.runOnUiThread { nav.leaveTabs(Routes.SETUP) }
        compose.waitForIdle()
    }

    @Test
    fun leavingFromAnotherTabShowsSetup() {
        tab(Tab.HISTORY).performClick()
        compose.onNodeWithText("item 2").performClick()
        tab(Tab.MONEY).performClick()
        leaveTabs()
        compose.onNodeWithText("setup").assertIsDisplayed()
        compose.onNodeWithTag(BarTags.BAR).assertDoesNotExist()
    }

    @Test
    fun leavingFromTodayShowsSetup() {
        leaveTabs()
        compose.onNodeWithText("setup").assertIsDisplayed()
    }

    @Test
    fun theNextAccountStartsOnFreshTabs() {
        tab(Tab.HISTORY).performClick()
        compose.onNodeWithText("item 2").performClick()
        tab(Tab.MONEY).performClick()
        leaveTabs()
        compose.onNodeWithText("setup").performClick()
        compose.onNodeWithText("today").assertIsDisplayed()
        tab(Tab.TODAY).assertIsSelected()
        tab(Tab.HISTORY).performClick()
        compose.onNodeWithTag("past_days").assertIsDisplayed()
        compose.onNodeWithText("day").assertDoesNotExist()
    }
}
