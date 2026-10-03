package com.workoutrec.google

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

// contracts/google-apis.md calls 1, 2, 3, 11
class DriveClientTest {

    private lateinit var server: MockWebServer
    private lateinit var drive: DriveClient

    @Before
    fun setUp() {
        server = MockWebServer().also { it.start() }
        val http = GoogleHttp(OkHttpClient(), { "tok" }, sleep = {})
        drive = DriveClient(http, baseUrl = server.url("/"))
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `call 1 finds app-visible spreadsheets by exact name, newest first`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"files":[{"id":"new","name":"workout_rec_database_","modifiedTime":"2026-10-02T10:00:00.000Z"},
                   {"id":"old","name":"workout_rec_database_","modifiedTime":"2026-09-01T10:00:00.000Z"}]}""",
            ),
        )
        val files = drive.findSpreadsheets("workout_rec_database_")
        assertEquals(listOf("new", "old"), files.map { it.id })

        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/drive/v3/files", request.requestUrl!!.encodedPath)
        assertEquals(
            "name = 'workout_rec_database_' and mimeType = 'application/vnd.google-apps.spreadsheet' and trashed = false",
            request.requestUrl!!.queryParameter("q"),
        )
        assertEquals("modifiedTime desc", request.requestUrl!!.queryParameter("orderBy"))
        assertEquals("files(id,name,modifiedTime)", request.requestUrl!!.queryParameter("fields"))
    }

    @Test
    fun `call 1 with no files returns an empty list`() = runTest {
        server.enqueue(MockResponse().setBody("""{"files":[]}"""))
        assertEquals(emptyList<DriveFile>(), drive.findSpreadsheets("workout_rec_database_"))
    }

    @Test
    fun `quotes in names are escaped`() = runTest {
        server.enqueue(MockResponse().setBody("""{"files":[]}"""))
        drive.isNameTaken("it's")
        assertTrue(server.takeRequest().requestUrl!!.queryParameter("q")!!.startsWith("name = 'it\\'s'"))
    }

    @Test
    fun `call 2 reports whether a backup name is taken`() = runTest {
        server.enqueue(MockResponse().setBody("""{"files":[{"id":"x"}]}"""))
        server.enqueue(MockResponse().setBody("""{"files":[]}"""))
        assertTrue(drive.isNameTaken("workout_rec_database_backup_2026-10-03"))
        assertFalse(drive.isNameTaken("workout_rec_database_backup_2026-10-03_2"))
        val request = server.takeRequest()
        assertEquals("name = 'workout_rec_database_backup_2026-10-03' and trashed = false", request.requestUrl!!.queryParameter("q"))
        assertEquals("files(id)", request.requestUrl!!.queryParameter("fields"))
    }

    @Test
    fun `call 3 renames with PATCH and only the name`() = runTest {
        server.enqueue(MockResponse().setBody("""{"id":"abc","name":"workout_rec_database_backup_2026-10-03"}"""))
        drive.rename("abc", "workout_rec_database_backup_2026-10-03")
        val request = server.takeRequest()
        assertEquals("PATCH", request.method)
        assertEquals("/drive/v3/files/abc", request.requestUrl!!.encodedPath)
        assertEquals("""{"name":"workout_rec_database_backup_2026-10-03"}""", request.body.readUtf8())
    }

    @Test
    fun `call 11 reads id and trashed`() = runTest {
        server.enqueue(MockResponse().setBody("""{"id":"abc","trashed":true}"""))
        assertEquals(DriveFile(id = "abc", trashed = true), drive.getFile("abc"))
        val request = server.takeRequest()
        assertEquals("/drive/v3/files/abc", request.requestUrl!!.encodedPath)
        assertEquals("id,trashed", request.requestUrl!!.queryParameter("fields"))
    }

    @Test
    fun `there is no way to delete or trash a file`() {
        val methods = DriveClient::class.java.declaredMethods.map { it.name.lowercase() }
        assertFalse(methods.any { "delete" in it || "trash" in it }) // FR-010
    }
}
