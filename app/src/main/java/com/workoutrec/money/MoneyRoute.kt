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
import kotlinx.coroutines.launch

/** The Money tab's first screen (feature 004 US4); [onEnableAutomation] is feature 001's fix. */
@Composable
fun MoneyRoute(container: AppContainer, onEnableAutomation: () -> Unit, modifier: Modifier = Modifier, actions: @Composable () -> Unit = {}) {
    val payments by container.workoutRepository.payments.collectAsState(initial = emptyList())
    val summary by container.workoutRepository.moneySummary.collectAsState(initial = null)
    val hasPending by container.workoutRepository.hasPendingPayment.collectAsState(initial = false)
    val status by container.syncStatusStore.status.collectAsState(initial = SyncStatus())
    val zone = status.zone()
    val scope = rememberCoroutineScope()
    var refreshing by remember { mutableStateOf(false) }
    var adding by rememberSaveable { mutableStateOf(false) }
    // FR-010: opening Money downloads when the data is older than 5 minutes.
    LaunchedEffect(Unit) { container.moneyRefresher.onOpen() }

    val note = BalanceNotes.of(
        automation = summary?.automation ?: AutomationStatus.ScriptMissing,
        lastDailyRun = summary?.lastDailyRun,
        pendingPayment = hasPending,
        latestPayment = payments.filterNot { it.pending }.maxOfOrNull { it.date },
        zone = zone,
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
                    container.moneyRefresher.refresh()
                } finally {
                    refreshing = false
                }
            }
        },
        onEnableAutomation = onEnableAutomation,
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
