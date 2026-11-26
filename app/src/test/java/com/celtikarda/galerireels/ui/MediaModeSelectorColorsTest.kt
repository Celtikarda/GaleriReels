package com.celtikarda.galerireels.ui

import org.junit.Assert.assertTrue
import org.junit.Test

class MediaModeSelectorColorsTest {
    @Test
    fun selectedTabTextReadableOnItsBackground() {
        val c = mediaModeTabColors(selected = true, palette = MakaraAppColors)
        val ratio = contrast(c.text, c.background)
        assertTrue("selected = $ratio", ratio >= 4.5)
    }

    @Test
    fun unselectedTabTextReadableOnCard() {
        val c = mediaModeTabColors(selected = false, palette = MakaraAppColors)
        val ratio = contrast(c.text, MakaraAppColors.card)
        assertTrue("unselected = $ratio", ratio >= 4.5)
    }
}
