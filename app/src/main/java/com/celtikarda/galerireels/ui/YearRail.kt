package com.celtikarda.galerireels.ui

import kotlinx.coroutines.delay

import androidx.compose.runtime.setValue

import androidx.compose.runtime.remember

import androidx.compose.runtime.mutableStateOf

import androidx.compose.runtime.getValue

import androidx.compose.runtime.derivedStateOf

import androidx.compose.runtime.LaunchedEffect

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/**
 * Sağ kenarda yüzen yıl şeridi ('26 '25 …). İçeriğe yer ayırmaz; yalnızca kaydırırken ve
 * durduktan 1,5 sn sonrasına kadar görünür. Aktif yıl kehribar; dokununca o yıl açılır. [years] boşsa hiç çizilmez.
 */
@Composable
internal fun YearRail(years: List<Int>, activeYear: Int?, scrolling: () -> Boolean, onPick: (Int) -> Unit, modifier: Modifier = Modifier) {
    if (years.isEmpty()) return
    // Kaydırma durumu burada okunur: her kaydırma başlayıp bitince bütün ana ekran değil yalnızca şerit yeniden çizilir
    val isScrolling by remember { derivedStateOf(scrolling) }
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(isScrolling) {
        if (isScrolling) visible = true else {
            delay(HIDE_DELAY_MS)
            visible = false
        }
    }
    AnimatedVisibility(visible, modifier, enter = fadeIn(tween(150)), exit = fadeOut(tween(150))) {
        Column(
            Modifier
                .width(38.dp)
                .shadow(8.dp, CircleShape)
                .clip(CircleShape)
                .background(Makara.Surface.copy(alpha = 0.92f))
                .padding(vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            years.take(MAX_YEARS).forEach { year ->
                val active = year == activeYear
                Box(
                    Modifier
                        .size(38.dp, 44.dp)
                        .clickable(role = Role.Button) { onPick(year) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "'" + (year % 100).toString().padStart(2, '0'),
                        style = MakaraType.CounterSmall,
                        color = if (active) Makara.Amber else Makara.Muted,
                    )
                }
            }
        }
    }
}

private const val MAX_YEARS = 12
private const val HIDE_DELAY_MS = 1_500L
