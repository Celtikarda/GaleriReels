package com.celtikarda.galerireels

import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.annotation.OptIn
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil
import androidx.media3.exoplayer.util.EventLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Reels ekranının oynatma tarafı.
 *
 * Ekrandaki video ile bir önceki ve bir sonraki video ayrı ExoPlayer'larda hazırlanır; komşular
 * ilk karelerinde bekler, kaydırınca video beklemeden başlar. Telefonun çözücülerinin
 * kaldıramadığı videolar (ör. 1440x1600@60) VLC ile oynatılır; VLC tek kopyadır, sadece
 * ekrandaki video için kullanılır ve ekran açılırken arka planda hazırlanır.
 */
@OptIn(UnstableApi::class)
class ReelsPlayback(context: Context, private val scope: CoroutineScope) {

    private val appContext = context.applicationContext
    // önceki, ekrandaki, sonraki
    private val slots = List(3) { PlayerSlot(createPlayer(appContext)) }

    /** Video id → onu hazırlayan oynatıcı */
    var players by mutableStateOf(emptyMap<Long, ExoPlayer>())
        private set
    /** İlk karesi ekrana çizilmiş videolar; bunların küçük resmi kaldırılır */
    var renderedIds by mutableStateOf(emptySet<Long>())
        private set
    /** Galerinin boyutunu bilmediği videolar için oynatıcının söylediği en / boy */
    var aspectRatios by mutableStateOf(emptyMap<Long, Float>())
        private set
    var vlc by mutableStateOf<VlcEngine?>(null)
        private set
    /** Şu an VLC ile oynayan video */
    var vlcItemId by mutableStateOf<Long?>(null)
        private set
    /** Oynatılamayan video → kullanıcıya gösterilecek açıklama */
    var error by mutableStateOf<Pair<Long, String>?>(null)
        private set

    private var current: VideoItem? = null
    private var neighbors: List<VideoItem> = emptyList()
    /** Kullanıcı dokunup durdurdu */
    private var paused = false
    /** Uygulama arka plana geçti */
    private var hostPaused = false
    private var muted = false
    private var released = false
    private var arrangeJob: Job? = null

    private var vlcRequested = false

    /** Sayfa oturunca çağrılır: [current] oynar, [neighbors] (önce sıradaki) ilk karesinde bekler. */
    fun show(current: VideoItem?, neighbors: List<VideoItem>) {
        if (current?.id != this.current?.id) {
            paused = false
            error = null
            current?.let { OpenTimer.start(it.id) }
        }
        this.current = current
        this.neighbors = neighbors
        arrange()
    }

    fun setPaused(paused: Boolean) {
        this.paused = paused
        applyPlaying()
    }

    fun setMuted(muted: Boolean) {
        this.muted = muted
        slots.forEach { it.player.volume = if (muted) 0f else 1f }
        vlc?.setMuted(muted)
    }

    fun onHostPause() {
        hostPaused = true
        applyPlaying()
    }

    fun onHostResume() {
        hostPaused = false
        applyPlaying()
    }

    /** VLC ile oynayan videonun 0..1 arası konumu */
    val vlcProgress: Float get() = vlc?.progress ?: 0f

    fun release() {
        released = true
        arrangeJob?.cancel()
        slots.forEach { it.player.release() }
        vlc?.release()
        vlc = null
    }

    private fun arrange() {
        if (released) return
        arrangeJob?.cancel()
        arrangeJob = scope.launch {
            // Ekrandaki videonun motoru hemen lazım; komşularınki ondan sonra
            val current = current
            current?.let { decide(it) }
            assign()
            // Komşular ve VLC, ekrandaki video açıldıktan sonra hazırlanır; hepsi aynı anda
            // yapılınca ilk video geç açılıyor
            if (current != null) {
                withTimeoutOrNull(1000) { snapshotFlow { current.id in renderedIds }.first { it } }
            }
            prepareVlc()
            neighbors.forEach { decide(it) }
            assign()
        }
    }

    private suspend fun decide(item: VideoItem) {
        if (DecoderCheck.decision(item.id) != null) return
        withContext(Dispatchers.IO) { DecoderCheck.needsVlc(appContext, item) }
    }

