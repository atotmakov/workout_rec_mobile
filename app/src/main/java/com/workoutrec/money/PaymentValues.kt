package com.workoutrec.money

import java.math.BigInteger
import java.time.LocalDate
import java.time.temporal.ChronoUnit

enum class PaymentProblem { EMPTY, NOT_A_NUMBER, OUT_OF_RANGE, TOO_MANY_DECIMALS, FUTURE_DATE }

sealed interface PaymentInput<out T> {
    data class Ok<T>(val value: T) : PaymentInput<T>
    data class Invalid(val problem: PaymentProblem) : PaymentInput<Nothing>
}

/** Feature 004 research R8; data-model.md PaymentDate, PaidWorkouts, PaymentSum. */
object PaymentValues {
    const val MAX_WORKOUTS = 999
    const val MAX_SUM_HUNDREDTHS = 999_999_999L
    private val SHEET_EPOCH: LocalDate = LocalDate.of(1899, 12, 30)

    fun date(date: LocalDate, today: LocalDate): PaymentInput<LocalDate> =
        if (date.isAfter(today)) PaymentInput.Invalid(PaymentProblem.FUTURE_DATE) else PaymentInput.Ok(date)

    fun workouts(text: String): PaymentInput<Int> {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return PaymentInput.Invalid(PaymentProblem.EMPTY)
        val value = trimmed.toIntOrNull() ?: return PaymentInput.Invalid(PaymentProblem.NOT_A_NUMBER)
        return if (value in 1..MAX_WORKOUTS) PaymentInput.Ok(value) else PaymentInput.Invalid(PaymentProblem.OUT_OF_RANGE)
    }

    /** In hundredths; "1 500,50" and "1500.5" are the same sum. */
    fun sum(text: String): PaymentInput<Long> {
        val cleaned = text.filterNot { it.isWhitespace() || it == ' ' || it == ' ' }.replace(',', '.')
        if (cleaned.isEmpty()) return PaymentInput.Invalid(PaymentProblem.EMPTY)
        val value = cleaned.toBigDecimalOrNull() ?: return PaymentInput.Invalid(PaymentProblem.NOT_A_NUMBER)
        if (value.signum() < 0) return PaymentInput.Invalid(PaymentProblem.OUT_OF_RANGE)
        if (value.stripTrailingZeros().scale() > 2) return PaymentInput.Invalid(PaymentProblem.TOO_MANY_DECIMALS)
        val hundredths = value.movePointRight(2).toBigInteger()
        return if (hundredths > BigInteger.valueOf(MAX_SUM_HUNDREDTHS)) {
            PaymentInput.Invalid(PaymentProblem.OUT_OF_RANGE)
        } else {
            PaymentInput.Ok(hundredths.toLong())
        }
    }

    /** The date as a sheet serial: whole days from 1899-12-30, so the dd.MM.yyyy column shows it. */
    fun serial(date: LocalDate): Double = ChronoUnit.DAYS.between(SHEET_EPOCH, date).toDouble()

    /** A date cell's serial back to its day; a time of day is ignored. */
    fun dateOf(serial: Double): LocalDate = SHEET_EPOCH.plusDays(Math.floor(serial).toLong())
}
