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
import kotlin.math.sqrt

/**
 * Ses çubuğundaki sıvının küçük fizik modeli. Sıvı telefonun yerçekimine göre çubuğun bir ucuna
 * toplanır: telefon ters çevrilince öbür uca dökülür, eğilince yüzeyi yere paralel kalır.
 *
 * Dökülme gerçek su gibi: kaynak birikinti deliği olan bir kap gibi boşalır (Torricelli, akış
 * kalan suyun karekökü kadar), kopan her parça yerçekimiyle hızlanarak düşer ve öbür uçtaki
 * birikintiye ancak çarpınca eklenir. Akışın kalınlığı süreklilikten gelir (hızlandıkça incelir);
 * sonunda kuyruğu kopar ve o da düşer.
 *
 * Yüzey tek bir düz çizgi değil: [COLUMNS] tane yan yana su sütunu birbirini çeker (dalga
 * denklemi), böylece bir dökülme ya da sarsıntı yüzeyde yayılan, kenarlardan yansıyan ve yavaşça
 * sönen dalgalar yapar. Yaylar yarı-örtük Euler ile 12,5 ms'lik adımlarla ilerler (kararlı).
 */
internal class Slosh {
    /**
     * İki sıvı ayrı izlenir: beyaz (telefonun sesi) ve sarı (yükseltme). Sarı yağ gibi beyazın
     * üstünde yüzer: dökülürken önce yüzeydeki sarı akar, sonra beyaz; varınca sarı yine üstte
     * kalır. Değerler her sıvının kendi toplamı içindeki payı (her biri ayrı ayrı 1'e tamamlanır).
     */
    var whiteTop = 0f
        private set
    var whiteBottom = 1f
        private set
    var boostTop = 0f
        private set
    var boostBottom = 1f
        private set
    /** Tüm sıvının içinde beyazın payı (seviyeden): %140'ta 1/1,4 */
    fun whiteShare(): Float {
        val l = level.coerceIn(0f, 2f)
        return if (!boostPresent || l <= 1f) 1f else 1f / l
    }
    /** Üst / alt uçtaki birikintide duran sıvının toplam payı; kalan havada düşüyor */
    val top: Float get() = whiteShare().let { it * whiteTop + (1f - it) * boostTop }
    val bottom: Float get() = whiteShare().let { it * whiteBottom + (1f - it) * boostBottom }
    /** Eski adıyla: sıvının üst uçtaki payı */
    val pos: Float get() = top
    /** Havada düşen sıvının toplam payı */
    fun airShare(): Float {
        val ws = whiteShare()
        var air = 0f
        for (d in drops) air += d[2] * if (d[5] > 0f) 1f - ws else ws
        return air
    }
    /** Kaynaktan şu an kopan akış (toplamın payı/sn): + üst uca, - alt uca doğru */
    var flow = 0f
        private set
    /**
     * Düşen parçalar: kaynak ucundan uzaklık (çubuk boyu), hız, kendi sıvısındaki pay, yön
     * (+1 üst uca), kopma anındaki akış, tür (1 sarı, 0 beyaz)
     */
    val drops = ArrayList<FloatArray>()
    /** Yeni birikintiye çarpmanın sıçrama gücü (0..1) ve hangi uca çarptığı (+1 üst, -1 alt) */
    var splash = 0f
        private set
    var splashDir = 1f
        private set
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
    /** Kaynağın kenardan taşmaya başlaması: 0'dan 1'e yumuşakça açılır (ilk damlalar ince) */
    private var ramp = 0f
    private var rampDir = 0f

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
        val drop = 3 + next() % (COLUMNS - 6)
        kick(drop, if (rising) 5f else -3.2f)
        if (rising) repeat(2 + next() % 2) {
            if (bubbles.size < 8) bubbles += floatArrayOf(0.02f + rand() * 0.25f, 0.2f + rand() * 0.6f, 1f)
        }
    }

    /** Sıvının çubukta kapladığı boy (çubuk boyunun payı) */
    private fun fill() = (level * 0.5f).coerceIn(0.02f, 1f)

    /** Yüzeye yumuşak bir darbe: tek sütuna sert vurup sivri taç yapmasın diye beş sütuna yayılır */
    private fun kick(center: Int, amount: Float) {
        for (k in -2..2) {
            val i = center + k
            if (i in 0 until COLUMNS) speeds[i] += amount * KICK_SHAPE[k + 2]
        }
    }

    fun step(dtTotal: Float) {
        var left = dtTotal.coerceIn(0f, 0.1f)
        while (left > 0f) {
            val dt = min(left, 0.0125f)
            left -= dt
            pourStep(dt)
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

            wave += (abs(flow) * 0.25f + abs(levelVel) * 0.5f) * dt
            wave = (wave * exp(-2.6f * dt)).coerceIn(0f, 1f)
            phase += dt * (5f + 8f * wave)
            splash *= exp(-5f * dt)

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

    /** Şelale: kaynaktan parça kopar, parçalar düşer, çarpanlar yeni birikintiye eklenir */
    private fun pourStep(dt: Float) {
        val dir = if (targetPos > 0.5f) 1f else -1f
        // Telefon az eğikse (yerçekiminin çubuk boyunca payı küçük) su da yavaş akar
        val pull = (abs(gy) / 9.8f).coerceIn(0.3f, 1f)
        val fillLen = fill()
        val ws = whiteShare()
        // Sarı görünmüyorsa (yükseltme yok) onun payı görünmeden yeni uca geçer
        if (ws > 0.999f) {
            if (dir > 0f) { boostTop += boostBottom; boostBottom = 0f } else { boostBottom += boostTop; boostTop = 0f }
        }
        val source = if (dir > 0f) bottom else top
        if (source > 0f) {
            if (dir != rampDir) {
                rampDir = dir
                ramp = 0f
            }
            ramp += (1f - ramp) * (1f - exp(-RAMP_RATE * dt))
            val q = FLOW_RATE * sqrt(pull) * ramp * sqrt(source)
            // Önce yüzeydeki sarı akar, bitince alttaki beyaz
            val srcBoost = if (dir > 0f) boostBottom else boostTop
            val boost = ws < 0.999f && srcBoost > 0f
            val unit = if (boost) 1f - ws else ws
            val have = if (boost) srcBoost else (if (dir > 0f) whiteBottom else whiteTop)
            var m = min(have, q * dt / unit)
            // Son ince kuyruk kopar: kaynakta damla kadar su asılı kalmasın
            if ((have - m) * unit < 0.003f) m = have
            when {
                boost && dir > 0f -> boostBottom -= m
                boost -> boostTop -= m
                dir > 0f -> whiteBottom -= m
                else -> whiteTop -= m
            }
            drops += floatArrayOf(fillLen * (if (dir > 0f) bottom else top), START_SPEED, m, dir, q, if (boost) 1f else 0f)
            flow = dir * q
        } else {
            flow = 0f
            ramp = 0f
        }
        if (drops.isEmpty()) return
        val g = FALL_GRAVITY * pull
        val it = drops.iterator()
        while (it.hasNext()) {
            val d = it.next()
            d[1] += g * dt
            d[0] += d[1] * dt
            val toTop = d[3] > 0f
            val sinkSurface = 1f - fillLen * (if (toTop) top else bottom)
            if (d[0] >= sinkSurface) {
                // Çarptı: birikintiye eklenir, çarptığı yer içeri itilir, dalga yayılır
                val boost = d[5] > 0f
                when {
                    boost && toTop -> boostTop += d[2]
                    boost -> boostBottom += d[2]
                    toTop -> whiteTop += d[2]
                    else -> whiteBottom += d[2]
                }
                val hit = d[2] * (if (boost) 1f - ws else ws) * d[1]
                kick(COLUMNS / 2 - 1 + next() % 3, hit * 5f)
                wave = min(1f, wave + hit * 0.3f)
                splash = min(1f, splash + hit * 0.9f)
                splashDir = d[3]
                if (hit > 0.08f && next() % 4 == 0 && bubbles.size < 8) {
                    bubbles += floatArrayOf(0.05f + rand() * 0.2f, 0.25f + rand() * 0.5f, 1f)
                }
                it.remove()
            }
        }
        if (drops.isEmpty()) {
            // Yuvarlama artıkları: toplam tam 1 kalsın
            whiteBottom = whiteBottom.coerceIn(0f, 1f)
            whiteTop = 1f - whiteBottom
            if (whiteTop < 0.0005f) { whiteTop = 0f; whiteBottom = 1f } else if (whiteBottom < 0.0005f) { whiteBottom = 0f; whiteTop = 1f }
            boostBottom = boostBottom.coerceIn(0f, 1f)
            boostTop = 1f - boostBottom
            if (boostTop < 0.0005f) { boostTop = 0f; boostBottom = 1f } else if (boostBottom < 0.0005f) { boostBottom = 0f; boostTop = 1f }
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
            if (drops.isNotEmpty() || abs(flow) > 0f || abs(pos - targetPos) > 0.01f || wave > 0.03f) return false
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
        /** Dolu kaynağın akışı (toplamın saniyedeki payı); karekökle azalır: dolu çubuk ~0,9 sn'de boşalır */
        private const val FLOW_RATE = 2.2f
        /** Kenardan taşmanın açılma hızı (1/sn) */
        private const val RAMP_RATE = 7f
        /** Düşen suyun ivmesi (çubuk boyu / sn²): boydan boya ~0,35 sn'de düşer */
        private const val FALL_GRAVITY = 16f
        /** Kaynaktan kopan suyun ilk hızı (çubuk boyu / sn) */
        const val START_SPEED = 0.9f
        /** Sütunlar arası çekim; büyüdükçe dalga hızlı yayılır */
        private const val WAVE_STIFFNESS = 900f
        private val KICK_SHAPE = floatArrayOf(0.25f, 0.65f, 1f, 0.65f, 0.25f)
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
 * Serbest yüzey sütunlardan gelen pürüzsüz bir eğri; iki sıvı arasındaki sınır yüzeyi biraz
 * geriden izler. Dökülürken düşen su, kaynaktan yeni birikintiye uzanan incelen bir şerit.
 */
internal fun DrawScope.drawLiquid(s: Slosh, white: Color, boostBrush: Brush, track: Color) {
    val w = size.width
    val h = size.height
    val radius = CornerRadius(w / 2f, w / 2f)
    drawRoundRect(track, cornerRadius = radius)
    // Camın parlaması sıvının altında: sadece boş kısımda görünür, sıvıya süt tabakası katmaz
    drawLine(
        Color.White.copy(alpha = 0.18f),
        Offset(w * 0.28f, w * 0.6f),
        Offset(w * 0.28f, h - w * 0.6f),
        strokeWidth = w * 0.12f,
    )
    val container = Path().apply {
        addRoundRect(androidx.compose.ui.geometry.RoundRect(0f, 0f, w, h, radius))
    }
    val lvl = s.level.coerceIn(0f, 2f)
    val whiteLen = h * 0.5f * lvl.coerceAtMost(1f)
    // Sarı sadece gerçekten %100'ün üstüne çıkıldıysa (yay taşması sarı göstermesin)
    val boostLen = if (s.boostPresent) h * 0.5f * (lvl - 1f).coerceAtLeast(0f) else 0f
    val total = whiteLen + boostLen
    if (total < 0.5f) return
    // Sıvı iki uçta birikebilir; aradaki pay havada düşüyor (şelale). Her birikintide beyaz
    // dipte, sarı üstünde; ikisinin miktarı ayrı (dökülürken önce sarı geçer)
    val topWhite = whiteLen * s.whiteTop
    val bottomWhite = whiteLen * s.whiteBottom
    val topLen = topWhite + boostLen * s.boostTop
    val bottomLen = bottomWhite + boostLen * s.boostBottom
    val ripple = s.wave * w * 0.08f

    // flip: alt uçtaki birikintide +1, üst uçtakinde -1 (yüzey ters bakar)
    fun edge(y0: Float, mul: Float, flip: Float, len: Float): (Float) -> Float {
        // Sıvı azken dalga da küçük kalır (boş çubukta dev dalga olmasın)
        val depthMul = (len / (w * 1.5f)).coerceIn(0.25f, 1f)
        return { x ->
            val u = x / w
            y0 + s.slope * (x - w / 2f) +
                mul * depthMul * (flip * s.surfaceAt(u) * w + ripple * sin(s.phase + u * 5.6f))
        }
    }

    // Yüzey noktaları arası düz çizgi değil, orta noktalardan geçen eğri: köşesiz, akıcı
    val steps = 20
    fun trace(path: Path, f: (Float) -> Float, forward: Boolean, move: Boolean) {
        var px = if (forward) 0f else w
        var py = f(px)
        if (move) path.moveTo(px, py) else path.lineTo(px, py)
        for (k in 1..steps) {
            val i = if (forward) k else steps - k
            val x = w * i / steps
            val y = f(x)
            if (k == steps) {
                path.quadraticTo(px, py, x, y)
            } else {
                path.quadraticTo(px, py, (px + x) / 2f, (py + y) / 2f)
            }
            px = x
            py = y
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
        val hasBoost = boostLen >= 0.75f
        // Parlak çizgi sadece beyaz yüzeyde: turuncunun üstünde açık bir zar gibi duruyordu
        val shines = ArrayList<(Float) -> Float>(2)
        if (bottomLen >= 0.5f) {
            // Alt uçtaki birikinti: yüzey yukarıda, beyaz dipte, sarı onun üstünde
            val free = edge(h - bottomLen, 1f, 1f, bottomLen)
            val back = edge(h + w * 2f, 0f, 1f, bottomLen)
            val boostHere = bottomLen - bottomWhite
            if (hasBoost && boostHere >= 0.75f) {
                // Sarı katman dalgada bile en az bu kadar kalın görünür; sınır serbest yüzeyin
                // üstüne taşmasın (dalgada beyaz turuncunun üstünde görünüyordu)
                val minBoost = min(boostHere, 1.5f * density)
                val rawB = edge(h - bottomWhite, 0.45f, 1f, bottomLen)
                val boundary = { x: Float -> max(rawB(x), free(x) + minBoost) }
                fillBetween(free, boundary, boostBrush)
                fillBetween(boundary, back, whiteBrush)
            } else {
                fillBetween(free, back, whiteBrush)
                shines += free
            }
        }
        if (topLen >= 0.5f) {
            // Üst uçtaki birikinti (telefon ters): yüzey aşağıda
            val free = edge(topLen, 1f, -1f, topLen)
            val back = edge(-w * 2f, 0f, -1f, topLen)
            val boostHere = topLen - topWhite
            if (hasBoost && boostHere >= 0.75f) {
                val minBoost = min(boostHere, 1.5f * density)
                val rawB = edge(topWhite, 0.45f, -1f, topLen)
                val boundary = { x: Float -> min(rawB(x), free(x) - minBoost) }
                fillBetween(back, boundary, whiteBrush)
                fillBetween(boundary, free, boostBrush)
            } else {
                fillBetween(back, free, whiteBrush)
                shines += free
            }
        }
        drawStreams(s, total, hasBoost, whiteBrush, boostBrush)
        // Kabarcıklar: büyük birikintide dipten yüzeye doğru yükselir, yüzeye yaklaştıkça söner
        val bubbleColor = if (hasBoost) BoostShine else Color.Black
        val mainLen = max(topLen, bottomLen)
        for (b in s.bubbles) {
            val x = b[1] * w
            val fromBack = mainLen * b[0]
            val y = if (bottomLen >= topLen) h - fromBack else fromBack
            val r = (1.1f + b[2] * 1.1f) * density
            drawCircle(bubbleColor.copy(alpha = 0.35f * b[2]), r, Offset(x, y))
        }
        val shineColor = Color.White.copy(alpha = 0.6f)
        for (free in shines) {
            val shine = Path()
            trace(shine, free, forward = true, move = true)
            drawPath(shine, shineColor, style = Stroke(width = 1.5f * density))
        }
    }
}

/**
 * Düşen su: her yön için kaynağın yüzeyinden (hâlâ akıyorsa) en öndeki damlaya uzanan şerit.
 * Kalınlık kopma anındaki akışla doğru, hızla ters orantılı (süreklilik): düştükçe incelir, akış
 * azaldıkça kuyruk incelir ve sonunda kopup düşer. Ön uç yuvarlak bir damla.
 */
private fun DrawScope.drawStreams(s: Slosh, total: Float, hasBoost: Boolean, whiteBrush: Brush, boostBrush: Brush) {
    if (s.drops.isEmpty()) return
    val w = size.width
    val h = size.height
    val fillLen = total / h
    for (dir in floatArrayOf(1f, -1f)) {
        // Damlalar kopma sırasıyla eklenir: eski (en önde) başta. Kaynaktan öne doğru dizilsin
        val xs = ArrayList<Float>(48)
        val ys = ArrayList<Float>(48)
        val hws = ArrayList<Float>(48)
        val kinds = ArrayList<Boolean>(48)
        fun screenY(u: Float) = if (dir > 0f) h * (1f - u) else h * u
        fun centerX(u: Float) =
            w / 2f + s.slope * w * 0.3f + w * 0.06f * sin(s.phase * 2.3f + u * 7f) * u.coerceIn(0f, 1f)
        fun halfWidth(q: Float, v: Float) = w * (0.32f * q / v).coerceIn(0.035f, 0.4f)
        val emitting = s.flow * dir > 0f
        var newest: FloatArray? = null
        for (i in s.drops.indices.reversed()) if (s.drops[i][3] == dir) { newest = s.drops[i]; break }
        if (emitting && newest != null) {
            val src = if (dir > 0f) s.bottom else s.top
            val u = fillLen * src
            xs += centerX(u); ys += screenY(u); hws += halfWidth(abs(s.flow), Slosh.START_SPEED)
            kinds += hasBoost && newest[5] > 0f
        }
        for (i in s.drops.indices.reversed()) {
            val d = s.drops[i]
            if (d[3] != dir) continue
            xs += centerX(d[0]); ys += screenY(d[0]); hws += halfWidth(d[4], d[1])
            kinds += hasBoost && d[5] > 0f
        }
        if (xs.isEmpty()) continue
        val n = xs.size
        // Aynı renkteki ardışık parçalar tek şerit; renk değiştiği yerde şeritler bir nokta örtüşür
        var start = 0
        while (start < n) {
            var end = start
            while (end + 1 < n && kinds[end + 1] == kinds[start]) end++
            val last = min(end + 1, n - 1)
            if (last > start) {
                val path = Path()
                path.moveTo(xs[start] - hws[start], ys[start])
                for (k in start + 1..last) path.lineTo(xs[k] - hws[k], ys[k])
                for (k in last downTo start) path.lineTo(xs[k] + hws[k], ys[k])
                path.close()
                drawPath(path, if (kinds[start]) boostBrush else whiteBrush)
            }
            start = end + 1
        }
        // Ön uç: düşen suyun yuvarlak başı
        drawCircle(if (kinds[n - 1]) boostBrush else whiteBrush, hws[n - 1] * 1.15f, Offset(xs[n - 1], ys[n - 1]))
    }
    // Çarptığı yerde sıçrayan birkaç damla
    if (s.splash > 0.05f) {
        val toTop = s.splashDir > 0f
        val ySink = if (toTop) total * s.top else h - total * s.bottom
        val back = if (toTop) 1f else -1f
        for (k in 0..2) {
            val p = (s.phase * 1.3f + k * 0.37f) % 1f
            val dx = (k - 1) * w * 0.28f * (0.4f + p)
            val dy = back * w * 0.9f * p * (1f - p) * 4f * s.splash
            drawCircle(
                (if (hasBoost) BoostShine else Color.White).copy(alpha = 0.7f * (1f - p) * s.splash.coerceAtMost(1f)),
                (1f + s.splash) * density,
                Offset(w / 2f + dx, ySink + dy),
            )
        }
    }
}

/** Yükseltme sıvısının sıçrayan damla ve kabarcık rengi: beyaz değil açık sarı */
private val BoostShine = Color(0xFFFFE27A)
