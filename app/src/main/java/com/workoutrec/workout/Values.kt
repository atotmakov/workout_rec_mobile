package com.workoutrec.workout

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/** Why a typed weight or reps value cannot be used (FR-004). */
enum class InvalidReason { EMPTY, NOT_A_NUMBER, NEGATIVE, TOO_MANY_DECIMALS, TOO_LARGE, NOT_WHOLE, TOO_SMALL }

sealed interface Parsed<out T> {
    data class Ok<T>(val value: T) : Parsed<T>
    data class Invalid(val reason: InvalidReason) : Parsed<Nothing>
}

/** Turns "17,5" or " 17.5 " into a number, or says why it is not one. */
private fun parseDecimal(text: String): Parsed<BigDecimal> {
    val normalized = text.trim().replace(',', '.')
    if (normalized.isEmpty()) return Parsed.Invalid(InvalidReason.EMPTY)
    if (!normalized.matches(Regex("""-?\d+(\.\d+)?"""))) return Parsed.Invalid(InvalidReason.NOT_A_NUMBER)
    return Parsed.Ok(BigDecimal(normalized))
}

/** Weight in kg with 2 decimals, kept as hundredths so equal weights compare equal (research R12). */
data class Weight private constructor(val hundredths: Long) {

    /** −/+ button: ±0.5 kg, kept within 0–999.99 (FR-004). */
    fun step(direction: Int): Weight = Weight((hundredths + direction.sign() * STEP).coerceIn(0, MAX))

    /** "17,5" in Russian, "17.5" in English, "20" for whole kilos. */
    fun format(locale: Locale): String =
        DecimalFormat("0.##", DecimalFormatSymbols.getInstance(locale)).format(BigDecimal.valueOf(hundredths, 2))

    fun toDouble(): Double = hundredths / 100.0

    companion object {
        private const val STEP = 50L
        private const val MAX = 99_999L

        val ZERO = Weight(0)

        fun ofHundredths(hundredths: Long): Weight = Weight(hundredths)

        fun parse(text: String): Parsed<Weight> {
            val number = when (val parsed = parseDecimal(text)) {
                is Parsed.Invalid -> return parsed
                is Parsed.Ok -> parsed.value
            }
            return when {
                number.signum() < 0 -> Parsed.Invalid(InvalidReason.NEGATIVE)
                number.stripTrailingZeros().scale() > 2 -> Parsed.Invalid(InvalidReason.TOO_MANY_DECIMALS)
                number.movePointRight(2).toLong() > MAX -> Parsed.Invalid(InvalidReason.TOO_LARGE)
                else -> Parsed.Ok(Weight(number.movePointRight(2).toLong()))
            }
        }

        /** A W cell value, rounded to 2 decimals. */
        fun fromSheet(value: Double): Weight =
            Weight(BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).movePointRight(2).toLong())
    }
}

/** Repetitions, 1–999 (FR-004). */
data class Reps(val value: Int) {

    /** −/+ button: ±1, kept within 1–999 (FR-004). */
    fun step(direction: Int): Reps = Reps((value + direction.sign()).coerceIn(MIN, MAX))

    companion object {
        const val MIN = 1
        const val MAX = 999

        fun parse(text: String): Parsed<Reps> {
            val number = when (val parsed = parseDecimal(text)) {
                is Parsed.Invalid -> return parsed
                is Parsed.Ok -> parsed.value
            }
            return when {
                number.stripTrailingZeros().scale() > 0 -> Parsed.Invalid(InvalidReason.NOT_WHOLE)
                number < BigDecimal(MIN) -> Parsed.Invalid(InvalidReason.TOO_SMALL)
                number > BigDecimal(MAX) -> Parsed.Invalid(InvalidReason.TOO_LARGE)
                else -> Parsed.Ok(Reps(number.toInt()))
            }
        }
    }
}

private fun Int.sign(): Int = Integer.signum(this)

/** A set's identity in the log tab: time to the second, exercise, weight, reps (research R5). */
data class SetKey(val time: Long, val exercise: String, val weight: Weight, val reps: Reps)
