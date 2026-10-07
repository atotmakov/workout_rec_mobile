package com.workoutrec.workout

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

// data-model.md "Value types", FR-004
class ValuesTest {

    private fun weight(text: String) = (Weight.parse(text) as Parsed.Ok).value
    private fun weightError(text: String) = (Weight.parse(text) as Parsed.Invalid).reason
    private fun reps(text: String) = (Reps.parse(text) as Parsed.Ok).value
    private fun repsError(text: String) = (Reps.parse(text) as Parsed.Invalid).reason

    @Test
    fun `weight accepts comma or dot and up to 2 decimals`() {
        assertEquals(Weight.ofHundredths(1750), weight("17,5"))
        assertEquals(Weight.ofHundredths(1750), weight("17.5"))
        assertEquals(Weight.ofHundredths(1725), weight(" 17,25 "))
        assertEquals(Weight.ZERO, weight("0"))
        assertEquals(Weight.ofHundredths(99999), weight("999,99"))
    }

    @Test
    fun `equal weights are equal however they were typed`() {
        assertEquals(weight("17.50"), weight("17,5"))
        assertEquals(weight("20"), weight("20,00"))
        assertNotEquals(weight("17.5"), weight("17.55"))
    }

    @Test
    fun `invalid weights give a reason`() {
        assertEquals(InvalidReason.EMPTY, weightError(""))
        assertEquals(InvalidReason.EMPTY, weightError("  "))
        assertEquals(InvalidReason.NOT_A_NUMBER, weightError("abc"))
        assertEquals(InvalidReason.NOT_A_NUMBER, weightError("1,2,3"))
        assertEquals(InvalidReason.NEGATIVE, weightError("-1"))
        assertEquals(InvalidReason.TOO_MANY_DECIMALS, weightError("17,555"))
        assertEquals(InvalidReason.TOO_LARGE, weightError("1000"))
    }

    @Test
    fun `reps accept whole numbers from 1 to 999`() {
        assertEquals(Reps(1), reps("1"))
        assertEquals(Reps(15), reps(" 15 "))
        assertEquals(Reps(999), reps("999"))
    }

    @Test
    fun `invalid reps give a reason`() {
        assertEquals(InvalidReason.EMPTY, repsError(""))
        assertEquals(InvalidReason.NOT_A_NUMBER, repsError("x"))
        assertEquals(InvalidReason.NOT_WHOLE, repsError("1,5"))
        assertEquals(InvalidReason.NOT_WHOLE, repsError("1.5"))
        assertEquals(InvalidReason.TOO_SMALL, repsError("0"))
        assertEquals(InvalidReason.TOO_SMALL, repsError("-3"))
        assertEquals(InvalidReason.TOO_LARGE, repsError("1000"))
    }

    @Test
    fun `weight stepper changes by half a kilo, not below 0`() {
        assertEquals(weight("18"), weight("17,5").step(+1))
        assertEquals(weight("17"), weight("17,5").step(-1))
        assertEquals(weight("17,75"), weight("17,25").step(+1))
        assertEquals(Weight.ZERO, weight("0,3").step(-1))
        assertEquals(Weight.ZERO, Weight.ZERO.step(-1))
        assertEquals(weight("999,99"), weight("999,99").step(+1))
    }

    @Test
    fun `reps stepper changes by one, not below 1`() {
        assertEquals(Reps(16), Reps(15).step(+1))
        assertEquals(Reps(14), Reps(15).step(-1))
        assertEquals(Reps(1), Reps(1).step(-1))
        assertEquals(Reps(999), Reps(999).step(+1))
    }

    @Test
    fun `weight is shown with the locale decimal separator`() {
        val ru = Locale.forLanguageTag("ru")
        assertEquals("17,5", weight("17.5").format(ru))
        assertEquals("17.5", weight("17,5").format(Locale.ENGLISH))
        assertEquals("20", weight("20,00").format(ru))
        assertEquals("17,25", weight("17.25").format(ru))
        assertEquals("0", Weight.ZERO.format(Locale.ENGLISH))
    }

    @Test
    fun `weight from a sheet number is rounded to 2 decimals`() {
        assertEquals(weight("17,5"), Weight.fromSheet(17.5))
        assertEquals(weight("0,1"), Weight.fromSheet(0.1 + 0.2 - 0.2))
        assertEquals(17.5, weight("17,5").toDouble(), 0.0)
    }
}
