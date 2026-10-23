package com.workoutrec.gallery

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.workoutrec.money.BalanceNote
import com.workoutrec.money.BalanceNoteState
import com.workoutrec.money.MoneyError
import com.workoutrec.money.MoneyScreen
import com.workoutrec.money.MoneyState
import com.workoutrec.money.PaymentDialog
import com.workoutrec.workout.DisplayModel
import com.workoutrec.workout.ShownPayment
import com.workoutrec.exercises.ExerciseDetailScreen
import com.workoutrec.exercises.ExerciseDialog
import com.workoutrec.exercises.ExerciseRules
import com.workoutrec.exercises.ExercisesScreen
import com.workoutrec.workout.ShownExercise
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import com.workoutrec.home.AccountActions
import com.workoutrec.log.HistoryScreen
import com.workoutrec.nav.Tab
import com.workoutrec.nav.TabBar
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.workoutrec.R
import com.workoutrec.automation.AutomationStatus
import com.workoutrec.data.SelectedAccount
import com.workoutrec.google.ApiError
import com.workoutrec.home.AutomationReminder
import com.workoutrec.home.MainScreen
import com.workoutrec.home.SignOutDialog
import com.workoutrec.home.SwitchAccountDialog
import com.workoutrec.log.DayDetailScreen
import com.workoutrec.log.DoneSet
import com.workoutrec.log.DraftSet
import com.workoutrec.log.EditSetDialog
import com.workoutrec.log.ExerciseLoggingScreen
import com.workoutrec.log.ExercisePicker
import com.workoutrec.log.LoggingActions
import com.workoutrec.log.LoggingState
import com.workoutrec.log.PastDaysScreen
import com.workoutrec.log.PastWorkoutDialog
import com.workoutrec.log.PastWorkoutEditor
import com.workoutrec.log.PastWorkoutState
import com.workoutrec.log.PlanSetsDialog
import com.workoutrec.log.SetRowState
import com.workoutrec.log.SyncNoticeList
import com.workoutrec.log.SyncStatusButton
import com.workoutrec.log.TodayScreen
import com.workoutrec.setup.AutomationGuideScreen
import com.workoutrec.setup.ChooseAccountScreen
import com.workoutrec.setup.GuideStep
import com.workoutrec.setup.RewriteDialog
import com.workoutrec.setup.SetupMessage
import com.workoutrec.setup.SetupProgressScreen
import com.workoutrec.sync.FailReason
import com.workoutrec.sync.NoticeKind
import com.workoutrec.sync.SyncNotice
import com.workoutrec.sync.SyncPhase
import com.workoutrec.ui.theme.WorkoutRecTheme
import com.workoutrec.workout.DaySummary
import com.workoutrec.workout.DisplaySet
import com.workoutrec.workout.Exercise
import com.workoutrec.workout.ExerciseGroup
import com.workoutrec.workout.InvalidReason
import com.workoutrec.workout.LastTime
import com.workoutrec.workout.Record
import com.workoutrec.workout.Reps
import com.workoutrec.workout.SetKey
import com.workoutrec.workout.SetRef
import com.workoutrec.workout.SetValues
import com.workoutrec.workout.SyncState
import com.workoutrec.workout.Weight
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Every screen and state of the app with sample data, for the redesign: Android Studio shows them
 * as previews, and `ScreenGalleryTest` renders them to PNGs. No account, network or database.
 *
 * Screens shown inside a route (top bar, Today's header) are framed here the way `LogRoutes` and
 * `MainActivity` frame them, so a screenshot looks like the real screen.
 */
data class GalleryScreen(
    /** File-name friendly, unique; the number orders the gallery. */
    val name: String,
    /** A popup the test opens by tapping, since the screen keeps it closed in its own state. */
    val open: GalleryOpen? = null,
    val content: @Composable () -> Unit,
) {
    override fun toString() = name
}

enum class GalleryOpen { ACCOUNT_MENU, DIAGNOSTICS_TEST_DIALOG, SYNC_DETAILS }

object ScreenGallery {
    val screens: List<GalleryScreen> = listOf(
        // Setup
        GalleryScreen("01-welcome") { ChooseAccountScreen(message = null, onChooseAccount = {}) },
        GalleryScreen("02-welcome-account-required") { ChooseAccountScreen(message = SetupMessage.AccountRequired, onChooseAccount = {}) },
        GalleryScreen("03-welcome-access-revoked") { ChooseAccountScreen(message = SetupMessage.AccessRevoked, onChooseAccount = {}) },
        GalleryScreen("04-setup-working") { SetupProgressScreen(error = null, onRetry = {}) },
        GalleryScreen("05-setup-error-offline") { SetupProgressScreen(error = ApiError.Offline, onRetry = {}) },
        GalleryScreen("06-setup-error-access") { SetupProgressScreen(error = ApiError.AccessDenied("403"), onRetry = {}) },
        GalleryScreen("07-setup-rewrite-dialog") { SetupProgressScreen(error = null, onRetry = {}); RewriteDialog(onAnswer = {}) },
        GalleryScreen("08-guide-step1-api-setting") { AutomationGuideScreen(step = GuideStep.ApiSetting, onOpen = {}, onContinue = {}) },
        GalleryScreen("09-guide-step2-private-tab") {
            AutomationGuideScreen(step = GuideStep.Enable, onOpen = {}, onContinue = {}, accountEmail = Samples.ACCOUNT.email)
        },
        GalleryScreen("10-guide-step2-copy-link") {
            AutomationGuideScreen(step = GuideStep.Enable, onOpen = {}, onContinue = {}, accountEmail = Samples.ACCOUNT.email, privateTab = false, onCopyLink = {})
        },
        // Today (the main screen)
        GalleryScreen("11-today-empty") { TodayFrame(groups = emptyList()) },
        GalleryScreen("12-today-workout") { TodayFrame(groups = Samples.todayGroups()) },
        GalleryScreen("12a-sync-details-synced", open = GalleryOpen.SYNC_DETAILS) { TodayFrame(groups = Samples.todayGroups()) },
        GalleryScreen("13-today-not-synced") { TodayFrame(groups = Samples.todayGroups(lastNotSynced = true), pendingCount = 2) },
        GalleryScreen("13a-sync-details-waiting", open = GalleryOpen.SYNC_DETAILS) {
            TodayFrame(groups = Samples.todayGroups(lastNotSynced = true), pendingCount = 2)
        },
        GalleryScreen("13b-today-syncing") {
            TodayFrame(groups = Samples.todayGroups(lastNotSynced = true), pendingCount = 2, phase = SyncPhase.Running)
        },
        GalleryScreen("14-today-sync-failing") {
            TodayFrame(groups = Samples.todayGroups(lastNotSynced = true), pendingCount = 2, phase = SyncPhase.Failing(FailReason.NETWORK))
        },
        GalleryScreen("14a-sync-details-failing", open = GalleryOpen.SYNC_DETAILS) {
            TodayFrame(groups = Samples.todayGroups(lastNotSynced = true), pendingCount = 2, phase = SyncPhase.Failing(FailReason.NETWORK))
        },
        GalleryScreen("15-today-needs-sign-in") { TodayFrame(groups = Samples.todayGroups(), pendingCount = 1, phase = SyncPhase.NeedsSignIn) },
        GalleryScreen("15a-sync-details-sign-in", open = GalleryOpen.SYNC_DETAILS) {
            TodayFrame(groups = Samples.todayGroups(), pendingCount = 1, phase = SyncPhase.NeedsSignIn)
        },
        GalleryScreen("16-today-automation-reminder") { TodayFrame(groups = Samples.todayGroups(), automation = AutomationStatus.NotEnabled) },
        GalleryScreen("17-today-sync-notices") { TodayFrame(groups = Samples.todayGroups(), notices = Samples.notices()) },
        GalleryScreen("18-account-menu", open = GalleryOpen.ACCOUNT_MENU) { TodayFrame(groups = Samples.todayGroups(), pendingCount = 1) },
        GalleryScreen("19-diagnostics-test-dialog", open = GalleryOpen.DIAGNOSTICS_TEST_DIALOG) { TodayFrame(groups = Samples.todayGroups()) },
        GalleryScreen("20-sign-out-dialog") {
            TodayFrame(groups = Samples.todayGroups(lastNotSynced = true), pendingCount = 2)
            SignOutDialog(onConfirm = {}, onCancel = {}, unsyncedCount = 2)
        },
        GalleryScreen("21-switch-account-dialog") {
            TodayFrame(groups = Samples.todayGroups(lastNotSynced = true), pendingCount = 2)
            SwitchAccountDialog(unsyncedCount = 2, onConfirm = {}, onCancel = {})
        },
        // Adding an exercise and logging sets
        GalleryScreen("22-picker") {
            RouteFrame(stringResource(R.string.picker_title), tab = Tab.TODAY) { modifier ->
                ExercisePicker(exercises = Samples.exercises(), recent = Samples.recent(), onPick = {}, onOpenDrills = {}, modifier = modifier)
            }
        },
        GalleryScreen("23-picker-empty") {
            RouteFrame(stringResource(R.string.picker_title), tab = Tab.TODAY) { modifier ->
                ExercisePicker(exercises = emptyList(), recent = emptyList(), onPick = {}, onOpenDrills = {}, modifier = modifier)
            }
        },
        GalleryScreen("24-plan-sets-dialog") {
            RouteFrame(stringResource(R.string.picker_title), tab = Tab.TODAY) { modifier ->
                ExercisePicker(exercises = Samples.exercises(), recent = Samples.recent(), onPick = {}, onOpenDrills = {}, modifier = modifier)
            }
            PlanSetsDialog(initial = 3, onConfirm = {}, onDismiss = {})
        },
        GalleryScreen("25-logging-start") { LoggingFrame(Samples.loggingStart()) },
        GalleryScreen("26-logging-in-progress") { LoggingFrame(Samples.loggingInProgress(), pendingCount = 1) },
        GalleryScreen("27-logging-invalid-value") { LoggingFrame(Samples.loggingInvalid()) },
        // History and corrections
        GalleryScreen("28-past-days") {
            BarFrame(Tab.HISTORY) { modifier ->
                HistoryScreen(Samples.pastDays(), onOpenDay = {}, onPastWorkout = {}, modifier = modifier, actions = { SampleAccountActions() })
            }
        },
        GalleryScreen("28a-history-empty") {
            BarFrame(Tab.HISTORY) { modifier ->
                HistoryScreen(emptyList(), onOpenDay = {}, onPastWorkout = {}, modifier = modifier, actions = { SampleAccountActions() })
            }
        },
        GalleryScreen("29-day-detail") {
            BarFrame(Tab.HISTORY) { barModifier ->
                Box(barModifier) {
                    RouteFrame(Samples.DAY_TITLE, pendingCount = 1) { modifier -> DayDetailScreen(Samples.dayGroups(), onEditSet = {}, modifier = modifier) }
                }
            }
        },
        GalleryScreen("30-edit-set-dialog") {
            BarFrame(Tab.HISTORY) { barModifier ->
                Box(barModifier) {
                    RouteFrame(Samples.DAY_TITLE, pendingCount = 1) { modifier -> DayDetailScreen(Samples.dayGroups(), onEditSet = {}, modifier = modifier) }
                }
            }
            EditSetDialog(set = Samples.dayGroups().first().sets.first(), exercises = Samples.exercises(), onSave = { _, _, _ -> }, onDelete = {}, onDismiss = {}, zone = Samples.ZONE)
        },
        GalleryScreen("31-past-workout-dialog") {
            RouteFrame(stringResource(R.string.past_workout_title)) { }
            PastWorkoutDialog(initialDate = Samples.YESTERDAY, onStart = { _, _, _ -> null }, onDismiss = {})
        },
        GalleryScreen("32-past-workout-editor") {
            RouteFrame(stringResource(R.string.past_workout_title)) { modifier ->
                Box(modifier) {
                    PastWorkoutEditor(Samples.pastWorkout(), Samples.exercises(), onAddSet = {}, onRemoveSet = {}, onSave = {}, onCancel = {}, zone = Samples.ZONE)
                }
            }
        },
        // Feature 004: tabs not built yet
        // Feature 004 SC-006: the bar on a 360 dp phone at the largest font size
        GalleryScreen("42-tabs-large-font") {
            val density = LocalDensity.current
            Box(Modifier.width(360.dp).fillMaxHeight()) {
                CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                    TodayFrame(groups = Samples.todayGroups())
                }
            }
        },
        // Feature 004 US3: Exercises tab
        GalleryScreen("50-exercises") {
            BarFrame(Tab.EXERCISES) { modifier ->
                ExercisesScreen(Samples.shownExercises(), onOpen = {}, onAdd = {}, onRefresh = {}, modifier = modifier, actions = { SampleAccountActions(pendingCount = 1) })
            }
        },
        GalleryScreen("51-exercise-detail") {
            BarFrame(Tab.EXERCISES) { modifier ->
                ExerciseDetailScreen(
                    Samples.shownExercises().first { it.pending },
                    lastTime = null,
                    record = null,
                    onLogToday = {},
                    onRename = {},
                    onDelete = {},
                    onBack = {},
                    modifier = modifier,
                    actions = { SampleSyncStatus(1, SyncPhase.Idle) },
                )
            }
        },
        GalleryScreen("52-exercise-detail-in-use") {
            BarFrame(Tab.EXERCISES) { modifier ->
                ExerciseDetailScreen(
                    Samples.shownExercises().first(),
                    lastTime = Samples.benchLastTime(),
                    record = Record(Weight.ofHundredths(7500), Reps(5)),
                    onLogToday = {},
                    onRename = {},
                    onDelete = {},
                    onBack = {},
                    modifier = modifier,
                    actions = { SampleSyncStatus(0, SyncPhase.Idle) },
                )
            }
        },
        GalleryScreen("53-exercise-dialog-error") {
            BarFrame(Tab.EXERCISES) { modifier ->
                ExercisesScreen(Samples.shownExercises(), onOpen = {}, onAdd = {}, onRefresh = {}, modifier = modifier, actions = { SampleAccountActions(pendingCount = 1) })
            }
            val shown = Samples.shownExercises()
            ExerciseDialog(
                title = stringResource(R.string.exercise_add_title),
                initialName = Samples.squat(),
                initialGroup = shown.first { it.name == Samples.squat() }.muscleGroup,
                groups = shown.map { it.muscleGroup }.distinct(),
                check = { name, group -> ExerciseRules.check(name, group, shown) },
                onSave = { _, _ -> },
                onDismiss = {},
            )
        },
        // Feature 004 US4: Money tab
        GalleryScreen("60-money") { MoneyFrame(Samples.moneyState(balance = 11.0, newPayment = true)) },
        GalleryScreen("61-money-owed") { MoneyFrame(Samples.moneyState(balance = -2.0)) },
        GalleryScreen("62-money-automation-off") { MoneyFrame(Samples.moneyState(balance = 11.0, note = BalanceNote.Off)) },
        GalleryScreen("63-payment-dialog") {
            MoneyFrame(Samples.moneyState(balance = 11.0))
            PaymentDialog(today = Samples.TODAY, defaultWorkouts = 8, defaultSum = 800_000, onSave = { _, _, _ -> }, onDismiss = {})
        },
        GalleryScreen("64-money-empty") { MoneyFrame(Samples.moneyState(balance = null, payments = false, error = MoneyError.MISSING_TAB)) },
    )
}

