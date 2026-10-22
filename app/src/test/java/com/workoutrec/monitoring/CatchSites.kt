package com.workoutrec.monitoring

import java.io.File

/** One place in the source that catches errors (feature 005 research R7). */
data class CatchSite(val path: String, val line: Int, val type: String, val kind: Kind) {
    enum class Kind { REPORTS, RETHROWS, UNREPORTED }
}

/**
 * Finds `catch (e: Type) { … }`, `runCatching`, `Flow.catch { … }` and `CoroutineExceptionHandler`
 * in Kotlin sources, ignoring comments and string contents, and checks them against the reviewed
 * normal-outcome list (`path | caught type | count | reason`).
 */
object CatchSites {

    private val catchBlock = Regex("""\bcatch\s*\(\s*\w+\s*:\s*([\w.]+)\s*\)\s*\{""")
    private val runCatching = Regex("""\brunCatching\b""")
    private val flowCatch = Regex("""\.catch\s*\{""")
    private val handler = Regex("""\bCoroutineExceptionHandler\b""")
    private val reportCalls = listOf("nonFatal(", ".failure(", "onBadValue(", "badValue(")

    fun scan(root: File, extension: String = ".kt"): List<CatchSite> =
        root.walkTopDown()
            .filter { it.isFile && it.name.endsWith(extension) }
            .sortedBy { it.relativeTo(root).invariantSeparatorsPath }
            .flatMap { file -> scanFile(file.relativeTo(root).invariantSeparatorsPath, file.readText()) }
            .toList()

    /** Problems to fix; empty when every site reports, passes the error on, or is listed. */
    fun check(root: File, list: File, extension: String = ".kt"): List<String> {
        val unreported = scan(root, extension).filter { it.kind == CatchSite.Kind.UNREPORTED }
        val entries = parseList(list)
        val listed = entries.map { it.path to it.type }.toSet()
        val problems = unreported
            .filter { (it.path to it.type) !in listed }
            .map { "${it.path}:${it.line} ${it.type}: not reported and not in the normal-outcome list" }
            .toMutableList()
        entries.forEach { entry ->
            val found = unreported.count { it.path == entry.path && it.type == entry.type }
            if (found != entry.count) {
                problems += "normal-outcomes: ${entry.path} | ${entry.type} lists ${sites(entry.count)}, found $found"
            }
            if (entry.reason.isBlank()) problems += "normal-outcomes line ${entry.line}: a reason is required"
        }
        return problems
    }

    private data class Entry(val line: Int, val path: String, val type: String, val count: Int, val reason: String)

    private fun sites(count: Int) = if (count == 1) "1 site" else "$count sites"

    private fun parseList(list: File): List<Entry> = list.readLines().mapIndexedNotNull { index, raw ->
        val text = raw.trim()
        if (text.isEmpty() || text.startsWith("#")) return@mapIndexedNotNull null
        val parts = text.split("|").map { it.trim() }
        require(parts.size in 3..4) { "normal-outcomes line ${index + 1}: expected path | caught type | count | reason" }
        Entry(index + 1, parts[0], parts[1], parts[2].toInt(), parts.getOrElse(3) { "" })
    }

    private fun scanFile(path: String, source: String): List<CatchSite> {
        val code = codeOnly(source)
        val lineOf = { offset: Int -> source.substring(0, offset).count { it == '\n' } + 1 }
        val sites = mutableListOf<Pair<Int, CatchSite>>()
        catchBlock.findAll(code).forEach { match ->
            val body = block(code, match.range.last)
            sites += match.range.first to CatchSite(path, lineOf(match.range.first), match.groupValues[1], classify(body, rethrowCounts = true))
        }
        runCatching.findAll(code).forEach {
            sites += it.range.first to CatchSite(path, lineOf(it.range.first), "runCatching", CatchSite.Kind.UNREPORTED)
        }
        flowCatch.findAll(code).forEach { match ->
            val body = block(code, match.range.last)
            sites += match.range.first to CatchSite(path, lineOf(match.range.first), "Flow.catch", classify(body, rethrowCounts = false))
        }
        handler.findAll(code).forEach { match ->
            val open = code.indexOf('{', match.range.last)
            val body = if (open < 0) "" else block(code, open)
            sites += match.range.first to CatchSite(path, lineOf(match.range.first), "CoroutineExceptionHandler", classify(body, rethrowCounts = false))
        }
        return sites.sortedBy { it.first }.map { it.second }
    }

