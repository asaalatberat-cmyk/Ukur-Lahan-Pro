package com.ukurlahan.pro.data

import android.content.Context
import android.os.Build
import com.asarental.common.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max

/** Bidang pekerjaan yang dapat diekspor. */
enum class Module(val key: String, val title: String, val icon: String) {
    LEVELING("leveling", "Sipat Datar", "🎯"),
    TOTAL_STATION("totalstation", "Total Station", "🔭"),
    AREA("area", "Luas Lahan", "🚶"),
    VOLUME("volume", "Galian & Timbunan", "⛏️"),
}

private fun Double.f(d: Int) = String.format(Locale.US, "%.${d}f", this)
private fun Double.s(d: Int) = (if (this >= 0) "+" else "") + f(d)

/** Semua data satu proyek. */
class ProjectData(
    val project: Project,
    val lv: LevelingData,
    val ts: TsData,
    val areaName: String,
    val area: List<GeoPt>,
    val vol: VolumeData,
) {
    fun has(m: Module): Boolean = when (m) {
        Module.LEVELING -> lv.points.isNotEmpty()
        Module.TOTAL_STATION -> ts.points.isNotEmpty()
        Module.AREA -> area.isNotEmpty()
        Module.VOLUME -> !vol.isEmpty()
    }

    fun available(): Set<Module> = Module.entries.filter { has(it) }.toSet()

    companion object {
        fun load(ctx: Context, p: Project): ProjectData {
            val a = AreaStore.parse(Store.loadFrom(ctx, p.id, AreaStore.KEY))
            return ProjectData(
                p,
                LevelingData.parse(Store.loadFrom(ctx, p.id, LevelingData.KEY)),
                TsData.parse(Store.loadFrom(ctx, p.id, TsData.KEY)),
                a.first, a.second,
                VolumeData.parse(Store.loadFrom(ctx, p.id, VolumeData.KEY)),
            )
        }
    }
}

// ---------------------------------------------------------------- statistik
data class LvStats(
    val n: Int, val min: Double, val max: Double, val mean: Double,
    val design: Double?, val target: Double?,
    val cut: Int, val fill: Int, val ok: Int, val maxCut: Double, val maxFill: Double,
    val badPitch: Int, val corrected: Int, val longSight: Int,
)

fun lvStats(d: LevelingData): LvStats? {
    if (d.points.isEmpty()) return null
    val e = d.points.map { it.elev }
    var cut = 0; var fill = 0; var ok = 0; var mc = 0.0; var mf = 0.0
    d.design?.let { ds ->
        d.points.forEach {
            val x = it.elev - ds
            when {
                abs(x) <= 0.005 -> ok++
                x > 0 -> { cut++; mc = max(mc, x) }
                else -> { fill++; mf = max(mf, -x) }
            }
        }
    }
    return LvStats(
        e.size, e.min(), e.max(), e.average(), d.design, d.hi?.let { h -> d.design?.let { h - it } },
        cut, fill, ok, mc, mf,
        d.points.count { abs(it.pitch) > 0.2 && it.dist == null },
        d.points.count { it.dist != null },
        d.points.count { (it.dist ?: 0.0) > 20.0 },
    )
}

private fun lvStatus(diff: Double?): String = when {
    diff == null -> "-"
    abs(diff) <= 0.005 -> "RATA"
    diff > 0 -> "GALI ${(diff * 100).f(1)} cm"
    else -> "TIMBUN ${(-diff * 100).f(1)} cm"
}

object Reports {
    // ============================================================ PDF / gambar
    fun blocks(pd: ProjectData, modules: Set<Module>): List<Block> {
        val p = pd.project
        val out = mutableListOf<Block>()
        val mods = modules.filter { pd.has(it) }
        out += TitleBlock(
            "Laporan Pekerjaan Tanah – ${p.name}",
            "Dibuat ${Report.dateText()} • Ukur Lahan Pro",
            listOfNotNull(
                if (p.location.isNotBlank()) "Lokasi: ${p.location}" else null,
                if (p.note.isNotBlank()) "Catatan: ${p.note}" else null,
                "Bagian: " + (if (mods.isEmpty()) "(belum ada data)" else mods.joinToString(", ") { it.title }),
            ),
        )
        if (mods.isEmpty()) {
            out += NoteBlock("Belum ada data tersimpan pada proyek ini untuk bagian yang dipilih.")
            return out
        }
        if (Module.LEVELING in mods) out += leveling(pd.lv)
        if (Module.TOTAL_STATION in mods) out += totalStation(pd.ts)
        if (Module.AREA in mods) out += area(pd.areaName, pd.area)
        if (Module.VOLUME in mods) out += volume(pd.vol)
        out += SpaceBlock(10f)
        out += NoteBlock(
            "Dokumen ini dibuat otomatis oleh aplikasi internal ${Brand.COMPANY}. Khusus ${Brand.TEAM} – tidak diperjualbelikan. " +
                "Akurasi tergantung sensor HP, kalibrasi, dan cara pengukuran.",
        )
        return out
    }

