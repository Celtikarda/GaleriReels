package com.celtikarda.galerireels.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.celtikarda.galerireels.GalleryViewModel
import com.celtikarda.galerireels.R
import com.celtikarda.galerireels.achievements
import com.celtikarda.galerireels.plural
import com.celtikarda.galerireels.str
import com.celtikarda.galerireels.unlockedCount

/** Sen sekmesi: bu ay, başarımlar, aylık özet */
@Composable
fun YouScreen(vm: GalleryViewModel, onOpenRecap: () -> Unit, onOpenAchievements: () -> Unit, contentBottomPadding: Dp) {
    val recap = remember(vm.stats.historyRevision, vm.videoCount) { vm.monthlyRecap() }
    val unlocked = unlockedCount(vm.stats.unlocked)
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = 20.dp)
            .padding(top = 12.dp, bottom = contentBottomPadding)
    ) {
        SectionTitle(str(R.string.tab_you))
        Spacer(Modifier.height(12.dp))
        ThisMonthTile(recap.watchTimeMs, recap.uniqueVideos, mode = vm.stats.mediaMode, onClick = onOpenRecap, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        Text(
            str(
                R.string.you_month_line,
                plural(R.plurals.n_days, recap.activeDays, formatCount(recap.activeDays)),
                plural(R.plurals.n_likes, recap.newLikes, formatCount(recap.newLikes)),
            ),
            style = MakaraType.BodySmall,
            color = Makara.Muted,
        )
        Spacer(Modifier.height(16.dp))
        YouBox(
            title = str(R.string.achievements),
            detail = str(R.string.achievements_progress, formatCount(unlocked), formatCount(achievements.size)),
            onClick = onOpenAchievements,
        )
        Spacer(Modifier.height(10.dp))
        YouBox(title = str(R.string.monthly_recap), detail = formatMonth(recap.month), onClick = onOpenRecap)
    }
}

@Composable
private fun YouBox(title: String, detail: String, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .clip(MakaraShape.Default)
            .background(Makara.Surface)
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        Text(title.upper(), style = MakaraType.Counter, color = Makara.Cream)
        Text(detail, style = MakaraType.BodySmall, color = Makara.Muted)
    }
}
