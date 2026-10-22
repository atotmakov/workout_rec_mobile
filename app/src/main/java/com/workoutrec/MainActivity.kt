package com.workoutrec

import com.workoutrec.exercises.ExerciseDetailRoute
import com.workoutrec.exercises.ExercisesRoute
import com.workoutrec.nav.logToday
import androidx.navigation.NavDestination.Companion.hierarchy
import com.workoutrec.home.AccountActions
import com.workoutrec.nav.PlaceholderTabScreen
import com.workoutrec.nav.Tab
import com.workoutrec.nav.TabRoutes
import com.workoutrec.nav.TabScaffold
import com.workoutrec.nav.mainTabs
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
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
import com.workoutrec.diag.shareLogIntent
import com.workoutrec.home.AutomationReminder
import com.workoutrec.home.DiagnosticsMenuState
import com.workoutrec.home.DiagnosticsTestAction
import com.workoutrec.home.MainScreen
import com.workoutrec.log.DayRoute
import com.workoutrec.log.LogRoutes
import com.workoutrec.log.LoggingRoute
import com.workoutrec.log.PastWorkoutRoute
import com.workoutrec.log.PickerRoute
import com.workoutrec.log.LocalSyncSignIn
import com.workoutrec.log.SyncStatusAction
import com.workoutrec.log.TodayRoute
import com.workoutrec.log.WorkoutsRoute
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
import java.time.LocalDate
import kotlinx.coroutines.launch

object Routes {
    const val SETUP = "setup"
    const val HOME = "home"
}

