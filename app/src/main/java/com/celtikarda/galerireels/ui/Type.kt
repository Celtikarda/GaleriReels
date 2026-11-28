package com.celtikarda.galerireels.ui

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.celtikarda.galerireels.R

/** Makara fontları (uygulamaya gömülü, OFL — lisanslar assets/licenses) */
object MakaraFonts {
    /** Inter: metinler, düğmeler, ayarlar */
    val Body = FontFamily(
        Font(R.font.inter_regular, FontWeight.Normal),
        Font(R.font.inter_medium, FontWeight.Medium),
        Font(R.font.inter_semibold, FontWeight.SemiBold),
    )

    /** Big Shoulders Display: bölüm başlıkları, kare sayaçları, sayılar */
    val Display = FontFamily(
        Font(R.font.big_shoulders_display_semibold, FontWeight.SemiBold),
        Font(R.font.big_shoulders_display_extrabold, FontWeight.ExtraBold),
    )
}

/** Material ölçüleri aynı, yazı tipi Inter */
val MakaraTypography: Typography = Typography().let { t ->
    val f = MakaraFonts.Body
    t.copy(
        displayLarge = t.displayLarge.copy(fontFamily = f),
        displayMedium = t.displayMedium.copy(fontFamily = f),
        displaySmall = t.displaySmall.copy(fontFamily = f),
        headlineLarge = t.headlineLarge.copy(fontFamily = f),
        headlineMedium = t.headlineMedium.copy(fontFamily = f),
        headlineSmall = t.headlineSmall.copy(fontFamily = f),
        titleLarge = t.titleLarge.copy(fontFamily = f),
        titleMedium = t.titleMedium.copy(fontFamily = f),
        titleSmall = t.titleSmall.copy(fontFamily = f),
        bodyLarge = t.bodyLarge.copy(fontFamily = f),
        bodyMedium = t.bodyMedium.copy(fontFamily = f),
        bodySmall = t.bodySmall.copy(fontFamily = f),
        labelLarge = t.labelLarge.copy(fontFamily = f),
        labelMedium = t.labelMedium.copy(fontFamily = f),
        labelSmall = t.labelSmall.copy(fontFamily = f),
    )
}

/** DESIGN.md `typography` ölçüleri (renk verilmez; kullanan yer seçer) */
object MakaraType {
    val Display = TextStyle(fontFamily = MakaraFonts.Display, fontWeight = FontWeight.ExtraBold, fontSize = 30.sp, lineHeight = 32.sp, letterSpacing = 0.01.em)
    val DisplayLarge = TextStyle(fontFamily = MakaraFonts.Display, fontWeight = FontWeight.ExtraBold, fontSize = 40.sp, lineHeight = 41.sp, letterSpacing = 0.01.em)
    val Counter = TextStyle(fontFamily = MakaraFonts.Display, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, lineHeight = 20.sp)
    val CounterSmall = TextStyle(fontFamily = MakaraFonts.Display, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 14.sp)
    val Title = TextStyle(fontFamily = MakaraFonts.Body, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp)
    val Body = TextStyle(fontFamily = MakaraFonts.Body, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp)
    val BodySmall = TextStyle(fontFamily = MakaraFonts.Body, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp)
    val Label = TextStyle(fontFamily = MakaraFonts.Body, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 18.sp)
    val Overline = TextStyle(fontFamily = MakaraFonts.Body, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.16.em)
}
