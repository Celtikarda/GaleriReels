package com.celtikarda.galerireels.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.celtikarda.galerireels.Album
import com.celtikarda.galerireels.GalleryViewModel
import com.celtikarda.galerireels.R
import com.celtikarda.galerireels.str

/** Sevdiklerim sekmesi: Favoriler ve Beğendiklerin kutuları, en çok beğendiklerin şeridi */
@Composable
fun LovedScreen(vm: GalleryViewModel, contentBottomPadding: Dp) {
    val mode = vm.stats.mediaMode
    val boxes = vm.albumTiles().filter { it.id == Album.FAVORITES || it.id == Album.LIKED }
    val liked = remember(vm.stats.historyRevision, vm.videoCount, mode) { vm.mostLiked() }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = 20.dp)
            .padding(top = 12.dp, bottom = contentBottomPadding)
    ) {
        SectionTitle(str(R.string.tab_loved))
        Spacer(Modifier.height(12.dp))
        if (boxes.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                boxes.forEach { album ->
                    Canister(album, mode, onClick = { vm.open(album) }, onLongClick = null, modifier = Modifier.weight(1f))
                }
                // Tek kutu varsa yarım genişlikte kalsın
                if (boxes.size == 1) Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(20.dp))
        }
        if (liked.isNotEmpty()) {
            FilmStrip(
                title = str(R.string.most_liked),
                items = liked,
                onPlay = { vm.openMostLiked(liked.first()) },
                onItem = vm::openMostLiked,
            )
        } else {
            Text(str(R.string.loved_empty), style = MakaraType.Body, color = Makara.Muted)
        }
    }
}