class MainActivity : ComponentActivity() {
    private val container get() = (application as WorkoutRecApplication).container

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WorkoutRecTheme {
                AppNavHost(container)
            }
            // Feature 003 FR-011: the app start ends with the first frame of the first screen.
            LaunchedEffect(Unit) {
                withFrameNanos { }
                (application as WorkoutRecApplication).appStartProfile.shown()
            }
        }
    }

    // When the app is on screen, for reading the background sync log (quickstart-results.md issue 1).
    override fun onStart() {
        super.onStart()
        container.diagnosticLog.write("app opened")
    }

    override fun onStop() {
        container.diagnosticLog.write("app closed")
        super.onStop()
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
    // Leave the tabs whenever setup needs the user again (e.g. reminder tapped, spreadsheet deleted).
    LaunchedEffect(state, backStack) {
        val inTabs = backStack?.destination?.hierarchy?.any { it.route == TabRoutes.MAIN } == true
        if (inTabs && state != SetupState.Ready) {
            navController.navigate(Routes.SETUP) { popUpTo(TabRoutes.MAIN) { inclusive = true } }
            // Feature 004 research R1: the next account starts on fresh tabs.
            Tab.entries.forEach { navController.clearBackStack(it.route) }
        }
    }

    val account by container.settingsStore.account.collectAsState(initial = null)
    val waiting by container.workoutRepository.waitingCount.collectAsState(initial = 0)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    // The phone's sets belong to this account's spreadsheet; they go when the account does.
    val clearWorkoutData: suspend () -> Unit = {
        container.workoutRepository.clearAll()
        container.syncStatusStore.clear()
    }
    val onSwitchAccount: () -> Unit = {
        scope.launch {
            clearWorkoutData()
            viewModel.switchAccount(CredentialManagerAccountPicker(context, BuildConfig.WEB_CLIENT_ID))
        }
    }
    val onSignOut: () -> Unit = { scope.launch { clearWorkoutData(); viewModel.signOut() } }
    val onShareLog: () -> Unit = { context.startActivity(shareLogIntent(context, container.diagnosticLog)) }
    // The sync icon and the avatar on every tab's first screen (feature 004 spec edge case).
    val accountActions: @Composable () -> Unit = {
        account?.let {
            AccountActions(
                account = it,
                onSwitchAccount = onSwitchAccount,
                onSignOut = onSignOut,
                unsyncedCount = waiting,
                // Research R13: try to sync first, so the warning counts only what is really left.
                onMenuOpened = container.syncScheduler::requestSync,
                onShareLog = onShareLog,
                diagnostics = diagnosticsMenuState(container),
                syncStatus = { SyncStatusAction(container) },
            )
        }
    }

    CompositionLocalProvider(LocalSyncSignIn provides { viewModel.start() }) {
        TabScaffold(navController) { modifier ->
            NavHost(navController = navController, startDestination = Routes.SETUP, modifier = modifier) {
                composable(Routes.SETUP) {
                    val setupAccount by container.settingsStore.account.collectAsState(initial = null)
                    SetupRoute(viewModel, state, accountEmail = setupAccount?.email, onShareLog = onShareLog, onReady = {
                        navController.navigate(TabRoutes.MAIN) { popUpTo(Routes.SETUP) { inclusive = true } }
                    })
                }
                mainTabs(
                    today = {
                        composable(Routes.HOME) {
                            val reminder by viewModel.reminder.collectAsState()
                            LaunchedEffect(Unit) { viewModel.refreshStatus() }
                            account?.let {
                                MainScreen(
                                    account = it,
                                    onSwitchAccount = onSwitchAccount,
                                    onSignOut = onSignOut,
                                    unsyncedCount = waiting,
                                    onMenuOpened = container.syncScheduler::requestSync,
                                    onShareLog = onShareLog,
                                    diagnostics = diagnosticsMenuState(container),
                                    syncStatus = { SyncStatusAction(container) },
                                ) { modifier ->
                                    TodayRoute(
                                        container = container,
                                        onAddExercise = { navController.navigate(LogRoutes.picker()) },
                                        onOpenExercise = { navController.navigate(LogRoutes.logging(it, sets = 0)) },
                                        modifier = modifier,
                                        header = {
                                            reminder?.let { r ->
                                                AutomationReminder(status = r.status, onFix = { viewModel.onAction(SetupAction.FixAutomation) })
                                            }
                                        },
                                    )
                                }
                            }
                        }
                        composable(
                            LogRoutes.PICKER,
                            arguments = listOf(navArgument("exercise") { type = NavType.StringType; nullable = true; defaultValue = null }),
                        ) { entry ->
                            PickerRoute(container, onBack = { navController.popBackStack() }, onStart = { exercise, sets ->
                                navController.navigate(LogRoutes.logging(exercise, sets)) { popUpTo(Routes.HOME) }
                            }, preselected = entry.arguments?.getString("exercise"))
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
                    },
                    history = {
                        composable(LogRoutes.WORKOUTS) {
                            WorkoutsRoute(
                                container,
                                onOpenDay = { navController.navigate(LogRoutes.day(it)) },
                                onPastWorkout = { navController.navigate(LogRoutes.PAST_WORKOUT) },
                                actions = accountActions,
                            )
                        }
                        composable(LogRoutes.DAY, arguments = listOf(navArgument("date") { type = NavType.StringType })) { entry ->
                            DayRoute(container, LocalDate.parse(entry.arguments?.getString("date")), onBack = { navController.popBackStack() })
                        }
                        // Back to the past days after Save or Cancel (spec US2 scenario 2).
                        composable(LogRoutes.PAST_WORKOUT) {
                            PastWorkoutRoute(container, onDone = { navController.popBackStack() })
                        }
                    },
                    exercises = {
                        composable(TabRoutes.EXERCISES) {
                            ExercisesRoute(container, onOpen = { navController.navigate(TabRoutes.exercise(it)) }, actions = accountActions)
                        }
                        composable(TabRoutes.EXERCISE, arguments = listOf(navArgument("name") { type = NavType.StringType })) { entry ->
                            ExerciseDetailRoute(
                                container,
                                name = entry.arguments?.getString("name").orEmpty(),
                                onBack = { navController.popBackStack() },
                                onLogToday = { navController.logToday(it) },
                                onRenamed = { navController.navigate(TabRoutes.exercise(it)) { popUpTo(TabRoutes.EXERCISES) } },
                                actions = { SyncStatusAction(container) },
                            )
                        }
                    },
                    money = {
                        composable(TabRoutes.MONEY) { PlaceholderTabScreen(R.string.tab_money, actions = accountActions) }
                    },
                )
            }
        }
    }
}

@Composable
private fun SetupRoute(viewModel: SetupViewModel, state: SetupState, accountEmail: String?, onShareLog: () -> Unit, onReady: () -> Unit) {
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
        }, onShareLog = onShareLog)
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

/** Feature 003: the Send diagnostics switch and its test actions, for the account menu. */
@Composable
private fun diagnosticsMenuState(container: AppContainer): DiagnosticsMenuState {
    val settings = container.monitoringSettings
    var send by remember { mutableStateOf(settings.sendDiagnostics) }
    val actions = container.diagnosticsTestActions
    return DiagnosticsMenuState(
        sendDiagnostics = send,
        appliesAfterRestart = send && !settings.startedWith,
        onSendDiagnosticsChange = { on ->
            container.monitoringControl.setSendDiagnostics(on)
            send = on
        },
        onTestAction = { action ->
            when (action) {
                DiagnosticsTestAction.CRASH -> actions.crashNow()
                DiagnosticsTestAction.FREEZE -> actions.freeze(10_000)
                DiagnosticsTestAction.TEST_ERROR -> actions.sendTestError()
                DiagnosticsTestAction.HEAP_DUMP -> actions.memorySnapshotNow()
                DiagnosticsTestAction.DISK_CHECK -> actions.diskCheckNow()
                DiagnosticsTestAction.PROFILE_NEXT_START -> actions.profileNextAppStart()
            }
        },
    )
}
