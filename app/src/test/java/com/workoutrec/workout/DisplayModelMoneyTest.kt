package com.workoutrec.workout

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

// Feature 004 data-model.md "Display model additions": payments and totals
class DisplayModelMoneyTest {

    private fun d(day: Int) = LocalDate.of(2026, 10, day)

    @Test
    fun newestFirstWaitingFirstOnTheSameDayThenLowerRowsFirst() {
        val rows = listOf(PaymentRow(1, d(1), 8.0, 800_000), PaymentRow(2, d(10), 4.0, 400_000), PaymentRow(3, d(10), 1.0, 100_000))
        val pending = listOf(PendingPayment("p", d(10), 12, 1_200_000, createdAt = 1))
        assertEquals(
            listOf(
                ShownPayment(d(10), 12.0, 1_200_000, pending = true),
                ShownPayment(d(10), 1.0, 100_000, pending = false),
                ShownPayment(d(10), 4.0, 400_000, pending = false),
                ShownPayment(d(1), 8.0, 800_000, pending = false),
            ),
            DisplayModel.payments(rows, pending),
        )
    }

    @Test
    fun totalsCountOnlyNumbers() {
        val shown = listOf(ShownPayment(d(1), 8.0, 800_000, false), ShownPayment(d(2), null, null, false), ShownPayment(d(3), 4.0, 450_050, true))
        assertEquals(MoneyTotals(workouts = 12.0, sumHundredths = 1_250_050), DisplayModel.totals(shown))
    }
}
