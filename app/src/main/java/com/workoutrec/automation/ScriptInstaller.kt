package com.workoutrec.automation

import com.workoutrec.google.ScriptApi
import com.workoutrec.google.SheetsApi

/** Reads the bundled script files (assets/apps-script/). */
fun interface ScriptAssets {
    fun read(name: String): String
}

data class InstalledScript(val scriptId: String, val enableUrl: String)

/** Attaches the script to the spreadsheet (research R8). */
interface ScriptInstall {
    suspend fun install(
        spreadsheetId: String,
        timeZone: String,
        existingMetadata: Map<String, String>,
        existingIds: Map<String, Int>,
    ): InstalledScript
}

class ScriptInstaller(
    private val script: ScriptApi,
    private val sheets: SheetsApi,
    private val assets: ScriptAssets,
) : ScriptInstall {
    override suspend fun install(
        spreadsheetId: String,
        timeZone: String,
        existingMetadata: Map<String, String>,
        existingIds: Map<String, Int>,
    ): InstalledScript = TODO()
}
