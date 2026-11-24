package com.asarental.laporan

import com.asarental.common.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** Tingkat penekanan sebuah bagian/kalimat. */
enum class Level { INFO, GOOD, WARN, BAD }

data class Section(
    val icon: String,
    val title: String,
    val paragraphs: List<String> = emptyList(),
    val facts: List<Pair<String, String>> = emptyList(),
    val bullets: List<String> = emptyList(),
    val table: Pair<List<String>, List<List<String>>>? = null,
    val polygon: Pair<List<Pair<Double, Double>>, List<String>>? = null,
    val level: Level = Level.INFO,
)

data class Summary(
    val project: String,
    val location: String,
    val note: String,
    val exportedAt: Long,
    val device: String,
    val verdict: String,
    val verdictLevel: Level,
    val highlights: List<String>,
    val sections: List<Section>,
)

private fun Double.f(d: Int) = String.format(Locale.US, "%.${d}f", this)
private fun Double.s(d: Int) = (if (this >= 0) "+" else "") + f(d)
private fun Double.id(d: Int) = f(d)
private fun JSONObject.d(k: String): Double? = if (has(k) && !isNull(k)) optDouble(k).takeIf { !it.isNaN() } else null

/**
 * Mesin rangkuman: membaca paket data Ukur Lahan Pro lalu menyusun
 * penjelasan, penilaian, dan rekomendasi dalam Bahasa Indonesia.
 * Bekerja sepenuhnya offline.
 */
object Summarizer {

    fun isValid(j: JSONObject) = j.optString("format") == Brand.PACKAGE_FORMAT

    fun summarize(j: JSONObject): Summary {
        val p = j.optJSONObject("project") ?: JSONObject()
        val secs = mutableListOf<Section>()
        val highlights = mutableListOf<String>()
        var verdict = ""
        var verdictLevel = Level.INFO

        j.optJSONObject("leveling")?.let { lv ->
            val r = leveling(lv)
            secs += r.first
            highlights += r.second
            verdict = r.third.first
            verdictLevel = r.third.second
        }
        j.optJSONObject("volume")?.let { v ->
            val r = volume(v)
            secs += r.first
            highlights += r.second
        }
        j.optJSONObject("area")?.let { a ->
            val r = area(a)
            secs += r.first
            highlights += r.second
        }
        j.optJSONObject("totalstation")?.let { t ->
            val r = totalStation(t)
            secs += r.first
            highlights += r.second
        }

        if (verdict.isBlank()) {
            verdict = if (secs.isEmpty()) "Paket data tidak berisi hasil pengukuran." else "Data pengukuran berhasil dirangkum. Lihat rincian tiap bagian di bawah."
        }
        secs += Section(
            "🧭", "Langkah selanjutnya yang disarankan",
            bullets = nextSteps(j),
        )
        secs += Section(
            "ℹ️", "Catatan",
            paragraphs = listOf(
                "Rangkuman ini disusun otomatis oleh ASA Laporan dari data aplikasi Ukur Lahan Pro. " +
                    "Angka berasal dari sensor HP (kemiringan, kompas, GPS) sehingga ketelitiannya terbatas. " +
                    "Untuk keputusan biaya besar atau dokumen legal, lakukan verifikasi dengan alat ukur standar/surveyor.",
                "Dokumen internal ${Brand.COMPANY} – khusus ${Brand.TEAM}, tidak diperjualbelikan.",
            ),
        )
        return Summary(
            project = p.optString("name", "Tanpa nama"),
            location = p.optString("location", ""),
            note = p.optString("note", ""),
            exportedAt = j.optLong("exportedAt", System.currentTimeMillis()),
            device = j.optString("device", ""),
            verdict = verdict,
            verdictLevel = verdictLevel,
            highlights = highlights,
            sections = secs,
        )
    }

