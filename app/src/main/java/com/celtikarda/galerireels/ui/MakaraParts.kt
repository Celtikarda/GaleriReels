package com.celtikarda.galerireels.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.celtikarda.galerireels.Album
import com.celtikarda.galerireels.MediaMode
import com.celtikarda.galerireels.MonthReel
import com.celtikarda.galerireels.MosaicTile
import com.celtikarda.galerireels.R
import com.celtikarda.galerireels.Strings
import com.celtikarda.galerireels.VideoItem
import com.celtikarda.galerireels.VideoThumb
import com.celtikarda.galerireels.frameNumber
import com.celtikarda.galerireels.mosaicRows
import com.celtikarda.galerireels.packMosaic
import com.celtikarda.galerireels.plural
import com.celtikarda.galerireels.str
import java.time.format.DateTimeFormatter

/*
 * Makara ana ekranının parçaları (DESIGN.md Components). Film delikleri yalnızca
 * Kaldığın yer kartında ve ay makarası şeridinde kullanılır.
 */

private val Gap = 8.dp

/** Türkçe büyük harf ("i" → "İ") seçili dile göre */
internal fun String.upper(): String = uppercase(Strings.locale)

/** Bölüm başlığı: Big Shoulders, büyük harf; sağda isteğe bağlı kehribar eylem */
@Composable
internal fun SectionTitle(text: String, action: String? = null, onAction: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text.upper(), style = MakaraType.Display, color = Makara.Cream, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) {
            Box(
                Modifier
                    .heightIn(min = 44.dp)
                    .clip(MakaraShape.Small)
                    .clickable(role = Role.Button, onClick = onAction)
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(action, style = MakaraType.Label, color = Makara.Amber)
            }
        }
    }
}

/** Film delikleri sırası (12×8 dp, zemin renginde) */
@Composable
internal fun Perforations(count: Int, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 5.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        repeat(count) {
            Box(Modifier.size(width = 12.dp, height = 8.dp).clip(MakaraShape.Frame).background(Makara.Black))
        }
    }
}

/** "Kaldığın yer" kartı: delikli kare, kehribar ilerleme, ad + Devam et */
@Composable
internal fun ResumeCard(video: VideoItem, positionMs: Long, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val progress = if (video.durationMs > 0) (positionMs.toFloat() / video.durationMs).coerceIn(0f, 1f) else 0f
    Column(modifier.fillMaxWidth()) {
        Text(
            str(R.string.resume_label, frameNumber((positionMs / 1000).toInt())),
            style = MakaraType.Overline,
            color = Makara.Muted,
        )
        Spacer(Modifier.height(Gap))
        Column(
            Modifier
                .fillMaxWidth()
                .clip(MakaraShape.Default)
                .background(Makara.Surface)
                .clickable(onClick = onClick)
        ) {
            Perforations(10)
            Box(
                Modifier
                    .padding(horizontal = 10.dp)
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(MakaraShape.Frame)
                    .background(Makara.SurfaceHigh)
            ) {
                AsyncImage(
                    model = VideoThumb(video.uri, 720),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(4.dp).background(Makara.Black.copy(alpha = 0.55f))) {
                    Box(Modifier.fillMaxWidth(progress).height(4.dp).background(Makara.Amber))
                }
            }
            Perforations(10)
            Row(
                Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(video.name, style = MakaraType.Title, color = Makara.Cream, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${formatDuration(positionMs)} / ${formatDuration(video.durationMs)}",
                        style = MakaraType.BodySmall,
                        color = Makara.Muted,
                    )
                }
                Spacer(Modifier.width(Gap))
                Box(
                    Modifier
                        .height(44.dp)
                        .clip(CircleShape)
                        .background(Makara.Amber)
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(str(R.string.resume_action), style = MakaraType.Label, color = Makara.OnAmber)
                }
            }
        }
    }
}