    private fun leveling(d: LevelingData): List<Block> {
        val st = lvStats(d) ?: return emptyList()
        val b = mutableListOf<Block>(H1("🎯 Sipat Datar (Waterpass)"))
        b += KV(
            listOfNotNull(
                "Titik acuan" to "${d.refName} = ${d.refElev.f(3)} m",
                "HI terkunci" to (d.hi?.f(3)?.plus(" m") ?: "-"),
                "Elevasi rencana" to (d.design?.f(3)?.plus(" m") ?: "-"),
                st.target?.let { "Bacaan rambu target" to "${it.f(3)} m" },
                "Jumlah titik" to "${st.n}",
                "Elevasi tertinggi / terendah" to "${st.max.f(3)} / ${st.min.f(3)} m",
                "Selisih maksimum" to "${((st.max - st.min) * 100).f(1)} cm",
                "Elevasi rata-rata" to "${st.mean.f(3)} m",
            ),
        )
        if (d.design != null) {
            b += Para("Terhadap elevasi rencana: ${st.cut} titik perlu digali (maks ${(st.maxCut * 100).f(1)} cm), ${st.fill} titik perlu ditimbun (maks ${(st.maxFill * 100).f(1)} cm), ${st.ok} titik sudah rata.")
        }
        val rows = d.points.mapIndexed { i, p ->
            val diff = d.design?.let { p.elev - it }
            listOf("${i + 1}", p.name, p.reading.f(3), p.elev.f(3), diff?.s(3) ?: "-", lvStatus(diff))
        }
        val colors = d.points.map { p ->
            val diff = d.design?.let { p.elev - it }
            when {
                diff == null -> null
                abs(diff) <= 0.005 -> Report.GREEN
                diff > 0 -> Report.RED
                else -> Report.BLUE
            }
        }
        b += TableBlock(listOf("No", "Titik", "Bacaan (m)", "Elevasi (m)", "Selisih (m)", "Status"), rows, listOf(0.5f, 1.3f, 1.1f, 1.1f, 1.1f, 1.5f), colors)
        return b
    }

    private fun totalStation(d: TsData): List<Block> {
        if (d.points.isEmpty()) return emptyList()
        val xy = d.points.map { it.x to it.y }
        val b = mutableListOf<Block>(H1("🔭 Total Station Kamera"))
        val zs = d.points.map { it.z }
        b += KV(
            listOfNotNull(
                "Tinggi alat / elevasi stasiun" to "${d.hi.f(3)} m / ${d.stElev.f(3)} m",
                "Jumlah titik" to "${d.points.size}",
                "Elevasi min / maks" to "${zs.min().f(3)} / ${zs.max().f(3)} m",
                if (xy.size >= 3) "Luas poligon" to "${(Geo.area(xy) / 10000).f(4)} ha (${Geo.area(xy).f(2)} m²)" else null,
                if (xy.size >= 3) "Keliling" to "${Geo.perimeter(xy).f(2)} m" else null,
            ),
        )
        b += PolygonBlock(xy, d.points.map { it.name }, 240f, xy.size >= 3)
        b += TableBlock(
            listOf("Titik", "Azimut (°)", "Sudut V (°)", "Jarak (m)", "Δh (m)", "X (m)", "Y (m)", "Z (m)"),
            d.points.map { listOf(it.name, it.az.f(1), it.pitch.f(2), it.dist.f(2), it.dh.s(3), it.x.f(2), it.y.f(2), it.z.f(3)) },
        )
        return b
    }