/** The main screen with Today and its header, as `MainActivity` and `TodayRoute` build it. */
@Composable
private fun TodayFrame(
    groups: List<ExerciseGroup>,
    pendingCount: Int = 0,
    phase: SyncPhase = SyncPhase.Idle,
    automation: AutomationStatus? = null,
    notices: List<Pair<Long, SyncNotice>> = emptyList(),
) {
    BarFrame(Tab.TODAY) { barModifier ->
        Box(barModifier) {
            MainScreen(
                account = Samples.ACCOUNT,
                onSwitchAccount = {},
                onSignOut = {},
                unsyncedCount = pendingCount,
                syncStatus = { SampleSyncStatus(pendingCount, phase) },
            ) { modifier ->
                TodayScreen(
                    groups, onAddExercise = {}, onOpenExercise = {}, modifier = modifier,
                    header = {
                        automation?.let { AutomationReminder(status = it, onFix = {}) }
                        SyncNoticeList(notices, onDismiss = {})
                    },
                    // Feature 006: the Paid/Free control with no earlier day (Free).
                    today = java.time.LocalDate.now(),
                    onDayStatus = {},
                )
            }
        }
    }
}

/** A tab's screen with the bottom bar under it, as `TabScaffold` shows it (feature 004). */
@Composable
private fun BarFrame(tab: Tab, content: @Composable (Modifier) -> Unit) {
    Scaffold(contentWindowInsets = WindowInsets(0), bottomBar = { TabBar(selected = tab, onSelect = {}) }) { padding ->
        content(Modifier.padding(padding).consumeWindowInsets(padding))
    }
}

