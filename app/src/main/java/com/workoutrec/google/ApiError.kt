package com.workoutrec.google

import java.io.IOException

sealed class ApiError(message: String? = null) : Exception(message) {
    data object Offline : ApiError("Offline")
    data object TokenExpired : ApiError("Token expired")
    data class AppsScriptApiDisabled(val detail: String) : ApiError(detail)
    data class AccessDenied(val detail: String) : ApiError(detail)
    data object NotFound : ApiError("Not found")
    data class ServiceUnavailable(val code: Int) : ApiError("Service unavailable: $code")
    data class Unexpected(val code: Int, val detail: String) : ApiError("HTTP $code: $detail")

    val isRetryable: Boolean get() = TODO()
}

object ApiErrorMapper {
    fun fromException(e: IOException): ApiError = TODO()
    fun fromResponse(code: Int, body: String): ApiError = TODO()
}
