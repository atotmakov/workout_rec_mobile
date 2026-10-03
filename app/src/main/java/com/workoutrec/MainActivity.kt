package com.workoutrec

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.workoutrec.ui.theme.WorkoutRecTheme

object Routes {
    const val SETUP = "setup"
    const val HOME = "home"
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WorkoutRecTheme {
                AppNavHost(startDestination = Routes.SETUP)
            }
        }
    }
}

@Composable
private fun AppNavHost(startDestination: String) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = startDestination) {
        composable(Routes.SETUP) {}
        composable(Routes.HOME) {}
    }
}
