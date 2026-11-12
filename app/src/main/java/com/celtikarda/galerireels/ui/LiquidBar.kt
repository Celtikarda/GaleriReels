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
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Ses çubuğundaki sıvının küçük fizik modeli. Sıvı telefonun yerçekimine göre çubuğun bir ucuna
 * toplanır: telefon ters çevrilince öbür uca akar, eğilince yüzeyi yere paralel kalır.
 *
 * Yüzey tek bir düz çizgi değil: [COLUMNS] tane yan yana su sütunu birbirini çeker (dalga
 * denklemi), böylece bir dökülme ya da sarsıntı yüzeyde yayılan, kenarlardan yansıyan ve yavaşça
 * sönen dalgalar yapar. Eğim değişince sıvı geride kalıp öbür tarafa çarpar (çalkalanma).
 * Yaylar yarı-örtük Euler ile 12,5 ms'lik adımlarla ilerler (kararlı).
 */
internal class Slosh {
    /** 0: sıvı ekranın alt ucunda, 1: üst ucunda; arada akıyor */
    var pos = 0f
    var vel = 0f
    /** Yüzeyin ortalama eğimi (dikey / yatay); yerçekimi yana kayınca değişir */
    var slope = 0f
    var slopeVel = 0f
    /** Genel çalkantı (0..1): ince dalgacıkların büyüklüğü */
    var wave = 0f
    var phase = 0f
    /** Sütunların yüzeydeki yükseklik farkı (çubuk genişliği cinsinden) ve hızları */
    val heights = FloatArray(COLUMNS)
    private val speeds = FloatArray(COLUMNS)
    /** Gösterilen seviye: 0..1 telefonun sesi (beyaz), 1..2 yükseltme (sarı); su dökülür gibi değişir */
    var level = 0f
    var levelVel = 0f
    var targetLevel = 0f
    var targetPos = 0f
    var targetSlope = 0f
    /**
     * Sarı yükseltme sıvısı var mı. Sadece hedef %100'ün üstüne çıkınca açılır; %100'ün altındaki
     * bir seviyeye dökülürken yayın kısa taşması sarı göstermesin diye.
     */
    var boostPresent = false
        private set
    /** Yükselen kabarcıklar: (yüzeye uzaklık 0..1, yatay konum 0..1, ömür) */
    val bubbles = ArrayList<FloatArray>()
    /** Telefonun son belirgin hareketi (System.nanoTime) */
    var lastMotionNanos = 0L
    private var gx = 0f
    private var gy = 9.8f
    private var hasGravity = false
    private var seed = 12345

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

    /**
     * Telefonun yerçekimsiz ivmesi (ekran ekseninde, m/s²): yana sarsınca sıvı geride kalıp öbür
     * kenara çarpar, aşağı-yukarı sarsınca yüzey zıplar.
     */
    fun onShake(sideways: Float, along: Float) {
        // Küçük titremeler (elde tutarken) yok sayılır; kuvvet [step]'te zamanla uygulanır ki
        // sensörün olay sıklığından bağımsız olsun
        shakeSide = if (abs(sideways) < 0.6f) 0f else sideways.coerceIn(-12f, 12f)
        shakeUp = if (abs(along) < 0.6f) 0f else along.coerceIn(-12f, 12f)
    }

    private var shakeSide = 0f
    private var shakeUp = 0f

    /** Tuşa basınca: yeni seviye, damladığı yerde çukur + kabarcıklar */
    fun pour(target: Float) {
        targetLevel = target.coerceIn(0f, 2f)
        if (targetLevel > 1.001f) boostPresent = true
        wave = min(1f, wave + 0.35f)
        val rising = targetLevel >= level
        // Su yukarıdan bir noktaya damlar: o nokta çöker, dalga iki yana yayılır
        val drop = 2 + next() % (COLUMNS - 4)
        val push = if (rising) 6.5f else -4f
        for (d in -1..1) speeds[drop + d] += push * if (d == 0) 1f else 0.5f
        if (rising) repeat(2 + next() % 2) {
            if (bubbles.size < 8) bubbles += floatArrayOf(0.02f + rand() * 0.25f, 0.2f + rand() * 0.6f, 1f)
        }
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
                val hit = abs(vel)
                wave = min(1f, wave + hit * 0.35f)
                for (i in 0 until COLUMNS) speeds[i] += hit * 3f * if (i % 3 == 0) 1f else -0.5f
                pos = pos.coerceIn(0f, 1f)
                vel = -vel * 0.25f
            }
            // Sarsıntı: yana ivmede sıvı geride kalır, aşağı-yukarıda yüzey zıplar
            if (shakeSide != 0f || shakeUp != 0f) {
                slopeVel += shakeSide * 1.2f * dt
                for (i in 0 until COLUMNS) {
                    val x = i / (COLUMNS - 1f) - 0.5f
                    speeds[i] += (shakeSide * x * 1.7f + shakeUp * 0.4f * if (i % 2 == 0) 1f else -1f) * dt
                }
                wave = min(1f, wave + (abs(shakeSide) + abs(shakeUp)) * 0.15f * dt)
                val fade = exp(-10f * dt)
                shakeSide *= fade
                shakeUp *= fade
            }
            // Eğim yayı yumuşak ve az sönümlü: sıvı yeni eğime bir iki kez gidip gelerek oturur
            val slopeAcc = 48f * (targetSlope - slope) - 3.4f * slopeVel
            slopeVel += slopeAcc * dt
            slope += slopeVel * dt
            levelVel += (120f * (targetLevel - level) - 12f * levelVel) * dt
            level += levelVel * dt
            if (boostPresent && targetLevel <= 1f && level <= 1.002f) boostPresent = false

