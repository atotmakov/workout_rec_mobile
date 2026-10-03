package com.workoutrec.google

import java.io.IOException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Errors from Google REST calls, per contracts/google-apis.md "Error mapping". */
sealed class ApiError(message: String? = null) : Exception(message) {
    data object Offline : ApiError("Offline")
    data object TokenExpired : ApiError("Token expired")
    data class AppsScriptApiDisabled(val detail: String) : ApiError(detail)
    data class AccessDenied(val detail: String) : ApiError(detail)
    data object NotFound : ApiError("Not found")
    data class ServiceUnavailable(val code: Int) : ApiError("Service unavailable: $code")
    data class Unexpected(val code: Int, val detail: String) : ApiError("HTTP $code: $detail")

    /** Worth offering "Retry" for, or retrying automatically. */
    val isRetryable: Boolean get() = this is Offline || this is ServiceUnavailable
}

object ApiErrorMapper {

    fun fromException(e: IOException): ApiError = ApiError.Offline

    fun fromResponse(code: Int, body: String): ApiError {
        val message = googleMessage(body)
        return when {
            code == 401 -> ApiError.TokenExpired
            code == 403 && isAppsScriptApiDisabled(message) -> ApiError.AppsScriptApiDisabled(message)
            code == 403 -> ApiError.AccessDenied(message)
            code == 404 -> ApiError.NotFound
            code == 429 || code in 500..599 -> ApiError.ServiceUnavailable(code)
            else -> ApiError.Unexpected(code, message)
        }
    }

    private fun isAppsScriptApiDisabled(message: String): Boolean =
        message.contains("Apps Script API", ignoreCase = true) &&
            message.contains("enable", ignoreCase = true)

    /** Google errors look like {"error":{"code":403,"message":"...","status":"..."}}. */
    private fun googleMessage(body: String): String = runCatching {
        val error = GoogleJson.parseToJsonElement(body).jsonObject["error"]
        when (error) {
            is JsonObject -> error["message"]?.jsonPrimitive?.contentOrNull
            else -> error?.jsonPrimitive?.contentOrNull
        }
    }.getOrNull() ?: body
}
