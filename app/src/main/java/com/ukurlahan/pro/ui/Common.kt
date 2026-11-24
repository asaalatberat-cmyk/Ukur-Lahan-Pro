package com.ukurlahan.pro.ui

import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

val Accent = Color(0xFFFFB300)
val Good = Color(0xFF4CE07A)
val Warn = Color(0xFFFFD54F)
val Bad = Color(0xFFFF5252)
val Info = Color(0xFF4FC3F7)

fun Double.f(d: Int): String = String.format(Locale.US, "%.${d}f", this)
fun Double.signed(d: Int): String = (if (this >= 0) "+" else "") + f(d)
fun String.toNum(): Double? = trim().replace(',', '.').toDoubleOrNull()

/** Luas utama dalam hektar (1 ha = 10.000 m²), m² sebagai keterangan. */
fun formatArea(m2: Double): String = "${(m2 / 10000).f(4)} ha  (${m2.f(1)} m²)"

fun formatHa(m2: Double): String = "${(m2 / 10000).f(4)} ha"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenScaffold(
    title: String,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    showProject: Boolean = true,
    content: @Composable (PaddingValues) -> Unit,
) {
    val ctx = LocalContext.current
    val projectName = remember { if (showProject) com.ukurlahan.pro.data.Projects.active(ctx).name else "" }
    Scaffold(
        modifier = Modifier.imePadding(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(title, maxLines = 1, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        if (projectName.isNotBlank()) Text("📁 $projectName", maxLines = 1, fontSize = 11.sp, color = Accent)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali") }
                },
                actions = actions,
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { p -> content(p) }
}

@Composable
fun NumField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    suffix: String? = null,
    signed: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontSize = 12.sp) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = if (signed) KeyboardType.Text else KeyboardType.Decimal),
        suffix = if (suffix != null) {
            { Text(suffix) }
        } else null,
        modifier = modifier,
    )
}

@Composable
fun SectionCard(title: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (title != null) Text(title, color = Accent, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            content()
        }
    }
}

@Composable
fun InfoRow(label: String, value: String, valueColor: Color = Color.Unspecified) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = valueColor)
    }
}

@Composable
fun ToggleRow(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEachIndexed { i, s ->
            if (i == selected) {
                Button(onClick = { onSelect(i) }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(4.dp)) {
                    Text(s, fontSize = 12.sp, maxLines = 1)
                }
            } else {
                OutlinedButton(onClick = { onSelect(i) }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(4.dp)) {
                    Text(s, fontSize = 12.sp, maxLines = 1)
                }
            }
        }
    }
}

@Composable
fun OverlayText(text: String, modifier: Modifier = Modifier, color: Color = Color.White, big: Boolean = false) {
    Text(
        text,
        color = color,
        fontSize = if (big) 20.sp else 13.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Monospace,
        modifier = modifier
            .background(Color(0xB0000000), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

/** Minta izin; mengembalikan (sudahDiizinkan, fungsiMintaUlang). */
@Composable
fun rememberPermissions(perms: List<String>): Pair<Boolean, () -> Unit> {
    val ctx = LocalContext.current
    val check = { perms.all { ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED } }
    var granted by remember { mutableStateOf(check()) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        granted = check()
    }
    LaunchedEffect(Unit) { if (!granted) launcher.launch(perms.toTypedArray()) }
    return granted to { launcher.launch(perms.toTypedArray()) }
}

/** Minta beberapa izin sekaligus; mengembalikan status per izin. */
@Composable
fun rememberPermissionMap(perms: List<String>): Pair<Map<String, Boolean>, () -> Unit> {
    val ctx = LocalContext.current
    val check = {
        perms.associateWith { ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED }
    }
    var state by remember { mutableStateOf(check()) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        state = check()
    }
    LaunchedEffect(Unit) { if (state.values.any { !it }) launcher.launch(perms.toTypedArray()) }
    return state to { launcher.launch(perms.toTypedArray()) }
}

/** Pratinjau kamera belakang dengan dukungan zoom. */
@Composable
fun CameraPreview(modifier: Modifier = Modifier, zoom: Float = 1f, onMaxZoom: (Float) -> Unit = {}) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
    }
    var camera by remember { mutableStateOf<Camera?>(null) }

    DisposableEffect(lifecycleOwner) {
        val future = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        future.addListener({
            try {
                val p = future.get()
                provider = p
                val preview = Preview.Builder().build()
                preview.setSurfaceProvider(previewView.surfaceProvider)
                p.unbindAll()
                val cam = p.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview)
                camera = cam
                cam.cameraInfo.zoomState.value?.let { onMaxZoom(it.maxZoomRatio) }
            } catch (_: Exception) {
            }
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            try {
                provider?.unbindAll()
            } catch (_: Exception) {
            }
        }
    }

    LaunchedEffect(camera, zoom) {
        camera?.let { cam ->
            val max = cam.cameraInfo.zoomState.value?.maxZoomRatio ?: 1f
            cam.cameraControl.setZoomRatio(zoom.coerceIn(1f, max))
        }
    }

    AndroidView(factory = { previewView }, modifier = modifier)
}

@Composable
fun ZoomBar(zoom: Float, maxZoom: Float, onZoom: (Float) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Zoom ${"%.1f".format(Locale.US, zoom)}x", fontSize = 12.sp, modifier = Modifier.width(78.dp))
        Slider(
            value = zoom.coerceIn(1f, max(1.01f, maxZoom)),
            onValueChange = onZoom,
            valueRange = 1f..max(1.01f, maxZoom),
            modifier = Modifier.weight(1f),
        )
    }
}

