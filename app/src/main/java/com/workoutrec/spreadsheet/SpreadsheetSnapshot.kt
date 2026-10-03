package com.workoutrec.spreadsheet

import com.workoutrec.google.TabsAndMetadata
import kotlinx.serialization.json.JsonObject

/** A conditional formatting rule: A1 range and custom formula. */
data class ConditionalRule(val range: String, val formula: String)

/** What the structure check reads from a spreadsheet (data-model.md). */
data class SpreadsheetSnapshot(
    val tabTitles: List<String>,
    val headerRows: Map<String, List<String>>,
    val recDropDownRange: String?,
    val recA2Formula: String?,
    val recRules: List<ConditionalRule>,
    val metadata: Map<String, String>,
    val metadataIds: Map<String, Int>,
) {
    companion object {
        fun parse(tabs: TabsAndMetadata, cells: JsonObject): SpreadsheetSnapshot = TODO()
    }
}
