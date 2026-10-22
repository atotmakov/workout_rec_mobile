package com.workoutrec.diag

import com.workoutrec.fakes.FakeMonitoring
import com.workoutrec.monitoring.NonFatalIssue
import java.io.File
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

// quickstart-results.md issue 1: a log file of background sync the user can share
class DiagnosticLogTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val at = 1_768_240_800_000L // 2026-01-12 18:00:00 UTC

    private fun log(file: File = File(tmp.root, "logs/sync.log"), maxBytes: Long = 256_000) =
        DiagnosticLog(file, now = { at }, zone = { ZoneOffset.UTC }, maxBytes = maxBytes)

    @Test
    fun `nothing written reads as empty`() {
        assertEquals("", log().read())
    }

    @Test
    fun `each line starts with the date and time`() {
        val log = log()
        log.write("background run: started")
        log.write("token: requesting")
        assertEquals("2026-01-12 18:00:00.000 background run: started\n2026-01-12 18:00:00.000 token: requesting\n", log.read())
    }

    @Test
    fun `lines survive a new process`() {
        log().write("before")
        log().write("after")
        assertTrue(log().read().endsWith("before\n2026-01-12 18:00:00.000 after\n"))
    }

    @Test
    fun `the file keeps the newest whole lines within its size limit`() {
        val log = log(maxBytes = 1_000)
        repeat(100) { log.write("line $it") }
        assertTrue(log.file.length() <= 1_000)
        val lines = log.read().lines().filter { it.isNotEmpty() }
        assertEquals("2026-01-12 18:00:00.000 line 99", lines.last())
        assertTrue(lines.all { it.startsWith("2026-01-12 18:00:00.000 line ") })
    }

    @Test
    fun `a failing write never breaks the app`() {
        val notADirectory = tmp.newFile("plain")
        log(File(notADirectory, "sync.log")).write("lost")
    }

    // Feature 003 FR-006, FR-008: lines go to the file as before and, guarded, to monitoring.
    @Test
    fun `lines are also passed to monitoring, except ones with an email or token`() {
        val monitoring = FakeMonitoring()
        val log = DiagnosticLog(File(tmp.root, "logs/sync.log"), now = { at }, zone = { ZoneOffset.UTC }, monitoring = monitoring)
        log.write("sync: start")
        log.write("account: a@example.com")
        assertEquals(listOf("sync: start"), monitoring.logs)
        assertTrue(log.read().contains("account: a@example.com"))
    }

    // Feature 005 US1: a log that cannot be written or read is reported, never through the log
    @Test
    fun `a failed write is reported and the line still goes to monitoring`() {
        val monitoring = FakeMonitoring()
        val log = DiagnosticLog(tmp.root, now = { at }, zone = { ZoneOffset.UTC }, monitoring = monitoring)
        log.write("sync: start")
        assertEquals(listOf(NonFatalIssue.SYNC_LOG_FAILED to "action=write"), monitoring.nonFatals.map { it.issue to it.details })
        assertEquals(listOf("sync: start"), monitoring.logs)
    }

    @Test
    fun `a failed read is reported and reads as empty`() {
        val monitoring = FakeMonitoring()
        val log = DiagnosticLog(tmp.root, now = { at }, zone = { ZoneOffset.UTC }, monitoring = monitoring)
        assertEquals("", log.read())
        assertEquals(listOf(NonFatalIssue.SYNC_LOG_FAILED to "action=read"), monitoring.nonFatals.map { it.issue to it.details })
    }
}
