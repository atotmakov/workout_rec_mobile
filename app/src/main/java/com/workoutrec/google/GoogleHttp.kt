package com.workoutrec.google

import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody

val GoogleJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
}

private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

fun String.toJsonBody(): RequestBody = toRequestBody(JSON_MEDIA_TYPE)

/** Supplies OAuth access tokens for the selected account (research R3). */
fun interface TokenProvider {
    /** @param forceRefresh true after a 401, to get a new token instead of a cached one. */
    suspend fun accessToken(forceRefresh: Boolean): String
}

/**
 * Executes Google REST calls: adds the bearer token, re-authorizes once on 401, retries
 * 429/5xx up to [maxRetries] times with backoff, and maps failures to [ApiError].
 */
class GoogleHttp(
    private val client: OkHttpClient,
    private val tokens: TokenProvider,
    private val maxRetries: Int = 3,
    private val backoffMillis: (attempt: Int) -> Long = { attempt -> 500L shl attempt },
    private val sleep: suspend (Long) -> Unit = { delay(it) },
) {

    /** Returns the response body on 2xx; throws [ApiError] otherwise. */
    suspend fun execute(request: Request): String {
        var refreshedToken = false
        var retries = 0
        while (true) {
            val token = tokens.accessToken(forceRefresh = refreshedToken)
            val authorized = request.newBuilder().header("Authorization", "Bearer $token").build()
            val (code, body) = try {
                withContext(Dispatchers.IO) {
                    client.newCall(authorized).execute().use { it.code to (it.body?.string() ?: "") }
                }
            } catch (e: IOException) {
                throw ApiErrorMapper.fromException(e)
            }
            if (code in 200..299) return body
            when (val error = ApiErrorMapper.fromResponse(code, body)) {
                is ApiError.TokenExpired -> {
                    if (refreshedToken) throw error
                    refreshedToken = true
                }
                is ApiError.ServiceUnavailable -> {
                    if (retries >= maxRetries) throw error
                    sleep(backoffMillis(retries))
                    retries++
                }
                else -> throw error
            }
        }
    }
}