    // ================================================================ Sipat datar
    private fun leveling(lv: JSONObject): Triple<Section, List<String>, Pair<String, Level>> {
        val c = lv.optJSONObject("computed") ?: JSONObject()
        val pts = lv.optJSONArray("points") ?: JSONArray()
        val n = c.optInt("count", pts.length())
        val min = c.d("min") ?: 0.0
        val max = c.d("max") ?: 0.0
        val mean = c.d("mean") ?: 0.0
        val rangeCm = (max - min) * 100
        val design = lv.d("design")
        val hi = lv.d("hi")
        val target = c.d("target")

        val (cond, condLevel) = when {
            rangeCm <= 3 -> "sangat rata (beda tinggi ≤ 3 cm)" to Level.GOOD
            rangeCm <= 10 -> "cukup rata – cukup perataan ringan/finishing" to Level.GOOD
            rangeCm <= 30 -> "bergelombang – perlu perataan sedang" to Level.WARN
            else -> "tidak rata – perlu pekerjaan gali-timbun yang cukup besar" to Level.BAD
        }

        val paras = mutableListOf<String>()
        paras += "Pengukuran sipat datar dilakukan dari titik acuan ${lv.optString("refName", "BM")} (elevasi ${(lv.d("refElev") ?: 0.0).f(3)} m)" +
            (hi?.let { " dengan tinggi garis bidik (HI) terkunci ${it.f(3)} m" } ?: "") + ". Sebanyak $n titik telah diukur."
        paras += "Elevasi permukaan tanah berkisar antara ${min.f(3)} m dan ${max.f(3)} m, sehingga beda tinggi terbesar ${rangeCm.f(1)} cm. " +
            "Rata-rata elevasi ${mean.f(3)} m. Secara umum kondisi permukaan: $cond."

        val bullets = mutableListOf<String>()
        val rows = mutableListOf<List<String>>()
        val cuts = mutableListOf<Pair<String, Double>>()
        val fills = mutableListOf<Pair<String, Double>>()
        for (i in 0 until pts.length()) {
            val o = pts.getJSONObject(i)
            val e = o.optDouble("elev")
            val diff = design?.let { e - it }
            val status = when {
                diff == null -> "-"
                abs(diff) <= 0.005 -> "Rata"
                diff > 0 -> "Gali ${(diff * 100).f(1)} cm".also { cuts += o.optString("name") to diff }
                else -> "Timbun ${(-diff * 100).f(1)} cm".also { fills += o.optString("name") to -diff }
            }
            rows += listOf(o.optString("name"), o.optDouble("reading").f(3), e.f(3), diff?.s(3) ?: "-", status)
        }

        var verdict: String
        var vLevel = condLevel
        if (design != null) {
            val cut = c.optInt("cutCount")
            val fill = c.optInt("fillCount")
            val ok = c.optInt("okCount")
            val pct = if (n > 0) ok * 100.0 / n else 0.0
            paras += "Terhadap elevasi rencana ${design.f(3)} m: $cut titik masih lebih tinggi dan perlu DIGALI (terdalam ${((c.d("maxCut") ?: 0.0) * 100).f(1)} cm), " +
                "$fill titik lebih rendah dan perlu DITIMBUN (tertinggi ${((c.d("maxFill") ?: 0.0) * 100).f(1)} cm), dan $ok titik sudah sesuai rencana. " +
                "Tingkat kesesuaian saat ini ${pct.f(0)}%."
            target?.let {
                paras += "Di lapangan, tanah dianggap sudah rata bila rambu terbaca ${it.f(3)} m pada alat dengan HI yang sama. Bacaan lebih kecil berarti tanah masih terlalu tinggi; bacaan lebih besar berarti masih terlalu rendah."
            }
            val gap = (design - mean) * 100
            if (abs(gap) > 2) {
                paras += "Elevasi rencana ${if (gap > 0) "lebih tinggi" else "lebih rendah"} ${abs(gap).f(1)} cm dari rata-rata tanah, sehingga pekerjaan akan didominasi " +
                    (if (gap > 0) "TIMBUNAN (perlu tanah dari luar)." else "GALIAN (ada sisa tanah yang harus dibuang).") +
                    " Bila diinginkan gali ≈ timbun tanpa buang/datangkan tanah, gunakan elevasi rencana ± ${mean.f(3)} m."
            } else {
                paras += "Elevasi rencana sudah dekat dengan rata-rata tanah, sehingga volume galian dan timbunan relatif seimbang."
            }
            cuts.sortByDescending { it.second }
            fills.sortByDescending { it.second }
            if (cuts.isNotEmpty()) bullets += "Prioritas GALIAN: " + cuts.take(5).joinToString(", ") { "${it.first} (${(it.second * 100).f(1)} cm)" }
            if (fills.isNotEmpty()) bullets += "Prioritas TIMBUNAN: " + fills.take(5).joinToString(", ") { "${it.first} (${(it.second * 100).f(1)} cm)" }
            verdict = when {
                pct >= 90 -> "Lahan sudah hampir seluruhnya sesuai elevasi rencana (${pct.f(0)}% titik rata)."
                rangeCm <= 10 -> "Lahan perlu perataan ringan: $cut titik digali, $fill titik ditimbun."
                else -> "Lahan perlu pekerjaan tanah: $cut titik digali (maks ${((c.d("maxCut") ?: 0.0) * 100).f(0)} cm) dan $fill titik ditimbun (maks ${((c.d("maxFill") ?: 0.0) * 100).f(0)} cm)."
            }
            if (pct >= 90) vLevel = Level.GOOD
        } else {
            paras += "Elevasi rencana belum ditentukan. Saran: pakai rata-rata ${mean.f(3)} m agar volume galian dan timbunan seimbang."
            verdict = "Permukaan lahan $cond (beda tinggi ${rangeCm.f(1)} cm)."
        }

        // kualitas data
        val q = mutableListOf<String>()
        val bad = c.optInt("badPitch")
        val long = c.optInt("longSight")
        val corr = c.optInt("corrected")
        if (bad > 0) q += "$bad bacaan diambil saat bidikan belum datar dan tanpa koreksi jarak – sebaiknya diukur ulang."
        if (long > 0) q += "$long titik dibidik dari jarak > 20 m – ketelitian menurun, pertimbangkan pindah alat."
        if (corr > 0) q += "$corr bacaan sudah dikoreksi sudut secara otomatis."
        if (n < 5) q += "Jumlah titik masih sedikit ($n). Untuk gambaran lahan yang baik, ukur minimal tiap 5–10 m."
        if (q.isEmpty()) q += "Tidak ditemukan catatan kualitas: data layak dipakai."
        bullets += q.map { "Kualitas data: $it" }

        val sec = Section(
            "🎯", "Sipat Datar (Kerataan Lahan)",
            paragraphs = paras,
            facts = listOfNotNull(
                "Jumlah titik" to "$n",
                "Elevasi min – maks" to "${min.f(3)} – ${max.f(3)} m",
                "Beda tinggi terbesar" to "${rangeCm.f(1)} cm",
                "Elevasi rata-rata" to "${mean.f(3)} m",
                design?.let { "Elevasi rencana" to "${it.f(3)} m" },
                target?.let { "Bacaan rambu target" to "${it.f(3)} m" },
            ),
            bullets = bullets,
            table = Pair(listOf("Titik", "Bacaan", "Elevasi", "Selisih", "Tindakan"), rows),
            level = vLevel,
        )
        val hl = "🎯 Sipat datar: $n titik, beda tinggi ${rangeCm.f(1)} cm – $cond."
        return Triple(sec, listOf(hl), verdict to vLevel)
    }