    private fun area(name: String, pts: List<GeoPt>): List<Block> {
        if (pts.isEmpty()) return emptyList()
        val xy = Geo.project(pts)
        val a = Geo.area(xy)
        val per = Geo.perimeter(xy)
        val worst = pts.maxOf { it.acc }
        val b = mutableListOf<Block>(H1("🚶 Luas Lahan (GPS) – $name"))
        b += KV(
            listOf(
                "Jumlah titik sudut" to "${pts.size}",
                "Luas" to "${(a / 10000).f(4)} ha",
                "Luas (m²)" to "${a.f(2)} m²  (1 ha = 10.000 m²)",
                "Keliling" to "${per.f(2)} m",
                "Akurasi GPS terburuk" to "±${worst.f(1)} m",
                "Perkiraan ketelitian luas" to "±${(per * worst / 2 / 10000).f(4)} ha",
            ),
        )
        b += PolygonBlock(xy, pts.indices.map { "${it + 1}" }, 260f, pts.size >= 3)
        val sides = Geo.sides(xy)
        b += TableBlock(
            listOf("No", "Lintang", "Bujur", "Akurasi (m)", "Sisi ke berikutnya (m)"),
            pts.mapIndexed { i, p ->
                listOf(
                    "${i + 1}", String.format(Locale.US, "%.7f", p.lat), String.format(Locale.US, "%.7f", p.lon),
                    p.acc.f(1), sides.getOrNull(i)?.f(2) ?: "-",
                )
            },
            listOf(0.5f, 1.6f, 1.6f, 1f, 1.4f),
        )
        return b
    }

    private fun volume(d: VolumeData): List<Block> {
        val r = VolumeCalc.compute(d)
        if (r.filled == 0) return emptyList()
        val b = mutableListOf<Block>(H1("⛏️ Galian & Timbunan (metode grid)"))
        b += KV(
            listOfNotNull(
                "Grid" to "${d.nRows} × ${d.nCols} titik, jarak ${d.spacing} m",
                "Luas grid" to "${(r.area / 10000).f(4)} ha (${r.area.f(1)} m²)",
                "Titik terisi / kosong" to "${r.filled} / ${r.missing}",
                r.balance?.let { "Elevasi seimbang (gali = timbun)" to "${it.f(3)} m" },
                "Elevasi rencana" to (r.design?.f(3)?.plus(" m") ?: "-"),
                if (r.design != null) "Volume galian (padat)" to "${r.cut.f(2)} m³" else null,
                if (r.design != null) "Volume timbunan (padat)" to "${r.fill.f(2)} m³" else null,
                if (r.design != null) "Galian lepas (kembang ${r.swellPct.f(0)}%)" to "${r.loose.f(2)} m³" else null,
                r.trips?.let { "Ritase truk (${r.truck.f(1)} m³)" to "$it rit" },
            ),
        )
        val headers = listOf("") + (1..d.nCols).map { "$it" }
        val rows = (0 until d.nRows).map { rr -> listOf("${'A' + rr}") + (0 until d.nCols).map { cc -> d.grid["$rr,$cc"]?.ifBlank { "-" } ?: "-" } }
        b += Para("Elevasi titik grid (m):", bold = true)
        b += TableBlock(headers, rows, listOf(0.5f) + List(d.nCols) { 1f })
        return b
    }

