package com.workoutrec.money

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.workoutrec.AppContainer
import com.workoutrec.automation.AutomationStatus
import com.workoutrec.log.zone
import com.workoutrec.sync.SyncStatus
import com.workoutrec.workout.DisplayModel
import java.time.LocalDate
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The Money tab's first screen (feature 004 US4). */
@Composable
fun MoneyRoute(
    container: AppContainer,
    modifier: Modifier = Modifier,
    actions: @Composable () -> Unit = {},
) {
    val payments by container.workoutRepository.payments.collectAsState(initial = emptyList())
    val summary by container.workoutRepository.moneySummary.collectAsState(initial = null)
    val waiting by container.workoutRepository.waitingCount.collectAsState(initial = 0)
    val status by container.syncStatusStore.status.collectAsState(initial = SyncStatus())
    val zone = status.zone()
    val scope = rememberCoroutineScope()
    var refreshing by remember { mutableStateOf(false) }
    var adding by rememberSaveable { mutableStateOf(false) }
    // FR-010: opening Money downloads when the data is older than 5 minutes.
    LaunchedEffect(Unit) { container.moneyRefresher.onOpen() }

    val note = BalanceNotes.of(
        calculated = summary?.calculated ?: false,
        automation = summary?.automation ?: AutomationStatus.ScriptMissing,
        lastDailyRun = summary?.lastDailyRun,
        waiting = waiting > 0,
    )
    MoneyScreen(
        state = MoneyState(
            balance = summary?.balance,
            note = note,
            downloadedAt = summary?.downloadedAt,
            error = summary?.error ?: MoneyError.NONE,
            payments = payments,
            totals = DisplayModel.totals(payments),
        ),
        zone = zone,
        onAdd = { adding = true },
        onRefresh = {
            scope.launch {
                refreshing = true
                try {
                    // A pull is a sync first: the form step after it repairs typed-over durations and the
                    // balance (feature 007 FR-011), so the download then shows the repaired balance.
                    withContext(Dispatchers.Default) {
                        container.diagnosticLog.write("money pulled to refresh")
                        container.syncScheduler.runNow()
                    }
                    container.moneyRefresher.refresh()
                } finally {
                    refreshing = false
                }
            }
        },
        modifier = modifier,
        refreshing = refreshing,
        actions = actions,
    )
    if (adding) {
        // The last payment is the usual one: start from its values (contracts/screens.md "Payment dialog").
        val last = payments.firstOrNull()
        PaymentDialog(
            today = LocalDate.now(zone),
            defaultWorkouts = last?.workouts?.roundToInt()?.takeIf { it >= 1 } ?: 1,
            defaultSum = last?.sumHundredths,
            onSave = { date, workouts, sum ->
                adding = false
                scope.launch {
                    container.workoutRepository.addPayment(date, workouts, sum)
                    container.syncScheduler.requestSync()
                }
            },
            onDismiss = { adding = false },
        )
    }
}
