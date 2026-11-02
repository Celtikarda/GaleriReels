package com.celtikarda.galerireels.ui

import android.Manifest
import android.app.Activity
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.graphics.drawable.Icon as SystemIcon
import android.media.AudioManager
import android.os.SystemClock
import android.provider.Settings
import android.util.Rational
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.core.app.PictureInPictureModeChangedInfo
import androidx.core.util.Consumer
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Build
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.Image
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.doOnAttach
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.media3.exoplayer.ExoPlayer
import coil3.compose.AsyncImage
import com.celtikarda.galerireels.Album
import com.celtikarda.galerireels.BackgroundPlayService
import com.celtikarda.galerireels.DecoderCheck
import com.celtikarda.galerireels.EdgeHoldAction
import com.celtikarda.galerireels.GalleryViewModel
import com.celtikarda.galerireels.MediaActions
import com.celtikarda.galerireels.R
import com.celtikarda.galerireels.ReelsPlayback
import com.celtikarda.galerireels.SeekPreview
import com.celtikarda.galerireels.VideoItem
import com.celtikarda.galerireels.VideoThumb
import com.celtikarda.galerireels.VlcEngine
import com.celtikarda.galerireels.frameSaveSupported
import com.celtikarda.galerireels.saveFrameAsPhoto
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt

private val textShadow = TextStyle(shadow = Shadow(Color.Black.copy(alpha = 0.85f), offset = Offset(0f, 1.5f), blurRadius = 8f))

