package com.workoutrec.spreadsheet

import com.workoutrec.automation.AutomationStatus
import com.workoutrec.google.CalculatedCells
import com.workoutrec.google.CalculatedRow
import com.workoutrec.google.CellContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// Feature 007 research R5, R6; data-model.md "Calculated cells: states"
class CalculatedFormTest {

    private val duration = CellContent(ReferenceStructure.DURATION_FORMULA, empty = false)
    private val balance = CellContent(ReferenceStructure.BALANCE_FORMULA, empty = false)
    private val empty = CellContent(null, empty = true)
    private val value = CellContent(null, empty = false)

    private val version = ReferenceStructure.STRUCTURE_VERSION_KEY

    private fun cells(vararg durations: CellContent, balanceCell: CellContent = balance, structure: String? = "3") = CalculatedCells(
        rows = durations.mapIndexed { i, cell -> CalculatedRow(i + 1, hasDate = true, duration = cell) },
        balance = balanceCell,
        metadata = listOfNotNull(structure?.let { version to it }).toMap(),
        metadataIds = mapOf(version to 7),
    )

    private fun decide(cells: CalculatedCells, automation: AutomationStatus = AutomationStatus.NotEnabled, scriptVersion: Int = 3) =
        CalculatedForm.decide(cells, workoutSheetId = 9, balanceSheetId = 5, automation = automation, scriptVersion = scriptVersion)

    @Test
    fun `a converted spreadsheet with every formula in place needs nothing`() {
        val decision = decide(cells(duration, duration))
        assertNull(decision.repair)
        assertEquals(emptyMap<String, Int>(), decision.restored)
    }

    @Test
    fun `an empty duration is filled without a report`() {
        val decision = decide(cells(duration, empty))
        assertEquals(listOf(2..2), decision.repair?.durationRuns)
        assertEquals(emptyMap<String, Int>(), decision.restored)
    }

    @Test
    fun `a typed value in a converted spreadsheet is restored and reported by kind with counts`() {
        val decision = decide(cells(value, value, duration, value, balanceCell = value))
        assertEquals(listOf(1..2, 4..4), decision.repair?.durationRuns)
        assertEquals(5, decision.repair?.balanceSheetId)
        assertEquals(mapOf("duration" to 3, "balance" to 1), decision.restored)
        assertEquals(emptyMap<String, String>(), decision.repair?.metadata)
    }

    @Test
    fun `a different formula counts as overwritten`() {
        val decision = decide(cells(CellContent("=1+1", empty = false)))
        assertEquals(mapOf("duration" to 1), decision.restored)
    }

    @Test
    fun `formulas are compared without spaces and with the locale's separator`() {
        val ru = ReferenceStructure.DURATION_FORMULA.replace(",", ";").replace(" ", "")
        assertNull(decide(cells(CellContent(ru, empty = false))).repair)
    }

    @Test
    fun `a duration formula that did not parse is rewritten without a report`() {
        val unparsed = CellContent(ReferenceStructure.DURATION_FORMULA, empty = false, parseError = true)
        val decision = decide(cells(duration, unparsed))
        assertEquals(listOf(2..2), decision.repair?.durationRuns)
        assertEquals(emptyMap<String, Int>(), decision.restored)
    }

    @Test
    fun `durations are written in the spreadsheet's locale`() {
        val ru = decide(cells(empty).copy(locale = "ru_RU")).repair!!.durationFormula
        assertEquals(ReferenceStructure.DURATION_FORMULA.replace(',', ';'), ru)
        assertEquals(ReferenceStructure.DURATION_FORMULA, decide(cells(empty).copy(locale = "en_US")).repair!!.durationFormula)
        assertEquals(ReferenceStructure.DURATION_FORMULA, decide(cells(empty)).repair!!.durationFormula)
    }

    @Test
    fun `the locale's argument separator follows its decimal separator`() {
        assertEquals(false, ReferenceStructure.durationFormula("de_DE").contains(','))
        assertEquals(false, ReferenceStructure.durationFormula("fr_FR").contains(','))
        assertEquals(false, ReferenceStructure.durationFormula("en_GB").contains(';'))
        assertEquals(false, ReferenceStructure.durationFormula("ja_JP").contains(';'))
    }

    @Test
    fun `rows without a date are left alone`() {
        val cells = cells(duration).copy(rows = listOf(CalculatedRow(1, hasDate = false, duration = value), CalculatedRow(2, hasDate = true, duration = duration)))
        assertNull(decide(cells).repair)
    }

    @Test
    fun `converting writes every formula and the version marker, with no report`() {
        val decision = decide(cells(value, empty, balanceCell = value, structure = "2"))
        assertEquals(listOf(1..2), decision.repair?.durationRuns)
        assertEquals(5, decision.repair?.balanceSheetId)
        assertEquals(mapOf(version to "3"), decision.repair?.metadata)
        assertEquals(mapOf(version to 7), decision.repair?.metadataIds)
        assertEquals(emptyMap<String, Int>(), decision.restored)
    }

    @Test
    fun `a spreadsheet without the version key is converted`() {
        assertEquals(mapOf(version to "3"), decide(cells(duration, structure = null)).repair?.metadata)
    }

    @Test
    fun `an old script still running at night closes the gate - durations only`() {
        val decision = decide(cells(value, balanceCell = value, structure = "2"), automation = AutomationStatus.On, scriptVersion = 2)
        assertEquals(listOf(1..1), decision.repair?.durationRuns)
        assertNull(decision.repair?.balanceSheetId)
        assertEquals(emptyMap<String, String>(), decision.repair?.metadata)
        assertEquals(emptyMap<String, Int>(), decision.restored)
    }

    @Test
    fun `an old script that no longer runs does not close the gate`() {
        val decision = decide(cells(duration, balanceCell = value, structure = "2"), automation = AutomationStatus.Stopped, scriptVersion = 2)
        assertEquals(5, decision.repair?.balanceSheetId)
        assertEquals(mapOf(version to "3"), decision.repair?.metadata)
    }

    @Test
    fun `an upgraded script opens the gate`() {
        val decision = decide(cells(duration, balanceCell = value, structure = "2"), automation = AutomationStatus.On, scriptVersion = 3)
        assertEquals(5, decision.repair?.balanceSheetId)
    }
}
