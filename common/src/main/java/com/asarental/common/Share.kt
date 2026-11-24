package com.asarental.common

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File

/** Utilitas menyimpan file ke HP dan membagikannya. */
object Share {
    const val FOLDER = "ASA Earthwork"

    fun exportDir(ctx: Context): File = File(ctx.cacheDir, "exports").apply { mkdirs() }

    fun uri(ctx: Context, f: File): Uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", f)

    fun safeName(s: String): String = s.replace(Regex("[^A-Za-z0-9_-]+"), "_").trim('_').ifBlank { "proyek" }

    /**
     * Salin file ke penyimpanan umum HP (Android 10+):
     * gambar → Pictures/ASA Earthwork, lainnya → Download/ASA Earthwork.
     * Mengembalikan lokasi (teks) atau null bila tidak didukung.
     */
    fun saveToDevice(ctx: Context, f: File, mime: String): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        return try {
            val isImage = mime.startsWith("image/")
            val dir = (if (isImage) Environment.DIRECTORY_PICTURES else Environment.DIRECTORY_DOWNLOADS) + "/" + FOLDER
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, f.name)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, dir)
            }
            val collection = if (isImage) MediaStore.Images.Media.EXTERNAL_CONTENT_URI else MediaStore.Downloads.EXTERNAL_CONTENT_URI
            val uri = ctx.contentResolver.insert(collection, values) ?: return null
            ctx.contentResolver.openOutputStream(uri)?.use { o -> f.inputStream().use { it.copyTo(o) } }
            dir
        } catch (_: Exception) {
            null
        }
    }

    /** Buka menu bagikan untuk satu atau beberapa file. */
    fun shareFiles(ctx: Context, files: List<File>, mime: String, title: String) {
        if (files.isEmpty()) return
        val uris = ArrayList(files.map { uri(ctx, it) })
        val send = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).apply { putExtra(Intent.EXTRA_STREAM, uris[0]) }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply { putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris) }
        }
        send.type = mime
        send.putExtra(Intent.EXTRA_SUBJECT, title)
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val clip = ClipData.newRawUri(title, uris[0])
        uris.drop(1).forEach { clip.addItem(ClipData.Item(it)) }
        send.clipData = clip
        ctx.startActivity(Intent.createChooser(send, title))
    }

    /** Bagikan teks biasa (mis. ke WhatsApp). */
    fun shareText(ctx: Context, text: String, title: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            putExtra(Intent.EXTRA_SUBJECT, title)
        }
        ctx.startActivity(Intent.createChooser(send, title))
    }

    /**
     * Kirim file langsung ke aplikasi tertentu.
     * @return false bila aplikasi tujuan belum terpasang.
     */
    fun sendToPackage(ctx: Context, f: File, mime: String, pkg: String): Boolean {
        val u = uri(ctx, f)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mime
            setPackage(pkg)
            putExtra(Intent.EXTRA_STREAM, u)
            clipData = ClipData.newRawUri(f.name, u)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return try {
            ctx.startActivity(send)
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }

    /** Buka aplikasi lain (mis. ASA Laporan). */
    fun launchApp(ctx: Context, pkg: String): Boolean {
        val i = ctx.packageManager.getLaunchIntentForPackage(pkg) ?: return false
        ctx.startActivity(i)
        return true
    }
}