    private fun classify(body: String, rethrowCounts: Boolean): CatchSite.Kind = when {
        reportCalls.any { it in body } -> CatchSite.Kind.REPORTS
        rethrowCounts && lastStatement(body).startsWith("throw ") -> CatchSite.Kind.RETHROWS
        else -> CatchSite.Kind.UNREPORTED
    }

    private fun lastStatement(body: String): String =
        body.split('\n', ';').map { it.trim() }.lastOrNull { it.isNotEmpty() }.orEmpty()

    /** The text between the `{` at [open] and its matching `}` in comment- and string-free [code]. */
    private fun block(code: String, open: Int): String {
        var depth = 0
        for (i in open until code.length) {
            when (code[i]) {
                '{' -> depth++
                '}' -> if (--depth == 0) return code.substring(open + 1, i)
            }
        }
        return code.substring(open + 1)
    }

    /**
     * [source] with comments, string and char contents replaced by spaces (newlines kept), so
     * offsets and lines stay the same. String templates (`${…}`) are blanked with their string.
     */
    internal fun codeOnly(source: String): String {
        val out = StringBuilder(source)
        fun blank(from: Int, to: Int) {
            for (j in from until minOf(to, source.length)) if (out[j] != '\n') out[j] = ' '
        }
        var i = 0
        while (i < source.length) {
            when {
                source.startsWith("//", i) -> {
                    val end = source.indexOf('\n', i).let { if (it < 0) source.length else it }
                    blank(i, end)
                    i = end
                }
                source.startsWith("/*", i) -> {
                    val end = source.indexOf("*/", i + 2).let { if (it < 0) source.length else it + 2 }
                    blank(i, end)
                    i = end
                }
                source[i] == '"' -> {
                    val end = stringEnd(source, i)
                    blank(i + 1, end - 1)
                    i = end
                }
                source[i] == '\'' -> {
                    val end = charEnd(source, i)
                    blank(i + 1, end - 1)
                    i = end
                }
                else -> i++
            }
        }
        return out.toString()
    }

    /** Index just after the string literal that starts at [start] (plain or raw, with templates). */
    private fun stringEnd(source: String, start: Int): Int {
        val raw = source.startsWith("\"\"\"", start)
        var i = start + if (raw) 3 else 1
        while (i < source.length) {
            when {
                raw && source.startsWith("\"\"\"", i) -> return i + 3
                !raw && source[i] == '\\' -> i += 2
                !raw && source[i] == '"' -> return i + 1
                source.startsWith("\${", i) -> i = templateEnd(source, i + 2)
                else -> i++
            }
        }
        return source.length
    }

    /** Index just after the `}` closing a template whose code starts at [start]. */
    private fun templateEnd(source: String, start: Int): Int {
        var depth = 1
        var i = start
        while (i < source.length) {
            when (source[i]) {
                '"' -> {
                    i = stringEnd(source, i)
                    continue
                }
                '\'' -> {
                    i = charEnd(source, i)
                    continue
                }
                '{' -> depth++
                '}' -> if (--depth == 0) return i + 1
            }
            i++
        }
        return source.length
    }

    private fun charEnd(source: String, start: Int): Int {
        var i = start + 1
        while (i < source.length) {
            when (source[i]) {
                '\\' -> i += 2
                '\'' -> return i + 1
                else -> i++
            }
        }
        return source.length
    }
}
