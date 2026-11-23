package com.ukurlahan.pro.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Orientasi HP yang sudah dirata-rata (anti getar).
 * (ux, uy, uz) = vektor satuan arah "ATAS" bumi dalam koordinat HP
 *   x = ke kanan layar, y = ke atas layar, z = keluar dari layar.
 */
data class Orientation(
    val ux: Double = 0.0,
    val uy: Double = 1.0,
    val uz: Double = 0.0,
    /** Azimut sumbu kamera belakang (0 = Utara magnetis, searah jarum jam). */
    val azimuth: Double = 0.0,
    val hasCompass: Boolean = false,
    /** Sebaran sudut sampel terakhir (derajat) – indikator kestabilan. */
    val spread: Double = 0.0,
    val ready: Boolean = false,
) {
    /** Sudut elevasi sumbu kamera belakang: + = mendongak, − = menunduk. */
    val cameraPitch: Double get() = Math.toDegrees(asin((-uz).coerceIn(-1.0, 1.0)))

    /** HP rebah (layar menghadap atas): kemiringan kiri-kanan, + = sisi kanan lebih tinggi. */
    val flatTiltX: Double get() = Math.toDegrees(atan2(ux, uz))

    /** HP rebah: kemiringan depan-belakang, + = ujung atas HP lebih tinggi. */
    val flatTiltY: Double get() = Math.toDegrees(atan2(uy, uz))

    /** HP berdiri di sisi panjang: kemiringan sepanjang HP, + = ujung atas lebih tinggi. */
    val edgeTilt: Double get() = Math.toDegrees(atan2(uy, abs(ux)))

    val isFlat: Boolean get() = abs(uz) > 0.7
    val isEdge: Boolean get() = abs(ux) > 0.7

    /** Sudut putar garis horizon di layar kamera (derajat, searah jarum jam). */
    val horizonScreenDeg: Double get() = Math.toDegrees(atan2(ux, uy))
}

/**
 * Membaca sensor gravitasi (atau akselerometer) + rotation vector,
 * lalu merata-ratakan [window] sampel terakhir agar angka stabil.
 */
@Composable
fun rememberOrientation(window: Int = 40): State<Orientation> {
    val context = LocalContext.current
    val state = remember { mutableStateOf(Orientation()) }

    DisposableEffect(window) {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val gravity = sm.getDefaultSensor(Sensor.TYPE_GRAVITY)
            ?: sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val rotation = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

        val buf = Array(window) { DoubleArray(3) }
        var count = 0
        var idx = 0
        val azSin = DoubleArray(window)
        val azCos = DoubleArray(window)
        var azCount = 0
        var azIdx = 0
        val rot = FloatArray(9)
        var lastEmit = 0L

        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                when (e.sensor.type) {
                    Sensor.TYPE_GRAVITY, Sensor.TYPE_ACCELEROMETER -> {
                        val x = e.values[0].toDouble()
                        val y = e.values[1].toDouble()
                        val z = e.values[2].toDouble()
                        val n = sqrt(x * x + y * y + z * z)
                        if (n < 1e-3) return
                        buf[idx][0] = x / n
                        buf[idx][1] = y / n
                        buf[idx][2] = z / n
                        idx = (idx + 1) % window
                        if (count < window) count++
                    }

                    Sensor.TYPE_ROTATION_VECTOR -> {
                        val v = if (e.values.size > 4) e.values.copyOf(4) else e.values
                        SensorManager.getRotationMatrixFromVector(rot, v)
                        // Sumbu kamera = -Z perangkat. Dunia: X=Timur, Y=Utara, Z=Atas
                        val east = -rot[2].toDouble()
                        val north = -rot[5].toDouble()
                        val az = atan2(east, north)
                        azSin[azIdx] = sin(az)
                        azCos[azIdx] = cos(az)
                        azIdx = (azIdx + 1) % window
                        if (azCount < window) azCount++
                    }
                }

                val now = SystemClock.uptimeMillis()
                if (count > 0 && now - lastEmit >= 60) {
                    lastEmit = now
                    var sx = 0.0
                    var sy = 0.0
                    var sz = 0.0
                    var minP = 999.0
                    var maxP = -999.0
                    for (i in 0 until count) {
                        sx += buf[i][0]; sy += buf[i][1]; sz += buf[i][2]
                        val p = Math.toDegrees(asin((-buf[i][2]).coerceIn(-1.0, 1.0)))
                        if (p < minP) minP = p
                        if (p > maxP) maxP = p
                    }
                    val n = sqrt(sx * sx + sy * sy + sz * sz).coerceAtLeast(1e-9)
                    var az = 0.0
                    if (azCount > 0) {
                        var ss = 0.0
                        var cc = 0.0
                        for (i in 0 until azCount) { ss += azSin[i]; cc += azCos[i] }
                        az = Math.toDegrees(atan2(ss, cc))
                        if (az < 0) az += 360.0
                    }
                    state.value = Orientation(
                        ux = sx / n, uy = sy / n, uz = sz / n,
                        azimuth = az,
                        hasCompass = rotation != null,
                        spread = maxP - minP,
                        ready = count >= window / 2,
                    )
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        gravity?.let { sm.registerListener(listener, it, SensorManager.SENSOR_DELAY_GAME) }
        rotation?.let { sm.registerListener(listener, it, SensorManager.SENSOR_DELAY_GAME) }

        onDispose { sm.unregisterListener(listener) }
    }
    return state
}
