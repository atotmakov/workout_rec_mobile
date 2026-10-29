package com.workoutrec.google

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

// contracts/google-apis.md calls 7-10
class ScriptClientTest {

    private lateinit var server: MockWebServer
    private lateinit var script: ScriptClient

    @Before
    fun setUp() {
        server = MockWebServer().also { it.start() }
        script = ScriptClient(GoogleHttp(OkHttpClient(), { "tok" }, sleep = {}), baseUrl = server.url("/"))
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `call 7 creates a project attached to the spreadsheet`() = runTest {
        server.enqueue(MockResponse().setBody("""{"scriptId":"s1","title":"workout_rec_automation","parentId":"sheet1"}"""))
        assertEquals("s1", script.createProject(title = "workout_rec_automation", parentId = "sheet1"))
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/v1/projects", request.requestUrl!!.encodedPath)
        val body = GoogleJson.parseToJsonElement(request.body.readUtf8()).jsonObject
        assertEquals("workout_rec_automation", body["title"]!!.jsonPrimitive.content)
        assertEquals("sheet1", body["parentId"]!!.jsonPrimitive.content)
    }

    @Test
    fun `call 7 with the Apps Script API off gives AppsScriptApiDisabled`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(403).setBody(
                """{"error":{"code":403,"message":"User has not enabled the Apps Script API. Enable it by visiting https://script.google.com/home/usersettings then retry.","status":"PERMISSION_DENIED"}}""",
            ),
        )
        try {
            script.createProject("workout_rec_automation", "sheet1")
            fail("expected AppsScriptApiDisabled")
        } catch (e: ApiError.AppsScriptApiDisabled) {
            // expected
        }
    }

    @Test
    fun `call 8 uploads files with their types`() = runTest {
        server.enqueue(MockResponse().setBody("""{"scriptId":"s1","files":[]}"""))
        script.updateContent(
            "s1",
            listOf(
                ScriptFile("appsscript", ScriptFileType.JSON, "{}"),
                ScriptFile("Code", ScriptFileType.SERVER_JS, "function f(){}"),
                ScriptFile("Enable", ScriptFileType.HTML, "<p></p>"),
            ),
        )
        val request = server.takeRequest()
        assertEquals("PUT", request.method)
        assertEquals("/v1/projects/s1/content", request.requestUrl!!.encodedPath)
        val files = GoogleJson.parseToJsonElement(request.body.readUtf8()).jsonObject["files"]!!.jsonArray
        assertEquals(
            listOf("appsscript:JSON", "Code:SERVER_JS", "Enable:HTML"),
            files.map { "${it.jsonObject["name"]!!.jsonPrimitive.content}:${it.jsonObject["type"]!!.jsonPrimitive.content}" },
        )
        assertEquals("function f(){}", files[1].jsonObject["source"]!!.jsonPrimitive.content)
    }

    @Test
    fun `call 9 finds the Head deployment among the deployments`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"deployments":[
                     {"deploymentId":"v1","deploymentConfig":{"versionNumber":1,"description":"enable"}},
                     {"deploymentId":"head","deploymentConfig":{"description":"Head deployment"}}]}""",
            ),
        )
        assertEquals("head", script.headDeploymentId("s1"))
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/v1/projects/s1/deployments", request.requestUrl!!.encodedPath)
    }

    @Test
    fun `call 9 without a Head deployment is an error`() = runTest {
        server.enqueue(MockResponse().setBody("""{"deployments":[{"deploymentId":"v1","deploymentConfig":{"versionNumber":1}}]}"""))
        try {
            script.headDeploymentId("s1")
            fail("expected an error")
        } catch (e: ApiError.Unexpected) {
            // expected
        }
    }
}
