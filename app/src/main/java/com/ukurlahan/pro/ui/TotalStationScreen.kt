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
import com.ukurlahan.pro.data.Geo
import com.ukurlahan.pro.data.Settings
import com.ukurlahan.pro.data.Store
import com.ukurlahan.pro.data.TsData
import com.ukurlahan.pro.data.TsPoint
import com.ukurlahan.pro.sensor.rememberOrientation
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/**
 * "Total station" sederhana: HP di tripod pada titik berdiri (stasiun).
 * Sudut horizontal = kompas (azimut), sudut vertikal = sensor gravitasi.
 * Jarak:
 *  - OTOMATIS: dari tinggi alat & sudut tunduk ke titik di tanah (anggap tanah datar)
 *  - METERAN : jarak datar diisi manual → beda tinggi dihitung trigonometris
 */
@Composable
fun TotalStationScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val settings = remember { Settings(ctx) }
    val o by rememberOrientation(40)
    val (camOk, reqCam) = rememberPermissions(listOf(Manifest.permission.CAMERA))
    val saved = remember { TsData.load(ctx) }

    var hiText by remember { mutableStateOf(saved.hi.f(3)) }
    var htText by remember { mutableStateOf(saved.ht.f(3)) }
    var stElevText by remember { mutableStateOf(saved.stElev.f(3)) }
    var mode by remember { mutableIntStateOf(0) }
    var distText by remember { mutableStateOf("") }
    var nameText by remember { mutableStateOf("") }
    val points = remember { mutableStateListOf<TsPoint>().also { it.addAll(saved.points) } }
    var objBase by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var objHeight by remember { mutableStateOf<Double?>(null) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var maxZoom by remember { mutableFloatStateOf(8f) }
    var askReset by remember { mutableStateOf(false) }

    val pitch = o.cameraPitch - settings.pitchOffset
    val hiV = hiText.toNum() ?: 1.5
    val htV = htText.toNum() ?: 0.0
    val stElev = stElevText.toNum() ?: 0.0

    val liveD: Double? = if (mode == 0) {
        if (pitch < -0.5 && hiV - htV > 0) (hiV - htV) / tan(Math.toRadians(-pitch)) else null
    } else distText.toNum()
    val liveDh: Double? = if (mode == 0) {
        if (liveD != null) 0.0 else null
    } else liveD?.let { hiV + it * tan(Math.toRadians(pitch)) - htV }

    LaunchedEffect(hiText, htText, stElevText, points.size) {
        TsData(hiV, htV, stElev, points.toList()).save(ctx)
    }

    fun toast(s: String) = Toast.makeText(ctx, s, Toast.LENGTH_SHORT).show()

    ScreenScaffold(
        "Total Station Kamera", onBack,
        actions = {
            ExportButton(setOf(com.ukurlahan.pro.data.Module.TOTAL_STATION)) { exportTs(ctx, hiV, stElev, points) }
            IconButton(onClick = { askReset = true }) { Icon(Icons.Default.Refresh, "Baru") }
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            Box(Modifier.fillMaxWidth().weight(0.45f).background(Color.Black)) {
                if (camOk) {
                    CameraPreview(Modifier.fillMaxSize(), zoom) { maxZoom = it }
                } else {
                    Button(onClick = reqCam, modifier = Modifier.align(Alignment.Center)) { Text("Izinkan kamera") }
                }
                Crosshair(o.horizonScreenDeg, Accent)
                Column(Modifier.align(Alignment.TopStart).padding(6.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    OverlayText("Az  ${o.azimuth.f(1)}°", color = if (o.hasCompass) Color.White else Bad)
                    OverlayText("V   ${pitch.signed(2)}°")
                    OverlayText("D   ${liveD?.let { "${it.f(2)} m" } ?: "—"}", color = Accent)
                    OverlayText("Δh  ${liveDh?.let { "${it.signed(3)} m" } ?: "—"}", color = Accent)
                }
                if (o.spread > 0.3) {
                    OverlayText("Tahan stabil…", modifier = Modifier.align(Alignment.TopEnd).padding(6.dp), color = Warn)
                }
            }
            ZoomBar(zoom, maxZoom) { zoom = it }

            LazyColumn(
                Modifier.weight(0.55f).fillMaxWidth(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    SectionCard("Alat & mode jarak") {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            NumField("Tinggi lensa", hiText, { hiText = it }, Modifier.weight(1f), "m")
                            NumField("Tinggi target", htText, { htText = it }, Modifier.weight(1f), "m")
                            NumField("Elev. stasiun", stElevText, { stElevText = it }, Modifier.weight(1f), "m", signed = true)
                        }
                        ToggleRow(listOf("Jarak otomatis", "Jarak meteran"), mode) { mode = it }
                        if (mode == 0) {
                            Text(
                                "Bidik titik target (tinggi target dari tanah) – jarak dihitung dari sudut tunduk. Paling akurat untuk jarak ≤ 15 m di tanah relatif datar.",
                                fontSize = 12.sp,
                            )
                        } else {
                            NumField("Jarak datar ke target", distText, { distText = it }, Modifier.fillMaxWidth(), "m")
                            Text("Beda tinggi = tinggi lensa + D·tan(V) − tinggi target", fontSize = 12.sp)
                        }
                    }
                }
                item {
                    SectionCard("Rekam titik") {
                        OutlinedTextField(
                            nameText, { nameText = it }, label = { Text("Nama titik", fontSize = 12.sp) },
                            placeholder = { Text("T${points.size + 1}") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        )
                        Button(
                            onClick = {
                                val d = liveD
                                val dh = liveDh
                                if (d == null || dh == null) {
                                    toast(if (mode == 0) "Arahkan kamera menunduk ke titik di tanah" else "Isi jarak meteran")
                                } else {
                                    val az = Math.toRadians(o.azimuth)
                                    points.add(
                                        TsPoint(
                                            nameText.ifBlank { "T${points.size + 1}" }, o.azimuth, pitch, d, dh,
                                            d * sin(az), d * cos(az), stElev + dh,
                                        )
                                    )
                                    nameText = ""
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("📍 REKAM TITIK") }
                    }
                }
                item {
                    SectionCard("Tinggi objek (pohon, tiang, tebing)") {
                        val base = objBase
                        if (base == null) {
                            Text("1) Bidik DASAR objek, tekan tombol.", fontSize = 12.sp)
                            Button(onClick = {
                                val d = liveD
                                if (d == null) toast("Jarak belum terbaca") else { objBase = d to pitch; objHeight = null }
                            }, modifier = Modifier.fillMaxWidth()) { Text("Kunci dasar objek") }
                        } else {
                            Text("2) Bidik PUNCAK objek. Jarak terkunci ${base.first.f(2)} m", fontSize = 12.sp)
                            val h = base.first * (tan(Math.toRadians(pitch)) - tan(Math.toRadians(base.second)))
                            InfoRow("Tinggi (langsung)", "${h.f(2)} m", Accent)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { objHeight = h }, modifier = Modifier.weight(1f)) { Text("Kunci puncak") }
                                OutlinedButton(onClick = { objBase = null; objHeight = null }, modifier = Modifier.weight(1f)) { Text("Ulang") }
                            }
                        }
                        objHeight?.let { InfoRow("Hasil tinggi objek", "${it.f(2)} m", Good) }
                    }
                }
                if (points.isNotEmpty()) {
                    item {
                        val xy = points.map { it.x to it.y }
                        SectionCard("Peta titik (stasiun = pusat azimut)") {
                            PolygonView(xy, Modifier.fillMaxWidth().height(220.dp), labels = points.map { it.name }, closed = points.size >= 3)
                            if (points.size >= 3) {
                                InfoRow("Luas poligon", formatArea(Geo.area(xy)), Accent)
                                InfoRow("Keliling", "${Geo.perimeter(xy).f(2)} m")
                            }
                            val zs = points.map { it.z }
                            InfoRow("Elevasi min / maks", "${zs.min().f(3)} / ${zs.max().f(3)}")
                        }
                    }
                }
                items(points.reversed()) { p ->
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(p.name, fontWeight = FontWeight.Bold)
                                Text(
                                    "Az ${p.az.f(1)}°  D ${p.dist.f(2)} m  Δh ${p.dh.signed(3)}\nX ${p.x.f(2)}  Y ${p.y.f(2)}  Z ${p.z.f(3)}",
                                    fontSize = 12.sp, fontFamily = FontFamily.Monospace,
                                )
                            }
                            IconButton(onClick = { points.remove(p) }) { Icon(Icons.Default.Delete, "Hapus") }
                        }
                    }
                }
                item {
                    Text(
                        "Tips: jauhkan HP dari besi/kendaraan, kalibrasi kompas dengan gerakan angka 8. Azimut memakai utara magnetis.",
                        fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    if (askReset) {
        AlertDialog(
            onDismissRequest = { askReset = false },
            title = { Text("Hapus semua titik?") },
            confirmButton = { Button(onClick = { points.clear(); askReset = false }) { Text("Hapus") } },
            dismissButton = { TextButton(onClick = { askReset = false }) { Text("Batal") } },
        )
    }
}

private fun exportTs(ctx: android.content.Context, hi: Double, stElev: Double, points: List<TsPoint>) {
    val sb = StringBuilder()
    sb.append("Tinggi alat,${hi.f(3)}\nElevasi stasiun,${stElev.f(3)}\n\n")
    sb.append("No,Titik,Azimut (deg),Sudut V (deg),Jarak datar (m),Beda tinggi (m),X Timur (m),Y Utara (m),Z (m)\n")
    points.forEachIndexed { i, p ->
        sb.append("${i + 1},${p.name},${p.az.f(2)},${p.pitch.f(3)},${p.dist.f(3)},${p.dh.f(3)},${p.x.f(3)},${p.y.f(3)},${p.z.f(3)}\n")
    }
    if (points.size >= 3) {
        val xy = points.map { it.x to it.y }
        sb.append("\nLuas (m2),${Geo.area(xy).f(3)}\nKeliling (m),${Geo.perimeter(xy).f(3)}\n")
    }
    Store.share(ctx, "total_station_${Store.stamp()}.csv", sb.toString(), "text/csv")
}
