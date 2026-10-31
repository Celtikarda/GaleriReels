package com.celtikarda.galerireels

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * İlerleme çubuğunda sürüklerken gösterilen küçük kareler. Dosya bir kez açılır ve sürükleme
 * boyunca kullanılır; en yakın ana kare (keyframe) alındığı için hızlıdır. VLC'nin oynattığı
 * bazı videolarda (ör. AV1) telefon kare çıkaramaz, o zaman null döner ve sadece süre görünür.
 */
class SeekPreview(context: Context, private val uri: Uri) {
    private val appContext = context.applicationContext
    private val lock = Mutex()
    private var retriever: MediaMetadataRetriever? = null
    private var failed = false
    private var released = false

    suspend fun frameAt(positionMs: Long): Bitmap? = withContext(Dispatchers.IO) {
        lock.withLock {
            if (released || failed) return@withLock null
            val r = retriever ?: runCatching {
                MediaMetadataRetriever().apply { setDataSource(appContext, uri) }
            }.getOrNull()?.also { retriever = it }
            if (r == null) {
                failed = true
                return@withLock null
            }
            runCatching {
                val atUs = positionMs * 1000
                if (Build.VERSION.SDK_INT >= 27) {
                    r.getScaledFrameAtTime(atUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, PREVIEW_SIZE, PREVIEW_SIZE)
                } else {
                    r.getFrameAtTime(atUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                }
            }.getOrNull()
        }
    }

    /** Ekran kapanırken çağrılır; o an kare çıkarılıyorsa bitince kapatılır */
    fun release() {
        CoroutineScope(Dispatchers.IO).launch {
            lock.withLock {
                released = true
                runCatching { retriever?.release() }
                retriever = null
            }
        }
    }

    private companion object {
        const val PREVIEW_SIZE = 320
    }
}

/** Kare kaydetme Android 10+'da (izin istemeden Resimler klasörüne yazılabiliyor) */
val frameSaveSupported: Boolean get() = Build.VERSION.SDK_INT >= 29

/**
 * Videonun [positionMs]'deki karesini tam çözünürlükte "Resimler/GaleriReels" klasörüne JPEG
 * olarak kaydeder. Kaydedilen fotoğrafın adresi, olmadıysa null.
 */
suspend fun saveFrameAsPhoto(context: Context, item: VideoItem, positionMs: Long): Uri? = withContext(Dispatchers.IO) {
    if (Build.VERSION.SDK_INT < 29) return@withContext null
    val frame = MediaMetadataRetriever().let { retriever ->
        try {
            retriever.setDataSource(context, item.uri)
            retriever.getFrameAtTime(positionMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST)
        } catch (e: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    } ?: return@withContext null

    val seconds = positionMs / 1000
    val stamp = "%d-%02d-%03d".format(seconds / 60, seconds % 60, positionMs % 1000)
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "${item.name.substringBeforeLast('.')}_kare_$stamp.jpg")
        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/GaleriReels")
        put(MediaStore.Images.Media.IS_PENDING, 1)
    }
    val resolver = context.contentResolver
    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return@withContext null
    val written = runCatching {
        resolver.openOutputStream(uri)?.use { frame.compress(Bitmap.CompressFormat.JPEG, 95, it) } == true
    }.getOrDefault(false)
    if (!written) {
        runCatching { resolver.delete(uri, null, null) }
        return@withContext null
    }
    resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
    uri
}