    // ================================================================ Galian timbunan
    private fun volume(v: JSONObject): Pair<Section, List<String>> {
        val c = v.optJSONObject("computed") ?: JSONObject()
        val rows = v.optInt("rows")
        val cols = v.optInt("cols")
        val sp = v.d("spacing") ?: 0.0
        val design = v.d("design")
        val bal = c.d("balance")
        val cut = c.d("cut") ?: 0.0
        val fill = c.d("fill") ?: 0.0
        val loose = c.d("loose") ?: 0.0
        val swell = c.d("swellPct") ?: 25.0
        val truck = c.d("truck") ?: 7.0
        val trips = if (c.has("trips")) c.optInt("trips") else null
        val area = c.d("gridArea") ?: 0.0
        val missing = c.optInt("missing")
        val paras = mutableListOf<String>()
        val bullets = mutableListOf<String>()
        paras += "Perhitungan volume memakai metode grid $rows × $cols titik dengan jarak ${sp.f(1)} m, mencakup luas ${(area / 10000).f(4)} ha (${area.id(1)} m²)."
        bal?.let { paras += "Elevasi seimbang (galian = timbunan) adalah ${it.f(3)} m. Pada elevasi ini tanah hasil galian cukup untuk menimbun bagian yang rendah tanpa membuang atau mendatangkan tanah." }
        var hl = "⛏️ Galian & timbunan: elevasi seimbang ${bal?.f(3) ?: "-"} m."
        var level = Level.INFO
        if (design != null) {
            val net = cut - fill
            paras += "Dengan elevasi rencana ${design.f(3)} m, volume galian ± ${cut.id(1)} m³ dan timbunan ± ${fill.id(1)} m³ (volume padat)."
            if (abs(net) < 0.05 * maxOf(cut, fill, 1.0)) {
                paras += "Galian dan timbunan hampir seimbang, sehingga tanah galian dapat langsung dipakai untuk menimbun."
                level = Level.GOOD
            } else if (net > 0) {
                paras += "Terdapat kelebihan tanah ± ${net.id(1)} m³ padat (± ${(net * (1 + swell / 100)).id(1)} m³ lepas) yang perlu dibuang atau dipindahkan."
                level = Level.WARN
            } else {
                paras += "Terdapat kekurangan tanah ± ${(-net).id(1)} m³ padat yang perlu didatangkan. Perhitungkan tambahan 10–20% untuk penyusutan saat pemadatan."
                level = Level.WARN
            }
            paras += "Tanah galian setelah mengembang ${swell.f(0)}% menjadi ± ${loose.id(1)} m³" +
                (trips?.let { ", setara ± $it rit dump truck kapasitas ${truck.f(1)} m³." } ?: ".")
            if (bal != null && abs(design - bal) > 0.02) bullets += "Bila elevasi rencana diubah ke ${bal.f(3)} m, kebutuhan buang/datangkan tanah mendekati nol."
            hl = "⛏️ Galian ${cut.id(1)} m³, timbunan ${fill.id(1)} m³" + (trips?.let { ", ± $it rit truk." } ?: ".")
        } else {
            bullets += "Isi elevasi rencana di menu Galian & Timbunan untuk mendapatkan volume galian dan timbunan."
        }
        if (missing > 0) bullets += "$missing titik grid belum diisi sehingga volume belum lengkap."
        bullets += "Volume metode grid adalah perkiraan. Makin rapat grid (mis. 5 m), makin teliti."

        val g = v.optJSONArray("grid") ?: JSONArray()
        val head = listOf("") + (1..cols).map { "$it" }
        val tRows = (0 until g.length()).map { r ->
            val row = g.optJSONArray(r) ?: JSONArray()
            listOf("${'A' + r}") + (0 until row.length()).map { k -> if (row.isNull(k)) "-" else row.optDouble(k).f(2) }
        }
        return Section(
            "⛏️", "Galian & Timbunan",
            paragraphs = paras,
            facts = listOfNotNull(
                "Grid" to "$rows × $cols, jarak ${sp.f(1)} m",
                "Luas grid" to "${(area / 10000).f(4)} ha",
                bal?.let { "Elevasi seimbang" to "${it.f(3)} m" },
                design?.let { "Elevasi rencana" to "${it.f(3)} m" },
                if (design != null) "Galian / timbunan" to "${cut.id(1)} / ${fill.id(1)} m³" else null,
                trips?.let { "Ritase truk" to "$it rit" },
            ),
            bullets = bullets,
            table = head to tRows,
            level = level,
        ) to listOf(hl)
    }

