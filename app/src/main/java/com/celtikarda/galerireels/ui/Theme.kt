package com.celtikarda.galerireels.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
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
    outlineVariant = Makara.Outline,
    error = Makara.Error,
    onError = Makara.OnError,
    // Menüler, dialoglar, çipler: Material'in varsayılan mor tonları yerine Makara zeminleri
    primaryContainer = Makara.SurfaceHigh,
    onPrimaryContainer = Makara.Cream,
    secondary = Makara.CreamSoft,
    onSecondary = Makara.OnAmber,
    secondaryContainer = Makara.SurfaceHigh,
    onSecondaryContainer = Makara.Cream,
    tertiary = Makara.Amber,
    onTertiary = Makara.OnAmber,
    surfaceTint = Color.Transparent,
    surfaceBright = Makara.SurfaceHigh,
    surfaceDim = Makara.Black,
    surfaceContainerLowest = Makara.Black,
    surfaceContainerLow = Makara.Surface,
    surfaceContainer = Makara.Surface,
    surfaceContainerHigh = Makara.SurfaceHigh,
    surfaceContainerHighest = Makara.SurfaceHigh,
)

/**
 * 2.0'da tek tema var. Eski sürümde Aydınlık/Sistem seçmiş kullanıcının kaydı (ve yedek dosyasındaki alanı)
 * silinmez, sadece yok sayılır.
 */
@Suppress("UNUSED_PARAMETER")
internal fun paletteFor(mode: ThemeMode): AppColors = MakaraAppColors

/** Bir kez kurulur; her yeniden çizimde yeni ColorScheme oluşmasın */
private val MakaraColorScheme: ColorScheme = makaraColorScheme()

@Composable
fun MakaraTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = MakaraColorScheme, typography = MakaraTypography) {
        CompositionLocalProvider(LocalAppColors provides MakaraAppColors, content = content)
    }
}
