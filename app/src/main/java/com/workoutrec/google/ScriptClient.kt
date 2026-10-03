package com.workoutrec.google

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

enum class ScriptFileType { SERVER_JS, HTML, JSON }

data class ScriptFile(val name: String, val type: ScriptFileType, val source: String)

/** Apps Script calls 7-10 (contracts/google-apis.md). */
interface ScriptApi {
    suspend fun createProject(title: String, parentId: String): String
    suspend fun updateContent(scriptId: String, files: List<ScriptFile>)
    suspend fun createVersion(scriptId: String, description: String): Int
    suspend fun deployWebApp(scriptId: String, versionNumber: Int, description: String): String
}

class ScriptClient(
    private val http: GoogleHttp,
    private val baseUrl: HttpUrl = "https://script.googleapis.com/".toHttpUrl(),
) : ScriptApi {
    override suspend fun createProject(title: String, parentId: String): String = TODO()
    override suspend fun updateContent(scriptId: String, files: List<ScriptFile>): Unit = TODO()
    override suspend fun createVersion(scriptId: String, description: String): Int = TODO()
    override suspend fun deployWebApp(scriptId: String, versionNumber: Int, description: String): String = TODO()
}
