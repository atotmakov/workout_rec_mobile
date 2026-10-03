package com.workoutrec.google

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request

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

    @Serializable
    private data class Created(val spreadsheetId: String)

    @Serializable
    private data class SheetProperties(val sheetId: Int = 0, val title: String = "")

    @Serializable
    private data class Sheet(val properties: SheetProperties = SheetProperties())

    @Serializable
    private data class Metadata(val metadataId: Int = 0, val metadataKey: String = "", val metadataValue: String = "")

    @Serializable
    private data class TabsResponse(val sheets: List<Sheet> = emptyList(), val developerMetadata: List<Metadata> = emptyList())

    /** Call 4. */
    override suspend fun create(body: JsonObject): String {
        val request = Request.Builder().url(spreadsheets().build()).post(body.toString().toJsonBody()).build()
        return GoogleJson.decodeFromString<Created>(http.execute(request)).spreadsheetId
    }

    /** Calls 4b and 6. */
    override suspend fun batchUpdate(spreadsheetId: String, requests: JsonArray) {
        val url = spreadsheets().addPathSegment("$spreadsheetId:batchUpdate").build()
        val body = buildJsonObject { put("requests", requests) }.toString().toJsonBody()
        http.execute(Request.Builder().url(url).post(body).build())
    }

    /** Call 5a: no ranges, so a renamed or missing tab can never fail the request. */
    override suspend fun readTabsAndMetadata(spreadsheetId: String): TabsAndMetadata {
        val url = spreadsheets().addPathSegment(spreadsheetId)
            .addQueryParameter("fields", "sheets.properties(sheetId,title),developerMetadata")
            .build()
        val response = GoogleJson.decodeFromString<TabsResponse>(http.execute(Request.Builder().url(url).build()))
        return TabsAndMetadata(
            titles = response.sheets.map { it.properties.title },
            metadata = response.developerMetadata.associate { it.metadataKey to it.metadataValue },
            metadataIds = response.developerMetadata.associate { it.metadataKey to it.metadataId },
        )
    }

    /** Call 5b: only ranges of tabs that exist; skipped entirely when there are none. */
    override suspend fun readStructureCells(spreadsheetId: String, ranges: List<String>): JsonObject {
        if (ranges.isEmpty()) return JsonObject(emptyMap())
        val url = spreadsheets().addPathSegment(spreadsheetId).apply {
            ranges.forEach { addQueryParameter("ranges", it) }
            addQueryParameter(
                "fields",
                "sheets(properties.title,conditionalFormats,data.rowData.values(userEnteredValue,dataValidation))",
            )
        }.build()
        return GoogleJson.parseToJsonElement(http.execute(Request.Builder().url(url).build())) as JsonObject
    }

    /** Call 6: one entry per key; update existing entries, create missing ones. */
    override suspend fun upsertMetadata(spreadsheetId: String, values: Map<String, String>, existingIds: Map<String, Int>) {
        if (values.isEmpty()) return
        val requests = buildJsonArray {
            values.forEach { (key, value) ->
                val id = existingIds[key]
                if (id != null) {
                    add(
                        buildJsonObject {
                            putJsonObject("updateDeveloperMetadata") {
                                putJsonArray("dataFilters") {
                                    add(buildJsonObject { putJsonObject("developerMetadataLookup") { put("metadataId", id) } })
                                }
                                putJsonObject("developerMetadata") { put("metadataValue", value) }
                                put("fields", "metadataValue")
                            }
                        },
                    )
                } else {
                    add(buildJsonObject { putJsonObject("createDeveloperMetadata") { put("developerMetadata", documentMetadata(key, value)) } })
                }
            }
        }
        batchUpdate(spreadsheetId, requests)
    }

    private fun spreadsheets(): HttpUrl.Builder = baseUrl.newBuilder().addPathSegments("v4/spreadsheets")

    companion object {
        /** Spreadsheet-level metadata visible to anyone with access (contracts/spreadsheet.md). */
        fun documentMetadata(key: String, value: String): JsonObject = buildJsonObject {
            put("metadataKey", key)
            put("metadataValue", value)
            putJsonObject("location") { put("spreadsheet", true) }
            put("visibility", "DOCUMENT")
        }
    }
}
