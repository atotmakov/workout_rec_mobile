package com.workoutrec.google

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

/** Result of call 5a. */
data class TabsAndMetadata(
    val titles: List<String>,
    val metadata: Map<String, String>,
    val metadataIds: Map<String, Int>,
)

/** Sheets calls 4, 4b, 5a, 5b, 6 (contracts/google-apis.md). */
interface SheetsApi {
    suspend fun create(body: JsonObject): String
    suspend fun batchUpdate(spreadsheetId: String, requests: JsonArray)
    suspend fun readTabsAndMetadata(spreadsheetId: String): TabsAndMetadata
    suspend fun readStructureCells(spreadsheetId: String, ranges: List<String>): JsonObject
    suspend fun upsertMetadata(spreadsheetId: String, values: Map<String, String>, existingIds: Map<String, Int>)
}

class SheetsClient(
    private val http: GoogleHttp,
    private val baseUrl: HttpUrl = "https://sheets.googleapis.com/".toHttpUrl(),
) : SheetsApi {
    override suspend fun create(body: JsonObject): String = TODO()
    override suspend fun batchUpdate(spreadsheetId: String, requests: JsonArray): Unit = TODO()
    override suspend fun readTabsAndMetadata(spreadsheetId: String): TabsAndMetadata = TODO()
    override suspend fun readStructureCells(spreadsheetId: String, ranges: List<String>): JsonObject = TODO()
    override suspend fun upsertMetadata(spreadsheetId: String, values: Map<String, String>, existingIds: Map<String, Int>): Unit = TODO()
}
