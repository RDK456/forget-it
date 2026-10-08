package app.forgetit.domain

/** Minimal RFC 4180 reader and writer plus spreadsheet formula-injection guarding. */
object Csv {
    private const val DANGEROUS = "=+-@\t\r"
    private val TICK = 39.toChar()

    fun escape(cell: String): String =
        if (cell.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + cell.replace("\"", "\"\"") + "\"" else cell

    fun row(cells: List<String>): String = cells.joinToString(",") { escape(it) }

    /** Free text starting with a formula character gets a leading tick so spreadsheets keep it as text. */
    fun guard(s: String): String = if (s.isNotEmpty() && s[0] in DANGEROUS) "$TICK$s" else s

    fun unguard(s: String): String = if (s.length >= 2 && s[0] == TICK && s[1] in DANGEROUS) s.substring(1) else s

    /** Parses text into rows of cells. Handles a BOM, CRLF or LF, quoted cells with commas, quotes and newlines. */
    fun parse(input: String): List<List<String>> {
        val text = input.removePrefix("﻿")
        val rows = mutableListOf<List<String>>()
        var cells = mutableListOf<String>()
        val cell = StringBuilder()
        var quoted = false
        var started = false
        var i = 0
        while (i < text.length) {
            val ch = text[i]
            if (quoted) {
                if (ch == '"') {
                    if (i + 1 < text.length && text[i + 1] == '"') { cell.append('"'); i++ } else quoted = false
                } else cell.append(ch)
            } else when (ch) {
                '"' -> { quoted = true; started = true }
                ',' -> { cells.add(cell.toString()); cell.clear(); started = true }
                '\r' -> {}
                '\n' -> {
                    if (started || cell.isNotEmpty()) { cells.add(cell.toString()); rows.add(cells) }
                    cells = mutableListOf(); cell.clear(); started = false
                }
                else -> { cell.append(ch); started = true }
            }
            i++
        }
        if (started || cell.isNotEmpty()) { cells.add(cell.toString()); rows.add(cells) }
        return rows
    }
}
