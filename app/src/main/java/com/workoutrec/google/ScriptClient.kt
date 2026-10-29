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
    /** The Head deployment's id; its web app (`/dev`) runs the project's current code. */
    suspend fun headDeploymentId(scriptId: String): String
}

class ScriptClient(
    private val http: GoogleHttp,
    private val baseUrl: HttpUrl = "https://script.googleapis.com/".toHttpUrl(),
) : ScriptApi {

    @Serializable
    private data class Project(val scriptId: String)

    @Serializable
    private data class DeploymentConfig(val versionNumber: Int? = null)

    @Serializable
    private data class Deployment(val deploymentId: String = "", val deploymentConfig: DeploymentConfig? = null)

    @Serializable
    private data class Deployments(val deployments: List<Deployment> = emptyList())

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

    /** Call 9: lists the deployments; the Head deployment is the one without a version number. */
    override suspend fun headDeploymentId(scriptId: String): String {
        val url = projects().addPathSegment(scriptId).addPathSegment("deployments").build()
        val list = GoogleJson.decodeFromString<Deployments>(http.execute(Request.Builder().url(url).get().build()))
        return list.deployments.firstOrNull { it.deploymentConfig?.versionNumber == null }?.deploymentId
            ?: throw ApiError.Unexpected(200, "Project $scriptId has no Head deployment")
    }

    private fun projects(): HttpUrl.Builder = baseUrl.newBuilder().addPathSegments("v1/projects")
}
