package com.ukurlahan.pro.data

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Penyimpanan sederhana berbasis file JSON di memori internal aplikasi. */
object Store {
    private fun file(ctx: Context, key: String) = File(ctx.filesDir, "$key.json")

    fun save(ctx: Context, key: String, text: String) {
        try {
            file(ctx, key).writeText(text)
        } catch (_: Exception) {
        }
    }

    fun load(ctx: Context, key: String): String? = try {
        file(ctx, key).takeIf { it.exists() }?.readText()
    } catch (_: Exception) {
        null
    }

    fun stamp(): String = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())

    /** Tulis file ke cache lalu buka menu "Bagikan" (WhatsApp, Drive, Email, dll). */
    fun share(ctx: Context, fileName: String, content: String, mime: String) {
        try {
            val dir = File(ctx.cacheDir, "exports").apply { mkdirs() }
            val f = File(dir, fileName)
            f.writeText(content)
            val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", f)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = mime
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, fileName)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            ctx.startActivity(Intent.createChooser(send, "Simpan / Bagikan $fileName"))
        } catch (e: Exception) {
            Toast.makeText(ctx, "Gagal ekspor: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}

/** Nilai kalibrasi & pengaturan yang disimpan permanen. */
class Settings(ctx: Context) {
    private val p = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private fun get(k: String, def: Double) = p.getString(k, null)?.toDoubleOrNull() ?: def
    private fun put(k: String, v: Double) = p.edit().putString(k, v.toString()).apply()

    /** Koreksi garis bidik kamera (derajat) dari uji dua patok. */
    var pitchOffset: Double
        get() = get("pitchOffset", 0.0)
        set(v) = put("pitchOffset", v)

    /** Koreksi waterpass mode rebah (derajat). */
    var flatOffX: Double
        get() = get("flatOffX", 0.0)
        set(v) = put("flatOffX", v)
    var flatOffY: Double
        get() = get("flatOffY", 0.0)
        set(v) = put("flatOffY", v)

    /** Koreksi waterpass mode berdiri di sisi (derajat). */
    var edgeOff: Double
        get() = get("edgeOff", 0.0)
        set(v) = put("edgeOff", v)
}
