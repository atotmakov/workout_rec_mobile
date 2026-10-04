package com.workoutrec.spreadsheet

import com.workoutrec.google.SheetsClient
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/** The reference structure from contracts/spreadsheet.md; the single source for its values. */
object ReferenceStructure {
    const val TITLE = "workout_rec_database_"
    const val LOCALE = "ru_RU"
    const val STRUCTURE_VERSION = "2"
    const val STRUCTURE_VERSION_KEY = "workout_rec.structure_version"
    val TABS = listOf("log", "drills", "rec", "money", "workout", "balance")
    val HEADERS: Map<String, List<String>> = mapOf(
        "log" to listOf("Date", "Drill", "W", "R"),
        "drills" to listOf("mscl", "drill"),
        "money" to listOf("date", "workouts", "sum"),
        "workout" to listOf("date", "duration, min", "work alone"),
    )
    const val REC_FORMULA = "=FILTER(log!A:Z, log!B:B=A1)"
    const val REC_NOTE = "FILTER(log!A:Z, log!B:B=A1)"
    const val REC_DROPDOWN_RANGE = "=drills!\$B:\$B"
    val REC_RULES = listOf(
        ConditionalRule("C2:C1000", "=C2=MAX(\$C\$2:\$C)"),
        ConditionalRule("D2:D1000", "=AND(\$C2=MAX(\$C:\$C), \$D2=MAXIFS(\$D:\$D, \$C:\$C, MAX(\$C:\$C)))"),
    )
    const val DATE_PATTERN = "dd.MM.yyyy"
    val DATE_COLUMN_TABS = listOf("log", "money", "workout")

    /** Drop-down of `log` column B (Drill): the exercises listed in `drills`. */
    const val LOG_DROPDOWN_RANGE = "=drills!\$B\$2:\$B"

    const val UNSET = "COLUMN_TYPE_UNSPECIFIED"

    /** A Google Sheets table on [tab]: column types by position, header colour (RGB). */
    data class TableSpec(val tab: String, val name: String, val columnTypes: List<String>, val headerColor: Triple<Int, Int, Int>)

    /** contracts/spreadsheet.md "Tables". */
    val TABLES = listOf(
        TableSpec("log", "log", listOf("DATE", UNSET, "DOUBLE", "DOUBLE"), Triple(0x2E, 0x5E, 0x4E)),
        TableSpec("drills", "drills", listOf(UNSET, UNSET), Triple(0x5C, 0x6B, 0xC0)),
        TableSpec("money", "payments", listOf("DATE", "DOUBLE", "DOUBLE"), Triple(0xF6, 0xB0, 0x26)),
        TableSpec("workout", "workouts", listOf("DATE", UNSET, UNSET), Triple(0x2E, 0x7D, 0x5B)),
    )

    /** Default grid size of a new sheet; date format and highlight rules cover rows 2..1000. */
    private const val ROWS = 1000

    /** #B7E1CD */
    private val HIGHLIGHT = Triple(183, 225, 205)

    /** Call 5b ranges per tab, in request order. */
    val STRUCTURE_RANGES: Map<String, String> = linkedMapOf(
        "log" to "log!1:2",
        "drills" to "drills!1:1",
        "money" to "money!1:1",
        "workout" to "workout!1:1",
        "rec" to "rec!A1:D2",
    )

    private fun sheetId(tab: String) = TABS.indexOf(tab)

    /** Call 4: the whole structure in one all-or-nothing create (research R5). */
    fun createRequest(timeZone: String): JsonObject = buildJsonObject {
        put("properties", properties(timeZone))
        putJsonArray("sheets") {
            TABS.forEach { tab ->
                add(
                    buildJsonObject {
                        put("properties", sheetProperties(tab))
                        putJsonArray("data") { add(gridData(fullRows(tab))) }
                        if (tab == "rec") {
                            putJsonArray("conditionalFormats") { REC_RULES.forEach { add(conditionalRule(it)) } }
                        }
                    },
                )
            }
        }
        putJsonArray("developerMetadata") { add(SheetsClient.documentMetadata(STRUCTURE_VERSION_KEY, STRUCTURE_VERSION)) }
    }

    /** Fallback call 4: tabs and headers only. */
    fun minimalCreateRequest(timeZone: String): JsonObject = buildJsonObject {
        put("properties", properties(timeZone))
        putJsonArray("sheets") {
            TABS.forEach { tab ->
                add(
                    buildJsonObject {
                        put("properties", sheetProperties(tab))
                        HEADERS[tab]?.let { headers -> putJsonArray("data") { add(gridData(listOf(row(headers.map(::stringCell))))) } }
                    },
                )
            }
        }
    }

