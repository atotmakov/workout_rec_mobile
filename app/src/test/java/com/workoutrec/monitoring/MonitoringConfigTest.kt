package com.workoutrec.monitoring

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// research R2, R3, R6–R9
class MonitoringConfigTest {

    @Test
    fun `switch on with a token configures every module`() {
        val plan = MonitoringConfig.plan(tokenPresent = true, sendDiagnostics = true)
        assertEquals(
            listOf(
                TracerModule.Core(debugUpload = true),
                TracerModule.CrashReport(sendAnr = true, nonFatalSoftLimit = true),
                TracerModule.CrashFree,
                TracerModule.HeapDumps,
                TracerModule.DiskUsage(interestingSizeBytes = 50L * 1024 * 1024, probability = 1),
                TracerModule.SamplingProfiler,
                TracerModule.SystraceProfiler,
            ),
            plan.modules,
        )
        assertFalse(plan.deleteStoredReports)
    }

    @Test
    fun `switch off starts nothing and drops stored reports`() {
        val plan = MonitoringConfig.plan(tokenPresent = true, sendDiagnostics = false)
        assertTrue(plan.modules.isEmpty())
        assertTrue(plan.deleteStoredReports)
    }

    @Test
    fun `no token starts nothing and touches nothing`() {
        val nothing = ConfigPlan(emptyList(), deleteStoredReports = false)
        assertEquals(nothing, MonitoringConfig.plan(tokenPresent = false, sendDiagnostics = true))
        assertEquals(nothing, MonitoringConfig.plan(tokenPresent = false, sendDiagnostics = false))
    }
}
