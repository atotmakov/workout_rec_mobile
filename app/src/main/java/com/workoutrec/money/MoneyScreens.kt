package com.workoutrec.money

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.workoutrec.R
import com.workoutrec.automation.AutomationEntry
import com.workoutrec.automation.AutomationStatus
import com.workoutrec.workout.MoneyTotals
import com.workoutrec.workout.ShownPayment
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** Test tags of feature 004 contracts/screens.md "Money". */
object MoneyTags {
    const val SCREEN = "money_screen"
    const val BALANCE = "money_balance"
    const val NOTE = "money_balance_note"
    const val DOWNLOADED = "money_downloaded"
    const val TOTALS = "money_totals"
    const val ADD = "money_add"
    const val REFRESH = "money_refresh"
    const val EMPTY = "money_empty"
    const val ERROR = "money_error"
    const val DIALOG = "payment_dialog"
    const val DATE = "payment_date"
    const val WORKOUTS = "payment_workouts"
    const val SUM = "payment_sum"
    const val SAVE = "payment_save"
    fun payment(index: Int) = "money_payment_$index"
    fun waiting(index: Int) = "money_payment_waiting_$index"
}

/** What the Money tab shows (feature 004 US4). */
data class MoneyState(
    val balance: Double?,
    val note: BalanceNoteState,
    val downloadedAt: Long?,
    val error: MoneyError,
    val payments: List<ShownPayment>,
    val totals: MoneyTotals,
)

