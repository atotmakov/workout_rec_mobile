package com.workoutrec.spreadsheet

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// contracts/spreadsheet.md
class ReferenceStructureTest {

    private val request = ReferenceStructure.createRequest(timeZone = "Europe/Moscow")
    private val sheets = request["sheets"]!!.jsonArray.map { it.jsonObject }

    private fun sheet(title: String): JsonObject =
        sheets.first { it["properties"]!!.jsonObject["title"]!!.jsonPrimitive.content == title }

    private fun rows(title: String) =
        sheet(title)["data"]!!.jsonArray[0].jsonObject["rowData"]!!.jsonArray.map { row ->
            row.jsonObject["values"]?.jsonArray?.map { it.jsonObject } ?: emptyList()
        }

    private fun JsonObject.userValue(kind: String): String? =
        this["userEnteredValue"]?.jsonObject?.get(kind)?.jsonPrimitive?.content

    @Test
    fun `spreadsheet properties`() {
        val props = request["properties"]!!.jsonObject
        assertEquals("workout_rec_database_", props["title"]!!.jsonPrimitive.content)
        assertEquals("ru_RU", props["locale"]!!.jsonPrimitive.content)
        assertEquals("Europe/Moscow", props["timeZone"]!!.jsonPrimitive.content)
    }

    @Test
    fun `six tabs in order`() {
        assertEquals(
            listOf("log", "drills", "rec", "money", "workout", "balance"),
            sheets.map { it["properties"]!!.jsonObject["title"]!!.jsonPrimitive.content },
        )
    }

    @Test
    fun `header rows`() {
        fun header(tab: String) = rows(tab)[0].map { it.userValue("stringValue") }
        assertEquals(listOf("Date", "Drill", "W", "R"), header("log"))
        assertEquals(listOf("mscl", "drill"), header("drills"))
        assertEquals(listOf("date", "workouts", "sum"), header("money"))
        assertEquals(listOf("date", "duration, min", "work alone"), header("workout"))
    }

    @Test
    fun `rec cells - drop-down, note and formula`() {
        val rec = rows("rec")
        val validation = rec[0][0]["dataValidation"]!!.jsonObject
        val condition = validation["condition"]!!.jsonObject
        assertEquals("ONE_OF_RANGE", condition["type"]!!.jsonPrimitive.content)
        assertEquals("=drills!\$B:\$B", condition["values"]!!.jsonArray[0].jsonObject["userEnteredValue"]!!.jsonPrimitive.content)
        assertEquals("false", validation["strict"]!!.jsonPrimitive.content)
        assertEquals("true", validation["showCustomUi"]!!.jsonPrimitive.content)
        assertEquals("rec!A1 starts empty", null, rec[0][0]["userEnteredValue"])
        assertEquals("FILTER(log!A:Z, log!B:B=A1)", rec[0][3].userValue("stringValue"))
        assertEquals("=FILTER(log!A:Z, log!B:B=A1)", rec[1][0].userValue("formulaValue"))
    }

    @Test
    fun `balance A1 is 0`() {
        assertEquals("0", rows("balance")[0][0].userValue("numberValue")?.removeSuffix(".0"))
    }

    @Test
    fun `date columns use dd_MM_yyyy for all data rows`() {
        for (tab in listOf("log", "money", "workout")) {
            val data = rows(tab).drop(1)
            assertEquals("$tab rows 2..1000", 999, data.size)
            for (row in listOf(data.first(), data.last())) {
                val format = row[0]["userEnteredFormat"]!!.jsonObject["numberFormat"]!!.jsonObject
                assertEquals("DATE", format["type"]!!.jsonPrimitive.content)
                assertEquals("dd.MM.yyyy", format["pattern"]!!.jsonPrimitive.content)
            }
        }
    }

    @Test
    fun `two rec conditional rules with the light green background`() {
        val rules = sheet("rec")["conditionalFormats"]!!.jsonArray.map { it.jsonObject }
        assertEquals(2, rules.size)
        val formulas = rules.map {
            it["booleanRule"]!!.jsonObject["condition"]!!.jsonObject["values"]!!.jsonArray[0].jsonObject["userEnteredValue"]!!.jsonPrimitive.content
        }
        assertEquals(
            listOf("=C2=MAX(\$C\$2:\$C)", "=AND(\$C2=MAX(\$C:\$C), \$D2=MAXIFS(\$D:\$D, \$C:\$C, MAX(\$C:\$C)))"),
            formulas,
        )
        val ranges = rules.map { it["ranges"]!!.jsonArray[0].jsonObject }
        assertEquals(listOf(2, 3), ranges.map { it["startColumnIndex"]!!.jsonPrimitive.content.toInt() })
        assertEquals(listOf(1, 1), ranges.map { it["startRowIndex"]!!.jsonPrimitive.content.toInt() })
        assertEquals(listOf(1000, 1000), ranges.map { it["endRowIndex"]!!.jsonPrimitive.content.toInt() })
        val color = rules[0]["booleanRule"]!!.jsonObject["format"]!!.jsonObject["backgroundColor"]!!.jsonObject
        // #B7E1CD = (183, 225, 205)
        assertEquals(183.0 / 255, color["red"]!!.jsonPrimitive.content.toDouble(), 0.002)
        assertEquals(225.0 / 255, color["green"]!!.jsonPrimitive.content.toDouble(), 0.002)
        assertEquals(205.0 / 255, color["blue"]!!.jsonPrimitive.content.toDouble(), 0.002)
    }

    @Test
    fun `structure version metadata`() {
        val meta = request["developerMetadata"]!!.jsonArray.single().jsonObject
        assertEquals("workout_rec.structure_version", meta["metadataKey"]!!.jsonPrimitive.content)
        assertEquals("1", meta["metadataValue"]!!.jsonPrimitive.content)
        assertEquals("DOCUMENT", meta["visibility"]!!.jsonPrimitive.content)
    }

    @Test
    fun `fallback produces the same content as minimal create plus one batchUpdate`() {
        val minimal = ReferenceStructure.minimalCreateRequest("Europe/Moscow")
        val minimalSheets = minimal["sheets"]!!.jsonArray.map { it.jsonObject }
        assertEquals(6, minimalSheets.size)
        assertTrue(minimalSheets.none { it.containsKey("conditionalFormats") })
        val kinds = ReferenceStructure.completionRequests().map { it.jsonObject.keys.single() }.toSet()
        assertEquals(
            setOf("updateCells", "setDataValidation", "addConditionalFormatRule", "repeatCell", "createDeveloperMetadata"),
            kinds,
        )
    }
}
