package com.workoutrec.spreadsheet

sealed interface MismatchReason {
    data class MissingTab(val tab: String) : MismatchReason
    data class WrongHeader(val tab: String, val column: Int, val expected: String, val actual: String?) : MismatchReason
    data object MissingRecFormula : MismatchReason
    data object MissingRecDropDown : MismatchReason
    data class MissingConditionalRule(val index: Int) : MismatchReason
    data class MissingTable(val tab: String, val name: String) : MismatchReason
    data class WrongColumnType(val table: String, val column: Int, val expected: String, val actual: String?) : MismatchReason
    data object MissingLogDropDown : MismatchReason
}

sealed interface StructureCheckResult {
    data object Match : StructureCheckResult
    data class Mismatch(val reasons: List<MismatchReason>) : StructureCheckResult
}

/**
 * contracts/spreadsheet.md "Structure match rule": six tabs, header rows, rec formula, rec
 * drop-down, rec conditional rules. Ignores data rows, extra tabs/columns/rules, tab order,
 * colors, and anything about the script.
 */
object StructureValidator {

    fun check(snapshot: SpreadsheetSnapshot): StructureCheckResult {
        val reasons = mutableListOf<MismatchReason>()
        val present = snapshot.tabTitles.toSet()

        ReferenceStructure.TABS.filter { it !in present }.forEach { reasons += MismatchReason.MissingTab(it) }

        ReferenceStructure.HEADERS.forEach { (tab, expected) ->
            if (tab !in present) return@forEach
            val actual = snapshot.headerRows[tab].orEmpty()
            expected.forEachIndexed { column, header ->
                val value = actual.getOrNull(column)
                if (value != header) reasons += MismatchReason.WrongHeader(tab, column, header, value)
            }
        }

        if ("rec" in present) {
            if (!sameFormula(snapshot.recA2Formula, ReferenceStructure.REC_FORMULA)) reasons += MismatchReason.MissingRecFormula
            if (!sameFormula(snapshot.recDropDownRange, ReferenceStructure.REC_DROPDOWN_RANGE)) reasons += MismatchReason.MissingRecDropDown
            ReferenceStructure.REC_RULES.forEachIndexed { index, rule ->
                val found = snapshot.recRules.any { it.range == rule.range && sameFormula(it.formula, rule.formula) }
                if (!found) reasons += MismatchReason.MissingConditionalRule(index)
            }
        }

        // Rule 6: tables by name, column types by position (colours and size are not compared).
        ReferenceStructure.TABLES.forEach { spec ->
            if (spec.tab !in present) return@forEach
            val table = snapshot.tables[spec.tab].orEmpty().firstOrNull { it.name == spec.name }
            if (table == null) {
                reasons += MismatchReason.MissingTable(spec.tab, spec.name)
                return@forEach
            }
            spec.columnTypes.forEachIndexed { column, expected ->
                val actual = table.columns.firstOrNull { it.index == column }?.type
                if (actual != expected) reasons += MismatchReason.WrongColumnType(spec.name, column, expected, actual)
            }
        }

        // Rule 7: the exercise drop-down in log column B.
        if ("log" in present && !sameFormula(snapshot.logDropDownRange, ReferenceStructure.LOG_DROPDOWN_RANGE)) {
            reasons += MismatchReason.MissingLogDropDown
        }

        return if (reasons.isEmpty()) StructureCheckResult.Match else StructureCheckResult.Mismatch(reasons)
    }

    private fun sameFormula(actual: String?, expected: String): Boolean =
        actual != null && normalize(actual) == normalize(expected)

    private fun normalize(formula: String) = formula.filterNot { it.isWhitespace() }
}
