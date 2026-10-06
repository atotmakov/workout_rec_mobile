package com.workoutrec

import android.app.Activity
import android.os.Bundle
import android.widget.Toast
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.workoutrec.auth.CredentialManagerAccountPicker
import com.workoutrec.home.AutomationReminder
import com.workoutrec.home.MainScreen
import com.workoutrec.log.LogRoutes
import com.workoutrec.log.LoggingRoute
import com.workoutrec.log.PickerRoute
import com.workoutrec.log.SyncStatusLine
import com.workoutrec.log.TodayRoute
import com.workoutrec.sync.SyncStatus
import com.workoutrec.workout.DisplayModel
import com.workoutrec.setup.AutomationGuideScreen
import com.workoutrec.setup.Browser
import com.workoutrec.setup.ChooseAccountScreen
import com.workoutrec.setup.GuideLinks
import com.workoutrec.setup.GuideStep
import com.workoutrec.setup.RewriteDialog
import com.workoutrec.setup.SetupAction
import com.workoutrec.setup.SetupProgressScreen
import com.workoutrec.setup.SetupState
import com.workoutrec.setup.SetupViewModel
import com.workoutrec.ui.theme.WorkoutRecTheme
import kotlinx.coroutines.launch

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
                val pending by container.workoutRepository.pending.collectAsState(initial = emptyList())
                val syncStatus by container.syncStatusStore.status.collectAsState(initial = SyncStatus())
                val scope = rememberCoroutineScope()
                // The phone's sets belong to this account's spreadsheet; they go when the account does.
                val clearWorkoutData: suspend () -> Unit = {
                    container.workoutRepository.clearAll()
                    container.syncStatusStore.clear()
                }
                MainScreen(
                    account = it,
                    onSwitchAccount = {
                        scope.launch {
                            clearWorkoutData()
                            viewModel.switchAccount(CredentialManagerAccountPicker(context, BuildConfig.WEB_CLIENT_ID))
                        }
                    },
                    onSignOut = { scope.launch { clearWorkoutData(); viewModel.signOut() } },
                    unsyncedCount = DisplayModel.pendingCount(pending),
                    // Research R13: try to sync first, so the warning counts only what is really left.
                    onMenuOpened = container.syncScheduler::requestSync,
                ) { modifier ->
                    TodayRoute(
                        container = container,
                        onAddExercise = { navController.navigate(LogRoutes.PICKER) },
                        onOpenExercise = { navController.navigate(LogRoutes.logging(it, sets = 0)) },
                        modifier = modifier,
                        header = {
                            reminder?.let { r ->
                                AutomationReminder(status = r.status, onFix = { viewModel.onAction(SetupAction.FixAutomation) })
                            }
                            SyncStatusLine(
                                pendingCount = DisplayModel.pendingCount(pending),
                                phase = syncStatus.phase,
                                onSignIn = { viewModel.start() },
                            )
                        },
                    )
                }
            }
        }
        composable(LogRoutes.PICKER) {
            PickerRoute(container, onBack = { navController.popBackStack() }, onStart = { exercise, sets ->
                navController.navigate(LogRoutes.logging(exercise, sets)) { popUpTo(Routes.HOME) }
            })
        }
        composable(
            LogRoutes.LOGGING,
            arguments = listOf(
                navArgument("exercise") { type = NavType.StringType },
                navArgument("sets") { type = NavType.IntType; defaultValue = 0 },
            ),
        ) { entry ->
            LoggingRoute(
                container = container,
                exercise = entry.arguments?.getString("exercise").orEmpty(),
                plannedSets = entry.arguments?.getInt("sets") ?: 0,
                onBack = { navController.popBackStack() },
            )
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
        Browser.open(context, url)
    }
    val privateTab = remember { Browser.supportsPrivateTab(context) }
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
        is SetupState.NeedsEnable -> {
            val link = GuideLinks.enablePage(state.enableUrl, language)
            val copiedMessage = stringResource(R.string.guide_link_copied)
            AutomationGuideScreen(
                step = GuideStep.Enable,
                onOpen = {
                    openedBrowser = true
                    Browser.openPrivate(context, link)
                },
                onContinue = { viewModel.onAction(SetupAction.ContinueForNow) },
                accountEmail = accountEmail,
                privateTab = privateTab,
                onCopyLink = {
                    // The user pastes it into an incognito tab; re-check when they come back.
                    openedBrowser = true
                    Browser.copyToClipboard(context, "Workout Rec", link)
                    Toast.makeText(context, copiedMessage, Toast.LENGTH_SHORT).show()
                },
            )
        }
        is SetupState.Error -> SetupProgressScreen(error = state.error, onRetry = { viewModel.onAction(SetupAction.Retry) })
        SetupState.Ready -> LaunchedEffect(Unit) { onReady() }
    }
}

@Composable
private fun Progress() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}
