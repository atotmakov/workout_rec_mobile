package com.workoutrec.fakes

import com.workoutrec.automation.InstalledScript
import com.workoutrec.automation.ScriptInstall
import com.workoutrec.google.ApiError
import com.workoutrec.google.DriveFile
import com.workoutrec.spreadsheet.SpreadsheetCheck
import com.workoutrec.spreadsheet.SpreadsheetSetup
import com.workoutrec.spreadsheet.StructureCheckResult
import com.workoutrec.spreadsheet.StructureValidator
import com.workoutrec.spreadsheet.Snapshots

/** Configurable [SpreadsheetSetup]; records calls in [log]. */
class FakeSpreadsheetSetupService(
    var found: DriveFile? = null,
    var metadata: Map<String, String> = emptyMap(),
    var matches: Boolean = true,
    var existing: Set<String> = emptySet(),
    val log: MutableList<String> = mutableListOf(),
) : SpreadsheetSetup {
    /** Errors to throw from the next calls, by method name ("find", "create", ...). */
    val failNext = mutableMapOf<String, ApiError>()

    private fun maybeFail(name: String) {
        failNext.remove(name)?.let { throw it }
    }

    override suspend fun find(): DriveFile? {
        log += "find"
        maybeFail("find")
        return found
    }

    override suspend fun create(timeZone: String): String {
        log += "create"
        maybeFail("create")
        return "created"
    }

    override suspend fun check(spreadsheetId: String): SpreadsheetCheck {
        log += "check:$spreadsheetId"
        maybeFail("check")
        val reference = Snapshots.reference()
        val snapshot = if (matches) reference.copy(metadata = metadata) else reference.copy(tabTitles = emptyList(), metadata = metadata)
        val result = if (matches) StructureCheckResult.Match else StructureValidator.check(snapshot)
        return SpreadsheetCheck(result, snapshot)
    }

    override suspend fun rewrite(oldSpreadsheetId: String, timeZone: String): String {
        log += "rename:$oldSpreadsheetId"
        maybeFail("rewrite")
        log += "create"
        return "rewritten"
    }

    override suspend fun exists(spreadsheetId: String): Boolean {
        log += "exists:$spreadsheetId"
        return spreadsheetId in existing
    }

    val writeCalls: List<String> get() = log.filter { it == "create" || it.startsWith("rename") }
}

class FakeScriptInstaller(
    var apiDisabled: Boolean = false,
    val log: MutableList<String> = mutableListOf(),
) : ScriptInstall {
    override suspend fun install(
        spreadsheetId: String,
        timeZone: String,
        existingMetadata: Map<String, String>,
        existingIds: Map<String, Int>,
    ): InstalledScript {
        log += "install:$spreadsheetId"
        if (apiDisabled) throw ApiError.AppsScriptApiDisabled("User has not enabled the Apps Script API")
        return InstalledScript("script-1", "https://enable")
    }
}
