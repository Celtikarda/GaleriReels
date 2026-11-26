package com.celtikarda.galerireels.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.celtikarda.galerireels.ThemeMode

/** Material bileşenleri (Button, Switch, dialog…) için Makara renkleri */
fun makaraColorScheme(): ColorScheme = darkColorScheme(
    primary = Makara.Amber,
    onPrimary = Makara.OnAmber,
    background = Makara.Black,
    onBackground = Makara.Cream,
    surface = Makara.Surface,
    onSurface = Makara.Cream,
    surfaceVariant = Makara.SurfaceHigh,
    onSurfaceVariant = Makara.CreamSoft,
    outline = Makara.Outline,
    error = Makara.Error,
    onError = Makara.OnError,
)

/**
 * 2.0'da tek tema var. Eski sürümde Aydınlık/Sistem seçmiş kullanıcının kaydı (ve yedek dosyasındaki alanı)
 * silinmez, sadece yok sayılır.
 */
@Suppress("UNUSED_PARAMETER")
internal fun paletteFor(mode: ThemeMode): AppColors = MakaraAppColors

@Composable
fun MakaraTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = makaraColorScheme(), typography = MakaraTypography) {
        CompositionLocalProvider(LocalAppColors provides MakaraAppColors, content = content)
    }
}
