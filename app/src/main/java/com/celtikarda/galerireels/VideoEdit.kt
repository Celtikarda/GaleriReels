package com.celtikarda.galerireels

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume

/**
 * Kırpılan videonun kalitesi: kısa kenar (piksel) ve istenen bit hızı. Telefonun kodlayıcısı
 * istenenin biraz üstüne çıkabiliyor (vivo'da 480p'de 1,5 Mbps istendi, ~1,8 çıktı); tahmin
 * bu yüzden %20 paylı.
 */
enum class EditQuality(val title: String, val shortSide: Int?, val bitrate: Int?) {
    ORIGINAL("Orijinal", null, null),
    HD("720p", 720, 3_000_000),
    SD("480p", 480, 1_500_000),
    ;

    /** Yaklaşık dosya boyutu (ses ~128 kbps dahil); orijinalde videonun kendi oranından */
    fun estimateBytes(item: VideoItem, durationMs: Long): Long {
        val seconds = durationMs / 1000.0
        val perSecond = if (bitrate == null) {
            if (item.durationMs > 0) item.sizeBytes * 1000.0 / item.durationMs else 0.0
        } else {
            (bitrate * 1.2 + 128_000) / 8.0
        }
        return (perSecond * seconds).toLong()
    }

    /** Video zaten bu boyuttan küçükse küçültmenin anlamı yok */
    fun usefulFor(item: VideoItem): Boolean =
        shortSide == null || item.width <= 0 || item.height <= 0 || minOf(item.width, item.height) > shortSide
}

/** Kırpma Android 10+'da (izin istemeden Filmler klasörüne yazılabiliyor) */
val videoEditSupported: Boolean get() = Build.VERSION.SDK_INT >= 29

sealed interface EditResult {
    data class Saved(val uri: Uri) : EditResult
    data class Failed(val reason: String) : EditResult
}

/**
 * Videonun [startMs]–[endMs] arasını [quality] kalitesinde yeni bir dosya olarak
 * "Filmler/GaleriReels" klasörüne kaydeder; orijinale dokunmaz. [onProgress] 0..1 arası.
 * Coroutine iptal edilirse dışa aktarma durur ve yarım dosya silinir.
 */
@OptIn(UnstableApi::class)
suspend fun exportEdited(
    context: Context,
    item: VideoItem,
    startMs: Long,
    endMs: Long,
    quality: EditQuality,
    onProgress: (Float) -> Unit,
): EditResult {
    if (Build.VERSION.SDK_INT < 29) return EditResult.Failed(str(R.string.err_android10))
    val appContext = context.applicationContext
    val temp = File(appContext.cacheDir, "edit_${System.currentTimeMillis()}.mp4")

    val clipped = MediaItem.Builder()
        .setUri(item.uri)
        .setClippingConfiguration(
            MediaItem.ClippingConfiguration.Builder()
                .setStartPositionMs(startMs)
                .setEndPositionMs(endMs)
                .build()
        )
        .build()
    val effects = quality.shortSide?.let { Effects(emptyList(), listOf(Presentation.createForShortSide(it))) } ?: Effects.EMPTY
    val edited = EditedMediaItem.Builder(clipped).setEffects(effects).build()

    val result = try { withContext(Dispatchers.Main) {
        coroutineScope {
            var transformer: Transformer? = null
            val poll = launch {
                val holder = ProgressHolder()
                while (isActive) {
                    val t = transformer
                    if (t != null && t.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) {
                        onProgress(holder.progress / 100f)
                    }
                    delay(200)
                }
            }
            val error = suspendCancellableCoroutine { cont ->
                val builder = Transformer.Builder(appContext)
                    .setVideoMimeType(MimeTypes.VIDEO_H264)
                    .setAudioMimeType(MimeTypes.AUDIO_AAC)
                    .addListener(object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                            if (cont.isActive) cont.resume(null)
                        }

                        override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                            if (cont.isActive) cont.resume(exportException.errorCodeName)
                        }
                    })
                if (quality.bitrate != null) {
                    builder.setEncoderFactory(
                        DefaultEncoderFactory.Builder(appContext)
                            .setRequestedVideoEncoderSettings(
                                VideoEncoderSettings.Builder().setBitrate(quality.bitrate).build()
                            )
                            .build()
                    )
                } else {
                    // Sadece kırpılıyorsa baştaki yarım parça dışında yeniden kodlanmaz: hızlı ve kayıpsız
                    builder.experimentalSetTrimOptimizationEnabled(true)
                }
                val t = builder.build()
                transformer = t
                cont.invokeOnCancellation { t.cancel() }
                t.start(edited, temp.absolutePath)
            }
            poll.cancel()
            error
        }
    } } catch (e: kotlinx.coroutines.CancellationException) {
        // Vazgeçildi: yarım dosya kalmasın
        temp.delete()
        throw e
    }
    if (result != null) {
        temp.delete()
        return EditResult.Failed(result)
    }

    return withContext(Dispatchers.IO) {
        try {
            val base = item.name.substringBeforeLast('.')
            val suffix = if (quality == EditQuality.ORIGINAL) "kirpilmis" else quality.title
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, "${base}_$suffix.mp4")
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/GaleriReels")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            val resolver = appContext.contentResolver
            val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                ?: return@withContext EditResult.Failed(str(R.string.err_file_create))
            val copied = runCatching {
                resolver.openOutputStream(uri)?.use { out -> temp.inputStream().use { it.copyTo(out) } } != null
            }.getOrDefault(false)
            if (!copied) {
                runCatching { resolver.delete(uri, null, null) }
                return@withContext EditResult.Failed(str(R.string.err_file_write))
            }
            resolver.update(uri, ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }, null, null)
            EditResult.Saved(uri)
        } finally {
            temp.delete()
        }
    }
}
