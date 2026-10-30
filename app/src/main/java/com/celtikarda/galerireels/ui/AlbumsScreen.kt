package com.celtikarda.galerireels.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.celtikarda.galerireels.Album
import com.celtikarda.galerireels.GalleryViewModel
import com.celtikarda.galerireels.MediaActions
import com.celtikarda.galerireels.MediaMode
import com.celtikarda.galerireels.Memories
import com.celtikarda.galerireels.MonthlyRecap
import com.celtikarda.galerireels.R
import com.celtikarda.galerireels.VideoItem
import com.celtikarda.galerireels.VideoThumb

@Composable
fun AlbumsScreen(
    vm: GalleryViewModel,
    partialAccess: Boolean,
    onSelectMore: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAchievements: () -> Unit,
    onOpenRecap: () -> Unit,
) {
    val tiles = vm.albumTiles()
    val mode = vm.stats.mediaMode
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val version = remember { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
    var pendingHide by remember { mutableStateOf<Album?>(null) }

    // Tüm videolar / Favoriler / Beğendiklerin üstte, klasörler ayrı başlık altında
    val collections = tiles.filter { it.id in setOf(Album.ALL, Album.FAVORITES, Album.LIKED) }
    val folders = tiles.filterNot { it in collections }
    val hiddenCount = vm.hiddenFolders.size
    val currentRecap = remember(vm.stats.historyRevision, vm.videoCount) { vm.monthlyRecap() }
    val memories = remember(vm.videoCount) { vm.memories() }

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            val isDark = appColors.isDark
            Column(Modifier.statusBarsPadding().padding(start = 4.dp, top = 16.dp, bottom = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Galeri Reels",
                        color = appColors.textPrimary,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { vm.toggleDarkLight(isDark) }, modifier = Modifier.size(42.dp)) {
                        Icon(
                            painterResource(if (isDark) R.drawable.ic_light_mode else R.drawable.ic_dark_mode),
                            contentDescription = if (isDark) "Aydınlık moda geç" else "Gece moduna geç",
                            tint = appColors.textPrimary,
                        )
                    }
                    IconButton(onClick = onOpenRecap, modifier = Modifier.size(42.dp)) {
                        Icon(painterResource(R.drawable.ic_recap), contentDescription = "Aylık özet", tint = InstaBlue)
                    }
                    IconButton(onClick = onOpenAchievements, modifier = Modifier.size(42.dp)) {
                        Icon(painterResource(R.drawable.ic_trophy), contentDescription = "Başarımlar", tint = appColors.accentGold)
                    }
                    IconButton(onClick = onOpenSettings, modifier = Modifier.size(42.dp)) {
                        Icon(painterResource(R.drawable.ic_settings), contentDescription = "Ayarlar", tint = appColors.textPrimary)
                    }
                }
                Text(
                    "${formatCount(vm.videoCount)} ${mode.itemWord} · ${formatCount(vm.folderCount)} klasör",
                    color = appColors.textSecondary,
                    fontSize = 14.sp,
                )
                Spacer(Modifier.height(10.dp))
                MediaModeSelector(current = mode, onSelect = vm::setMediaMode)
                if (partialAccess) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Sınırlı erişim · Daha fazla video seç",
                        color = InstaBlue,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable(onClick = onSelectMore),
                    )
                }
                if (vm.stats.loaded && vm.stats.folder == null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Beğeni ve izlenmelerin yedeklenmiyor · Klasör seç",
                        color = InstaBlue,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable(onClick = onOpenSettings),
                    )
                }
            }
        }

        if (MediaActions.trashSupported) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                val unwatched = vm.unwatched()
                val duplicates = vm.duplicateCopies
                CleanupCard(
                    mode = mode,
                    unwatchedCount = unwatched.size,
                    unwatchedBytes = unwatched.sumOf { it.sizeBytes },
                    cleanedToday = vm.stats.cleanedToday,
                    duplicateCount = duplicates.size,
                    duplicateBytes = duplicates.sumOf { it.sizeBytes },
                    onStart = vm::openCleanup,
                    onStartDuplicates = vm::openDuplicateCleanup,
                )
            }
        }

        if (currentRecap.hasActivity) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                RecapBannerCard(recap = currentRecap, onClick = onOpenRecap)
            }
        }

        if (memories.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        modifier = Modifier.padding(start = 2.dp, top = 4.dp, bottom = 8.dp),
                    ) {
                        Text(
                            "Tarihte bugün",
                            color = appColors.textPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            Memories.summaryText(memories),
                            color = appColors.accentGold,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(memories.take(20), key = { it.id }) { video ->
                            MemoryTile(
                                video = video,
                                yearsAgo = Memories.yearsAgo(video),
                                onClick = { vm.openMemories(video) },
                            )
                        }
                    }
                }
            }
        }

        val recent = vm.recentVideos(15)
        if (recent.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    Text(
                        "Son izlediklerin",
                        color = appColors.textPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 2.dp, top = 4.dp, bottom = 8.dp),
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(recent, key = { it.id }) { video ->
                            RecentTile(video, views = vm.viewsOf(video), onClick = { vm.openRecent(video) })
                        }
                    }
                }
            }
        }

        items(collections, key = { it.id }) { album ->
            AlbumTile(album, itemWord = mode.itemWord, onClick = { vm.open(album) })
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(
                verticalAlignment = Alignment.Bottom,
                modifier = Modifier.padding(start = 2.dp, top = 6.dp),
            ) {
                Text(
                    "Klasörler",
                    color = appColors.textPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                if (hiddenCount > 0) {
                    Text(
                        "${formatCount(hiddenCount)} gizli · Göster",
                        color = InstaBlue,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable(onClick = onOpenSettings),
                    )
                } else {
                    Text(
                        "Gizlemek için basılı tut",
                        color = appColors.textMuted,
                        fontSize = 12.sp,
                    )
                }
            }
        }

        items(folders, key = { it.id }) { album ->
            AlbumTile(
                album = album,
                itemWord = mode.itemWord,
                onClick = { vm.open(album) },
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    pendingHide = album
                },
            )
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
            Text(
                "Galeri Reels $version",
                color = appColors.textMuted,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.navigationBarsPadding().padding(top = 12.dp, bottom = 16.dp),
            )
        }
    }

    pendingHide?.let { folder ->
        AlertDialog(
            onDismissRequest = { pendingHide = null },
            containerColor = appColors.card,
            titleContentColor = appColors.textPrimary,
            textContentColor = appColors.textSecondary,
            title = { Text("«${folder.name}» gizlensin mi?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Bu klasördeki ${formatCount(folder.count)} video ana ekranda, Tüm videolar'da, " +
                        "Tarihte bugün'de ve Temizlik önerilerinde gösterilmez.\n\n" +
                        "İstediğin zaman Ayarlar › Gizlenen klasörler kısmından geri açabilirsin."
                )
            },
            dismissButton = {
                TextButton(onClick = { pendingHide = null }) {
                    Text("İptal", color = appColors.textSecondary)
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.hideAlbum(folder.id)
                        pendingHide = null
                    }
                ) {
                    Text("Gizle", color = InstaBlue, fontWeight = FontWeight.Bold)
                }
            },
        )
    }
}

