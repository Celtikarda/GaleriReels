package com.celtikarda.galerireels

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

/**
 * Görüntü işleme (64-bit Algısal Fark Özeti — dHash) ve süre yakınlığı ile galerideki
 * kopya ya da birbirine çok benzeyen videoları cihaz içinde (internetsiz) tespit eder.
 *
 * Örneğin WhatsApp'tan iki kez gelmiş, farklı çözünürlükte yeniden kaydedilmiş ya da
 * arka arkaya çekilmiş aynı videoları bulup fazlalık kopyaları temizlik için ayırır.
 */
object DuplicateFinder {

    /** İki videonun kopya sayılması için en fazla süre farkı (ms) */
    private const val MAX_DURATION_DIFF_MS = 2_500L
    /** 64-bit dHash üzerinde izin verilen en fazla bit farkı (Hamming uzaklığı, 64 üzerinden 10) */
    internal const val MAX_HAMMING_DISTANCE = 10

    private val memoryCache = ConcurrentHashMap<String, Long>()
    private var diskLoaded = false

    /**
     * Galerideki videoları tarar; her benzer/kopya grubunda en iyi kopyayı (beğenilen/favori ya da
     * en yüksek çözünürlüklü olanı) koruyup silinebilecek fazlalık kopyaları döner.
     */
    suspend fun findExtraCopies(
        context: Context,
        videos: List<VideoItem>,
        keepKeys: Set<String>,
    ): List<VideoItem> = withContext(Dispatchers.IO) {
        if (videos.size < 2) return@withContext emptyList()
        loadCacheIfNeeded(context)

        // Sadece süresi başka bir videoya yakın olan adayların dHash özetini çıkar (hızlı eleme)
        val sortedByDur = videos.filter { it.durationMs > 0 }.sortedBy { it.durationMs }
        val candidates = HashSet<VideoItem>()
        for (i in sortedByDur.indices) {
            val a = sortedByDur[i]
            if (i > 0 && abs(a.durationMs - sortedByDur[i - 1].durationMs) <= MAX_DURATION_DIFF_MS) {
                candidates += a
                candidates += sortedByDur[i - 1]
            }
        }
        if (candidates.size < 2) return@withContext emptyList()

        val hashes = HashMap<String, Long>()
        var cacheDirty = false
        for (video in candidates) {
            val cached = memoryCache[video.key]
            if (cached != null) {
                if (cached != 0L) hashes[video.key] = cached
                continue
            }
            val computed = computeVideoDHash(context, video) ?: 0L
            memoryCache[video.key] = computed
            cacheDirty = true
            if (computed != 0L) hashes[video.key] = computed
        }
        if (cacheDirty) saveCache(context)

        selectExtraCopies(candidates.toList(), hashes, keepKeys)
    }

    /**
     * 9×8 gri tonlamalı parlaklık dizisinden (72 eleman) 64-bit yatay fark özeti (dHash) üretir.
     * Tek renk (düz siyah/beyaz) karelerde yanlış eşleşmeyi önlemek için 0L döner.
     */
    internal fun computeDHashFromLuminance(luminance9x8: IntArray): Long {
        require(luminance9x8.size == 72)
        val min = luminance9x8.minOrNull() ?: 0
        val max = luminance9x8.maxOrNull() ?: 0
        // Kontrastı çok düşük (düz renk) kareler ayırt edici değildir
        if (max - min < 12) return 0L

        var hash = 0L
        var bit = 0
        for (y in 0 until 8) {
            val rowOffset = y * 9
            for (x in 0 until 8) {
                if (luminance9x8[rowOffset + x] > luminance9x8[rowOffset + x + 1]) {
                    hash = hash or (1L shl bit)
                }
                bit++
            }
        }
        // 0L "geçersiz/hesaplanamadı" anlamında kullanıldığı için 0 çıkarsa en üst biti işaretle
        return if (hash == 0L) 1L else hash
    }

    /** İki 64-bit dHash arasındaki bit farkı (Hamming uzaklığı: 0 = birebir aynı, 64 = tamamen zıt) */
    internal fun hammingDistance(a: Long, b: Long): Int = java.lang.Long.bitCount(a xor b)

