package com.ukurlahan.pro.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot

// ---------------------------------------------------------------- Sipat datar

data class LvPoint(
    val name: String,
    val rawReading: Double,
    val dist: Double?,
    val pitch: Double,
    val reading: Double,
    val hi: Double,
    val elev: Double,
    val time: Long,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("name", name); put("raw", rawReading)
        if (dist != null) put("dist", dist)
        put("pitch", pitch); put("reading", reading); put("hi", hi); put("elev", elev); put("time", time)
    }

    companion object {
        fun from(j: JSONObject) = LvPoint(
            name = j.getString("name"),
            rawReading = j.getDouble("raw"),
            dist = if (j.has("dist")) j.getDouble("dist") else null,
            pitch = j.optDouble("pitch", 0.0),
            reading = j.getDouble("reading"),
            hi = j.getDouble("hi"),
            elev = j.getDouble("elev"),
            time = j.optLong("time", 0L),
        )
    }
}

data class LevelingData(
    val refName: String = "BM",
    val refElev: Double = 100.0,
    val hi: Double? = null,
    val design: Double? = null,
    val points: List<LvPoint> = emptyList(),
) {
    fun save(ctx: Context) {
        val j = JSONObject().apply {
            put("refName", refName); put("refElev", refElev)
            if (hi != null) put("hi", hi)
            if (design != null) put("design", design)
            put("points", JSONArray().apply { points.forEach { put(it.toJson()) } })
        }
        Store.save(ctx, KEY, j.toString())
    }

    companion object {
        const val KEY = "leveling"
        fun load(ctx: Context): LevelingData = parse(Store.load(ctx, KEY))
        fun parse(text: String?): LevelingData = try {
            val j = JSONObject(text ?: "{}")
            val arr = j.optJSONArray("points") ?: JSONArray()
            LevelingData(
                refName = j.optString("refName", "BM"),
                refElev = j.optDouble("refElev", 100.0),
                hi = if (j.has("hi")) j.getDouble("hi") else null,
                design = if (j.has("design")) j.getDouble("design") else null,
                points = (0 until arr.length()).map { LvPoint.from(arr.getJSONObject(it)) },
            )
        } catch (_: Exception) {
            LevelingData()
        }
    }
}

// ---------------------------------------------------------------- Total station kamera

data class TsPoint(
    val name: String,
    val az: Double,
    val pitch: Double,
    val dist: Double,
    val dh: Double,
    val x: Double,
    val y: Double,
    val z: Double,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("name", name); put("az", az); put("pitch", pitch); put("dist", dist)
        put("dh", dh); put("x", x); put("y", y); put("z", z)
    }

    companion object {
        fun from(j: JSONObject) = TsPoint(
            j.getString("name"), j.getDouble("az"), j.getDouble("pitch"), j.getDouble("dist"),
            j.getDouble("dh"), j.getDouble("x"), j.getDouble("y"), j.getDouble("z"),
        )
    }
}

data class TsData(
    val hi: Double = 1.5,
    val ht: Double = 0.0,
    val stElev: Double = 100.0,
    val points: List<TsPoint> = emptyList(),
) {
    fun save(ctx: Context) {
        val j = JSONObject().apply {
            put("hi", hi); put("ht", ht); put("stElev", stElev)
            put("points", JSONArray().apply { points.forEach { put(it.toJson()) } })
        }
        Store.save(ctx, KEY, j.toString())
    }

    companion object {
        const val KEY = "totalstation"
        fun load(ctx: Context): TsData = parse(Store.load(ctx, KEY))
        fun parse(text: String?): TsData = try {
            val j = JSONObject(text ?: "{}")
            val arr = j.optJSONArray("points") ?: JSONArray()
            TsData(
                j.optDouble("hi", 1.5), j.optDouble("ht", 0.0), j.optDouble("stElev", 100.0),
                (0 until arr.length()).map { TsPoint.from(arr.getJSONObject(it)) },
            )
        } catch (_: Exception) {
            TsData()
        }
    }
}

// ---------------------------------------------------------------- Luas GPS

data class GeoPt(val lat: Double, val lon: Double, val acc: Double, val alt: Double) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("lat", lat); put("lon", lon); put("acc", acc); put("alt", alt)
    }

    companion object {
        fun from(j: JSONObject) = GeoPt(j.getDouble("lat"), j.getDouble("lon"), j.optDouble("acc", 0.0), j.optDouble("alt", 0.0))
    }
}

object AreaStore {
    const val KEY = "area"
    fun save(ctx: Context, name: String, pts: List<GeoPt>) {
        val j = JSONObject().apply {
            put("name", name)
            put("points", JSONArray().apply { pts.forEach { put(it.toJson()) } })
        }
        Store.save(ctx, KEY, j.toString())
    }

    fun load(ctx: Context): Pair<String, List<GeoPt>> = parse(Store.load(ctx, KEY))
    fun parse(text: String?): Pair<String, List<GeoPt>> = try {
        val j = JSONObject(text ?: "{}")
        val arr = j.optJSONArray("points") ?: JSONArray()
        j.optString("name", "Lahan 1") to (0 until arr.length()).map { GeoPt.from(arr.getJSONObject(it)) }
    } catch (_: Exception) {
        "Lahan 1" to emptyList()
    }
}

