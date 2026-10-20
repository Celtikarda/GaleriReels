package com.celtikarda.galerireels

import android.content.Context
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import java.util.concurrent.ConcurrentHashMap

/**
 * Bir videonun ExoPlayer ile mi VLC ile mi oynatılacağına oynatmadan önce karar verir.
 * Dosyanın başlığından video biçimini (H.264, VP9, AV1...) ve boyutunu okur, telefonun
 * çözücülerine bu videoyu kaldırıp kaldıramayacaklarını sorar. Böylece ExoPlayer'ı deneyip
 * takılınca VLC'ye geçmek için beklenmez.
 */
object DecoderCheck {

    /** video id → VLC gerekiyor mu */
    private val decisions = ConcurrentHashMap<Long, Boolean>()
    /** biçim → o biçimi açabilen çözücüler */
    private val decoders = ConcurrentHashMap<String, List<MediaCodecInfo>>()

    /** Sık görülen biçimlerin çözücülerini önceden okur; uygulama açılırken arka planda çağrılır. */
    fun warmUp() {
        listOf("video/avc", "video/hevc", "video/x-vnd.on2.vp9", "video/av01").forEach { mime ->
            decodersFor(mime).forEach { runCatching { it.getCapabilitiesForType(mime) } }
        }
    }

    /** Daha önce karar verildiyse sonucu (VLC gerekiyor mu), verilmediyse null */
    fun decision(id: Long): Boolean? = decisions[id]

    /** ExoPlayer oynatırken takıldıysa ya da hata verdiyse sonraki açılışlarda doğrudan VLC */
    fun markVlc(id: Long) {
        decisions[id] = true
    }

    /** Dosyayı okuduğu için ana iş parçacığında çağrılmamalı. */
    fun needsVlc(context: Context, item: VideoItem): Boolean = decisions.getOrPut(item.id) {
        val start = SystemClock.uptimeMillis()
        probe(context, item.uri).also {
            Log.i("GaleriAcilis", "karar: video=${item.id} vlc=$it (${SystemClock.uptimeMillis() - start} ms)")
        }
    }

    private fun probe(context: Context, uri: Uri): Boolean {
        // Başlık okunamadıysa ExoPlayer denesin; takılırsa oynatırken yine VLC'ye geçilir
        val format = videoFormat(context, uri) ?: return false
        val mime = format.getString(MediaFormat.KEY_MIME) ?: return false
        val width = format.getInteger(MediaFormat.KEY_WIDTH)
        val height = format.getInteger(MediaFormat.KEY_HEIGHT)
        // Kare hızı yazmıyorsa en zorunu varsay (bu telefondaki büyük videoların çoğu 60 fps)
        val fps = frameRate(format) ?: 60.0
        val canPlay = decodersFor(mime).any { info ->
            val video = runCatching { info.getCapabilitiesForType(mime).videoCapabilities }.getOrNull()
            video != null && video.areSizeAndRateSupported(width, height, fps)
        }
        return !canPlay
    }

    private fun videoFormat(context: Context, uri: Uri): MediaFormat? {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(context, uri, null)
            (0 until extractor.trackCount)
                .map { extractor.getTrackFormat(it) }
                .firstOrNull { it.getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true }
        } catch (e: Exception) {
            null
        } finally {
            extractor.release()
        }
    }

    private fun frameRate(format: MediaFormat): Double? {
        if (!format.containsKey(MediaFormat.KEY_FRAME_RATE)) return null
        // Bazı dosyalarda tam sayı, bazılarında ondalıklı yazıyor
        val value = runCatching { format.getInteger(MediaFormat.KEY_FRAME_RATE).toDouble() }
            .recoverCatching { format.getFloat(MediaFormat.KEY_FRAME_RATE).toDouble() }
            .getOrNull()
        return value?.takeIf { it > 0 }
    }

    private val allCodecs by lazy { MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos }

    private fun decodersFor(mime: String): List<MediaCodecInfo> = decoders.getOrPut(mime) {
        allCodecs.filter { info ->
            !info.isEncoder && info.supportedTypes.any { it.equals(mime, ignoreCase = true) }
        }
    }
}
