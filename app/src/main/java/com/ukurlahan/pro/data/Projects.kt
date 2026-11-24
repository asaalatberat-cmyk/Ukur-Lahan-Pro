package com.ukurlahan.pro.data

import android.content.Context
import org.json.JSONObject
import java.io.File

/** Satu proyek (mis. "Proyek Prigen"). Semua data fitur disimpan per proyek. */
data class Project(
    val id: String,
    val name: String,
    val location: String = "",
    val note: String = "",
    val created: Long = System.currentTimeMillis(),
)

/**
 * Manajemen proyek. Struktur penyimpanan:
 *   files/projects/<id>/project.json      (nama, lokasi, catatan)
 *   files/projects/<id>/leveling.json     (Sipat Datar)
 *   files/projects/<id>/totalstation.json
 *   files/projects/<id>/area.json
 *   files/projects/<id>/volume.json
 */
object Projects {
    private const val PREF = "projects"
    private const val KEY_ACTIVE = "active"
    private val DATA_KEYS = listOf("leveling", "totalstation", "area", "volume")

    private fun root(ctx: Context) = File(ctx.filesDir, "projects").apply { mkdirs() }
    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    fun dir(ctx: Context, id: String): File = File(root(ctx), id).apply { mkdirs() }

    private fun read(f: File): Project? = try {
        val j = JSONObject(File(f, "project.json").readText())
        Project(
            id = f.name,
            name = j.optString("name", f.name),
            location = j.optString("location", ""),
            note = j.optString("note", ""),
            created = j.optLong("created", f.lastModified()),
        )
    } catch (_: Exception) {
        null
    }

    private fun write(ctx: Context, p: Project) {
        val j = JSONObject().apply {
            put("name", p.name); put("location", p.location); put("note", p.note); put("created", p.created)
        }
        File(dir(ctx, p.id), "project.json").writeText(j.toString())
    }

    fun list(ctx: Context): List<Project> {
        ensureDefault(ctx)
        return root(ctx).listFiles()?.filter { it.isDirectory }?.mapNotNull { read(it) }
            ?.sortedByDescending { it.created } ?: emptyList()
    }

    fun get(ctx: Context, id: String): Project? = read(File(root(ctx), id))

    fun create(ctx: Context, name: String, location: String = "", note: String = ""): Project {
        val id = "p" + System.currentTimeMillis()
        val p = Project(id, name.ifBlank { "Proyek baru" }, location, note)
        write(ctx, p)
        return p
    }

    fun update(ctx: Context, p: Project) = write(ctx, p)

    fun delete(ctx: Context, id: String) {
        File(root(ctx), id).deleteRecursively()
        if (activeId(ctx) == id) {
            val other = root(ctx).listFiles()?.firstOrNull { it.isDirectory && read(it) != null }
            if (other != null) setActive(ctx, other.name) else prefs(ctx).edit().remove(KEY_ACTIVE).apply()
        }
    }

    fun setActive(ctx: Context, id: String) = prefs(ctx).edit().putString(KEY_ACTIVE, id).apply()

    fun activeId(ctx: Context): String {
        ensureDefault(ctx)
        val id = prefs(ctx).getString(KEY_ACTIVE, null)
        if (id != null && File(root(ctx), id).exists()) return id
        val existing: File? = root(ctx).listFiles()?.firstOrNull { f -> f.isDirectory && read(f) != null }
        val first: String = existing?.name ?: create(ctx, "Proyek 1").id
        setActive(ctx, first)
        return first
    }

    fun active(ctx: Context): Project = get(ctx, activeId(ctx)) ?: Project(activeId(ctx), "Proyek")

    /** Jumlah titik/data tiap fitur – untuk ditampilkan di daftar proyek. */
    fun hasData(ctx: Context, id: String, key: String): Boolean = File(dir(ctx, id), "$key.json").exists()

    /** Proyek pertama kali: buat "Proyek 1" dan pindahkan data versi lama (bila ada). */
    private fun ensureDefault(ctx: Context) {
        val r = root(ctx)
        val any = r.listFiles()?.any { it.isDirectory && File(it, "project.json").exists() } ?: false
        if (any) return
        val p = Project("p" + System.currentTimeMillis(), "Proyek 1")
        write(ctx, p)
        DATA_KEYS.forEach { k ->
            val old = File(ctx.filesDir, "$k.json")
            if (old.exists()) {
                old.copyTo(File(dir(ctx, p.id), "$k.json"), overwrite = true)
                old.delete()
            }
        }
        prefs(ctx).edit().putString(KEY_ACTIVE, p.id).apply()
    }
}
