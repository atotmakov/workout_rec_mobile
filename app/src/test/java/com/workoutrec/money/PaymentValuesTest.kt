package com.workoutrec.money

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

// Feature 004 research R8; data-model.md PaymentDate, PaidWorkouts, PaymentSum
class PaymentValuesTest {

    private val today = LocalDate.of(2026, 10, 22)

    @Test
    fun dateUpToToday() {
        assertEquals(PaymentInput.Ok(today), PaymentValues.date(today, today))
        assertEquals(PaymentInput.Ok(today.minusYears(1)), PaymentValues.date(today.minusYears(1), today))
        assertEquals(PaymentInput.Invalid(PaymentProblem.FUTURE_DATE), PaymentValues.date(today.plusDays(1), today))
    }

    @Test
    fun workoutsAreAWholeNumberFrom1To999() {
        assertEquals(PaymentInput.Ok(8), PaymentValues.workouts(" 8 "))
        assertEquals(PaymentInput.Ok(999), PaymentValues.workouts("999"))
        assertEquals(PaymentInput.Invalid(PaymentProblem.EMPTY), PaymentValues.workouts(""))
        assertEquals(PaymentInput.Invalid(PaymentProblem.OUT_OF_RANGE), PaymentValues.workouts("0"))
        assertEquals(PaymentInput.Invalid(PaymentProblem.OUT_OF_RANGE), PaymentValues.workouts("1000"))
        assertEquals(PaymentInput.Invalid(PaymentProblem.NOT_A_NUMBER), PaymentValues.workouts("8.5"))
        assertEquals(PaymentInput.Invalid(PaymentProblem.NOT_A_NUMBER), PaymentValues.workouts("eight"))
    }

    @Test
    fun sumIsInHundredthsWithEitherSeparatorAndSpaces() {
        assertEquals(PaymentInput.Ok(150_050L), PaymentValues.sum("1 500,50"))
        assertEquals(PaymentInput.Ok(150_050L), PaymentValues.sum("1500.5"))
        assertEquals(PaymentInput.Ok(150_050L), PaymentValues.sum("1 500,5"))
        assertEquals(PaymentInput.Ok(0L), PaymentValues.sum("0"))
        assertEquals(PaymentInput.Ok(999_999_999L), PaymentValues.sum("9999999.99"))
    }

    @Test
    fun invalidSums() {
        assertEquals(PaymentInput.Invalid(PaymentProblem.EMPTY), PaymentValues.sum(" "))
        assertEquals(PaymentInput.Invalid(PaymentProblem.OUT_OF_RANGE), PaymentValues.sum("-1"))
        assertEquals(PaymentInput.Invalid(PaymentProblem.OUT_OF_RANGE), PaymentValues.sum("10000000"))
        assertEquals(PaymentInput.Invalid(PaymentProblem.TOO_MANY_DECIMALS), PaymentValues.sum("10.555"))
        assertEquals(PaymentInput.Invalid(PaymentProblem.NOT_A_NUMBER), PaymentValues.sum("1,5,0"))
    }

    @Test
    fun dateSerialIsWholeDaysFrom18991230() {
        assertEquals(1.0, PaymentValues.serial(LocalDate.of(1899, 12, 31)), 0.0)
        assertEquals(46317.0, PaymentValues.serial(LocalDate.of(2026, 10, 22)), 0.0)
        assertEquals(LocalDate.of(2026, 10, 22), PaymentValues.dateOf(46317.0))
        assertEquals(LocalDate.of(2026, 10, 22), PaymentValues.dateOf(46317.75))
    }
}
