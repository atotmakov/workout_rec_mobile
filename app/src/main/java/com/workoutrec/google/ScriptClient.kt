package com.workoutrec.google

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request

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

    @Serializable
    private data class Project(val scriptId: String)

    @Serializable
    private data class Version(val versionNumber: Int)

    @Serializable
    private data class WebApp(val url: String? = null)

    @Serializable
    private data class EntryPoint(val entryPointType: String = "", val webApp: WebApp? = null)

    @Serializable
    private data class Deployment(val deploymentId: String = "", val entryPoints: List<EntryPoint> = emptyList())

    /** Call 7: a script attached to the spreadsheet; 403 here usually means step 1 is needed. */
    override suspend fun createProject(title: String, parentId: String): String {
        val body = buildJsonObject {
            put("title", title)
            put("parentId", parentId)
        }
        val request = Request.Builder().url(projects().build()).post(body.toString().toJsonBody()).build()
        return GoogleJson.decodeFromString<Project>(http.execute(request)).scriptId
    }

    /** Call 8. */
    override suspend fun updateContent(scriptId: String, files: List<ScriptFile>) {
        val body = buildJsonObject {
            put(
                "files",
                buildJsonArray {
                    files.forEach { file ->
                        add(
                            buildJsonObject {
                                put("name", file.name)
                                put("type", file.type.name)
                                put("source", file.source)
                            },
                        )
                    }
                },
            )
        }
        val url = projects().addPathSegment(scriptId).addPathSegment("content").build()
        http.execute(Request.Builder().url(url).put(body.toString().toJsonBody()).build())
    }

    /** Call 9. */
    override suspend fun createVersion(scriptId: String, description: String): Int {
        val body = buildJsonObject { put("description", description) }
        val url = projects().addPathSegment(scriptId).addPathSegment("versions").build()
        return GoogleJson.decodeFromString<Version>(http.execute(Request.Builder().url(url).post(body.toString().toJsonBody()).build())).versionNumber
    }

    /** Call 10: returns the "Enable automation" web app URL. */
    override suspend fun deployWebApp(scriptId: String, versionNumber: Int, description: String): String {
        val body = buildJsonObject {
            put("versionNumber", versionNumber)
            put("manifestFileName", "appsscript")
            put("description", description)
        }
        val url = projects().addPathSegment(scriptId).addPathSegment("deployments").build()
        val deployment = GoogleJson.decodeFromString<Deployment>(
            http.execute(Request.Builder().url(url).post(body.toString().toJsonBody()).build()),
        )
        return deployment.entryPoints.firstOrNull { it.entryPointType == "WEB_APP" }?.webApp?.url
            ?: throw ApiError.Unexpected(200, "Deployment ${deployment.deploymentId} has no web app entry point")
    }

    private fun projects(): HttpUrl.Builder = baseUrl.newBuilder().addPathSegments("v1/projects")
}