/** Kehribar "Bu ay" kutusu: izleme süresi + video sayısı; dokununca Aylık özet */
@Composable
internal fun ThisMonthTile(watchTimeMs: Long, videoCount: Int, mode: MediaMode, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(MakaraShape.Default)
            .background(Makara.Amber)
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        Text(str(R.string.this_month), style = MakaraType.Overline, color = Makara.OnAmber)
        Spacer(Modifier.height(4.dp))
        Text(formatWatchTime(watchTimeMs).upper(), style = MakaraType.Display, color = Makara.OnAmber, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(mode.count(videoCount), style = MakaraType.BodySmall, color = Makara.OnAmber)
    }
}

/** Temizlik kutusu: boşaltılabilir yer; dokununca temizlik seçenekleri */
@Composable
internal fun CleanupTile(bytes: Long, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(MakaraShape.Default)
            .background(Makara.Surface)
            .border(1.dp, Makara.Outline, MakaraShape.Default)
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        Text(str(R.string.cleanup).upper(), style = MakaraType.Overline, color = Makara.Muted, maxLines = 1)
        Spacer(Modifier.height(4.dp))
        // Boyut ve "boşaltılabilir" ayrı satırlarda, tek satır: büyük yazı ayarında (1.6) kelime
        // ortadan bölünüyordu; sığmazsa ikisi de küçülür
        Text(
            formatSize(bytes).upper(),
            style = MakaraType.Counter,
            color = Makara.Cream,
            maxLines = 1,
            softWrap = false,
            autoSize = TextAutoSize.StepBased(minFontSize = 12.sp, maxFontSize = MakaraType.Counter.fontSize),
        )
        Text(
            str(R.string.freeable_label),
            style = MakaraType.BodySmall,
            color = Makara.Muted,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            autoSize = TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = MakaraType.BodySmall.fontSize),
        )
    }
}

/**
 * Ay düğmeleri: 3 harfli ay + kare sayısı. [todayCount] > 0 ise en başta "BUGÜN" düğmesi olur
 * ve [selected] 0 onu gösterir; ay makaraları bir kayar.
 */
@Composable
internal fun MonthChips(reels: List<MonthReel>, selected: Int, onSelect: (Int) -> Unit, todayCount: Int = 0, modifier: Modifier = Modifier) {
    val shortMonth = DateTimeFormatter.ofPattern("MMM", Strings.locale)
    val chips: List<Pair<String, Int>> = buildList {
        if (todayCount > 0) add(str(R.string.today_chip) to todayCount)
        reels.forEach { add(shortMonth.format(it.month).trimEnd('.').take(3).upper() to it.items.size) }
    }
    LazyRow(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Gap)) {
        itemsIndexed(chips) { i, (label, count) ->
            val active = i == selected
            Column(
                Modifier
                    .heightIn(min = 56.dp)
                    .widthIn(min = 64.dp)
                    .clip(MakaraShape.Default)
                    .background(if (active) Makara.Amber else Makara.Black)
                    .border(1.dp, if (active) Makara.Amber else Makara.Outline, MakaraShape.Default)
                    .clickable(role = Role.Tab) { onSelect(i) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(label, style = MakaraType.Counter, color = if (active) Makara.OnAmber else Makara.Cream, maxLines = 1)
                Text(
                    plural(R.plurals.n_frames, count, formatCount(count)),
                    style = MakaraType.CounterSmall,
                    color = if (active) Makara.OnAmber else Makara.Muted,
                    maxLines = 1,
                )
            }
        }
    }
}

/** Delikli film şeridi: başlık + Oynat, kareler (sol üstte numara, sağ altta süre) */
@Composable
internal fun FilmStrip(title: String, items: List<VideoItem>, onPlay: () -> Unit, onItem: (VideoItem) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        SectionTitle(title, action = str(R.string.play), onAction = onPlay)
        Spacer(Modifier.height(Gap))
        Column(
            Modifier
                .fillMaxWidth()
                .clip(MakaraShape.Default)
                .background(Makara.Surface)
        ) {
            Perforations(12)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(horizontal = 6.dp)) {
                itemsIndexed(items.take(MAX_STRIP), key = { _, v -> v.id }) { i, video ->
                    StripFrame(video, i + 1, onClick = { onItem(video) })
                }
            }
            Perforations(12)
        }
    }
}

private const val MAX_STRIP = 30

