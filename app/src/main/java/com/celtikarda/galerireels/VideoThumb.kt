package com.celtikarda.galerireels

import android.content.Context
import android.graphics.Bitmap
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
        val bitmap = systemThumbnail(context) ?: grabFrame(context)
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
            val frame = retriever.getFrameAtTime(1_000_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.frameAtTime
            frame?.let { scaleDown(it) }
        } catch (e: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
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