@Composable
private fun MoneyFrame(state: MoneyState) {
    BarFrame(Tab.MONEY) { modifier ->
        MoneyScreen(state, Samples.ZONE, onAdd = {}, onRefresh = {}, onEnableAutomation = {}, modifier = modifier, actions = { SampleAccountActions(pendingCount = if (state.payments.any { it.pending }) 1 else 0) })
    }
}

/** The sync icon and the avatar, as on every tab's first screen. */
@Composable
private fun SampleAccountActions(pendingCount: Int = 0) {
    AccountActions(account = Samples.ACCOUNT, onSwitchAccount = {}, onSignOut = {}, syncStatus = { SampleSyncStatus(pendingCount, SyncPhase.Idle) })
}

@Composable
private fun SampleSyncStatus(pendingCount: Int, phase: SyncPhase) {
    SyncStatusButton(
        pendingCount = pendingCount,
        phase = phase,
        lastSuccessAt = Samples.at(9, 30) * 1000,
        zone = Samples.ZONE,
        onSyncNow = {},
        onSignIn = {},
    )
}

/** A screen under a top bar with "←" and the sync icon, as the routes in `LogRoutes` show it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RouteFrame(title: String, pendingCount: Int = 0, tab: Tab? = null, content: @Composable (Modifier) -> Unit) {
    if (tab != null) {
        BarFrame(tab) { barModifier -> Box(barModifier) { RouteFrame(title, pendingCount, content = content) } }
        return
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = { TextButton(onClick = {}) { Text("←") } },
                actions = { SampleSyncStatus(pendingCount, SyncPhase.Idle) },
            )
        },
    ) { padding ->
        content(Modifier.padding(padding))
    }
}

@Composable
private fun LoggingFrame(state: LoggingState, pendingCount: Int = 0) {
    RouteFrame(state.exercise, pendingCount) { modifier -> ExerciseLoggingScreen(state, NoLoggingActions, modifier) }
}

private object NoLoggingActions : LoggingActions {
    override fun onWeightChange(index: Int, text: String) {}
    override fun onRepsChange(index: Int, text: String) {}
    override fun onStepWeight(index: Int, direction: Int) {}
    override fun onStepReps(index: Int, direction: Int) {}
    override fun onConfirm(index: Int) {}
    override fun onAddSet() {}
}

/** Sample workouts; exercise names follow the screen's language, like a real spreadsheet would. */
private object Samples {
    val ZONE: ZoneId = ZoneId.of("Europe/Moscow")
    val TODAY: LocalDate = LocalDate.of(2026, 10, 16)
    val YESTERDAY: LocalDate = TODAY.minusDays(1)
    const val DAY_TITLE = "Oct 14, 2026"
    val ACCOUNT = SelectedAccount(email = "alex.example@gmail.com", displayName = "Alex Example", photoUrl = null)

