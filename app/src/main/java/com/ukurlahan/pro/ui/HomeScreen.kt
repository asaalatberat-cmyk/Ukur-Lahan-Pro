package com.ukurlahan.pro.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ukurlahan.pro.Screen

private data class Menu(val icon: String, val title: String, val desc: String, val screen: Screen)

private val menus = listOf(
    Menu("🎯", "Sipat Datar Kamera", "Waterpass + rambu. Kunci tinggi alat, cek gali/timbun tiap titik", Screen.LEVELING),
    Menu("🫧", "Waterpass Digital", "Nivo gelembung, kunci titik acuan, mode jidar (mm)", Screen.LEVEL),
    Menu("🔭", "Total Station Kamera", "Bidik titik: azimut, jarak, beda tinggi, luas & tinggi objek", Screen.TOTAL_STATION),
    Menu("🚶", "Luas Lahan Berjalan", "Kelilingi batas lahan pakai kamera + GPS, ekspor KML", Screen.AREA),
    Menu("⛏️", "Galian & Timbunan", "Volume cut/fill metode grid, elevasi seimbang, ritase truk", Screen.VOLUME),
    Menu("📐", "Kemiringan", "Konversi %, derajat, 1:n dan beda tinggi rencana", Screen.SLOPE),
    Menu("⚙️", "Kalibrasi", "Uji dua patok (garis bidik) & reset koreksi sensor", Screen.CALIBRATION),
    Menu("📖", "Panduan", "Cara kerja tiap alat dan tips akurasi di lapangan", Screen.GUIDE),
)

@Composable
fun HomeScreen(onOpen: (Screen) -> Unit) {
    Scaffold { pad ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(span = { GridItemSpan(2) }) {
                Column(Modifier.padding(vertical = 8.dp)) {
                    Text("Ukur Lahan Pro", fontSize = 26.sp, fontWeight = FontWeight.Black, color = Accent)
                    Text("Alat ukur tanah & topografi di saku Anda", fontSize = 14.sp)
                }
            }
            items(menus) { m ->
                Card(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 150.dp).clickable { onOpen(m.screen) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(m.icon, fontSize = 30.sp)
                        Text(m.title, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text(m.desc, fontSize = 12.sp, lineHeight = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item(span = { GridItemSpan(2) }) {
                Text(
                    "Akurasi tergantung sensor HP, kalibrasi, dan cara pemakaian. Untuk pekerjaan legal/sertifikat tetap gunakan surveyor berlisensi.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}