    /**
     * Verilen videolar ve dHash özetlerinden benzer/kopya gruplarını bulur, her grupta en iyi
     * videoyu (beğenilen/favori veya en yüksek çözünürlüklü) ayırıp fazlalık kopyaları döner.
     */
    internal fun selectExtraCopies(
        candidates: List<VideoItem>,
        hashes: Map<String, Long>,
        keepKeys: Set<String>,
    ): List<VideoItem> {
        val sorted = candidates.sortedBy { it.durationMs }
        val parent = IntArray(sorted.size) { it }
        fun find(x: Int): Int {
            var r = x
            while (parent[r] != r) r = parent[r]
            var c = x
            while (c != r) {
                val nxt = parent[c]
                parent[c] = r
                c = nxt
            }
            return r
        }
        fun union(a: Int, b: Int) {
            val ra = find(a)
            val rb = find(b)
            if (ra != rb) parent[rb] = ra
        }

        for (i in sorted.indices) {
            val a = sorted[i]
            val hashA = hashes[a.key]
            for (j in i + 1 until sorted.size) {
                val b = sorted[j]
                if (b.durationMs - a.durationMs > MAX_DURATION_DIFF_MS) break
                val exactFileMatch = a.sizeBytes == b.sizeBytes && a.durationMs == b.durationMs
                val hashMatch = hashA != null && hashes[b.key]?.let { hashB ->
                    hammingDistance(hashA, hashB) <= MAX_HAMMING_DISTANCE
                } == true
                if (exactFileMatch || hashMatch) {
                    union(i, j)
                }
            }
        }

        val groups = sorted.indices.groupBy { find(it) }.values.filter { it.size >= 2 }
        val extras = ArrayList<VideoItem>()
        for (indices in groups) {
            val group = indices.map { sorted[it] }
            // Korunacak asıl kopya: önce beğenilen/favori olan, sonra en yüksek çözünürlük, sonra en büyük boyut, sonra en eski tarih
            val keeper = group.maxWithOrNull(
                compareBy<VideoItem> { it.key in keepKeys }
                    .thenBy { it.width.toLong() * it.height.toLong() }
                    .thenBy { it.sizeBytes }
                    .thenByDescending { it.dateMillis },
            ) ?: continue
            for (item in group) {
                if (item.id != keeper.id && item.key !in keepKeys) {
                    extras += item
                }
            }
        }
        return extras.sortedByDescending { it.sizeBytes }
    }

    private suspend fun computeVideoDHash(context: Context, video: VideoItem): Long? {
        val thumb = runCatching { loadThumbBitmap(context, video.uri, 96) }.getOrNull() ?: return null
        val small = Bitmap.createScaledBitmap(thumb, 9, 8, true)
        val lum = IntArray(72)
        var idx = 0
        for (y in 0 until 8) {
            for (x in 0 until 9) {
                val c = small.getPixel(x, y)
                lum[idx++] = (0.299 * Color.red(c) + 0.587 * Color.green(c) + 0.114 * Color.blue(c)).toInt()
            }
        }
        return computeDHashFromLuminance(lum).takeIf { it != 0L }
    }

    private fun cacheFile(context: Context): File = File(context.cacheDir, "dhash-v1.txt")

    private fun loadCacheIfNeeded(context: Context) {
        if (diskLoaded) return
        diskLoaded = true
        val file = cacheFile(context)
        if (!file.exists()) return
        runCatching {
            file.forEachLine(Charsets.UTF_8) { line ->
                val sep = line.lastIndexOf('\t')
                if (sep > 0) {
                    val key = line.substring(0, sep)
                    val hash = line.substring(sep + 1).toLongOrNull()
                    if (hash != null) memoryCache[key] = hash
                }
            }
        }
    }

    private fun saveCache(context: Context) {
        runCatching {
            val text = buildString {
                for ((k, v) in memoryCache) {
                    append(k).append('\t').append(v).append('\n')
                }
            }
            cacheFile(context).writeText(text, Charsets.UTF_8)
        }
    }
}