@Composable
fun ReelsScreen(vm: GalleryViewModel, album: Album, onBack: () -> Unit) {
    val items = vm.items
    val pagerState = rememberPagerState { vm.items.size }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val activity = context as? Activity
    var fullscreen by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(fullscreen) {
        val act = activity ?: return@LaunchedEffect
        act.requestedOrientation = if (fullscreen) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        val window = act.window ?: return@LaunchedEffect
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        if (fullscreen) {
            insetsController.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            insetsController.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            insetsController.show(WindowInsetsCompat.Type.systemBars())
            resetBrightness(act)
        }
    }

    var userPaused by remember { mutableStateOf(false) }
    var muted by rememberSaveable { mutableStateOf(false) }
    var boosted by rememberSaveable { mutableStateOf(false) }
    /** Menüden seçilen oynatma hızı (uzun basılı tutunca geçici olarak 2x) */
    var speed by rememberSaveable { mutableFloatStateOf(1f) }
    var fastForward by remember { mutableStateOf(false) }
    var rewind by remember { mutableStateOf(false) }
    var clearView by remember { mutableStateOf(false) }
    // Sade tam ekran: yatay tam ekranda bütün düğmeler / yazılar gizli, sadece video
    var hideUi by rememberSaveable { mutableStateOf(false) }
    /** Yatay tam ekranda ekran kilidi: dokunuşlar videoya gitmez (yatarken / cepteyken izlemek için) */
    var locked by remember { mutableStateOf(false) }
    var lockHintTick by remember { mutableIntStateOf(0) }
    /** Tam ekranda iki parmakla büyütünce video ekranı doldurur (kenarları kırpılır) */
    var fill by rememberSaveable { mutableStateOf(false) }
    /** Uyku zamanlayıcısının dolacağı an (SystemClock.elapsedRealtime); kapalıysa 0 */
    var sleepAt by rememberSaveable { mutableLongStateOf(0L) }
    /** Küçük pencerede (resim içinde resim) oynuyor */
    var inPip by remember { mutableStateOf(false) }
    /** Küçük pencereye geçildi, uygulama henüz tekrar açılmadı: pencere kapanınca video durur */
    var pipEntered by remember { mutableStateOf(false) }
    // Tam ekranda sol kenarda yukarı/aşağı kaydırma: parlaklık, sağ kenarda: ses
    var adjustKind by remember { mutableIntStateOf(0) } // -1 parlaklık, 1 ses, 0 gösterge kapalı
    var adjustLevel by remember { mutableFloatStateOf(0f) }
    var adjusting by remember { mutableStateOf(false) }
    var volumeLevel by remember { mutableFloatStateOf(-1f) }
    var brightLevel by remember { mutableFloatStateOf(-1f) }
    val audioManager = remember { context.getSystemService(AudioManager::class.java) }
    LaunchedEffect(fullscreen) {
        if (!fullscreen) {
            locked = false
            hideUi = false
        }
    }

    // "Karışık sıra" / "Yeniden eskiye" bilgi baloncuğu
    var chipText by remember { mutableStateOf("") }
    var chipTick by remember { mutableIntStateOf(0) }
    var chipVisible by remember { mutableStateOf(false) }
    var inBackground by remember { mutableStateOf(false) }

    val playback = remember {
        ReelsPlayback(
            context,
            scope,
            onViewed = vm::addView,
            onResumed = { _, ms ->
                chipText = "Kaldığın yerden: ${formatDuration(ms)}"
                chipTick++
            },
            onEnded = {
                val nextIndex = pagerState.settledPage + 1
                if (nextIndex < vm.items.size) {
                    // Arka planda kare çizilmediği için kaydırma animasyonu ilerlemez
                    scope.launch { if (inBackground) pagerState.scrollToPage(nextIndex) else pagerState.animateScrollToPage(nextIndex) }
                    true
                } else {
                    false
                }
            },
        )
    }
    DisposableEffect(playback) {
        onDispose {
            activity?.let { act ->
                resetBrightness(act)
                if (Build.VERSION.SDK_INT >= 31) {
                    runCatching { act.setPictureInPictureParams(PictureInPictureParams.Builder().setAutoEnterEnabled(false).build()) }
                }
                act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                act.window?.let { win ->
                    WindowCompat.getInsetsController(win, win.decorView)
                        .show(WindowInsetsCompat.Type.systemBars())
                }
            }
            BackgroundPlayService.stop(context)
            playback.release()
        }
    }

    // Favori isteği: Android önce kullanıcıya sorar ("Medya yönetimi" izni verildiyse sormaz)
    var pendingFavorite by remember { mutableStateOf<Pair<VideoItem, Boolean>?>(null) }
    val favoriteLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        val (item, favorite) = pendingFavorite ?: return@rememberLauncherForActivityResult
        pendingFavorite = null
        if (result.resultCode == Activity.RESULT_OK) {
            vm.setFavorite(item, favorite)
            chipText = if (favorite) "Favorilere eklendi" else "Favorilerden çıkarıldı"
            chipTick++
        }
    }
    var infoItem by remember { mutableStateOf<VideoItem?>(null) }
    // Tüm beğenileri kaldırmadan önce onay
    var pendingClearLikes by remember { mutableStateOf<VideoItem?>(null) }
    val notifPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val toggleBackgroundPlay: () -> Unit = {
        val nextState = !vm.stats.backgroundPlay
        vm.stats.updateBackgroundPlay(nextState)
        if (nextState && Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        chipText = if (nextState) "Arka planda oynatma açıldı" else "Arka planda oynatma kapatıldı"
        chipTick++
    }

    // Temizlik: işaretlenenler tek onayla Android'in çöp kutusuna taşınır
    val cleanup = album.id == Album.CLEANUP
    var pendingTrash by remember { mutableStateOf<List<VideoItem>>(emptyList()) }
    var leaveAfterTrash by remember { mutableStateOf(false) }
    var askBeforeLeaving by remember { mutableStateOf(false) }
    val trashLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        val videos = pendingTrash
        pendingTrash = emptyList()
        if (result.resultCode == Activity.RESULT_OK && videos.isNotEmpty()) {
            vm.onTrashed(videos)
            chipText = "${formatSize(videos.sumOf { it.sizeBytes })} temizlendi"
            chipTick++
            if (leaveAfterTrash) onBack()
        }
    }
    fun trashMarked(thenLeave: Boolean) {
        val videos = vm.cleanupMarked
        val request = MediaActions.trashRequest(context, videos.map { it.uri }, trash = true) ?: return
        pendingTrash = videos
        leaveAfterTrash = thenLeave
        trashLauncher.launch(IntentSenderRequest.Builder(request.intentSender).build())
    }
    // Tam ekrandayken geri tuşu önce dikey görünüme döndürür; işaretli video varken çıkmadan önce sorar
    val leave = {
        when {
            locked -> lockHintTick += 1
            hideUi -> hideUi = false
            fullscreen -> fullscreen = false
            cleanup && vm.cleanupMarked.isNotEmpty() -> askBeforeLeaving = true
            else -> onBack()
        }
    }
    BackHandler(enabled = cleanup && vm.cleanupMarked.isNotEmpty()) { askBeforeLeaving = true }
    BackHandler(enabled = fullscreen) { fullscreen = false }
    // Sade tam ekranda geri tuşu önce düğmeleri geri getirir
    BackHandler(enabled = hideUi && !locked) { hideUi = false }
    // Kilitliyken geri tuşu da çalışmaz; kilit simgesi gösterilir
    BackHandler(enabled = locked) { lockHintTick++ }
    LaunchedEffect(cleanup) {
        if (cleanup) {
            chipText = "Silmek istediğin videoyu sola kaydır"
            chipTick++
        }
    }

    val page = pagerState.settledPage
    val current = items.getOrNull(page)
    val previous = items.getOrNull(page - 1)
    val next = items.getOrNull(page + 1)

    // Kaydırma bitip sayfa oturunca o video oynar; komşuları (önce sıradaki) arkada hazırlanır
    LaunchedEffect(current?.id, previous?.id, next?.id) {
        playback.show(current, listOfNotNull(next, previous))
    }
    LaunchedEffect(current?.id) {
        userPaused = false
        fastForward = false
        rewind = false
        clearView = false
    }
    LaunchedEffect(muted) { playback.setMuted(muted) }
    LaunchedEffect(boosted) { playback.setBoosted(boosted) }
    LaunchedEffect(vm.stats.normalizeAudio) { playback.setNormalize(vm.stats.normalizeAudio) }
    LaunchedEffect(fastForward, speed) { playback.setSpeed(if (fastForward) 2f else speed) }
    LaunchedEffect(vm.stats.autoAdvance) { playback.setAutoAdvance(vm.stats.autoAdvance) }
    LaunchedEffect(rewind) { playback.setRewinding(rewind) }

    LaunchedEffect(chipTick) {
        if (chipTick == 0) return@LaunchedEffect
        chipVisible = true
        delay(1400)
        chipVisible = false
    }

    fun updateBackgroundNotification() {
        val item = items.getOrNull(pagerState.settledPage) ?: return
        BackgroundPlayService.startOrUpdate(
            context = context,
            title = item.name.substringBeforeLast('.'),
            subtitle = vm.albumNameOf(item),
            paused = userPaused,
            onTogglePause = {
                userPaused = !userPaused
                playback.setPaused(userPaused)
                updateBackgroundNotification()
            },
            onNext = {
                if (items.size > 1) {
                    scope.launch {
                        val nextIndex = (pagerState.settledPage + 1) % items.size
                        pagerState.scrollToPage(nextIndex)
                        userPaused = false
                        playback.setPaused(false)
                        updateBackgroundNotification()
                    }
                }
            },
            onStop = {
                userPaused = true
                playback.setPaused(true)
            },
        )
    }

    LaunchedEffect(current?.id, inBackground) {
        if (inBackground && vm.stats.backgroundPlay && current != null) {
            updateBackgroundNotification()
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) {
        // Küçük pencereye geçerken uygulama duraklar ama video oynamaya devam etmeli
        if (Build.VERSION.SDK_INT >= 26 && activity?.isInPictureInPictureMode == true) {
            pipEntered = true
            return@LifecycleEventEffect
        }
        inBackground = true
        if (vm.stats.backgroundPlay && current != null && !userPaused) {
            updateBackgroundNotification()
        } else {
            playback.onHostPause()
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        // Küçük pencere kapatıldı (ya da o sırada ekran kapandı): video durur
        if (pipEntered) {
            pipEntered = false
            inBackground = true
            userPaused = true
            playback.setPaused(true)
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        pipEntered = false
        inBackground = false
        BackgroundPlayService.stop(context)
        playback.onHostResume()
    }

    // ---- Küçük pencere (resim içinde resim)
    val pipSupported = remember {
        Build.VERSION.SDK_INT >= 26 && context.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
    }
    val togglePauseFromPip by rememberUpdatedState {
        if (current != null && !current.isPhoto) {
            userPaused = !userPaused
            playback.setPaused(userPaused)
        }
    }
    val nextFromPip by rememberUpdatedState {
        if (items.size > 1) {
            scope.launch {
                pagerState.scrollToPage((pagerState.settledPage + 1) % items.size)
                userPaused = false
                playback.setPaused(false)
            }
        }
    }
    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                when (intent?.getIntExtra(PIP_EXTRA, 0)) {
                    PIP_PLAY_PAUSE -> togglePauseFromPip()
                    PIP_NEXT -> nextFromPip()
                }
            }
        }
        ContextCompat.registerReceiver(context, receiver, IntentFilter(PIP_ACTION), ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { context.unregisterReceiver(receiver) }
    }
    DisposableEffect(activity) {
        val act = activity as? ComponentActivity ?: return@DisposableEffect onDispose { }
        val listener = Consumer<PictureInPictureModeChangedInfo> { info ->
            inPip = info.isInPictureInPictureMode
            if (inPip) pipEntered = true
        }
        act.addOnPictureInPictureModeChangedListener(listener)
        onDispose { act.removeOnPictureInPictureModeChangedListener(listener) }
    }
    fun pipParams(): PictureInPictureParams? {
        if (!pipSupported || Build.VERSION.SDK_INT < 26) return null
        val item = current
        val ratio = item?.aspectRatio?.takeIf { it > 0f } ?: item?.let { playback.aspectRatios[it.id] } ?: (16f / 9f)
        val safe = ratio.coerceIn(0.42f, 2.38f) // Android 1:2,39 – 2,39:1 dışını kabul etmiyor
        val playing = item != null && !item.isPhoto && !userPaused
        val builder = PictureInPictureParams.Builder()
            .setAspectRatio(Rational((safe * 1000).roundToInt(), 1000))
            .setActions(
                listOf(
                    pipAction(context, if (playing) R.drawable.ic_pause else R.drawable.ic_play, if (playing) "Durdur" else "Oynat", PIP_PLAY_PAUSE),
                    pipAction(context, R.drawable.ic_skip_next, "Sonraki", PIP_NEXT),
                ),
            )
        // Android 12+: tam ekranda video oynarken ana ekrana dönünce kendiliğinden küçük pencereye geçer
        if (Build.VERSION.SDK_INT >= 31) builder.setAutoEnterEnabled(fullscreen && playing && !cleanup)
        return builder.build()
    }
    val pipRatioKey = current?.let { playback.aspectRatios[it.id] }
    LaunchedEffect(fullscreen, userPaused, current?.id, pipRatioKey) {
        val act = activity ?: return@LaunchedEffect
        if (Build.VERSION.SDK_INT >= 26) pipParams()?.let { runCatching { act.setPictureInPictureParams(it) } }
    }
    val enterPip: () -> Unit = {
        val params = pipParams()
        if (params != null && Build.VERSION.SDK_INT >= 26) {
            if (userPaused) {
                userPaused = false
                playback.setPaused(false)
            }
            runCatching { activity?.enterPictureInPictureMode(params) }
        }
    }

    // ---- Uyku zamanlayıcısı: süre dolunca video durur (arka planda oynuyorsa da)
    LaunchedEffect(sleepAt) {
        if (sleepAt == 0L) return@LaunchedEffect
        val wait = sleepAt - SystemClock.elapsedRealtime()
        if (wait > 0) delay(wait)
        sleepAt = 0L
        userPaused = true
        playback.setPaused(true)
        if (inBackground && vm.stats.backgroundPlay) updateBackgroundNotification()
        chipText = "Uyku zamanlayıcısı doldu, video durduruldu"
        chipTick++
    }
    val pickSleep: (Int) -> Unit = { minutes ->
        sleepAt = if (minutes == 0) 0L else SystemClock.elapsedRealtime() + minutes * 60_000L
        chipText = if (minutes == 0) "Uyku zamanlayıcısı kapatıldı" else "$minutes dakika sonra video duracak"
        chipTick++
    }

    // ---- Parlaklık / ses kaydırma (yatay tam ekranda sol / sağ kenar)
    val adjust: (Int, Float) -> Unit = { side, delta ->
        adjusting = true
        if (side < 0) {
            activity?.window?.let { window ->
                // Göz parlaklığı doğrusal algılamaz: düz ölçekte üst yarıda kaydırınca ekran
                // neredeyse değişmiyor, azaltma gecikiyormuş gibi geliyordu. Parmak algısal
                // seviyeyi değiştirir, ekrana bunun ~2.2 kuvveti verilir.
                if (brightLevel < 0f) {
                    val attrs = window.attributes
                    val now = if (attrs.screenBrightness >= 0f) attrs.screenBrightness else systemBrightness(context)
                    brightLevel = now.coerceIn(0f, 1f).pow(1f / 2.2f)
                }
                brightLevel = (brightLevel + delta).coerceIn(0.05f, 1f)
                val target = brightLevel.pow(2.2f)
                val attrs = window.attributes
                if (abs(attrs.screenBrightness - target) > 0.002f) {
                    attrs.screenBrightness = target
                    window.attributes = attrs
                }
                adjustKind = -1
                adjustLevel = brightLevel
            }
        } else {
            val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
            if (volumeLevel < 0f) volumeLevel = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / max
            volumeLevel = (volumeLevel + delta).coerceIn(0f, 1f)
            val index = (volumeLevel * max).roundToInt()
            if (index != audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)) {
                runCatching { audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, index, 0) }
            }
            if (index > 0 && muted) muted = false
            adjustKind = 1
            adjustLevel = volumeLevel
        }
    }
    val adjustEnd: () -> Unit = {
        adjusting = false
        volumeLevel = -1f
        brightLevel = -1f
    }
    LaunchedEffect(adjusting) {
        if (!adjusting && adjustKind != 0) {
            delay(700)
            adjustKind = 0
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        VerticalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = !locked && !inPip,
            beyondViewportPageCount = 1,
            key = { items[it].id },
        ) { index ->
            val item = items[index]
            val active = index == pagerState.settledPage

            ReelPage(
                item = item,
                active = active,
                player = playback.players[item.id],
                vlc = playback.vlc?.takeIf { playback.vlcViewItemId == item.id },
                aspectRatio = item.aspectRatio.takeIf { it > 0f } ?: playback.aspectRatios[item.id] ?: 0f,
                // Hazırda bekleyen komşu, kaydırırken küçük resmiyle görünür: bazı videoların
                // ilk karesi simsiyah (kararmadan açılan kurgular), küçük resim daha iyi duruyor
                firstFrameShown = active && item.id in playback.renderedIds,
                error = playback.error?.takeIf { it.first == item.id }?.second,
                paused = active && userPaused,
                clearView = active && (clearView || inPip || locked || hideUi),
                fullscreen = fullscreen,
                fill = fullscreen && fill,
                gesturesEnabled = fullscreen && !locked && !inPip,
                onAdjust = adjust,
                onAdjustEnd = adjustEnd,
                onPinch = { zoomIn ->
                    if (fill != zoomIn) {
                        fill = zoomIn
                        chipText = if (zoomIn) "Ekranı doldur" else "Videonun tamamı"
                        chipTick++
                    }
                },
                leftHoldAction = vm.stats.leftHoldAction,
                rightHoldAction = vm.stats.rightHoldAction,
                liked = vm.isLikedNow(item),
                likes = vm.likesOf(item),
                favorite = vm.isFavorite(item),
                views = vm.viewsOf(item),
                muted = muted,
                boosted = boosted,
                backgroundPlay = vm.stats.backgroundPlay,
                speed = speed,
                autoAdvance = vm.stats.autoAdvance,
                normalize = vm.stats.normalizeAudio,
                sleepAt = sleepAt,
                pipSupported = pipSupported,
                abLoop = playback.abLoop.takeIf { active },
                albumName = vm.albumNameOf(item),
                avatar = vm.coverOf(item.albumId) ?: item,
                onTogglePause = {
                    if (active && !item.isPhoto) {
                        userPaused = !userPaused
                        playback.setPaused(userPaused)
                    }
                },
                onSpeedHold = { holding ->
                    if (active && !item.isPhoto) {
                        if (holding && userPaused) {
                            userPaused = false
                            playback.setPaused(false)
                        }
                        fastForward = holding
                    }
                },
                onRewindHold = { holding ->
                    if (active && !item.isPhoto) {
                        if (holding && userPaused) {
                            userPaused = false
                            playback.setPaused(false)
                        }
                        rewind = holding
                    }
                },
                onClearHold = { holding ->
                    if (active) {
                        if (hideUi) {
                            // Sade tam ekranda ortaya basılı tutmak düğmeleri geri getirir
                            if (holding) hideUi = false
                        } else {
                            clearView = holding
                        }
                    }
                },
                onDoubleTapLike = { vm.like(item) },
                onToggleLike = { vm.toggleLike(item) },
                onRemoveOneLike = {
                    vm.removeOneLike(item)
                    chipText = "Bir beğeni geri alındı"
                    chipTick++
                },
                onClearLikes = { pendingClearLikes = item },
                onViewsTap = {
                    val views = vm.viewsOf(item)
                    chipText = if (item.isPhoto) {
                        if (views == 0) "Bu fotoğrafa henüz bakmadın" else "Bu fotoğrafa ${formatCount(views)} kez baktın"
                    } else {
                        if (views == 0) "Bu videoyu henüz izlemedin" else "Bu videoyu ${formatCount(views)} kez izledin"
                    }
                    chipTick++
                },
                onShare = { shareVideo(context, item) },
                onToggleMute = { muted = !muted },
                onToggleBoost = {
                    boosted = !boosted
                    if (boosted && muted) muted = false
                    chipText = if (boosted) "Ses %200'e yükseltildi" else "Ses normale döndü (%100)"
                    chipTick++
                },
                onToggleBackgroundPlay = toggleBackgroundPlay,
                onToggleFavorite = {
                    val favorite = !vm.isFavorite(item)
                    MediaActions.favoriteRequest(context, listOf(item), favorite)?.let { request ->
                        pendingFavorite = item to favorite
                        favoriteLauncher.launch(IntentSenderRequest.Builder(request.intentSender).build())
                    }
                },
                onInfo = { infoItem = item },
                onSeek = { fraction -> playback.seekTo(item, fraction) },
                onSeekBy = { deltaMs -> if (active) playback.seekBy(item, deltaMs) },
                onStepFrame = { forward -> if (active) playback.stepFrame(item, forward) },
                onPickSpeed = { picked ->
                    speed = picked
                    chipText = "Oynatma hızı: ${formatSpeed(picked)}"
                    chipTick++
                },
                onToggleAutoAdvance = {
                    val next = !vm.stats.autoAdvance
                    vm.stats.updateAutoAdvance(next)
                    chipText = if (next) "Video bitince sıradakine geçilecek" else "Video bitince başa sarılacak"
                    chipTick++
                },
                onToggleNormalize = {
                    val next = !vm.stats.normalizeAudio
                    vm.stats.updateNormalizeAudio(next)
                    chipText = if (next) "Ses dengeleme açık" else "Ses dengeleme kapalı"
                    chipTick++
                },
                onPickSleep = pickSleep,
                onPip = enterPip,
                onAbLoop = {
                    if (active) {
                        playback.cycleAbLoop(item)
                        val ab = playback.abLoop
                        val b = ab?.second
                        chipText = when {
                            ab == null -> "A-B tekrarı kapatıldı"
                            b == null -> "A: ${formatDuration(ab.first)} · B için menüden tekrar seç"
                            else -> "A-B tekrarı: ${formatDuration(ab.first)} – ${formatDuration(b)}"
                        }
                        chipTick++
                    }
                },
                onSaveFrame = {
                    val position = playback.positionOf(item)
                    if (active && position != null) {
                        chipText = "Kare kaydediliyor…"
                        chipTick++
                        scope.launch {
                            val saved = saveFrameAsPhoto(context, item, position)
                            chipText = if (saved != null) "Kare Resimler/GaleriReels'e kaydedildi" else "Bu videodan kare alınamadı"
                            chipTick++
                        }
                    }
                },
                onDelete = if (cleanup) {
                    {
                        vm.markForCleanup(item)
                        chipText = "Silinecekler listesine eklendi"
                        chipTick++
                    }
                } else {
                    null
                },
            )
        }

        if (cleanup && items.isEmpty()) {
            Text(
                "Gösterilecek video kalmadı",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 16.sp,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        AnimatedVisibility(
            visible = !clearView && !inPip && !locked && !hideUi,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            TopBar(
                title = album.name,
                shuffled = vm.shuffled,
                showShuffle = !cleanup,
                fullscreen = fullscreen,
                onBack = leave,
                onToggleShuffle = {
                    val index = vm.toggleShuffle(current?.id)
                    chipText = if (vm.shuffled) "Karışık sıra" else "Yeniden eskiye"
                    chipTick++
                    scope.launch { pagerState.scrollToPage(index) }
                },
                onToggleFullscreen = { fullscreen = !fullscreen },
                onLock = {
                    locked = true
                    clearView = false
                    lockHintTick++
                },
                onHideUi = {
                    hideUi = true
                    chipText = "Sade görünüm · geri tuşu ya da ortaya basılı tut: düğmeler"
                    chipTick++
                },
            )
        }

        if (locked && !inPip) {
            LockOverlay(
                hintTick = lockHintTick,
                onUnlock = {
                    locked = false
                    chipText = "Kilit açıldı"
                    chipTick++
                },
                onShowHint = { lockHintTick++ },
            )
        }

        if (adjustKind != 0 && !inPip) {
            AdjustIndicator(
                brightness = adjustKind < 0,
                level = adjustLevel,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        if (cleanup && vm.cleanupMarked.isNotEmpty() && !clearView && !inPip && !hideUi) {
            CleanupBar(
                count = vm.cleanupMarked.size,
                bytes = vm.cleanupMarked.sumOf { it.sizeBytes },
                onUndo = {
                    // Geri alınan video ekrandaki yere konur; kaydırıcı ekrandaki videoyu yerinde
                    // tutmaya çalıştığı için o sayfaya açıkça gidilir (yoksa ekranda başka video kalır)
                    val index = pagerState.settledPage
                    vm.undoLastMark(index)
                    scope.launch {
                        // Kaydırıcı yeni listeyi bir kare sonra ölçüyor ve o zaman ekrandakini bir
                        // sonraki sayfaya itiyor; ölçtükten sonra geri alınan videoya git
                        withFrameNanos { }
                        if (pagerState.currentPage != index) pagerState.scrollToPage(index)
                    }
                },
                onDelete = { trashMarked(thenLeave = false) },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 60.dp),
            )
        }

        AnimatedVisibility(
            visible = !inPip && (fastForward || rewind || chipVisible),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = if (cleanup) 124.dp else 64.dp),
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Text(
                when {
                    fastForward -> "2x İleri ⏩"
                    rewind -> "⏪ 2x Geri"
                    else -> chipText
                },
                color = if (fastForward || rewind) FavoriteYellow else Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color.Black.copy(alpha = 0.65f))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }

        if (askBeforeLeaving) {
            val marked = vm.cleanupMarked
            AlertDialog(
                onDismissRequest = { askBeforeLeaving = false },
                title = { Text("Silinecekler var") },
                text = {
                    Text(
                        "${marked.size} video (${formatSize(marked.sumOf { it.sizeBytes })}) silinmek üzere " +
                            "işaretli. Çöp kutusuna taşınsın mı?",
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        askBeforeLeaving = false
                        trashMarked(thenLeave = true)
                    }) { Text("Sil") }
                },
                dismissButton = {
                    TextButton(onClick = {
                        askBeforeLeaving = false
                        vm.clearCleanupMarks()
                        onBack()
                    }) { Text("Silmeden çık") }
                },
            )
        }

        pendingClearLikes?.let { item ->
            AlertDialog(
                onDismissRequest = { pendingClearLikes = null },
                containerColor = appColors.card,
                titleContentColor = appColors.textPrimary,
                textContentColor = appColors.textSecondary,
                title = { Text("Tüm beğeniler kaldırılsın mı?", fontWeight = FontWeight.Bold) },
                text = {
                    Text(
                        "${formatCount(vm.likesOf(item))} beğeninin hepsi silinecek. " +
                            "${if (item.isPhoto) "Fotoğraf" else "Video"} Beğendiklerin'den çıkar ve Temizlik'te artık korunmaz."
                    )
                },
                dismissButton = {
                    TextButton(onClick = { pendingClearLikes = null }) {
                        Text("İptal", color = appColors.textSecondary)
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            vm.clearLikes(item)
                            pendingClearLikes = null
                            chipText = "Beğeniler kaldırıldı"
                            chipTick++
                        }
                    ) {
                        Text("Kaldır", color = LikeRed, fontWeight = FontWeight.Bold)
                    }
                },
            )
        }

        infoItem?.let { item ->
            VideoInfoDialog(
                item = item,
                views = vm.viewsOf(item),
                likes = vm.likesOf(item),
                favorite = vm.isFavorite(item),
                onDismiss = { infoItem = null },
            )
        }
    }
}

