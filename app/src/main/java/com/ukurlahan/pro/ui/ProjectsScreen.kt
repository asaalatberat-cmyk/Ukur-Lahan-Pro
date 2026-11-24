package com.ukurlahan.pro.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.asarental.common.BrandNotice
import com.asarental.common.Report
import com.ukurlahan.pro.data.Module
import com.ukurlahan.pro.data.Project
import com.ukurlahan.pro.data.ProjectData
import com.ukurlahan.pro.data.Projects
import kotlinx.coroutines.launch

/** Daftar proyek: buat, buka (aktifkan), ubah, hapus, dan ekspor laporan proyek. */
@Composable
fun ProjectsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    var version by remember { mutableIntStateOf(0) }
    val projects = remember(version) { Projects.list(ctx) }
    val activeId = remember(version) { Projects.activeId(ctx) }
    val datas = remember(version) { projects.associate { it.id to ProjectData.load(ctx, it) } }
    var editing by remember { mutableStateOf<Project?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Project?>(null) }
    val ex = rememberExporter()
    val scope = rememberCoroutineScope()

    ScreenScaffold("Proyek & Laporan", onBack, showProject = false) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { BrandNotice() }
            item {
                Button(onClick = { creating = true }, modifier = Modifier.fillMaxWidth().height(50.dp)) {
                    Text("＋ PROYEK BARU", fontWeight = FontWeight.Bold)
                }
            }
            items(projects, key = { it.id }) { p ->
                val active = p.id == activeId
                val pd = datas[p.id]
                val mods = pd?.available() ?: emptySet()
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    border = if (active) BorderStroke(2.dp, Accent) else null,
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("📁", fontSize = 22.sp)
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(p.name, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                                Text(
                                    listOf(p.location, "dibuat " + Report.dateText(p.created)).filter { it.isNotBlank() }.joinToString(" • "),
                                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (active) Text("AKTIF", color = Good, fontWeight = FontWeight.Black, fontSize = 12.sp)
                        }
                        if (p.note.isNotBlank()) Text(p.note, fontSize = 12.sp)
                        Text(
                            if (mods.isEmpty()) "Belum ada data"
                            else Module.entries.filter { it in mods }.joinToString("   ") { m ->
                                val n = when (m) {
                                    Module.LEVELING -> pd!!.lv.points.size
                                    Module.TOTAL_STATION -> pd!!.ts.points.size
                                    Module.AREA -> pd!!.area.size
                                    Module.VOLUME -> pd!!.vol.grid.values.count { it.isNotBlank() }
                                }
                                "${m.icon} ${m.title} ($n)"
                            },
                            fontSize = 12.sp, color = Info,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (!active) {
                                Button(
                                    onClick = { Projects.setActive(ctx, p.id); version++ },
                                    modifier = Modifier.weight(1f), contentPadding = PaddingValues(4.dp),
                                ) { Text("Buka / aktifkan", fontSize = 12.sp) }
                            }
                            OutlinedButton(onClick = { editing = p }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(4.dp)) {
                                Text("Ubah", fontSize = 12.sp)
                            }
                            OutlinedButton(onClick = { deleting = p }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(4.dp)) {
                                Text("Hapus", fontSize = 12.sp, color = Bad)
                            }
                        }
                        Text("Ekspor seluruh proyek:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(ExportKind.PDF to "PDF", ExportKind.EXCEL to "Excel", ExportKind.IMAGE to "Gambar HD").forEach { (k, label) ->
                                OutlinedButton(
                                    onClick = { scope.launch { ex.run(k, p.id, Module.entries.toSet()) } },
                                    modifier = Modifier.weight(1f), contentPadding = PaddingValues(2.dp),
                                ) { Text(label, fontSize = 12.sp) }
                            }
                        }
                        Button(
                            onClick = { scope.launch { ex.run(ExportKind.LAPORAN, p.id, Module.entries.toSet()) } },
                            enabled = mods.isNotEmpty(),
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Info),
                        ) { Text("🤖 Kirim ke ASA Laporan (rangkuman)", fontWeight = FontWeight.Bold) }
                    }
                }
            }
            item {
                Text(
                    "Proyek AKTIF adalah tempat semua pengukuran baru disimpan. Ganti proyek dengan tombol \"Buka / aktifkan\".",
                    fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    ExporterDialogs(ex)

    if (creating || editing != null) {
        val base = editing
        var name by remember(base) { mutableStateOf(base?.name ?: "") }
        var loc by remember(base) { mutableStateOf(base?.location ?: "") }
        var note by remember(base) { mutableStateOf(base?.note ?: "") }
        AlertDialog(
            onDismissRequest = { creating = false; editing = null },
            title = { Text(if (base == null) "Proyek baru" else "Ubah proyek") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(name, { name = it }, label = { Text("Nama proyek (mis. Proyek Prigen)") }, singleLine = true)
                    OutlinedTextField(loc, { loc = it }, label = { Text("Lokasi (opsional)") }, singleLine = true)
                    OutlinedTextField(note, { note = it }, label = { Text("Catatan (opsional)") })
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (base == null) {
                        val p = Projects.create(ctx, name.trim(), loc.trim(), note.trim())
                        Projects.setActive(ctx, p.id)
                    } else {
                        Projects.update(ctx, base.copy(name = name.trim().ifBlank { base.name }, location = loc.trim(), note = note.trim()))
                    }
                    creating = false; editing = null; version++
                }) { Text("Simpan") }
            },
            dismissButton = { TextButton(onClick = { creating = false; editing = null }) { Text("Batal") } },
        )
    }

    deleting?.let { p ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Hapus ${p.name}?") },
            text = { Text("Semua data pengukuran di proyek ini akan terhapus permanen. Ekspor dulu bila perlu.") },
            confirmButton = {
                Button(onClick = { Projects.delete(ctx, p.id); deleting = null; version++ }, colors = ButtonDefaults.buttonColors(containerColor = Bad)) {
                    Text("Hapus")
                }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Batal") } },
        )
    }
}
