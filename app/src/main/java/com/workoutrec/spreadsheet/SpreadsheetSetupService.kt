package com.workoutrec.spreadsheet

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
}

class SpreadsheetSetupService(
    private val drive: DriveApi,
    private val sheets: SheetsApi,
    private val today: () -> LocalDate = { LocalDate.now() },
) : SpreadsheetSetup {
    override suspend fun find(): DriveFile? = TODO()
    override suspend fun create(timeZone: String): String = TODO()
    override suspend fun check(spreadsheetId: String): SpreadsheetCheck = TODO()
    override suspend fun rewrite(oldSpreadsheetId: String, timeZone: String): String = TODO()
    override suspend fun exists(spreadsheetId: String): Boolean = TODO()
}