@Composable
private fun ReelPage(
    item: VideoItem,
    active: Boolean,
    /** Bu videoyu hazırlayan oynatıcı; yoksa sadece küçük resim görünür */
    player: ExoPlayer?,
    /** Bu video VLC ile oynatılıyorsa motor, değilse null */
    vlc: VlcEngine?,
    /** Videonun en / boy oranı; bilinmiyorsa 0 */
    aspectRatio: Float,
    firstFrameShown: Boolean,
    error: String?,
    paused: Boolean,
    clearView: Boolean,
    fullscreen: Boolean,
    /** Video ekranı doldurur (kenarları kırpılır) */
    fill: Boolean,
    /** Parlaklık / ses kaydırma ve iki parmakla doldur / sığdır */
    gesturesEnabled: Boolean,
    onAdjust: (side: Int, delta: Float) -> Unit,
    onAdjustEnd: () -> Unit,
    onPinch: (zoomIn: Boolean) -> Unit,
    leftHoldAction: EdgeHoldAction,
    rightHoldAction: EdgeHoldAction,
    /** Bu açılışta beğenildi mi (kalp dolu) */
    liked: Boolean,
    /** Toplam beğeni sayısı */
    likes: Int,
    /** Telefonun galerisinde favori mi */
    favorite: Boolean,
    /** Bu videoyu kaç kez izledin */
    views: Int,
    muted: Boolean,
    boosted: Boolean,
    backgroundPlay: Boolean,
    /** Menüden seçilen oynatma hızı */
    speed: Float,
    autoAdvance: Boolean,
    normalize: Boolean,
    /** Uyku zamanlayıcısının dolacağı an; kapalıysa 0 */
    sleepAt: Long,
    pipSupported: Boolean,
    /** Ekrandaki videonun A-B tekrarı (A, B); yoksa null */
    abLoop: Pair<Long, Long?>?,
    albumName: String,
    avatar: VideoItem,
    onTogglePause: () -> Unit,
    onSpeedHold: (Boolean) -> Unit,
    onRewindHold: (Boolean) -> Unit,
    onClearHold: (Boolean) -> Unit,
    onDoubleTapLike: () -> Unit,
    onToggleLike: () -> Unit,
    onRemoveOneLike: () -> Unit,
    onClearLikes: () -> Unit,
    onViewsTap: () -> Unit,
    onShare: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleBoost: () -> Unit,
    onToggleBackgroundPlay: () -> Unit,
    onToggleFavorite: () -> Unit,
    onInfo: () -> Unit,
    onSeek: (Float) -> Unit,
    /** Tam ekranda kenarlara çift dokununca ±10 sn */
    onSeekBy: (Long) -> Unit,
    onStepFrame: (forward: Boolean) -> Unit,
    onPickSpeed: (Float) -> Unit,
    onToggleAutoAdvance: () -> Unit,
    onToggleNormalize: () -> Unit,
    onPickSleep: (minutes: Int) -> Unit,
    onPip: () -> Unit,
    onAbLoop: () -> Unit,
    onSaveFrame: () -> Unit,
    /** Temizlik modunda: videoyu silinecekler listesine ekler (sola kaydırma ya da çöp kutusu) */
    onDelete: (() -> Unit)?,
) {
    val haptic = LocalHapticFeedback.current
    var burstId by remember { mutableIntStateOf(0) }
    var burstAt by remember { mutableStateOf(Offset.Zero) }
    val tap by rememberUpdatedState(onTogglePause)
    val speedHold by rememberUpdatedState(onSpeedHold)
    val rewindHold by rememberUpdatedState(onRewindHold)
    val clearHold by rememberUpdatedState(onClearHold)
    val leftAction by rememberUpdatedState(leftHoldAction)
    val rightAction by rememberUpdatedState(rightHoldAction)
    val doubleTap by rememberUpdatedState(onDoubleTapLike)
    val seekBy by rememberUpdatedState(onSeekBy)
    // Kenarlara çift dokunarak sarma sadece yatay tam ekranda; dikeyde her yer beğenir
    val edgeSeek by rememberUpdatedState(fullscreen && !item.isPhoto)
    val delete by rememberUpdatedState(onDelete)
    // Çift dokunarak sarma göstergesi: hangi taraf (-1 / 1), toplam saniye, kaçıncı dokunuş
    var seekSide by remember { mutableIntStateOf(0) }
    var seekSeconds by remember { mutableIntStateOf(0) }
    var seekTick by remember { mutableIntStateOf(0) }
    var lastSeekAt by remember { mutableStateOf(0L) }
    val uiAlpha by animateFloatAsState(
        targetValue = if (clearView) 0f else 1f,
        animationSpec = tween(180),
        label = "uiAlpha",
    )

    // Temizlik: video sola kaydırılır, yeterince gidince silinecekler listesine düşer
    val swipeX = remember { Animatable(0f) }
    val pageScope = rememberCoroutineScope()
    val swipe = if (onDelete == null) Modifier else Modifier.pointerInput(Unit) {
        detectHorizontalDragGestures(
            onDragEnd = {
                pageScope.launch {
                    if (swipeX.value < -size.width * 0.28f) {
                        swipeX.animateTo(-size.width.toFloat(), tween(160))
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        delete?.invoke()
                    } else {
                        swipeX.animateTo(0f, spring())
                    }
                }
            },
            onDragCancel = { pageScope.launch { swipeX.animateTo(0f, spring()) } },
            onHorizontalDrag = { change, amount ->
                change.consume()
                pageScope.launch { swipeX.snapTo((swipeX.value + amount).coerceAtMost(0f)) }
            },
        )
    }

    val adjustBy by rememberUpdatedState(onAdjust)
    val adjustEnd by rememberUpdatedState(onAdjustEnd)
    val pinch by rememberUpdatedState(onPinch)
    val gestures = if (!gesturesEnabled) Modifier else Modifier.fullscreenGestures(
        onAdjust = { side, delta -> adjustBy(side, delta) },
        onAdjustEnd = { adjustEnd() },
        onPinch = { pinch(it) },
    )

    Box(Modifier.fillMaxSize().then(swipe).then(gestures)) {
        if (swipeX.value < 0f) DeleteBackdrop()
        Box(Modifier.fillMaxSize().offset { IntOffset(swipeX.value.roundToInt(), 0) }) {
            VideoSurface(item, player, vlc, aspectRatio, firstFrameShown, fill)

            // Tek dokunma: durdur/oynat · Çift dokunma: beğen (yatay tam ekranda sol / sağ kenarda ∓10 sn)
            // Sağ/sol kenara (%25) basılı tutma: Ayarlar'da seçilen işlem · Ortaya basılı tutma: net görünüm (butonları gizle)
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { tap() },
                            onLongPress = { offset ->
                                val action = when {
                                    item.isPhoto -> EdgeHoldAction.CLEAR_VIEW
                                    offset.x < size.width * 0.25f -> leftAction
                                    offset.x > size.width * 0.75f -> rightAction
                                    else -> EdgeHoldAction.CLEAR_VIEW
                                }
                                when (action) {
                                    EdgeHoldAction.FAST_FORWARD -> {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        speedHold(true)
                                    }
                                    EdgeHoldAction.REWIND -> {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        rewindHold(true)
                                    }
                                    EdgeHoldAction.CLEAR_VIEW -> {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        clearHold(true)
                                    }
                                    EdgeHoldAction.NONE -> Unit
                                }
                            },
                            onPress = {
                                try {
                                    awaitRelease()
                                } finally {
                                    speedHold(false)
                                    rewindHold(false)
                                    clearHold(false)
                                }
                            },
                            onDoubleTap = { offset ->
                                val side = when {
                                    !edgeSeek -> 0
                                    offset.x < size.width * 0.3f -> -1
                                    offset.x > size.width * 0.7f -> 1
                                    else -> 0
                                }
                                if (side != 0) {
                                    // Aynı kenara art arda çift dokunulunca saniyeler toplanır
                                    val now = System.currentTimeMillis()
                                    seekSeconds = if (side == seekSide && now - lastSeekAt < 900) seekSeconds + 10 else 10
                                    seekSide = side
                                    lastSeekAt = now
                                    seekTick++
                                    seekBy(side * 10_000L)
                                } else {
                                    burstAt = offset
                                    burstId++
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    doubleTap()
                                }
                            },
                        )
                    }
            )

            if (paused && !clearView) {
                PlayIcon(Modifier.align(Alignment.Center))
                if (!item.isPhoto) {
                    FrameStepButtons(
                        onStep = onStepFrame,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .offset(y = 76.dp),
                    )
                }
            }
            if (seekTick > 0) {
                key(seekTick) {
                    SeekBurst(
                        seconds = seekSeconds,
                        forward = seekSide > 0,
                        modifier = Modifier
                            .align(if (seekSide > 0) Alignment.CenterEnd else Alignment.CenterStart)
                            .padding(horizontal = 72.dp),
                    )
                }
            }
            if (error != null) {
                val ctx = LocalContext.current
                ErrorBox(
                    message = error,
                    onReport = { openIssueReport(ctx, item = item, errorText = error) },
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            if (burstId > 0) key(burstId) { HeartBurst(burstAt) }

            if (uiAlpha > 0f) {
                BottomInfo(
                    item = item,
                    albumName = albumName,
                    avatar = avatar,
                    favorite = favorite,
                    fullscreen = fullscreen,
                    speed = speed,
                    abLoop = abLoop,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .graphicsLayer { alpha = uiAlpha },
                )

                // Sağdaki butonların arkasında hafif karartma: parlak videolarda simge ve sayılar okunsun
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .width(96.dp)
                        .fillMaxHeight(if (fullscreen) 0.85f else 0.65f)
                        .graphicsLayer { alpha = uiAlpha }
                        .background(Brush.horizontalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.35f))))
                )

                ActionRail(
                    isPhoto = item.isPhoto,
                    fullscreen = fullscreen,
                    liked = liked,
                    likes = likes,
                    favorite = favorite,
                    views = views,
                    muted = muted,
                    boosted = boosted,
                    backgroundPlay = backgroundPlay,
                    speed = speed,
                    autoAdvance = autoAdvance,
                    normalize = normalize,
                    sleepAt = sleepAt,
                    pipSupported = pipSupported,
                    abLoop = abLoop,
                    onLike = onToggleLike,
                    onRemoveOneLike = onRemoveOneLike,
                    onClearLikes = onClearLikes,
                    onViews = onViewsTap,
                    onShare = onShare,
                    onMute = onToggleMute,
                    onBoost = onToggleBoost,
                    onBackgroundPlay = onToggleBackgroundPlay,
                    onFavorite = onToggleFavorite,
                    onInfo = onInfo,
                    onPickSpeed = onPickSpeed,
                    onToggleAutoAdvance = onToggleAutoAdvance,
                    onToggleNormalize = onToggleNormalize,
                    onPickSleep = onPickSleep,
                    onPip = onPip,
                    onAbLoop = onAbLoop,
                    onSaveFrame = onSaveFrame,
                    onDelete = onDelete,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .navigationBarsPadding()
                        .padding(end = 8.dp, bottom = if (fullscreen) 14.dp else 24.dp)
                        .graphicsLayer { alpha = uiAlpha },
                )

                if (active && !item.isPhoto) {
                    VideoProgress(
                        progress = {
                            when {
                                vlc != null -> vlc.progress
                                player != null -> player.duration.let { d -> if (d > 0) player.currentPosition.toFloat() / d else 0f }
                                else -> 0f
                            }
                        },
                        durationMs = item.durationMs,
                        uri = item.uri,
                        onSeek = onSeek,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .graphicsLayer { alpha = uiAlpha },
                    )
                }
            }
        }
    }
}

