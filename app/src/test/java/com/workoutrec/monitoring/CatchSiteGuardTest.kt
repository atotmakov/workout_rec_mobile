package com.workoutrec.monitoring

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// Feature 005 FR-012, research R7: every catch site reports, passes the error on, or is listed
class CatchSiteGuardTest {

    private val fixtures = File("src/test/resources/guard-fixture")

    @Test
    fun `the scanner finds the fixture sites with their lines`() {
        val sites = CatchSites.scan(fixtures, extension = ".kt.txt")
        assertEquals(
            listOf(
                CatchSite("Reporting.kt.txt", 8, "IllegalStateException", CatchSite.Kind.REPORTS),
                CatchSite("Reporting.kt.txt", 14, "ApiError", CatchSite.Kind.REPORTS),
                CatchSite("Reporting.kt.txt", 20, "IllegalArgumentException", CatchSite.Kind.REPORTS),
                CatchSite("Reporting.kt.txt", 26, "DateTimeException", CatchSite.Kind.REPORTS),
                CatchSite("Reporting.kt.txt", 32, "CancellationException", CatchSite.Kind.RETHROWS),
                CatchSite("Reporting.kt.txt", 39, "IOException", CatchSite.Kind.RETHROWS),
                CatchSite("Reporting.kt.txt", 41, "Flow.catch", CatchSite.Kind.REPORTS),
                CatchSite("sub/Unreported.kt.txt", 8, "IOException", CatchSite.Kind.UNREPORTED),
                CatchSite("sub/Unreported.kt.txt", 13, "IllegalStateException", CatchSite.Kind.UNREPORTED),
                CatchSite("sub/Unreported.kt.txt", 22, "DateTimeParseException", CatchSite.Kind.UNREPORTED),
                CatchSite("sub/Unreported.kt.txt", 26, "runCatching", CatchSite.Kind.UNREPORTED),
                CatchSite("sub/Unreported.kt.txt", 28, "Flow.catch", CatchSite.Kind.UNREPORTED),
                CatchSite("sub/Unreported.kt.txt", 30, "CoroutineExceptionHandler", CatchSite.Kind.UNREPORTED),
            ),
            sites,
        )
    }

    @Test
    fun `unlisted sites and stale entries are problems`() {
        val problems = CatchSites.check(fixtures, File(fixtures, "normal-outcomes.txt"), extension = ".kt.txt")
        assertEquals(
            listOf(
                "sub/Unreported.kt.txt:8 IOException: not reported and not in the normal-outcome list",
                "sub/Unreported.kt.txt:13 IllegalStateException: not reported and not in the normal-outcome list",
                "sub/Unreported.kt.txt:26 runCatching: not reported and not in the normal-outcome list",
                "sub/Unreported.kt.txt:28 Flow.catch: not reported and not in the normal-outcome list",
                "sub/Unreported.kt.txt:30 CoroutineExceptionHandler: not reported and not in the normal-outcome list",
                "normal-outcomes: sub/Gone.kt.txt | IOException lists 1 site, found 0",
            ),
            problems,
        )
    }

    @Test
    fun `a wrong count and a missing reason are problems`() {
        val list = File.createTempFile("outcomes", ".txt").apply {
            writeText("sub/Unreported.kt.txt | DateTimeParseException | 2 | typed text\nsub/Unreported.kt.txt | IOException | 1 |\n")
            deleteOnExit()
        }
        val problems = CatchSites.check(fixtures, list, extension = ".kt.txt")
        assertTrue(problems.toString(), "normal-outcomes: sub/Unreported.kt.txt | DateTimeParseException lists 2 sites, found 1" in problems)
        assertTrue(problems.toString(), "normal-outcomes line 2: a reason is required" in problems)
    }

    @Test
    fun `every catch site in the app reports, passes the error on, or is a listed normal outcome`() {
        val app = File("src/main/java/com/workoutrec")
        assertTrue(app.isDirectory)
        val problems = CatchSites.check(app, File("src/test/resources/normal-outcomes.txt"))
        assertEquals("Report the error (Monitoring.failure/nonFatal) or list the site with a reason:\n" + problems.joinToString("\n"), emptyList<String>(), problems)
    }
}
