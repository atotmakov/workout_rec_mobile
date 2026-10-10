package com.workoutrec.spreadsheet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// contracts/spreadsheet.md "Structure match rule"
class StructureValidatorTest {

    private val reference = Snapshots.reference()

    private fun mismatch(snapshot: SpreadsheetSnapshot): List<MismatchReason> {
        val result = StructureValidator.check(snapshot)
        assertTrue("expected Mismatch but was $result", result is StructureCheckResult.Mismatch)
        return (result as StructureCheckResult.Mismatch).reasons
    }

    @Test
    fun `exact reference matches`() {
        assertEquals(StructureCheckResult.Match, StructureValidator.check(reference))
    }

    @Test
    fun `missing tab`() {
        val snapshot = reference.copy(tabTitles = reference.tabTitles - "balance")
        assertEquals(listOf(MismatchReason.MissingTab("balance")), mismatch(snapshot))
    }

    @Test
    fun `renamed tab is a missing tab, not an error`() {
        val snapshot = reference.copy(
            tabTitles = reference.tabTitles.map { if (it == "money") "pay" else it },
            headerRows = reference.headerRows - "money",
        )
        assertEquals(listOf(MismatchReason.MissingTab("money")), mismatch(snapshot))
    }

    @Test
    fun `wrong header`() {
        val snapshot = reference.copy(headerRows = reference.headerRows + ("log" to listOf("Date", "Exercise", "W", "R")))
        assertEquals(listOf(MismatchReason.WrongHeader("log", 1, expected = "Drill", actual = "Exercise")), mismatch(snapshot))
    }

    @Test
    fun `missing header cell`() {
        val snapshot = reference.copy(headerRows = reference.headerRows + ("drills" to listOf("mscl")))
        assertEquals(listOf(MismatchReason.WrongHeader("drills", 1, expected = "drill", actual = null)), mismatch(snapshot))
    }

    @Test
    fun `missing or altered rec formula`() {
        assertEquals(listOf(MismatchReason.MissingRecFormula), mismatch(reference.copy(recA2Formula = null)))
        assertEquals(listOf(MismatchReason.MissingRecFormula), mismatch(reference.copy(recA2Formula = "=FILTER(log!A:Z, log!C:C=A1)")))
    }

    @Test
    fun `rec formula comparison ignores whitespace`() {
        assertEquals(StructureCheckResult.Match, StructureValidator.check(reference.copy(recA2Formula = "=FILTER(log!A:Z,log!B:B=A1)")))
    }

    @Test
    fun `formulas in the ru_RU form with semicolons match`() {
        val snapshot = reference.copy(
            recA2Formula = "=FILTER(log!A:Z; log!B:B=A1)",
            recRules = reference.recRules.map { it.copy(formula = it.formula.replace(",", ";")) },
        )
        assertEquals(StructureCheckResult.Match, StructureValidator.check(snapshot))
    }

    @Test
    fun `missing drop-down`() {
        assertEquals(listOf(MismatchReason.MissingRecDropDown), mismatch(reference.copy(recDropDownRange = null)))
        assertEquals(listOf(MismatchReason.MissingRecDropDown), mismatch(reference.copy(recDropDownRange = "=drills!\$A:\$A")))
    }

    @Test
    fun `missing conditional rule`() {
        assertEquals(listOf(MismatchReason.MissingConditionalRule(1)), mismatch(reference.copy(recRules = reference.recRules.take(1))))
    }

    @Test
    fun `extra tabs, extra columns, tab order and extra rules still match`() {
        val snapshot = reference.copy(
            tabTitles = listOf("balance", "notes") + reference.tabTitles.reversed().filter { it != "balance" },
            headerRows = reference.headerRows + ("log" to listOf("Date", "Drill", "W", "R", "comment")),
            recRules = reference.recRules + ConditionalRule("A2:A10", "=TRUE"),
        )
        assertEquals(StructureCheckResult.Match, StructureValidator.check(snapshot))
    }

    @Test
    fun `script metadata does not affect the structure`() {
        val snapshot = reference.copy(metadata = mapOf("workout_rec.script_id" to "s1"))
        assertEquals(StructureCheckResult.Match, StructureValidator.check(snapshot))
    }

    // Match rules 6-7 (tables, log drop-down), structure version 2

    @Test
    fun `missing table`() {
        val snapshot = reference.copy(tables = reference.tables - "money")
        assertEquals(listOf(MismatchReason.MissingTable("money", "payments")), mismatch(snapshot))
    }

    @Test
    fun `table with another name counts as missing`() {
        val renamed = reference.tables + ("workout" to listOf(reference.tables.getValue("workout").single().copy(name = "Table1")))
        assertEquals(listOf(MismatchReason.MissingTable("workout", "workouts")), mismatch(reference.copy(tables = renamed)))
    }

    @Test
    fun `wrong column type`() {
        val log = reference.tables.getValue("log").single()
        val changed = log.copy(columns = log.columns.map { if (it.index == 2) it.copy(type = "TEXT") else it })
        assertEquals(
            listOf(MismatchReason.WrongColumnType("log", 2, expected = "DOUBLE", actual = "TEXT")),
            mismatch(reference.copy(tables = reference.tables + ("log" to listOf(changed)))),
        )
    }

    @Test
    fun `missing column counts as a wrong type`() {
        val drills = reference.tables.getValue("drills").single()
        val shorter = drills.copy(columns = drills.columns.take(1))
        assertEquals(
            listOf(MismatchReason.WrongColumnType("drills", 1, expected = "COLUMN_TYPE_UNSPECIFIED", actual = null)),
            mismatch(reference.copy(tables = reference.tables + ("drills" to listOf(shorter)))),
        )
    }

    @Test
    fun `missing log drop-down`() {
        assertEquals(listOf(MismatchReason.MissingLogDropDown), mismatch(reference.copy(logDropDownRange = null)))
    }

    @Test
    fun `version 1 spreadsheet without tables mismatches`() {
        val v1 = reference.copy(tables = emptyMap(), logDropDownRange = null)
        assertEquals(
            listOf(
                MismatchReason.MissingTable("log", "log"),
                MismatchReason.MissingTable("drills", "drills"),
                MismatchReason.MissingTable("money", "payments"),
                MismatchReason.MissingTable("workout", "workouts"),
                MismatchReason.MissingLogDropDown,
            ),
            mismatch(v1),
        )
    }

    @Test
    fun `extra tables and extra table columns are ignored`() {
        val log = reference.tables.getValue("log").single()
        val wider = log.copy(columns = log.columns + com.workoutrec.google.TableColumn(4, "note", "TEXT"))
        val tables = reference.tables + ("log" to listOf(wider)) + ("rec" to listOf(com.workoutrec.google.SheetTable("extra", emptyList())))
        assertEquals(StructureCheckResult.Match, StructureValidator.check(reference.copy(tables = tables)))
    }

    @Test
    fun `missing rec tab reports only the tab`() {
        val snapshot = reference.copy(
            tabTitles = reference.tabTitles - "rec",
            recDropDownRange = null,
            recA2Formula = null,
            recRules = emptyList(),
        )
        assertEquals(listOf(MismatchReason.MissingTab("rec")), mismatch(snapshot))
    }
}
