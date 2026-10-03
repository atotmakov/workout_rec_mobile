package com.workoutrec.fakes

import com.workoutrec.google.ApiError
import com.workoutrec.google.DriveApi
import com.workoutrec.google.DriveFile
import com.workoutrec.google.ScriptApi
import com.workoutrec.google.ScriptFile
import com.workoutrec.google.SheetsApi
import com.workoutrec.google.TabsAndMetadata
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

/** Records every call in [log] so tests can check order and that nothing is deleted. */
class FakeDriveApi(
    var spreadsheets: List<DriveFile> = emptyList(),
    var takenNames: Set<String> = emptySet(),
    var files: Map<String, DriveFile> = emptyMap(),
    val log: MutableList<String> = mutableListOf(),
) : DriveApi {
    override suspend fun findSpreadsheets(name: String): List<DriveFile> {
        log += "find:$name"
        return spreadsheets
    }

    override suspend fun isNameTaken(name: String): Boolean {
        log += "taken?:$name"
        return name in takenNames
    }

    override suspend fun rename(fileId: String, newName: String) {
        log += "rename:$fileId->$newName"
    }

    override suspend fun getFile(fileId: String): DriveFile {
        log += "get:$fileId"
        return files[fileId] ?: throw ApiError.NotFound
    }
}

class FakeSheetsApi(
    var createdId: String = "new-sheet",
    var rejectFullCreate: Boolean = false,
    var tabs: TabsAndMetadata = TabsAndMetadata(emptyList(), emptyMap(), emptyMap()),
    var cells: JsonObject = JsonObject(emptyMap()),
    val log: MutableList<String> = mutableListOf(),
) : SheetsApi {
    val createBodies = mutableListOf<JsonObject>()
    val batchUpdates = mutableListOf<JsonArray>()
    val requestedRanges = mutableListOf<List<String>>()
    val upserts = mutableListOf<Map<String, String>>()

    override suspend fun create(body: JsonObject): String {
        log += "create"
        createBodies += body
        if (rejectFullCreate && body.containsKey("developerMetadata")) {
            throw ApiError.Unexpected(400, "Invalid spreadsheet")
        }
        return createdId
    }

    override suspend fun batchUpdate(spreadsheetId: String, requests: JsonArray) {
        log += "batchUpdate:$spreadsheetId"
        batchUpdates += requests
    }

    override suspend fun readTabsAndMetadata(spreadsheetId: String): TabsAndMetadata {
        log += "readTabs:$spreadsheetId"
        return tabs
    }

    override suspend fun readStructureCells(spreadsheetId: String, ranges: List<String>): JsonObject {
        log += "readCells:$spreadsheetId"
        requestedRanges += ranges
        return cells
    }

    override suspend fun upsertMetadata(spreadsheetId: String, values: Map<String, String>, existingIds: Map<String, Int>) {
        log += "upsert:$spreadsheetId"
        upserts += values
    }
}

class FakeScriptApi(
    var disabled: Boolean = false,
    val log: MutableList<String> = mutableListOf(),
) : ScriptApi {
    val uploaded = mutableListOf<List<ScriptFile>>()

    override suspend fun createProject(title: String, parentId: String): String {
        log += "createProject:$parentId"
        if (disabled) throw ApiError.AppsScriptApiDisabled("User has not enabled the Apps Script API")
        return "script-1"
    }

    override suspend fun updateContent(scriptId: String, files: List<ScriptFile>) {
        log += "content:$scriptId"
        uploaded += files
    }

    override suspend fun createVersion(scriptId: String, description: String): Int {
        log += "version:$scriptId"
        return 1
    }

    override suspend fun deployWebApp(scriptId: String, versionNumber: Int, description: String): String {
        log += "deploy:$scriptId:$versionNumber"
        return "https://script.google.com/macros/s/dep/exec"
    }
}
