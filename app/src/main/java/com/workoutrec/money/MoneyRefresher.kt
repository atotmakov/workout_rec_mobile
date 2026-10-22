package com.workoutrec.money

import com.workoutrec.automation.AutomationStatus
import com.workoutrec.automation.AutomationStatusEvaluator
import com.workoutrec.google.ApiError
import com.workoutrec.google.MissingTabException
import com.workoutrec.google.MoneyApi
import com.workoutrec.monitoring.Monitoring
import com.workoutrec.monitoring.NoMonitoring
import com.workoutrec.monitoring.NonFatalIssue
import com.workoutrec.monitoring.badValue
import com.workoutrec.monitoring.failure
import com.workoutrec.sync.SheetInfoCache
import com.workoutrec.workout.PaymentRow
import java.time.Instant
import kotlin.math.roundToLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class MoneyError { NONE, MISSING_TAB, NETWORK, OTHER }

/** Feature 004 data-model.md `money_summary`. */
data class MoneySummary(
    val balance: Double?,
    val lastDailyRun: Long?,
    val automation: AutomationStatus,
    /** When the last download succeeded. */
    val downloadedAt: Long,
    val error: MoneyError,
)

/** Where downloaded money data lives (implemented by `WorkoutRepository`). */
interface MoneyStore {
    suspend fun moneySummaryNow(): MoneySummary?
    /** Rows and summary in one transaction. */
    suspend fun replaceMoney(rows: List<PaymentRow>, summary: MoneySummary)
    /** Keeps the stored values; only the error changes. */
    suspend fun moneyFailed(error: MoneyError)
}

/**
 * Feature 004 FR-010, research R6: downloads the money tab, the balance and the metadata (call 23)
 * on opening Money when older than 5 minutes, on pull-down, once per app start, and after a sync
 * wrote payments. Separate from the set sync: its failures only show on the Money tab.
 */
class MoneyRefresher(
    private val api: MoneyApi,
    private val store: MoneyStore,
    private val sheet: suspend () -> SheetInfoCache?,
    private val now: () -> Long = System::currentTimeMillis,
    /** Feature 005: failures as non-fatal events (contracts/issues.md). */
    private val monitoring: Monitoring = NoMonitoring,
) {
    private val lock = Mutex()
    private var started = false

    suspend fun onAppStart() {
        if (started) return
        started = true
        onOpen()
    }

    suspend fun onOpen() {
        val summary = store.moneySummaryNow()
        if (summary != null && summary.error == MoneyError.NONE && now() - summary.downloadedAt < FRESH_MILLIS) return
        refresh()
    }

    suspend fun refresh() = lock.withLock {
        val info = sheet()
        if (info == null) {
            store.moneyFailed(MoneyError.OTHER)
            return@withLock
        }
        try {
            val read = api.readMoney(info.spreadsheetId, hasMoney = info.moneySheetId != null, hasBalance = info.balanceSheetId != null)
            val at = now()
            val rows = read.payments.mapNotNull { row ->
                row.serial?.let { PaymentRow(row.rowIndex, PaymentValues.dateOf(it), row.workouts, row.sum?.let { sum -> (sum * 100).roundToLong() }) }
            }
            // Feature 005 US3: each unreadable value is reported once per download.
            val bad = mutableSetOf<String>()
            val lastRun = AutomationStatusEvaluator.parse(read.metadata[AutomationStatusEvaluator.LAST_DAILY_RUN], bad::add)?.toEpochMilli()
            val automation = AutomationStatusEvaluator.evaluate(read.metadata, Instant.ofEpochMilli(at), bad::add)
            bad.forEach { monitoring.badValue(NonFatalIssue.BAD_SHEET_DATA, it) }
            store.replaceMoney(rows, MoneySummary(read.balance, lastRun, automation, at, MoneyError.NONE))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val reason = if (e is MissingTabException) "reason=missing-tab" else "reason=other"
            monitoring.failure(NonFatalIssue.MONEY_REFRESH_FAILED, e, "money-refresh", reason)
            store.moneyFailed(
                when (e) {
                    is MissingTabException -> MoneyError.MISSING_TAB
                    is ApiError.Offline, is ApiError.ServiceUnavailable -> MoneyError.NETWORK
                    else -> MoneyError.OTHER
                },
            )
        }
    }

    companion object {
        const val FRESH_MILLIS = 5 * 60_000L
    }
}
