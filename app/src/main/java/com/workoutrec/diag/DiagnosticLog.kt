package com.workoutrec.diag

import java.io.File
import java.time.ZoneId

/** Timestamped lines about background sync, shared from the account menu (quickstart-results.md issue 1). */
class DiagnosticLog(
    val file: File,
    private val now: () -> Long = System::currentTimeMillis,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
    private val maxBytes: Long = 256_000,
) {
    fun write(message: String) {
        TODO("issue 1 diagnostics")
    }

    fun read(): String = TODO("issue 1 diagnostics")
}
