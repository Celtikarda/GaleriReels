package com.celtikarda.galerireels.ui

import com.celtikarda.galerireels.ThemeMode
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class MakaraThemeTest {
    @Test
    fun schemeTextPairsReadable() {
        val s = makaraColorScheme()
        listOf(
            "onPrimary/primary" to contrast(s.onPrimary, s.primary),
            "onSurface/surface" to contrast(s.onSurface, s.surface),
            "onBackground/background" to contrast(s.onBackground, s.background),
            "onError/error" to contrast(s.onError, s.error),
        ).forEach { (name, ratio) -> assertTrue("$name = $ratio", ratio >= 4.5) }
    }

    @Test
    fun savedLightOrSystemThemeStillOpensMakara() {
        ThemeMode.entries.forEach { mode -> assertSame(mode.name, MakaraAppColors, paletteFor(mode)) }
    }
}