    /** VLC'yi arka planda açar; hazır olunca ekrandaki video bekliyorsa onu oynatır. */
    private fun prepareVlc() {
        if (vlcRequested) return
        vlcRequested = true
        VlcEngine.createAsync(appContext) { engine ->
            if (released) {
                engine.release()
            } else {
                engine.setMuted(muted)
                vlc = engine
                arrange()
            }
        }
    }

    /** Videoları oynatıcılara dağıtır; zaten hazırlanmış olanlara dokunmaz. */
    private fun assign() {
        val current = current
        val exoItems = (listOfNotNull(current) + neighbors).filter { DecoderCheck.decision(it.id) == false }
        val kept = slots.filter { slot -> exoItems.any { it.id == slot.itemId } }
        val free = slots.filterNot { it in kept }.toMutableList()
        for (item in exoItems) {
            if (kept.none { it.itemId == item.id }) free.removeAt(0).load(item)
        }
        free.forEach { it.clear() }

        players = slots.mapNotNull { slot -> slot.itemId?.let { it to slot.player } }.toMap()
        renderedIds = renderedIds.filter { it in players || it == vlcItemId }.toSet()

        if (current != null && DecoderCheck.decision(current.id) == true) playVlc(current) else stopVlc()
        applyPlaying()
        // Önceden hazırlanmış video: ilk karesi zaten ekranda
        if (current != null && current.id in renderedIds) OpenTimer.firstFrame(current.id, "hazır")
    }

    private fun applyPlaying() {
        val play = !paused && !hostPaused
        val currentId = current?.id
        // Önce diğerlerini durdur ki ses odağı ekrandaki videoya geçsin
        slots.filter { it.itemId != currentId || currentId == null }.forEach { it.deactivate() }
        slots.firstOrNull { currentId != null && it.itemId == currentId }?.activate(play)
        if (vlcItemId != null) {
            if (play) vlc?.resume() else vlc?.pause()
        }
    }

    private fun playVlc(item: VideoItem) {
        if (vlcItemId == item.id) return
        // VLC henüz hazır değilse hazır olunca arrange() tekrar çağrılır
        val engine = vlc ?: return prepareVlc()
        stopVlc()
        vlcItemId = item.id
        engine.play(
            item.uri,
            onFirstFrame = {
                renderedIds = renderedIds + item.id
                OpenTimer.firstFrame(item.id, "vlc")
            },
            onError = { error = item.id to "Dosya bozuk ya da gerçek bir video değil." },
        )
    }

    private fun stopVlc() {
        val id = vlcItemId ?: return
        vlc?.stop()
        vlcItemId = null
        renderedIds = renderedIds - id
    }

