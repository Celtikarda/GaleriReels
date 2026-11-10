package com.celtikarda.galerireels

import android.content.Context
import android.graphics.Bitmap
import androidx.annotation.StringRes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.coroutineContext

/** Akıllı temizliğin kategorileri; temizlik kartında her biri ayrı bir satır olarak görünür */
enum class SmartCategory(@StringRes private val titleRes: Int, @StringRes private val rowRes: Int) {
    SHORT(R.string.smart_short, R.string.smart_short_row),
    SCREENS(R.string.smart_screens, R.string.smart_screens_row),
    WHATSAPP(R.string.smart_whatsapp, R.string.smart_whatsapp_row),
    BLURRY(R.string.smart_blurry, R.string.smart_blurry_row);

    val title: String get() = str(titleRes)

    /** "Çok kısa videolar: 12 video (45 MB)" */
    fun row(count: String, size: String): String = str(rowRes, count, size)

    /** Kısa videolar hep video, bulanıklar hep fotoğraf; diğerleri seçili moda göre sayılır */
    fun countText(mode: MediaMode, n: Int): String = when (this) {
        SHORT -> MediaMode.VIDEOS.count(n)
        BLURRY -> MediaMode.PHOTOS.count(n)
        else -> mode.count(n)
    }
}

/**
 * Silinmeye aday içerikleri cihaz içinde (internetsiz) bulur: çok kısa videolar, ekran
 * görüntüleri/kayıtları, WhatsApp'tan gelenler ve bulanık fotoğraflar.
 * Beğenilen/favori içerikler buraya hiç gelmez (GalleryViewModel önce eler).
 */
object SmartCleanup {

    /** Bundan kısa videolar "yanlışlıkla çekilmiş" sayılır */
    internal const val SHORT_VIDEO_MS = 3_000L

    /** Laplace varyansı bunun altındaysa fotoğraf bulanık sayılır (256 px küçük resimde) */
    internal const val BLUR_LAPLACIAN_MAX = 30.0
    /** Parlaklık sapması bunun altındaysa (düz duvar, gökyüzü, siyah ekran) bulanık denmez */
    internal const val BLUR_MIN_CONTRAST = 18.0
    private const val BLUR_THUMB = 256

    private val SCREEN_WORDS = listOf("screenshot", "screen_shot", "screenrecord", "screen_record", "screen record", "screen recording", "ekran görüntü", "ekran kayd")
    private val WA_NAME = Regex("""-WA\d{3,}""", RegexOption.IGNORE_CASE)

    fun isShortVideo(item: VideoItem): Boolean = !item.isPhoto && item.durationMs in 1 until SHORT_VIDEO_MS

    fun isScreenCapture(item: VideoItem): Boolean {
        val text = "${item.albumPath}/${item.albumName.orEmpty()}/${item.name}".lowercase()
        return SCREEN_WORDS.any { it in text }
    }

    fun isWhatsApp(item: VideoItem): Boolean =
        "whatsapp" in item.albumPath.lowercase() || "whatsapp" in item.albumName.orEmpty().lowercase() || WA_NAME.containsMatchIn(item.name)

    /** Laplace varyansı ve kontrasta göre bulanık mı ([sharpness] null ise henüz ölçülmedi) */
    fun isBlurry(sharpness: Sharpness?): Boolean =
        sharpness != null && sharpness.contrast >= BLUR_MIN_CONTRAST && sharpness.laplacian < BLUR_LAPLACIAN_MAX

    /**
     * Hızlı (galeri bilgisinden) kategoriler. [duplicateIds] önce gelir (WhatsApp'ta iki kez
     * gelmiş videolar gibi), sonra en çok yer kaplayandan başlar.
     */
    fun quickGroups(candidates: List<VideoItem>, duplicateIds: Set<Long>): Map<SmartCategory, List<VideoItem>> {
        val order = compareBy<VideoItem> { it.id !in duplicateIds }.thenByDescending { it.sizeBytes }
        return mapOf(
            SmartCategory.SHORT to candidates.filter(::isShortVideo).sortedWith(order),
            SmartCategory.SCREENS to candidates.filter(::isScreenCapture).sortedWith(order),
            SmartCategory.WHATSAPP to candidates.filter(::isWhatsApp).sortedWith(order),
        )
    }

    // --- Bulanıklık

    data class Sharpness(val laplacian: Double, val contrast: Double)

