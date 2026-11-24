package com.asarental.common

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Satu lembar kerja Excel. */
class XlsxSheet(name: String) {
    val name: String = name.replace(Regex("[\\\\/?*\\[\\]:]"), " ").take(31)
    internal val rows = mutableListOf<Pair<Int, List<Any?>>>()
    var widths: List<Double> = emptyList()

    fun title(text: String) { rows += STYLE_TITLE to listOf(text) }
    fun header(vararg h: String) { rows += STYLE_HEAD to h.toList() }
    fun row(vararg v: Any?) { rows += STYLE_NORMAL to v.toList() }
    fun rowList(v: List<Any?>) { rows += STYLE_NORMAL to v }
    fun blank() { rows += STYLE_NORMAL to emptyList() }

    companion object {
        const val STYLE_NORMAL = 0
        const val STYLE_HEAD = 1
        const val STYLE_TITLE = 2
        const val STYLE_NUM = 3
    }
}

/** Penulis file .xlsx minimal (tanpa pustaka tambahan). Bisa dibuka Excel, Google Sheets, WPS. */
object Xlsx {
    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    private fun col(i: Int): String {
        var n = i + 1
        val sb = StringBuilder()
        while (n > 0) {
            val r = (n - 1) % 26
            sb.insert(0, ('A' + r))
            n = (n - 1) / 26
        }
        return sb.toString()
    }

    private fun sheetXml(s: XlsxSheet): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sb.append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
        if (s.widths.isNotEmpty()) {
            sb.append("<cols>")
            s.widths.forEachIndexed { i, w -> sb.append("""<col min="${i + 1}" max="${i + 1}" width="$w" customWidth="1"/>""") }
            sb.append("</cols>")
        }
        sb.append("<sheetData>")
        s.rows.forEachIndexed { ri, (style, cells) ->
            if (cells.isEmpty()) return@forEachIndexed
            val r = ri + 1
            sb.append("""<row r="$r">""")
            cells.forEachIndexed { ci, v ->
                val ref = "${col(ci)}$r"
                when (v) {
                    null -> {}
                    is Number -> {
                        val d = v.toDouble()
                        if (d.isNaN() || d.isInfinite()) {
                            sb.append("""<c r="$ref" t="inlineStr"><is><t>-</t></is></c>""")
                        } else {
                            val st = if (style == XlsxSheet.STYLE_NORMAL && (v is Double || v is Float)) XlsxSheet.STYLE_NUM else style
                            sb.append("""<c r="$ref" s="$st"><v>$d</v></c>""")
                        }
                    }
                    else -> sb.append("""<c r="$ref" t="inlineStr" s="$style"><is><t xml:space="preserve">${esc(v.toString())}</t></is></c>""")
                }
            }
            sb.append("</row>")
        }
        sb.append("</sheetData></worksheet>")
        return sb.toString()
    }

    private const val STYLES = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
<numFmts count="1"><numFmt numFmtId="164" formatCode="0.000"/></numFmts>
<fonts count="3"><font><sz val="11"/><name val="Calibri"/></font><font><b/><sz val="11"/><color rgb="FFFFB300"/><name val="Calibri"/></font><font><b/><sz val="14"/><name val="Calibri"/></font></fonts>
<fills count="3"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill><fill><patternFill patternType="solid"><fgColor rgb="FF14171B"/><bgColor indexed="64"/></patternFill></fill></fills>
<borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders>
<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
<cellXfs count="4"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/><xf numFmtId="0" fontId="1" fillId="2" borderId="0" xfId="0" applyFont="1" applyFill="1"/><xf numFmtId="0" fontId="2" fillId="0" borderId="0" xfId="0" applyFont="1"/><xf numFmtId="164" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/></cellXfs>
<cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles>
</styleSheet>"""

    fun write(sheets: List<XlsxSheet>, out: File) {
        val list = sheets.ifEmpty { listOf(XlsxSheet("Kosong").apply { title("Tidak ada data") }) }
        // pastikan nama lembar unik
        val names = mutableSetOf<String>()
        val finalNames = list.map { s ->
            var n = s.name.ifBlank { "Sheet" }
            var k = 2
            while (!names.add(n)) n = "${s.name.take(28)} $k".also { k++ }
            n
        }
        ZipOutputStream(out.outputStream()).use { z ->
            fun put(path: String, text: String) {
                z.putNextEntry(ZipEntry(path))
                z.write(text.toByteArray(Charsets.UTF_8))
                z.closeEntry()
            }
            val ct = StringBuilder()
            ct.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
            ct.append("""<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""")
            ct.append("""<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>""")
            ct.append("""<Default Extension="xml" ContentType="application/xml"/>""")
            ct.append("""<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>""")
            ct.append("""<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>""")
            list.indices.forEach {
                ct.append("""<Override PartName="/xl/worksheets/sheet${it + 1}.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>""")
            }
            ct.append("</Types>")
            put("[Content_Types].xml", ct.toString())

            put(
                "_rels/.rels",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>""",
            )

            val wb = StringBuilder()
            wb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
            wb.append("""<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets>""")
            finalNames.forEachIndexed { i, n -> wb.append("""<sheet name="${esc(n)}" sheetId="${i + 1}" r:id="rId${i + 1}"/>""") }
            wb.append("</sheets></workbook>")
            put("xl/workbook.xml", wb.toString())

            val rel = StringBuilder()
            rel.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
            list.indices.forEach {
                rel.append("""<Relationship Id="rId${it + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet${it + 1}.xml"/>""")
            }
            rel.append("""<Relationship Id="rId${list.size + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>""")
            rel.append("</Relationships>")
            put("xl/_rels/workbook.xml.rels", rel.toString())

            put("xl/styles.xml", STYLES)
            list.forEachIndexed { i, s -> put("xl/worksheets/sheet${i + 1}.xml", sheetXml(s)) }
        }
    }
}
