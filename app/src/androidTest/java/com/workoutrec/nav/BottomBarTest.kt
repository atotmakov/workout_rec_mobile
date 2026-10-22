package com.workoutrec.nav

import androidx.activity.ComponentActivity
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.workoutrec.Routes
import com.workoutrec.log.LogRoutes
import java.time.LocalDate
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Feature 004 FR-001–FR-005, SC-001, SC-003 (contracts/screens.md "Bottom bar"). Uses the same
 * [mainTabs] graph and route constants as `MainActivity`, with simple stand-in screens.
 */
@RunWith(AndroidJUnit4::class)
class BottomBarTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var nav: NavHostController

    @Before
    fun show() {
        compose.setContent {
            nav = rememberNavController()
            TabScaffold(nav) { modifier ->
                NavHost(nav, startDestination = TabRoutes.MAIN, modifier = modifier) {
                    mainTabs(
                        today = {
                            composable(Routes.HOME) {
                                Button(onClick = { nav.navigate(LogRoutes.logging("squat", 3)) }) { Text("today") }
                            }
                            composable(
                                LogRoutes.LOGGING,
                                arguments = listOf(
                                    navArgument("exercise") { type = NavType.StringType },
                                    navArgument("sets") { type = NavType.IntType; defaultValue = 0 },
                                ),
                            ) { Text("logging") }
                        },
                        history = {
                            composable(LogRoutes.WORKOUTS) {
                                LazyColumn(Modifier.testTag("past_days")) {
                                    item { Button(onClick = { nav.navigate(LogRoutes.PAST_WORKOUT) }) { Text("past workout") } }
                                    items((0 until 40).toList()) { i ->
                                        Button(onClick = { nav.navigate(LogRoutes.day(LocalDate.of(2026, 1, 1).plusDays(i.toLong()))) }) { Text("item $i") }
                                    }
                                }
                            }
                            composable(LogRoutes.DAY) { Text("day") }
                            composable(LogRoutes.PAST_WORKOUT) { Text("editor") }
                        },
                        exercises = { composable(TabRoutes.EXERCISES) { Text("exercises") } },
                        money = { composable(TabRoutes.MONEY) { Text("money") } },
                    )
                }
            }
        }
    }

    private fun tab(tab: Tab) = compose.onNodeWithTag(tab.testTag)

    @Test
    fun startsOnTodayWithFourTabs() {
        compose.onNodeWithTag(BarTags.BAR).assertIsDisplayed()
        Tab.entries.forEach { tab(it).assertIsDisplayed() }
        tab(Tab.TODAY).assertIsSelected()
        tab(Tab.HISTORY).assertIsNotSelected()
        compose.onNodeWithText("today").assertIsDisplayed()
    }

    @Test
    fun eachTabOpensItsFirstScreenInOneTap() {
        tab(Tab.HISTORY).performClick()
        compose.onNodeWithTag("past_days").assertIsDisplayed()
        tab(Tab.HISTORY).assertIsSelected()
        tab(Tab.EXERCISES).performClick()
        compose.onNodeWithText("exercises").assertIsDisplayed()
        tab(Tab.MONEY).performClick()
        compose.onNodeWithText("money").assertIsDisplayed()
        tab(Tab.TODAY).performClick()
        compose.onNodeWithText("today").assertIsDisplayed()
    }

    @Test
    fun aTabKeepsItsInnerScreen() {
        tab(Tab.HISTORY).performClick()
        compose.onNodeWithText("item 3").performClick()
        compose.onNodeWithText("day").assertIsDisplayed()
        tab(Tab.MONEY).performClick()
        tab(Tab.HISTORY).performClick()
        compose.onNodeWithText("day").assertIsDisplayed()
    }

    @Test
    fun aTabKeepsItsScrollPosition() {
        tab(Tab.HISTORY).performClick()
        compose.onNodeWithTag("past_days").performScrollToIndex(11)
        compose.onNodeWithText("item 10").assertIsDisplayed()
        tab(Tab.MONEY).performClick()
        tab(Tab.HISTORY).performClick()
        compose.onNodeWithText("item 10").assertIsDisplayed()
        compose.onNodeWithText("item 0").assertDoesNotExist()
    }

    @Test
    fun tappingTheSelectedTabReturnsToItsFirstScreen() {
        tab(Tab.HISTORY).performClick()
        compose.onNodeWithText("item 3").performClick()
        tab(Tab.HISTORY).performClick()
        compose.onNodeWithTag("past_days").assertIsDisplayed()
    }

    @Test
    fun backFromAnotherTabGoesToTodayAndBackOnTodayLeaves() {
        tab(Tab.MONEY).performClick()
        back()
        compose.onNodeWithText("today").assertIsDisplayed()
        tab(Tab.TODAY).assertIsSelected()
        // Nothing on Today handles Back, so the system's default applies: the app is left. (Finishing
        // the activity here would leave the test rule without a screen.)
        compose.waitForIdle()
        compose.activityRule.scenario.onActivity { assertFalse(it.onBackPressedDispatcher.hasEnabledCallbacks()) }
    }

    @Test
    fun barHiddenWhileLoggingAndInThePastWorkoutEditor() {
        compose.onNodeWithText("today").performClick()
        compose.onNodeWithText("logging").assertIsDisplayed()
        compose.onNodeWithTag(BarTags.BAR).assertDoesNotExist()
        back()
        compose.onNodeWithTag(BarTags.BAR).assertIsDisplayed()

        tab(Tab.HISTORY).performClick()
        compose.onNodeWithText("past workout").performClick()
        compose.onNodeWithText("editor").assertIsDisplayed()
        compose.onNodeWithTag(BarTags.BAR).assertDoesNotExist()
    }

    /** System Back, once the screen has settled (Back handling follows the screen shown). */
    private fun back() {
        compose.waitForIdle()
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }


}