    /** Epoch seconds, the unit of set times (`SetKey`, `DraftSet`). */
    fun at(hour: Int, minute: Int, day: LocalDate = TODAY): Long =
        LocalDateTime.of(day, java.time.LocalTime.of(hour, minute)).atZone(ZONE).toEpochSecond()

    @Composable
    private fun ru(): Boolean = LocalConfiguration.current.locales[0].language == "ru"

    @Composable
    fun name(en: String, ru: String): String = if (ru()) ru else en

    private fun w(kg: Double) = Weight.ofHundredths(Math.round(kg * 100))

    private fun set(time: Long, exercise: String, kg: Double, reps: Int, synced: Boolean = true, row: Int = 0): DisplaySet {
        val key = SetKey(time, exercise, w(kg), Reps(reps))
        return DisplaySet(key, if (synced) SyncState.SYNCED else SyncState.NOT_SYNCED, if (synced) SetRef.Row(row, key) else SetRef.Pending("p$row"))
    }

    @Composable
    fun bench() = name("Bench press", "Жим лёжа")

    @Composable
    fun row() = name("Bent-over row", "Тяга штанги в наклоне")

    @Composable
    fun squat() = name("Squat", "Приседания")

    @Composable
    fun todayGroups(lastNotSynced: Boolean = false): List<ExerciseGroup> {
        val bench = bench()
        val row = row()
        val squat = squat()
        return listOf(
            ExerciseGroup(bench, listOf(set(at(9, 5), bench, 60.0, 10, row = 1), set(at(9, 9), bench, 70.0, 8, row = 2), set(at(9, 13), bench, 72.5, 6, row = 3))),
            ExerciseGroup(row, listOf(set(at(9, 20), row, 50.0, 12, row = 4), set(at(9, 24), row, 55.0, 10, row = 5))),
            ExerciseGroup(squat, listOf(set(at(9, 35), squat, 80.0, 8, synced = !lastNotSynced, row = 6), set(at(9, 40), squat, 85.0, 6, synced = !lastNotSynced, row = 7))),
        )
    }