/** Sola kaydırılan videonun arkasından görünen kırmızı "Sil" zemini */
@Composable
private fun DeleteBackdrop() {
    Box(Modifier.fillMaxSize().background(DeleteRed), contentAlignment = Alignment.CenterEnd) {
        Column(Modifier.padding(end = 36.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(painterResource(R.drawable.ic_delete), contentDescription = null, tint = Color.White, modifier = Modifier.size(44.dp))
            Spacer(Modifier.height(6.dp))
            Text("Sil", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/** Temizlikte üstte: kaç video işaretli, ne kadar yer açılacak; Geri al / Sil */
@Composable
private fun CleanupBar(count: Int, bytes: Long, onUndo: () -> Unit, onDelete: () -> Unit, modifier: Modifier) {
    Row(
        modifier
            .padding(horizontal = 12.dp)
            .clip(RoundedCornerShape(50))
            .background(Color.Black.copy(alpha = 0.75f))
            .padding(start = 16.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(R.drawable.ic_delete), contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("$count video · ${formatSize(bytes)}", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(4.dp))
        TextButton(onClick = onUndo) { Text("Geri al", color = Color.White.copy(alpha = 0.8f)) }
        Button(onClick = onDelete, colors = ButtonDefaults.buttonColors(containerColor = DeleteRed, contentColor = Color.White)) {
            Text("Sil")
        }
    }
}

/**
 * Video alanı en baştan videonun oranında açılır (boyutu galeri biliyor); oynatıcı videonun
 * boyutunu söyleyene kadar tam ekran kalıp sonra küçülmez.
 */
@Composable
private fun VideoSurface(item: VideoItem, player: ExoPlayer?, vlc: VlcEngine?, aspectRatio: Float, firstFrameShown: Boolean, fill: Boolean = false) {
    if (item.isPhoto) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            AsyncImage(
                model = item.uri,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
        return
    }
    // Galeri boyutu bilmiyorsa ve video VLC'deyse oranı VLC'den öğreniriz
    var vlcAspect by remember(item.id) { mutableFloatStateOf(0f) }
    val ratio = if (aspectRatio > 0f) aspectRatio else vlcAspect

    BoxWithConstraints(Modifier.fillMaxSize().clipToBounds(), contentAlignment = Alignment.Center) {
        // Doldur: kısa kenar ekrana oturur, taşan kısım ekran dışında kalır
        val fillSize = if (fill && ratio > 0f && maxHeight.value > 0f) {
            if (ratio > maxWidth / maxHeight) DpSize(maxHeight * ratio, maxHeight)
            else DpSize(maxWidth, maxWidth / ratio)
        } else null
        val videoBox = when {
            ratio <= 0f -> Modifier.fillMaxSize()
            fillSize != null -> Modifier.requiredSize(fillSize)
            else -> Modifier.aspectRatio(ratio)
        }
        // SurfaceView, Compose'un kırpmasını dinlemez: taşan kısım kaydırırken komşu sayfanın
        // üstünde görünüyordu. Yüzeyi sayfanın sınırına kendimiz kırparız (Android 13+).
        val density = LocalDensity.current
        val surfaceClip = fillSize?.let {
            with(density) {
                val left = ((it.width - maxWidth) / 2).roundToPx().coerceAtLeast(0)
                val top = ((it.height - maxHeight) / 2).roundToPx().coerceAtLeast(0)
                android.graphics.Rect(left, top, left + maxWidth.roundToPx(), top + maxHeight.roundToPx())
            }
        }
        Box(videoBox) {
            when {
                vlc != null -> VlcSurface(vlc, surfaceClip, onAspectRatio = { vlcAspect = it })
                // Oynatıcı başka videoya geçerse yüzey de yenilenir
                player != null -> key(player) { ExoSurface(player, surfaceClip) }
            }

            // İlk kare gelene kadar küçük resim videonun yerinde durur, sonra hızlıca kaybolur
            val thumbAlpha by animateFloatAsState(
                targetValue = if (firstFrameShown) 0f else 1f,
                animationSpec = tween(150),
                label = "thumbAlpha",
            )
            if (thumbAlpha > 0f) {
                AsyncImage(
                    model = VideoThumb(item.uri, 1080),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = thumbAlpha },
                )
            }
        }
    }
}

@Composable
private fun ExoSurface(player: ExoPlayer, clip: android.graphics.Rect?) {
    AndroidView(
        factory = { ctx ->
            SurfaceView(ctx).apply {
                keepScreenOn = true
                player.setVideoSurfaceView(this)
            }
        },
        update = { if (it.clipBounds != clip) it.clipBounds = clip },
        onRelease = { player.clearVideoSurfaceView(it) },
        modifier = Modifier.fillMaxSize(),
    )
}

/** VLC görüntüyü yüzeyin tamamına gerer; yüzeyi videonun oranında tutmak çağıranın işi. */
@Composable
private fun VlcSurface(vlc: VlcEngine, clip: android.graphics.Rect?, onAspectRatio: (Float) -> Unit) {
    AndroidView(
        factory = { ctx ->
            SurfaceView(ctx).apply {
                keepScreenOn = true
                // Ekran o an duruk ise Compose yeni eklenen SurfaceView için çizim turu
                // başlatmıyor ve yüzey (dolayısıyla VLC görüntüsü) bir dokunuşa kadar oluşmuyor
                doOnAttach { post { requestLayout(); invalidate() } }
                holder.addCallback(object : SurfaceHolder.Callback {
                    override fun surfaceCreated(holder: SurfaceHolder) = Unit

                    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                        vlc.attach(this@apply, width, height, onAspectRatio)
                    }

                    override fun surfaceDestroyed(holder: SurfaceHolder) = vlc.detach()
                })
            }
        },
        update = { if (it.clipBounds != clip) it.clipBounds = clip },
        onRelease = { vlc.detach() },
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun BottomInfo(
    item: VideoItem,
    albumName: String,
    avatar: VideoItem,
    favorite: Boolean,
    fullscreen: Boolean = false,
    speed: Float = 1f,
    abLoop: Pair<Long, Long?>? = null,
    modifier: Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f))))
            .navigationBarsPadding()
            .padding(
                start = 14.dp,
                end = 76.dp,
                top = if (fullscreen) 24.dp else 80.dp,
                bottom = if (fullscreen) 14.dp else 22.dp,
            )
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(avatar, Modifier.size(36.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                albumName,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = textShadow,
                modifier = Modifier.weight(1f, fill = false),
            )
            val relative = relativeDate(item.dateMillis)
            if (relative.isNotEmpty()) {
                Text(
                    "  ·  $relative",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 13.sp,
                    maxLines = 1,
                    style = textShadow,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(formatDate(item.dateMillis), color = Color.White, fontSize = 14.sp, style = textShadow)
            if (favorite) {
                Spacer(Modifier.width(10.dp))
                Icon(
                    painterResource(R.drawable.ic_star),
                    contentDescription = null,
                    tint = FavoriteYellow,
                    modifier = Modifier.size(16.dp),
                )
                Text(" Favori", color = FavoriteYellow, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, style = textShadow)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            if (item.isPhoto) {
                val dims = if (item.width > 0) "${item.width} × ${item.height} · " else ""
                "📷  Fotoğraf · $dims${formatSize(item.sizeBytes)}"
            } else {
                buildString {
                    append("♫  Orijinal ses · ${formatDuration(item.durationMs)}")
                    if (speed != 1f) append(" · ${formatSpeed(speed)}")
                    val b = abLoop?.second
                    if (abLoop != null && b != null) append(" · 🔁 ${formatDuration(abLoop.first)}–${formatDuration(b)}")
                }
            },
            color = Color.White.copy(alpha = 0.9f),
            fontSize = 13.sp,
            maxLines = 1,
            style = textShadow,
        )
    }
}

/** Klasörün kapak videosu, Instagram hikâye halkası içinde */
@Composable
private fun Avatar(cover: VideoItem, modifier: Modifier) {
    Box(
        modifier
            .clip(CircleShape)
            .background(Brush.sweepGradient(StoryRing))
            .padding(2.dp)
            .clip(CircleShape)
            .background(Color.Black)
            .padding(2.dp)
    ) {
        AsyncImage(
            model = VideoThumb(cover.uri, 128),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(Color(0xFF333333)),
        )
    }
}

@Composable
private fun ActionRail(
    isPhoto: Boolean = false,
    fullscreen: Boolean = false,
    liked: Boolean,
    likes: Int,
    favorite: Boolean,
    views: Int,
    muted: Boolean,
    boosted: Boolean,
    backgroundPlay: Boolean,
    speed: Float,
    autoAdvance: Boolean,
    normalize: Boolean,
    sleepAt: Long,
    pipSupported: Boolean,
    abLoop: Pair<Long, Long?>?,
    onLike: () -> Unit,
    onRemoveOneLike: () -> Unit,
    onClearLikes: () -> Unit,
    onViews: () -> Unit,
    onShare: () -> Unit,
    onMute: () -> Unit,
    onBoost: () -> Unit,
    onBackgroundPlay: () -> Unit,
    onFavorite: () -> Unit,
    onInfo: () -> Unit,
    onPickSpeed: (Float) -> Unit,
    onToggleAutoAdvance: () -> Unit,
    onToggleNormalize: () -> Unit,
    onPickSleep: (minutes: Int) -> Unit,
    onPip: () -> Unit,
    onAbLoop: () -> Unit,
    onSaveFrame: () -> Unit,
    onDelete: (() -> Unit)?,
    modifier: Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var speedMenuOpen by remember { mutableStateOf(false) }
    var sleepMenuOpen by remember { mutableStateOf(false) }
    var likeMenuOpen by remember { mutableStateOf(false) }

    // Beğenince kalp hafifçe zıplar
    val likeScale = remember { Animatable(1f) }
    var wasLiked by remember { mutableStateOf(liked) }
    LaunchedEffect(liked) {
        if (liked && !wasLiked) {
            likeScale.animateTo(1.3f, tween(110))
            likeScale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
        }
        wasLiked = liked
    }

    Column(
        modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(if (fullscreen) 8.dp else 18.dp),
    ) {
        if (onDelete != null) {
            RailButton(icon = R.drawable.ic_delete, tint = Color.White, label = "Sil", compact = fullscreen, onClick = onDelete)
        }
        // Instagram'daki gibi kalbin altında beğeni sayısı; kalp sadece bu açılışta beğenildiyse dolu
        // Basılı tutunca: önceki açılışlarda verilen beğenileri geri alma menüsü
        Box {
            RailButton(
                icon = if (liked) R.drawable.ic_heart_filled else R.drawable.ic_heart_outline,
                tint = if (liked) LikeRed else Color.White,
                label = formatCount(likes),
                description = if (liked) "Beğeniyi geri al" else "Beğen",
                iconScale = likeScale.value,
                compact = fullscreen,
                onClick = onLike,
                onLongClick = if (likes > 0) ({ likeMenuOpen = true }) else null,
            )
            DropdownMenu(expanded = likeMenuOpen, onDismissRequest = { likeMenuOpen = false }) {
                Text(
                    "${formatCount(likes)} beğeni",
                    color = appColors.textSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
                LikeRemovalItems(
                    likes = likes,
                    onRemoveOne = onRemoveOneLike,
                    onClear = onClearLikes,
                    close = { likeMenuOpen = false },
                )
            }
        }
        RailButton(
            icon = R.drawable.ic_eye,
            tint = Color.White,
            label = formatCount(views),
            description = "İzlenme sayısı",
            compact = fullscreen,
            onClick = onViews,
        )
        RailButton(icon = R.drawable.ic_send, tint = Color.White, label = "Paylaş", compact = fullscreen, onClick = onShare)
        if (!isPhoto) {
            RailButton(
                icon = if (muted) R.drawable.ic_volume_off else R.drawable.ic_volume_on,
                tint = if (!muted && boosted) FavoriteYellow else Color.White,
                label = when {
                    muted -> "Sessiz"
                    boosted -> "%200"
                    else -> "Ses"
                },
                compact = fullscreen,
                onClick = onMute,
            )
        }
        // Üç nokta: favoriler, ses yükseltme ve video/fotoğraf bilgisi
        Box {
            RailButton(icon = R.drawable.ic_more_vert, tint = Color.White, label = "", description = "Diğer", compact = fullscreen, onClick = { menuOpen = true })
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                if (MediaActions.favoritesSupported) {
                    DropdownMenuItem(
                        text = { Text(if (favorite) "Favorilerden çıkar" else "Favorilere ekle") },
                        leadingIcon = {
                            Icon(
                                painterResource(if (favorite) R.drawable.ic_star else R.drawable.ic_star_outline),
                                contentDescription = null,
                                tint = if (favorite) FavoriteYellow else appColors.textPrimary,
                            )
                        },
                        onClick = {
                            menuOpen = false
                            onFavorite()
                        },
                    )
                }
                if (likes > 0) {
                    LikeRemovalItems(
                        likes = likes,
                        onRemoveOne = onRemoveOneLike,
                        onClear = onClearLikes,
                        close = { menuOpen = false },
                    )
                }
                if (!isPhoto) {
                    DropdownMenuItem(
                        text = { Text(if (boosted) "Normal ses (%100)" else "Sesi yükselt (%200)") },
                        leadingIcon = {
                            Icon(
                                painterResource(R.drawable.ic_volume_on),
                                contentDescription = null,
                                tint = if (boosted) FavoriteYellow else appColors.textPrimary,
                            )
                        },
                        onClick = {
                            menuOpen = false
                            onBoost()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(if (backgroundPlay) "Arka planda oynat: Açık" else "Arka planda oynat: Kapalı") },
                        leadingIcon = {
                            Icon(
                                painterResource(R.drawable.ic_play),
                                contentDescription = null,
                                tint = if (backgroundPlay) CleanupGreen else appColors.textPrimary,
                            )
                        },
                        onClick = {
                            menuOpen = false
                            onBackgroundPlay()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Oynatma hızı: ${formatSpeed(speed)}") },
                        leadingIcon = {
                            Icon(
                                painterResource(R.drawable.ic_speed),
                                contentDescription = null,
                                tint = if (speed != 1f) FavoriteYellow else appColors.textPrimary,
                            )
                        },
                        onClick = {
                            menuOpen = false
                            speedMenuOpen = true
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(if (normalize) "Ses dengeleme: Açık" else "Ses dengeleme: Kapalı") },
                        leadingIcon = {
                            Icon(
                                painterResource(R.drawable.ic_equalizer),
                                contentDescription = null,
                                tint = if (normalize) CleanupGreen else appColors.textPrimary,
                            )
                        },
                        onClick = {
                            menuOpen = false
                            onToggleNormalize()
                        },
                    )
                    DropdownMenuItem(
                        text = {
                            val left = ((sleepAt - SystemClock.elapsedRealtime()) / 60_000L + 1).coerceAtLeast(1)
                            Text(if (sleepAt > 0L) "Uyku zamanlayıcısı: $left dk kaldı" else "Uyku zamanlayıcısı")
                        },
                        leadingIcon = {
                            Icon(
                                painterResource(R.drawable.ic_timer),
                                contentDescription = null,
                                tint = if (sleepAt > 0L) FavoriteYellow else appColors.textPrimary,
                            )
                        },
                        onClick = {
                            menuOpen = false
                            sleepMenuOpen = true
                        },
                    )
                    if (pipSupported) {
                        DropdownMenuItem(
                            text = { Text("Küçük pencerede oynat") },
                            leadingIcon = {
                                Icon(painterResource(R.drawable.ic_pip), contentDescription = null, tint = appColors.textPrimary)
                            },
                            onClick = {
                                menuOpen = false
                                onPip()
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(if (autoAdvance) "Bitince: Sıradakine geç" else "Bitince: Başa sar") },
                        leadingIcon = {
                            Icon(
                                painterResource(R.drawable.ic_repeat),
                                contentDescription = null,
                                tint = if (autoAdvance) CleanupGreen else appColors.textPrimary,
                            )
                        },
                        onClick = {
                            menuOpen = false
                            onToggleAutoAdvance()
                        },
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                when {
                                    abLoop == null -> "A-B tekrar: A noktasını işaretle"
                                    abLoop.second == null -> "A-B tekrar: B noktasını işaretle"
                                    else -> "A-B tekrarını kapat"
                                }
                            )
                        },
                        leadingIcon = {
                            Icon(
                                painterResource(R.drawable.ic_repeat),
                                contentDescription = null,
                                tint = if (abLoop != null) FavoriteYellow else appColors.textPrimary,
                            )
                        },
                        onClick = {
                            menuOpen = false
                            onAbLoop()
                        },
                    )
                    if (frameSaveSupported) {
                        DropdownMenuItem(
                            text = { Text("Bu kareyi fotoğraf olarak kaydet") },
                            leadingIcon = {
                                Icon(painterResource(R.drawable.ic_camera), contentDescription = null, tint = appColors.textPrimary)
                            },
                            onClick = {
                                menuOpen = false
                                onSaveFrame()
                            },
                        )
                    }
                }
                DropdownMenuItem(
                    text = { Text(if (isPhoto) "Fotoğraf bilgisi" else "Video bilgisi") },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_info), contentDescription = null, tint = appColors.textPrimary) },
                    onClick = {
                        menuOpen = false
                        onInfo()
                    },
                )
            }
            DropdownMenu(expanded = speedMenuOpen, onDismissRequest = { speedMenuOpen = false }) {
                Text(
                    "Oynatma hızı",
                    color = appColors.textSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
                speedOptions.forEach { option ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                if (option == 1f) "${formatSpeed(option)} (normal)" else formatSpeed(option),
                                fontWeight = if (option == speed) FontWeight.Bold else FontWeight.Normal,
                                color = if (option == speed) FavoriteYellow else appColors.textPrimary,
                            )
                        },
                        onClick = {
                            speedMenuOpen = false
                            onPickSpeed(option)
                        },
                    )
                }
            }
            DropdownMenu(expanded = sleepMenuOpen, onDismissRequest = { sleepMenuOpen = false }) {
                Text(
                    "Video şu kadar sonra dursun",
                    color = appColors.textSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
                sleepOptions.forEach { minutes ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                if (minutes == 0) "Kapalı" else "$minutes dakika",
                                color = if (minutes == 0 && sleepAt == 0L) FavoriteYellow else appColors.textPrimary,
                            )
                        },
                        onClick = {
                            sleepMenuOpen = false
                            onPickSleep(minutes)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun VideoInfoDialog(item: VideoItem, views: Int, likes: Int, favorite: Boolean, onDismiss: () -> Unit) {
    val engine = when (DecoderCheck.decision(item.id)) {
        true -> "VLC (yazılımla çözülüyor)"
        false -> "Telefonun çözücüsü"
        null -> "—"
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Tamam") } },
        title = { Text(if (item.isPhoto) "Fotoğraf bilgisi" else "Video bilgisi") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                InfoRow("Ad", item.name)
                InfoRow("Klasör", item.albumPath.ifEmpty { "—" })
                InfoRow("Boyut", formatSize(item.sizeBytes))
                if (item.width > 0) InfoRow("Çözünürlük", "${item.width} × ${item.height}")
                if (!item.isPhoto) InfoRow("Süre", formatDuration(item.durationMs))
                InfoRow("Tarih", formatDate(item.dateMillis))
                InfoRow(if (item.isPhoto) "Görüntülenme" else "İzlenme", "${formatCount(views)} kez")
                InfoRow("Beğeni", if (likes > 0) "${formatCount(likes)} kez" else "—")
                InfoRow("Favori", if (favorite) "Evet" else "Hayır")
                if (!item.isPhoto) InfoRow("Oynatıcı", engine)
            }
        },
    )
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row {
        Text(label, color = appColors.textSecondary, fontSize = 14.sp, modifier = Modifier.width(92.dp))
        Text(value, color = appColors.textPrimary, fontSize = 14.sp)
    }
}

/** Kalbe basılı tutunca ve üç nokta menüsünde: beğenileri geri alma seçenekleri */
@Composable
private fun LikeRemovalItems(likes: Int, onRemoveOne: () -> Unit, onClear: () -> Unit, close: () -> Unit) {
    val icon = @Composable {
        Icon(painterResource(R.drawable.ic_heart_outline), contentDescription = null, tint = appColors.textPrimary)
    }
    DropdownMenuItem(
        text = { Text("Bir beğeniyi geri al (${formatCount(likes)} → ${formatCount(likes - 1)})") },
        leadingIcon = icon,
        onClick = {
            close()
            onRemoveOne()
        },
    )
    // Tek beğeni varsa üstteki seçenek zaten hepsini kaldırır
    if (likes > 1) {
        DropdownMenuItem(
            text = { Text("Tüm beğenileri kaldır") },
            leadingIcon = icon,
            onClick = {
                close()
                onClear()
            },
        )
    }
}

@Composable
private fun RailButton(
    icon: Int,
    tint: Color,
    label: String,
    onClick: () -> Unit,
    description: String = label,
    iconScale: Float = 1f,
    compact: Boolean = false,
    onLongClick: (() -> Unit)? = null,
) {
    val haptic = LocalHapticFeedback.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(64.dp)
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick?.let { longClick ->
                    {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        longClick()
                    }
                },
            ),
    ) {
        Icon(
            painterResource(icon),
            contentDescription = description,
            tint = tint,
            modifier = Modifier
                .size(if (compact) 23.dp else 30.dp)
                .graphicsLayer {
                    scaleX = iconScale
                    scaleY = iconScale
                },
        )
        if (label.isNotEmpty()) {
            Spacer(Modifier.height(if (compact) 2.dp else 4.dp))
            Text(
                label,
                color = Color.White,
                fontSize = if (compact) 11.sp else 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                style = textShadow,
            )
        }
    }
}

/** Çift dokunulan yerde büyüyüp kaybolan kalp */
@Composable
private fun HeartBurst(position: Offset) {
    val scale = remember { Animatable(0f) }
    val alpha = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
        delay(200)
        alpha.animateTo(0f, tween(250))
    }
    val half = with(LocalDensity.current) { 50.dp.toPx() }
    Icon(
        painterResource(R.drawable.ic_heart_filled),
        contentDescription = null,
        tint = LikeRed,
        modifier = Modifier
            .offset { IntOffset((position.x - half).roundToInt(), (position.y - half).roundToInt()) }
            .size(100.dp)
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
                this.alpha = alpha.value
                rotationZ = -10f
            },
    )
}

