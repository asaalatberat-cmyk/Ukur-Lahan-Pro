package com.ukurlahan.pro.ui

import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ukurlahan.pro.data.Settings
import com.ukurlahan.pro.sensor.rememberOrientation
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.tan

/**
 * Waterpass / nivo digital.
 * - Mode REBAH: HP diletakkan telentang (gelembung bulat, 2 sumbu)
 * - Mode SISI: HP berdiri di sisi panjang di atas jidar/balok (1 sumbu)
 * - KUNCI: simpan kemiringan saat ini sebagai acuan; di titik lain yang
 *   ditampilkan adalah selisih terhadap acuan tsb.
 */
@Composable
fun LevelScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val settings = remember { Settings(ctx) }
    val o by rememberOrientation(30)

    var offX by remember { mutableDoubleStateOf(settings.flatOffX) }
    var offY by remember { mutableDoubleStateOf(settings.flatOffY) }
    var offE by remember { mutableDoubleStateOf(settings.edgeOff) }
    var lockAlong by remember { mutableStateOf<Double?>(null) }
    var lockCross by remember { mutableStateOf<Double?>(null) }
    var tolText by remember { mutableStateOf("0.10") }
    var barText by remember { mutableStateOf("2.0") }
    var sound by remember { mutableStateOf(true) }
    var calStep by remember { mutableIntStateOf(0) }
    var cal1 by remember { mutableStateOf(Pair(0.0, 0.0)) }

    val flat = o.isFlat
    val edge = !flat && o.isEdge
    val rawAlong = if (flat) o.flatTiltY else o.edgeTilt
    val rawCross = if (flat) o.flatTiltX else 0.0
    val along = rawAlong - (if (flat) offY else offE) - (lockAlong ?: 0.0)
    val cross = rawCross - (if (flat) offX else 0.0) - (lockCross ?: 0.0)
    val tol = tolText.toNum() ?: 0.1
    val valid = flat || edge
    val isLevel = valid && abs(along) <= tol && abs(cross) <= tol
    val color = if (!valid) Bad else levelColor(hypot(along, cross), tol, tol * 5)

    // Bunyi "bip" saat datar
    val tone = remember {
        try { ToneGenerator(AudioManager.STREAM_MUSIC, 70) } catch (_: Exception) { null }
    }
    DisposableEffect(Unit) { onDispose { tone?.release() } }
    LaunchedEffect(isLevel, sound) {
        while (isLevel && sound) {
            tone?.startTone(ToneGenerator.TONE_PROP_BEEP, 70)
            delay(700)
        }
    }

    ScreenScaffold("Waterpass Digital", onBack) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                when {
                    flat -> "Mode REBAH (HP telentang)"
                    edge -> "Mode SISI (HP berdiri di sisi panjang)"
                    else -> "Letakkan HP rebah atau berdiri di sisi panjangnya"
                } + if (lockAlong != null) "  •  🔒 relatif ke titik kunci" else "",
                color = if (valid) Info else Bad, fontSize = 13.sp,
            )

            // ---- Gelembung
            Box(Modifier.fillMaxWidth().height(260.dp), contentAlignment = Alignment.Center) {
                if (flat) BullsEye(cross, along, color) else TubeLevel(along, color)
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                AxisValue("Depan-belakang", along)
                if (flat) AxisValue("Kiri-kanan", cross)
            }
            Text(
                if (isLevel) "✓ DATAR" else if (valid) "Belum datar" else "—",
                color = color, fontSize = 28.sp, fontWeight = FontWeight.Black,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        if (lockAlong == null) {
                            lockAlong = rawAlong - (if (flat) offY else offE)
                            lockCross = rawCross - (if (flat) offX else 0.0)
                        } else {
                            lockAlong = null; lockCross = null
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) { Text(if (lockAlong == null) "🔒 Kunci titik ini" else "🔓 Lepas kunci") }
                OutlinedButton(onClick = { calStep = 1 }, modifier = Modifier.weight(1f)) { Text("Kalibrasi") }
            }

            SectionCard("Mode Jidar / Balok Lurus") {
                Text(
                    "Letakkan HP di atas jidar (sejajar panjangnya). Aplikasi menghitung beda tinggi antar ujung jidar.",
                    fontSize = 12.sp,
                )
                NumField("Panjang jidar", barText, { barText = it }, Modifier.fillMaxWidth(), suffix = "m")
                val L = barText.toNum() ?: 0.0
                val dhMm = L * 1000 * tan(Math.toRadians(along))
                InfoRow(
                    "Beda tinggi ujung",
                    "${abs(dhMm).f(1)} mm",
                    levelColor(dhMm, 2.0, 5.0),
                )
                Text(
                    when {
                        abs(dhMm) < 0.5 -> "Kedua ujung sama tinggi ✓"
                        dhMm > 0 -> "Ujung ATAS HP lebih tinggi → turunkan ujung itu / tambah tanah di ujung bawah"
                        else -> "Ujung BAWAH HP lebih tinggi → turunkan ujung itu / tambah tanah di ujung atas"
                    },
                    fontSize = 13.sp,
                )
            }

            SectionCard("Pengaturan") {
                NumField("Toleransi datar", tolText, { tolText = it }, Modifier.fillMaxWidth(), suffix = "°")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Bunyi bip saat datar", Modifier.weight(1f))
                    Switch(checked = sound, onCheckedChange = { sound = it })
                }
                Text(
                    "Koreksi tersimpan: rebah X ${offX.signed(2)}°, Y ${offY.signed(2)}°, sisi ${offE.signed(2)}°",
                    fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    // ---- Dialog kalibrasi metode putar 180°
    if (calStep > 0) {
        AlertDialog(
            onDismissRequest = { calStep = 0 },
            title = { Text("Kalibrasi (putar 180°)") },
            text = {
                Text(
                    if (calStep == 1)
                        "1. Letakkan HP di permukaan yang stabil (tidak harus datar sempurna). Tandai posisinya dengan pensil.\n2. Diamkan 3 detik lalu tekan REKAM 1."
                    else
                        "3. Putar HP 180° (ujung atas jadi di bawah) di posisi yang SAMA persis.\n4. Diamkan 3 detik lalu tekan REKAM 2."
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (calStep == 1) {
                        cal1 = Pair(rawAlong, rawCross)
                        calStep = 2
                    } else {
                        val a = (cal1.first + rawAlong) / 2
                        val c = (cal1.second + rawCross) / 2
                        if (flat) {
                            offY = a; offX = c
                            settings.flatOffY = a; settings.flatOffX = c
                        } else {
                            offE = a
                            settings.edgeOff = a
                        }
                        lockAlong = null; lockCross = null
                        calStep = 0
                    }
                }) { Text(if (calStep == 1) "REKAM 1" else "REKAM 2") }
            },
            dismissButton = {
                TextButton(onClick = {
                    if (flat) {
                        offX = 0.0; offY = 0.0; settings.flatOffX = 0.0; settings.flatOffY = 0.0
                    } else {
                        offE = 0.0; settings.edgeOff = 0.0
                    }
                    calStep = 0
                }) { Text("Reset koreksi") }
            },
        )
    }
}