@Composable
private fun StripFrame(video: VideoItem, number: Int, onClick: () -> Unit) {
    Box(
        Modifier
            .width(88.dp)
            .aspectRatio(9f / 16f)
            .clip(MakaraShape.Frame)
            .background(Makara.SurfaceHigh)
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = VideoThumb(video.uri, 320),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        FrameLabel(frameNumber(number), Makara.Amber, Modifier.align(Alignment.TopStart))
        if (!video.isPhoto && video.durationMs > 0) {
            FrameLabel(formatDuration(video.durationMs), Makara.Cream, Modifier.align(Alignment.BottomEnd))
        }
    }
}

/** Kare üstündeki küçük sayı: perde zeminli, okunur kalsın */
@Composable
private fun FrameLabel(text: String, color: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MakaraType.CounterSmall,
        color = color,
        modifier = modifier
            .padding(4.dp)
            .clip(MakaraShape.Frame)
            .background(Makara.Black.copy(alpha = 0.55f))
            .padding(horizontal = 4.dp, vertical = 1.dp),
    )
}

/** Son izlediklerin sırası için küçük kare (delik yok) */
@Composable
internal fun SmallFrame(video: VideoItem, views: Int, onClick: () -> Unit) {
    Box(
        Modifier
            .width(72.dp)
            .aspectRatio(9f / 16f)
            .clip(MakaraShape.Tile)
            .background(Makara.Surface)
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = VideoThumb(video.uri, 240),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        if (views > 0) FrameLabel("×${formatCount(views)}", Makara.Cream, Modifier.align(Alignment.BottomStart))
    }
}

/** "En çok sarılanlar": 3 sütunlu yoğun ızgara, çok izlenen büyür */
@Composable
internal fun MostWatchedMosaic(tiles: List<MosaicTile>, onItem: (VideoItem) -> Unit, modifier: Modifier = Modifier) {
    val sizes = tiles.map { it.size }
    val cells = packMosaic(sizes)
    val rows = mosaicRows(sizes, cells)
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val cell = (maxWidth - Gap * 2) / 3
        Box(Modifier.fillMaxWidth().height(cell * rows + Gap * (rows - 1).coerceAtLeast(0))) {
            tiles.forEachIndexed { i, tile ->
                val c = cells[i]
                Box(
                    Modifier
                        .offset(x = (cell + Gap) * c.col, y = (cell + Gap) * c.row)
                        .size(width = cell * tile.size.cols + Gap * (tile.size.cols - 1), height = cell * tile.size.rows + Gap * (tile.size.rows - 1))
                        .clip(MakaraShape.Tile)
                        .background(Makara.Surface)
                        .clickable { onItem(tile.video) }
                ) {
                    AsyncImage(
                        model = VideoThumb(tile.video.uri, if (tile.size.cols > 1 || tile.size.rows > 1) 480 else 320),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    Text(
                        "×${formatCount(tile.views)}",
                        style = if (tile.loud) MakaraType.Counter else MakaraType.CounterSmall,
                        color = if (tile.loud) Makara.Amber else Makara.Cream,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(6.dp)
                            .clip(MakaraShape.Frame)
                            .background(Makara.Black.copy(alpha = 0.55f))
                            .padding(horizontal = 4.dp, vertical = 1.dp),
                    )
                }
            }
        }
    }
}

/** Film kutusu (klasör): oval kapak + 4 dp kenar, altında ad ve sıfır dolgulu sayı */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun Canister(album: Album, mode: MediaMode, onClick: () -> Unit, onLongClick: (() -> Unit)?, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(MakaraShape.Default)
            .background(Makara.Surface)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .semantics(mergeDescendants = true) { contentDescription = "${album.name}, ${mode.count(album.count)}" }
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(CircleShape)
                .background(Makara.SurfaceHigh)
                .padding(4.dp)
                .clip(CircleShape)
        ) {
            AsyncImage(
                model = VideoThumb(album.cover.uri, 320),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Spacer(Modifier.height(Gap))
        Text(album.name, style = MakaraType.Title, color = Makara.Cream, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(frameNumber(album.count), style = MakaraType.CounterSmall, color = Makara.Muted, maxLines = 1)
    }
}
