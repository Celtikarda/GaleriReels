package com.celtikarda.galerireels.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

val LikeRed = Color(0xFFFF3040)
val InstaBlue = Color(0xFF0095F6)
val FavoriteYellow = Color(0xFFFFC83D)
val DeleteRed = Color(0xFFE5383B)
val CleanupGreen = Color(0xFF34C759)

/** Profil resminin etrafındaki hikâye halkası renkleri */
val StoryRing = listOf(
    Color(0xFFFEDA75),
    Color(0xFFFA7E1E),
    Color(0xFFD62976),
    Color(0xFF962FBF),
    Color(0xFF4F5BD5),
    Color(0xFFFEDA75),
)

/** Gece (koyu) ve Aydınlık (açık) modlar için ortak renk paleti */
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

val DarkAppColors = AppColors(
    isDark = true,
    background = Color.Black,
    card = Color(0xFF1C1C1E),
    cardElevated = Color(0xFF2C2C2E),
    textPrimary = Color.White,
    textSecondary = Color.White.copy(alpha = 0.72f),
    textMuted = Color.White.copy(alpha = 0.45f),
    divider = Color.White.copy(alpha = 0.08f),
    accentGold = FavoriteYellow,
    achievementDoneBg = Color(0xFF2A2412),
    statusBarScrim = Color.Black.copy(alpha = 0.92f),
)

val LightAppColors = AppColors(
    isDark = false,
    background = Color(0xFFF2F2F7),
    card = Color.White,
    cardElevated = Color(0xFFE5E5EA),
    textPrimary = Color(0xFF111114),
    textSecondary = Color(0xFF3C3C43).copy(alpha = 0.78f),
    textMuted = Color(0xFF3C3C43).copy(alpha = 0.48f),
    divider = Color.Black.copy(alpha = 0.06f),
    accentGold = Color(0xFFC28200),
    achievementDoneBg = Color(0xFFFFF3D1),
    statusBarScrim = Color(0xFFF2F2F7).copy(alpha = 0.92f),
)

val LocalAppColors = staticCompositionLocalOf { DarkAppColors }

val appColors: AppColors
    @Composable
    @ReadOnlyComposable
    get() = LocalAppColors.current
