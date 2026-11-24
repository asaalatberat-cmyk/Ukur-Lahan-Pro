package com.asarental.common

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

// =====================================================================
// Blok-blok isi laporan (dipakai PDF & gambar HD)
// =====================================================================
sealed class Block

/** Kepala laporan dengan logo perusahaan. */
class TitleBlock(val title: String, val subtitle: String, val meta: List<String> = emptyList()) : Block()
class H1(val text: String) : Block()
class H2(val text: String) : Block()
class Para(val text: String, val size: Float = 10f, val bold: Boolean = false, val color: Int = Report.INK) : Block()
class Bullets(val items: List<String>) : Block()
class KV(val rows: List<Pair<String, String>>) : Block()
class TableBlock(
    val headers: List<String>,
    val rows: List<List<String>>,
    val weights: List<Float>? = null,
    /** Warna teks per baris (null = default). */
    val rowColors: List<Int?>? = null,
) : Block()
class PolygonBlock(
    val xy: List<Pair<Double, Double>>,
    val labels: List<String>,
    val height: Float = 250f,
    val closed: Boolean = true,
) : Block()
class NoteBlock(val text: String, val bg: Int = 0xFFFFF8E1.toInt(), val fg: Int = 0xFF5D4A10.toInt()) : Block()
class SpaceBlock(val h: Float) : Block()
class PageBreak : Block()

// =====================================================================
// Renderer PDF (A4) + konversi ke PNG resolusi tinggi
// =====================================================================
object Report {
    const val INK = 0xFF1D232B.toInt()
    const val MUTED = 0xFF5F6B76.toInt()
    const val ACCENT = 0xFFFFB300.toInt()
    const val ACCENT_DARK = 0xFF9A6A00.toInt()
    const val DARK = 0xFF14171B.toInt()
    const val RED = 0xFFC62828.toInt()
    const val BLUE = 0xFF0277BD.toInt()
    const val GREEN = 0xFF2E7D32.toInt()

    private const val W = 595f
    private const val H = 842f
    private const val M = 38f
    private const val CW = W - 2 * M
    private const val BOTTOM = H - M - 18f

    fun dateText(t: Long = System.currentTimeMillis()): String =
        SimpleDateFormat("dd MMM yyyy HH:mm", Locale("id", "ID")).format(Date(t))

