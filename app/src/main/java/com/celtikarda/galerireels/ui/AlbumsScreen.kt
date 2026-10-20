package com.celtikarda.galerireels.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
) {
    val tiles = vm.albumTiles()
    val context = LocalContext.current
    val version = remember { context.packageManager.getPackageInfo(context.packageName, 0).versionName }

    // Tüm videolar / Favoriler / Beğendiklerin üstte, klasörler ayrı başlık altında
    val collections = tiles.filter { it.id in setOf(Album.ALL, Album.FAVORITES, Album.LIKED) }
    val folders = tiles.filterNot { it in collections }

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(Modifier.statusBarsPadding().padding(start = 4.dp, top = 16.dp, bottom = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Galeri Reels",
                        color = Color.White,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onOpenAchievements) {
                        Icon(painterResource(R.drawable.ic_trophy), contentDescription = "Başarımlar", tint = FavoriteYellow)
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(painterResource(R.drawable.ic_settings), contentDescription = "Ayarlar", tint = Color.White)
                    }
                }
                Text(
                    "${formatCount(vm.videoCount)} video · ${formatCount(vm.folderCount)} klasör",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 14.sp,
                )
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
                CleanupCard(
                    unwatchedCount = unwatched.size,
                    unwatchedBytes = unwatched.sumOf { it.sizeBytes },
                    cleanedToday = vm.stats.cleanedToday,
                    onStart = vm::openCleanup,
                )
            }
        }

        val recent = vm.recentVideos(15)
        if (recent.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    Text(
                        "Son izlediklerin",
                        color = Color.White,
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
            AlbumTile(album, onClick = { vm.open(album) })
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
            Text(
                "Klasörler",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 2.dp, top = 6.dp),
            )
        }

        items(folders, key = { it.id }) { album ->
            AlbumTile(album, onClick = { vm.open(album) })
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
            Text(
                "Galeri Reels $version",
                color = Color.White.copy(alpha = 0.35f),
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.navigationBarsPadding().padding(top = 12.dp, bottom = 16.dp),
            )
        }
    }
}

/** Temizlik modunun girişi: hiç izlenmeyenlerin kapladığı yer, bugün temizlenen */
@Composable
private fun CleanupCard(unwatchedCount: Int, unwatchedBytes: Long, cleanedToday: Long, onStart: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF1C1C1E))
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
            Text("Temizlik", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(
                if (unwatchedCount > 0) {
                    "Hiç izlemediğin ${formatCount(unwatchedCount)} video ${formatSize(unwatchedBytes)} yer kaplıyor"
                } else {
                    "Artık istemediğin videoları sil, yer aç"
                },
                color = Color.White.copy(alpha = 0.7f),
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

/** Telefonun galerisindeki gibi: kare kapak, altında klasör adı ve video sayısı */
@Composable
private fun AlbumTile(album: Album, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF1C1C1E))
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
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text("${formatCount(album.count)} video", color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp)
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
