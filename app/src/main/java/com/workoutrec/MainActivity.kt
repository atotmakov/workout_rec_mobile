package com.workoutrec

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.workoutrec.auth.CredentialManagerAccountPicker
import com.workoutrec.home.MainScreen
import com.workoutrec.setup.ChooseAccountScreen
import com.workoutrec.setup.PostAuthStep
import com.workoutrec.setup.SetupState
import com.workoutrec.setup.SetupViewModel
import com.workoutrec.ui.theme.WorkoutRecTheme

object Routes {
    const val SETUP = "setup"
    const val HOME = "home"
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as WorkoutRecApplication).container
        setContent {
            WorkoutRecTheme {
                AppNavHost(container)
            }
        }
    }
}

@Composable
private fun AppNavHost(container: AppContainer) {
    val navController = rememberNavController()
    val setupViewModel: SetupViewModel = viewModel(
        factory = viewModelFactory {
            initializer { SetupViewModel(container.accountRepository, PostAuthStep { SetupState.Ready }) }
        },
    )
    LaunchedEffect(Unit) { setupViewModel.start() }

    NavHost(navController = navController, startDestination = Routes.SETUP) {
        composable(Routes.SETUP) {
            SetupRoute(setupViewModel, onReady = {
                navController.navigate(Routes.HOME) { popUpTo(Routes.SETUP) { inclusive = true } }
            })
        }
        composable(Routes.HOME) {
            val account by container.settingsStore.account.collectAsState(initial = null)
            account?.let { MainScreen(account = it, onSwitchAccount = {}, onSignOut = {}) }
        }
    }
}

@Composable
private fun SetupRoute(viewModel: SetupViewModel, onReady: () -> Unit) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val consentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        viewModel.onConsentResult(if (result.resultCode == Activity.RESULT_OK) result.data else null)
    }
    when (val current = state) {
        SetupState.Starting, SetupState.Authorizing -> Progress()
        is SetupState.SignedOut -> ChooseAccountScreen(message = current.message, onChooseAccount = {
            viewModel.chooseAccount(CredentialManagerAccountPicker(context, BuildConfig.WEB_CLIENT_ID))
        })
        is SetupState.NeedsConsent -> {
            Progress()
            LaunchedEffect(current) {
                current.consent.pendingIntent?.let {
                    consentLauncher.launch(IntentSenderRequest.Builder(it.intentSender).build())
                }
            }
        }
        SetupState.Ready -> LaunchedEffect(Unit) { onReady() }
        else -> Progress()
    }
}

@Composable
private fun Progress() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}