    @Composable
    fun notices(): List<Pair<Long, SyncNotice>> {
        val bench = bench()
        return listOf(
            1L to SyncNotice(NoticeKind.CONFLICT_DROPPED, SetKey(at(9, 9), bench, w(70.0), Reps(8))),
            2L to SyncNotice(NoticeKind.WORKOUT_ROW_NOT_UPDATED, SetKey(at(9, 13), bench, w(72.5), Reps(6))),
        )
    }

    @Composable
    fun exercises(): List<Exercise> {
        val chest = name("Chest", "Грудь")
        val back = name("Back", "Спина")
        val legs = name("Legs", "Ноги")
        return listOf(
            Exercise(bench(), chest),
            Exercise(name("Incline dumbbell press", "Жим гантелей на наклонной"), chest),
            Exercise(name("Dips", "Отжимания на брусьях"), chest),
            Exercise(row(), back),
            Exercise(name("Pull-up", "Подтягивания"), back),
            Exercise(name("Lat pulldown", "Тяга верхнего блока"), back),
            Exercise(squat(), legs),
            Exercise(name("Leg press", "Жим ногами"), legs),
            Exercise(name("Romanian deadlift", "Румынская тяга"), legs),
        )
    }

    @Composable
    fun recent(): List<String> = listOf(bench(), squat())