    /** Fallback call 4b: everything [minimalCreateRequest] leaves out, in one batchUpdate. */
    fun completionRequests(): JsonArray = buildJsonArray {
        val rec = sheetId("rec")
        add(updateCells(rec, rowIndex = 0, columnIndex = 3, cell = stringCell(REC_NOTE)))
        add(updateCells(rec, rowIndex = 1, columnIndex = 0, cell = formulaCell(REC_FORMULA)))
        add(updateCells(sheetId("balance"), rowIndex = 0, columnIndex = 0, cell = numberCell(0)))
        add(
            buildJsonObject {
                putJsonObject("setDataValidation") {
                    put("range", gridRange(rec, 0, 1, 0, 1))
                    put("rule", dropDownRule())
                }
            },
        )
        REC_RULES.forEachIndexed { index, rule ->
            add(
                buildJsonObject {
                    putJsonObject("addConditionalFormatRule") {
                        put("rule", conditionalRule(rule))
                        put("index", index)
                    }
                },
            )
        }
        DATE_COLUMN_TABS.forEach { tab ->
            add(
                buildJsonObject {
                    putJsonObject("repeatCell") {
                        put("range", gridRange(sheetId(tab), 1, ROWS, 0, 1))
                        put("cell", dateFormatCell())
                        put("fields", "userEnteredFormat.numberFormat")
                    }
                },
            )
        }
        add(
            buildJsonObject {
                putJsonObject("setDataValidation") {
                    put("range", gridRange(sheetId("log"), 1, ROWS, 1, 2))
                    put("rule", dropDownRule(LOG_DROPDOWN_RANGE))
                }
            },
        )
        addTableRequests().forEach { add(it) }
        add(
            buildJsonObject {
                putJsonObject("createDeveloperMetadata") {
                    put("developerMetadata", SheetsClient.documentMetadata(STRUCTURE_VERSION_KEY, STRUCTURE_VERSION))
                }
            },
        )
    }

    /**
     * `addTable` requests for the tables named in [names]. Tables are always added this way:
     * spreadsheets.create silently ignores `Sheet.tables` (found on the device test, 2026-10-04).
     */
    fun addTableRequests(names: Collection<String> = TABLES.map { it.name }): JsonArray = buildJsonArray {
        TABLES.filter { it.name in names }.forEach { spec ->
            add(buildJsonObject { putJsonObject("addTable") { put("table", table(spec)) } })
        }
    }

    /** A table covering rows 1..1000 from A1; column names are the header row (research R5). */
    private fun table(spec: TableSpec) = buildJsonObject {
        put("name", spec.name)
        put("range", gridRange(sheetId(spec.tab), 0, ROWS, 0, spec.columnTypes.size))
        putJsonObject("rowsProperties") {
            putJsonObject("headerColorStyle") { put("rgbColor", rgb(spec.headerColor)) }
        }
        putJsonArray("columnProperties") {
            val headers = HEADERS.getValue(spec.tab)
            spec.columnTypes.forEachIndexed { index, type ->
                add(
                    buildJsonObject {
                        put("columnIndex", index)
                        put("columnName", headers[index])
                        put("columnType", type)
                    },
                )
            }
        }
    }

    private fun rgb(color: Triple<Int, Int, Int>) = buildJsonObject {
        put("red", color.first / 255.0)
        put("green", color.second / 255.0)
        put("blue", color.third / 255.0)
    }

    private fun properties(timeZone: String) = buildJsonObject {
        put("title", TITLE)
        put("locale", LOCALE)
        put("timeZone", timeZone)
    }

    private fun sheetProperties(tab: String) = buildJsonObject {
        put("sheetId", sheetId(tab))
        put("title", tab)
        put("index", sheetId(tab))
    }

    /** Row data for [createRequest]: headers, fixed cells, and date formats on rows 2..1000. */
    private fun fullRows(tab: String): List<JsonObject> = when (tab) {
        "rec" -> listOf(
            row(listOf(buildJsonObject { put("dataValidation", dropDownRule()) }, JsonObject(emptyMap()), JsonObject(emptyMap()), stringCell(REC_NOTE))),
            row(listOf(formulaCell(REC_FORMULA))),
        )
        "balance" -> listOf(row(listOf(numberCell(0))))
        // log rows 2..1000: date format in A and the exercise drop-down in B (contracts "Tabs").
        "log" -> listOf(row(HEADERS.getValue(tab).map(::stringCell))) +
            List(ROWS - 1) { row(listOf(dateFormatCell(), buildJsonObject { put("dataValidation", dropDownRule(LOG_DROPDOWN_RANGE)) })) }
        else -> {
            val header = row(HEADERS.getValue(tab).map(::stringCell))
            if (tab in DATE_COLUMN_TABS) listOf(header) + List(ROWS - 1) { row(listOf(dateFormatCell())) } else listOf(header)
        }
    }