private val speedOptions = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)

private val sleepOptions = listOf(0, 15, 30, 45, 60, 90)

private const val PIP_ACTION = "com.celtikarda.galerireels.PIP"
private const val PIP_EXTRA = "pip"
private const val PIP_PLAY_PAUSE = 1
private const val PIP_NEXT = 2

/** Küçük penceredeki düğme (oynat / durdur, sonraki); basılınca ReelsScreen'deki alıcıya gider */
@androidx.annotation.RequiresApi(26)
private fun pipAction(context: Context, icon: Int, title: String, code: Int): RemoteAction {
    val intent = Intent(PIP_ACTION).setPackage(context.packageName).putExtra(PIP_EXTRA, code)
    val pending = PendingIntent.getBroadcast(context, code, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    return RemoteAction(SystemIcon.createWithResource(context, icon), title, title, pending)
}

/** Sistemin şu anki parlaklığı (0..1), uygulama kendi parlaklığını henüz ayarlamadıysa */
private fun systemBrightness(context: Context): Float {
    val value = runCatching { Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS) }.getOrDefault(128)
    return (value / 255f).coerceIn(0.01f, 1f)
}

/** Kaydırarak ayarlanan parlaklığı bırakır; ekran sistemin parlaklığına döner */
private fun resetBrightness(activity: Activity) {
    val window = activity.window ?: return
    val attrs = window.attributes
    if (attrs.screenBrightness != WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE) {
        attrs.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        window.attributes = attrs
    }
}

