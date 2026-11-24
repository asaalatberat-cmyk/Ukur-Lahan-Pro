package com.ukurlahan.pro.ui

import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ukurlahan.pro.data.LevelingData
import com.ukurlahan.pro.data.Settings
import com.ukurlahan.pro.data.Store
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.ceil
import kotlin.math.tan

// =====================================================================
// GALIAN & TIMBUNAN (metode grid / borrow-pit)
// =====================================================================
@Composable
fun VolumeScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val saved = remember { com.ukurlahan.pro.data.VolumeData.load(ctx) }
    var rowsText by remember { mutableStateOf(saved.rows) }
    var colsText by remember { mutableStateOf(saved.cols) }
    var spacingText by remember { mutableStateOf(saved.spacing) }
    var designText by remember { mutableStateOf(saved.design) }
    var swellText by remember { mutableStateOf(saved.swell) }
    var truckText by remember { mutableStateOf(saved.truck) }
    val grid = remember { mutableStateMapOf<String, String>().also { it.putAll(saved.grid) } }
    val gridSnapshot = grid.toMap()
    LaunchedEffect(rowsText, colsText, spacingText, designText, swellText, truckText, gridSnapshot) {
        com.ukurlahan.pro.data.VolumeData(rowsText, colsText, spacingText, designText, swellText, truckText, gridSnapshot).save(ctx)
    }

    val rows = (rowsText.toIntOrNull() ?: 2).coerceIn(2, 15)
    val cols = (colsText.toIntOrNull() ?: 2).coerceIn(2, 15)
    val s = spacingText.toNum() ?: 0.0
    val design = designText.toNum()

    // ---- Perhitungan
    var sumW = 0.0
    var sumWZ = 0.0
    var cut = 0.0
    var fill = 0.0
    var missing = 0
    for (r in 0 until rows) for (c in 0 until cols) {
        val z = grid["$r,$c"]?.toNum()
        if (z == null) {
            missing++
        } else {
            val edgeR = r == 0 || r == rows - 1
            val edgeC = c == 0 || c == cols - 1
            val w = if (edgeR && edgeC) 1.0 else if (edgeR || edgeC) 2.0 else 4.0
            sumW += w
            sumWZ += w * z
            if (design != null) {
                val dh = z - design
                if (dh > 0) cut += w * dh else fill += w * -dh
            }
        }
    }
    val k = s * s / 4.0
    cut *= k
    fill *= k
    val balance = if (sumW > 0) sumWZ / sumW else null
    val areaM2 = (rows - 1) * (cols - 1) * s * s
    val swell = (swellText.toNum() ?: 25.0) / 100.0
    val truck = truckText.toNum() ?: 7.0

    ScreenScaffold(
        "Galian & Timbunan", onBack,
        actions = {
            ExportButton(setOf(com.ukurlahan.pro.data.Module.VOLUME)) {
                val sb = StringBuilder("Grid,${rows}x$cols,jarak ${s} m\nElevasi rencana,${design ?: "-"}\n\n")
                for (r in 0 until rows) {
                    sb.append((0 until cols).joinToString(",") { c -> grid["$r,$c"] ?: "" }).append('\n')
                }
                sb.append("\nGalian (m3),${cut.f(2)}\nTimbunan (m3),${fill.f(2)}\nElevasi seimbang,${balance?.f(3) ?: "-"}\n")
                Store.share(ctx, "galian_timbunan_${Store.stamp()}.csv", sb.toString(), "text/csv")
            }
        },
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SectionCard("Grid lahan") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumField("Baris", rowsText, { rowsText = it }, Modifier.weight(1f))
                    NumField("Kolom", colsText, { colsText = it }, Modifier.weight(1f))
                    NumField("Jarak grid", spacingText, { spacingText = it }, Modifier.weight(1f), "m")
                }
                NumField("Elevasi rencana (tanah jadi)", designText, { designText = it }, Modifier.fillMaxWidth(), "m", signed = true)
                OutlinedButton(onClick = {
                    val pts = LevelingData.load(ctx).points
                    if (pts.isEmpty()) {
                        Toast.makeText(ctx, "Belum ada data di Sipat Datar", Toast.LENGTH_SHORT).show()
                    } else {
                        var i = 0
                        for (r in 0 until rows) for (c in 0 until cols) {
                            if (i < pts.size) grid["$r,$c"] = pts[i].elev.f(3)
                            i++
                        }
                        Toast.makeText(ctx, "Diisi ${minOf(pts.size, rows * cols)} titik (urut baris)", Toast.LENGTH_SHORT).show()
                    }
                }, modifier = Modifier.fillMaxWidth()) { Text("Ambil elevasi dari Sipat Datar") }
                Text(
                    "Isi elevasi tiap titik grid (m). Baris A, B, C… ; kolom 1, 2, 3… Titik diukur di persilangan grid.",
                    fontSize = 12.sp,
                )
            }

            Row(Modifier.horizontalScroll(rememberScrollState())) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (r in 0 until rows) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("${'A' + r}", Modifier.width(18.dp), fontWeight = FontWeight.Bold, color = Accent)
                            for (c in 0 until cols) {
                                val key = "$r,$c"
                                OutlinedTextField(
                                    value = grid[key] ?: "",
                                    onValueChange = { grid[key] = it },
                                    placeholder = { Text("${'A' + r}${c + 1}", fontSize = 11.sp) },
                                    singleLine = true,
                                    textStyle = TextStyle(fontSize = 13.sp),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                                    modifier = Modifier.width(84.dp),
                                )
                            }
                        }
                    }
                }
            }

            SectionCard("Hasil") {
                if (missing > 0) Text("$missing titik belum diisi", color = Warn, fontSize = 12.sp)
                InfoRow("Luas grid", formatArea(areaM2))
                balance?.let {
                    InfoRow("Elevasi seimbang (gali = timbun)", "${it.f(3)} m", Accent)
                    OutlinedButton(onClick = { designText = it.f(3) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Pakai elevasi seimbang sebagai rencana")
                    }
                }
                if (design != null) {
                    InfoRow("Volume galian (padat)", "${cut.f(2)} m³", Bad)
                    InfoRow("Volume timbunan (padat)", "${fill.f(2)} m³", Info)
                    InfoRow("Selisih (galian − timbunan)", "${(cut - fill).signed(2)} m³")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NumField("Faktor kembang", swellText, { swellText = it }, Modifier.weight(1f), "%")
                        NumField("Kapasitas truk", truckText, { truckText = it }, Modifier.weight(1f), "m³")
                    }
                    val loose = cut * (1 + swell)
                    InfoRow("Galian lepas (diangkut)", "${loose.f(2)} m³")
                    if (truck > 0) InfoRow("Perkiraan ritase truk", "${ceil(loose / truck).toInt()} rit")
                    val net = cut - fill
                    Text(
                        if (net >= 0) "Sisa tanah galian ±${net.f(1)} m³ (padat) perlu dibuang."
                        else "Kekurangan tanah timbun ±${(-net).f(1)} m³ (padat) perlu didatangkan.",
                        fontSize = 13.sp,
                    )
                }
                Text(
                    "Metode grid: bobot sudut 1, tepi 2, tengah 4. Volume = jarak² / 4 × Σ(bobot × tebal).",
                    fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// =====================================================================
// KEMIRINGAN
// =====================================================================
@Composable
fun SlopeScreen(onBack: () -> Unit) {
    var dhText by remember { mutableStateOf("0.5") }
    var distText by remember { mutableStateOf("50") }
    var pctText by remember { mutableStateOf("1") }
    var dist2Text by remember { mutableStateOf("50") }
    var startElevText by remember { mutableStateOf("100") }

    ScreenScaffold("Kemiringan", onBack) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SectionCard("Dari beda tinggi & jarak") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumField("Beda tinggi", dhText, { dhText = it }, Modifier.weight(1f), "m", signed = true)
                    NumField("Jarak datar", distText, { distText = it }, Modifier.weight(1f), "m")
                }
                val dh = dhText.toNum()
                val d = distText.toNum()
                if (dh != null && d != null && d > 0) {
                    val g = dh / d
                    InfoRow("Kemiringan", "${(g * 100).f(3)} %", Accent)
                    InfoRow("Sudut", "${Math.toDegrees(atan(g)).f(3)}°")
                    InfoRow("Perbandingan", if (abs(g) > 1e-9) "1 : ${(1 / abs(g)).f(1)}" else "datar")
                    InfoRow("Per meter", "${(g * 1000).f(1)} mm/m")
                }
            }
            SectionCard("Dari kemiringan rencana") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumField("Kemiringan", pctText, { pctText = it }, Modifier.weight(1f), "%", signed = true)
                    NumField("Jarak", dist2Text, { dist2Text = it }, Modifier.weight(1f), "m")
                }
                NumField("Elevasi awal", startElevText, { startElevText = it }, Modifier.fillMaxWidth(), "m", signed = true)
                val p = pctText.toNum()
                val d = dist2Text.toNum()
                val e0 = startElevText.toNum()
                if (p != null && d != null) {
                    val dh = d * p / 100
                    InfoRow("Beda tinggi", "${dh.signed(3)} m", Accent)
                    InfoRow("Sudut", "${Math.toDegrees(atan(p / 100)).f(3)}°")
                    if (e0 != null) {
                        InfoRow("Elevasi akhir", "${(e0 + dh).f(3)} m")
                        if (d > 0) {
                            val step = when {
                                d <= 20 -> 2.0
                                d <= 100 -> 10.0
                                else -> 25.0
                            }
                            Text("Elevasi per $step m:", fontSize = 12.sp)
                            var x = 0.0
                            while (x <= d + 1e-9) {
                                InfoRow("  ${x.f(0)} m", "${(e0 + x * p / 100).f(3)} m")
                                x += step
                            }
                        }
                    }
                }
            }
            SectionCard("Acuan kemiringan umum") {
                Text(
                    "• Saluran drainase tanah: 0,5 – 2 %\n• Halaman / lantai kerja: 1 – 2 % ke arah buangan\n" +
                        "• Lahan sawah: datar (selisih ≤ 3 cm per petak)\n• Jalan tanah: kemiringan melintang 3 – 4 %\n" +
                        "• Lereng timbunan tanah: 1 : 1,5 – 1 : 2",
                    fontSize = 13.sp,
                )
            }
        }
    }
}

