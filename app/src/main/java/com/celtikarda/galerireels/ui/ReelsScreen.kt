package com.celtikarda.galerireels.ui

import android.content.ClipData
import android.content.Context
import android.content.Intent
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
import androidx.media3.exoplayer.ExoPlayer
import coil3.compose.AsyncImage
import com.celtikarda.galerireels.Album
import com.celtikarda.galerireels.GalleryViewModel
import com.celtikarda.galerireels.R
import com.celtikarda.galerireels.ReelsPlayback
import com.celtikarda.galerireels.VideoItem
import com.celtikarda.galerireels.VideoThumb
import com.celtikarda.galerireels.VlcEngine
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val textShadow = TextStyle(shadow = Shadow(Color.Black.copy(alpha = 0.5f), blurRadius = 6f))

@Composable
fun ReelsScreen(vm: GalleryViewModel, album: Album, onBack: () -> Unit) {
    val items = vm.items
    val pagerState = rememberPagerState { vm.items.size }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val playback = remember { ReelsPlayback(context, scope) }
    DisposableEffect(playback) { onDispose { playback.release() } }

    var userPaused by remember { mutableStateOf(false) }
    var muted by rememberSaveable { mutableStateOf(false) }

    // "Karışık sıra" / "Yeniden eskiye" bilgi baloncuğu
    var chipText by remember { mutableStateOf("") }
    var chipTick by remember { mutableIntStateOf(0) }
    var chipVisible by remember { mutableStateOf(false) }

    val page = pagerState.settledPage
    val current = items.getOrNull(page)
    val previous = items.getOrNull(page - 1)
    val next = items.getOrNull(page + 1)

    // Kaydırma bitip sayfa oturunca o video oynar; komşuları (önce sıradaki) arkada hazırlanır
    LaunchedEffect(current?.id, previous?.id, next?.id) {
        playback.show(current, listOfNotNull(next, previous))
    }
    LaunchedEffect(current?.id) { userPaused = false }
    LaunchedEffect(muted) { playback.setMuted(muted) }

    LaunchedEffect(chipTick) {
        if (chipTick == 0) return@LaunchedEffect
        chipVisible = true
        delay(1400)
        chipVisible = false
    }

    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { playback.onHostPause() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { playback.onHostResume() }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        VerticalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 1,
            key = { items[it].id },
        ) { index ->
            val item = items[index]
            val active = index == pagerState.settledPage

            ReelPage(
                item = item,
                active = active,
                player = playback.players[item.id],
                vlc = playback.vlc?.takeIf { playback.vlcItemId == item.id },
                aspectRatio = item.aspectRatio.takeIf { it > 0f } ?: playback.aspectRatios[item.id] ?: 0f,
                // Hazırda bekleyen komşu, kaydırırken küçük resmiyle görünür: bazı videoların
                // ilk karesi simsiyah (kararmadan açılan kurgular), küçük resim daha iyi duruyor
                firstFrameShown = active && item.id in playback.renderedIds,
                error = playback.error?.takeIf { it.first == item.id }?.second,
                paused = active && userPaused,
                liked = item.id in vm.likedIds,
                muted = muted,
                albumName = vm.albumNameOf(item),
                avatar = vm.coverOf(item.albumId) ?: item,
                onTogglePause = {
                    if (active) {
                        userPaused = !userPaused
                        playback.setPaused(userPaused)
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
    /** Bu videoyu hazırlayan oynatıcı; yoksa sadece küçük resim görünür */
    player: ExoPlayer?,
    /** Bu video VLC ile oynatılıyorsa motor, değilse null */
    vlc: VlcEngine?,
    /** Videonun en / boy oranı; bilinmiyorsa 0 */
    aspectRatio: Float,
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
        VideoSurface(item, player, vlc, aspectRatio, firstFrameShown)

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
                    when {
                        vlc != null -> vlc.progress
                        player != null -> player.duration.let { d -> if (d > 0) player.currentPosition.toFloat() / d else 0f }
                        else -> 0f
                    }
                },
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding(),
            )
        }
    }
}

/**
 * Video alanı en baştan videonun oranında açılır (boyutu galeri biliyor); oynatıcı videonun
 * boyutunu söyleyene kadar tam ekran kalıp sonra küçülmez.
 */
@Composable
private fun VideoSurface(item: VideoItem, player: ExoPlayer?, vlc: VlcEngine?, aspectRatio: Float, firstFrameShown: Boolean) {
    // Galeri boyutu bilmiyorsa ve video VLC'deyse oranı VLC'den öğreniriz
    var vlcAspect by remember(item.id) { mutableFloatStateOf(0f) }
    val ratio = if (aspectRatio > 0f) aspectRatio else vlcAspect

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(if (ratio > 0f) Modifier.aspectRatio(ratio) else Modifier.fillMaxSize()) {
            when {
                vlc != null -> VlcSurface(vlc, onAspectRatio = { vlcAspect = it })
                // Oynatıcı başka videoya geçerse yüzey de yenilenir
                player != null -> key(player) { ExoSurface(player) }
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
private fun ExoSurface(player: ExoPlayer) {
    AndroidView(
        factory = { ctx ->
            SurfaceView(ctx).apply {
                keepScreenOn = true
                player.setVideoSurfaceView(this)
            }
        },
        onRelease = { player.clearVideoSurfaceView(it) },
        modifier = Modifier.fillMaxSize(),
    )
}

/** VLC görüntüyü yüzeyin tamamına gerer; yüzeyi videonun oranında tutmak çağıranın işi. */
@Composable
private fun VlcSurface(vlc: VlcEngine, onAspectRatio: (Float) -> Unit) {
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
        onRelease = { vlc.detach() },
        modifier = Modifier.fillMaxSize(),
    )
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

private fun shareVideo(context: Context, item: VideoItem) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = item.mimeType ?: "video/*"
        putExtra(Intent.EXTRA_STREAM, item.uri)
        clipData = ClipData.newRawUri(null, item.uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, "Paylaş"))
}
