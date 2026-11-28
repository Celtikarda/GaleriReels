package com.celtikarda.galerireels.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.celtikarda.galerireels.R
import com.celtikarda.galerireels.str

/** Ana ekranın alt menüsündeki sekmeler */
enum class HomeTab(@DrawableRes val icon: Int, @StringRes val label: Int) {
    REEL(R.drawable.ic_tab_reel, R.string.tab_reel),
    FLOW(R.drawable.ic_tab_flow, R.string.tab_flow),
    LOVED(R.drawable.ic_tab_loved, R.string.tab_loved),
    YOU(R.drawable.ic_tab_you, R.string.tab_you);
}

/** Akış bir sekme değil: dokununca tüm videolar oynatıcıda açılır, menü Makara'da kalır */
fun HomeTab.opensPlayer(): Boolean = this == HomeTab.FLOW

/** Yüzen alt menü: 64 dp, 24 dp köşe, tek gölge; etkin sekme kehribar kutu, yazı yok */
@Composable
internal fun FloatingBottomBar(current: HomeTab, onSelect: (HomeTab) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .height(64.dp)
            .shadow(16.dp, MakaraShape.Bar)
            .clip(MakaraShape.Bar)
            .background(Makara.Surface)
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HomeTab.entries.forEach { tab ->
            val active = tab == current
            val label = str(tab.label)
            Box(
                Modifier
                    .size(width = 64.dp, height = 48.dp)
                    .clip(MakaraShape.Control)
                    .background(if (active) Makara.Amber else Makara.Surface)
                    .selectable(selected = active, role = Role.Tab) { onSelect(tab) }
                    .semantics { contentDescription = label },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(tab.icon),
                    contentDescription = null,
                    tint = if (active) Makara.OnAmber else Makara.Muted,
                    modifier = Modifier.width(24.dp).height(24.dp),
                )
            }
        }
    }
}
