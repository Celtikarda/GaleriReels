package com.celtikarda.galerireels.ui

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.view.SurfaceHolder
import android.view.SurfaceView
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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.doOnAttach
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil
import androidx.media3.exoplayer.util.EventLogger
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import com.celtikarda.galerireels.Album
import com.celtikarda.galerireels.GalleryViewModel
import com.celtikarda.galerireels.VlcEngine
import com.celtikarda.galerireels.R
import com.celtikarda.galerireels.VideoItem
import com.celtikarda.galerireels.VideoThumb
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val textShadow = TextStyle(shadow = Shadow(Color.Black.copy(alpha = 0.5f), blurRadius = 6f))

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun ReelsScreen(vm: GalleryViewModel, album: Album, onBack: () -> Unit) {
    val items = vm.items
    val pagerState = rememberPagerState { vm.items.size }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // Tek bir oynatıcı, sadece ekrandaki videoya bağlanır (her sayfaya ayrı oynatıcı belleği doldurur)
    val player = remember { createPlayer(context) }
    DisposableEffect(player) { onDispose { player.release() } }

    // VLC motoru sadece gerektiğinde açılır (açılışı ~yarım saniye sürer)
    val vlcHolder = remember { lazy { VlcEngine(context) } }
    DisposableEffect(vlcHolder) { onDispose { if (vlcHolder.isInitialized()) vlcHolder.value.release() } }
    // ExoPlayer'ın oynatamadığı ya da takılarak oynattığı videolar; bunlar VLC ile oynatılır
    var vlcIds by remember { mutableStateOf(emptySet<Long>()) }

    // İlk karesi ekrana çizilmiş videonun id'si; o zamana kadar küçük resim üstte kalır
    var renderedId by remember { mutableStateOf<String?>(null) }
    // Oynatılamayan video: mediaId → kullanıcıya gösterilecek açıklama
    var error by remember { mutableStateOf<Pair<String, String>?>(null) }
    var userPaused by remember { mutableStateOf(false) }
    var muted by rememberSaveable { mutableStateOf(false) }

    // "Karışık sıra" / "Yeniden eskiye" bilgi baloncuğu
    var chipText by remember { mutableStateOf("") }
    var chipTick by remember { mutableIntStateOf(0) }
    var chipVisible by remember { mutableStateOf(false) }

    val current = items.getOrNull(pagerState.settledPage)
    val currentItem by rememberUpdatedState(current)
    val useVlc = current != null && current.id in vlcIds

    DisposableEffect(player) {
        var decoderName: String? = null
        var videoFormat: Format? = null

        fun switchToVlc() {
            val id = currentItem?.id ?: return
            if (id !in vlcIds) vlcIds = vlcIds + id
        }

        // Yazılım çözücü, videoyu kapasitesinin üstünde bulduysa en baştan VLC'ye geç
        fun checkOverload() {
            val name = decoderName ?: return
            val format = videoFormat ?: return
            if (isOverloadedSoftwareDecoder(name, format)) switchToVlc()
        }

        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() {
                renderedId = player.currentMediaItem?.mediaId
            }

            override fun onPlayerError(e: PlaybackException) {
                val id = currentItem?.id
                if (id != null && id !in vlcIds) switchToVlc()
                else error = (player.currentMediaItem?.mediaId ?: "") to describe(e)
            }
        }
        val analytics = object : AnalyticsListener {
            override fun onVideoDecoderInitialized(
                eventTime: AnalyticsListener.EventTime,
                name: String,
                initializedTimestampMs: Long,
                initializationDurationMs: Long,
            ) {
                decoderName = name
                checkOverload()
            }

            override fun onVideoInputFormatChanged(
                eventTime: AnalyticsListener.EventTime,
                format: Format,
                decoderReuseEvaluation: DecoderReuseEvaluation?,
            ) {
                videoFormat = format
                checkOverload()
            }

            // Yazılım çözücü 4 saniyeden kısa sürede 50 kare atladıysa video takılıyor demektir
            override fun onDroppedVideoFrames(eventTime: AnalyticsListener.EventTime, droppedFrames: Int, elapsedMs: Long) {
                val name = decoderName ?: return
                if (droppedFrames >= 50 && elapsedMs < 4000 && isSoftwareDecoder(name)) switchToVlc()
            }
        }
        player.addListener(listener)
        player.addAnalyticsListener(analytics)
        onDispose {
            player.removeListener(listener)
            player.removeAnalyticsListener(analytics)
        }
    }

    // Kaydırma bitip sayfa oturunca o videoyu oynat (gerekiyorsa VLC ile)
    LaunchedEffect(current?.id, useVlc) {
        userPaused = false
        renderedId = null
        error = null
        if (current == null) {
            player.stop()
            player.clearMediaItems()
            if (vlcHolder.isInitialized()) vlcHolder.value.stop()
        } else if (useVlc) {
            player.stop()
            player.clearMediaItems()
            val mediaId = current.id.toString()
            vlcHolder.value.play(
                current.uri,
                onFirstFrame = { renderedId = mediaId },
                onError = { error = mediaId to "Dosya bozuk ya da gerçek bir video değil." },
            )
        } else {
            if (vlcHolder.isInitialized()) vlcHolder.value.stop()
            player.setMediaItem(
                MediaItem.Builder().setUri(current.uri).setMediaId(current.id.toString()).build()
            )
            player.prepare()
            player.playWhenReady = true
        }
    }

    LaunchedEffect(muted) {
        player.volume = if (muted) 0f else 1f
        if (vlcHolder.isInitialized()) vlcHolder.value.setMuted(muted)
    }

    LaunchedEffect(chipTick) {
        if (chipTick == 0) return@LaunchedEffect
        chipVisible = true
        delay(1400)
        chipVisible = false
    }

    fun setPlaying(playing: Boolean) {
        if (useVlc) {
            if (playing) vlcHolder.value.resume() else vlcHolder.value.pause()
        } else {
            player.playWhenReady = playing
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) {
        player.playWhenReady = false
        if (vlcHolder.isInitialized()) vlcHolder.value.pause()
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (current != null && !userPaused && error == null) setPlaying(true)
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        VerticalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 1,
            key = { items[it].id },
        ) { page ->
            val item = items[page]
            val active = page == pagerState.settledPage

            ReelPage(
                item = item,
                active = active,
                player = player,
                vlc = if (active && useVlc) vlcHolder.value else null,
                firstFrameShown = renderedId == item.id.toString(),
                error = error?.takeIf { it.first == item.id.toString() }?.second,
                paused = active && userPaused,
                liked = item.id in vm.likedIds,
                muted = muted,
                albumName = vm.albumNameOf(item),
                avatar = vm.coverOf(item.albumId) ?: item,
                onTogglePause = {
                    if (active) {
                        userPaused = !userPaused
                        setPlaying(!userPaused)
                    }
                },
                onDoubleTapLike = { vm.like(item.id) },
                onToggleLike = { vm.toggleLike(item.id) },
                onShare = { shareVideo(context, item) },
                onToggleMute = { muted = !muted },
            )
        }

        TopBar(
            title = album.name,
            shuffled = vm.shuffled,
            onBack = onBack,
            onToggleShuffle = {
                val index = vm.toggleShuffle(current?.id)
                chipText = if (vm.shuffled) "Karışık sıra" else "Yeniden eskiye"
                chipTick++
                scope.launch { pagerState.scrollToPage(index) }
            },
        )

        AnimatedVisibility(
            visible = chipVisible,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 64.dp),
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Text(
                chipText,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun ReelPage(
    item: VideoItem,
    active: Boolean,
    player: ExoPlayer,
    /** Bu video VLC ile oynatılıyorsa motor, değilse null */
    vlc: VlcEngine?,
    firstFrameShown: Boolean,
    error: String?,
    paused: Boolean,
    liked: Boolean,
    muted: Boolean,
    albumName: String,
    avatar: VideoItem,
    onTogglePause: () -> Unit,
    onDoubleTapLike: () -> Unit,
    onToggleLike: () -> Unit,
    onShare: () -> Unit,
    onToggleMute: () -> Unit,
) {
    var burstId by remember { mutableIntStateOf(0) }
    var burstAt by remember { mutableStateOf(Offset.Zero) }
    val tap by rememberUpdatedState(onTogglePause)
    val doubleTap by rememberUpdatedState(onDoubleTapLike)

    Box(Modifier.fillMaxSize()) {
        VideoSurface(item, active, player, vlc, firstFrameShown)

        // Tek dokunma: durdur/oynat · Çift dokunma: beğen (kalp animasyonu)
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { tap() },
                        onDoubleTap = { offset ->
                            burstAt = offset
                            burstId++
                            doubleTap()
                        },
                    )
                }
        )

        if (paused) PlayIcon(Modifier.align(Alignment.Center))
        if (error != null) ErrorBox(error, Modifier.align(Alignment.Center))
        if (burstId > 0) key(burstId) { HeartBurst(burstAt) }

        BottomInfo(item, albumName, avatar, Modifier.align(Alignment.BottomStart))

        ActionRail(
            liked = liked,
            muted = muted,
            onLike = onToggleLike,
            onShare = onShare,
            onMute = onToggleMute,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 8.dp, bottom = 24.dp),
        )

        if (active) {
            VideoProgress(
                progress = {
                    if (vlc != null) vlc.progress
                    else player.duration.let { d -> if (d > 0) player.currentPosition.toFloat() / d else 0f }
                },
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding(),
            )
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun VideoSurface(item: VideoItem, active: Boolean, player: ExoPlayer, vlc: VlcEngine?, firstFrameShown: Boolean) {
    Box(Modifier.fillMaxSize()) {
        if (active && vlc != null) {
            // VLC görüntüyü yüzeyin tamamına gerdiği için yüzeyi videonun oranında tutuyoruz
            var aspect by remember(item.id) { mutableFloatStateOf(0f) }
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
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
                                    vlc.attach(this@apply, width, height) { aspect = it }
                                }

                                override fun surfaceDestroyed(holder: SurfaceHolder) = vlc.detach()
                            })
                        }
                    },
                    onRelease = { vlc.detach() },
                    modifier = if (aspect > 0f) Modifier.aspectRatio(aspect) else Modifier.fillMaxSize(),
                )
            }
        } else if (active) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                        keepScreenOn = true
                    }
                },
                update = { it.player = player },
                onRelease = { it.player = null },
                modifier = Modifier.fillMaxSize(),
            )
        }

        // İlk kare gelene kadar küçük resim videonun üstünde durur, sonra yavaşça kaybolur
        val thumbAlpha by animateFloatAsState(
            targetValue = if (active && firstFrameShown) 0f else 1f,
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

@Composable
private fun BottomInfo(item: VideoItem, albumName: String, avatar: VideoItem, modifier: Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f))))
            .navigationBarsPadding()
            .padding(start = 14.dp, end = 76.dp, top = 80.dp, bottom = 22.dp)
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
        Text(formatDate(item.dateMillis), color = Color.White, fontSize = 14.sp, style = textShadow)
        Spacer(Modifier.height(8.dp))
        Text(
            "♫  Orijinal ses · ${formatDuration(item.durationMs)}",
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
    liked: Boolean,
    muted: Boolean,
    onLike: () -> Unit,
    onShare: () -> Unit,
    onMute: () -> Unit,
    modifier: Modifier,
) {
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
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        RailButton(
            icon = if (liked) R.drawable.ic_heart_filled else R.drawable.ic_heart_outline,
            tint = if (liked) LikeRed else Color.White,
            label = "Beğen",
            iconScale = likeScale.value,
            onClick = onLike,
        )
        RailButton(icon = R.drawable.ic_send, tint = Color.White, label = "Paylaş", onClick = onShare)
        RailButton(
            icon = if (muted) R.drawable.ic_volume_off else R.drawable.ic_volume_on,
            tint = Color.White,
            label = if (muted) "Sessiz" else "Ses",
            onClick = onMute,
        )
    }
}