    private fun tp(size: Float, bold: Boolean = false, color: Int = INK): TextPaint =
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            this.color = color
            typeface = if (bold) Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD) else Typeface.SANS_SERIF
        }

    private fun layout(text: CharSequence, paint: TextPaint, width: Float, align: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, max(10, width.toInt()))
            .setAlignment(align)
            .setLineSpacing(0f, 1.12f)
            .setIncludePad(false)
            .build()

    private fun drawLayout(c: Canvas, l: StaticLayout, x: Float, y: Float) {
        c.save()
        c.translate(x, y)
        l.draw(c)
        c.restore()
    }

    /** Tulis blok ke file PDF A4. */
    fun writePdf(ctx: Context, blocks: List<Block>, out: File, docTitle: String) {
        val logo: Bitmap? = try {
            BitmapFactory.decodeResource(ctx.resources, Brand.LOGO)
        } catch (_: Exception) {
            null
        }
        val logoFull: Bitmap? = try {
            BitmapFactory.decodeResource(ctx.resources, Brand.LOGO_FULL)
        } catch (_: Exception) {
            null
        }
        val doc = PdfDocument()
        var pageNo = 0
        var page: PdfDocument.Page? = null
        var y = M

        fun finishPage() {
            val p = page ?: return
            val c = p.canvas
            val fp = tp(7f, color = MUTED)
            c.drawLine(M, H - M + 2f, W - M, H - M + 2f, Paint().apply { color = 0xFFDDDDDD.toInt(); strokeWidth = 0.6f })
            c.drawText(Brand.FOOTER, M, H - M + 13f, fp)
            val pn = "Hal. $pageNo"
            c.drawText(pn, W - M - fp.measureText(pn), H - M + 13f, fp)
            doc.finishPage(p)
            page = null
        }

        fun newPage() {
            finishPage()
            pageNo++
            val p = doc.startPage(PdfDocument.PageInfo.Builder(W.toInt(), H.toInt(), pageNo).create())
            page = p
            val c = p.canvas
            c.drawColor(0xFFFFFFFF.toInt())
            // garis atas + judul kecil
            c.drawRect(0f, 0f, W, 5f, Paint().apply { color = ACCENT })
            if (pageNo > 1) {
                val hp = tp(7.5f, color = MUTED)
                c.drawText(docTitle, M, M - 12f, hp)
                if (logo != null) c.drawBitmap(logo, null, RectF(W - M - 16f, M - 24f, W - M, M - 8f), null)
            }
            y = M
        }

        fun canvas(): Canvas {
            if (page == null) newPage()
            return page!!.canvas
        }

        fun ensure(h: Float) {
            if (page == null || y + h > BOTTOM) newPage()
        }

        for (b in blocks) {
            when (b) {
                is PageBreak -> newPage()
                is TableBlock -> {
                    val widths = colWidths(b)
                    val headH = rowHeight(b.headers, widths, true)
                    ensure(headH + 18f)
                    drawRow(canvas(), b.headers, widths, y, true, null, false)
                    y += headH
                    b.rows.forEachIndexed { i, r ->
                        val rh = rowHeight(r, widths, false)
                        if (y + rh > BOTTOM) {
                            newPage()
                            drawRow(canvas(), b.headers, widths, y, true, null, false)
                            y += headH
                        }
                        drawRow(canvas(), r, widths, y, false, b.rowColors?.getOrNull(i), i % 2 == 1)
                        y += rh
                    }
                    y += 8f
                }
                else -> {
                    val h = measure(b)
                    ensure(h)
                    draw(canvas(), b, y, logo, logoFull)
                    y += h
                }
            }
        }
        if (page == null) newPage()
        finishPage()
        FileOutputStream(out).use { doc.writeTo(it) }
        doc.close()
    }

    /** Ubah setiap halaman PDF menjadi PNG (default 300 dpi = 2480×3508 px). */
    fun pdfToPng(pdf: File, outDir: File, baseName: String, dpi: Int = 300): List<File> {
        val result = mutableListOf<File>()
        val pfd = ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(pfd)
        try {
            val scale = dpi / 72f
            for (i in 0 until renderer.pageCount) {
                val pg = renderer.openPage(i)
                val bmp = Bitmap.createBitmap((pg.width * scale).toInt(), (pg.height * scale).toInt(), Bitmap.Config.ARGB_8888)
                bmp.eraseColor(0xFFFFFFFF.toInt())
                pg.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                pg.close()
                val f = File(outDir, "${baseName}_hal${i + 1}.png")
                FileOutputStream(f).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bmp.recycle()
                result += f
            }
        } finally {
            renderer.close()
            pfd.close()
        }
        return result
    }

    // ------------------------------------------------------------- ukur & gambar
    private fun measure(b: Block): Float = when (b) {
        is TitleBlock -> 172f + b.meta.size * 12f
        is H1 -> layout(b.text, tp(15f, true), CW).height + 16f
        is H2 -> layout(b.text, tp(11.5f, true, ACCENT_DARK), CW).height + 10f
        is Para -> layout(b.text, tp(b.size, b.bold, b.color), CW).height + 6f
        is Bullets -> b.items.sumOf { layout(it, tp(10f), CW - 14f).height.toDouble() + 3.0 }.toFloat() + 4f
        is KV -> b.rows.size * 15f + 6f
        is PolygonBlock -> b.height + 10f
        is NoteBlock -> layout(b.text, tp(9.5f, color = b.fg), CW - 20f).height + 20f
        is SpaceBlock -> b.h
        else -> 0f
    }

    private fun draw(c: Canvas, b: Block, y0: Float, logo: Bitmap?, logoFull: Bitmap?) {
        var y = y0
        when (b) {
            is TitleBlock -> {
                // logo lengkap perusahaan di bagian paling atas
                if (logoFull != null) {
                    val w = 250f
                    val h = w * logoFull.height / logoFull.width
                    c.drawBitmap(logoFull, null, RectF(M, y, M + w, y + h), Paint(Paint.FILTER_BITMAP_FLAG))
                }
                y += 54f
                val bg = Paint().apply { color = DARK }
                c.drawRoundRect(RectF(M, y, W - M, y + 96f), 10f, 10f, bg)
                if (logo != null) {
                    c.drawRoundRect(RectF(M + 12f, y + 14f, M + 80f, y + 82f), 8f, 8f, Paint().apply { color = 0xFFFFFFFF.toInt() })
                    c.drawBitmap(logo, null, RectF(M + 14f, y + 16f, M + 78f, y + 80f), null)
                }
                val tx = M + 94f
                c.drawText(Brand.COMPANY, tx, y + 26f, tp(9f, true, ACCENT))
                drawLayout(c, layout(b.title, tp(17f, true, 0xFFFFFFFF.toInt()), W - M - tx - 10f), tx, y + 32f)
                c.drawText(b.subtitle, tx, y + 78f, tp(9f, color = 0xFFCFD8DC.toInt()))
                c.drawText("Khusus ${Brand.TEAM} — tidak diperjualbelikan", tx, y + 89f, tp(7.5f, color = 0xFF90A4AE.toInt()))
                var yy = y + 112f
                b.meta.forEach {
                    c.drawText(it, M, yy, tp(9f, color = MUTED))
                    yy += 12f
                }
            }
            is H1 -> {
                c.drawRect(M, y + 4f, M + 4f, y + 22f, Paint().apply { color = ACCENT })
                drawLayout(c, layout(b.text, tp(15f, true), CW - 12f), M + 10f, y + 4f)
            }
            is H2 -> drawLayout(c, layout(b.text, tp(11.5f, true, ACCENT_DARK), CW), M, y + 4f)
            is Para -> drawLayout(c, layout(b.text, tp(b.size, b.bold, b.color), CW), M, y)
            is Bullets -> {
                var yy = y
                val p = tp(10f)
                b.items.forEach {
                    c.drawCircle(M + 4f, yy + 6.5f, 1.8f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ACCENT_DARK })
                    val l = layout(it, p, CW - 14f)
                    drawLayout(c, l, M + 12f, yy)
                    yy += l.height + 3f
                }
            }
            is KV -> {
                var yy = y + 11f
                val lp = tp(9.5f, color = MUTED)
                val vp = tp(9.5f, true)
                b.rows.forEach { (k, v) ->
                    c.drawText(k, M, yy, lp)
                    c.drawText(v, M + CW * 0.42f, yy, vp)
                    c.drawLine(M, yy + 4f, W - M, yy + 4f, Paint().apply { color = 0xFFEEEEEE.toInt(); strokeWidth = 0.5f })
                    yy += 15f
                }
            }
            is PolygonBlock -> drawPolygon(c, b, RectF(M, y, W - M, y + b.height))
            is NoteBlock -> {
                val l = layout(b.text, tp(9.5f, color = b.fg), CW - 20f)
                c.drawRoundRect(RectF(M, y + 2f, W - M, y + l.height + 16f), 6f, 6f, Paint().apply { color = b.bg })
                drawLayout(c, l, M + 10f, y + 9f)
            }
            else -> {}
        }
    }

    private fun colWidths(t: TableBlock): List<Float> {
        val n = t.headers.size
        val w = t.weights ?: List(n) { 1f }
        val sum = w.sum()
        return w.map { it / sum * CW }
    }

    private fun rowHeight(cells: List<String>, widths: List<Float>, head: Boolean): Float {
        val p = tp(if (head) 8.5f else 8.8f, head)
        var h = 0f
        cells.forEachIndexed { i, s -> h = max(h, layout(s, p, widths.getOrElse(i) { 40f } - 8f).height.toFloat()) }
        return h + 8f
    }

    private fun drawRow(c: Canvas, cells: List<String>, widths: List<Float>, y: Float, head: Boolean, color: Int?, zebra: Boolean) {
        val h = rowHeight(cells, widths, head)
        if (head) c.drawRect(M, y, W - M, y + h, Paint().apply { this.color = DARK })
        else if (zebra) c.drawRect(M, y, W - M, y + h, Paint().apply { this.color = 0xFFF5F7F9.toInt() })
        val p = if (head) tp(8.5f, true, ACCENT) else tp(8.8f, color != null, color ?: INK)
        var x = M
        cells.forEachIndexed { i, s ->
            val w = widths.getOrElse(i) { 40f }
            drawLayout(c, layout(s, p, w - 8f), x + 4f, y + 4f)
            x += w
        }
        if (!head) c.drawLine(M, y + h, W - M, y + h, Paint().apply { this.color = 0xFFE0E4E8.toInt(); strokeWidth = 0.5f })
    }

    private fun drawPolygon(c: Canvas, b: PolygonBlock, r: RectF) {
        c.drawRoundRect(r, 8f, 8f, Paint().apply { color = 0xFF0E1114.toInt() })
        val lp = tp(8f, color = 0xFFFFFFFF.toInt())
        if (b.xy.isEmpty()) {
            c.drawText("Tidak ada titik", r.left + 12f, r.centerY(), lp)
            return
        }
        val minX = b.xy.minOf { it.first }
        val maxX = b.xy.maxOf { it.first }
        val minY = b.xy.minOf { it.second }
        val maxY = b.xy.maxOf { it.second }
        val pad = 28f
        val sx = max(maxX - minX, 1.0)
        val sy = max(maxY - minY, 1.0)
        val scale = min((r.width() - 2 * pad) / sx, (r.height() - 2 * pad) / sy)
        val cx = (minX + maxX) / 2
        val cy = (minY + maxY) / 2
        fun px(p: Pair<Double, Double>) = Pair(
            (r.centerX() + (p.first - cx) * scale).toFloat(),
            (r.centerY() - (p.second - cy) * scale).toFloat(),
        )
        val path = Path()
        b.xy.forEachIndexed { i, p ->
            val (x, y) = px(p)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        val poly = b.closed && b.xy.size >= 3
        if (poly) {
            path.close()
            c.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x44FFB300; style = Paint.Style.FILL })
        }
        c.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ACCENT; style = Paint.Style.STROKE; strokeWidth = 2f })
        b.xy.forEachIndexed { i, p ->
            val (x, y) = px(p)
            c.drawCircle(x, y, 3f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = if (i == 0) 0xFF4CE07A.toInt() else 0xFFFFFFFF.toInt() })
            c.drawText(b.labels.getOrElse(i) { "${i + 1}" }, x + 5f, y - 5f, lp)
        }
        // utara
        c.drawText("U ↑", r.right - 26f, r.top + 16f, lp)
        // skala
        val target = (r.width() * 0.25f) / scale
        val p10 = 10.0.pow(floor(log10(target)))
        val nice = listOf(1.0, 2.0, 5.0, 10.0).map { it * p10 }.last { it <= target * 1.0001 }
        val len = (nice * scale).toFloat()
        val yb = r.bottom - 10f
        c.drawLine(r.left + 12f, yb, r.left + 12f + len, yb, Paint().apply { color = 0xFFFFFFFF.toInt(); strokeWidth = 2f })
        c.drawText(if (nice >= 1) "${nice.toInt()} m" else String.format(Locale.US, "%.2f m", nice), r.left + 16f + len, yb + 3f, lp)
    }
}
