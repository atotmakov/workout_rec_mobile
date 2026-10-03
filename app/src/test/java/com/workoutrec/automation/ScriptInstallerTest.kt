package com.workoutrec.automation

import com.workoutrec.fakes.FakeScriptApi
import com.workoutrec.fakes.FakeSheetsApi
import com.workoutrec.google.GoogleJson
import com.workoutrec.google.ScriptFileType
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// research.md R8; contracts/apps-script.md "Files"
class ScriptInstallerTest {

    private val log = mutableListOf<String>()
    private val script = FakeScriptApi(log = log)
    private val sheets = FakeSheetsApi(log = log)
    private val assets = ScriptAssets { name ->
        when (name) {
            "appsscript.json" -> """{"timeZone":"Etc/UTC","runtimeVersion":"V8"}"""
            "Code.gs" -> "// code"
            "Logic.gs" -> "// logic"
            "Enable.html" -> "<p></p>"
            else -> error("unexpected asset $name")
        }
    }
    private val installer = ScriptInstaller(script, sheets, assets)

    @Test
    fun `creates, uploads, versions, deploys, then records metadata`() = runTest {
        val installed = installer.install("sheet-1", "Europe/Moscow", existingMetadata = emptyMap(), existingIds = emptyMap())
        assertEquals(InstalledScript("script-1", "https://script.google.com/macros/s/dep/exec"), installed)
        assertEquals(
            listOf("createProject:sheet-1", "content:script-1", "version:script-1", "deploy:script-1:1", "upsert:sheet-1"),
            log,
        )
        assertEquals(
            mapOf(
                "workout_rec.script_id" to "script-1",
                "workout_rec.enable_url" to "https://script.google.com/macros/s/dep/exec",
            ),
            sheets.upserts.single(),
        )
    }

    @Test
    fun `uploads the five files with types, manifest time zone and generated config`() = runTest {
        installer.install("sheet-1", "Europe/Moscow", emptyMap(), emptyMap())
        val files = script.uploaded.single().associateBy { it.name }
        assertEquals(setOf("appsscript", "Config", "Code", "Logic", "Enable"), files.keys)
        assertEquals(ScriptFileType.JSON, files.getValue("appsscript").type)
        assertEquals(ScriptFileType.HTML, files.getValue("Enable").type)
        assertEquals(ScriptFileType.SERVER_JS, files.getValue("Code").type)
        assertEquals(ScriptFileType.SERVER_JS, files.getValue("Config").type)
        val manifest = GoogleJson.parseToJsonElement(files.getValue("appsscript").source).jsonObject
        assertEquals("Europe/Moscow", manifest["timeZone"]!!.jsonPrimitive.content)
        assertEquals("V8", manifest["runtimeVersion"]!!.jsonPrimitive.content)
        val config = files.getValue("Config").source
        assertTrue(config.contains("const SPREADSHEET_ID = 'sheet-1';"))
        assertTrue(config.contains("const SCRIPT_VERSION = 1;"))
    }

    @Test
    fun `skips project creation when a script id already exists`() = runTest {
        installer.install("sheet-1", "Europe/Moscow", mapOf("workout_rec.script_id" to "existing"), mapOf("workout_rec.script_id" to 5))
        assertEquals(listOf("content:existing", "version:existing", "deploy:existing:1", "upsert:sheet-1"), log)
    }

    @Test
    fun `does nothing when the script and its page already exist`() = runTest {
        val installed = installer.install(
            "sheet-1",
            "Europe/Moscow",
            mapOf("workout_rec.script_id" to "existing", "workout_rec.enable_url" to "https://e"),
            emptyMap(),
        )
        assertEquals(InstalledScript("existing", "https://e"), installed)
        assertEquals(emptyList<String>(), log)
    }
}