/** Ana ekranda Videolar / Fotoğraflar / Tümü arasında hızlı geçiş sekmesi */
@Composable
private fun MediaModeSelector(current: MediaMode, onSelect: (MediaMode) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(appColors.card)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        MediaMode.entries.forEach { entry ->
            val selected = entry == current
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (selected) InstaBlue else Color.Transparent)
                    .clickable { onSelect(entry) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    entry.title,
                    color = if (selected) Color.White else appColors.textSecondary,
                    fontSize = 13.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                )
            }
        }
    }
}

/** Aylık özet kartının ana ekrandaki kısa girişi */
@Composable
private fun RecapBannerCard(recap: MonthlyRecap, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.horizontalGradient(listOf(Color(0xFF2B1055), Color(0xFF5E1742))))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(R.drawable.ic_recap), contentDescription = null, tint = FavoriteYellow, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("${formatMonth(recap.month)} özeti", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            val details = buildList {
                if (recap.totalViews > 0) add("${formatCount(recap.totalViews)} izlenme")
                recap.topAlbum?.let { add("En çok: ${it.name}") }
                if (recap.cleanedBytes > 0 && recap.topAlbum == null) add("${formatSize(recap.cleanedBytes)} temizlendi")
            }.joinToString(" · ")
            if (details.isNotEmpty()) {
                Text(
                    details,
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Text("Özeti gör", color = FavoriteYellow, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

/** Temizlik modunun girişi: hiç izlenmeyenlerin kapladığı yer, bugün temizlenen ve kopya içerikler */
@Composable
private fun CleanupCard(
    mode: MediaMode,
    unwatchedCount: Int,
    unwatchedBytes: Long,
    cleanedToday: Long,
    duplicateCount: Int,
    duplicateBytes: Long,
    onStart: () -> Unit,
    onStartDuplicates: () -> Unit,
) {
    val actionWord = if (mode == MediaMode.PHOTOS) "bakmadığın" else "izlemediğin"
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(appColors.card)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onStart)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(CleanupGreen.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(R.drawable.ic_delete_sweep), contentDescription = null, tint = CleanupGreen, modifier = Modifier.size(26.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Temizlik", color = appColors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    if (unwatchedCount > 0) {
                        "Hiç $actionWord ${formatCount(unwatchedCount)} ${mode.itemWord} ${formatSize(unwatchedBytes)} yer kaplıyor"
                    } else {
                        "Artık istemediğin içerikleri sil, yer aç"
                    },
                    color = appColors.textSecondary,
                    fontSize = 13.sp,
                )
                if (cleanedToday > 0) {
                    Text(
                        "Bugün ${formatSize(cleanedToday)} temizledin",
                        color = CleanupGreen,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Text("Başla", color = InstaBlue, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
        if (duplicateCount > 0) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(appColors.divider)
                    .clickable(onClick = onStartDuplicates)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Benzer / kopya ${formatCount(duplicateCount)} ${mode.itemWord} (${formatSize(duplicateBytes)}) bulundu",
                    color = appColors.accentGold,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(12.dp))
                Text("Kopyaları incele", color = InstaBlue, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** Geçmiş yıllarda bu hafta çekilmiş anı videosu: hikâye çerçevesi ve kaç yıl önce olduğu */
@Composable
private fun MemoryTile(video: VideoItem, yearsAgo: Int, onClick: () -> Unit) {
    Box(
        Modifier
            .width(92.dp)
            .aspectRatio(9f / 16f)
            .clip(RoundedCornerShape(12.dp))
            .border(1.5.dp, Brush.sweepGradient(StoryRing), RoundedCornerShape(12.dp))
            .background(Color(0xFF1C1C1E))
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = VideoThumb(video.uri, 320),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(0.55f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.8f)))
        )
        Text(
            "$yearsAgo yıl önce",
            color = FavoriteYellow,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.BottomStart).padding(7.dp),
        )
    }
}

/** Son izlenen video: dikey küçük resim, köşede kaç kez izlendiği */
@Composable
private fun RecentTile(video: VideoItem, views: Int, onClick: () -> Unit) {
    Box(
        Modifier
            .width(88.dp)
            .aspectRatio(9f / 16f)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF1C1C1E))
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = VideoThumb(video.uri, 320),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(0.6f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.7f)))
        )
        Row(
            Modifier.align(Alignment.BottomStart).padding(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(painterResource(R.drawable.ic_eye), contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(3.dp))
            Text(formatCount(views), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** Telefonun galerisindeki gibi: kare kapak, altında klasör adı ve içerik sayısı */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AlbumTile(album: Album, itemWord: String = "video", onClick: () -> Unit, onLongClick: (() -> Unit)? = null) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(appColors.card)
        ) {
            AsyncImage(
                model = VideoThumb(album.cover.uri, 360),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            when (album.id) {
                Album.LIKED -> TileBadge(R.drawable.ic_heart_filled, LikeRed)
                Album.FAVORITES -> TileBadge(R.drawable.ic_star, FavoriteYellow)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            album.name,
            color = appColors.textPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text("${formatCount(album.count)} $itemWord", color = appColors.textSecondary, fontSize = 12.sp)
    }
}

/** Kapağın köşesinde koyu daire içinde simge (kapak parlak olsa da görünsün) */
@Composable
private fun TileBadge(icon: Int, tint: Color) {
    Box(
        Modifier
            .padding(6.dp)
            .size(26.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.5f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
    }
}
