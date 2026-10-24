package com.celtikarda.galerireels.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import com.celtikarda.galerireels.GalleryViewModel
import com.celtikarda.galerireels.MonthlyRecap
import com.celtikarda.galerireels.R
import com.celtikarda.galerireels.RankedAlbum
import com.celtikarda.galerireels.RankedVideo
import com.celtikarda.galerireels.RecapCardShare
import com.celtikarda.galerireels.VideoItem
import com.celtikarda.galerireels.VideoThumb
import kotlinx.coroutines.launch
import java.time.YearMonth

private val RecapGradient = listOf(
    Color(0xFF2B1055),
    Color(0xFF5E1742),
    Color(0xFF1A0F26),
    Color(0xFF100E16),
)

@Composable
fun RecapScreen(vm: GalleryViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val months = remember(vm.stats.historyRevision) { vm.availableMonths() }
    var selectedMonth by remember(months) { mutableStateOf(months.firstOrNull() ?: YearMonth.now()) }
    val recap = remember(selectedMonth, vm.stats.historyRevision, vm.videoCount, vm.stats.totalLikes) {
        vm.monthlyRecap(selectedMonth)
    }
    var sharing by remember { mutableStateOf(false) }

    val shareCard: () -> Unit = {
        if (!sharing && recap.hasActivity) {
            sharing = true
            scope.launch {
                runCatching { RecapCardShare.share(context, recap, vm::albumNameOf) }
                    .onFailure { Toast.makeText(context, "Özet kartı hazırlanamadı", Toast.LENGTH_SHORT).show() }
                sharing = false
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 8.dp, bottom = 6.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Geri", tint = appColors.textPrimary)
            }
            Text(
                "Aylık özet",
                color = appColors.textPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            if (recap.hasActivity) {
                IconButton(onClick = shareCard, enabled = !sharing) {
                    Icon(painterResource(R.drawable.ic_send), contentDescription = "Özeti paylaş", tint = appColors.accentGold)
                }
            }
        }

        if (months.size > 1) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 6.dp),
            ) {
                items(months) { month ->
                    val selected = month == selectedMonth
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .background(if (selected) FavoriteYellow else appColors.card)
                            .clickable { selectedMonth = month }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        Text(
                            formatMonth(month),
                            color = if (selected) Color.Black else appColors.textSecondary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
        }

        RecapCard(
            recap = recap,
            albumNameOf = vm::albumNameOf,
            onVideoClick = { video -> vm.openMonthly(selectedMonth, video) },
        )

        if (recap.hasActivity) {
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = shareCard,
                enabled = !sharing,
                colors = ButtonDefaults.buttonColors(
                    containerColor = FavoriteYellow,
                    contentColor = Color.Black,
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                if (sharing) {
                    CircularProgressIndicator(
                        color = Color.Black,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text("Kart hazırlanıyor…", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                } else {
                    Icon(painterResource(R.drawable.ic_send), contentDescription = null, tint = Color.Black, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Özeti kart olarak paylaş", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun RecapCard(
    recap: MonthlyRecap,
    albumNameOf: (VideoItem) -> String,
    onVideoClick: (VideoItem) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(RecapGradient))
            .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(24.dp))
            .padding(20.dp)
    ) {
        Box(
            Modifier
                .clip(RoundedCornerShape(50))
                .background(Color.White.copy(alpha = 0.15f))
                .padding(horizontal = 12.dp, vertical = 4.dp)
        ) {
            Text(
                "GALERİ REELS ÖZETİ",
                color = FavoriteYellow,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            formatMonth(recap.month),
            color = Color.White,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
        )

        if (!recap.hasActivity) {
            Spacer(Modifier.height(20.dp))
            Text(
                "Bu ay henüz video izlemedin ya da temizlik yapmadın. İzledikçe en sevdiğin videolar, " +
                    "klasörün ve toplam süren burada birikecek.",
                color = Color.White.copy(alpha = 0.75f),
                fontSize = 15.sp,
            )
            Spacer(Modifier.height(12.dp))
            return@Column
        }

        Spacer(Modifier.height(16.dp))

        // 2x2 istatistik kutuları
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            StatBox(
                label = "TOPLAM İZLENME",
                value = "${formatCount(recap.totalViews)} kez",
                modifier = Modifier.weight(1f),
            )
            StatBox(
                label = "İZLEME SÜRESİ",
                value = formatWatchTime(recap.watchTimeMs),
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            StatBox(
                label = "FARKLI VİDEO",
                value = "${formatCount(recap.uniqueVideos)} video",
                modifier = Modifier.weight(1f),
            )
            if (recap.cleanedBytes > 0) {
                StatBox(
                    label = "AÇILAN YER",
                    value = formatSize(recap.cleanedBytes),
                    modifier = Modifier.weight(1f),
                )
            } else {
                StatBox(
                    label = "AKTİF GÜN",
                    value = "${recap.activeDays} gün",
                    modifier = Modifier.weight(1f),
                )
            }
        }

        recap.topAlbum?.let { album ->
            Spacer(Modifier.height(20.dp))
            Text(
                "EN SEVDİĞİN KLASÖR",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            TopAlbumRow(album)
        }

        if (recap.topVideos.isNotEmpty()) {
            Spacer(Modifier.height(20.dp))
            Text(
                "BU AY EN ÇOK İZLEDİKLERİN",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                recap.topVideos.forEachIndexed { index, ranked ->
                    TopVideoRow(
                        rank = index + 1,
                        ranked = ranked,
                        folderName = albumNameOf(ranked.video),
                        onClick = { onVideoClick(ranked.video) },
                    )
                }
            }
        }

        Spacer(Modifier.height(18.dp))
        Text(
            "Galeri Reels",
            color = Color.White.copy(alpha = 0.45f),
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun StatBox(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.1f))
            .padding(14.dp)
    ) {
        Text(label, color = Color.White.copy(alpha = 0.65f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(value, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun TopAlbumRow(album: RankedAlbum) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.1f))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = VideoThumb(album.cover.uri, 240),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(54.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF2C2C2E)),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                album.name,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "${formatCount(album.views)} izlenme · ${formatCount(album.watchedVideos)} farklı video",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 13.sp,
            )
        }
    }
}

@Composable
private fun TopVideoRow(
    rank: Int,
    ranked: RankedVideo,
    folderName: String,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.1f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "#$rank",
            color = FavoriteYellow,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(30.dp),
        )
        AsyncImage(
            model = VideoThumb(ranked.video.uri, 200),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF2C2C2E)),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                ranked.video.name.substringBeforeLast('.'),
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                folderName,
                color = Color.White.copy(alpha = 0.65f),
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            "${formatCount(ranked.views)} kez",
            color = FavoriteYellow,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}
