package com.workoutrec.spreadsheet

import com.workoutrec.automation.AutomationStatus
import com.workoutrec.google.CalculatedCells
import com.workoutrec.google.CalculatedRepair
import com.workoutrec.google.CellContent

/** What the form step writes ([repair], null = nothing) and which overwritten cells it reports, by kind. */
data class FormDecision(val repair: CalculatedRepair?, val restored: Map<String, Int>)

/**
 * Feature 007 research R5, R6 (data-model.md "Calculated cells: states"): durations and the balance
 * must be the spreadsheet's formulas. The first repair with the gate open converts the spreadsheet
 * and records structure version 3.
 */
object CalculatedForm {

    /** The attached script version that writes formulas and no balance into a converted spreadsheet. */
    const val SCRIPT_VERSION_WITH_FORMULAS = 3

    fun decide(cells: CalculatedCells, workoutSheetId: Int, balanceSheetId: Int, automation: AutomationStatus, scriptVersion: Int): FormDecision {
        val versionKey = ReferenceStructure.STRUCTURE_VERSION_KEY
        val converted = (cells.metadata[versionKey]?.toIntOrNull() ?: 0) >= ReferenceStructure.STRUCTURE_VERSION.toInt()
        // R6: an old script that still runs at night writes a value over the balance formula.
        val gateOpen = !(automation == AutomationStatus.On && scriptVersion < SCRIPT_VERSION_WITH_FORMULAS)

        // A formula with the right text can still be unparsed: one written in another locale's syntax.
        val durations = cells.rows.filter { it.hasDate && (it.duration.parseError || !it.duration.holds(ReferenceStructure.DURATION_FORMULA)) }
        val writeBalance = gateOpen && !cells.balance.holds(ReferenceStructure.BALANCE_FORMULA)
        val restored = buildMap {
            // An empty cell is a row added by hand or by an old script, not an overwritten calculation.
            if (converted && writeBalance && !cells.balance.empty) put("balance", 1)
            val typedOver = durations.count { !it.duration.empty && !it.duration.holds(ReferenceStructure.DURATION_FORMULA) }
            if (converted && typedOver > 0) put("duration", typedOver)
        }
        val marker = if (gateOpen && !converted) mapOf(versionKey to ReferenceStructure.STRUCTURE_VERSION) else emptyMap()
        val repair = CalculatedRepair(
            workoutSheetId = workoutSheetId,
            durationRuns = runs(durations.map { it.rowIndex }),
            balanceSheetId = balanceSheetId.takeIf { writeBalance },
            metadata = marker,
            metadataIds = cells.metadataIds.filterKeys { it in marker },
            durationFormula = ReferenceStructure.durationFormula(cells.locale),
        )
        return FormDecision(repair.takeUnless { it.isEmpty }, restored)
    }

    private fun CellContent.holds(formula: String) = sameFormulaText(this.formula, formula)

    /** Sorted row indexes as contiguous ranges, for one `repeatCell` each. */
    private fun runs(rows: List<Int>): List<IntRange> {
        val result = mutableListOf<IntRange>()
        rows.sorted().forEach { row ->
            val last = result.lastOrNull()
            if (last != null && last.last + 1 == row) result[result.size - 1] = last.first..row else result += row..row
        }
        return result
    }
}
