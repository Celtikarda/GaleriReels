package com.celtikarda.galerireels

import android.content.Context
import android.graphics.Bitmap
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
import java.io.IOException

/** Coil'e verilen model: videonun küçük resmi, en uzun kenarı en fazla [maxSize] piksel */
data class VideoThumb(val uri: Uri, val maxSize: Int)

/**
 * Önce sistemin hazır küçük resmini dener (hızlı, galeri uygulamasıyla ortak önbellek),
 * olmazsa videodan bir kare çeker.
 */
class VideoThumbFetcher(private val data: VideoThumb, private val options: Options) : Fetcher {

    override suspend fun fetch(): FetchResult {
        val context = options.context
        val system = systemThumbnail(context)
        // Kararmayla açılan videolarda sistemin küçük resmi simsiyah çıkıyor (ilk kareden alınmış);
        // o zaman videonun ortalarından bir kare al, o da yoksa siyahı kullan
        val bitmap = system?.takeUnless { isNearlyBlack(it) } ?: grabFrame(context) ?: system
            ?: throw IOException("Küçük resim alınamadı: ${data.uri}")
        return ImageFetchResult(image = bitmap.asImage(), isSampled = true, dataSource = DataSource.DISK)
    }

    private fun systemThumbnail(context: Context): Bitmap? {
        if (Build.VERSION.SDK_INT < 29) return null
        return runCatching {
            context.contentResolver.loadThumbnail(data.uri, Size(data.maxSize, data.maxSize), null)
        }.getOrNull()
    }

    private fun grabFrame(context: Context): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, data.uri)
            val durationUs = (retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L) * 1000
            // Kararmayla açılan videolarda başlangıç birkaç saniye simsiyah olabiliyor (5 sn'yi
            // geçeni de var); videonun üçte birine, yarısına, üçte ikisine sırayla bakılır
            val positions = if (durationUs > 0) listOf(durationUs / 3, durationUs / 2, durationUs * 2 / 3) else listOf(1_000_000L)
            var fallback: Bitmap? = null
            for (atUs in positions) {
                // Önce en yakın anahtar kare (hızlı); o da siyahsa tam o andaki kare (yavaş ama doğru)
                val sync = retriever.getFrameAtTime(atUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                if (sync != null && !isNearlyBlack(sync)) return scaleDown(sync)
                val exact = retriever.getFrameAtTime(atUs, MediaMetadataRetriever.OPTION_CLOSEST)
                if (exact != null && !isNearlyBlack(exact)) return scaleDown(exact)
                fallback = fallback ?: exact ?: sync
            }
            (fallback ?: retriever.frameAtTime)?.let { scaleDown(it) }
        } catch (e: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
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