    // ================================================================ Luas lahan
    private fun area(a: JSONObject): Pair<Section, List<String>> {
        val c = a.optJSONObject("computed") ?: JSONObject()
        val m2 = c.d("area") ?: 0.0
        val per = c.d("perimeter") ?: 0.0
        val n = c.optInt("count")
        val meanAcc = c.d("meanAcc") ?: 0.0
        val unc = c.d("uncertainty") ?: 0.0
        val sides = c.optJSONArray("sides") ?: JSONArray()
        val sideList = (0 until sides.length()).map { sides.optDouble(it) }
        val xyArr = c.optJSONArray("xy") ?: JSONArray()
        val xy = (0 until xyArr.length()).map { xyArr.getJSONArray(it).let { p -> p.optDouble(0) to p.optDouble(1) } }
        val name = a.optString("name", "Lahan")
        val relErr = if (m2 > 0) unc / m2 * 100 else 0.0
        val (quality, lvl) = when {
            meanAcc <= 3 -> "baik" to Level.GOOD
            meanAcc <= 6 -> "sedang" to Level.WARN
            else -> "rendah" to Level.BAD
        }
        val paras = mutableListOf<String>()
        paras += "Lahan \"$name\" diukur dengan berjalan menyusuri batas menggunakan GPS, terdiri dari $n titik sudut."
        paras += "Luas lahan ± ${(m2 / 10000).f(4)} hektare (ha), setara ${m2.id(2)} m² (1 ha = 10.000 m²). Keliling batas ${per.id(2)} m."
        if (sideList.isNotEmpty()) {
            paras += "Sisi terpanjang ${sideList.max().f(2)} m dan sisi terpendek ${sideList.min().f(2)} m."
        }
        paras += "Kualitas sinyal GPS rata-rata ±${meanAcc.f(1)} m (kategori $quality). Perkiraan ketelitian luas ±${(unc / 10000).f(4)} ha (±${relErr.f(1)}%)."
        val bullets = mutableListOf<String>()
        if (m2 in 0.1..500.0) bullets += "Lahan tergolong kecil (< 0,05 ha) untuk GPS HP. Untuk hasil lebih teliti gunakan Total Station Kamera atau meteran."
        if (lvl == Level.BAD) bullets += "Akurasi GPS rendah – ulangi pengukuran di tempat terbuka dan diamkan HP 5 detik di setiap sudut."
        if (relErr > 10) bullets += "Ketidakpastian luas di atas 10% – angka luas sebaiknya dipakai sebagai perkiraan awal saja."
        bullets += "Batas lahan dapat dilihat di Google Earth dengan ekspor KML dari Ukur Lahan Pro."
        return Section(
            "🚶", "Luas Lahan – $name",
            paragraphs = paras,
            facts = listOf(
                "Luas" to "${(m2 / 10000).f(4)} ha",
                "Luas (m²)" to "${m2.id(2)} m²",
                "Keliling" to "${per.id(2)} m",
                "Akurasi GPS rata-rata" to "±${meanAcc.f(1)} m",
            ),
            bullets = bullets,
            table = Pair(listOf("Sisi", "Panjang (m)"), sideList.mapIndexed { i, s -> listOf("${i + 1} → ${(i + 1) % sideList.size + 1}", s.f(2)) }),
            polygon = if (xy.size >= 3) xy to xy.indices.map { "${it + 1}" } else null,
            level = lvl,
        ) to listOf("🚶 Luas lahan $name: ${(m2 / 10000).f(4)} ha (${m2.id(2)} m²).")
    }

