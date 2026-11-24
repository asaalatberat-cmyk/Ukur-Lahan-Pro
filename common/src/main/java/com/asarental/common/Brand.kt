package com.asarental.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Identitas perusahaan. Untuk mengganti logo: timpa file
 * common/src/main/res/drawable-nodpi/logo_asa.png dengan logo asli (PNG, persegi).
 */
object Brand {
    const val COMPANY = "PT. ASA RENTAL"
    const val TEAM = "Team Earthwork PT. ASA"
    const val NOTICE = "Aplikasi internal khusus Team Earthwork PT. ASA. Tidak diperjualbelikan."
    const val FOOTER = "PT. ASA RENTAL  •  Khusus Team Earthwork PT. ASA  •  Tidak diperjualbelikan"

    /** Paket aplikasi ASA Laporan (penerima data). */
    const val LAPORAN_PACKAGE = "com.asarental.laporan"

    /** Format paket data yang dikirim Ukur Lahan Pro → ASA Laporan. */
    const val PACKAGE_FORMAT = "asa-earthwork"
    const val PACKAGE_VERSION = 1

    val ACCENT = Color(0xFFFFB300)
    val LOGO = R.drawable.logo_asa
    val LOGO_FULL = R.drawable.logo_asa_full
}

/** Kepala halaman bergaya perusahaan: logo + nama aplikasi + nama perusahaan. */
@Composable
fun BrandHeader(appName: String, subtitle: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Image(
            painter = painterResource(Brand.LOGO),
            contentDescription = "Logo PT. ASA RENTAL",
            modifier = Modifier.size(58.dp).clip(RoundedCornerShape(12.dp)).background(Color.White),
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Text(appName, fontSize = 24.sp, fontWeight = FontWeight.Black, color = Brand.ACCENT)
            Text(Brand.COMPANY, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Logo lengkap PT. ASA RENTAL di atas latar putih. */
@Composable
fun BrandBanner(modifier: Modifier = Modifier) {
    Box(
        modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(12.dp)).padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(Brand.LOGO_FULL),
            contentDescription = "PT. ASA RENTAL",
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Kotak pemberitahuan "khusus internal – tidak diperjualbelikan". */
@Composable
fun BrandNotice(modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .background(Color(0x33FFB300), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("🔒", fontSize = 16.sp)
        Spacer(Modifier.width(8.dp))
        Column {
            Text("Khusus ${Brand.TEAM}", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text("Aplikasi internal ${Brand.COMPANY} — tidak diperjualbelikan.", fontSize = 11.sp)
        }
    }
}