    // ============================================================ Excel
    fun sheets(pd: ProjectData, modules: Set<Module>): List<XlsxSheet> {
        val p = pd.project
        val list = mutableListOf<XlsxSheet>()
        val mods = modules.filter { pd.has(it) }
        val sum = XlsxSheet("Ringkasan").apply {
            widths = listOf(34.0, 30.0)
            title("${Brand.COMPANY} – Laporan Proyek ${p.name}")
            row("Khusus ${Brand.TEAM} – tidak diperjualbelikan")
            row("Lokasi", p.location)
            row("Catatan", p.note)
            row("Dibuat", Report.dateText())
            blank()
            header("Bagian", "Hasil utama")
        }
        list += sum

        if (Module.LEVELING in mods) {
            val d = pd.lv
            val st = lvStats(d)!!
            sum.row("Sipat Datar", "${st.n} titik, selisih maks ${((st.max - st.min) * 100).f(1)} cm")
            list += XlsxSheet("Sipat Datar").apply {
                widths = listOf(6.0, 12.0, 14.0, 10.0, 10.0, 16.0, 12.0, 12.0, 14.0, 18.0)
                title("Sipat Datar – ${p.name}")
                row("Titik acuan", d.refName, d.refElev)
                row("HI", d.hi)
                row("Elevasi rencana", d.design)
                blank()
                header("No", "Titik", "Bacaan mentah", "Jarak (m)", "Sudut (°)", "Bacaan koreksi", "HI", "Elevasi", "Selisih rencana", "Status")
                d.points.forEachIndexed { i, pt ->
                    val diff = d.design?.let { pt.elev - it }
                    row(i + 1, pt.name, pt.rawReading, pt.dist, pt.pitch, pt.reading, pt.hi, pt.elev, diff, lvStatus(diff))
                }
            }
        }
        if (Module.TOTAL_STATION in mods) {
            val d = pd.ts
            val xy = d.points.map { it.x to it.y }
            sum.row("Total Station", "${d.points.size} titik" + if (xy.size >= 3) ", luas ${(Geo.area(xy) / 10000).f(4)} ha" else "")
            list += XlsxSheet("Total Station").apply {
                widths = listOf(6.0, 10.0, 12.0, 12.0, 12.0, 12.0, 12.0, 12.0, 12.0)
                title("Total Station – ${p.name}")
                row("Tinggi alat", d.hi); row("Tinggi target", d.ht); row("Elevasi stasiun", d.stElev)
                blank()
                header("No", "Titik", "Azimut", "Sudut V", "Jarak datar", "Beda tinggi", "X Timur", "Y Utara", "Z")
                d.points.forEachIndexed { i, t -> row(i + 1, t.name, t.az, t.pitch, t.dist, t.dh, t.x, t.y, t.z) }
                if (xy.size >= 3) {
                    blank(); row("Luas (ha)", Geo.area(xy) / 10000); row("Luas (m²)", Geo.area(xy)); row("Keliling (m)", Geo.perimeter(xy))
                }
            }
        }
        if (Module.AREA in mods) {
            val xy = Geo.project(pd.area)
            val a = Geo.area(xy)
            sum.row("Luas Lahan", "${(a / 10000).f(4)} ha (${a.f(2)} m²)")
            list += XlsxSheet("Luas Lahan").apply {
                widths = listOf(6.0, 16.0, 16.0, 12.0, 12.0, 12.0, 14.0)
                title("Luas Lahan – ${pd.areaName}")
                row("Luas (ha)", a / 10000); row("Luas (m²)", a)
                row("Keliling (m)", Geo.perimeter(xy))
                blank()
                header("No", "Lintang", "Bujur", "Akurasi (m)", "X lokal (m)", "Y lokal (m)", "Sisi (m)")
                val sides = Geo.sides(xy)
                pd.area.forEachIndexed { i, g -> row(i + 1, g.lat, g.lon, g.acc, xy[i].first, xy[i].second, sides.getOrNull(i)) }
            }
        }
        if (Module.VOLUME in mods) {
            val d = pd.vol
            val r = VolumeCalc.compute(d)
            sum.row("Galian & Timbunan", if (r.design != null) "Galian ${r.cut.f(2)} m³, timbunan ${r.fill.f(2)} m³" else "Elevasi seimbang ${r.balance?.f(3) ?: "-"} m")
            list += XlsxSheet("Galian Timbunan").apply {
                widths = listOf(8.0) + List(d.nCols) { 11.0 }
                title("Galian & Timbunan – ${p.name}")
                row("Jarak grid (m)", d.spacing.replace(',', '.').toDoubleOrNull())
                row("Elevasi rencana", r.design)
                row("Elevasi seimbang", r.balance)
                row("Luas grid (ha)", r.area / 10000); row("Luas grid (m²)", r.area)
                row("Volume galian (m³)", r.cut)
                row("Volume timbunan (m³)", r.fill)
                row("Galian lepas (m³)", r.loose)
                row("Ritase truk", r.trips)
                blank()
                header(*(listOf("Baris") + (1..d.nCols).map { "Kolom $it" }).toTypedArray())
                for (rr in 0 until d.nRows) {
                    rowList(listOf<Any?>("${'A' + rr}") + (0 until d.nCols).map { cc -> d.grid["$rr,$cc"]?.replace(',', '.')?.toDoubleOrNull() })
                }
            }
        }
        if (mods.isEmpty()) sum.row("(belum ada data)", "")
        return list
    }