    // ================================================================ Total station
    private fun totalStation(t: JSONObject): Pair<Section, List<String>> {
        val c = t.optJSONObject("computed") ?: JSONObject()
        val pts = t.optJSONArray("points") ?: JSONArray()
        val n = pts.length()
        val xy = (0 until n).map { pts.getJSONObject(it).let { o -> o.optDouble("x") to o.optDouble("y") } }
        val names = (0 until n).map { pts.getJSONObject(it).optString("name") }
        val area = c.d("area")
        val zmin = c.d("zmin") ?: 0.0
        val zmax = c.d("zmax") ?: 0.0
        val slope = c.d("slopePct")
        val maxDist = c.d("maxDist") ?: 0.0
        val paras = mutableListOf<String>()
        paras += "Dari satu titik stasiun (tinggi alat ${(t.d("hi") ?: 0.0).f(2)} m) dibidik $n titik. Elevasi titik berkisar ${zmin.f(3)} – ${zmax.f(3)} m (beda ${((zmax - zmin) * 100).f(1)} cm)."
        area?.let { paras += "Titik-titik membentuk poligon dengan luas ± ${(it / 10000).f(4)} ha (${it.id(2)} m²) dan keliling ${(c.d("perimeter") ?: 0.0).id(2)} m." }
        slope?.let { paras += "Kemiringan rata-rata antara titik tertinggi dan terendah ± ${abs(it).f(2)}%." }
        val bullets = mutableListOf<String>()
        if (maxDist > 15) bullets += "Ada titik dengan jarak ${maxDist.f(1)} m. Jarak otomatis dari sudut kurang teliti di atas 15 m – sebaiknya pakai meteran."
        bullets += "Azimut memakai kompas HP (utara magnetis). Pastikan tidak ada benda logam di dekat HP saat membidik."
        val rows = (0 until n).map {
            val o = pts.getJSONObject(it)
            listOf(o.optString("name"), o.optDouble("az").f(1), o.optDouble("dist").f(2), o.optDouble("dh").s(3), o.optDouble("z").f(3))
        }
        return Section(
            "🔭", "Total Station Kamera",
            paragraphs = paras,
            facts = listOfNotNull(
                "Jumlah titik" to "$n",
                area?.let { "Luas poligon" to "${(it / 10000).f(4)} ha" },
                "Elevasi min – maks" to "${zmin.f(3)} – ${zmax.f(3)} m",
                slope?.let { "Kemiringan" to "${abs(it).f(2)} %" },
            ),
            bullets = bullets,
            table = Pair(listOf("Titik", "Azimut", "Jarak", "Δh", "Z"), rows),
            polygon = if (n >= 3) xy to names else null,
        ) to listOf("🔭 Total station: $n titik" + (area?.let { ", luas ${(it / 10000).f(4)} ha." } ?: "."))
    }

