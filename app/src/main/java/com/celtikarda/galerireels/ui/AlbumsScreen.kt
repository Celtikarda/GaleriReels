package com.celtikarda.galerireels.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.celtikarda.galerireels.Album
import com.celtikarda.galerireels.GalleryViewModel
import com.celtikarda.galerireels.R
import com.celtikarda.galerireels.VideoThumb

@Composable
fun AlbumsScreen(vm: GalleryViewModel, partialAccess: Boolean, onSelectMore: () -> Unit) {
    val tiles = vm.albumTiles()

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(Modifier.statusBarsPadding().padding(start = 4.dp, top = 16.dp, bottom = 8.dp)) {
                Text("Galeri Reels", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(2.dp))
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
            }
        }

        items(tiles, key = { it.id }) { album ->
            AlbumTile(album, onClick = { vm.open(album) })
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
            Spacer(Modifier.navigationBarsPadding().height(16.dp))
        }
    }
}

@Composable
private fun AlbumTile(album: Album, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(3f / 4f)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF1C1C1E))
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = VideoThumb(album.cover.uri, 480),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(0.5f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.75f)))
        )

        if (album.id == Album.LIKED) {
            Icon(
                painterResource(R.drawable.ic_heart_filled),
                contentDescription = null,
                tint = LikeRed,
                modifier = Modifier.align(Alignment.TopStart).padding(10.dp).size(22.dp),
            )
        }

        Column(Modifier.align(Alignment.BottomStart).padding(12.dp)) {
            Text(
                album.name,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text("${formatCount(album.count)} video", color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp)
        }
    }
}
