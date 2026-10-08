package app.forgetit.domain

import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.xml.parsers.DocumentBuilderFactory

data class Sheet(val name: String, val rows: List<List<String>>)

/**
 * Minimal Excel (.xlsx) reader and writer, so no large spreadsheet library is needed.
 * Reads text, numbers and shared strings from every sheet; writes plain sheets with a bold, frozen header row.
 */
object Xlsx {
    private const val MAX_BYTES = 30L * 1024 * 1024

    private fun builder() = DocumentBuilderFactory.newInstance().apply {
        // Spreadsheets come from outside the app: never resolve external entities.
        for ((f, v) in listOf("http://apache.org/xml/features/disallow-doctype-decl" to true, "http://xml.org/sax/features/external-general-entities" to false)) {
            try { setFeature(f, v) } catch (_: Exception) {}
        }
        isNamespaceAware = false
        isExpandEntityReferences = false
    }.newDocumentBuilder()

    private fun parse(bytes: ByteArray) = builder().parse(ByteArrayInputStream(bytes)).documentElement

    private fun Element.kids(tag: String): List<Element> {
        val out = mutableListOf<Element>()
        val n = childNodes
        for (i in 0 until n.length) (n.item(i) as? Element)?.takeIf { it.tagName == tag }?.let(out::add)
        return out
    }

    private fun Element.allText(): String {
        val sb = StringBuilder()
        fun walk(node: Node) {
            if (node is Element && node.tagName == "t") sb.append(node.textContent)
            else for (i in 0 until node.childNodes.length) walk(node.childNodes.item(i))
        }
        walk(this)
        return sb.toString()
    }

    private fun columnIndex(ref: String): Int {
        var n = 0
        for (ch in ref) { if (ch in 'A'..'Z') n = n * 26 + (ch - 'A' + 1) else break }
        return n - 1
    }

    /** Throws IllegalArgumentException when the bytes are not an .xlsx workbook. */
    fun read(bytes: ByteArray): List<Sheet> {
        val parts = mutableMapOf<String, ByteArray>()
        var total = 0L
        try {
            ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
                var e = zip.nextEntry
                while (e != null) {
                    if (!e.isDirectory && (e.name.startsWith("xl/") || e.name == "[Content_Types].xml")) {
                        val data = zip.readBytes()
                        total += data.size
                        require(total <= MAX_BYTES) { "That spreadsheet is too large" }
                        parts[e.name] = data
                    }
                    e = zip.nextEntry
                }
            }
        } catch (e: java.io.IOException) {
            throw IllegalArgumentException("That is not an Excel .xlsx file")
        }
        val workbook = parts["xl/workbook.xml"] ?: throw IllegalArgumentException("That is not an Excel .xlsx file")

        val shared = parts["xl/sharedStrings.xml"]?.let { b -> parse(b).kids("si").map { it.allText() } }.orEmpty()
        val targets = parts["xl/_rels/workbook.xml.rels"]?.let { b ->
            parse(b).kids("Relationship").associate { it.getAttribute("Id") to it.getAttribute("Target") }
        }.orEmpty()