    private fun nextSteps(j: JSONObject): List<String> {
        val out = mutableListOf<String>()
        val lv = j.optJSONObject("leveling")
        val vol = j.optJSONObject("volume")
        if (lv != null) {
            if (!lv.has("design")) out += "Tetapkan elevasi rencana (mis. rata-rata tanah) lalu ukur ulang untuk mendapat status gali/timbun tiap titik."
            else out += "Tandai setiap patok dengan angka gali/timbun (cm) agar operator alat berat mudah bekerja."
            out += "Setelah perataan, ukur ulang titik yang sama untuk memastikan semua status menjadi RATA."
        }
        if (vol != null) out += "Siapkan jadwal dump truck sesuai perkiraan ritase dan cek kebutuhan pemadatan."
        if (j.optJSONObject("area") != null) out += "Simpan file KML batas lahan sebagai arsip dan cocokkan dengan dokumen/sertifikat bila ada."
        out += "Simpan laporan PDF ini di folder proyek dan bagikan ke pengawas lapangan."
        return out
    }

    /** Versi teks untuk WhatsApp. */
    fun toText(s: Summary): String {
        val sb = StringBuilder()
        sb.append("*RANGKUMAN HASIL KERJA*\n*${s.project}*")
        if (s.location.isNotBlank()) sb.append(" – ${s.location}")
        sb.append("\n${Report.dateText(s.exportedAt)}\n\n")
        sb.append("*Kesimpulan:* ${s.verdict}\n\n")
        s.highlights.forEach { sb.append("• $it\n") }
        s.sections.forEach { sec ->
            sb.append("\n*${sec.icon} ${sec.title}*\n")
            sec.facts.forEach { (k, v) -> sb.append("- $k: $v\n") }
            sec.paragraphs.forEach { sb.append(it).append("\n") }
            sec.bullets.forEach { sb.append("• ").append(it).append("\n") }
        }
        sb.append("\n_${Brand.FOOTER}_")
        return sb.toString()
    }

    /** Versi blok untuk PDF / gambar. */
    fun toBlocks(s: Summary): List<Block> {
        val b = mutableListOf<Block>()
        b += TitleBlock(
            "Rangkuman Hasil Kerja – ${s.project}",
            "Data ${Report.dateText(s.exportedAt)} • ASA Laporan",
            listOfNotNull(
                if (s.location.isNotBlank()) "Lokasi: ${s.location}" else null,
                if (s.note.isNotBlank()) "Catatan: ${s.note}" else null,
                if (s.device.isNotBlank()) "Perangkat ukur: ${s.device}" else null,
            ),
        )
        val (bg, fg) = when (s.verdictLevel) {
            Level.GOOD -> 0xFFE8F5E9.toInt() to 0xFF1B5E20.toInt()
            Level.WARN -> 0xFFFFF8E1.toInt() to 0xFF6D4C00.toInt()
            Level.BAD -> 0xFFFFEBEE.toInt() to 0xFFB71C1C.toInt()
            Level.INFO -> 0xFFE3F2FD.toInt() to 0xFF0D47A1.toInt()
        }
        b += NoteBlock("KESIMPULAN: ${s.verdict}", bg, fg)
        if (s.highlights.isNotEmpty()) {
            b += H2("Ringkasan")
            b += Bullets(s.highlights)
        }
        s.sections.forEach { sec ->
            b += H1("${sec.icon} ${sec.title}")
            if (sec.facts.isNotEmpty()) b += KV(sec.facts)
            sec.paragraphs.forEach { b += Para(it) }
            sec.polygon?.let { (xy, lb) -> b += PolygonBlock(xy, lb, 230f) }
            if (sec.bullets.isNotEmpty()) b += Bullets(sec.bullets)
            sec.table?.let { (h, r) -> if (r.isNotEmpty()) b += TableBlock(h, r) }
        }
        return b
    }

    /** Titik poligon dari azimut & jarak (cadangan bila x/y tidak ada). */
    @Suppress("unused")
    fun polar(az: Double, d: Double) = d * sin(Math.toRadians(az)) to d * cos(Math.toRadians(az))
}
