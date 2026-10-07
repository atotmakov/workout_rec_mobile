package com.workoutrec.sync

/** When opening or resuming the app should sync (research R7). */
object RefreshPolicy {
    const val INTERVAL_MILLIS = 5 * 60_000L

    /** Waiting sets always sync; otherwise refresh when never synced or the last sync is 5+ minutes old. */
    fun shouldSync(lastSuccessAt: Long?, now: Long, pendingCount: Int): Boolean =
        pendingCount > 0 || lastSuccessAt == null || now - lastSuccessAt >= INTERVAL_MILLIS
}
