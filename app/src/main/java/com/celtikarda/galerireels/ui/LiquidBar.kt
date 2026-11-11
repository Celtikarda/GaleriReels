package com.celtikarda.galerireels.ui

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Ses çubuğundaki sıvının küçük fizik modeli. Sıvı telefonun yerçekimine göre çubuğun bir ucuna
 * toplanır: telefon ters çevrilince öbür uca akar, eğilince yüzeyi yere paralel kalır, hızlı
 * hareketlerde ve her tuşa basışta çalkalanıp yavaşça durulur. Yaylar yarı-örtük Euler ile
 * adımlanır (dt en fazla 25 ms, kararlı).
 */
internal class Slosh {
    /** 0: sıvı ekranın alt ucunda, 1: üst ucunda; arada akıyor */
    var pos = 0f
    var vel = 0f
    /** Yüzeyin eğimi (dikey / yatay); yerçekimi yana kayınca değişir */
    var slope = 0f
    var slopeVel = 0f
    /** Yüzeydeki dalganın büyüklüğü (0..1) ve fazı */
    var wave = 0f
    var phase = 0f
    /** Gösterilen seviye: 0..1 telefonun sesi (beyaz), 1..2 yükseltme (sarı); su dökülür gibi değişir */
    var level = 0f
    var levelVel = 0f
    var targetLevel = 0f
    var targetPos = 0f
    var targetSlope = 0f
    /** Telefonun son belirgin hareketi (System.nanoTime) */
    var lastMotionNanos = 0L
    private var gx = 0f
    private var gy = 9.8f
    private var hasGravity = false

    /** Ekran koordinatlarında aşağı yön (y aşağı doğru artar), m/s² */
    fun onGravity(downX: Float, downY: Float, now: Long) {
        if (!hasGravity) {
            gx = downX
            gy = downY
            hasGravity = true
        } else {
            val nx = gx + (downX - gx) * 0.3f
            val ny = gy + (downY - gy) * 0.3f
            if (abs(nx - gx) + abs(ny - gy) > 0.12f) lastMotionNanos = now
            gx = nx
            gy = ny
        }
        // Telefon masada yatıyorsa (yerçekimi ekrana dik) son yön korunur
        if (hypot(gx, gy) < 3f) return
        if (gy > 1.2f) targetPos = 0f else if (gy < -1.2f) targetPos = 1f
        val along = if (gy >= 0f) max(gy, 2f) else min(gy, -2f)
        targetSlope = (-gx / along).coerceIn(-1.4f, 1.4f)
    }

    /** Tuşa basınca: yeni seviye ve dökülme dalgası */
    fun pour(target: Float) {
        targetLevel = target.coerceIn(0f, 2f)
        wave = min(1f, wave + 0.35f)
    }

    fun step(dtTotal: Float) {
        var left = dtTotal.coerceIn(0f, 0.1f)
        while (left > 0f) {
            val dt = min(left, 0.0125f)
            left -= dt
            vel += (55f * (targetPos - pos) - 7f * vel) * dt
            pos += vel * dt
            if (pos < 0f || pos > 1f) {
                // Uca çarpınca sekip sıçrar
                wave = min(1f, wave + abs(vel) * 0.35f)
                pos = pos.coerceIn(0f, 1f)
                vel = -vel * 0.25f
            }
            slopeVel += (90f * (targetSlope - slope) - 5f * slopeVel) * dt
            slope += slopeVel * dt
            levelVel += (120f * (targetLevel - level) - 12f * levelVel) * dt
            level += levelVel * dt
            wave += (abs(vel) * 1.2f + abs(slopeVel) * 0.08f + abs(levelVel) * 0.8f) * dt
            wave = (wave * exp(-2.4f * dt)).coerceIn(0f, 1f)
            phase += dt * (7f + 10f * wave)
        }
    }

    val settled: Boolean
        get() = abs(vel) < 0.02f && abs(pos - targetPos) < 0.01f && wave < 0.03f &&
            abs(slopeVel) < 0.05f && abs(levelVel) < 0.02f
}