    /** The drills list as the Exercises tab shows it, with one exercise added on the phone. */
    @Composable
    fun shownExercises(): List<ShownExercise> {
        val logged = setOf(bench(), row(), squat())
        return exercises().map { ShownExercise(it.name, it.muscleGroup, pending = false, deletable = it.name !in logged) } +
            ShownExercise(name("Face pull", "Тяга к лицу"), name("Shoulders", "Плечи"), pending = true, deletable = true)
    }

    fun moneyState(
        balance: Double?,
        newPayment: Boolean = false,
        note: BalanceNote = BalanceNote.Calculated(at(3, 0) * 1000),
        payments: Boolean = true,
        error: MoneyError = MoneyError.NONE,
    ): MoneyState {
        val shown = if (!payments) emptyList() else listOfNotNull(
            ShownPayment(TODAY, 8.0, 800_000, pending = true).takeIf { newPayment },
            ShownPayment(TODAY.minusDays(30), 8.0, 800_000, pending = false),
            ShownPayment(TODAY.minusDays(62), 12.0, 1_150_000, pending = false),
            ShownPayment(TODAY.minusDays(95), 8.0, 800_000, pending = false),
        )
        return MoneyState(
            balance = balance,
            note = BalanceNoteState(note, newPaymentsWaiting = newPayment && note !is BalanceNote.Off),
            downloadedAt = at(9, 40) * 1000,
            error = error,
            payments = shown,
            totals = DisplayModel.totals(shown),
        )
    }

    @Composable
    fun benchLastTime(): LastTime =
        LastTime(Samples.YESTERDAY.minusDays(1), listOf(70.0 to 8, 70.0 to 7).map { (kg, r) -> SetKey(at(9, 0), bench(), w(kg), Reps(r)) })

