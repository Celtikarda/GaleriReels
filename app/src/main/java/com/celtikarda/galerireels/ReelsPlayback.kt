package com.celtikarda.galerireels

import android.content.Context
import android.content.pm.ApplicationInfo
import android.media.audiofx.LoudnessEnhancer
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
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.util.EventLogger
import androidx.media3.exoplayer.video.MediaCodecVideoRenderer
import androidx.media3.exoplayer.video.VideoRendererEventListener
import androidx.media3.extractor.DefaultExtractorsFactory
import android.os.Handler
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToLong

/**
 * Reels ekranının oynatma tarafı.
 *
 * Ekrandaki video ile bir önceki ve bir sonraki video ayrı ExoPlayer'larda hazırlanır; komşular
 * ilk karelerinde bekler, kaydırınca video beklemeden başlar. Telefonun çözücülerinin
 * kaldıramadığı videolar (ör. 1440x1600@60) VLC ile oynatılır; VLC tek kopyadır, sadece
 * ekrandaki video için kullanılır ve ekran açılırken arka planda hazırlanır.
 */
@OptIn(UnstableApi::class)
class ReelsPlayback(
    context: Context,
    private val scope: CoroutineScope,
    /** Ekrandaki video bir kez izlendi sayıldı (bkz. [countViews]) */
    private val onViewed: (VideoItem) -> Unit,
    /** Video kaldığı yerden ([ResumeStore]) başladı; konum ms */
    private val onResumed: (VideoItem, Long) -> Unit,
    /** "Bitince sonrakine geç" açıkken ekrandaki video bitti; sıradaki yoksa false döner ve video başa sarar */
    private val onEnded: () -> Boolean,
) {

    private val appContext = context.applicationContext
    private val resume = ResumeStore(appContext)
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
    /** A-B tekrarı: A noktası ve (işaretlendiyse) B noktası, ms. Video değişince kapanır. */
    var abLoop by mutableStateOf<Pair<Long, Long?>?>(null)
        private set

    private var current: VideoItem? = null
    private var neighbors: List<VideoItem> = emptyList()
    /** Kullanıcı dokunup durdurdu */
    private var paused = false
    /** Uygulama arka plana geçti */
    private var hostPaused = false
    private var muted = false
    /** Kısık sesli videolar için %200 ses yükseltme */
    private var boosted = false
    /** Oynatma hızı: menüden seçilen hız ya da uzun basılı tutunca 2x */
    private var speed = 1f
    /** Video bitince başa sarmak yerine sıradakine geçilir */
    private var autoAdvance = false
    // VLC videoyu kendisi başa sarıyor; bitişi konumun sondan başa dönmesinden anlarız
    private var loopItemId: Long? = null
    private var lastLoopPositionMs = 0L
    /** Uzun basılı tutunca 2x hızda geriye sarma */
    private var rewinding = false
    private var rewindJob: Job? = null
    private var released = false
    private var arrangeJob: Job? = null
    private var vlcRequested = false
    /** VLC bu cihazda açılamadı; VLC gerektiren videolar hata mesajı gösterir */
    private var vlcFailed = false

    // İzlenme sayımı: hangi video, bu oynatmada sayıldı mı, son bakılan konum
    private var viewItemId: Long? = null
    private var viewCounted = false
    private var lastPositionMs = 0L
    /** Bu oynatmada gerçekten oynayan süre (sarma ile atlanan kısım hariç) */
    private var watchedMs = 0L

    init {
        scope.launch {
            while (true) {
                delay(250)
                countViews()
            }
        }
        scope.launch {
            while (true) {
                delay(50)
                checkLoop()
            }
        }
    }

    /** Sayfa oturunca çağrılır: [current] oynar, [neighbors] (önce sıradaki) ilk karesinde bekler. */
    fun show(current: VideoItem?, neighbors: List<VideoItem>) {
        if (current?.id != this.current?.id) {
            savePosition()
            paused = false
            error = null
            abLoop = null
            setRewinding(false)
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
        slots.forEach {
            it.player.volume = if (muted) 0f else 1f
            it.updateBoost()
        }
        vlc?.setMuted(muted)
    }

    fun setBoosted(boosted: Boolean) {
        this.boosted = boosted
        slots.forEach { it.updateBoost() }
        vlc?.setBoosted(boosted)
    }

    fun setSpeed(speed: Float) {
        if (this.speed == speed) return
        this.speed = speed
        // Sadece ekrandaki oynatıcı 2x hız ister; komşular da isterse donanım çözücünün
        // toplam kare/saniye bütçesi bölünür ve yüksek kaliteli videolar 2x'te takılır
        slots.forEach { it.updateSpeed() }
        vlc?.setSpeed(speed)
    }

    fun setRewinding(rewinding: Boolean) {
        if (this.rewinding == rewinding) return
        this.rewinding = rewinding
        rewindJob?.cancel()
        rewindJob = null
        slots.forEach { it.updateScrubbing() }
        vlc?.setScrubbing(rewinding)
        applyPlaying()
        if (rewinding) {
            rewindJob = scope.launch {
                var lastTickMs = SystemClock.uptimeMillis()
                var targetPosMs: Long? = null
                while (true) {
                    // VLC ana kareye atlıyor; daha sık sarınca ana kare çözülüp ekrana çıkmadan
                    // yeni sarma geliyor. ExoPlayer'ın sarma (scrubbing) modu sık sarmayı kaldırıyor.
                    delay(if (vlcItemId != null && vlcItemId == current?.id) 250 else 60)
                    val item = current ?: break
                    val now = SystemClock.uptimeMillis()
                    val elapsedMs = (now - lastTickMs).coerceIn(1L, 500L)
                    lastTickMs = now
                    val stepMs = elapsedMs * 2L
                    if (vlcItemId == item.id) {
                        val engine = vlc ?: continue
                        // VLC geri sarma sırasında oynamaya devam ediyor (duraklatılınca atlanan
                        // kare ekrana çıkmıyor); hedef konum kendi saatimizle 2x geriye gider
                        val basePos = targetPosMs ?: engine.timeMs
                        val nextPos = (basePos - stepMs).coerceAtLeast(0L)
                        targetPosMs = nextPos
                        if (nextPos != basePos) engine.seekFast(nextPos)
                    } else {
                        val player = players[item.id] ?: continue
                        val basePos = targetPosMs ?: player.currentPosition
                        val nextPos = (basePos - stepMs).coerceAtLeast(0L)
                        targetPosMs = nextPos
                        if (nextPos != basePos) {
                            player.seekTo(nextPos)
                        }
                    }
                }
            }
        }
    }

    fun setAutoAdvance(autoAdvance: Boolean) {
        this.autoAdvance = autoAdvance
        slots.forEach { it.updateRepeat() }
    }

    fun onHostPause() {
        savePosition()
        hostPaused = true
        applyPlaying()
    }

    fun onHostResume() {
        hostPaused = false
        applyPlaying()
    }

    /** İlerleme çubuğundan sarma: [fraction] 0..1 */
    fun seekTo(item: VideoItem, fraction: Float) {
        if (vlcItemId == item.id) {
            vlc?.seek(fraction)
        } else {
            players[item.id]?.let { player ->
                val duration = player.duration
                if (duration > 0) player.seekTo((duration * fraction).toLong())
            }
        }
    }

    /** Videonun şu anki konumu (ms); oynatıcısı yoksa null */
    fun positionOf(item: VideoItem): Long? = when {
        vlcItemId == item.id -> vlc?.timeMs
        else -> players[item.id]?.currentPosition
    }

    /** Tam olarak [ms]'ye sarar (videonun sonunu geçmez) */
    fun seekToMs(item: VideoItem, ms: Long) {
        val end = item.durationMs.takeIf { it > 0 }?.let { it - 250 } ?: Long.MAX_VALUE
        val target = ms.coerceAtMost(end).coerceAtLeast(0L)
        if (vlcItemId == item.id) vlc?.seekTo(target) else players[item.id]?.seekTo(target)
    }

    /** Çift dokunarak ±10 sn gibi göreli sarma */
    fun seekBy(item: VideoItem, deltaMs: Long) {
        val position = positionOf(item) ?: return
        seekToMs(item, position + deltaMs)
    }

    /** Durdurulmuş videoda bir kare ileri / geri (kare hızı bilinmiyorsa 30 fps varsayılır) */
    fun stepFrame(item: VideoItem, forward: Boolean) {
        val fps = slots.firstOrNull { it.itemId == item.id }?.frameRate?.takeIf { it > 0f } ?: 30f
        val frameMs = (1000f / fps).roundToLong().coerceAtLeast(1L)
        seekBy(item, if (forward) frameMs else -frameMs)
    }

    /**
     * A-B tekrarında bir adım: kapalıysa A'yı, A varsa B'yi işaretler (B, A'dan önceyse ikisi yer
     * değiştirir), ikisi de varsa kapatır.
     */
    fun cycleAbLoop(item: VideoItem) {
        val position = positionOf(item) ?: return
        val ab = abLoop
        abLoop = when {
            ab == null -> position to null
            ab.second == null -> {
                val a = minOf(ab.first, position)
                val b = maxOf(ab.first, position)
                // Neredeyse aynı nokta: B sayılmaz, A yeniden işaretlenir
                if (b - a < 300) position to null else a to b
            }
            else -> null
        }
        abLoop?.let { (a, b) -> if (b != null) seekToMs(item, a) }
    }

    fun release() {
        savePosition()
        released = true
        arrangeJob?.cancel()
        rewindJob?.cancel()
        slots.forEach {
            it.releaseEnhancer()
            it.player.release()
        }
        vlc?.release()
        vlc = null
    }

    /** Ekrandaki uzun videonun konumunu kaydeder; bir dahaki açılışta oradan devam eder */
    private fun savePosition() {
        val item = current ?: return
        if (item.isPhoto) return
        val position = positionOf(item) ?: return
        resume.save(item, position)
    }

    /** A-B tekrarında B'ye gelince A'ya döner; VLC'de video bitince (başa sarınca) [onEnded] */
    private fun checkLoop() {
        if (released) return
        val item = current ?: return
        if (item.isPhoto || rewinding) return
        val position = positionOf(item) ?: return
        if (item.id != loopItemId) {
            loopItemId = item.id
            lastLoopPositionMs = position
        }
        val ab = abLoop
        val b = ab?.second
        if (ab != null && b != null && position >= b) {
            seekToMs(item, ab.first)
        } else if (autoAdvance && vlcItemId == item.id && item.durationMs > 0 &&
            position < 1000 && lastLoopPositionMs > item.durationMs - 1500
        ) {
            // VLC zaten başa sardı; sıradaki yoksa öylece devam eder
            onEnded()
        }
        lastLoopPositionMs = position
    }

    /**
     * Video bir oynatmada 5 saniye (5 saniyeden kısaysa %80'i) izlenince bir izlenme sayılır.
     * Video bitip başa sarınca yeni oynatma başlar ve tekrar sayılır. Durdurulmuş videonun konumu
     * ilerlemediği için sayılmaz.
     */
    private fun countViews() {
        if (released) return
        val item = current ?: return
        if (item.id != viewItemId) {
            viewItemId = item.id
            viewCounted = false
            lastPositionMs = 0L
            watchedMs = 0L
        }
        if (item.isPhoto) {
            if (!paused && !hostPaused) watchedMs += 250L
            if (!viewCounted && watchedMs >= 1_500L) {
                viewCounted = true
                onViewed(item)
            }
            return
        }
        val position = when {
            vlcItemId == item.id -> vlc?.timeMs
            else -> players[item.id]?.currentPosition
        } ?: return
        if (rewinding) {
            lastPositionMs = position
            return
        }
        val delta = position - lastPositionMs
        if (position < lastPositionMs - 500 && position < 1500) {
            // Video başa sardı: yeni oynatma (çubuktan ortalara geri sarmak yeni izlenme sayılmaz)
            viewCounted = false
            watchedMs = position
        } else if (delta in 1..2200) {
            // Normal ya da 2x oynama; daha büyük sıçrama çubuktan ileri sarmadır, izlenmiş sayılmaz
            watchedMs += delta
        }
        lastPositionMs = position
        val threshold = if (item.durationMs in 1 until VIEW_AFTER_MS) item.durationMs * 8 / 10 else VIEW_AFTER_MS
        if (!viewCounted && watchedMs >= threshold) {
            viewCounted = true
            onViewed(item)
        }
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
        if (item.isPhoto || DecoderCheck.decision(item.id) != null) return
        withContext(Dispatchers.IO) { DecoderCheck.needsVlc(appContext, item) }
    }

    /** VLC'yi arka planda açar; hazır olunca ekrandaki video bekliyorsa onu oynatır. */
    private fun prepareVlc() {
        if (vlcRequested) return
        vlcRequested = true
        VlcEngine.createAsync(
            appContext,
            onReady = { engine ->
                if (released) {
                    engine.release()
                } else {
                    engine.setMuted(muted)
                    engine.setBoosted(boosted)
                    engine.setSpeed(speed)
                    engine.setScrubbing(rewinding)
                    vlc = engine
                    arrange()
                }
            },
            onFailed = {
                vlcFailed = true
                if (!released) arrange()
            },
        )
    }

    /** Videoları oynatıcılara dağıtır; zaten hazırlanmış olanlara dokunmaz. */
    private fun assign() {
        val current = current
        val allVisible = listOfNotNull(current) + neighbors
        val exoItems = allVisible.filter { !it.isPhoto && DecoderCheck.decision(it.id) == false }
        val kept = slots.filter { slot -> exoItems.any { it.id == slot.itemId } }
        val free = slots.filterNot { it in kept }.toMutableList()
        for (item in exoItems) {
            if (kept.none { it.itemId == item.id }) free.removeAt(0).load(item)
        }
        free.forEach { it.clear() }

        players = slots.mapNotNull { slot -> slot.itemId?.let { it to slot.player } }.toMap()
        val photoIds = allVisible.filter { it.isPhoto }.mapTo(HashSet()) { it.id }
        renderedIds = renderedIds.filter { it in players || it == vlcItemId }.toSet() + photoIds

        if (current != null && !current.isPhoto && DecoderCheck.decision(current.id) == true) playVlc(current) else stopVlc()
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
        if (vlcFailed) {
            error = item.id to "Bu video telefonun çözücüsüyle açılamıyor ve VLC başlatılamadı."
            return
        }
        // VLC henüz hazır değilse hazır olunca arrange() tekrar çağrılır
        val engine = vlc ?: return prepareVlc()
        stopVlc()
        vlcItemId = item.id
        engine.play(
            item.uri,
            onFirstFrame = {
                renderedIds = renderedIds + item.id
                OpenTimer.firstFrame(item.id, "vlc")
                resume.get(item)?.let { ms ->
                    engine.seekTo(ms)
                    onResumed(item, ms)
                }
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
        private var item: VideoItem? = null
        private var active = false
        private var decoderName: String? = null
        private var videoFormat: Format? = null
        private var enhancer: LoudnessEnhancer? = null
        private var enhancerSessionId = C.AUDIO_SESSION_ID_UNSET

        /** Videonun kare hızı; başlıkta yazmıyorsa 0 */
        val frameRate: Float get() = videoFormat?.frameRate ?: 0f

        init {
            player.addAnalyticsListener(this)
        }

        fun load(item: VideoItem) {
            itemId = item.id
            this.item = item
            active = false
            decoderName = null
            videoFormat = null
            // Önceki videonun sayfasına çizmesin; yeni videonun sayfası kendi yüzeyini bağlar
            player.clearVideoSurface()
            player.playWhenReady = false
            updateSpeed()
            updateScrubbing()
            updateRepeat()
            player.setMediaItem(MediaItem.Builder().setUri(item.uri).setMediaId(item.id.toString()).build())
            player.prepare()
            updateBoost()
        }

        fun clear() {
            if (itemId == null) return
            itemId = null
            item = null
            active = false
            updateSpeed()
            updateScrubbing()
            updateRepeat()
            updateBoost()
            player.stop()
            player.clearMediaItems()
            player.clearVideoSurface()
        }

        fun activate(play: Boolean) {
            if (!active) restorePosition()
            active = true
            player.playWhenReady = play
            updateSpeed()
            updateScrubbing()
            updateRepeat()
            updateBoost()
        }

        /** Uzun video ekrana gelince kaldığı yerden başlar */
        private fun restorePosition() {
            val item = item ?: return
            val ms = resume.get(item) ?: return
            player.seekTo(ms)
            onResumed(item, ms)
        }

        fun deactivate() {
            if (!active) return
            active = false
            updateSpeed()
            updateScrubbing()
            updateRepeat()
            updateBoost()
            player.playWhenReady = false
            // Geri dönülünce baştan başlasın; beklerken de ilk karesi görünsün
            player.seekTo(0)
        }

        fun updateSpeed() {
            val target = if (active) speed else 1f
            if (player.playbackParameters.speed != target) {
                player.setPlaybackSpeed(target)
            }
        }

        fun updateScrubbing() {
            val target = active && rewinding
            if (player.isScrubbingModeEnabled != target) {
                player.setScrubbingModeEnabled(target)
            }
        }

        /** Ekrandaki video "bitince sonrakine geç" açıkken bir kez oynar, diğerleri başa sarar */
        fun updateRepeat() {
            val target = if (active && autoAdvance) Player.REPEAT_MODE_OFF else Player.REPEAT_MODE_ONE
            if (player.repeatMode != target) player.repeatMode = target
        }

        fun updateBoost() {
            val session = player.audioSessionId
            val shouldBoost = active && boosted && !muted && session != C.AUDIO_SESSION_ID_UNSET
            if (!shouldBoost) {
                runCatching { enhancer?.enabled = false }
                return
            }
            runCatching {
                if (enhancer == null || enhancerSessionId != session) {
                    enhancer?.release()
                    enhancer = LoudnessEnhancer(session)
                    enhancerSessionId = session
                }
                // +600 mB (~6 dB) sesi yaklaşık %200 seviyesine çıkarır
                enhancer?.setTargetGain(600)
                enhancer?.enabled = true
            }
        }

        fun releaseEnhancer() {
            runCatching { enhancer?.release() }
            enhancer = null
            enhancerSessionId = C.AUDIO_SESSION_ID_UNSET
        }

        override fun onPlaybackStateChanged(eventTime: AnalyticsListener.EventTime, state: Int) {
            if (state == Player.STATE_ENDED && active && ownId(eventTime) != null && !onEnded()) player.seekTo(0)
        }

        override fun onAudioSessionIdChanged(eventTime: AnalyticsListener.EventTime, audioSessionId: Int) {
            updateBoost()
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

        // Yazılım çözücü normal hızda 4 saniyeden kısa sürede 50 kare atladıysa video takılıyor demektir
        // (2x hızda 60 Hz ekranda fazla karelerin atlanması normaldir, o sırada motor değiştirilmez)
        override fun onDroppedVideoFrames(eventTime: AnalyticsListener.EventTime, droppedFrames: Int, elapsedMs: Long) {
            val name = decoderName ?: return
            if (speed <= 1f && !rewinding && droppedFrames >= 50 && elapsedMs < 4000 && isSoftwareDecoder(name)) switchToVlc(eventTime)
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

/** Bir videonun izlenmiş sayılması için oynaması gereken süre */
private const val VIEW_AFTER_MS = 5_000L

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
    // H.264 ve HEVC (H.265) MP4'lerde hangi karelerin referans olmadığını (B-kare / ara katman)
    // başlığından ayrıştırır; böylece 2x hızda yetişmeyen kareler çözücüye girmeden atlanır
    val extractors = DefaultExtractorsFactory()
        .experimentalSetCodecsToParseWithinGopSampleDependencies(
            C.VIDEO_CODEC_FLAG_H264 or C.VIDEO_CODEC_FLAG_H265,
        )
    val mediaSourceFactory = DefaultMediaSourceFactory(context, extractors)

    // Bir çözücü açılamazsa (ör. donanım NO_MEMORY verirse) sıradakini denesin;
    // 2x hızlı oynatmada yüksek kare hızlı (60 fps → 120 fps) videolarda takılmayı önlemek için:
    // - Geciken referans-dışı kareleri çözücüye sokmadan düşür (500 ms geride kalıp keyframe sıçraması yapmasın)
    // - Android 14+'ta sadece referans için çözülen kareleri ekrana çizmeden geç (DECODE_ONLY)
    // - Sabit 10 ms döngüsü yerine kare zamanına göre dinamik uyanma kullan (120 fps = 8,3 ms)
    val renderers = object : DefaultRenderersFactory(context) {
        override fun buildVideoRenderers(
            context: Context,
            extensionRendererMode: Int,
            mediaCodecSelector: MediaCodecSelector,
            enableDecoderFallback: Boolean,
            eventHandler: Handler,
            eventListener: VideoRendererEventListener,
            allowedVideoJoiningTimeMs: Long,
            out: ArrayList<Renderer>,
        ) {
            val videoRenderer = object : MediaCodecVideoRenderer(
                Builder(context)
                    .setCodecAdapterFactory(codecAdapterFactory)
                    .setMediaCodecSelector(mediaCodecSelector)
                    .setAllowedJoiningTimeMs(allowedVideoJoiningTimeMs)
                    .setEnableDecoderFallback(enableDecoderFallback)
                    .setEventHandler(eventHandler)
                    .setEventListener(eventListener)
                    .setMaxDroppedFramesToNotify(MAX_DROPPED_VIDEO_FRAME_COUNT_TO_NOTIFY)
                    .experimentalSetParseAv1SampleDependencies(true)
                    .experimentalSetLateThresholdToDropDecoderInputUs(
                        DEFAULT_LATE_THRESHOLD_TO_DROP_DECODER_INPUT_US,
                    )
                    .experimentalSetEnableMediaCodecBufferDecodeOnlyFlag(true)
                    .setEnableDurationToProgressUs(true),
            ) {
                override fun getCodecOperatingRateV23(
                    playbackSpeed: Float,
                    format: Format,
                    streamFormats: Array<Format>,
                ): Float {
                    val rate = super.getCodecOperatingRateV23(playbackSpeed, format, streamFormats)
                    // Değişken kare hızlı (VFR) telefon videolarında başlıkta kare hızı yazmıyorsa
                    // super -1 döner ve donanım çözücü düşük saat hızında (30 fps) kalır; 60 fps varsay
                    val target = if (rate > 0f) rate else 60f * playbackSpeed
                    if (playbackSpeed <= 1f) return target
                    val maxSupported = codecInfo?.getMaxSupportedFrameRate(format.width, format.height) ?: -1f
                    return maxOf(target, maxSupported, 120f)
                }
            }
            out.add(videoRenderer)
        }
    }.setEnableDecoderFallback(true)

    return ExoPlayer.Builder(context, renderers)
        .setMediaSourceFactory(mediaSourceFactory)
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
