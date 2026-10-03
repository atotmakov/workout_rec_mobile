package com.workoutrec.automation

import java.time.Instant

/** data-model.md AutomationStatus */
enum class AutomationStatus { ScriptMissing, NotEnabled, On, Stopped }

object AutomationStatusEvaluator {
    fun evaluate(metadata: Map<String, String>, now: Instant): AutomationStatus = TODO()
}