/** Telefonun yerçekimi sensörünü dinler, aşağı yönü ekranın dönüşüne göre çevirip [onDown]'a verir */
internal fun listenGravity(context: Context, rotation: () -> Int, onDown: (Float, Float) -> Unit): () -> Unit {
    val manager = context.getSystemService(SensorManager::class.java) ?: return {}
    val sensor = manager.getDefaultSensor(Sensor.TYPE_GRAVITY)
        ?: manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        ?: return {}
    val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val ax = event.values[0]
            val ay = event.values[1]
            // Cihaz ekseninden ekran eksenine (x sağ, y yukarı)
            val (sx, sy) = when (rotation()) {
                Surface.ROTATION_90 -> -ay to ax
                Surface.ROTATION_180 -> -ax to -ay
                Surface.ROTATION_270 -> ay to -ax
                else -> ax to ay
            }
            // Sensör yukarı doğru tepkiyi ölçer; aşağı yön tersidir (y aşağı doğru)
            onDown(-sx, sy)
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }
    manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
    return { manager.unregisterListener(listener) }
}

/**
 * Çubuğu çizer: %100'lük beyaz sıvı yerçekimi tarafında, sarı yükseltme sıvısı onun üstünde.
 * Serbest yüzey dalgalı ve eğimli, üstünde ince parlak bir çizgi var.
 */
internal fun DrawScope.drawLiquid(s: Slosh, white: Color, boostBrush: Brush, track: Color) {
    val w = size.width
    val h = size.height
    val radius = CornerRadius(w / 2f, w / 2f)
    drawRoundRect(track, cornerRadius = radius)
    val container = Path().apply {
        addRoundRect(androidx.compose.ui.geometry.RoundRect(0f, 0f, w, h, radius))
    }
    val lvl = s.level.coerceIn(0f, 2f)
    val whiteLen = h * 0.5f * lvl.coerceAtMost(1f)
    val boostLen = h * 0.5f * (lvl - 1f).coerceAtLeast(0f)
    val total = whiteLen + boostLen
    if (total < 0.5f) return
    // Akarken sıvı biraz uzar (ince akış gibi)
    val shown = min(h, total * (1f + 1.2f * abs(s.vel)))
    val scale = shown / total
    val start = (1f - s.pos) * (h - shown)
    val end = start + shown
    val amp = s.wave * w * 0.35f
    val far = w * 2f

    fun edge(y0: Float, waveMul: Float): (Float) -> Float = { x ->
        y0 + s.slope * (x - w / 2f) + amp * waveMul * sin(s.phase + x / w * 2f * PI.toFloat() * 0.9f)
    }

    fun fillBetween(a: (Float) -> Float, b: (Float) -> Float, brush: Brush) {
        val steps = 10
        val path = Path()
        for (i in 0..steps) {
            val x = w * i / steps
            if (i == 0) path.moveTo(x, a(x)) else path.lineTo(x, a(x))
        }
        for (i in steps downTo 0) {
            val x = w * i / steps
            path.lineTo(x, b(x))
        }
        path.close()
        drawPath(path, brush)
    }

    val whiteBrush = SolidColor(white)
    clipPath(container) {
        val free: (Float) -> Float
        if (s.pos < 0.5f) {
            // Sıvı alt uçta: yüzey yukarıda
            free = edge(start, 1f)
            val boundary = edge(end - whiteLen * scale, 0.4f)
            val back = if (end >= h - 1f) edge(h + far, 0f) else edge(end, 0.3f)
            fillBetween(free, boundary, boostBrush)
            fillBetween(boundary, back, whiteBrush)
        } else {
            // Ters: sıvı üst uçta, yüzey aşağıda
            free = edge(end, 1f)
            val boundary = edge(start + whiteLen * scale, 0.4f)
            val back = if (start <= 1f) edge(-far, 0f) else edge(start, 0.3f)
            fillBetween(back, boundary, whiteBrush)
            fillBetween(boundary, free, boostBrush)
        }
        val shine = Path()
        for (i in 0..10) {
            val x = w * i / 10
            if (i == 0) shine.moveTo(x, free(x)) else shine.lineTo(x, free(x))
        }
        drawPath(shine, Color.White.copy(alpha = 0.55f), style = Stroke(width = 1.5f * density))
    }
    // Kenardaki hafif cam parlaması
    drawLine(
        Color.White.copy(alpha = 0.18f),
        Offset(w * 0.28f, w * 0.6f),
        Offset(w * 0.28f, h - w * 0.6f),
        strokeWidth = w * 0.12f,
    )
}
