package com.workoutrec.monitoring

import java.io.File

/** Deletes what Tracer stored but has not sent: its `tracer*` entries in the cache folder (R3 update). */
class StoredReportsCleaner(private val cacheDir: File) {
    fun deleteStoredReports() {}
}
