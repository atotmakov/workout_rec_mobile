package com.workoutrec.automation

import com.workoutrec.fakes.FakeScriptApi
import com.workoutrec.fakes.FakeSheetsApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// Feature 006 research R7; contracts/apps-script.md "Upgrading an attached script"
class ScriptUpgradeTest {

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
    fun `uploads the current files to the recorded script, then records the version`() = runTest {
        installer.upgrade("sheet-1", "script-9", "Europe/Moscow", existingIds = mapOf("workout_rec.script_version" to 4))
        // No new version or deployment: the Enable page keeps its deployed version.
        assertEquals(listOf("content:script-9", "upsert:sheet-1"), log)
        assertEquals(mapOf("workout_rec.script_version" to "5"), sheets.upserts.single())
        val files = script.uploaded.single().associateBy { it.name }
        assertEquals(setOf("appsscript", "Config", "Code", "Logic", "Enable"), files.keys)
        val config = files.getValue("Config").source
        assertTrue(config.contains("const SPREADSHEET_ID = 'sheet-1';"))
        assertTrue(config.contains("const SCRIPT_VERSION = 5;"))
    }

    @Test
    fun `the version key and number`() {
        assertEquals("workout_rec.script_version", AutomationStatusEvaluator.SCRIPT_VERSION)
        assertEquals(5, ScriptInstaller.SCRIPT_VERSION)
    }

    @Test
    fun `an install records the version too`() = runTest {
        installer.install("sheet-1", "Europe/Moscow", existingMetadata = emptyMap(), existingIds = emptyMap())
        assertEquals("5", sheets.upserts.single()["workout_rec.script_version"])
    }
}
