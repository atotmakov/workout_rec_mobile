package com.workoutrec.workout

/** Time of a set confirmed during a live workout (FR-006). */
object LiveSetTime {

    /**
     * The confirm time in whole seconds, but always after [previous] (the latest set time), so the
     * app's sets have unique keys (research R5).
     */
    fun next(nowMillis: Long, previous: Long?): Long = TODO()
}
