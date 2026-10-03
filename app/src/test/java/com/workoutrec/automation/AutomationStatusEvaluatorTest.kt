package com.workoutrec.automation

import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

// data-model.md AutomationStatus
class AutomationStatusEvaluatorTest {

    private val now = Instant.parse("2026-10-05T12:00:00Z")
    private fun ago(hours: Long) = now.minus(Duration.ofHours(hours)).toString()

    private fun status(vararg entries: Pair<String, String>) =
        AutomationStatusEvaluator.evaluate(mapOf(*entries), now)

    @Test
    fun `no script id is ScriptMissing`() {
        assertEquals(AutomationStatus.ScriptMissing, status())
        assertEquals(AutomationStatus.ScriptMissing, status("workout_rec.automation_enabled_at" to ago(1)))
    }

    @Test
    fun `script without enabled marker is NotEnabled`() {
        assertEquals(AutomationStatus.NotEnabled, status("workout_rec.script_id" to "s1"))
    }

    @Test
    fun `recently enabled is On`() {
        assertEquals(AutomationStatus.On, status("workout_rec.script_id" to "s1", "workout_rec.automation_enabled_at" to ago(2)))
    }

    @Test
    fun `recent daily run is On even if enabled long ago`() {
        assertEquals(
            AutomationStatus.On,
            status(
                "workout_rec.script_id" to "s1",
                "workout_rec.automation_enabled_at" to ago(24 * 30),
                "workout_rec.last_daily_run" to ago(10),
            ),
        )
    }

    @Test
    fun `48 h boundary uses the newer timestamp`() {
        val base = arrayOf("workout_rec.script_id" to "s1", "workout_rec.automation_enabled_at" to ago(24 * 10))
        assertEquals(AutomationStatus.On, status(*base, "workout_rec.last_daily_run" to ago(48)))
        assertEquals(AutomationStatus.Stopped, status(*base, "workout_rec.last_daily_run" to ago(49)))
    }

    @Test
    fun `old enabled time without runs is Stopped`() {
        assertEquals(AutomationStatus.Stopped, status("workout_rec.script_id" to "s1", "workout_rec.automation_enabled_at" to ago(72)))
    }

    @Test
    fun `unreadable timestamps count as missing`() {
        assertEquals(
            AutomationStatus.Stopped,
            status("workout_rec.script_id" to "s1", "workout_rec.automation_enabled_at" to "garbage"),
        )
    }
}