@Composable
private fun AxisValue(label: String, deg: Double) {
    val t = tan(Math.toRadians(deg))
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("${deg.signed(2)}°", fontSize = 26.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        Text("${(t * 100).signed(2)} %  •  ${(t * 1000).signed(1)} mm/m", fontSize = 12.sp, fontFamily = FontFamily.Monospace)
    }
}

/** Nivo kotak/bulat: gelembung bergerak ke sisi yang lebih tinggi. */
@Composable
private fun BullsEye(crossDeg: Double, alongDeg: Double, color: Color) {
    Canvas(Modifier.size(250.dp)) {
        val r = size.minDimension / 2 - 6f
        val c = center
        val maxDeg = 4.0
        drawCircle(Color(0xFF1E3B2A), r, c)
        drawCircle(Color(0xFF3A6B4C), r, c, style = Stroke(4f))
        listOf(0.25, 0.5, 0.75).forEach { drawCircle(Color(0x55FFFFFF), (r * it).toFloat(), c, style = Stroke(2f)) }
        drawLine(Color(0x55FFFFFF), Offset(c.x - r, c.y), Offset(c.x + r, c.y), 2f)
        drawLine(Color(0x55FFFFFF), Offset(c.x, c.y - r), Offset(c.x, c.y + r), 2f)
        var bx = (crossDeg / maxDeg) * r
        var by = -(alongDeg / maxDeg) * r
        val d = hypot(bx, by)
        val lim = r * 0.85
        if (d > lim) { bx = bx / d * lim; by = by / d * lim }
        val bc = Offset(c.x + bx.toFloat(), c.y + by.toFloat())
        drawCircle(color.copy(alpha = 0.85f), r * 0.13f, bc)
        drawCircle(Color.White, r * 0.13f, bc, style = Stroke(3f))
        drawCircle(Color.White, r * 0.15f, c, style = Stroke(2f))
    }
}

/** Nivo tabung (vertikal di layar = sepanjang badan HP). */
@Composable
private fun TubeLevel(alongDeg: Double, color: Color) {
    Canvas(Modifier.width(90.dp).height(250.dp)) {
        val w = size.width
        val h = size.height
        drawRoundRect(Color(0xFF1E3B2A), cornerRadius = CornerRadius(w / 2, w / 2))
        drawRoundRect(Color(0xFF3A6B4C), cornerRadius = CornerRadius(w / 2, w / 2), style = Stroke(4f))
        val cy = h / 2
        val gap = w * 0.4f
        drawLine(Color.White, Offset(0f, cy - gap), Offset(w, cy - gap), 2f)
        drawLine(Color.White, Offset(0f, cy + gap), Offset(w, cy + gap), 2f)
        val maxDeg = 4.0
        val off = (-(alongDeg / maxDeg) * (h / 2 - w / 2)).coerceIn(-(h / 2 - w / 2).toDouble(), (h / 2 - w / 2).toDouble())
        val bc = Offset(w / 2, cy + off.toFloat())
        drawOval(color.copy(alpha = 0.85f), topLeft = Offset(bc.x - w * 0.3f, bc.y - w * 0.35f), size = Size(w * 0.6f, w * 0.7f))
    }
}
