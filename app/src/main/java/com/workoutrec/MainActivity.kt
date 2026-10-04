package com.workoutrec

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.workoutrec.auth.CredentialManagerAccountPicker
import com.workoutrec.home.AutomationReminder
import com.workoutrec.home.MainScreen
import com.workoutrec.setup.AutomationGuideScreen
import com.workoutrec.setup.ChooseAccountScreen
import com.workoutrec.setup.GuideLinks
import com.workoutrec.setup.GuideStep
import com.workoutrec.setup.RewriteDialog
import com.workoutrec.setup.SetupAction
import com.workoutrec.setup.SetupProgressScreen
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
    val viewModel: SetupViewModel = viewModel(
        factory = viewModelFactory {
            initializer { SetupViewModel(container.accountRepository, container.spreadsheetSetupFlow) }
        },
    )
    LaunchedEffect(Unit) { viewModel.start() }

    val state by viewModel.state.collectAsState()
    val backStack by navController.currentBackStackEntryAsState()
    // Leave home whenever setup needs the user again (e.g. reminder tapped, spreadsheet deleted).
    LaunchedEffect(state, backStack) {
        val onHome = backStack?.destination?.route == Routes.HOME
        if (onHome && state != SetupState.Ready) {
            navController.navigate(Routes.SETUP) { popUpTo(Routes.HOME) { inclusive = true } }
        }
    }

    NavHost(navController = navController, startDestination = Routes.SETUP) {
        composable(Routes.SETUP) {
            val setupAccount by container.settingsStore.account.collectAsState(initial = null)
            SetupRoute(viewModel, state, accountEmail = setupAccount?.email, onReady = {
                navController.navigate(Routes.HOME) { popUpTo(Routes.SETUP) { inclusive = true } }
            })
        }
        composable(Routes.HOME) {
            val account by container.settingsStore.account.collectAsState(initial = null)
            val reminder by viewModel.reminder.collectAsState()
            LaunchedEffect(Unit) { viewModel.refreshStatus() }
            val context = LocalContext.current
            account?.let {
                MainScreen(
                    account = it,
                    onSwitchAccount = { viewModel.switchAccount(CredentialManagerAccountPicker(context, BuildConfig.WEB_CLIENT_ID)) },
                    onSignOut = { viewModel.signOut() },
                ) { modifier ->
                    reminder?.let { r ->
                        AutomationReminder(status = r.status, onFix = { viewModel.onAction(SetupAction.FixAutomation) }, modifier = modifier)
                    }
                }
            }
        }
    }
}

@Composable
private fun SetupRoute(viewModel: SetupViewModel, state: SetupState, accountEmail: String?, onReady: () -> Unit) {
    val context = LocalContext.current
    val language = LocalConfiguration.current.locales[0].language
    val consentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        viewModel.onConsentResult(if (result.resultCode == Activity.RESULT_OK) result.data else null)
    }
    // Coming back from the browser after step 1 or 2: check again (research R14). Only after the
    // user actually opened the browser, so a recheck never triggers another recheck.
    var openedBrowser by rememberSaveable { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) {
        if (openedBrowser) {
            openedBrowser = false
            viewModel.onAction(SetupAction.Recheck)
        }
        onPauseOrDispose {}
    }
    val open = { url: String ->
        openedBrowser = true
        openInBrowser(context, url)
    }
    when (state) {
        SetupState.Starting, SetupState.Authorizing, SetupState.Working -> SetupProgressScreen(error = null, onRetry = {})
        is SetupState.SignedOut -> ChooseAccountScreen(message = state.message, onChooseAccount = {
            viewModel.chooseAccount(CredentialManagerAccountPicker(context, BuildConfig.WEB_CLIENT_ID))
        })
        is SetupState.NeedsConsent -> {
            Progress()
            LaunchedEffect(state) {
                state.consent.pendingIntent?.let {
                    consentLauncher.launch(IntentSenderRequest.Builder(it.intentSender).build())
                }
            }
        }
        is SetupState.AskRewrite -> {
            Progress()
            RewriteDialog(onAnswer = { yes -> viewModel.onAction(SetupAction.AnswerRewrite(yes)) })
        }
        SetupState.NeedsApiSetting -> AutomationGuideScreen(
            step = GuideStep.ApiSetting,
            onOpen = { open(GuideLinks.appsScriptSettings(accountEmail)) },
            onContinue = { viewModel.onAction(SetupAction.ContinueForNow) },
        )
        is SetupState.NeedsEnable -> AutomationGuideScreen(
            step = GuideStep.Enable,
            onOpen = { open(GuideLinks.enablePage(state.enableUrl, language)) },
            onContinue = { viewModel.onAction(SetupAction.ContinueForNow) },
        )
        is SetupState.Error -> SetupProgressScreen(error = state.error, onRetry = { viewModel.onAction(SetupAction.Retry) })
        SetupState.Ready -> LaunchedEffect(Unit) { onReady() }
    }
}

/** Custom Tabs share the browser's Google sign-in (research R14). */
private fun openInBrowser(context: Context, url: String) {
    CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(url))
}

@Composable
private fun Progress() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}
