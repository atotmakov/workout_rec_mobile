package com.workoutrec.workout

import java.time.LocalDate

/** A money-tab row as downloaded (feature 004 `payment_row`); null values were not numbers. */
data class PaymentRow(val rowIndex: Int, val date: LocalDate, val workouts: Double?, val sumHundredths: Long?)

/** A payment added in the app and not in the money tab yet (feature 004 `pending_payment`). */
data class PendingPayment(val id: String, val date: LocalDate, val workouts: Int, val sumHundredths: Long, val createdAt: Long)

/** A payment as the Money tab shows it; [pending] while it waits on the phone. */
data class ShownPayment(val date: LocalDate, val workouts: Double?, val sumHundredths: Long?, val pending: Boolean)

/** What all shown payments add up to; values that are not numbers are left out (spec edge case). */
data class MoneyTotals(val workouts: Double, val sumHundredths: Long)