        val sheets = parse(workbook).kids("sheets").firstOrNull()?.kids("sheet").orEmpty()
        return sheets.mapIndexedNotNull { i, s ->
            val target = targets[s.getAttribute("r:id")] ?: "worksheets/sheet${i + 1}.xml"
            val path = if (target.startsWith("/")) target.removePrefix("/") else "xl/" + target.removePrefix("./")
            val xml = parts[path] ?: return@mapIndexedNotNull null
            Sheet(s.getAttribute("name"), readRows(parse(xml), shared))
        }
    }

    private fun readRows(sheet: Element, shared: List<String>): List<List<String>> {
        val data = sheet.kids("sheetData").firstOrNull() ?: return emptyList()
        val rows = mutableListOf<List<String>>()
        for (row in data.kids("row")) {
            val cells = mutableListOf<String>()
            for (c in row.kids("c")) {
                val col = columnIndex(c.getAttribute("r")).let { if (it < 0) cells.size else it }
                while (cells.size < col) cells += ""
                val v = c.kids("v").firstOrNull()?.textContent.orEmpty()
                val text = when (c.getAttribute("t")) {
                    "s" -> shared.getOrNull(v.trim().toIntOrNull() ?: -1).orEmpty()
                    "inlineStr" -> c.kids("is").firstOrNull()?.allText().orEmpty()
                    else -> v
                }
                if (col < cells.size) cells[col] = text else cells += text
            }
            if (cells.any { it.isNotBlank() }) rows += cells
        }
        return rows
    }

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
        .filter { it == '\n' || it == '\t' || it == '\r' || it.code >= 0x20 }

    private fun colName(i: Int): String {
        var n = i + 1
        val sb = StringBuilder()
        while (n > 0) { sb.append('A' + (n - 1) % 26); n = (n - 1) / 26 }
        return sb.reverse().toString()
    }

    private val NUMBER = Regex("""-?(0|[1-9]\d{0,14})(\.\d{1,10})?""")

    private fun sheetXml(rows: List<List<String>>): String {
        val width = rows.maxOfOrNull { it.size } ?: 1
        val sb = StringBuilder("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
        sb.append("""<sheetViews><sheetView workbookViewId="0"><pane ySplit="1" topLeftCell="A2" activePane="bottomLeft" state="frozen"/></sheetView></sheetViews>""")
        sb.append("<cols>")
        for (i in 0 until width) sb.append("""<col min="${i + 1}" max="${i + 1}" width="${if (i == 0) 28 else 18}" customWidth="1"/>""")
        sb.append("</cols><sheetData>")
        rows.forEachIndexed { r, row ->
            sb.append("""<row r="${r + 1}">""")
            row.forEachIndexed { c, cell ->
                val ref = colName(c) + (r + 1)
                val style = if (r == 0) """ s="1"""" else ""
                if (r > 0 && NUMBER.matches(cell)) sb.append("""<c r="$ref"$style><v>$cell</v></c>""")
                else sb.append("""<c r="$ref"$style t="inlineStr"><is><t xml:space="preserve">${esc(cell)}</t></is></c>""")
            }
            sb.append("</row>")
        }
        sb.append("</sheetData></worksheet>")
        return sb.toString()
    }

    private val STYLES = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""" +
        """<fonts count="2"><font><sz val="11"/><name val="Calibri"/></font><font><b/><color rgb="FFFFFFFF"/><sz val="11"/><name val="Calibri"/></font></fonts>""" +
        """<fills count="3"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill>""" +
        """<fill><patternFill patternType="solid"><fgColor rgb="FF0B6E6A"/><bgColor indexed="64"/></patternFill></fill></fills>""" +
        """<borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders>""" +
        """<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>""" +
        """<cellXfs count="2"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/><xf numFmtId="0" fontId="1" fillId="2" borderId="0" xfId="0" applyFont="1" applyFill="1"/></cellXfs>""" +
        """</styleSheet>"""

    fun write(sheets: List<Sheet>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            fun put(name: String, text: String) { zip.putNextEntry(ZipEntry(name)); zip.write(text.toByteArray(Charsets.UTF_8)); zip.closeEntry() }
            val xml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>"""
            put(
                "[Content_Types].xml",
                xml + """<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>""" +
                    """<Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>""" +
                    """<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>""" +
                    sheets.indices.joinToString("") { """<Override PartName="/xl/worksheets/sheet${it + 1}.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>""" } + "</Types>",
            )
            put("_rels/.rels", xml + """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>""")
            put(
                "xl/workbook.xml",
                xml + """<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets>""" +
                    sheets.mapIndexed { i, s -> """<sheet name="${esc(s.name.take(31))}" sheetId="${i + 1}" r:id="rId${i + 1}"/>""" }.joinToString("") + "</sheets></workbook>",
            )
            put(
                "xl/_rels/workbook.xml.rels",
                xml + """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""" +
                    sheets.indices.joinToString("") { """<Relationship Id="rId${it + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet${it + 1}.xml"/>""" } +
                    """<Relationship Id="rId${sheets.size + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/></Relationships>""",
            )
            put("xl/styles.xml", STYLES)
            sheets.forEachIndexed { i, s -> put("xl/worksheets/sheet${i + 1}.xml", sheetXml(s.rows)) }
        }
        return out.toByteArray()
    }
}
