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

class MakaraSchemeNoDefaultPurpleTest {
    private val s = makaraColorScheme()

    @Test
    fun menuAndDialogSurfacesAreMakara() {
        val makaraSurfaces = setOf(Makara.Black, Makara.Surface, Makara.SurfaceHigh)
        listOf(
            "surfaceContainerLowest" to s.surfaceContainerLowest,
            "surfaceContainerLow" to s.surfaceContainerLow,
            "surfaceContainer" to s.surfaceContainer,
            "surfaceContainerHigh" to s.surfaceContainerHigh,
            "surfaceContainerHighest" to s.surfaceContainerHighest,
            "surfaceBright" to s.surfaceBright,
            "surfaceDim" to s.surfaceDim,
            "primaryContainer" to s.primaryContainer,
            "secondaryContainer" to s.secondaryContainer,
        ).forEach { (name, c) -> org.junit.Assert.assertTrue(name, c in makaraSurfaces) }
    }

    @Test
    fun accentRolesAreMakara() {
        val accents = setOf(Makara.Amber, Makara.CreamSoft, Makara.Cream)
        org.junit.Assert.assertTrue("secondary", s.secondary in accents)
        org.junit.Assert.assertTrue("tertiary", s.tertiary in accents)
    }

    @Test
    fun containerTextReadable() {
        listOf(
            "onSecondaryContainer" to contrast(s.onSecondaryContainer, s.secondaryContainer),
            "onPrimaryContainer" to contrast(s.onPrimaryContainer, s.primaryContainer),
            "onSecondary" to contrast(s.onSecondary, s.secondary),
            "onTertiary" to contrast(s.onTertiary, s.tertiary),
        ).forEach { (name, r) -> org.junit.Assert.assertTrue("$name = $r", r >= 4.5) }
    }
}