/**
 * Yatay tam ekran hareketleri:
 * - Sol %40'ta yukarı / aşağı kaydırma: parlaklık, sağ %40'ta: ses ([onAdjust] side -1 / 1,
 *   delta: ekran yüksekliğinin kesri, yukarı artı)
 * - İki parmakla açma: ekranı doldur, kapatma: videonun tamamı ([onPinch])
 * Ortada dikey kaydırma eskisi gibi sonraki videoya geçer; yatay kaydırmalar (ilerleme çubuğu,
 * temizlikte silme) dokunulmadan bırakılır.
 */
private fun Modifier.fullscreenGestures(
    onAdjust: (side: Int, delta: Float) -> Unit,
    onAdjustEnd: () -> Unit,
    onPinch: (zoomIn: Boolean) -> Unit,
): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val side = when {
            down.position.x < size.width * 0.4f -> -1
            down.position.x > size.width * 0.6f -> 1
            else -> 0
        }
        // 0: henüz belli değil, 1: parlaklık / ses, 2: iki parmak, -1: başka bir hareket
        var mode = 0
        var moved = Offset.Zero
        var zoom = 1f
        while (true) {
            val event = awaitPointerEvent()
            if (event.changes.none { it.pressed }) break
            val fingers = event.changes.count { it.pressed }
            if (fingers >= 2 && (mode == 0 || mode == 2)) {
                mode = 2
                zoom *= event.calculateZoom()
                event.changes.forEach { it.consume() }
                continue
            }
            if (mode == 2) {
                event.changes.forEach { it.consume() }
                continue
            }
            if (mode == -1) continue
            val change = event.changes.firstOrNull { it.id == down.id } ?: continue
            val delta = change.positionChange()
            if (mode == 0) {
                // Alttaki bir öğe (ör. ilerleme çubuğu) hareketi aldıysa karışma
                if (change.isConsumed) {
                    mode = -1
                    continue
                }
                moved += delta
                if (moved.getDistance() > viewConfiguration.touchSlop) {
                    mode = if (side != 0 && abs(moved.y) > abs(moved.x) * 1.5f) 1 else -1
                }
            }
            if (mode == 1) {
                change.consume()
                if (delta.y != 0f) onAdjust(side, -delta.y / (size.height * 0.8f))
            }
        }
        when (mode) {
            1 -> onAdjustEnd()
            2 -> when {
                zoom > 1.12f -> onPinch(true)
                zoom < 0.89f -> onPinch(false)
            }
        }
    }
}

