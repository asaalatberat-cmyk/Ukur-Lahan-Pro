package com.ukurlahan.pro.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.provider.Settings as AndroidSettings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ukurlahan.pro.data.AreaStore
import com.ukurlahan.pro.data.Geo
import com.ukurlahan.pro.data.GeoPt
import com.ukurlahan.pro.data.Store
import java.util.Locale

/**
 * Ukur luas dengan berjalan mengelilingi batas lahan.
 * Kamera dipakai untuk membidik/melihat patok sudut, posisi dari GPS.
 * - TANDAI SUDUT: posisi dirata-rata 5 detik terakhir (lebih akurat)
 * - OTOMATIS   : titik ditambah tiap N meter selama berjalan
 */
@SuppressLint("MissingPermission")
@Composable
fun AreaWalkScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val (perm, reqPerm) = rememberPermissionMap(
        listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.CAMERA,
        )
    )
    val locOk = perm[Manifest.permission.ACCESS_FINE_LOCATION] == true
    val camOk = perm[Manifest.permission.CAMERA] == true
    val saved = remember { AreaStore.load(ctx) }
    var name by remember { mutableStateOf(saved.first) }
    val pts = remember { mutableStateListOf<GeoPt>().also { it.addAll(saved.second) } }
    var fix by remember { mutableStateOf<Location?>(null) }
    val recent = remember { ArrayList<Location>() }
    var auto by remember { mutableStateOf(false) }
    var stepText by remember { mutableStateOf("3") }
    var maxAccText by remember { mutableStateOf("8") }
    var showCam by remember { mutableStateOf(true) }
    var askReset by remember { mutableStateOf(false) }

    LaunchedEffect(name, pts.size) { AreaStore.save(ctx, name, pts.toList()) }

    val lm = remember { ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager }
    val gpsOn = remember(fix) {
        try { lm.isProviderEnabled(LocationManager.GPS_PROVIDER) } catch (_: Exception) { false }
    }

    fun addPoint(p: GeoPt) {
        pts.add(p)
    }

    DisposableEffect(locOk) {
        val listener = object : LocationListener {
            override fun onLocationChanged(l: Location) {
                fix = l
                recent.add(l)
                val cutoff = l.time - 15_000
                recent.removeAll { it.time < cutoff }
                if (auto) {
                    val step = stepText.toNum() ?: 3.0
                    val maxAcc = maxAccText.toNum() ?: 8.0
                    if (l.accuracy <= maxAcc) {
                        val last = pts.lastOrNull()
                        val dist = if (last == null) Float.MAX_VALUE else {
                            val r = FloatArray(1)
                            Location.distanceBetween(last.lat, last.lon, l.latitude, l.longitude, r)
                            r[0]
                        }
                        if (dist >= step) addPoint(GeoPt(l.latitude, l.longitude, l.accuracy.toDouble(), l.altitude))
                    }
                }
            }

            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}

            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        }
        if (locOk) {
            try {
                lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, listener, Looper.getMainLooper())
            } catch (_: Exception) {
            }
        }
        onDispose { lm.removeUpdates(listener) }
    }

    /** Rata-rata posisi 5 detik terakhir dengan bobot 1/akurasi². */
    fun averagedFix(): GeoPt? {
        val last = fix ?: return null
        val win = recent.filter { it.time >= last.time - 5_000 }
        if (win.isEmpty()) return GeoPt(last.latitude, last.longitude, last.accuracy.toDouble(), last.altitude)
        var w = 0.0; var la = 0.0; var lo = 0.0; var al = 0.0
        win.forEach {
            val a = it.accuracy.toDouble().coerceAtLeast(1.0)
            val wi = 1.0 / (a * a)
            w += wi; la += it.latitude * wi; lo += it.longitude * wi; al += it.altitude * wi
        }
        val acc = win.minOf { it.accuracy }.toDouble() / kotlin.math.sqrt(win.size.toDouble())
        return GeoPt(la / w, lo / w, acc, al / w)
    }

    val xy = Geo.project(pts)
    val area = Geo.area(xy)
    val perim = Geo.perimeter(xy)
    val acc = fix?.accuracy?.toDouble()
    val accColor = when {
        acc == null -> Bad
        acc <= 3 -> Good
        acc <= 8 -> Warn
        else -> Bad
    }

    ScreenScaffold(
        "Luas Lahan Berjalan", onBack,
        actions = {
            ExportButton(setOf(com.ukurlahan.pro.data.Module.AREA), csvLabel = "🧾 Ekspor CSV / KML") { exportArea(ctx, name, pts, area, perim) }
            IconButton(onClick = { askReset = true }) { Icon(Icons.Default.Refresh, "Baru") }
        },
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (!locOk) {
                SectionCard("Izin lokasi diperlukan") {
                    Text("Aktifkan izin lokasi TEPAT (precise) agar GPS bisa dipakai.")
                    Button(onClick = reqPerm) { Text("Izinkan lokasi") }
                }
            } else if (!gpsOn) {
                SectionCard("GPS mati") {
                    Button(onClick = { ctx.startActivity(Intent(AndroidSettings.ACTION_LOCATION_SOURCE_SETTINGS)) }) {
                        Text("Buka pengaturan lokasi")
                    }
                }
            }

            // ---- Kamera bidik + info GPS
            Box(Modifier.fillMaxWidth().height(if (showCam) 220.dp else 90.dp).background(Color.Black)) {
                if (showCam && camOk) {
                    CameraPreview(Modifier.fillMaxSize())
                    Crosshair(0.0, Accent.copy(alpha = 0.7f))
                } else if (showCam) {
                    Button(onClick = reqPerm, modifier = Modifier.align(Alignment.Center)) { Text("Izinkan kamera") }
                }
                Column(Modifier.align(Alignment.TopStart).padding(6.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    OverlayText(
                        if (acc == null) "Menunggu sinyal GPS…" else "Akurasi ±${acc.f(1)} m",
                        color = accColor,
                    )
                    fix?.let {
                        OverlayText(String.format(Locale.US, "%.6f, %.6f", it.latitude, it.longitude))
                    }
                    OverlayText("Titik: ${pts.size}   Luas: ${formatHa(area)}", color = Accent)
                }
                TextButton(onClick = { showCam = !showCam }, modifier = Modifier.align(Alignment.TopEnd)) {
                    Text(if (showCam) "Sembunyikan kamera" else "Tampilkan kamera", fontSize = 11.sp)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        val p = averagedFix()
                        if (p == null) Toast.makeText(ctx, "Belum ada sinyal GPS", Toast.LENGTH_SHORT).show()
                        else addPoint(p)
                    },
                    modifier = Modifier.weight(1.4f).height(56.dp),
                ) { Text("📍 TANDAI SUDUT", fontSize = 15.sp) }
                OutlinedButton(
                    onClick = { if (pts.isNotEmpty()) pts.removeAt(pts.size - 1) },
                    modifier = Modifier.weight(1f).height(56.dp),
                ) { Text("↶ Batal") }
            }

            SectionCard("Mode otomatis (rekam jejak berjalan)") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Tambah titik otomatis saat berjalan", Modifier.weight(1f), fontSize = 14.sp)
                    Switch(checked = auto, onCheckedChange = { auto = it })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumField("Setiap", stepText, { stepText = it }, Modifier.weight(1f), "m")
                    NumField("Akurasi maks", maxAccText, { maxAccText = it }, Modifier.weight(1f), "m")
                }
            }

            SectionCard("Hasil") {
                OutlinedTextField(name, { name = it }, label = { Text("Nama lahan") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                PolygonView(xy, Modifier.fillMaxWidth().height(260.dp), closed = pts.size >= 3)
                InfoRow("Luas", formatHa(area), Accent)
                InfoRow("", "= ${area.f(2)} m²  (1 ha = 10.000 m²)")
                InfoRow("Keliling", "${perim.f(2)} m")
                if (pts.size in 3..12) {
                    Geo.sides(xy).forEachIndexed { i, s ->
                        InfoRow("Sisi ${i + 1}–${(i + 1) % pts.size + 1}", "${s.f(2)} m")
                    }
                }
                if (pts.isNotEmpty()) {
                    val worst = pts.maxOf { it.acc }
                    Text(
                        "Perkiraan ketelitian: ±${(perim * worst / 2 / 10000).f(4)} ha (dari akurasi GPS ±${worst.f(1)} m)",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Text(
                "Tips: di setiap sudut lahan, berdiri tepat di atas patok, diamkan HP 5 detik, lalu tekan TANDAI SUDUT. " +
                    "Lakukan di tempat terbuka. GPS HP biasa ±3–5 m, sehingga cocok untuk lahan > 0,05 ha (500 m²). " +
                    "Untuk lahan kecil gunakan Total Station Kamera.",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (askReset) {
        AlertDialog(
            onDismissRequest = { askReset = false },
            title = { Text("Mulai lahan baru?") },
            text = { Text("Semua titik akan dihapus.") },
            confirmButton = { Button(onClick = { pts.clear(); auto = false; askReset = false }) { Text("Hapus") } },
            dismissButton = { TextButton(onClick = { askReset = false }) { Text("Batal") } },
        )
    }
}

private fun exportArea(ctx: Context, name: String, pts: List<GeoPt>, area: Double, perim: Double) {
    if (pts.isEmpty()) {
        Toast.makeText(ctx, "Belum ada titik", Toast.LENGTH_SHORT).show(); return
    }
    val safe = name.replace(Regex("[^A-Za-z0-9_-]"), "_")
    // CSV
    val csv = StringBuilder("Nama,$name\nLuas (ha),${(area / 10000).f(5)}\nLuas (m2),${area.f(3)}\nKeliling (m),${perim.f(3)}\n\n")
    csv.append("No,Lintang,Bujur,Akurasi (m),Tinggi GPS (m)\n")
    pts.forEachIndexed { i, p ->
        csv.append("${i + 1},${String.format(Locale.US, "%.8f", p.lat)},${String.format(Locale.US, "%.8f", p.lon)},${p.acc.f(1)},${p.alt.f(1)}\n")
    }
    // KML (bisa dibuka di Google Earth / Maps)
    val coords = (pts + pts.first()).joinToString(" ") {
        String.format(Locale.US, "%.8f,%.8f,0", it.lon, it.lat)
    }
    val kml = """<?xml version="1.0" encoding="UTF-8"?>
<kml xmlns="http://www.opengis.net/kml/2.2"><Document><name>$name</name>
<Style id="s"><LineStyle><color>ff00b3ff</color><width>3</width></LineStyle><PolyStyle><color>5500b3ff</color></PolyStyle></Style>
<Placemark><name>$name</name><description>Luas ${(area / 10000).f(4)} ha (${area.f(2)} m2), keliling ${perim.f(2)} m</description><styleUrl>#s</styleUrl>
<Polygon><outerBoundaryIs><LinearRing><coordinates>$coords</coordinates></LinearRing></outerBoundaryIs></Polygon></Placemark>
</Document></kml>"""
    val choices = arrayOf("CSV (Excel)", "KML (Google Earth)")
    android.app.AlertDialog.Builder(ctx)
        .setTitle("Format ekspor")
        .setItems(choices) { _, which ->
            if (which == 0) Store.share(ctx, "${safe}_${Store.stamp()}.csv", csv.toString(), "text/csv")
            else Store.share(ctx, "${safe}_${Store.stamp()}.kml", kml, "application/vnd.google-earth.kml+xml")
        }
        .show()
}