// =====================================================================
// KALIBRASI (uji dua patok / two-peg test)
// =====================================================================
@Composable
fun CalibrationScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val settings = remember { Settings(ctx) }
    var pitchOff by remember { mutableDoubleStateOf(settings.pitchOffset) }
    var a1 by remember { mutableStateOf("") }
    var b1 by remember { mutableStateOf("") }
    var a2 by remember { mutableStateOf("") }
    var b2 by remember { mutableStateOf("") }
    var dA by remember { mutableStateOf("3") }
    var dB by remember { mutableStateOf("33") }

    ScreenScaffold("Kalibrasi", onBack) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SectionCard("Koreksi tersimpan") {
                InfoRow("Garis bidik kamera", "${pitchOff.signed(4)}°")
                InfoRow("Waterpass rebah X / Y", "${settings.flatOffX.signed(2)}° / ${settings.flatOffY.signed(2)}°")
                InfoRow("Waterpass sisi", "${settings.edgeOff.signed(2)}°")
                OutlinedButton(onClick = {
                    settings.pitchOffset = 0.0; pitchOff = 0.0
                    settings.flatOffX = 0.0; settings.flatOffY = 0.0; settings.edgeOff = 0.0
                    Toast.makeText(ctx, "Semua koreksi direset", Toast.LENGTH_SHORT).show()
                }, modifier = Modifier.fillMaxWidth()) { Text("Reset semua koreksi") }
            }

            SectionCard("Uji dua patok (garis bidik kamera)") {
                Text(
                    "Wajib sekali sebelum dipakai Sipat Datar / Total Station. Pasang HP di tripod, pakai rambu ukur.\n\n" +
                        "1. Tancapkan patok A dan B berjarak ±30 m.\n" +
                        "2. Taruh alat TEPAT di tengah A–B. Datarkan (indikator hijau), baca rambu di A (a1) dan B (b1).\n" +
                        "3. Pindah alat ke dekat A (±3 m dari A, segaris A–B). Datarkan lagi, baca rambu A (a2) dan B (b2).\n" +
                        "4. Isi jarak alat ke A dan ke B pada posisi kedua, lalu tekan TERAPKAN.",
                    fontSize = 12.sp,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumField("a1 (di tengah)", a1, { a1 = it }, Modifier.weight(1f), "m")
                    NumField("b1 (di tengah)", b1, { b1 = it }, Modifier.weight(1f), "m")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumField("a2 (dekat A)", a2, { a2 = it }, Modifier.weight(1f), "m")
                    NumField("b2 (dekat A)", b2, { b2 = it }, Modifier.weight(1f), "m")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumField("Jarak ke A", dA, { dA = it }, Modifier.weight(1f), "m")
                    NumField("Jarak ke B", dB, { dB = it }, Modifier.weight(1f), "m")
                }
                val va1 = a1.toNum(); val vb1 = b1.toNum(); val va2 = a2.toNum(); val vb2 = b2.toNum()
                val vdA = dA.toNum(); val vdB = dB.toNum()
                if (va1 != null && vb1 != null && va2 != null && vb2 != null && vdA != null && vdB != null && abs(vdA - vdB) > 0.5) {
                    val dTrue = va1 - vb1
                    val dMeas = va2 - vb2
                    val t = (dMeas - dTrue) / (vdA - vdB)
                    val alpha = Math.toDegrees(atan(t))
                    InfoRow("Beda tinggi A–B benar", "${dTrue.signed(4)} m")
                    InfoRow("Galat garis bidik", "${(t * 1000).signed(2)} mm/m  (${alpha.signed(4)}°)", if (abs(t) < 0.001) Good else Warn)
                    InfoRow("Bacaan B seharusnya", "${(vb2 - vdB * t).f(3)} m")
                    Button(onClick = {
                        val newOff = pitchOff - alpha
                        settings.pitchOffset = newOff
                        pitchOff = newOff
                        Toast.makeText(ctx, "Koreksi garis bidik diterapkan", Toast.LENGTH_SHORT).show()
                    }, modifier = Modifier.fillMaxWidth()) { Text("TERAPKAN KOREKSI") }
                }
                Text("Ulangi uji setelah koreksi: galat < 1 mm/m sudah baik.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            SectionCard("Kalibrasi waterpass") {
                Text("Buka menu Waterpass Digital → tombol Kalibrasi (metode putar 180°).", fontSize = 12.sp)
            }
            SectionCard("Kalibrasi kompas") {
                Text("Gerakkan HP membentuk angka 8 beberapa kali, jauh dari besi, motor, atau mobil.", fontSize = 12.sp)
            }
        }
    }
}