@Composable
private fun RailButton(icon: Int, tint: Color, label: String, onClick: () -> Unit, iconScale: Float = 1f) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(64.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
    ) {
        Icon(
            painterResource(icon),
            contentDescription = label,
            tint = tint,
            modifier = Modifier
                .size(30.dp)
                .graphicsLayer {
                    scaleX = iconScale
                    scaleY = iconScale
                },
        )
        Spacer(Modifier.height(4.dp))
        Text(label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium, style = textShadow)
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
private fun ErrorBox(message: String, modifier: Modifier) {
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
    }
}

@Composable
private fun TopBar(title: String, shuffled: Boolean, onBack: () -> Unit, onToggleShuffle: () -> Unit) {
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
        IconButton(onClick = onToggleShuffle) {
            Icon(
                painterResource(R.drawable.ic_shuffle),
                contentDescription = if (shuffled) "Karışık sıra" else "Yeniden eskiye",
                tint = if (shuffled) Color.White else Color.White.copy(alpha = 0.4f),
            )
        }
    }
}

@Composable
private fun VideoProgress(progress: () -> Float, modifier: Modifier) {
    val read by rememberUpdatedState(progress)
    var value by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            value = read().coerceIn(0f, 1f)
            delay(100)
        }
    }
    Box(
        modifier
            .fillMaxWidth()
            .height(2.dp)
            .background(Color.White.copy(alpha = 0.25f))
    ) {
        Box(
            Modifier
                .fillMaxWidth(value)
                .fillMaxHeight()
                .background(Color.White)
        )
    }
}

