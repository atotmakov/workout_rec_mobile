package com.workoutrec.spreadsheet

import com.workoutrec.fakes.FakeDriveApi
import com.workoutrec.fakes.FakeSheetsApi
import com.workoutrec.google.ApiError
import com.workoutrec.google.DriveFile
import com.workoutrec.google.TabsAndMetadata
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpreadsheetSetupServiceTest {

    private val log = mutableListOf<String>()
    private val drive = FakeDriveApi(log = log)
    private val sheets = FakeSheetsApi(log = log)
    private val service = SpreadsheetSetupService(drive, sheets, today = { LocalDate.of(2026, 10, 3) })

    @Test
    fun `find returns the newest of several files`() = runTest {
        drive.spreadsheets = listOf(DriveFile("newest"), DriveFile("older"))
        assertEquals("newest", service.find()?.id)
        assertEquals(listOf("find:workout_rec_database_"), log)
    }

    @Test
    fun `find returns null when there is none`() = runTest {
        assertNull(service.find())
    }

    @Test
    fun `create sends one full create call`() = runTest {
        assertEquals("new-sheet", service.create("Europe/Moscow"))
        assertEquals(listOf("create"), log)
        assertEquals(ReferenceStructure.createRequest("Europe/Moscow"), sheets.createBodies.single())
    }

    @Test
    fun `rejected full create falls back to minimal create plus one batchUpdate`() = runTest {
        sheets.rejectFullCreate = true
        assertEquals("new-sheet", service.create("Europe/Moscow"))
        assertEquals(listOf("create", "create", "batchUpdate:new-sheet"), log)
        assertEquals(ReferenceStructure.minimalCreateRequest("Europe/Moscow"), sheets.createBodies[1])
        assertEquals(ReferenceStructure.completionRequests(), sheets.batchUpdates.single())
    }

    @Test
    fun `rewrite checks the backup name, renames, then creates`() = runTest {
        drive.takenNames = setOf("workout_rec_database_backup_2026-10-03")
        assertEquals("new-sheet", service.rewrite("old", "Europe/Moscow"))
        assertEquals(
            listOf(
                "taken?:workout_rec_database_backup_2026-10-03",
                "taken?:workout_rec_database_backup_2026-10-03_2",
                "rename:old->workout_rec_database_backup_2026-10-03_2",
                "create",
            ),
            log,
        )
    }

    @Test
    fun `nothing is ever deleted or trashed`() = runTest {
        drive.spreadsheets = listOf(DriveFile("old"))
        service.find()
        service.rewrite("old", "Europe/Moscow")
        assertFalse(log.any { "delete" in it || "trash" in it }) // FR-010
    }

    @Test
    fun `check reads tabs first and requests ranges only for tabs that exist`() = runTest {
        sheets.tabs = TabsAndMetadata(
            titles = listOf("log", "drills", "rec", "pay", "workout", "balance"),
            metadata = mapOf("workout_rec.script_id" to "s1"),
            metadataIds = mapOf("workout_rec.script_id" to 7),
        )
        val check = service.check("id1")
        assertEquals(listOf("readTabs:id1", "readCells:id1"), log)
        assertEquals(listOf("log!1:2", "drills!1:1", "workout!1:1", "rec!A1:D2"), sheets.requestedRanges.single())
        assertTrue(check.result is StructureCheckResult.Mismatch)
        assertTrue(MismatchReason.MissingTab("money") in (check.result as StructureCheckResult.Mismatch).reasons)
        assertEquals(mapOf("workout_rec.script_id" to "s1"), check.snapshot.metadata)
    }

    private fun addedTableNames(requests: kotlinx.serialization.json.JsonArray) = requests.map {
        it.jsonObject["addTable"]!!.jsonObject["table"]!!.jsonObject["name"]!!.jsonPrimitive.content
    }

    @Test
    fun `ensureTables adds all four tables to a new spreadsheet in one batchUpdate`() = runTest {
        sheets.tabs = TabsAndMetadata(ReferenceStructure.TABS, emptyMap(), emptyMap())
        service.ensureTables("new-sheet")
        assertEquals(listOf("readTabs:new-sheet", "batchUpdate:new-sheet"), log)
        assertEquals(listOf("log", "drills", "payments", "workouts"), addedTableNames(sheets.batchUpdates.single()))
    }

    @Test
    fun `ensureTables adds only the missing tables`() = runTest {
        sheets.tabs = TabsAndMetadata(
            ReferenceStructure.TABS,
            emptyMap(),
            emptyMap(),
            tables = mapOf("log" to Snapshots.referenceTables.getValue("log"), "drills" to Snapshots.referenceTables.getValue("drills")),
        )
        service.ensureTables("id1")
        assertEquals(listOf("payments", "workouts"), addedTableNames(sheets.batchUpdates.single()))
    }

    @Test
    fun `ensureTables does nothing when all tables exist`() = runTest {
        sheets.tabs = TabsAndMetadata(ReferenceStructure.TABS, emptyMap(), emptyMap(), tables = Snapshots.referenceTables)
        service.ensureTables("id1")
        assertEquals(listOf("readTabs:id1"), log)
    }

    @Test
    fun `exists is false for a trashed or missing file`() = runTest {
        drive.files = mapOf("a" to DriveFile("a", trashed = false), "b" to DriveFile("b", trashed = true))
        assertTrue(service.exists("a"))
        assertFalse(service.exists("b"))
        assertFalse(service.exists("gone"))
    }

    @Test(expected = ApiError.Offline::class)
    fun `other errors propagate`() = runTest {
        val offline = object : com.workoutrec.google.DriveApi by drive {
            override suspend fun findSpreadsheets(name: String): List<DriveFile> = throw ApiError.Offline
        }
        SpreadsheetSetupService(offline, sheets, today = { LocalDate.of(2026, 10, 3) }).find()
    }
}
