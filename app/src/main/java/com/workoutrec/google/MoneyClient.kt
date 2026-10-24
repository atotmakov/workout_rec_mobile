package com.workoutrec.google

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request

/** Call 23 result: money rows with a date, the balance, and the spreadsheet's metadata. */
data class MoneyRead(
    val payments: List<SheetMoneyRow>,
    val balance: Double?,
    val metadata: Map<String, String>,
    /** Feature 007 analysis C1: `balance!A1` shows a formula error (a tab it needs was renamed). */
    val balanceError: Boolean = false,
)

/** Feature 004 contracts/sheets.md call 23. */
interface MoneyApi {
    /** [hasMoney], [hasBalance]: whether call 20 found those tabs; a missing one is [MissingTabException]. */
    suspend fun readMoney(spreadsheetId: String, hasMoney: Boolean, hasBalance: Boolean): MoneyRead
}

class MoneyClient(
    private val http: GoogleHttp,
    private val baseUrl: HttpUrl = "https://sheets.googleapis.com/".toHttpUrl(),
) : MoneyApi {

    @Serializable
    private data class Cell(val effectiveValue: JsonObject? = null)

    @Serializable
    private data class RowData(val values: List<Cell> = emptyList())

    @Serializable
    private data class GridData(val rowData: List<RowData> = emptyList())

    @Serializable
    private data class Properties(val title: String = "")

    @Serializable
    private data class Sheet(val properties: Properties = Properties(), val data: List<GridData> = emptyList())

    @Serializable
    private data class Metadata(val metadataKey: String = "", val metadataValue: String = "")

    @Serializable
    private data class Response(val developerMetadata: List<Metadata> = emptyList(), val sheets: List<Sheet> = emptyList())

    override suspend fun readMoney(spreadsheetId: String, hasMoney: Boolean, hasBalance: Boolean): MoneyRead {
        // A range naming a missing tab fails the whole request, so ask only for tabs that exist.
        if (!hasMoney) throw MissingTabException(MONEY)
        if (!hasBalance) throw MissingTabException(BALANCE)
        val url = baseUrl.newBuilder().addPathSegments("v4/spreadsheets").addPathSegment(spreadsheetId)
            .addQueryParameter("ranges", "$MONEY!A2:C")
            .addQueryParameter("ranges", "$BALANCE!A1")
            .addQueryParameter("includeGridData", "true")
            .addQueryParameter("fields", "developerMetadata(metadataKey,metadataValue),sheets(properties(title),data(rowData(values(effectiveValue))))")
            .build()
        val response = GoogleJson.decodeFromString<Response>(http.execute(Request.Builder().url(url).build()))
        val rows = { title: String -> response.sheets.firstOrNull { it.properties.title == title }?.data?.firstOrNull()?.rowData.orEmpty() }
        val payments = rows(MONEY).mapIndexedNotNull { i, row ->
            val date = row.values.number(0) ?: return@mapIndexedNotNull null
            SheetMoneyRow(i + 1, date, row.values.number(1), row.values.number(2))
        }
        return MoneyRead(
            payments = payments,
            balance = rows(BALANCE).firstOrNull()?.values?.number(0),
            metadata = response.developerMetadata.associate { it.metadataKey to it.metadataValue },
            balanceError = rows(BALANCE).firstOrNull()?.values?.firstOrNull()?.effectiveValue?.containsKey("errorValue") == true,
        )
    }

    private fun List<Cell>.number(i: Int): Double? = (getOrNull(i)?.effectiveValue?.get("numberValue") as? JsonPrimitive)?.doubleOrNull

    private companion object {
        const val MONEY = "money"
        const val BALANCE = "balance"
    }
}