/** The balance with its note, the payments, and Add payment (FR-009, FR-009a). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoneyScreen(
    state: MoneyState,
    zone: ZoneId,
    onAdd: () -> Unit,
    onRefresh: () -> Unit,
    onEnableAutomation: () -> Unit,
    modifier: Modifier = Modifier,
    refreshing: Boolean = false,
    actions: @Composable () -> Unit = {},
    /** Feature 007: shown by the automation entry; null = not known yet. */
    automation: AutomationStatus? = null,
) {
    val locale = LocalConfiguration.current.locales[0]
    Scaffold(
        modifier = modifier.testTag(MoneyTags.SCREEN),
        topBar = { TopAppBar(title = { Text(stringResource(R.string.tab_money)) }, actions = { actions() }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAdd,
                icon = { Icon(painterResource(R.drawable.ic_add), contentDescription = null) },
                text = { Text(stringResource(R.string.money_add)) },
                modifier = Modifier.testTag(MoneyTags.ADD),
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = onRefresh,
            modifier = Modifier.padding(padding).fillMaxSize().testTag(MoneyTags.REFRESH),
        ) {
            LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { BalanceCard(state, zone, locale) }
                // Feature 007 FR-012: optional, shown whatever the balance state.
                item { AutomationEntry(automation, onClick = onEnableAutomation) }
                errorText(state.error)?.let { id ->
                    item { Text(stringResource(id), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag(MoneyTags.ERROR)) }
                }
                state.downloadedAt?.takeIf { it > 0 }?.let { at ->
                    item {
                        Text(
                            stringResource(R.string.money_downloaded, dateTime(at, zone, locale)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.testTag(MoneyTags.DOWNLOADED),
                        )
                    }
                }
                if (state.payments.isEmpty()) {
                    item { Text(stringResource(R.string.money_empty), modifier = Modifier.padding(top = 8.dp).testTag(MoneyTags.EMPTY)) }
                } else {
                    item {
                        Text(
                            stringResource(R.string.money_totals, number(state.totals.workouts, locale), money(state.totals.sumHundredths, locale)),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 8.dp).testTag(MoneyTags.TOTALS),
                        )
                    }
                    item {
                        Text(stringResource(R.string.money_payments_title), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
                    }
                    itemsIndexed(state.payments) { i, payment -> PaymentRow(i, payment, locale) }
                }
                // Room for the add button over the last rows.
                item { Text("", modifier = Modifier.padding(bottom = 72.dp)) }
            }
        }
    }
}

@Composable
private fun BalanceCard(state: MoneyState, zone: ZoneId, locale: Locale) {
    val owed = (state.balance ?: 0.0) < 0
    Card(
        Modifier.fillMaxWidth().padding(top = 8.dp),
        colors = CardDefaults.cardColors(containerColor = if (owed) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Column(Modifier.semantics(mergeDescendants = true) {}.testTag(MoneyTags.BALANCE)) {
                val balance = state.balance
                if (balance == null) {
                    Text(stringResource(R.string.money_balance_unknown), style = MaterialTheme.typography.titleMedium)
                } else {
                    val count = abs(balance)
                    val color = if (owed) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer
                    Text(number(count, locale), style = MaterialTheme.typography.displayMedium, color = color)
                    Text(
                        pluralStringResource(if (owed) R.plurals.money_owed else R.plurals.money_left, count.roundToInt()),
                        style = MaterialTheme.typography.titleMedium,
                        color = color,
                    )
                }
            }
            Column(Modifier.testTag(MoneyTags.NOTE).semantics(mergeDescendants = true) {}) {
                val note = when (val n = state.note.note) {
                    BalanceNote.Calculated -> stringResource(R.string.money_note_calculated)
                    is BalanceNote.Daily -> n.lastRun?.let { stringResource(R.string.money_note_daily, dateTime(it, zone, locale)) }
                        ?: stringResource(R.string.money_note_daily_unknown)
                    is BalanceNote.Stopped -> stringResource(R.string.money_note_stopped, n.lastRun?.let { dateTime(it, zone, locale) } ?: "—")
                }
                Text(note, style = MaterialTheme.typography.bodySmall)
                // Feature 007 FR-005a: the app never calculates the balance; it says when it will change.
                if (state.note.waiting) Text(stringResource(R.string.money_note_waiting), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun PaymentRow(index: Int, payment: ShownPayment, locale: Locale) {
    val format = remember(locale) { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale) }
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp).semantics(mergeDescendants = true) {}.testTag(MoneyTags.payment(index)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(payment.date.format(format), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(
            stringResource(R.string.money_payment, payment.workouts?.let { number(it, locale) } ?: "—", payment.sumHundredths?.let { money(it, locale) } ?: "—"),
            style = MaterialTheme.typography.bodyMedium,
        )
        if (payment.pending) {
            val waiting = stringResource(R.string.exercise_waiting)
            Icon(
                painterResource(R.drawable.ic_sync_waiting),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp).semantics { contentDescription = waiting }.testTag(MoneyTags.waiting(index)),
            )
        }
    }
}

/** A payment: date (no future dates), workouts paid for, sum; defaults from the last payment (FR-009b). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentDialog(
    today: LocalDate,
    defaultWorkouts: Int?,
    defaultSum: Long?,
    onSave: (date: LocalDate, workouts: Int, sumHundredths: Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    var date by rememberSaveable { mutableStateOf(today) }
    var workoutsText by rememberSaveable { mutableStateOf(defaultWorkouts?.toString().orEmpty()) }
    var sumText by rememberSaveable { mutableStateOf(defaultSum?.let { plain(it, locale) }.orEmpty()) }
    var picking by rememberSaveable { mutableStateOf(false) }
    val dateOk = PaymentValues.date(date, today)
    val workouts = PaymentValues.workouts(workoutsText)
    val sum = PaymentValues.sum(sumText)
    val valid = dateOk is PaymentInput.Ok && workouts is PaymentInput.Ok && sum is PaymentInput.Ok
    // A read-only field: a tap opens the date picker.
    val dateTaps = remember { MutableInteractionSource() }
    LaunchedEffect(dateTaps) {
        dateTaps.interactions.collect { if (it is PressInteraction.Release) picking = true }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag(MoneyTags.DIALOG),
        title = { Text(stringResource(R.string.payment_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.payment_date)) },
                    modifier = Modifier.fillMaxWidth().testTag(MoneyTags.DATE),
                    interactionSource = dateTaps,
                )
                OutlinedTextField(
                    value = workoutsText,
                    onValueChange = { workoutsText = it },
                    label = { Text(stringResource(R.string.payment_workouts)) },
                    singleLine = true,
                    isError = workoutsText.isNotEmpty() && workouts is PaymentInput.Invalid,
                    supportingText = if (workoutsText.isNotEmpty() && workouts is PaymentInput.Invalid) {
                        { Text(stringResource(R.string.payment_workouts_error)) }
                    } else {
                        null
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().testTag(MoneyTags.WORKOUTS),
                )
                OutlinedTextField(
                    value = sumText,
                    onValueChange = { sumText = it },
                    label = { Text(stringResource(R.string.payment_sum)) },
                    singleLine = true,
                    isError = sumText.isNotEmpty() && sum is PaymentInput.Invalid,
                    supportingText = if (sumText.isNotEmpty() && sum is PaymentInput.Invalid) {
                        { Text(stringResource(R.string.payment_sum_error)) }
                    } else {
                        null
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth().testTag(MoneyTags.SUM),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (dateOk is PaymentInput.Ok && workouts is PaymentInput.Ok && sum is PaymentInput.Ok) onSave(dateOk.value, workouts.value, sum.value)
                },
                enabled = valid,
                modifier = Modifier.testTag(MoneyTags.SAVE),
            ) { Text(stringResource(R.string.edit_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.edit_cancel)) } },
    )
    if (picking) {
        // The date picker works in UTC millis; future days cannot be picked (data-model PaymentDate).
        val todayMillis = today.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= todayMillis
                override fun isSelectableYear(year: Int) = year <= today.year
            },
        )
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                    picking = false
                }) { Text(stringResource(R.string.edit_save)) }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text(stringResource(R.string.edit_cancel)) } },
        ) { DatePicker(state = pickerState) }
    }
}

private fun errorText(error: MoneyError): Int? = when (error) {
    MoneyError.NONE -> null
    MoneyError.MISSING_TAB -> R.string.money_error_missing_tab
    MoneyError.NETWORK -> R.string.money_error_network
    MoneyError.OTHER -> R.string.money_error_other
}

private fun dateTime(millis: Long, zone: ZoneId, locale: Locale): String =
    Instant.ofEpochMilli(millis).atZone(zone).format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(locale))

/** "8" or "8.5": a count as entered in the sheet. */
private fun number(value: Double, locale: Locale): String = NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 2 }.format(value)

/** "8 000" or "8 000,50": the sheet's sums have no currency. */
private fun money(hundredths: Long, locale: Locale): String =
    NumberFormat.getNumberInstance(locale).apply { minimumFractionDigits = if (hundredths % 100 == 0L) 0 else 2; maximumFractionDigits = 2 }.format(hundredths / 100.0)

/** A sum to type over: "8000" or "8000,5" in Russian. */
private fun plain(hundredths: Long, locale: Locale): String =
    NumberFormat.getNumberInstance(locale).apply { isGroupingUsed = false; maximumFractionDigits = 2 }.format(hundredths / 100.0)
