package com.celtikarda.galerireels.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class MakaraTypeTest {
    @Test
    fun allMaterialStylesUseBodyFont() {
        val t = MakaraTypography
        listOf(
            t.displayLarge, t.displayMedium, t.displaySmall,
            t.headlineLarge, t.headlineMedium, t.headlineSmall,
            t.titleLarge, t.titleMedium, t.titleSmall,
            t.bodyLarge, t.bodyMedium, t.bodySmall,
            t.labelLarge, t.labelMedium, t.labelSmall,
        ).forEachIndexed { i, style -> assertEquals("style #$i", MakaraFonts.Body, style.fontFamily) }
    }
}
