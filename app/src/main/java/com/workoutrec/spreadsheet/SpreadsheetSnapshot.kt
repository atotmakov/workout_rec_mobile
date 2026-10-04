package com.workoutrec.spreadsheet

import com.workoutrec.google.SheetTable
import com.workoutrec.google.TabsAndMetadata
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

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
    /** Tab title → tables on that tab. */
    val tables: Map<String, List<SheetTable>> = emptyMap(),
    /** Drop-down range of `log!B2`. */
    val logDropDownRange: String? = null,
) {
    companion object {
        /** Builds a snapshot from call 5a ([tabs]) and call 5b ([cells]). */
        fun parse(tabs: TabsAndMetadata, cells: JsonObject): SpreadsheetSnapshot {
            val sheets = (cells["sheets"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
            val byTitle = sheets.associateBy { it.obj("properties")?.string("title") }
            val headerRows = ReferenceStructure.HEADERS.keys
                .mapNotNull { tab -> byTitle[tab]?.let { tab to rows(it).firstOrNull().orEmpty().map { cell -> cellText(cell) ?: "" } } }
                .toMap()
            val rec = byTitle["rec"]
            val recRows = rec?.let(::rows).orEmpty()
            return SpreadsheetSnapshot(
                tabTitles = tabs.titles,
                headerRows = headerRows,
                recDropDownRange = recRows.getOrNull(0)?.getOrNull(0)?.let(::dropDownRange),
                recA2Formula = recRows.getOrNull(1)?.getOrNull(0)?.obj("userEnteredValue")?.string("formulaValue"),
                recRules = rec?.let(::conditionalRules).orEmpty(),
                metadata = tabs.metadata,
                metadataIds = tabs.metadataIds,
                tables = tabs.tables,
                logDropDownRange = byTitle["log"]?.let(::rows)?.getOrNull(1)?.getOrNull(1)?.let(::dropDownRange),
            )
        }

        private fun rows(sheet: JsonObject): List<List<JsonObject>> =
            (sheet["data"] as? JsonArray).orEmpty().firstOrNull()?.let { it as? JsonObject }
                ?.let { (it["rowData"] as? JsonArray).orEmpty() }
                .orEmpty()
                .map { row -> ((row as? JsonObject)?.get("values") as? JsonArray).orEmpty().map { it as? JsonObject ?: JsonObject(emptyMap()) } }

        private fun cellText(cell: JsonObject): String? {
            val value = cell.obj("userEnteredValue") ?: return null
            return value.string("stringValue") ?: value.string("formulaValue") ?: value["numberValue"]?.jsonPrimitive?.contentOrNull
        }

        private fun dropDownRange(cell: JsonObject): String? {
            val condition = cell.obj("dataValidation")?.obj("condition") ?: return null
            if (condition.string("type") != "ONE_OF_RANGE") return null
            return (condition["values"] as? JsonArray)?.firstOrNull()?.let { it as? JsonObject }?.string("userEnteredValue")
        }

        private fun conditionalRules(sheet: JsonObject): List<ConditionalRule> =
            (sheet["conditionalFormats"] as? JsonArray).orEmpty().mapNotNull { element ->
                val rule = element as? JsonObject ?: return@mapNotNull null
                val condition = rule.obj("booleanRule")?.obj("condition") ?: return@mapNotNull null
                if (condition.string("type") != "CUSTOM_FORMULA") return@mapNotNull null
                val formula = (condition["values"] as? JsonArray)?.firstOrNull()?.let { it as? JsonObject }?.string("userEnteredValue")
                    ?: return@mapNotNull null
                val range = (rule["ranges"] as? JsonArray)?.firstOrNull()?.let { it as? JsonObject } ?: return@mapNotNull null
                ConditionalRule(
                    A1Range.format(
                        A1Range.Grid(
                            startRow = range.int("startRowIndex") ?: 0,
                            endRow = range.int("endRowIndex") ?: 0,
                            startCol = range.int("startColumnIndex") ?: 0,
                            endCol = range.int("endColumnIndex") ?: 0,
                        ),
                    ),
                    formula,
                )
            }

        private fun JsonObject.obj(key: String) = this[key] as? JsonObject
        private fun JsonObject.string(key: String) = this[key]?.jsonPrimitive?.contentOrNull
        private fun JsonObject.int(key: String) = this[key]?.jsonPrimitive?.intOrNull
        private fun JsonArray?.orEmpty(): List<kotlinx.serialization.json.JsonElement> = this ?: emptyList()
    }
}
