package com.asarental.laporan

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.asarental.common.Brand
import com.asarental.common.BrandHeader
import com.asarental.common.BrandNotice
import com.asarental.common.Report
import com.asarental.common.Share
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max
import kotlin.math.min

private val Accent = Color(0xFFFFB300)
private val GoodC = Color(0xFF4CE07A)
private val WarnC = Color(0xFFFFD54F)
private val BadC = Color(0xFFFF5252)
private val InfoC = Color(0xFF4FC3F7)

private fun levelColor(l: Level) = when (l) {
    Level.GOOD -> GoodC
    Level.WARN -> WarnC
    Level.BAD -> BadC
    Level.INFO -> InfoC
}

private val MODULE_NAMES = mapOf(
    "leveling" to "🎯 Sipat Datar",
    "totalstation" to "🔭 Total Station",
    "area" to "🚶 Luas Lahan",
    "volume" to "⛏️ Galian & Timbunan",
)

// =====================================================================
// Daftar laporan
// =====================================================================
@Composable
fun ListScreen(version: Int, onOpen: (String) -> Unit, onImport: (Uri) -> Unit) {
    val ctx = LocalContext.current
    val entries = remember(version) { ReportStore.list(ctx) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onImport(uri)
    }
    Scaffold { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { com.asarental.common.BrandBanner() }
            item { BrandHeader("ASA Laporan", "Rangkuman & penjelasan hasil kerja", Modifier.padding(vertical = 6.dp)) }
            item { BrandNotice() }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Cara memakai", fontWeight = FontWeight.Bold, color = Accent)
                        Text(
                            "1. Di Ukur Lahan Pro, buka fitur (atau menu Proyek) → ikon bagikan → \"Kirim ke ASA Laporan\".\n" +
                                "2. Aplikasi ini terbuka otomatis dan langsung membuat rangkuman.\n" +
                                "3. Rangkuman bisa dibagikan sebagai teks WhatsApp, PDF, atau gambar HD.",
                            fontSize = 13.sp,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { picker.launch(arrayOf("application/json", "application/octet-stream", "text/plain", "*/*")) }, modifier = Modifier.weight(1f)) {
                                Text("📂 Buka file data", fontSize = 12.sp)
                            }
                            OutlinedButton(onClick = {
                                if (!Share.launchApp(ctx, "com.ukurlahan.pro")) Toast.makeText(ctx, "Ukur Lahan Pro belum terpasang", Toast.LENGTH_SHORT).show()
                            }, modifier = Modifier.weight(1f)) { Text("📏 Ukur Lahan Pro", fontSize = 12.sp) }
                        }
                    }
                }
            }
            item { Text("Riwayat laporan (${entries.size})", fontWeight = FontWeight.Bold, fontSize = 16.sp) }
            if (entries.isEmpty()) {
                item {
                    Text(
                        "Belum ada data. Kirim hasil pengukuran dari Ukur Lahan Pro.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp,
                    )
                }
            }
            items(entries, key = { it.id }) { e ->
                Card(
                    Modifier.fillMaxWidth().clickable { onOpen(e.id) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text("📁 ${e.project}", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text(
                            listOf(e.location, Report.dateText(e.exportedAt)).filter { it.isNotBlank() }.joinToString(" • "),
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(e.modules.joinToString("  ") { MODULE_NAMES[it] ?: it }, fontSize = 12.sp, color = InfoC)
                    }
                }
            }
        }
    }
}