    // ============================================================ paket untuk ASA Laporan
    fun packageJson(pd: ProjectData, modules: Set<Module>): JSONObject {
        val p = pd.project
        val mods = modules.filter { pd.has(it) }
        val root = JSONObject()
        root.put("format", Brand.PACKAGE_FORMAT)
        root.put("version", Brand.PACKAGE_VERSION)
        root.put("company", Brand.COMPANY)
        root.put("team", Brand.TEAM)
        root.put("exportedAt", System.currentTimeMillis())
        root.put("device", "${Build.MANUFACTURER} ${Build.MODEL}")
        root.put("project", JSONObject().apply {
            put("id", p.id); put("name", p.name); put("location", p.location); put("note", p.note); put("created", p.created)
        })
        root.put("modules", JSONArray(mods.map { it.key }))

        if (Module.LEVELING in mods) {
            val d = pd.lv
            val st = lvStats(d)!!
            root.put("leveling", JSONObject().apply {
                put("refName", d.refName); put("refElev", d.refElev)
                d.hi?.let { put("hi", it) }
                d.design?.let { put("design", it) }
                put("points", JSONArray().apply { d.points.forEach { put(it.toJson()) } })
                put("computed", JSONObject().apply {
                    put("count", st.n); put("min", st.min); put("max", st.max); put("mean", st.mean)
                    put("range", st.max - st.min)
                    st.target?.let { put("target", it) }
                    put("cutCount", st.cut); put("fillCount", st.fill); put("okCount", st.ok)
                    put("maxCut", st.maxCut); put("maxFill", st.maxFill)
                    put("badPitch", st.badPitch); put("corrected", st.corrected); put("longSight", st.longSight)
                })
            })
        }
        if (Module.TOTAL_STATION in mods) {
            val d = pd.ts
            val xy = d.points.map { it.x to it.y }
            root.put("totalstation", JSONObject().apply {
                put("hi", d.hi); put("ht", d.ht); put("stElev", d.stElev)
                put("points", JSONArray().apply { d.points.forEach { put(it.toJson()) } })
                put("computed", JSONObject().apply {
                    put("count", d.points.size)
                    if (xy.size >= 3) { put("area", Geo.area(xy)); put("perimeter", Geo.perimeter(xy)) }
                    put("zmin", d.points.minOf { it.z }); put("zmax", d.points.maxOf { it.z })
                    // kemiringan antara titik tertinggi & terendah
                    val hiP = d.points.maxBy { it.z }
                    val loP = d.points.minBy { it.z }
                    val dist = hypot(hiP.x - loP.x, hiP.y - loP.y)
                    if (dist > 0.01) put("slopePct", (hiP.z - loP.z) / dist * 100)
                    put("maxDist", d.points.maxOf { it.dist })
                })
            })
        }
        if (Module.AREA in mods) {
            val xy = Geo.project(pd.area)
            val a = Geo.area(xy)
            val per = Geo.perimeter(xy)
            root.put("area", JSONObject().apply {
                put("name", pd.areaName)
                put("points", JSONArray().apply { pd.area.forEach { put(it.toJson()) } })
                put("computed", JSONObject().apply {
                    put("count", pd.area.size); put("area", a); put("perimeter", per)
                    put("sides", JSONArray(Geo.sides(xy)))
                    put("worstAcc", pd.area.maxOf { it.acc }); put("meanAcc", pd.area.map { it.acc }.average())
                    put("uncertainty", per * pd.area.maxOf { it.acc } / 2)
                    put("xy", JSONArray().apply { xy.forEach { put(JSONArray(listOf(it.first, it.second))) } })
                })
            })
        }
        if (Module.VOLUME in mods) {
            val d = pd.vol
            val r = VolumeCalc.compute(d)
            root.put("volume", JSONObject().apply {
                put("rows", d.nRows); put("cols", d.nCols); put("spacing", d.spacing.replace(',', '.').toDoubleOrNull() ?: 0.0)
                r.design?.let { put("design", it) }
                put("grid", JSONArray().apply {
                    for (rr in 0 until d.nRows) put(JSONArray().apply {
                        for (cc in 0 until d.nCols) put(d.grid["$rr,$cc"]?.replace(',', '.')?.toDoubleOrNull() ?: JSONObject.NULL)
                    })
                })
                put("computed", JSONObject().apply {
                    put("filled", r.filled); put("missing", r.missing); put("gridArea", r.area)
                    r.balance?.let { put("balance", it) }
                    put("cut", r.cut); put("fill", r.fill); put("loose", r.loose)
                    put("swellPct", r.swellPct); put("truck", r.truck)
                    r.trips?.let { put("trips", it) }
                })
            })
        }
        return root
    }
}
