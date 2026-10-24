package com.workoutrec.sync

import com.workoutrec.automation.AutomationStatusEvaluator
import com.workoutrec.google.SheetsLogApi
import com.workoutrec.monitoring.Monitoring
import com.workoutrec.monitoring.NoMonitoring
import com.workoutrec.monitoring.NonFatalIssue
import com.workoutrec.monitoring.badValue
import com.workoutrec.monitoring.failure
import com.workoutrec.spreadsheet.CalculatedForm
import java.time.Instant
import kotlinx.coroutines.CancellationException

/**
 * Feature 007 research R5: after a successful sync, checks that durations and the balance are the
 * spreadsheet's formulas (call 24) and repairs them (call 25); the first repair converts an old
 * spreadsheet. Failures are reported and never change the outcome of the sync.
 */
class FormCheck(
    private val sheets: SheetsLogApi,
    private val monitoring: Monitoring = NoMonitoring,
    private val now: () -> Long = System::currentTimeMillis,
    private val log: (String) -> Unit = {},
) {

    suspend fun run(spreadsheetId: String, sheet: SheetInfoCache) {
        val workoutSheetId = sheet.workoutSheetId ?: return
        val balanceSheetId = sheet.balanceSheetId ?: return
        try {
            val cells = sheets.readCalculatedCells(spreadsheetId)
            val automation = AutomationStatusEvaluator.evaluate(cells.metadata, Instant.ofEpochMilli(now())) {
                monitoring.badValue(NonFatalIssue.BAD_SHEET_DATA, it)
            }
            val scriptVersion = cells.metadata[AutomationStatusEvaluator.SCRIPT_VERSION]?.toIntOrNull() ?: 1
            val decision = CalculatedForm.decide(cells, workoutSheetId, balanceSheetId, automation, scriptVersion)
            val repair = decision.repair ?: return
            sheets.repairCalculatedCells(spreadsheetId, repair)
            log(
                "sync: calculated cells repaired: ${repair.durationRuns.sumOf { it.count() }} durations, " +
                    "balance ${repair.balanceSheetId != null}, converted ${repair.metadata.isNotEmpty()}",
            )
            if (decision.restored.isNotEmpty()) {
                val details = decision.restored.toSortedMap().entries.joinToString(",") { (cell, count) -> "$cell:$count" }
                monitoring.nonFatal(NonFatalIssue.CALCULATION_RESTORED, CalculationOverwritten(), "cells=$details")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log("sync: calculated cells check failed: ${e::class.simpleName}")
            monitoring.failure(NonFatalIssue.CALCULATION_CHECK_FAILED, e, "calculation-check")
        }
    }
}

/** Reported when a duration or the balance was typed over (spec FR-011); carries no cell contents. */
class CalculationOverwritten : Exception("A calculated cell was overwritten")