// =====================================================================
// Rincian rangkuman
// =====================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(id: String, onBack: () -> Unit, onDeleted: () -> Unit) {
    val ctx = LocalContext.current
    val json = remember(id) { ReportStore.load(ctx, id) }
    val summary = remember(json) { json?.let { Summarizer.summarize(it) } }
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf<String?>(null) }
    var askDelete by remember { mutableStateOf(false) }

    fun export(kind: Int) {
        val s = summary ?: return
        if (kind == 0) {
            Share.shareText(ctx, Summarizer.toText(s), "Rangkuman ${s.project}")
            return
        }
        busy = if (kind == 1) "Membuat PDF…" else "Membuat gambar HD…"
        scope.launch {
            try {
                val files = withContext(Dispatchers.IO) {
                    val dir = Share.exportDir(ctx)
                    val base = "Rangkuman_${Share.safeName(s.project)}_${System.currentTimeMillis() / 1000}"
                    val pdf = File(dir, "$base.pdf")
                    Report.writePdf(ctx, Summarizer.toBlocks(s), pdf, "Rangkuman ${s.project}")
                    if (kind == 1) {
                        Share.saveToDevice(ctx, pdf, "application/pdf")
                        listOf(pdf)
                    } else {
                        Report.pdfToPng(pdf, dir, base, 300).onEach { Share.saveToDevice(ctx, it, "image/png") }
                    }
                }
                busy = null
                Share.shareFiles(ctx, files, if (kind == 1) "application/pdf" else "image/png", files.first().name)
            } catch (e: Exception) {
                busy = null
                Toast.makeText(ctx, "Gagal: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Rangkuman", fontWeight = FontWeight.Bold)
                        Text(summary?.project ?: "", fontSize = 11.sp, color = Accent, maxLines = 1)
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali") } },
                actions = { IconButton(onClick = { askDelete = true }) { Icon(Icons.Default.Delete, "Hapus") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { pad ->
        if (summary == null) {
            Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.Center) { Text("Data rusak / tidak dapat dibaca") }
            return@Scaffold
        }
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Column {
                    Text(summary.project, fontSize = 22.sp, fontWeight = FontWeight.Black)
                    Text(
                        listOf(summary.location, "Data: " + Report.dateText(summary.exportedAt), summary.device).filter { it.isNotBlank() }.joinToString(" • "),
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (summary.note.isNotBlank()) Text("Catatan: ${summary.note}", fontSize = 12.sp)
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(onClick = { export(0) }, Modifier.weight(1f), contentPadding = PaddingValues(4.dp)) { Text("💬 WhatsApp", fontSize = 12.sp) }
                    Button(onClick = { export(1) }, Modifier.weight(1f), contentPadding = PaddingValues(4.dp)) { Text("📄 PDF", fontSize = 12.sp) }
                    Button(onClick = { export(2) }, Modifier.weight(1f), contentPadding = PaddingValues(4.dp)) { Text("🖼️ Gambar HD", fontSize = 12.sp) }
                }
            }
            item {
                val c = levelColor(summary.verdictLevel)
                Card(
                    colors = CardDefaults.cardColors(containerColor = c.copy(alpha = 0.15f)),
                    border = BorderStroke(1.5.dp, c),
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("KESIMPULAN", color = c, fontWeight = FontWeight.Black, fontSize = 12.sp)
                        Text(summary.verdict, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        summary.highlights.forEach { Text(it, fontSize = 13.sp) }
                    }
                }
            }
            items(summary.sections) { sec -> SectionCard(sec) }
            item { BrandNotice() }
        }
    }

    busy?.let {
        AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            title = { Text("Memproses…") },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(28.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(it)
                }
            },
        )
    }
    if (askDelete) {
        AlertDialog(
            onDismissRequest = { askDelete = false },
            title = { Text("Hapus laporan ini?") },
            text = { Text("Data di Ukur Lahan Pro tidak ikut terhapus.") },
            confirmButton = {
                Button(onClick = { ReportStore.delete(ctx, id); askDelete = false; onDeleted() }) { Text("Hapus") }
            },
            dismissButton = { TextButton(onClick = { askDelete = false }) { Text("Batal") } },
        )
    }
}

