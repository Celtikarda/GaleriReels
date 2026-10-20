package com.celtikarda.galerireels.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.celtikarda.galerireels.Achievement
import com.celtikarda.galerireels.AchievementInput
import com.celtikarda.galerireels.GalleryViewModel
import com.celtikarda.galerireels.R
import com.celtikarda.galerireels.achievements

private val GoldYellow = Color(0xFFFFC83D)

@Composable
fun AchievementsScreen(vm: GalleryViewModel, onBack: () -> Unit) {
    val input = remember(vm.stats.totalViews, vm.favoriteIds, vm.stats.cleanedTotal) { vm.achievementInput() }
    val unlocked = vm.stats.unlocked
    // Açılanlar önce (en yeni en üstte), sonra kalanlar listedeki sırasıyla
    val sorted = achievements.sortedWith(
        compareBy<Achievement> { it.id !in unlocked }.thenByDescending { unlocked[it.id] ?: 0L },
    )

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(Modifier.statusBarsPadding().padding(top = 8.dp, bottom = 6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Geri", tint = Color.White)
                    }
                    Text("Başarımlar", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text(
                        "${unlocked.size} / ${achievements.size}",
                        color = GoldYellow,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
                Text(
                    "İzledikçe, temizledikçe ve uygulamayı her gün açtıkça yenileri açılır.",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 14.sp,
                    modifier = Modifier.padding(start = 12.dp, top = 2.dp),
                )
            }
        }

        items(sorted, key = { it.id }) { achievement ->
            AchievementCard(achievement, input, unlockedAt = unlocked[achievement.id])
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
            Spacer(Modifier.navigationBarsPadding().height(16.dp))
        }
    }
}

@Composable
private fun AchievementCard(achievement: Achievement, input: AchievementInput, unlockedAt: Long?) {
    val done = unlockedAt != null
    val progress = achievement.progress(input).coerceAtMost(achievement.target)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (done) Color(0xFF2A2412) else Color(0xFF1C1C1E))
            .padding(14.dp)
    ) {
        Text(achievement.emoji, fontSize = 34.sp, modifier = Modifier.alpha(if (done) 1f else 0.35f))
        Spacer(Modifier.height(8.dp))
        Text(
            achievement.title,
            color = if (done) GoldYellow else Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(achievement.description, color = Color.White.copy(alpha = 0.65f), fontSize = 13.sp)
        Spacer(Modifier.height(10.dp))
        if (done) {
            Text("Açıldı · ${formatDate(unlockedAt!!)}", color = GoldYellow.copy(alpha = 0.85f), fontSize = 12.sp)
        } else {
            ProgressLine(progress.toFloat() / achievement.target)
            val text = achievement.format?.let { f ->
                val left = f(progress)
                if (left.isEmpty()) "" else "$left / ${f(achievement.target)}"
            } ?: "${formatCount(progress.toInt())} / ${formatCount(achievement.target.toInt())}"
            if (text.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(text, color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun ProgressLine(fraction: Float) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(5.dp)
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.12f))
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(5.dp)
                .clip(RoundedCornerShape(50))
                .background(GoldYellow)
        )
    }
}

/** Yeni başarım açılınca ekranın üstünde kısa süre görünen bildirim; dokununca başarımlar açılır */
@Composable
fun AchievementBanner(unlocked: List<Achievement>, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val title = if (unlocked.size == 1) "Yeni başarım: ${unlocked.first().title}" else "${unlocked.size} yeni başarım açıldı"
    Row(
        modifier
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(50))
            .background(Color(0xFF2A2412))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(unlocked.take(4).joinToString(" ") { it.emoji }, fontSize = 20.sp)
        Spacer(Modifier.width(10.dp))
        Text(title, color = GoldYellow, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}
