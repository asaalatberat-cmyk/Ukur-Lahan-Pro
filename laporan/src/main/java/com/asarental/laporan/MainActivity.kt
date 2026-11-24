package com.asarental.laporan

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.asarental.common.Brand
import org.json.JSONObject

class MainActivity : ComponentActivity() {
    /** Laporan yang sedang dibuka (null = daftar). */
    private var openId by mutableStateOf<String?>(null)
    private var listVersion by mutableStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handle(intent)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Brand.ACCENT,
                    onPrimary = Color.Black,
                    secondary = Color(0xFF4FC3F7),
                    background = Color(0xFF14171B),
                    onBackground = Color(0xFFECEFF1),
                    surface = Color(0xFF1C2026),
                    onSurface = Color(0xFFECEFF1),
                    surfaceVariant = Color(0xFF262B33),
                    onSurfaceVariant = Color(0xFFD5DCE0),
                ),
            ) {
                val id = openId
                BackHandler(enabled = id != null) { openId = null }
                if (id == null) {
                    ListScreen(
                        version = listVersion,
                        onOpen = { openId = it },
                        onImport = { uri -> importUri(uri) },
                    )
                } else {
                    DetailScreen(id, onBack = { openId = null }, onDeleted = { openId = null; listVersion++ })
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(i: Intent?) {
        if (i == null) return
        when (i.action) {
            Intent.ACTION_SEND -> {
                val extra: Uri? = if (Build.VERSION.SDK_INT >= 33) {
                    i.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    i.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                }
                val uri: Uri? = extra ?: i.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri
                if (uri != null) {
                    importUri(uri)
                } else {
                    i.getStringExtra(Intent.EXTRA_TEXT)?.let { importText(it) }
                }
            }
            Intent.ACTION_VIEW -> i.data?.let { importUri(it) }
        }
        // cegah diproses ulang saat layar diputar / dibuka kembali
        i.action = Intent.ACTION_MAIN
    }

    private fun importUri(uri: Uri) {
        val text = try {
            contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        } catch (e: Exception) {
            null
        }
        if (text == null) {
            Toast.makeText(this, "File tidak dapat dibaca", Toast.LENGTH_LONG).show()
            return
        }
        importText(text)
    }

    private fun importText(text: String) {
        val ok = try {
            Summarizer.isValid(JSONObject(text))
        } catch (_: Exception) {
            false
        }
        if (!ok) {
            Toast.makeText(this, "Bukan file data Ukur Lahan Pro", Toast.LENGTH_LONG).show()
            return
        }
        val id = ReportStore.save(this, text)
        listVersion++
        openId = id
        Toast.makeText(this, "Data diterima – rangkuman dibuat", Toast.LENGTH_SHORT).show()
    }
}
