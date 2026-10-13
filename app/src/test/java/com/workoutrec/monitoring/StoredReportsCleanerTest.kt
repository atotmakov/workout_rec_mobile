package com.workoutrec.monitoring

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

// research R3 update: Tracer keeps unsent reports in cacheDir/tracer*
class StoredReportsCleanerTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `deletes only the tracer entries of the cache folder`() {
        val cache = tmp.newFolder("cache")
        File(cache, "tracer").mkdirs()
        File(cache, "tracer/crashes/c1").apply { parentFile!!.mkdirs(); writeText("x") }
        File(cache, "tracer-heap-dumps").mkdirs()
        File(cache, "tracer-sample.tmp").writeText("x")
        File(cache, "image_cache").mkdirs()
        File(cache, "other.tmp").writeText("x")
        StoredReportsCleaner(cache).deleteStoredReports()
        assertEquals(listOf("image_cache", "other.tmp"), cache.list()!!.sorted())
    }

    @Test
    fun `a missing cache folder is fine`() {
        StoredReportsCleaner(File(tmp.root, "absent")).deleteStoredReports()
    }
}