    /**
     * Gri tonlamalı parlaklık dizisinden (genişlik × yükseklik) Laplace varyansı ve parlaklığın
     * standart sapması. Keskin kenar az ise Laplace varyansı düşük çıkar.
     */
    internal fun sharpnessOf(lum: IntArray, width: Int, height: Int): Sharpness {
        if (width < 3 || height < 3) return Sharpness(0.0, 0.0)
        var sum = 0.0
        var sumSq = 0.0
        for (v in lum) { sum += v; sumSq += v.toDouble() * v }
        val n = lum.size
        val mean = sum / n
        val contrast = kotlin.math.sqrt((sumSq / n - mean * mean).coerceAtLeast(0.0))

        var lSum = 0.0
        var lSq = 0.0
        var count = 0
        for (y in 1 until height - 1) {
            val row = y * width
            for (x in 1 until width - 1) {
                val i = row + x
                val l = (lum[i - 1] + lum[i + 1] + lum[i - width] + lum[i + width] - 4 * lum[i]).toDouble()
                lSum += l
                lSq += l * l
                count++
            }
        }
        val lMean = lSum / count
        return Sharpness(lSq / count - lMean * lMean, contrast)
    }

    private val cache = ConcurrentHashMap<String, Sharpness>()
    private var diskLoaded = false

    /**
     * Fotoğrafların keskinliğini ölçer (önbellekte olanları tekrar ölçmez) ve bulanık olanları
     * en çok yer kaplayandan başlayarak döner. [onProgress] ara sonuçları verir.
     */
    suspend fun findBlurry(
        context: Context,
        photos: List<VideoItem>,
        onProgress: (List<VideoItem>) -> Unit = {},
    ): List<VideoItem> = withContext(Dispatchers.IO) {
        loadCache(context)
        val targets = photos.filter { it.isPhoto && !isScreenCapture(it) }
        var dirty = 0
        var lastReport = System.currentTimeMillis()
        for (item in targets) {
            coroutineContext.ensureActive()
            if (cache.containsKey(item.key)) continue
            cache[item.key] = measure(context, item) ?: Sharpness(-1.0, 0.0)
            dirty++
            if (dirty % 200 == 0) saveCache(context)
            if (System.currentTimeMillis() - lastReport > 1500) {
                lastReport = System.currentTimeMillis()
                onProgress(blurryOf(targets))
            }
        }
        if (dirty > 0) saveCache(context)
        blurryOf(targets)
    }

    private fun blurryOf(photos: List<VideoItem>): List<VideoItem> =
        photos.filter { isBlurry(cache[it.key]) }.sortedByDescending { it.sizeBytes }

    private suspend fun measure(context: Context, item: VideoItem): Sharpness? {
        val thumb = runCatching { loadThumbBitmap(context, item.uri, BLUR_THUMB) }.getOrNull() ?: return null
        // Galerinin küçük resmi kare ya da farklı boyutta olabilir; ölçümün tutarlı olması için aynı boya getir
        val scale = BLUR_THUMB.toFloat() / maxOf(thumb.width, thumb.height)
        val bmp = if (scale < 1f) {
            Bitmap.createScaledBitmap(thumb, (thumb.width * scale).toInt().coerceAtLeast(3), (thumb.height * scale).toInt().coerceAtLeast(3), true)
        } else {
            thumb
        }
        val w = bmp.width
        val h = bmp.height
        val px = IntArray(w * h)
        bmp.getPixels(px, 0, w, 0, 0, w, h)
        val lum = IntArray(px.size) { i ->
            val c = px[i]
            (((c shr 16) and 0xFF) * 299 + ((c shr 8) and 0xFF) * 587 + (c and 0xFF) * 114) / 1000
        }
        return sharpnessOf(lum, w, h)
    }

    private fun cacheFile(context: Context) = File(context.cacheDir, "sharpness-v1.txt")

    private fun loadCache(context: Context) {
        if (diskLoaded) return
        diskLoaded = true
        runCatching {
            val f = cacheFile(context)
            if (!f.exists()) return
            f.forEachLine(Charsets.UTF_8) { line ->
                val parts = line.split('\t')
                if (parts.size == 3) {
                    val l = parts[1].toDoubleOrNull()
                    val c = parts[2].toDoubleOrNull()
                    if (l != null && c != null) cache[parts[0]] = Sharpness(l, c)
                }
            }
        }
    }

    private fun saveCache(context: Context) {
        runCatching {
            val text = buildString {
                for ((k, v) in cache) append(k).append('\t').append("%.2f".format(java.util.Locale.ROOT, v.laplacian)).append('\t')
                    .append("%.2f".format(java.util.Locale.ROOT, v.contrast)).append('\n')
            }
            cacheFile(context).writeText(text, Charsets.UTF_8)
        }
    }
}
