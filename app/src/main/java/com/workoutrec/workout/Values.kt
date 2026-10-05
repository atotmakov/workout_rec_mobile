package com.workoutrec.workout

import java.util.Locale

/** Why a typed weight or reps value cannot be used (FR-004). */
enum class InvalidReason { EMPTY, NOT_A_NUMBER, NEGATIVE, TOO_MANY_DECIMALS, TOO_LARGE, NOT_WHOLE, TOO_SMALL }

sealed interface Parsed<out T> {
    data class Ok<T>(val value: T) : Parsed<T>
    data class Invalid(val reason: InvalidReason) : Parsed<Nothing>
}

/** Weight in kg with 2 decimals, kept as hundredths so equal weights compare equal (research R12). */
data class Weight private constructor(val hundredths: Long) {

    fun step(direction: Int): Weight = TODO()

    fun format(locale: Locale): String = TODO()

    fun toDouble(): Double = TODO()

    companion object {
        val ZERO = Weight(0)

        fun ofHundredths(hundredths: Long): Weight = Weight(hundredths)

        fun parse(text: String): Parsed<Weight> = TODO()

        fun fromSheet(value: Double): Weight = TODO()
    }
}

/** Repetitions, 1–999 (FR-004). */
data class Reps(val value: Int) {

    fun step(direction: Int): Reps = TODO()

    companion object {
        fun parse(text: String): Parsed<Reps> = TODO()
    }
}

/** A set's identity in the log tab: time to the second, exercise, weight, reps (research R5). */
data class SetKey(val time: Long, val exercise: String, val weight: Weight, val reps: Reps)