    @Composable
    fun loggingStart(): LoggingState {
        val bench = bench()
        val last = LastTime(LocalDate.of(2026, 10, 13), listOf(SetKey(at(9, 0, TODAY.minusDays(3)), bench, w(70.0), Reps(8)), SetKey(at(9, 4, TODAY.minusDays(3)), bench, w(70.0), Reps(7))))
        val suggested = SetRowState("70", "8", null, null, canConfirm = true, weightSuggested = true, repsSuggested = true)
        return LoggingState(bench, done = emptyList(), rows = listOf(suggested, suggested, suggested), lastTime = last, record = Record(w(75.0), Reps(5)))
    }

    @Composable
    fun loggingInProgress(): LoggingState {
        val start = loggingStart()
        val bench = start.exercise
        return start.copy(
            done = listOf(
                DoneSet(SetKey(at(9, 5), bench, w(70.0), Reps(8)), SyncState.SYNCED),
                DoneSet(SetKey(at(9, 9), bench, w(72.5), Reps(6)), SyncState.NOT_SYNCED),
            ),
            rows = listOf(SetRowState("72.5", "6", null, null, canConfirm = true)),
        )
    }

    @Composable
    fun loggingInvalid(): LoggingState = loggingStart().copy(
        rows = listOf(
            SetRowState("70,555", "8", InvalidReason.TOO_MANY_DECIMALS, null, canConfirm = false),
            SetRowState("70", "0", null, InvalidReason.TOO_SMALL, canConfirm = false),
        ),
    )

    fun pastDays(): List<DaySummary> = listOf(
        DaySummary(TODAY.minusDays(2), exerciseCount = 4, setCount = 14),
        DaySummary(TODAY.minusDays(4), exerciseCount = 3, setCount = 11),
        DaySummary(TODAY.minusDays(7), exerciseCount = 5, setCount = 17),
        DaySummary(TODAY.minusDays(9), exerciseCount = 3, setCount = 9),
    )

    @Composable
    fun dayGroups(): List<ExerciseGroup> {
        val day = TODAY.minusDays(2)
        val pull = name("Pull-up", "Подтягивания")
        val press = name("Leg press", "Жим ногами")
        return listOf(
            ExerciseGroup(pull, listOf(set(at(18, 5, day), pull, 0.0, 10, row = 1), set(at(18, 9, day), pull, 0.0, 8, row = 2), set(at(18, 13, day), pull, 5.0, 6, row = 3))),
            ExerciseGroup(press, listOf(set(at(18, 25, day), press, 120.0, 12, row = 4), set(at(18, 30, day), press, 140.0, 10, synced = false, row = 5))),
        )
    }

    @Composable
    fun pastWorkout(): PastWorkoutState {
        val bench = bench()
        return PastWorkoutState(
            date = YESTERDAY,
            dayHasSets = true,
            sets = listOf(
                DraftSet(SetValues(bench, w(60.0), Reps(10)), at(18, 0, YESTERDAY)),
                DraftSet(SetValues(bench, w(65.0), Reps(8)), at(18, 15, YESTERDAY)),
                DraftSet(SetValues(bench, w(70.0), Reps(6)), at(18, 30, YESTERDAY)),
            ),
        )
    }
}

class GalleryScreens : PreviewParameterProvider<GalleryScreen> {
    override val values = ScreenGallery.screens.asSequence()
}

/**
 * [screen] as the app shows it: the app theme over the window background of `themes.xml`, with no
 * Compose background or content color of its own, so screens without a Scaffold look as on a phone.
 */
@Composable
fun GalleryWindow(screen: GalleryScreen) {
    WorkoutRecTheme {
        Box(Modifier.fillMaxSize().background(colorResource(R.color.window_background))) { screen.content() }
    }
}

/** Every gallery screen in Android Studio; switch the preview to dark or Russian in its toolbar. */
@Preview(name = "Light", showSystemUi = true)
@Preview(name = "Dark", showSystemUi = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun GalleryPreview(@PreviewParameter(GalleryScreens::class) screen: GalleryScreen) {
    GalleryWindow(screen)
}
