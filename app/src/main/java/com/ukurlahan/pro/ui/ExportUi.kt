package com.ukurlahan.pro.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.asarental.common.Brand
import com.asarental.common.Report
import com.asarental.common.Share as AsaShare
import com.asarental.common.Xlsx
import com.ukurlahan.pro.data.Module
import com.ukurlahan.pro.data.ProjectData
import com.ukurlahan.pro.data.Projects
import com.ukurlahan.pro.data.Reports
import com.ukurlahan.pro.data.Store
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class ExportKind(val label: String) {
    PDF("📄 Ekspor PDF"),
    EXCEL("📊 Ekspor Excel"),
    IMAGE("🖼️ Ekspor Gambar HD"),
    LAPORAN("🤖 Kirim ke ASA Laporan"),
}

/** Hasil ekspor yang siap dibagikan. */
private class ExportResult(val files: List<File>, val mime: String, val saved: String?, val sentToApp: Boolean? = null)

private fun doExport(ctx: Context, kind: ExportKind, projectId: String, modules: Set<Module>): ExportResult {
    val p = Projects.get(ctx, projectId) ?: Projects.active(ctx)
    val pd = ProjectData.load(ctx, p)
    val dir = AsaShare.exportDir(ctx)
    val part = if (modules.size == 1) "_" + AsaShare.safeName(modules.first().title) else ""
    val base = "ASA_${AsaShare.safeName(p.name)}${part}_${Store.stamp()}"
    val title = "Laporan ${p.name}"
    return when (kind) {
        ExportKind.PDF -> {
            val f = File(dir, "$base.pdf")
            Report.writePdf(ctx, Reports.blocks(pd, modules), f, title)
            ExportResult(listOf(f), "application/pdf", AsaShare.saveToDevice(ctx, f, "application/pdf"))
        }
        ExportKind.EXCEL -> {
            val f = File(dir, "$base.xlsx")
            Xlsx.write(Reports.sheets(pd, modules), f)
            val mime = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            ExportResult(listOf(f), mime, AsaShare.saveToDevice(ctx, f, mime))
        }
        ExportKind.IMAGE -> {
            val pdf = File(dir, "$base.pdf")
            Report.writePdf(ctx, Reports.blocks(pd, modules), pdf, title)
            val imgs = Report.pdfToPng(pdf, dir, base, 300)
            var saved: String? = null
            imgs.forEach { saved = AsaShare.saveToDevice(ctx, it, "image/png") ?: saved }
            ExportResult(imgs, "image/png", saved)
        }
        ExportKind.LAPORAN -> {
            val f = File(dir, "$base.asa.json")
            f.writeText(Reports.packageJson(pd, modules).toString(2))
            ExportResult(listOf(f), "application/json", null)
        }
    }
}

/**
 * Menjalankan ekspor di latar belakang, menampilkan proses, lalu
 * menyimpan ke HP dan membuka menu bagikan.
 */
class Exporter(private val ctx: Context) {
    var busy by mutableStateOf<String?>(null)
    var notInstalled by mutableStateOf<File?>(null)

    suspend fun run(kind: ExportKind, projectId: String, modules: Set<Module>) {
        busy = kind.label.substringAfter(' ')
        try {
            val r = withContext(Dispatchers.IO) { doExport(ctx, kind, projectId, modules) }
            busy = null
            if (kind == ExportKind.LAPORAN) {
                val ok = AsaShare.sendToPackage(ctx, r.files.first(), r.mime, Brand.LAPORAN_PACKAGE)
                if (!ok) notInstalled = r.files.first()
            } else {
                r.saved?.let { Toast.makeText(ctx, "Tersimpan di $it", Toast.LENGTH_LONG).show() }
                AsaShare.shareFiles(ctx, r.files, r.mime, r.files.first().name)
            }
        } catch (e: Exception) {
            busy = null
            Toast.makeText(ctx, "Ekspor gagal: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}

@Composable
fun rememberExporter(): Exporter {
    val ctx = LocalContext.current
    return remember { Exporter(ctx) }
}

/** Dialog proses & dialog "ASA Laporan belum terpasang". */
@Composable
fun ExporterDialogs(ex: Exporter) {
    val ctx = LocalContext.current
    ex.busy?.let { label ->
        AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            title = { Text("Memproses…") },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(28.dp))
                    Spacer(Modifier.width(14.dp))
                    Text(label)
                }
            },
        )
    }
    ex.notInstalled?.let { f ->
        AlertDialog(
            onDismissRequest = { ex.notInstalled = null },
            title = { Text("ASA Laporan belum terpasang") },
            text = {
                Text(
                    "Pasang aplikasi ASA Laporan (APK kedua dari hasil build) agar rangkuman bisa dibuat otomatis.\n\n" +
                        "Sementara itu, file data bisa dikirim lewat WhatsApp/Drive lalu dibuka di HP yang sudah memasang ASA Laporan.",
                )
            },
            confirmButton = {
                Button(onClick = {
                    ex.notInstalled = null
                    AsaShare.shareFiles(ctx, listOf(f), "application/json", f.name)
                }) { Text("Bagikan file data") }
            },
            dismissButton = { TextButton(onClick = { ex.notInstalled = null }) { Text("Tutup") } },
        )
    }
}

/**
 * Tombol bagikan di bilah atas setiap fitur: PDF, Excel, Gambar HD,
 * CSV (opsional), dan Kirim ke ASA Laporan.
 */
@Composable
fun ExportButton(modules: Set<Module>, csvLabel: String = "🧾 Ekspor CSV", onCsv: (() -> Unit)? = null) {
    val ctx = LocalContext.current
    val ex = rememberExporter()
    val scope = rememberCoroutineScope()
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Default.Share, "Ekspor / bagikan") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            ExportKind.entries.forEach { k ->
                DropdownMenuItem(
                    text = { Text(k.label) },
                    onClick = {
                        open = false
                        scope.launch { ex.run(k, Projects.activeId(ctx), modules) }
                    },
                )
            }
            if (onCsv != null) {
                DropdownMenuItem(text = { Text(csvLabel) }, onClick = { open = false; onCsv() })
            }
        }
    }
    ExporterDialogs(ex)
}
