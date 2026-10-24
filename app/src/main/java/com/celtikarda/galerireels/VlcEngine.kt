package com.celtikarda.galerireels

import android.content.Context
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.util.Log
import android.view.SurfaceView
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.interfaces.IVLCVout
import java.util.concurrent.Executors
import kotlin.concurrent.thread

/**
 * VLC'nin oynatma motoru (FFmpeg + dav1d). Telefonun donanım çözücüsünün açamadığı ve
 * Android'in yazılım çözücüsünün yetişemediği videolar (ör. 1440x1600@60 H.264, AV1) için.
 * Çok çekirdekli yazılım çözme yaptığı için bu videoları takılmadan oynatabiliyor.
 *
 * libVLC'nin başlatma / durdurma / duraklatma çağrıları VLC'nin kendi iş parçacıklarını
 * bekleyebiliyor (ör. stop() video açılana kadar). Bunlar ana iş parçacığında yapılınca uygulama
 * donuyordu; hepsi sırayla "vlc-control" iş parçacığında yapılır. Görüntü yüzeyi bağlama
 * (attach/detach) Android gereği ana iş parçacığında kalır.
 */
class VlcEngine private constructor(private val appContext: Context, private val libVlc: LibVLC) {

    private val player = MediaPlayer(libVlc)
    private val vout: IVLCVout = player.vlcVout
    private val control = Executors.newSingleThreadExecutor { Thread(it, "vlc-control") }
    private val main = Handler(Looper.getMainLooper())

    // Ana iş parçacığında kullanılanlar
    private var hasMedia = false
    private var pendingStart = false
    private var muted = false
    private var boosted = false
    /** Her play() / stop() ile artar; önceki videodan gecikmiş gelen olaylar ve işler yok sayılır */
    private var generation = 0
    /** VLC'ye verilmiş videonun numarası; olaylar ancak bu [generation] ile aynıysa geçerli */
    private var readyGeneration = 0
    // Olaylarla gelen son konum; okumak için VLC'yi beklemek gerekmesin
    private var positionCache = 0f
    private var timeCache = 0L

    // Sadece vlc-control iş parçacığında kullanılanlar
    private var file: ParcelFileDescriptor? = null
    private var controlGeneration = 0

    private var onFirstFrame: () -> Unit = {}
    private var onError: () -> Unit = {}

    // Debug sürümde 2 saniyede bir kare istatistiklerini logcat'e yazar (akıcılık testi için)
    private val debug = appContext.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    private val statsLogger = object : Runnable {
        override fun run() {
            if (!hasMedia) return
            onControl {
                player.media?.let { media ->
                    media.stats?.let { s ->
                        Log.i(TAG, "kare: çözülen=${s.decodedVideo} gösterilen=${s.displayedPictures} kaybolan=${s.lostPictures}")
                    }
                    media.release()
                }
            }
            main.postDelayed(this, 2000)
        }
    }

    init {
        player.setEventListener { event ->
            if (readyGeneration != generation) return@setEventListener
            when (event.type) {
                MediaPlayer.Event.Vout -> if (event.voutCount > 0) onFirstFrame()
                MediaPlayer.Event.Playing -> applyVolume()
                MediaPlayer.Event.EncounteredError -> onError()
                MediaPlayer.Event.TimeChanged -> timeCache = event.timeChanged
                MediaPlayer.Event.PositionChanged -> {
                    val next = event.positionChanged
                    // :input-repeat ile başa sarınca TimeChanged gecikebiliyor; izlenme sayımı için sıfırla
                    if (next < 0.05f && positionCache > 0.85f && timeCache > 1500L) {
                        timeCache = 0L
                    }
                    positionCache = next
                }
                MediaPlayer.Event.EndReached -> {
                    positionCache = 0f
                    timeCache = 0L
                }
            }
        }
    }

    private fun targetVolume(): Int = when {
        muted -> 0
        boosted -> 200
        else -> 100
    }

    fun play(uri: Uri, onFirstFrame: () -> Unit, onError: () -> Unit) {
        val gen = ++generation
        readyGeneration = 0
        this.onFirstFrame = onFirstFrame
        this.onError = onError
        hasMedia = true
        positionCache = 0f
        timeCache = 0L
        // Görüntü yüzeyi bağlanmadan başlarsa VLC görüntü çıkışı açamaz; yüzey hazır olunca başla
        pendingStart = !vout.areViewsAttached()
        val startNow = !pendingStart
        val volume = targetVolume()

        onControl {
            stopPlayer()
            // VLC content:// adreslerini açamaz; dosyayı biz açıp tanıtıcısını veriyoruz
            val fd = runCatching { appContext.contentResolver.openFileDescriptor(uri, "r") }.getOrNull()
            if (fd == null) {
                main.post { if (gen == generation) onError() }
                return@onControl
            }
            file = fd
            val media = Media(libVlc, fd.fileDescriptor).apply {
                // Buraya sadece donanımın açamadığı videolar gelir; doğrudan FFmpeg / dav1d ile çöz
                setHWDecoderEnabled(false, false)
                addOption(":input-repeat=65535") // başa sar
            }
            player.media = media
            media.release()
            player.volume = volume
            controlGeneration = gen
            // Bundan sonra gelen olaylar bu videoya ait
            main.post { if (gen == generation) readyGeneration = gen }
            if (startNow) player.play()
        }
        if (debug) {
            main.removeCallbacks(statsLogger)
            main.postDelayed(statsLogger, 2000)
        }
    }

