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
import kotlin.concurrent.thread

/**
 * VLC'nin oynatma motoru (FFmpeg + dav1d). Telefonun donanım çözücüsünün açamadığı ve
 * Android'in yazılım çözücüsünün yetişemediği videolar (ör. 1440x1600@60 H.264, AV1) için.
 * Çok çekirdekli yazılım çözme yaptığı için bu videoları takılmadan oynatabiliyor.
 */
class VlcEngine private constructor(private val appContext: Context, private val libVlc: LibVLC) {

    private val player = MediaPlayer(libVlc)
    private val vout: IVLCVout = player.vlcVout
    private var file: ParcelFileDescriptor? = null
    private var hasMedia = false
    private var pendingStart = false
    private var muted = false

    private var onFirstFrame: () -> Unit = {}
    private var onError: () -> Unit = {}

    // Debug sürümde 2 saniyede bir kare istatistiklerini logcat'e yazar (akıcılık testi için)
    private val debug = appContext.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    private val handler = Handler(Looper.getMainLooper())
    private val statsLogger = object : Runnable {
        override fun run() {
            if (!hasMedia) return
            player.media?.let { media ->
                media.stats?.let { s ->
                    Log.i(TAG, "kare: çözülen=${s.decodedVideo} gösterilen=${s.displayedPictures} kaybolan=${s.lostPictures}")
                }
                media.release()
            }
            handler.postDelayed(this, 2000)
        }
    }

    init {
        player.setEventListener { event ->
            when (event.type) {
                MediaPlayer.Event.Vout -> if (event.voutCount > 0) onFirstFrame()
                MediaPlayer.Event.Playing -> player.volume = if (muted) 0 else 100
                MediaPlayer.Event.EncounteredError -> onError()
            }
        }
    }

    fun play(uri: Uri, onFirstFrame: () -> Unit, onError: () -> Unit) {
        stop()
        this.onFirstFrame = onFirstFrame
        this.onError = onError
        // VLC content:// adreslerini açamaz; dosyayı biz açıp tanıtıcısını veriyoruz
        val fd = runCatching { appContext.contentResolver.openFileDescriptor(uri, "r") }.getOrNull()
        if (fd == null) {
            onError()
            return
        }
        file = fd
        val media = Media(libVlc, fd.fileDescriptor).apply {
            // Buraya sadece donanımın açamadığı videolar gelir; doğrudan FFmpeg ile çöz
            setHWDecoderEnabled(false, false)
            addOption(":input-repeat=65535") // başa sar
        }
        player.media = media
        media.release()
        hasMedia = true
        // Görüntü yüzeyi bağlanmadan başlarsa VLC görüntü çıkışı açamaz; yüzey hazır olunca başla
        if (vout.areViewsAttached()) start() else pendingStart = true
    }

    private fun start() {
        pendingStart = false
        player.play()
        if (debug) handler.postDelayed(statsLogger, 2000)
    }

    fun stop() {
        handler.removeCallbacks(statsLogger)
        pendingStart = false
        if (hasMedia) player.stop()
        hasMedia = false
        file?.close()
        file = null
    }

    fun pause() = player.pause()

    fun resume() {
        if (hasMedia && !pendingStart) player.play()
    }

    fun setMuted(muted: Boolean) {
        this.muted = muted
        if (hasMedia) player.volume = if (muted) 0 else 100
    }

    /** 0..1 arası oynatma konumu */
    val progress: Float get() = if (hasMedia) player.position else 0f

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
        player.release()
        libVlc.release()
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
                    ),
                )
                Handler(Looper.getMainLooper()).post { onReady(VlcEngine(appContext, libVlc)) }
            }
        }
    }
}
