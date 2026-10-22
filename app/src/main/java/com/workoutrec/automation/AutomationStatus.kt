package com.workoutrec.automation

import java.time.Duration
import java.time.Instant
import java.time.format.DateTimeParseException

/** data-model.md AutomationStatus */
enum class AutomationStatus { ScriptMissing, NotEnabled, On, Stopped }

/** Derives [AutomationStatus] from the spreadsheet's developer metadata (research R10). */
object AutomationStatusEvaluator {
    const val SCRIPT_ID = "workout_rec.script_id"
    const val ENABLE_URL = "workout_rec.enable_url"
    const val ENABLED_AT = "workout_rec.automation_enabled_at"
    const val LAST_DAILY_RUN = "workout_rec.last_daily_run"

    /** One missed daily run is tolerated. */
    val STALE_AFTER: Duration = Duration.ofHours(48)

    /** [onBadValue] is told about an unreadable timestamp, which is skipped (feature 005 US3). */
    fun evaluate(metadata: Map<String, String>, now: Instant, onBadValue: (String) -> Unit = {}): AutomationStatus {
        if (metadata[SCRIPT_ID].isNullOrBlank()) return AutomationStatus.ScriptMissing
        if (!metadata.containsKey(ENABLED_AT)) return AutomationStatus.NotEnabled
        val newest = listOfNotNull(parse(metadata[LAST_DAILY_RUN], onBadValue), parse(metadata[ENABLED_AT], onBadValue)).maxOrNull()
            ?: return AutomationStatus.Stopped
        return if (Duration.between(newest, now) <= STALE_AFTER) AutomationStatus.On else AutomationStatus.Stopped
    }

    fun parse(value: String?, onBadValue: (String) -> Unit = {}): Instant? = value?.let {
        try {
            Instant.parse(it)
        } catch (e: DateTimeParseException) {
            onBadValue("automation-timestamp")
            null
        }
    }
}
