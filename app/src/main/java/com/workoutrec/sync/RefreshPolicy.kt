package com.workoutrec.sync

/** When opening or resuming the app should sync (research R7). */
object RefreshPolicy {
    const val INTERVAL_MILLIS = 5 * 60_000L

    fun shouldSync(lastSuccessAt: Long?, now: Long, pendingCount: Int): Boolean = TODO()
}
