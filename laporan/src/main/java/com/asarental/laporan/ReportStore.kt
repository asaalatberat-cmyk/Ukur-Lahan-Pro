package com.asarental.laporan

import android.content.Context
import org.json.JSONObject
import java.io.File

data class Entry(val id: String, val project: String, val location: String, val exportedAt: Long, val modules: List<String>)

/** Riwayat paket data yang sudah diterima (disimpan di memori internal). */
object ReportStore {
    private fun dir(ctx: Context) = File(ctx.filesDir, "reports").apply { mkdirs() }

    fun save(ctx: Context, text: String): String {
        val id = "r" + System.currentTimeMillis()
        File(dir(ctx), "$id.json").writeText(text)
        return id
    }

    fun load(ctx: Context, id: String): JSONObject? = try {
        JSONObject(File(dir(ctx), "$id.json").readText())
    } catch (_: Exception) {
        null
    }

    fun delete(ctx: Context, id: String) {
        File(dir(ctx), "$id.json").delete()
    }

    fun list(ctx: Context): List<Entry> =
        dir(ctx).listFiles()?.filter { it.name.endsWith(".json") }?.mapNotNull { f ->
            try {
                val j = JSONObject(f.readText())
                val p = j.optJSONObject("project") ?: JSONObject()
                val m = j.optJSONArray("modules")
                Entry(
                    f.nameWithoutExtension,
                    p.optString("name", "Tanpa nama"),
                    p.optString("location", ""),
                    j.optLong("exportedAt", f.lastModified()),
                    if (m == null) emptyList() else (0 until m.length()).map { m.optString(it) },
                )
            } catch (_: Exception) {
                null
            }
        }?.sortedByDescending { it.exportedAt } ?: emptyList()
}
