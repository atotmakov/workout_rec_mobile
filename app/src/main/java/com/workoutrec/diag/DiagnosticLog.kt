package com.workoutrec.diag

import java.io.File
import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Timestamped lines about background sync, shared from the account menu (quickstart-results.md
 * issue 1). Holds no tokens or set values. Writes never throw: the log must not break sync.
 */
class DiagnosticLog(
    val file: File,
    private val now: () -> Long = System::currentTimeMillis,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
    private val maxBytes: Long = 256_000,
) {
    private val format = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")

    @Synchronized
    fun write(message: String) {
        try {
            file.parentFile?.mkdirs()
            file.appendText("${format.format(Instant.ofEpochMilli(now()).atZone(zone()))} $message\n")
            if (file.length() > maxBytes) keepNewestHalf()
        } catch (e: IOException) {
            // The log is only a diagnostic aid.
        }
    }

    @Synchronized
    fun read(): String =
        try {
            if (file.exists()) file.readText() else ""
        } catch (e: IOException) {
            ""
        }

    /** Drops the oldest lines, cutting at a line start so every kept line is whole. */
    private fun keepNewestHalf() {
        val text = file.readText()
        val cut = text.indexOf('\n', text.length - (maxBytes / 2).toInt())
        file.writeText(if (cut < 0) "" else text.substring(cut + 1))
    }
}