            // Sütunlar: komşusuna yaslanır (dalga yayılır), yerine döner, sürtünmeyle söner.
            // Eğim hızla değişirken bir yan kabarır, öbür yan çöker.
            var mean = 0f
            for (i in 0 until COLUMNS) {
                val l = heights[if (i == 0) 0 else i - 1]
                val r = heights[if (i == COLUMNS - 1) i else i + 1]
                val x = i / (COLUMNS - 1f) - 0.5f
                speeds[i] += (WAVE_STIFFNESS * (l + r - 2f * heights[i]) - 8f * heights[i] -
                    2.1f * speeds[i] - slopeAcc * x * 0.25f) * dt
            }
            for (i in 0 until COLUMNS) {
                heights[i] = (heights[i] + speeds[i] * dt).coerceIn(-0.9f, 0.9f)
                mean += heights[i]
            }
            // Su miktarı korunur: yüzey ortalaması kaymaz
            mean /= COLUMNS
            for (i in 0 until COLUMNS) heights[i] -= mean

            wave += (abs(vel) * 1.2f + abs(levelVel) * 0.5f) * dt
            wave = (wave * exp(-2.6f * dt)).coerceIn(0f, 1f)
            phase += dt * (5f + 8f * wave)

            val it = bubbles.iterator()
            while (it.hasNext()) {
                val b = it.next()
                b[0] += dt * (0.55f + b[2] * 0.3f)
                b[1] += sin(phase * 1.7f + b[0] * 9f) * dt * 0.08f
                b[2] -= dt * 0.9f
                if (b[0] >= 1f || b[2] <= 0f) it.remove()
            }
        }
    }

    /** Yüzeyin x konumundaki (0..1) yüksekliği: sütunlar arası pürüzsüz ara değer */
    fun surfaceAt(x: Float): Float {
        val f = x.coerceIn(0f, 1f) * (COLUMNS - 1)
        val i = f.toInt().coerceAtMost(COLUMNS - 2)
        val t = f - i
        val s = t * t * (3f - 2f * t)
        return heights[i] + (heights[i + 1] - heights[i]) * s
    }

    val settled: Boolean
        get() {
            if (abs(vel) > 0.02f || abs(pos - targetPos) > 0.01f || wave > 0.03f) return false
            if (abs(slopeVel) > 0.05f || abs(levelVel) > 0.02f || bubbles.isNotEmpty()) return false
            for (i in 0 until COLUMNS) if (abs(heights[i]) > 0.025f || abs(speeds[i]) > 0.12f) return false
            return true
        }

    private fun next(): Int {
        seed = seed * 1103515245 + 12345
        return (seed ushr 16) and 0x7fff
    }

    private fun rand() = next() / 32767f

    companion object {
        const val COLUMNS = 14
        /** Sütunlar arası çekim; büyüdükçe dalga hızlı yayılır */
        private const val WAVE_STIFFNESS = 900f
    }
}

/**
 * Telefonun yerçekimi sensörünü dinler, aşağı yönü ekranın dönüşüne göre çevirip [onDown]'a,
 * yerçekimsiz ivmeyi (sarsıntı) [onShake]'e verir.
 */
internal fun listenGravity(
    context: Context,
    rotation: () -> Int,
    onShake: (Float, Float) -> Unit = { _, _ -> },
    onDown: (Float, Float) -> Unit,
): () -> Unit {
    val manager = context.getSystemService(SensorManager::class.java) ?: return {}
    val gravity = manager.getDefaultSensor(Sensor.TYPE_GRAVITY)
        ?: manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        ?: return {}
    val linear = manager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)

    fun toScreen(ax: Float, ay: Float): Pair<Float, Float> = when (rotation()) {
        Surface.ROTATION_90 -> -ay to ax
        Surface.ROTATION_180 -> -ax to -ay
        Surface.ROTATION_270 -> ay to -ax
        else -> ax to ay
    }

    val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            // Cihaz ekseninden ekran eksenine (x sağ, y yukarı)
            val (sx, sy) = toScreen(event.values[0], event.values[1])
            if (event.sensor.type == Sensor.TYPE_LINEAR_ACCELERATION) {
                // Telefon sağa ivmelenince sıvı sola kalır; aşağı yön y aşağı doğru
                onShake(-sx, sy)
            } else {
                // Sensör yukarı doğru tepkiyi ölçer; aşağı yön tersidir (y aşağı doğru)
                onDown(-sx, sy)
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }
    manager.registerListener(listener, gravity, SensorManager.SENSOR_DELAY_GAME)
    if (linear != null) manager.registerListener(listener, linear, SensorManager.SENSOR_DELAY_GAME)
    return { manager.unregisterListener(listener) }
}

