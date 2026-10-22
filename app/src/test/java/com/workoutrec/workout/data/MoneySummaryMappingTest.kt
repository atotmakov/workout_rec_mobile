package com.workoutrec.workout.data

import com.workoutrec.automation.AutomationStatus
import com.workoutrec.money.MoneyError
import org.junit.Assert.assertEquals
import org.junit.Test

// Feature 005 US3: an unreadable saved money summary falls back as before and is named
class MoneySummaryMappingTest {

    private val bad = mutableListOf<String>()

    @Test
    fun `unknown saved values fall back and are reported`() {
        val summary = MoneySummaryEntity(balance = 1.0, lastDailyRun = null, automation = "Bogus", downloadedAt = 5, error = "BOGUS").toDomain { bad += it }
        assertEquals(AutomationStatus.ScriptMissing, summary.automation)
        assertEquals(MoneyError.OTHER, summary.error)
        assertEquals(listOf("automation-status", "money-error"), bad)
    }

    @Test
    fun `valid saved values report nothing`() {
        val summary = MoneySummaryEntity(balance = 1.0, lastDailyRun = null, automation = "On", downloadedAt = 5, error = "NETWORK").toDomain { bad += it }
        assertEquals(AutomationStatus.On, summary.automation)
        assertEquals(MoneyError.NETWORK, summary.error)
        assertEquals(emptyList<String>(), bad)
    }
}
