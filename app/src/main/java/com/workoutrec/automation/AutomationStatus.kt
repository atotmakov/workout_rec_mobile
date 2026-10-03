package com.workoutrec.automation

import java.time.Duration
import java.time.Instant

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

    fun evaluate(metadata: Map<String, String>, now: Instant): AutomationStatus {
        if (metadata[SCRIPT_ID].isNullOrBlank()) return AutomationStatus.ScriptMissing
        if (!metadata.containsKey(ENABLED_AT)) return AutomationStatus.NotEnabled
        val newest = listOfNotNull(parse(metadata[LAST_DAILY_RUN]), parse(metadata[ENABLED_AT])).maxOrNull()
            ?: return AutomationStatus.Stopped
        return if (Duration.between(newest, now) <= STALE_AFTER) AutomationStatus.On else AutomationStatus.Stopped
    }

    private fun parse(value: String?): Instant? = value?.let { runCatching { Instant.parse(it) }.getOrNull() }
}
