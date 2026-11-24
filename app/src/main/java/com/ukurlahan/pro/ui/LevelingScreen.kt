package com.ukurlahan.pro.ui

import android.Manifest
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ukurlahan.pro.data.LevelingData
import com.ukurlahan.pro.data.LvPoint
import com.ukurlahan.pro.data.Settings
import com.ukurlahan.pro.data.Store
import com.ukurlahan.pro.sensor.rememberOrientation
import kotlin.math.abs
import kotlin.math.tan

/**
 * Sipat datar (waterpass) memakai kamera HP di atas tripod + rambu ukur.
 *
 * 1. Bidik titik acuan (BM) → masukkan bacaan rambu belakang (BS) → KUNCI.
 *    Tinggi garis bidik HI = elevasi acuan + BS. HI ini terkunci, jadi di titik
 *    mana pun garis bidik tetap pada ketinggian yang sama.
 * 2. Bidik titik lain → bacaan muka (FS) → elevasi = HI − FS.
 * 3. Bila diisi elevasi rencana: aplikasi menampilkan BACAAN TARGET di rambu
 *    dan status GALI / TIMBUN di tiap titik.
 */
@Composable
fun LevelingScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val settings = remember { Settings(ctx) }
    val o by rememberOrientation(40)
    val (camOk, reqCam) = rememberPermissions(listOf(Manifest.permission.CAMERA))
    val saved = remember { LevelingData.load(ctx) }

    var refName by remember { mutableStateOf(saved.refName) }
    var refElevText by remember { mutableStateOf(saved.refElev.f(3)) }
    var hi by remember { mutableStateOf(saved.hi) }
    var designText by remember { mutableStateOf(saved.design?.f(3) ?: "") }
    var bsText by remember { mutableStateOf("") }
    var bsDistText by remember { mutableStateOf("") }
    var fsText by remember { mutableStateOf("") }
    var fsDistText by remember { mutableStateOf("") }
    var nameText by remember { mutableStateOf("") }
    val points = remember { mutableStateListOf<LvPoint>().also { it.addAll(saved.points) } }
    var zoom by remember { mutableFloatStateOf(1f) }
    var maxZoom by remember { mutableFloatStateOf(8f) }
    var askReset by remember { mutableStateOf(false) }

    val pitch = o.cameraPitch - settings.pitchOffset
    val design = designText.toNum()
    val pitchColor = levelColor(pitch, 0.05, 0.2)

    LaunchedEffect(refName, refElevText, hi, designText, points.size) {
        LevelingData(refName, refElevText.toNum() ?: 0.0, hi, designText.toNum(), points.toList()).save(ctx)
    }

    /** Koreksi bacaan akibat garis bidik tidak persis datar (butuh jarak ke rambu). */
    fun corrected(raw: Double, dist: Double?): Double =
        if (dist != null && dist > 0) raw - dist * tan(Math.toRadians(pitch)) else raw

    fun toast(s: String) = Toast.makeText(ctx, s, Toast.LENGTH_SHORT).show()

    ScreenScaffold(
        "Sipat Datar Kamera", onBack,
        actions = {
            ExportButton(setOf(com.ukurlahan.pro.data.Module.LEVELING)) {
                exportLeveling(ctx, refName, refElevText, design, points)
            }
            IconButton(onClick = { askReset = true }) { Icon(Icons.Default.Refresh, "Proyek baru") }
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            // ---------------- Kamera + benang silang
            Box(Modifier.fillMaxWidth().weight(0.42f).background(Color.Black)) {
                if (camOk) {
                    CameraPreview(Modifier.fillMaxSize(), zoom) { maxZoom = it }
                } else {
                    Button(onClick = reqCam, modifier = Modifier.align(Alignment.Center)) { Text("Izinkan kamera") }
                }
                Crosshair(o.horizonScreenDeg, pitchColor)
                Column(Modifier.align(Alignment.TopCenter).padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    OverlayText("Sudut bidik ${pitch.signed(2)}°", color = pitchColor, big = true)
                    Spacer(Modifier.height(4.dp))
                    OverlayText(
                        when {
                            abs(pitch) <= 0.05 -> "✓ DATAR – baca rambu di garis tengah"
                            pitch < 0 -> "▲ Angkat bidikan sedikit"
                            else -> "▼ Turunkan bidikan sedikit"
                        },
                        color = pitchColor,
                    )
                }
                Column(Modifier.align(Alignment.BottomStart).padding(6.dp)) {
                    if (hi != null) OverlayText("HI ${hi!!.f(3)} m 🔒", color = Good)
                    if (hi != null && design != null) {
                        Spacer(Modifier.height(4.dp))
                        OverlayText("Target rambu ${(hi!! - design).f(3)} m", color = Accent)
                    }
                }
                OverlayText(
                    "Galat ${(10000 * tan(Math.toRadians(pitch))).signed(1)} mm/10m",
                    modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp),
                    color = pitchColor,
                )
            }
            ZoomBar(zoom, maxZoom) { zoom = it }

            // ---------------- Panel kerja
            LazyColumn(
                Modifier.weight(0.58f).fillMaxWidth(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    SectionCard("1. Kunci tinggi garis bidik (HI)") {
                        if (hi == null) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    refName, { refName = it }, label = { Text("Titik acuan", fontSize = 12.sp) },
                                    singleLine = true, modifier = Modifier.weight(1f),
                                )
                                NumField("Elevasi acuan", refElevText, { refElevText = it }, Modifier.weight(1f), "m", signed = true)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                NumField("Bacaan belakang (BS)", bsText, { bsText = it }, Modifier.weight(1f), "m")
                                NumField("Jarak (opsional)", bsDistText, { bsDistText = it }, Modifier.weight(1f), "m")
                            }
                            Button(
                                onClick = {
                                    val raw = bsText.toNum()
                                    val re = refElevText.toNum()
                                    if (raw == null || re == null) {
                                        toast("Isi elevasi acuan & bacaan BS")
                                    } else {
                                        hi = re + corrected(raw, bsDistText.toNum())
                                        bsText = ""; bsDistText = ""
                                        toast("Tinggi alat terkunci")
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("🔒 KUNCI HI") }
                        } else {
                            InfoRow("Acuan", "$refName = ${refElevText} m")
                            InfoRow("HI (terkunci)", "${hi!!.f(3)} m", Good)
                            Text(
                                "Jangan geser tripod. Bila alat harus dipindah, tekan \"Acuan\" pada salah satu titik di daftar.",
                                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            OutlinedButton(onClick = { hi = null }, modifier = Modifier.fillMaxWidth()) { Text("🔓 Buka kunci / atur ulang HI") }
                        }
                    }
                }
                item {
                    SectionCard("2. Elevasi rencana (target perataan)") {
                        NumField("Elevasi rencana", designText, { designText = it }, Modifier.fillMaxWidth(), "m", signed = true)
                        if (hi != null && design != null) {
                            InfoRow("Bacaan rambu target", "${(hi!! - design).f(3)} m", Accent)
                            Text(
                                "Tanah sudah rata bila rambu terbaca ${(hi!! - design).f(3)} m di semua titik. Bacaan lebih kecil = tanah terlalu tinggi (gali); lebih besar = terlalu rendah (timbun).",
                                fontSize = 12.sp,
                            )
                        }
                    }
                }
                item {
                    SectionCard("3. Bidik titik (bacaan muka / FS)") {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                nameText, { nameText = it }, label = { Text("Nama titik", fontSize = 12.sp) },
                                placeholder = { Text("P${points.size + 1}") }, singleLine = true, modifier = Modifier.weight(1f),
                            )
                            NumField("Bacaan rambu", fsText, { fsText = it }, Modifier.weight(1f), "m")
                        }
                        NumField("Jarak ke rambu (opsional, untuk koreksi sudut)", fsDistText, { fsDistText = it }, Modifier.fillMaxWidth(), "m")
                        val fs = fsText.toNum()
                        if (hi != null && fs != null) {
                            val e = hi!! - corrected(fs, fsDistText.toNum())
                            InfoRow("Elevasi titik", "${e.f(3)} m")
                            if (design != null) StatusLine(e - design)
                        }
                        Button(
                            enabled = hi != null,
                            onClick = {
                                val raw = fsText.toNum()
                                val h = hi
                                if (raw == null || h == null) {
                                    toast("Kunci HI dulu dan isi bacaan rambu")
                                } else {
                                    val d = fsDistText.toNum()
                                    val r = corrected(raw, d)
                                    points.add(
                                        LvPoint(
                                            nameText.ifBlank { "P${points.size + 1}" }, raw, d, pitch, r, h, h - r,
                                            System.currentTimeMillis(),
                                        )
                                    )
                                    nameText = ""; fsText = ""
                                    if (abs(pitch) > 0.2 && d == null) toast("Tersimpan. Bidikan tadi kurang datar (${pitch.signed(2)}°)")
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("SIMPAN TITIK") }
                    }
                }
                if (points.isNotEmpty()) {
                    item {
                        val el = points.map { it.elev }
                        val avg = el.average()
                        SectionCard("Ringkasan ${points.size} titik") {
                            InfoRow("Tertinggi", "${el.max().f(3)} m")
                            InfoRow("Terendah", "${el.min().f(3)} m")
                            InfoRow("Selisih maks", "${((el.max() - el.min()) * 100).f(1)} cm")
                            InfoRow("Rata-rata", "${avg.f(3)} m")
                            OutlinedButton(onClick = { designText = avg.f(3) }, modifier = Modifier.fillMaxWidth()) {
                                Text("Pakai rata-rata sebagai elevasi rencana")
                            }
                        }
                    }
                }
                items(points.reversed()) { p ->
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(p.name, fontWeight = FontWeight.Bold)
                                Text(
                                    "Bacaan ${p.reading.f(3)}  •  Elev ${p.elev.f(3)} m",
                                    fontSize = 12.sp, fontFamily = FontFamily.Monospace,
                                )
                                if (design != null) StatusLine(p.elev - design)
                            }
                            TextButton(onClick = {
                                refName = p.name
                                refElevText = p.elev.f(3)
                                hi = null
                                toast("Pindahkan alat, lalu bidik ${p.name} sebagai BS")
                            }) { Text("Acuan") }
                            IconButton(onClick = { points.remove(p) }) { Icon(Icons.Default.Delete, "Hapus") }
                        }
                    }
                }
            }
        }
    }

    if (askReset) {
        AlertDialog(
            onDismissRequest = { askReset = false },
            title = { Text("Proyek baru?") },
            text = { Text("Semua titik & kunci HI akan dihapus. Ekspor dulu bila perlu.") },
            confirmButton = {
                Button(onClick = {
                    points.clear(); hi = null; askReset = false
                }) { Text("Hapus") }
            },
            dismissButton = { TextButton(onClick = { askReset = false }) { Text("Batal") } },
        )
    }
}

