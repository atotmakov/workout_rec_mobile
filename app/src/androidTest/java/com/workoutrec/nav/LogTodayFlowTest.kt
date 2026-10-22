package com.workoutrec.nav

import androidx.activity.ComponentActivity
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.workoutrec.Routes
import com.workoutrec.log.LogRoutes
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

// Feature 004 spec US3 scenario 5: "Log today" from an exercise opens Today with the plan sets dialog
@RunWith(AndroidJUnit4::class)
class LogTodayFlowTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var nav: NavHostController

    @Test
    fun logTodayOpensTodaysPlanSetsForTheExercise() {
        compose.setContent {
            nav = rememberNavController()
            TabScaffold(nav) { modifier ->
                NavHost(nav, startDestination = TabRoutes.MAIN, modifier = modifier) {
                    mainTabs(
                        today = {
                            composable(Routes.HOME) { Text("today") }
                            composable(LogRoutes.PICKER, arguments = listOf(navArgument("exercise") { type = NavType.StringType; nullable = true })) { entry ->
                                Text("plan ${entry.arguments?.getString("exercise")}")
                            }
                        },
                        history = { composable(LogRoutes.WORKOUTS) { Text("history") } },
                        exercises = {
                            composable(TabRoutes.EXERCISES) {
                                Button(onClick = { nav.logToday("Face pull") }) { Text("log today") }
                            }
                        },
                        money = { composable(TabRoutes.MONEY) { Text("money") } },
                    )
                }
            }
        }
        compose.onNodeWithTag(Tab.EXERCISES.testTag).performClick()
        compose.onNodeWithText("log today").performClick()
        compose.onNodeWithTag(Tab.TODAY.testTag).assertIsSelected()
        compose.onNodeWithText("plan Face pull").assertIsDisplayed()
    }
}
