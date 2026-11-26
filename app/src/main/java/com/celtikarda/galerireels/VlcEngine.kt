package com.celtikarda.galerireels

import android.content.Context
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.system.Os
import android.system.OsConstants
import android.util.Log
import android.view.SurfaceView
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.interfaces.IVLCVout
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread
import kotlin.math.roundToInt

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
class VlcEngine private constructor(
    private val appContext: Context,
    private val libVlc: LibVLC,
    /** VLC'nin sesi bu Android ses oturumunda çalınır; dengeleyici buna takılır (bkz. [setNormalize]) */
    private val audioSessionId: Int,
) {

    private val player = MediaPlayer(libVlc)
    private val vout: IVLCVout = player.vlcVout
    private val control = Executors.newSingleThreadExecutor { Thread(it, "vlc-control") }
    private val main = Handler(Looper.getMainLooper())

    // Ana iş parçacığında kullanılanlar
    /** VLC'nin görüntüyü çizdiği yüzey */
    private var attachedView: SurfaceView? = null
    private var hasMedia = false
    private var pendingStart = false
    /** Video açıkken yüzey gitti (uygulamadan çıkıldı); dönünce görüntü [restoreVideo] ile açılır */
    private var viewLost = false
    /**
     * Duraklatılmış videoda yüzey geri geldi: durduğu kare çizilsin diye sessizce kısa bir an
     * oynatılıyor, ilk kare gelince yeniden duraklatılacak (bkz. [restoreVideo])
     */
    @Volatile private var restoringPaused = false
    private val finishPausedRestore = Runnable {
        if (!restoringPaused) return@Runnable
        restoringPaused = false
        val gen = generation
        val volume = targetVolume()
        onControl {
            if (gen == controlGeneration) {
                player.pause()
                player.volume = volume
            }
        }
        onFirstFrame()
    }
    private var muted = false
    private var boost = 0f
    private var dynamics: android.media.audiofx.DynamicsProcessing? = null
    private var normalize = false

    /**
     * Dengeleme açıkken video sessiz başlar: VLC ses izini açmadan önce etkinleştirilen dengeleyici
     * ses sunucusunda kapalı kalıyor, ilk an ses dengelenmeden (yüksek) çıkıyordu. Ses izi
     * çalmaya başlayınca dengeleyici yeniden açılır, ses ondan sonra açılır.
     */
    private var normalizerPending = false
    private val normalizerFallback = Runnable { finishNormalizerWait() }
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager

    /** Bu video "Playing" oldu; ondan sonra başlayan ses izi VLC'ninkidir (öncesi eski videonun kapanışı) */
    private var playingSeen = false

    /**
     * Bir ses izi çalmaya başlayınca haber verir. Android uygulamalara oturum numarasını
     * göstermiyor (sessionId:0); video "Playing" olduktan sonraki ilk değişiklik VLC'nin izidir. Yanılırsa da
     * dengeleyici [applyNormalize] ile her "Playing"de ve en geç [NORMALIZER_FALLBACK_MS]'de açılır.
     */
    private val playbackCallback = object : android.media.AudioManager.AudioPlaybackCallback() {
        override fun onPlaybackConfigChanged(configs: MutableList<android.media.AudioPlaybackConfiguration>) {
            if (normalizerPending && playingSeen) finishNormalizerWait()
        }
    }

    private fun finishNormalizerWait() {
        main.removeCallbacks(normalizerFallback)
        if (!normalizerPending) return
        normalizerPending = false
        applyNormalize()
        if (hasMedia) applyVolume()
        if (debug) Log.i(TAG, "dengeleyici ses iziyle açıldı")
    }
    private var speed = 1f
    /** Geri sarılıyor: ses kısılır (video ileri-geri atlarken cızırtı olmasın) */
    private var scrubbing = false
    /** Hızlı sarmada vlc-control kuyruğunda bekleyen hedef (ms); -1 = bekleyen yok */
    private val pendingSeekMs = AtomicLong(-1L)
    /** Her play() / stop() ile artar; önceki videodan gecikmiş gelen olaylar ve işler yok sayılır */
    private var generation = 0
    /** VLC'ye verilmiş videonun numarası; olaylar ancak bu [generation] ile aynıysa geçerli */
    private var readyGeneration = 0
    // Olaylarla gelen son konum; okumak için VLC'yi beklemek gerekmesin
    private var positionCache = 0f
    private var timeCache = 0L
    /** Kaldığı yerden açıldı: bitince başa sarmayı biz yaparız (VLC başlangıç noktasına sarardı) */
    private var manualRepeat = false

    // Sadece vlc-control iş parçacığında kullanılanlar
    private var file: ParcelFileDescriptor? = null
    private var controlGeneration = 0

    /**
     * Kaldığı yerden açılan video sona geldi (ana iş parçacığında). true dönerse (ör. otomatik
     * geçişle sıradaki videoya geçiliyor) başa sarılmaz, son kare ekranda kalır.
     */
    var onEndReached: () -> Boolean = { false }

    private var onFirstFrame: () -> Unit = {}
    private var onViewLost: () -> Unit = {}
    private var onError: () -> Unit = {}

    // Debug sürümde 2 saniyede bir kare istatistiklerini logcat'e yazar (akıcılık testi için)
    private val debug = appContext.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    private val statsLogger = object : Runnable {
        override fun run() {
            if (!hasMedia) return
            onControl {
                player.media?.let { media ->
                    media.stats?.let { s ->
                        Log.i(TAG, "kare: çözülen=${s.decodedVideo} gösterilen=${s.displayedPictures} kaybolan=${s.lostPictures} sesKayıp=${s.lostAbuffers} t=${player.time} hız=${player.rate}")
                    }
                    media.release()
                }
            }
            main.postDelayed(this, 2000)
        }
    }

    init {
        audioManager.registerAudioPlaybackCallback(playbackCallback, main)
        player.setEventListener { event ->
            if (readyGeneration != generation) return@setEventListener
            when (event.type) {
                MediaPlayer.Event.Vout -> if (event.voutCount > 0) {
                    // Görüntü çıkışı açıldı ama ilk kare henüz çizilmemiş olabilir; çizilsin diye kısa bekle
                    if (restoringPaused) main.postDelayed(finishPausedRestore, PAUSED_RESTORE_FRAME_MS)
                    else onFirstFrame()
                }
                MediaPlayer.Event.Playing -> {
                    playingSeen = true
                    applyVolume()
                    applyNormalize()
                    // Ses izinin başladığı yakalanamazsa (bkz. [playbackCallback]) en geç burada açılır
                    main.removeCallbacks(normalizerFallback)
                    if (normalizerPending) main.postDelayed(normalizerFallback, NORMALIZER_FALLBACK_MS)
                }
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
                    // Kaldığı yerden açılan video bitti: sıradakine geçilecekse başa sarma
                    if (manualRepeat && !onEndReached()) restartFromZero()
                    positionCache = 0f
                    timeCache = 0L
                }
            }
        }
    }

    private fun targetVolume(): Int = when {
        muted || scrubbing || normalizerPending || restoringPaused -> 0
        else -> 100 + (boost * 100).roundToInt()
    }

    /**
     * [waitForView] true ise eldeki yüzey başka bir videonun sayfasında: video hazırlanır ama yeni
     * yüzey bağlanana kadar başlamaz (eski yüzeye çizip onu kaldırırken beklemeye yol açmasın).
     */
    fun play(
        uri: Uri,
        onFirstFrame: () -> Unit,
        onError: () -> Unit,
        onViewLost: () -> Unit = {},
        waitForView: Boolean = false,
        startMs: Long = 0L,
        stopAtEnd: Boolean = false,
    ) {
        val gen = ++generation
        readyGeneration = 0
        this.onFirstFrame = onFirstFrame
        this.onError = onError
        this.onViewLost = onViewLost
        hasMedia = true
        viewLost = false
        cancelPausedRestore()
        // Dengeleme açıksa ses izi başlayana kadar sessiz (bkz. [normalizerPending])
        main.removeCallbacks(normalizerFallback)
        normalizerPending = normalize && Build.VERSION.SDK_INT >= 28
        playingSeen = false
        positionCache = 0f
        timeCache = startMs.coerceAtLeast(0L)
        // Kaldığı yerden açılan videoda VLC'nin kendi tekrarı başa değil başlangıç noktasına
        // sarıyor; bu videolarda bitince başa sarmayı kendimiz yaparız (bkz. [restartFromZero]).
        // [stopAtEnd] (otomatik geçiş) ise de: bitiş [onEndReached]'e sorulur, kendiliğinden sarılmaz
        manualRepeat = startMs > 0L || stopAtEnd
        // Görüntü yüzeyi bağlanmadan başlarsa VLC görüntü çıkışı açamaz; yüzey hazır olunca başla
        pendingStart = waitForView || !vout.areViewsAttached()
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
            newMedia(fd, startMs, repeat = !stopAtEnd).let {
                player.media = it
                it.release() // player kendi referansını tutar
            }
            player.volume = volume
            player.rate = speed
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

    /**
     * vlc-control iş parçacığında: VLC'ye verilecek video. [startMs] > 0 ise doğrudan oradan açılır;
     * önce baştan açıp ilk kareden sonra oraya atlamak yazılımsal çözücüde ana kareden hedefe kadar
     * her kareyi çözdürüyor, baştaki kare görünüp video bir süre donuyordu.
     */
    private fun newMedia(fd: ParcelFileDescriptor, startMs: Long, repeat: Boolean = true): Media {
        val media = Media(libVlc, fd.fileDescriptor).apply {
            // Buraya sadece donanımın açamadığı videolar gelir; doğrudan FFmpeg / dav1d ile çöz
            setHWDecoderEnabled(false, false)
            if (startMs > 0L) {
                addOption(":start-time=%.3f".format(java.util.Locale.US, startMs / 1000.0))
                // Hedefe tam oturmak için anahtar kareden itibaren yüzlerce kare çözmek gerekiyor
                // (webm'de ~1,8 s); en yakın anahtar kareden başlamak anında akar, birkaç saniye
                // önceden başlamak da kaldığın yeri hatırlatır
                addOption(":input-fast-seek")
            } else if (repeat) {
                addOption(":input-repeat=65535") // başa sar
            }
        }
        return media
    }

    /**
     * Kaldığı yerden açılan video bitti: aynı dosyayla baştan, bu sefer VLC'nin kendi tekrarıyla
     * açılır. Yüzey bağlı kalır.
     */
    private fun restartFromZero() {
        manualRepeat = false
        positionCache = 0f
        timeCache = 0L
        val gen = generation
        onControl {
            val fd = file
            if (gen != controlGeneration || fd == null) return@onControl
            // VLC tanıtıcının kopyasını (dup) okur, okuma konumu ortak: video bitince dosyanın
            // sonunda kalıyor ve yeni açılış boş dosya görüp hata veriyordu. Başa al.
            runCatching { Os.lseek(fd.fileDescriptor, 0L, OsConstants.SEEK_SET) }
            newMedia(fd, 0L).let {
                player.media = it
                it.release()
            }
            player.play()
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

    /** [onStopped] VLC görüntü yüzeyini bıraktıktan sonra ana iş parçacığında çağrılır */
    fun stop(onStopped: (() -> Unit)? = null) {
        generation++
        readyGeneration = 0
        main.removeCallbacks(statsLogger)
        hasMedia = false
        manualRepeat = false
        pendingStart = false
        viewLost = false
        cancelPausedRestore()
        positionCache = 0f
        timeCache = 0L
        onControl {
            stopPlayer()
            onStopped?.let { main.post(it) }
        }
    }

    /** vlc-control iş parçacığında: önceki videoyu durdurup dosyasını kapatır */
    private fun stopPlayer() {
        if (controlGeneration != 0 || file != null) player.stop()
        controlGeneration = 0
        file?.close()
        file = null
    }

    fun pause() {
        val wasRestoring = restoringPaused
        cancelPausedRestore()
        val gen = generation
        val volume = targetVolume()
        onControl {
            if (gen == controlGeneration) {
                player.pause()
                if (wasRestoring) player.volume = volume
            }
        }
    }

    fun resume() {
        if (!hasMedia || pendingStart) return
        // Durduğu kare çizilirken oynat'a basıldı: yeniden duraklatma, sesi aç
        if (restoringPaused) {
            cancelPausedRestore()
            applyVolume()
            return
        }
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

    /** Tam olarak [timeMs]'ye sarar (kaldığın yer, ±10 sn, kare kare, A-B tekrarı) */
    fun seekTo(timeMs: Long) {
        if (!hasMedia) return
        timeCache = timeMs
        val gen = generation
        onControl { if (gen == controlGeneration) player.setTime(timeMs, false) }
    }

    /**
     * Basılı tutarak geri sarmada kullanılır: en yakın ana kareye (keyframe) atlar. Tam isabetli
     * sarma yazılımsal çözülen ağır videolarda ana kareden hedefe kadar her kareyi çözüyor; sık
     * sık yapılınca bir sonraki sarma gelmeden hiçbir kare ekrana çıkmıyor ve video donuyordu.
     * Kuyrukta bekleyen sarma varsa yenisi onun hedefini günceller, sarmalar birikmez.
     */
    fun seekFast(timeMs: Long) {
        if (!hasMedia) return
        timeCache = timeMs
        val gen = generation
        if (pendingSeekMs.getAndSet(timeMs) != -1L) return
        onControl {
            val target = pendingSeekMs.getAndSet(-1L)
            if (gen == controlGeneration && target >= 0L) player.setTime(target, true)
        }
    }

    fun setScrubbing(scrubbing: Boolean) {
        if (this.scrubbing == scrubbing) return
        this.scrubbing = scrubbing
        if (hasMedia) applyVolume()
    }

    fun setMuted(muted: Boolean) {
        this.muted = muted
        if (hasMedia) applyVolume()
    }

    /** 0 = %100, 1 = %200 (VLC ses seviyesi 100..200) */
    fun setBoost(level: Float) {
        this.boost = level.coerceIn(0f, 1f)
        if (hasMedia) applyVolume()
    }

    /**
     * Ses dengeleme (Android 9+): VLC'nin ses oturumuna ExoPlayer'daki dengeleyici takılır
     * (bkz. createNormalizer). VLC'nin %200'e kadar ses yükseltmesi dengeleyiciden önce
     * uygulandığı için sınırlayıcı patlamayı yine önler. Açıp kapatmak anında işler.
     */
    fun setNormalize(normalize: Boolean) {
        this.normalize = normalize
        if (!normalize && normalizerPending) finishNormalizerWait()
        applyNormalize()
    }

    /**
     * Dengeleyiciyi oturuma uygular. VLC çalmaya başlayınca (ses izini açınca) dengeleyicinin
     * denetimi başka bir tutamağa geçebiliyor ve açık görünse de ses sunucusunda kapalı kalıyordu;
     * o yüzden her "Playing"de denetim kontrol edilir, kaybolduysa yeniden takılır.
     */
    private fun applyNormalize() {
        if (Build.VERSION.SDK_INT < 28) return
        runCatching {
            val current = dynamics
            if (normalize && (current == null || !current.hasControl())) {
                current?.release()
                dynamics = createNormalizer(audioSessionId)
            }
            dynamics?.enabled = normalize
            if (debug) Log.i(TAG, "dengeleme=$normalize oturum=$audioSessionId etkin=${dynamics?.enabled} denetim=${dynamics?.hasControl()}")
        }.onFailure { Log.w(TAG, "dengeleyici takılamadı", it) }
    }

    fun setSpeed(speed: Float) {
        this.speed = speed
        if (!hasMedia) return
        val gen = generation
        onControl { if (gen == controlGeneration) player.rate = speed }
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
        // Önceki sayfanın yüzeyi hâlâ bağlıysa önce o bırakılır
        if (vout.areViewsAttached() && attachedView !== view) detach()
        if (!vout.areViewsAttached()) {
            attachedView = view
            vout.setVideoView(view)
            vout.attachViews { _, _, _, visibleWidth, visibleHeight, sarNum, sarDen ->
                if (visibleWidth > 0 && visibleHeight > 0) {
                    val sar = if (sarNum > 0 && sarDen > 0) sarNum.toFloat() / sarDen else 1f
                    onAspectRatio(visibleWidth * sar / visibleHeight)
                }
            }
        }
        vout.setWindowSize(width, height)
        if (pendingStart) start() else if (viewLost) restoreVideo()
    }

    /**
     * Yüzey yokken libVLC görüntü izini kapatır, yüzey gelince yeniden açar; ses hiç durmaz. Yeni
     * açılan çözücü bir sonraki ana kareye kadar hiçbir şey gösteremediği için görüntü saniyelerce
     * siyah kalıyordu (AV1 1080p60'ta ana kare aralığı 5,5 sn). En yakın ana kareye atlayınca hemen gelir.
     */
    private fun restoreVideo() {
        viewLost = false
        val gen = generation
        val target = timeCache
        onControl {
            if (gen != controlGeneration) return@onControl
            if (player.isPlaying) {
                player.setTime(target, true)
                return@onControl
            }
            // Duraklatılmış: VLC durmuşken yeni yüzeye kare çizmiyor, küçük resim kalıyordu. Durduğu
            // yere tam sarıp sessizce oynat; ilk kare gelince [finishPausedRestore] yeniden duraklatır.
            restoringPaused = true
            player.volume = 0
            player.setTime(target, false)
            player.play()
        }
    }

    private fun cancelPausedRestore() {
        restoringPaused = false
        main.removeCallbacks(finishPausedRestore)
    }

    fun detach() {
        attachedView = null
        // Durduğu kare çizilirken yine çıkıldı: oynamaya devam etmesin
        if (restoringPaused) pause()
        if (vout.areViewsAttached() && hasMedia && !pendingStart && !viewLost) {
            viewLost = true
            onViewLost()
        }
        if (vout.areViewsAttached()) vout.detachViews()
    }

    fun release() {
        stop()
        detach()
        normalize = false // bekleyen kontrol dengeleyiciyi yeniden takmasın
        normalizerPending = false
        main.removeCallbacks(normalizerFallback)
        audioManager.unregisterAudioPlaybackCallback(playbackCallback)
        runCatching { dynamics?.release() }
        dynamics = null
        onControl {
            player.release()
            libVlc.release()
        }
        control.shutdown()
    }

    companion object {
        private const val TAG = "VlcEngine"
        /** Ses izinin başladığı haberi gelmezse dengeleme beklenmeden ses bu kadar sonra açılır */
        private const val NORMALIZER_FALLBACK_MS = 500L
        /** Duraklatılmış videoda yüzey dönünce görüntü çıkışı açıldıktan sonra ilk karenin çizilmesi için beklenen süre */
        private const val PAUSED_RESTORE_FRAME_MS = 120L

        /**
         * VLC'nin açılışı (kütüphaneleri yüklemesi) yarım saniye kadar sürüyor; bunu arka planda
         * yapar, motor hazır olunca ana iş parçacığında [onReady]'yi çağırır.
         * VLC açılamazsa (ör. tanımadığı bir seçenek) uygulama çökmesin diye [onFailed] çağrılır.
         */
        fun createAsync(context: Context, onReady: (VlcEngine) -> Unit, onFailed: () -> Unit) {
            val appContext = context.applicationContext
            val session = (appContext.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager).generateAudioSessionId()
            thread(name = "vlc-init") {
                val libVlc = try {
                    LibVLC(appContext, options(session))
                } catch (e: IllegalStateException) {
                    Log.e(TAG, "LibVLC açılamadı", e)
                    Handler(Looper.getMainLooper()).post { onFailed() }
                    return@thread
                }
                Handler(Looper.getMainLooper()).post { onReady(VlcEngine(appContext, libVlc, session)) }
            }
        }

        /**
         * libVLC tanımadığı tek bir seçenekte bile hiç açılmıyor ("can't create LibVLC instance");
         * seçenek adları libVLC 3.x'teki adlarla birebir aynı olmalı (vlc --help).
         */
        private fun options(session: Int) = arrayListOf(
            "--no-osd",
            "--avcodec-threads=0", // tüm çekirdekleri kullan
            // Yüksek çözünürlüklü videolarda ve 2x hızlı oynatmada yazılımsal çözmeyi
            // hızlandırır: referans olmayan karelerde ağır deblocking filtresini atlar ve
            // geride kalan kareleri çözücüye sokmadan düşürür
            "--avcodec-fast",
            "--avcodec-skiploopfilter=1",
            // Geç kalan kareleri atmak şart: ekran video oynarken 60 Hz, 60 fps videoyu 2x'te
            // göstermek saniyede 120 kare ister. --no-drop-late-frames denendi; görüntü 2x'te
            // hiç hızlanmadı, sesin her saniye bir saniye gerisine düştü (vivo, AV1 1080p60).
            // Atınca ekrana ~48 kare/sn çıkıyor ve görüntü sesle eşit kalıyor.
            "--drop-late-frames",
            "--skip-frames",
            // Altyazı / ekran yazısı yok. Açık kalınca VLC ilk videoda telefondaki bütün
            // yazı tiplerini tarıyor (vivo'da 342 dosya, 216 MB): önbellek yokken yarım
            // dakikadan uzun sürüyor ve video o bitene kadar açılmıyordu.
            "--no-spu",
            "--text-renderer=none",
            // Ses dengeleme için: VLC sesini bizim verdiğimiz Android ses oturumunda çalar, ona
            // ExoPlayer'daki dengeleyicinin aynısı takılır (bkz. [setNormalize]). VLC'nin kendi
            // süzgeçleri kullanılamıyor: libVLC oynatıcısı "audio-filter" ayarını eziyor; videoya
            // ":audio-filter=normvol" vermek de hiç işlemiyordu (log'da süzgeç hiç yüklenmiyordu).
            "--audiotrack-session-id=$session",
            // VLC sesi Android'e 48000 Hz verir; 44100 Hz videolarda örnekleme hızını kendisi
            // çevirir. Varsayılan çeviricisi sesi "elektronik"/cızırtılı yapıyordu (vivo,
            // 1440x1600 AAC 44.1k videolar). soxr en kaliteli çevirici: ses vivo Galeri'deki gibi temiz.
            "--audio-resampler=soxr",
        )
    }
}
