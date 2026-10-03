package com.workoutrec.spreadsheet

sealed interface MismatchReason {
    data class MissingTab(val tab: String) : MismatchReason
    data class WrongHeader(val tab: String, val column: Int, val expected: String, val actual: String?) : MismatchReason
    data object MissingRecFormula : MismatchReason
    data object MissingRecDropDown : MismatchReason
    data class MissingConditionalRule(val index: Int) : MismatchReason
}

sealed interface StructureCheckResult {
    data object Match : StructureCheckResult
    data class Mismatch(val reasons: List<MismatchReason>) : StructureCheckResult
}

object StructureValidator {
    fun check(snapshot: SpreadsheetSnapshot): StructureCheckResult = TODO()
}