// =====================================================================
// PANDUAN
// =====================================================================
@Composable
fun GuideScreen(onBack: () -> Unit) {
    val sections = listOf(
        "Perlengkapan yang disarankan" to
            "• Tripod HP dengan holder (atau tripod kamera + klem HP)\n• Rambu ukur / meteran yang ditempel tegak di tiang lurus\n" +
            "• Jidar aluminium 2–3 m untuk mode waterpass\n• Patok & benang untuk menandai sudut lahan",
        "Meratakan lahan (Sipat Datar Kamera)" to
            "1. Pasang HP di tripod di tengah lahan, kencangkan.\n2. Lakukan Uji Dua Patok sekali (menu Kalibrasi).\n" +
            "3. Tegakkan rambu di titik acuan (BM). Atur HP sampai indikator HIJAU, baca angka rambu di garis tengah, isi BS, tekan KUNCI HI.\n" +
            "4. Isi elevasi rencana (atau ukur dulu beberapa titik lalu pakai rata-rata).\n" +
            "5. Pindahkan rambu ke titik lain. Aplikasi menampilkan BACAAN TARGET: pekerja menggali/menimbun sampai rambu terbaca angka itu.\n" +
            "6. Jika lahan luas dan alat harus pindah: tekan \"Acuan\" di titik terakhir, pindah alat, bidik titik itu sebagai BS, KUNCI lagi.",
        "Waterpass Digital" to
            "• Rebahkan HP di atas permukaan / jidar. Gelembung bergerak ke sisi yang lebih tinggi.\n" +
            "• KUNCI TITIK: simpan kemiringan di titik pertama, lalu di titik lain layar menunjukkan selisihnya — dipakai untuk menyamakan kemiringan (mis. lantai miring 1%).\n" +
            "• Mode Jidar: isi panjang jidar, aplikasi menghitung beda tinggi ujung dalam mm.\n• Kalibrasi putar 180° menghilangkan kesalahan sensor.",
        "Total Station Kamera" to
            "• Isi tinggi lensa dari tanah. Bidik titik di tanah: jarak dihitung otomatis dari sudut tunduk (cocok untuk tanah datar, ≤ 15 m).\n" +
            "• Untuk tanah berlereng: ukur jarak dengan meteran, pilih \"Jarak meteran\", bidik target setinggi \"tinggi target\" → beda tinggi dihitung.\n" +
            "• Rekam sudut-sudut lahan secara berurutan untuk mendapatkan luas dan peta.\n• Tinggi objek: kunci dasar, lalu bidik puncak.",
        "Luas Lahan Berjalan" to
            "• Berjalan menyusuri batas lahan; di setiap sudut berhenti 5 detik lalu TANDAI SUDUT.\n" +
            "• Atau aktifkan mode otomatis agar titik direkam tiap beberapa meter.\n" +
            "• Hasil dapat diekspor ke KML lalu dibuka di Google Earth.",
        "Proyek & ekspor laporan" to
            "• Menu Proyek & Laporan: buat proyek (mis. Proyek Prigen, Proyek Malang). Semua pengukuran disimpan ke proyek AKTIF.\n" +
            "• Ganti proyek dengan tombol Buka / aktifkan. Nama proyek aktif tampil di bawah judul setiap layar.\n" +
            "• Ikon bagikan di setiap fitur: Ekspor PDF, Excel, Gambar HD (300 dpi), CSV, dan Kirim ke ASA Laporan.\n" +
            "• File otomatis tersimpan di Download/ASA Earthwork (gambar di Pictures/ASA Earthwork).",
        "ASA Laporan (rangkuman otomatis)" to
            "• Pasang aplikasi kedua: ASA Laporan.\n" +
            "• Tekan Kirim ke ASA Laporan → aplikasi terbuka dan membuat kesimpulan, penjelasan, kualitas data, dan rekomendasi.\n" +
            "• Rangkuman dapat dibagikan ke WhatsApp, PDF, atau gambar HD.",
        "Akurasi yang realistis" to
            "• Sensor kemiringan HP setelah kalibrasi: ±0,05–0,1° → ±1–2 cm pada jarak 10–20 m. Jaga jarak bidik ≤ 20 m dan usahakan jarak BS ≈ FS.\n" +
            "• Jarak otomatis dari sudut: makin jauh makin kurang teliti.\n• GPS HP: ±3–5 m di tempat terbuka.\n" +
            "• Untuk pekerjaan legal (sertifikat tanah, batas resmi) tetap gunakan surveyor berlisensi.",
    )
    ScreenScaffold("Panduan", onBack) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            sections.forEach { (t, body) ->
                SectionCard(t) { Text(body, fontSize = 13.sp, lineHeight = 19.sp) }
            }
        }
    }
}

@Suppress("unused")
private fun tanDeg(d: Double) = tan(Math.toRadians(d))