    private fun gridData(rows: List<JsonObject>) = buildJsonObject {
        put("startRow", 0)
        put("startColumn", 0)
        put("rowData", JsonArray(rows))
    }

    private fun row(cells: List<JsonElement>) = buildJsonObject { put("values", JsonArray(cells)) }

    private fun stringCell(text: String) = buildJsonObject { putJsonObject("userEnteredValue") { put("stringValue", text) } }

    private fun formulaCell(formula: String) = buildJsonObject { putJsonObject("userEnteredValue") { put("formulaValue", formula) } }

    private fun numberCell(number: Int) = buildJsonObject { putJsonObject("userEnteredValue") { put("numberValue", number) } }

    private fun dateFormatCell() = buildJsonObject {
        putJsonObject("userEnteredFormat") {
            putJsonObject("numberFormat") {
                put("type", "DATE")
                put("pattern", DATE_PATTERN)
            }
        }
    }

    private fun dropDownRule(range: String = REC_DROPDOWN_RANGE) = buildJsonObject {
        putJsonObject("condition") {
            put("type", "ONE_OF_RANGE")
            putJsonArray("values") { add(buildJsonObject { put("userEnteredValue", range) }) }
        }
        put("strict", false)
        put("showCustomUi", true)
    }

    private fun conditionalRule(rule: ConditionalRule) = buildJsonObject {
        val (startRow, endRow, startCol, endCol) = A1Range.parse(rule.range)
        putJsonArray("ranges") { add(gridRange(sheetId("rec"), startRow, endRow, startCol, endCol)) }
        putJsonObject("booleanRule") {
            putJsonObject("condition") {
                put("type", "CUSTOM_FORMULA")
                putJsonArray("values") { add(buildJsonObject { put("userEnteredValue", rule.formula) }) }
            }
            putJsonObject("format") {
                putJsonObject("backgroundColor") {
                    put("red", HIGHLIGHT.first / 255.0)
                    put("green", HIGHLIGHT.second / 255.0)
                    put("blue", HIGHLIGHT.third / 255.0)
                }
            }
        }
    }

    private fun gridRange(sheetId: Int, startRow: Int, endRow: Int, startCol: Int, endCol: Int) = buildJsonObject {
        put("sheetId", sheetId)
        put("startRowIndex", startRow)
        put("endRowIndex", endRow)
        put("startColumnIndex", startCol)
        put("endColumnIndex", endCol)
    }

    private fun updateCells(sheetId: Int, rowIndex: Int, columnIndex: Int, cell: JsonObject) = buildJsonObject {
        putJsonObject("updateCells") {
            putJsonObject("start") {
                put("sheetId", sheetId)
                put("rowIndex", rowIndex)
                put("columnIndex", columnIndex)
            }
            putJsonArray("rows") { add(row(listOf(cell))) }
            put("fields", "userEnteredValue")
        }
    }
}

/** Conversions between A1 ranges like "C2:C1000" and 0-based, end-exclusive grid indexes. */
object A1Range {
    data class Grid(val startRow: Int, val endRow: Int, val startCol: Int, val endCol: Int)

    fun parse(range: String): Grid {
        val (from, to) = range.split(":")
        val (fromCol, fromRow) = cell(from)
        val (toCol, toRow) = cell(to)
        return Grid(fromRow - 1, toRow, fromCol, toCol + 1)
    }

    fun format(grid: Grid): String =
        "${column(grid.startCol)}${grid.startRow + 1}:${column(grid.endCol - 1)}${grid.endRow}"

    private fun cell(ref: String): Pair<Int, Int> {
        val letters = ref.takeWhile { it.isLetter() }
        val col = letters.fold(0) { acc, c -> acc * 26 + (c.uppercaseChar() - 'A' + 1) } - 1
        return col to ref.drop(letters.length).toInt()
    }

    private fun column(index: Int): String {
        var n = index + 1
        val sb = StringBuilder()
        while (n > 0) {
            val rem = (n - 1) % 26
            sb.insert(0, 'A' + rem)
            n = (n - 1) / 26
        }
        return sb.toString()
    }
}
