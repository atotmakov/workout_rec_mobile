package com.workoutrec.google

import kotlinx.serialization.Serializable
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

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
    override suspend fun findSpreadsheets(name: String): List<DriveFile> = TODO()
    override suspend fun isNameTaken(name: String): Boolean = TODO()
    override suspend fun rename(fileId: String, newName: String): Unit = TODO()
    override suspend fun getFile(fileId: String): DriveFile = TODO()
}