// ---------------------------------------------------------------- Geometri

object Geo {
    private const val R = 6378137.0

    /** Proyeksi lat/lon ke koordinat lokal meter (X = Timur, Y = Utara) relatif titik pertama. */
    fun project(pts: List<GeoPt>): List<Pair<Double, Double>> {
        if (pts.isEmpty()) return emptyList()
        val lat0 = pts[0].lat
        val lon0 = pts[0].lon
        val k = cos(Math.toRadians(lat0))
        return pts.map {
            Pair(Math.toRadians(it.lon - lon0) * R * k, Math.toRadians(it.lat - lat0) * R)
        }
    }

    /** Luas poligon (rumus shoelace / Gauss), m². */
    fun area(xy: List<Pair<Double, Double>>): Double {
        if (xy.size < 3) return 0.0
        var s = 0.0
        for (i in xy.indices) {
            val a = xy[i]
            val b = xy[(i + 1) % xy.size]
            s += a.first * b.second - b.first * a.second
        }
        return abs(s) / 2.0
    }

    fun sides(xy: List<Pair<Double, Double>>, closed: Boolean = true): List<Double> {
        if (xy.size < 2) return emptyList()
        val n = if (closed && xy.size >= 3) xy.size else xy.size - 1
        return (0 until n).map {
            val a = xy[it]
            val b = xy[(it + 1) % xy.size]
            hypot(b.first - a.first, b.second - a.second)
        }
    }

    fun perimeter(xy: List<Pair<Double, Double>>): Double = sides(xy, true).sum()
}

// ---------------------------------------------------------------- Galian & timbunan

data class VolumeData(
    val rows: String = "4",
    val cols: String = "4",
    val spacing: String = "5",
    val design: String = "",
    val swell: String = "25",
    val truck: String = "7",
    val grid: Map<String, String> = emptyMap(),
) {
    val nRows: Int get() = (rows.trim().toIntOrNull() ?: 2).coerceIn(2, 15)
    val nCols: Int get() = (cols.trim().toIntOrNull() ?: 2).coerceIn(2, 15)

    fun save(ctx: Context) {
        val g = JSONObject()
        grid.forEach { (k, v) -> if (v.isNotBlank()) g.put(k, v) }
        val j = JSONObject().apply {
            put("rows", rows); put("cols", cols); put("spacing", spacing); put("design", design)
            put("swell", swell); put("truck", truck); put("grid", g)
        }
        Store.save(ctx, KEY, j.toString())
    }

    fun isEmpty(): Boolean = grid.values.none { it.isNotBlank() }

    companion object {
        const val KEY = "volume"
        fun load(ctx: Context): VolumeData = parse(Store.load(ctx, KEY))
        fun parse(text: String?): VolumeData = try {
            val j = JSONObject(text ?: "{}")
            val g = j.optJSONObject("grid") ?: JSONObject()
            val map = mutableMapOf<String, String>()
            g.keys().forEach { k -> map[k] = g.optString(k, "") }
            VolumeData(
                j.optString("rows", "4"), j.optString("cols", "4"), j.optString("spacing", "5"),
                j.optString("design", ""), j.optString("swell", "25"), j.optString("truck", "7"), map,
            )
        } catch (_: Exception) {
            VolumeData()
        }
    }
}

data class VolumeResult(
    val filled: Int,
    val missing: Int,
    val area: Double,
    val balance: Double?,
    val design: Double?,
    val cut: Double,
    val fill: Double,
    val loose: Double,
    val trips: Int?,
    val swellPct: Double,
    val truck: Double,
)

/** Metode grid (borrow pit): bobot sudut 1, tepi 2, tengah 4. */
object VolumeCalc {
    private fun num(s: String?): Double? = s?.trim()?.replace(',', '.')?.toDoubleOrNull()

    fun compute(d: VolumeData): VolumeResult {
        val rows = d.nRows
        val cols = d.nCols
        val s = num(d.spacing) ?: 0.0
        val design = num(d.design)
        var sumW = 0.0
        var sumWZ = 0.0
        var cut = 0.0
        var fill = 0.0
        var missing = 0
        var filled = 0
        for (r in 0 until rows) for (c in 0 until cols) {
            val z = num(d.grid["$r,$c"])
            if (z == null) {
                missing++
            } else {
                filled++
                val edgeR = r == 0 || r == rows - 1
                val edgeC = c == 0 || c == cols - 1
                val w = if (edgeR && edgeC) 1.0 else if (edgeR || edgeC) 2.0 else 4.0
                sumW += w
                sumWZ += w * z
                if (design != null) {
                    val dh = z - design
                    if (dh > 0) cut += w * dh else fill += w * -dh
                }
            }
        }
        val k = s * s / 4.0
        cut *= k
        fill *= k
        val swell = (num(d.swell) ?: 25.0)
        val truck = num(d.truck) ?: 7.0
        val loose = cut * (1 + swell / 100.0)
        return VolumeResult(
            filled, missing, (rows - 1) * (cols - 1) * s * s,
            if (sumW > 0) sumWZ / sumW else null, design, cut, fill, loose,
            if (truck > 0 && design != null) kotlin.math.ceil(loose / truck).toInt() else null,
            swell, truck,
        )
    }
}