    /** Bir ExoPlayer ve o an hazırladığı video */
    private inner class PlayerSlot(val player: ExoPlayer) : AnalyticsListener {
        var itemId: Long? = null
            private set
        private var active = false
        private var decoderName: String? = null
        private var videoFormat: Format? = null

        init {
            player.addAnalyticsListener(this)
        }

        fun load(item: VideoItem) {
            itemId = item.id
            active = false
            decoderName = null
            videoFormat = null
            // Önceki videonun sayfasına çizmesin; yeni videonun sayfası kendi yüzeyini bağlar
            player.clearVideoSurface()
            player.playWhenReady = false
            player.setMediaItem(MediaItem.Builder().setUri(item.uri).setMediaId(item.id.toString()).build())
            player.prepare()
        }

        fun clear() {
            if (itemId == null) return
            itemId = null
            active = false
            player.stop()
            player.clearMediaItems()
            player.clearVideoSurface()
        }

        fun activate(play: Boolean) {
            active = true
            player.playWhenReady = play
        }

        fun deactivate() {
            if (!active) return
            active = false
            player.playWhenReady = false
            // Geri dönülünce baştan başlasın; beklerken de ilk karesi görünsün
            player.seekTo(0)
        }

        override fun onRenderedFirstFrame(eventTime: AnalyticsListener.EventTime, output: Any, renderTimeMs: Long) {
            val id = ownId(eventTime) ?: return
            renderedIds = renderedIds + id
            if (id == current?.id) OpenTimer.firstFrame(id, "exo")
        }

        override fun onVideoSizeChanged(eventTime: AnalyticsListener.EventTime, videoSize: VideoSize) {
            val id = ownId(eventTime) ?: return
            if (videoSize.width > 0 && videoSize.height > 0) {
                aspectRatios = aspectRatios + (id to videoSize.width * videoSize.pixelWidthHeightRatio / videoSize.height)
            }
        }

        override fun onPlayerError(eventTime: AnalyticsListener.EventTime, error: PlaybackException) {
            switchToVlc(eventTime)
        }

        override fun onVideoDecoderInitialized(
            eventTime: AnalyticsListener.EventTime,
            decoderName: String,
            initializedTimestampMs: Long,
            initializationDurationMs: Long,
        ) {
            if (ownId(eventTime) == null) return
            this.decoderName = decoderName
            checkOverload(eventTime)
        }

        override fun onVideoInputFormatChanged(
            eventTime: AnalyticsListener.EventTime,
            format: Format,
            decoderReuseEvaluation: DecoderReuseEvaluation?,
        ) {
            if (ownId(eventTime) == null) return
            videoFormat = format
            checkOverload(eventTime)
        }

        // Yazılım çözücü 4 saniyeden kısa sürede 50 kare atladıysa video takılıyor demektir
        override fun onDroppedVideoFrames(eventTime: AnalyticsListener.EventTime, droppedFrames: Int, elapsedMs: Long) {
            val name = decoderName ?: return
            if (droppedFrames >= 50 && elapsedMs < 4000 && isSoftwareDecoder(name)) switchToVlc(eventTime)
        }

        // Yazılım çözücü videoyu kapasitesinin üstünde bulduysa en baştan VLC'ye geç
        private fun checkOverload(eventTime: AnalyticsListener.EventTime) {
            val name = decoderName ?: return
            val format = videoFormat ?: return
            if (isOverloadedSoftwareDecoder(name, format)) switchToVlc(eventTime)
        }

        private fun switchToVlc(eventTime: AnalyticsListener.EventTime) {
            val id = ownId(eventTime) ?: return
            DecoderCheck.markVlc(id)
            arrange()
        }

        /**
         * Olayın ait olduğu video, bu oynatıcının şu anki videosuysa id'si. Oynatıcı yeni videoya
         * geçerken önceki videodan gecikmiş olaylar gelebiliyor; onlar yok sayılır.
         */
        private fun ownId(eventTime: AnalyticsListener.EventTime): Long? {
            if (eventTime.timeline.isEmpty) return null
            val id = eventTime.timeline.getWindow(eventTime.windowIndex, Timeline.Window()).mediaItem.mediaId.toLongOrNull()
            return id?.takeIf { it == itemId }
        }
    }
}

private fun isSoftwareDecoder(name: String): Boolean =
    name.startsWith("c2.android.") || name.startsWith("OMX.google.")

/**
 * ExoPlayer donanım çözücüyü açamayınca (ör. 1440x1600 donanımın 1080p sınırını aşıyor)
 * Android'in yazılım çözücüsüne düşer; o da videoyu kapasitesinin üstünde bulursa takılır.
 */
@OptIn(UnstableApi::class)
private fun isOverloadedSoftwareDecoder(name: String, format: Format): Boolean {
    if (!isSoftwareDecoder(name)) return false
    val mime = format.sampleMimeType ?: return false
    if (format.width <= 0 || format.height <= 0) return false
    val info = runCatching { MediaCodecUtil.getDecoderInfos(mime, false, false) }.getOrNull()
        ?.firstOrNull { it.name == name } ?: return false
    val frameRate = if (format.frameRate > 0) format.frameRate.toDouble() else 30.0
    return !info.isVideoSizeAndRateSupported(format.width, format.height, frameRate)
}

@OptIn(UnstableApi::class)
private fun createPlayer(context: Context): ExoPlayer {
    // Bir çözücü açılamazsa (ör. donanım NO_MEMORY verirse) sıradakini denesin
    val renderers = DefaultRenderersFactory(context).setEnableDecoderFallback(true)
    return ExoPlayer.Builder(context, renderers)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build(),
            /* handleAudioFocus = */ true,
        )
        .setHandleAudioBecomingNoisy(true)
        .build()
        .apply {
            repeatMode = Player.REPEAT_MODE_ONE
            // Debug sürümde oynatma olaylarını (atlanan kareler, hatalar) logcat'e yazar
            if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
                addAnalyticsListener(EventLogger())
            }
        }
}
