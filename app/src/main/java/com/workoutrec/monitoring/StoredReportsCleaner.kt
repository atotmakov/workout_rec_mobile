package com.workoutrec.monitoring

import java.io.File

/** Deletes what Tracer stored but has not sent: its `tracer*` entries in the cache folder (R3 update). */
class StoredReportsCleaner(private val cacheDir: File) {
    fun deleteStoredReports() {
        cacheDir.listFiles { file -> file.name.startsWith(PREFIX) }?.forEach { it.deleteRecursively() }
    }

    companion object {
        /** Tracer's `TracerFiles.getTracerDir` names (SDK 1.4.0). */
        const val PREFIX = "tracer"
    }
}
