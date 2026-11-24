package com.ukurlahan.pro.ui

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.asarental.common.Brand
import com.asarental.common.BrandHeader
import com.asarental.common.BrandNotice
import com.ukurlahan.pro.Screen
import com.ukurlahan.pro.data.Projects

private data class Menu(val icon: String, val title: String, val desc: String, val screen: Screen?)

private val menus = listOf(
    Menu("🎯", "Sipat Datar Kamera", "Waterpass + rambu. Kunci tinggi alat, cek gali/timbun tiap titik", Screen.LEVELING),
    Menu("🫧", "Waterpass Digital", "Nivo gelembung, kunci titik acuan, mode jidar (mm)", Screen.LEVEL),
    Menu("🔭", "Total Station Kamera", "Bidik titik: azimut, jarak, beda tinggi, luas & tinggi objek", Screen.TOTAL_STATION),
    Menu("🚶", "Luas Lahan Berjalan", "Kelilingi batas lahan pakai kamera + GPS, ekspor KML", Screen.AREA),
    Menu("⛏️", "Galian & Timbunan", "Volume cut/fill metode grid, elevasi seimbang, ritase truk", Screen.VOLUME),
    Menu("📐", "Kemiringan", "Konversi %, derajat, 1:n dan beda tinggi rencana", Screen.SLOPE),
    Menu("📁", "Proyek & Laporan", "Simpan per proyek, ekspor PDF, Excel, Gambar HD", Screen.PROJECTS),
    Menu("🤖", "Buka ASA Laporan", "Aplikasi rangkuman & penjelasan hasil kerja", null),
    Menu("⚙️", "Kalibrasi", "Uji dua patok (garis bidik) & reset koreksi sensor", Screen.CALIBRATION),
    Menu("📖", "Panduan", "Cara kerja tiap alat dan tips akurasi di lapangan", Screen.GUIDE),
)

@Composable
fun HomeScreen(onOpen: (Screen) -> Unit) {
    val ctx = LocalContext.current
    val project = remember { Projects.active(ctx) }
    Scaffold { pad ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(span = { GridItemSpan(2) }) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    com.asarental.common.BrandBanner()
                    BrandHeader("Ukur Lahan Pro", "Alat ukur tanah & topografi")
                }
            }
            item(span = { GridItemSpan(2) }) {
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { onOpen(Screen.PROJECTS) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.5.dp, Accent),
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("📁", fontSize = 26.sp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Proyek aktif", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(project.name, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            if (project.location.isNotBlank()) Text(project.location, fontSize = 12.sp)
                        }
                        Text("Ganti ›", color = Accent, fontWeight = FontWeight.Bold)
                    }
                }
            }
            items(menus) { m ->
                Card(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp).clickable {
                        if (m.screen != null) {
                            onOpen(m.screen)
                        } else if (!com.asarental.common.Share.launchApp(ctx, Brand.LAPORAN_PACKAGE)) {
                            Toast.makeText(ctx, "ASA Laporan belum terpasang. Pasang APK ASA Laporan terlebih dahulu.", Toast.LENGTH_LONG).show()
                        }
                    },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(m.icon, fontSize = 28.sp)
                        Text(m.title, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text(m.desc, fontSize = 12.sp, lineHeight = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item(span = { GridItemSpan(2) }) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                    BrandNotice()
                    Text(
                        "Akurasi tergantung sensor HP, kalibrasi, dan cara pemakaian. Untuk pekerjaan legal/sertifikat tetap gunakan surveyor berlisensi.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text("© ${Brand.COMPANY}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
