package com.celtikarda.galerireels.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Makara kimliğinin renkleri — kaynak: DESIGN.md (önce orası güncellenir) */
object Makara {
    /** Makara siyahı: her ekranın zemini */
    val Black = Color(0xFF120F0D)
    /** Kare zemini: kartlar, film şeridi, alt menü */
    val Surface = Color(0xFF1E1915)
    /** Kutu kenarı */
    val SurfaceHigh = Color(0xFF2B241F)
    val Outline = Color(0xFF3A302A)
    /** Kehribar: yalnızca "şu an" ve eylem için */
    val Amber = Color(0xFFFFB547)
    val OnAmber = Color(0xFF120F0D)
    val Cream = Color(0xFFF5EDE3)
    val CreamSoft = Color(0xFFD8CCBE)
    val Muted = Color(0xFFA89B8C)
    val Error = Color(0xFFB3261E)
    val OnError = Color(0xFFFFFFFF)
}

// Eski adlar: Aşama 4'te kaldırılacak. ReelsScreen.kt'ye dokunmadan oynatıcının da Makara'ya geçmesi için.
val LikeRed = Makara.Amber
val InstaBlue = Makara.Amber
val FavoriteYellow = Makara.Amber
val DeleteRed = Makara.Error
val CleanupGreen = Makara.Amber

/** Anı / profil halkası renkleri (eski adıyla) */
val StoryRing = listOf(Makara.Amber, Color(0xFFFFD08A), Makara.Amber)

/** Uygulamanın renk paleti (2.0'da tek, koyu Makara teması) */
@Immutable
data class AppColors(
    val isDark: Boolean,
    val background: Color,
    val card: Color,
    val cardElevated: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val divider: Color,
    val accentGold: Color,
    val achievementDoneBg: Color,
    val statusBarScrim: Color,
)

val MakaraAppColors = AppColors(
    isDark = true,
    background = Makara.Black,
    card = Makara.Surface,
    cardElevated = Makara.SurfaceHigh,
    textPrimary = Makara.Cream,
    textSecondary = Makara.CreamSoft,
    textMuted = Makara.Muted,
    divider = Makara.Outline,
    accentGold = Makara.Amber,
    achievementDoneBg = Makara.SurfaceHigh,
    statusBarScrim = Makara.Black.copy(alpha = 0.92f),
)

// Geçici: Task 3'te kaldırılacak
val DarkAppColors = MakaraAppColors
val LightAppColors = MakaraAppColors

val LocalAppColors = staticCompositionLocalOf { MakaraAppColors }

val appColors: AppColors
    @Composable
    @ReadOnlyComposable
    get() = LocalAppColors.current
