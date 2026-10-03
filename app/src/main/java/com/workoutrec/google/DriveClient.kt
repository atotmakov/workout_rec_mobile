package com.workoutrec.google

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request

@Serializable
data class DriveFile(
    val id: String,
    val name: String? = null,
    val modifiedTime: String? = null,
    val trashed: Boolean? = null,
)

/** Drive calls 1, 2, 3, 11 (contracts/google-apis.md). There is deliberately no delete (FR-010). */
interface DriveApi {
    suspend fun findSpreadsheets(name: String): List<DriveFile>
    suspend fun isNameTaken(name: String): Boolean
    suspend fun rename(fileId: String, newName: String)
    suspend fun getFile(fileId: String): DriveFile
}

class DriveClient(
    private val http: GoogleHttp,
    private val baseUrl: HttpUrl = "https://www.googleapis.com/".toHttpUrl(),
) : DriveApi {

    @Serializable
    private data class FileList(val files: List<DriveFile> = emptyList())

    /** Call 1. With drive.file, only files this app created are visible (clarification Q1). */
    override suspend fun findSpreadsheets(name: String): List<DriveFile> {
        val url = files()
            .addQueryParameter("q", "name = ${quote(name)} and mimeType = '$SPREADSHEET_MIME' and trashed = false")
            .addQueryParameter("orderBy", "modifiedTime desc")
            .addQueryParameter("fields", "files(id,name,modifiedTime)")
            .build()
        return GoogleJson.decodeFromString<FileList>(http.execute(Request.Builder().url(url).build())).files
    }

    /** Call 2. */
    override suspend fun isNameTaken(name: String): Boolean {
        val url = files()
            .addQueryParameter("q", "name = ${quote(name)} and trashed = false")
            .addQueryParameter("fields", "files(id)")
            .build()
        return GoogleJson.decodeFromString<FileList>(http.execute(Request.Builder().url(url).build())).files.isNotEmpty()
    }

    /** Call 3. */
    override suspend fun rename(fileId: String, newName: String) {
        val body = buildJsonObject { put("name", newName) }.toString().toJsonBody()
        http.execute(Request.Builder().url(files().addPathSegment(fileId).build()).patch(body).build())
    }

    /** Call 11. */
    override suspend fun getFile(fileId: String): DriveFile {
        val url = files().addPathSegment(fileId).addQueryParameter("fields", "id,trashed").build()
        return GoogleJson.decodeFromString(http.execute(Request.Builder().url(url).build()))
    }

    private fun files(): HttpUrl.Builder = baseUrl.newBuilder().addPathSegments("drive/v3/files")

    /** Drive query string literal: escape backslashes and single quotes. */
    private fun quote(value: String) = "'" + value.replace("\\", "\\\\").replace("'", "\\'") + "'"

    private companion object {
        const val SPREADSHEET_MIME = "application/vnd.google-apps.spreadsheet"
    }
}