/** Benang silang (crosshair) yang garis horizontalnya mengikuti horizon. */
@Composable
fun Crosshair(horizonDeg: Double, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxSize()) {
        val c = center
        rotate(horizonDeg.toFloat(), c) {
            drawLine(color, Offset(-size.width, c.y), Offset(size.width * 2, c.y), 3f)
            for (i in -5..5) {
                if (i == 0) continue
                val x = c.x + i * size.width / 12f
                drawLine(color, Offset(x, c.y - 12f), Offset(x, c.y + 12f), 2f)
            }
        }
        drawLine(color, Offset(c.x, c.y - 50f), Offset(c.x, c.y + 50f), 2f)
        drawCircle(color, 16f, c, style = Stroke(2f))
    }
}

/** Gambar poligon (atas = Utara) lengkap dengan label titik & skala. */
@Composable
fun PolygonView(
    xy: List<Pair<Double, Double>>,
    modifier: Modifier = Modifier,
    labels: List<String>? = null,
    closed: Boolean = true,
) {
    Canvas(modifier.background(Color(0xFF0E1114), RoundedCornerShape(8.dp))) {
        val paint = android.graphics.Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = 30f
            isAntiAlias = true
        }
        if (xy.isEmpty()) {
            drawContext.canvas.nativeCanvas.drawText("Belum ada titik", 24f, size.height / 2, paint)
            return@Canvas
        }
        val minX = xy.minOf { it.first }
        val maxX = xy.maxOf { it.first }
        val minY = xy.minOf { it.second }
        val maxY = xy.maxOf { it.second }
        val spanX = max(maxX - minX, 1.0)
        val spanY = max(maxY - minY, 1.0)
        val pad = 44f
        val scale = min((size.width - 2 * pad) / spanX, (size.height - 2 * pad) / spanY)
        val cx = (minX + maxX) / 2
        val cy = (minY + maxY) / 2
        fun tx(p: Pair<Double, Double>) = Offset(
            (size.width / 2 + (p.first - cx) * scale).toFloat(),
            (size.height / 2 - (p.second - cy) * scale).toFloat(),
        )

        val path = Path()
        xy.forEachIndexed { i, p ->
            val o = tx(p)
            if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y)
        }
        val isPoly = closed && xy.size >= 3
        if (isPoly) {
            path.close()
            drawPath(path, Accent.copy(alpha = 0.22f))
        }
        drawPath(path, Accent, style = Stroke(4f))
        xy.forEachIndexed { i, p ->
            val o = tx(p)
            drawCircle(if (i == 0) Good else Color.White, 8f, o)
            val label = labels?.getOrNull(i) ?: "${i + 1}"
            drawContext.canvas.nativeCanvas.drawText(label, o.x + 10f, o.y - 10f, paint)
        }

        // Panah utara
        val nx = size.width - 30f
        drawLine(Color.White, Offset(nx, 60f), Offset(nx, 20f), 3f)
        drawLine(Color.White, Offset(nx, 20f), Offset(nx - 8f, 32f), 3f)
        drawLine(Color.White, Offset(nx, 20f), Offset(nx + 8f, 32f), 3f)
        drawContext.canvas.nativeCanvas.drawText("U", nx - 9f, 88f, paint)

        // Skala
        val targetM = (size.width * 0.3f) / scale
        val pow10 = 10.0.pow(floor(log10(targetM)))
        val nice = listOf(1.0, 2.0, 5.0, 10.0).map { it * pow10 }.last { it <= targetM * 1.0001 }
        val lenPx = (nice * scale).toFloat()
        val y0 = size.height - 18f
        drawLine(Color.White, Offset(20f, y0), Offset(20f + lenPx, y0), 4f)
        val txt = if (nice >= 1) "${nice.toInt()} m" else "${nice.f(2)} m"
        drawContext.canvas.nativeCanvas.drawText(txt, 24f + lenPx, y0 + 8f, paint)
    }
}

/** Warna status berdasarkan besar simpangan. */
fun levelColor(dev: Double, tolGood: Double, tolWarn: Double): Color = when {
    abs(dev) <= tolGood -> Good
    abs(dev) <= tolWarn -> Warn
    else -> Bad
}