    /** vlc-control iş parçacığında sırayla çalıştırır (motor kapatıldıysa hiçbir şey yapmaz) */
    private fun onControl(block: () -> Unit) {
        if (!control.isShutdown) control.execute(block)
    }

    private fun start() {
        pendingStart = false
        val gen = generation
        onControl { if (gen == controlGeneration) player.play() }
    }

    fun stop() {
        generation++
        readyGeneration = 0
        main.removeCallbacks(statsLogger)
        hasMedia = false
        pendingStart = false
        positionCache = 0f
        timeCache = 0L
        onControl { stopPlayer() }
    }

    /** vlc-control iş parçacığında: önceki videoyu durdurup dosyasını kapatır */
    private fun stopPlayer() {
        if (controlGeneration != 0 || file != null) player.stop()
        controlGeneration = 0
        file?.close()
        file = null
    }

    fun pause() {
        val gen = generation
        onControl { if (gen == controlGeneration) player.pause() }
    }

    fun resume() {
        if (!hasMedia || pendingStart) return
        val gen = generation
        onControl { if (gen == controlGeneration) player.play() }
    }

    /** 0..1 arası konuma sarar */
    fun seek(fraction: Float) {
        if (!hasMedia) return
        positionCache = fraction
        val gen = generation
        onControl { if (gen == controlGeneration) player.position = fraction }
    }

    fun setMuted(muted: Boolean) {
        this.muted = muted
        if (hasMedia) applyVolume()
    }

    fun setBoosted(boosted: Boolean) {
        this.boosted = boosted
        if (hasMedia) applyVolume()
    }

    private fun applyVolume() {
        val volume = targetVolume()
        val gen = generation
        onControl { if (gen == controlGeneration) player.volume = volume }
    }

    /** 0..1 arası oynatma konumu */
    val progress: Float get() = if (hasMedia) positionCache else 0f

    /** Oynatma konumu (ms) */
    val timeMs: Long get() = if (hasMedia) timeCache else 0L

    /**
     * Yüzey oluşup boyutu belli olduğunda (SurfaceHolder.Callback.surfaceChanged) çağrılır.
     * VLC görüntüyü yüzeyin tamamına gerer; [onAspectRatio] ile gelen orana göre yüzeyi
     * boyutlandırmak çağıranın işi.
     */
    fun attach(view: SurfaceView, width: Int, height: Int, onAspectRatio: (Float) -> Unit) {
        if (!vout.areViewsAttached()) {
            vout.setVideoView(view)
            vout.attachViews { _, _, _, visibleWidth, visibleHeight, sarNum, sarDen ->
                if (visibleWidth > 0 && visibleHeight > 0) {
                    val sar = if (sarNum > 0 && sarDen > 0) sarNum.toFloat() / sarDen else 1f
                    onAspectRatio(visibleWidth * sar / visibleHeight)
                }
            }
        }
        vout.setWindowSize(width, height)
        if (pendingStart) start()
    }

    fun detach() {
        if (vout.areViewsAttached()) vout.detachViews()
    }

    fun release() {
        stop()
        detach()
        onControl {
            player.release()
            libVlc.release()
        }
        control.shutdown()
    }

    companion object {
        private const val TAG = "VlcEngine"

        /**
         * VLC'nin açılışı (kütüphaneleri yüklemesi) yarım saniye kadar sürüyor; bunu arka planda
         * yapar, motor hazır olunca ana iş parçacığında [onReady]'yi çağırır.
         */
        fun createAsync(context: Context, onReady: (VlcEngine) -> Unit) {
            val appContext = context.applicationContext
            thread(name = "vlc-init") {
                val libVlc = LibVLC(
                    appContext,
                    arrayListOf(
                        "--no-osd",
                        "--avcodec-threads=0", // tüm çekirdekleri kullan
                        // Altyazı / ekran yazısı yok. Açık kalınca VLC ilk videoda telefondaki bütün
                        // yazı tiplerini tarıyor (vivo'da 342 dosya, 216 MB): önbellek yokken yarım
                        // dakikadan uzun sürüyor ve video o bitene kadar açılmıyordu.
                        "--no-spu",
                        "--text-renderer=none",
                    ),
                )
                Handler(Looper.getMainLooper()).post { onReady(VlcEngine(appContext, libVlc)) }
            }
        }
    }
}