private fun describe(e: PlaybackException): String = when (e.errorCode) {
    PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
    PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED -> "Dosya bozuk ya da gerçek bir video değil."
    PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
    PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
    PlaybackException.ERROR_CODE_DECODING_FAILED,
    PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
    PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED -> "Bu telefon bu video formatını çözemiyor."
    else -> e.errorCodeName
}

private fun shareVideo(context: Context, item: VideoItem) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = item.mimeType ?: "video/*"
        putExtra(Intent.EXTRA_STREAM, item.uri)
        clipData = ClipData.newRawUri(null, item.uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, "Paylaş"))
}

private fun isSoftwareDecoder(name: String): Boolean =
    name.startsWith("c2.android.") || name.startsWith("OMX.google.")

/**
 * ExoPlayer donanım çözücüyü açamayınca (ör. 1440x1600 donanımın 1080p sınırını aşıyor)
 * Android'in yazılım çözücüsüne düşer; o da videoyu kapasitesinin üstünde bulursa takılır.
 */
@androidx.annotation.OptIn(UnstableApi::class)
private fun isOverloadedSoftwareDecoder(name: String, format: Format): Boolean {
    if (!isSoftwareDecoder(name)) return false
    val mime = format.sampleMimeType ?: return false
    if (format.width <= 0 || format.height <= 0) return false
    val info = runCatching { MediaCodecUtil.getDecoderInfos(mime, false, false) }.getOrNull()
        ?.firstOrNull { it.name == name } ?: return false
    val frameRate = if (format.frameRate > 0) format.frameRate.toDouble() else 30.0
    return !info.isVideoSizeAndRateSupported(format.width, format.height, frameRate)
}

@androidx.annotation.OptIn(UnstableApi::class)
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