/** Parlaklık / ses ayarlanırken ekranın ortasında simge, çubuk ve yüzde */
@Composable
private fun AdjustIndicator(brightness: Boolean, level: Float, modifier: Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(Color.Black.copy(alpha = 0.65f))
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painterResource(
                when {
                    brightness -> R.drawable.ic_brightness
                    level <= 0f -> R.drawable.ic_volume_off
                    else -> R.drawable.ic_volume_on
                }
            ),
            contentDescription = if (brightness) "Parlaklık" else "Ses",
            tint = Color.White,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(12.dp))
        Box(
            Modifier
                .width(140.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(50))
                .background(Color.White.copy(alpha = 0.25f))
        ) {
            Box(Modifier.fillMaxWidth(level.coerceIn(0f, 1f)).fillMaxHeight().background(Color.White))
        }
        Spacer(Modifier.width(12.dp))
        Text(
            "%${(level * 100).roundToInt()}",
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(44.dp),
        )
    }
}

/**
 * Ekran kilidi: bütün dokunuşları yutar. Dokununca (ya da geri tuşunda) kilit düğmesi birkaç
 * saniye görünür; ona dokununca kilit açılır.
 */
@Composable
private fun LockOverlay(hintTick: Int, onUnlock: () -> Unit, onShowHint: () -> Unit) {
    var visible by remember { mutableStateOf(true) }
    LaunchedEffect(hintTick) {
        visible = true
        delay(2500)
        visible = false
    }
    val showHint by rememberUpdatedState(onShowHint)
    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false).consume()
                    showHint()
                    while (true) {
                        val event = awaitPointerEvent()
                        event.changes.forEach { it.consume() }
                        if (event.changes.none { it.pressed }) break
                    }
                }
            }
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(16.dp),
        ) {
            Row(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color.Black.copy(alpha = 0.65f))
                    .clickable(onClick = onUnlock)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(painterResource(R.drawable.ic_lock_open), contentDescription = "Kilidi aç", tint = Color.White, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text("Kilidi açmak için dokun", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** "1x", "1,5x", "0,75x" */
private fun formatSpeed(speed: Float): String =
    if (speed % 1f == 0f) "${speed.toInt()}x" else "${speed.toString().trimEnd('0').replace('.', ',')}x"

/** Durdurulmuş videoda bir kare geri / ileri */
@Composable
private fun FrameStepButtons(onStep: (forward: Boolean) -> Unit, modifier: Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        listOf(false to "‹ 1 kare", true to "1 kare ›").forEach { (forward, label) ->
            Text(
                label,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable { onStep(forward) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
}

/** Yatay tam ekranda kenara çift dokununca: "⏪ 10 sn" / "20 sn ⏩", kısa süre görünüp kaybolur */
@Composable
private fun SeekBurst(seconds: Int, forward: Boolean, modifier: Modifier) {
    val alpha = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        delay(450)
        alpha.animateTo(0f, tween(250))
    }
    Text(
        if (forward) "$seconds sn ⏩" else "⏪ $seconds sn",
        color = Color.White,
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        modifier = modifier
            .graphicsLayer { this.alpha = alpha.value }
            .clip(RoundedCornerShape(50))
            .background(Color.Black.copy(alpha = 0.5f))
            .padding(horizontal = 18.dp, vertical = 10.dp),
    )
}

@Composable
private fun PlayIcon(modifier: Modifier) {
    Box(
        modifier
            .size(76.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.4f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(R.drawable.ic_play), contentDescription = "Oynat", tint = Color.White, modifier = Modifier.size(44.dp))
    }
}

@Composable
private fun ErrorBox(message: String, onReport: () -> Unit, modifier: Modifier) {
    Column(
        modifier
            .padding(32.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black.copy(alpha = 0.65f))
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Bu video oynatılamadı", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(message, color = Color.White.copy(alpha = 0.75f), fontSize = 13.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onReport) {
            Text("Sorunu bildir", color = InstaBlue, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun TopBar(
    title: String,
    shuffled: Boolean,
    showShuffle: Boolean,
    fullscreen: Boolean,
    onBack: () -> Unit,
    onToggleShuffle: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onLock: () -> Unit,
    onHideUi: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.45f), Color.Transparent)))
            .statusBarsPadding()
            .padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Geri", tint = Color.White)
        }
        Text(
            title,
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = textShadow,
            modifier = Modifier.weight(1f),
        )
        if (showShuffle) {
            IconButton(onClick = onToggleShuffle) {
                Icon(
                    painterResource(R.drawable.ic_shuffle),
                    contentDescription = if (shuffled) "Karışık sıra" else "Yeniden eskiye",
                    tint = if (shuffled) Color.White else Color.White.copy(alpha = 0.4f),
                )
            }
        }
        if (fullscreen) {
            IconButton(onClick = onHideUi) {
                Icon(painterResource(R.drawable.ic_visibility_off), contentDescription = "Sade görünüm (düğmeleri gizle)", tint = Color.White)
            }
            IconButton(onClick = onLock) {
                Icon(painterResource(R.drawable.ic_lock), contentDescription = "Ekranı kilitle", tint = Color.White)
            }
        }
        IconButton(onClick = onToggleFullscreen) {
            Icon(
                painterResource(if (fullscreen) R.drawable.ic_fullscreen_exit else R.drawable.ic_fullscreen),
                contentDescription = if (fullscreen) "Tam ekrandan çık" else "Tam ekran",
                tint = Color.White,
            )
        }
    }
}

@Composable
/** Alttaki ilerleme çubuğu; parmakla sürükleyince o andaki kare görünür, bırakınca video oraya sarılır */
private fun VideoProgress(progress: () -> Float, durationMs: Long, uri: android.net.Uri, onSeek: (Float) -> Unit, modifier: Modifier) {
    val read by rememberUpdatedState(progress)
    val seek by rememberUpdatedState(onSeek)
    var value by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            if (!dragging) value = read().coerceIn(0f, 1f)
            delay(100)
        }
    }
    val shown = if (dragging) dragValue else value

    val context = LocalContext.current
    val preview = remember(uri) { SeekPreview(context, uri) }
    var previewFrame by remember(uri) { mutableStateOf<android.graphics.Bitmap?>(null) }
    DisposableEffect(preview) { onDispose { preview.release() } }
    LaunchedEffect(dragging, preview) {
        if (!dragging || durationMs <= 0) return@LaunchedEffect
        // Yarım saniyelik adımlarla. Kare çıkarılırken parmak ilerlerse aradaki konumlar atlanır,
        // sıradaki kare en son konumdan alınır (iptal edilseydi parmak hareket ettikçe hiç kare yetişmiyordu)
        androidx.compose.runtime.snapshotFlow { (durationMs * dragValue).toLong() / 500 }
            .distinctUntilChanged()
            .conflate()
            .collect { step -> preview.frameAt(step * 500)?.let { previewFrame = it } }
    }
    LaunchedEffect(dragging) { if (!dragging) previewFrame = null }

    // Çizgi ince ama dokunma alanı geniş
    Box(
        modifier
            .fillMaxWidth()
            .height(if (dragging) 220.dp else 24.dp)
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        dragging = true
                        dragValue = (offset.x / size.width).coerceIn(0f, 1f)
                    },
                    onDragEnd = {
                        seek(dragValue)
                        value = dragValue
                        dragging = false
                    },
                    onDragCancel = { dragging = false },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        dragValue = (change.position.x / size.width).coerceIn(0f, 1f)
                    },
                )
            },
        contentAlignment = Alignment.BottomStart,
    ) {
        if (dragging && durationMs > 0) {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                previewFrame?.let { frame ->
                    Image(
                        bitmap = frame.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .height(120.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black),
                    )
                    Spacer(Modifier.height(8.dp))
                }
                Text(
                    "${formatDuration((durationMs * dragValue).toLong())} / ${formatDuration(durationMs)}",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    style = textShadow,
                )
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(if (dragging) 6.dp else 2.dp)
                .background(Color.White.copy(alpha = 0.25f))
        ) {
            Box(
                Modifier
                    .fillMaxWidth(shown)
                    .fillMaxHeight()
                    .background(Color.White)
            )
        }
    }
}

private fun shareVideo(context: Context, item: VideoItem) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = item.mimeType ?: if (item.isPhoto) "image/*" else "video/*"
        putExtra(Intent.EXTRA_STREAM, item.uri)
        clipData = ClipData.newRawUri(null, item.uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, "Paylaş"))
}