@Composable
private fun SectionCard(sec: Section) {
    var showTable by remember { mutableStateOf(false) }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(sec.icon, fontSize = 20.sp)
                Spacer(Modifier.width(8.dp))
                Text(sec.title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Accent, modifier = Modifier.weight(1f))
                Box(Modifier.size(10.dp).background(levelColor(sec.level), RoundedCornerShape(5.dp)))
            }
            if (sec.facts.isNotEmpty()) {
                Column(
                    Modifier.fillMaxWidth().background(Color(0xFF1C2026), RoundedCornerShape(8.dp)).padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    sec.facts.forEach { (k, v) ->
                        Row {
                            Text(k, fontSize = 13.sp, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(v, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }
            sec.paragraphs.forEach { Text(it, fontSize = 14.sp, lineHeight = 20.sp) }
            sec.polygon?.let { (xy, lb) -> PolygonView(xy, lb, Modifier.fillMaxWidth().height(220.dp)) }
            sec.bullets.forEach {
                Row {
                    Text("•  ", color = Accent, fontWeight = FontWeight.Bold)
                    Text(it, fontSize = 13.sp, lineHeight = 18.sp)
                }
            }
            sec.table?.let { (h, rows) ->
                if (rows.isNotEmpty()) {
                    TextButton(onClick = { showTable = !showTable }) {
                        Text(if (showTable) "Sembunyikan tabel ▲" else "Lihat tabel data (${rows.size} baris) ▼")
                    }
                    if (showTable) DataTable(h, rows)
                }
            }
        }
    }
}

@Composable
private fun DataTable(head: List<String>, rows: List<List<String>>) {
    Column(Modifier.horizontalScroll(rememberScrollState())) {
        Row(Modifier.background(Color(0xFF14171B))) {
            head.forEach { Text(it, Modifier.width(92.dp).padding(5.dp), color = Accent, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
        }
        rows.forEachIndexed { i, r ->
            Row(Modifier.background(if (i % 2 == 0) Color.Transparent else Color(0x22FFFFFF))) {
                r.forEach { cell ->
                    val col = when {
                        cell.startsWith("Gali") -> BadC
                        cell.startsWith("Timbun") -> InfoC
                        cell == "Rata" -> GoodC
                        else -> Color.Unspecified
                    }
                    Text(cell, Modifier.width(92.dp).padding(5.dp), fontSize = 12.sp, color = col, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}

@Composable
private fun PolygonView(xy: List<Pair<Double, Double>>, labels: List<String>, modifier: Modifier) {
    Canvas(modifier.background(Color(0xFF0E1114), RoundedCornerShape(8.dp))) {
        if (xy.isEmpty()) return@Canvas
        val minX = xy.minOf { it.first }
        val maxX = xy.maxOf { it.first }
        val minY = xy.minOf { it.second }
        val maxY = xy.maxOf { it.second }
        val pad = 36f
        val scale = min((size.width - 2 * pad) / max(maxX - minX, 1.0), (size.height - 2 * pad) / max(maxY - minY, 1.0))
        val cx = (minX + maxX) / 2
        val cy = (minY + maxY) / 2
        fun t(p: Pair<Double, Double>) = Offset(
            (size.width / 2 + (p.first - cx) * scale).toFloat(),
            (size.height / 2 - (p.second - cy) * scale).toFloat(),
        )
        val path = Path()
        xy.forEachIndexed { i, p -> val o = t(p); if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y) }
        path.close()
        drawPath(path, Accent.copy(alpha = 0.22f))
        drawPath(path, Accent, style = Stroke(4f))
        val paint = android.graphics.Paint().apply { color = android.graphics.Color.WHITE; textSize = 28f; isAntiAlias = true }
        xy.forEachIndexed { i, p ->
            val o = t(p)
            drawCircle(if (i == 0) GoodC else Color.White, 7f, o)
            drawContext.canvas.nativeCanvas.drawText(labels.getOrElse(i) { "${i + 1}" }, o.x + 10f, o.y - 10f, paint)
        }
        drawContext.canvas.nativeCanvas.drawText("U ↑", size.width - 60f, 36f, paint)
    }
}
