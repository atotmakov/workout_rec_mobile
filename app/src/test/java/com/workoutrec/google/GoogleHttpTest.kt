package com.workoutrec.google

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class GoogleHttpTest {

    private lateinit var server: MockWebServer
    private val tokenCalls = mutableListOf<Boolean>()
    private val sleeps = mutableListOf<Long>()

    private val tokens = TokenProvider { forceRefresh ->
        tokenCalls += forceRefresh
        if (forceRefresh) "fresh" else "cached"
    }

    private fun http() = GoogleHttp(
        client = OkHttpClient(),
        tokens = tokens,
        sleep = { sleeps += it },
    )

    private fun request() = Request.Builder().url(server.url("/v4/spreadsheets/abc")).build()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        runCatching { server.shutdown() }
    }

    @Test
    fun `sends the bearer token and returns the body on 2xx`() = runTest {
        server.enqueue(MockResponse().setBody("""{"ok":true}"""))
        assertEquals("""{"ok":true}""", http().execute(request()))
        assertEquals("Bearer cached", server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun `re-authorizes once on 401 and retries with the new token`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(MockResponse().setBody("done"))
        assertEquals("done", http().execute(request()))
        assertEquals(listOf(false, true), tokenCalls)
        server.takeRequest()
        assertEquals("Bearer fresh", server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun `second 401 gives TokenExpired`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(MockResponse().setResponseCode(401))
        assertFails<ApiError.TokenExpired> { http().execute(request()) }
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `retries 503 with backoff and then succeeds`() = runTest {
        server.enqueue(MockResponse().setResponseCode(503))
        server.enqueue(MockResponse().setResponseCode(429))
        server.enqueue(MockResponse().setBody("ok"))
        assertEquals("ok", http().execute(request()))
        assertEquals(2, sleeps.size)
        assertTrue("backoff grows", sleeps[1] > sleeps[0])
    }

    @Test
    fun `gives up after three retries with ServiceUnavailable`() = runTest {
        repeat(4) { server.enqueue(MockResponse().setResponseCode(500)) }
        assertFails<ApiError.ServiceUnavailable> { http().execute(request()) }
        assertEquals(4, server.requestCount)
        assertEquals(3, sleeps.size)
    }

    @Test
    fun `404 is not retried`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404))
        assertFails<ApiError.NotFound> { http().execute(request()) }
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `connection failure gives Offline`() = runTest {
        server.shutdown()
        assertFails<ApiError.Offline> { http().execute(request()) }
    }

    private suspend inline fun <reified T : ApiError> assertFails(block: suspend () -> Unit) {
        try {
            block()
            fail("expected ${T::class.simpleName}")
        } catch (e: ApiError) {
            assertTrue("expected ${T::class.simpleName} but was $e", e is T)
        }
    }
}
