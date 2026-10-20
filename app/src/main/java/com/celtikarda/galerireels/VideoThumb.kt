package com.celtikarda.galerireels

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.Size
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.key.Keyer
import coil3.request.Options
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/** Coil'e verilen model: videonun küçük resmi, en uzun kenarı en fazla [maxSize] piksel */
data class VideoThumb(val uri: Uri, val maxSize: Int)

/**
 * Önce sistemin hazır küçük resmini dener (hızlı, galeri uygulamasıyla ortak önbellek).
 * Sistem resmi yoksa ya da simsiyahsa (kararmayla açılan videolar) videonun içinden bir kare
 * bulur; bu pahalı olduğu için bir kez hesaplanıp diskte saklanır.
 */
class VideoThumbFetcher(private val data: VideoThumb, private val options: Options) : Fetcher {

    override suspend fun fetch(): FetchResult {
        val context = options.context
        val system = systemThumbnail(context)
        val bitmap = system?.takeUnless { isNearlyBlack(it) }
            ?: FrameCache.get(context, data.uri)?.let { scaleDown(it) }
            ?: system
            ?: throw IOException("Küçük resim alınamadı: ${data.uri}")
        return ImageFetchResult(image = bitmap.asImage(), isSampled = true, dataSource = DataSource.DISK)
    }

    private fun systemThumbnail(context: Context): Bitmap? {
        if (Build.VERSION.SDK_INT < 29) return null
        return runCatching {
            context.contentResolver.loadThumbnail(data.uri, Size(data.maxSize, data.maxSize), null)
        }.getOrNull()
    }

    private fun scaleDown(bitmap: Bitmap): Bitmap {
        val longest = maxOf(bitmap.width, bitmap.height)
        if (longest <= data.maxSize) return bitmap
        val ratio = data.maxSize.toFloat() / longest
        return Bitmap.createScaledBitmap(bitmap, (bitmap.width * ratio).toInt(), (bitmap.height * ratio).toInt(), true)
    }

    class Factory : Fetcher.Factory<VideoThumb> {
        override fun create(data: VideoThumb, options: Options, imageLoader: ImageLoader): Fetcher =
            VideoThumbFetcher(data, options)
    }
}

class VideoThumbKeyer : Keyer<VideoThumb> {
    override fun key(data: VideoThumb, options: Options): String = "${data.uri}#${data.maxSize}"
}

/**
 * Videonun içinden bulunan kareler. Büyük dosyalarda (2 GB'lık dizi bölümleri) saniyeler
 * sürebildiği için her video için bir kez, aynı anda tek bir kez hesaplanır ve uygulamanın
 * önbellek klasörüne kaydedilir; sonraki açılışlarda diskten gelir.
 */
private object FrameCache {
    private const val SIZE = 720
    private val locks = ConcurrentHashMap<String, Mutex>()

    suspend fun get(context: Context, uri: Uri): Bitmap? {
        // Çok büyük dosyalarda (2 GB'lık .ts dizi bölümleri) ortadan kare çıkarmak dosyanın neredeyse
        // tamamını okumak demek; telefonun kare çıkarma servisi de aynı anda tek iş yaptığı için
        // bütün diğer küçük resimler onu bekliyordu. Bunlarda aranmaz, sistemin resmi kalır.
        if (fileSize(context, uri) > 300L * 1024 * 1024) return null
        val file = File(context.cacheDir, "kareler/${uri.lastPathSegment}.jpg")
        val lock = locks.getOrPut(file.name) { Mutex() }
        return lock.withLock {
            if (file.exists()) BitmapFactory.decodeFile(file.path)?.let { return@withLock it }
            findFrame(context, uri)?.also { frame ->
                // Hepsi karanlık çıktıysa saklama; bir dahaki sefere yeniden denensin
                if (!isNearlyBlack(frame)) runCatching {
                    file.parentFile?.mkdirs()
                    file.outputStream().use { frame.compress(Bitmap.CompressFormat.JPEG, 85, it) }
                }
            }
        }
    }

    private fun findFrame(context: Context, uri: Uri): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val durationUs = (retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L) * 1000
            // Kararmayla açılan videolarda başlangıç birkaç saniye simsiyah olabiliyor (5 sn'yi
            // geçeni de var); videonun üçte birine, yarısına, üçte ikisine ve sonlarına bakılır
            val positions = if (durationUs > 0) {
                listOf(durationUs / 3, durationUs / 2, durationUs * 2 / 3, durationUs * 85 / 100)
            } else {
                listOf(1_000_000L)
            }
            // Önce en yakın anahtar kareler (hızlı)
            var fallback: Bitmap? = null
            for (atUs in positions) {
                val sync = retriever.getFrameAtTime(atUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC) ?: continue
                if (!isNearlyBlack(sync)) return scale(sync)
                fallback = fallback ?: sync
            }
            // Bazı videolarda tek anahtar kare en baştaki siyah kare; o zaman tam o andaki kare
            // çözülür (daha yavaş)
            for (atUs in positions.reversed()) {
                val exact = retriever.getFrameAtTime(atUs, MediaMetadataRetriever.OPTION_CLOSEST) ?: continue
                if (!isNearlyBlack(exact)) return scale(exact)
            }
            (fallback ?: retriever.frameAtTime)?.let { scale(it) }
        } catch (e: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun fileSize(context: Context, uri: Uri): Long =
        runCatching { context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } }.getOrNull() ?: Long.MAX_VALUE

    private fun scale(bitmap: Bitmap): Bitmap {
        val longest = maxOf(bitmap.width, bitmap.height)
        if (longest <= SIZE) return bitmap
        val ratio = SIZE.toFloat() / longest
        return Bitmap.createScaledBitmap(bitmap, (bitmap.width * ratio).toInt(), (bitmap.height * ratio).toInt(), true)
    }
}

/** Ortalama parlaklık çok düşükse (neredeyse simsiyah kare) true */
private fun isNearlyBlack(bitmap: Bitmap): Boolean = runCatching {
    // Donanım belleğindeki resmin piksellerine doğrudan bakılamıyor
    val readable = if (Build.VERSION.SDK_INT >= 26 && bitmap.config == Bitmap.Config.HARDWARE) {
        bitmap.copy(Bitmap.Config.ARGB_8888, false)
    } else {
        bitmap
    }
    val small = Bitmap.createScaledBitmap(readable, 12, 12, true)
    var sum = 0.0
    for (y in 0 until 12) for (x in 0 until 12) {
        val c = small.getPixel(x, y)
        sum += 0.299 * Color.red(c) + 0.587 * Color.green(c) + 0.114 * Color.blue(c)
    }
    sum / 144 < 16
}.getOrDefault(false)