/**
 * Çubuğu çizer: %100'lük beyaz sıvı yerçekimi tarafında, sarı yükseltme sıvısı onun üstünde.
 * Serbest yüzey sütunlardan gelen pürüzsüz bir eğri, üstünde ince parlak bir çizgi var; iki sıvı
 * arasındaki sınır yüzeyi biraz geriden izler.
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
    // Sarı sadece gerçekten %100'ün üstüne çıkıldıysa (yay taşması sarı göstermesin)
    val boostLen = if (s.boostPresent) h * 0.5f * (lvl - 1f).coerceAtLeast(0f) else 0f
    val total = whiteLen + boostLen
    if (total < 0.5f) return
    // Akarken sıvı biraz uzar (ince akış gibi)
    val shown = min(h, total * (1f + 1.2f * abs(s.vel)))
    val scale = shown / total
    val start = (1f - s.pos) * (h - shown)
    val end = start + shown
    // Sıvı azken dalga da küçük kalır (boş çubukta dev dalga olmasın)
    val depthMul = (shown / (w * 1.5f)).coerceIn(0.25f, 1f)
    val ripple = s.wave * w * 0.08f
    val flip = if (s.pos < 0.5f) 1f else -1f

    fun edge(y0: Float, mul: Float): (Float) -> Float = { x ->
        val u = x / w
        y0 + s.slope * (x - w / 2f) +
            mul * depthMul * (flip * s.surfaceAt(u) * w + ripple * sin(s.phase + u * 5.6f))
    }

    val steps = 16
    fun trace(path: Path, f: (Float) -> Float, forward: Boolean, move: Boolean) {
        for (k in 0..steps) {
            val i = if (forward) k else steps - k
            val x = w * i / steps
            if (k == 0 && move) path.moveTo(x, f(x)) else path.lineTo(x, f(x))
        }
    }

    fun fillBetween(a: (Float) -> Float, b: (Float) -> Float, brush: Brush) {
        val path = Path()
        trace(path, a, forward = true, move = true)
        trace(path, b, forward = false, move = false)
        path.close()
        drawPath(path, brush)
    }

    val whiteBrush = SolidColor(white)
    clipPath(container) {
        val free: (Float) -> Float
        val hasBoost = boostLen * scale >= 0.75f
        if (s.pos < 0.5f) {
            // Sıvı alt uçta: yüzey yukarıda
            free = edge(start, 1f)
            val back = if (end >= h - 1f) edge(h + w * 2f, 0f) else edge(end, 0.35f)
            if (hasBoost) {
                val boundary = edge(end - whiteLen * scale, 0.45f)
                fillBetween(free, boundary, boostBrush)
                fillBetween(boundary, back, whiteBrush)
            } else {
                fillBetween(free, back, whiteBrush)
            }
        } else {
            // Ters: sıvı üst uçta, yüzey aşağıda
            free = edge(end, 1f)
            val back = if (start <= 1f) edge(-w * 2f, 0f) else edge(start, 0.35f)
            if (hasBoost) {
                val boundary = edge(start + whiteLen * scale, 0.45f)
                fillBetween(back, boundary, whiteBrush)
                fillBetween(boundary, free, boostBrush)
            } else {
                fillBetween(back, free, whiteBrush)
            }
        }
        // Kabarcıklar: dipten yüzeye doğru yükselir, yüzeye yaklaştıkça söner
        val bubbleColor = if (hasBoost) Color.White else Color.Black
        for (b in s.bubbles) {
            val x = b[1] * w
            val fromBack = shown * b[0]
            val y = if (s.pos < 0.5f) end - fromBack else start + fromBack
            val r = (1.1f + b[2] * 1.1f) * density
            drawCircle(bubbleColor.copy(alpha = 0.35f * b[2]), r, Offset(x, y))
        }
        val shine = Path()
        trace(shine, free, forward = true, move = true)
        drawPath(shine, Color.White.copy(alpha = 0.6f), style = Stroke(width = 1.5f * density))
    }
    // Kenardaki hafif cam parlaması
    drawLine(
        Color.White.copy(alpha = 0.18f),
        Offset(w * 0.28f, w * 0.6f),
        Offset(w * 0.28f, h - w * 0.6f),
        strokeWidth = w * 0.12f,
    )
}