@Composable
fun StatusLine(diff: Double) {
    val cm = abs(diff) * 100
    val (txt, col) = when {
        abs(diff) <= 0.005 -> "✓ SUDAH RATA (±${cm.f(1)} cm)" to Good
        diff > 0 -> "⛏ GALI ${cm.f(1)} cm" to Bad
        else -> "🧱 TIMBUN ${cm.f(1)} cm" to Info
    }
    Text(txt, color = col, fontWeight = FontWeight.Bold, fontSize = 14.sp)
}

private fun exportLeveling(
    ctx: android.content.Context,
    refName: String,
    refElev: String,
    design: Double?,
    points: List<LvPoint>,
) {
    val sb = StringBuilder()
    sb.append("Acuan,$refName,$refElev\n")
    sb.append("Elevasi rencana,${design?.f(3) ?: "-"}\n\n")
    sb.append("No,Titik,Bacaan mentah (m),Jarak (m),Sudut (deg),Bacaan terkoreksi (m),HI (m),Elevasi (m),Selisih rencana (m),Status\n")
    points.forEachIndexed { i, p ->
        val diff = design?.let { p.elev - it }
        val status = when {
            diff == null -> "-"
            abs(diff) <= 0.005 -> "RATA"
            diff > 0 -> "GALI"
            else -> "TIMBUN"
        }
        sb.append(
            "${i + 1},${p.name},${p.rawReading.f(3)},${p.dist?.f(2) ?: ""},${p.pitch.f(3)},${p.reading.f(3)}," +
                "${p.hi.f(3)},${p.elev.f(3)},${diff?.f(3) ?: ""},$status\n"
        )
    }
    Store.share(ctx, "sipat_datar_${Store.stamp()}.csv", sb.toString(), "text/csv")
}
