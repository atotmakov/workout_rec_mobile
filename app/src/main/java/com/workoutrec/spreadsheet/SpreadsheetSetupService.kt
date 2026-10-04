package com.workoutrec.spreadsheet

import com.workoutrec.google.ApiError
import com.workoutrec.google.DriveApi
import com.workoutrec.google.DriveFile
import com.workoutrec.google.SheetsApi
import java.time.LocalDate

data class SpreadsheetCheck(val result: StructureCheckResult, val snapshot: SpreadsheetSnapshot)

/** Find, create, check and rewrite the workout spreadsheet (US2). */
interface SpreadsheetSetup {
    suspend fun find(): DriveFile?
    suspend fun create(timeZone: String): String
    suspend fun check(spreadsheetId: String): SpreadsheetCheck
    suspend fun rewrite(oldSpreadsheetId: String, timeZone: String): String
    suspend fun exists(spreadsheetId: String): Boolean

    /** Adds the reference tables that the spreadsheet does not have yet (safe to repeat). */
    suspend fun ensureTables(spreadsheetId: String)
}

class SpreadsheetSetupService(
    private val drive: DriveApi,
    private val sheets: SheetsApi,
    private val today: () -> LocalDate = { LocalDate.now() },
) : SpreadsheetSetup {

    /** The newest app-created `workout_rec_database_` (spec edge case "more than one"). */
    override suspend fun find(): DriveFile? = drive.findSpreadsheets(ReferenceStructure.TITLE).firstOrNull()

    /** One all-or-nothing create; if Google rejects part of it, minimal create + one batchUpdate. */
    override suspend fun create(timeZone: String): String = try {
        sheets.create(ReferenceStructure.createRequest(timeZone))
    } catch (e: ApiError.Unexpected) {
        if (e.code != 400) throw e
        val id = sheets.create(ReferenceStructure.minimalCreateRequest(timeZone))
        sheets.batchUpdate(id, ReferenceStructure.completionRequests())
        id
    }

    /** Calls 5a + 5b; 5b only for tabs that exist, so a renamed tab is a mismatch, not an error. */
    override suspend fun check(spreadsheetId: String): SpreadsheetCheck {
        val tabs = sheets.readTabsAndMetadata(spreadsheetId)
        val ranges = ReferenceStructure.STRUCTURE_RANGES.filterKeys { it in tabs.titles }.values.toList()
        val cells = sheets.readStructureCells(spreadsheetId, ranges)
        val snapshot = SpreadsheetSnapshot.parse(tabs, cells)
        return SpreadsheetCheck(StructureValidator.check(snapshot), snapshot)
    }

    /** Clarification Q2: keep the old file as a dated backup, then create a new one. Never deletes. */
    override suspend fun rewrite(oldSpreadsheetId: String, timeZone: String): String {
        val backupName = BackupNamer.choose(today()) { drive.isNameTaken(it) }
        drive.rename(oldSpreadsheetId, backupName)
        return create(timeZone)
    }

    override suspend fun ensureTables(spreadsheetId: String): Unit = TODO()

    /** Call 11: false when the file is trashed or gone. */
    override suspend fun exists(spreadsheetId: String): Boolean = try {
        drive.getFile(spreadsheetId).trashed != true
    } catch (e: ApiError.NotFound) {
        false
    }
}
