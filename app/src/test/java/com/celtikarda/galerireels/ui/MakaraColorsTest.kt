package com.celtikarda.galerireels.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MakaraColorsTest {
    @Test
    fun tokensMatchDesignMd() {
        assertEquals(0xFF120F0D.toInt(), Makara.Black.argb())
        assertEquals(0xFF1E1915.toInt(), Makara.Surface.argb())
        assertEquals(0xFF2B241F.toInt(), Makara.SurfaceHigh.argb())
        assertEquals(0xFF3A302A.toInt(), Makara.Outline.argb())
        assertEquals(0xFFFFB547.toInt(), Makara.Amber.argb())
        assertEquals(0xFF120F0D.toInt(), Makara.OnAmber.argb())
        assertEquals(0xFFF5EDE3.toInt(), Makara.Cream.argb())
        assertEquals(0xFFD8CCBE.toInt(), Makara.CreamSoft.argb())
        assertEquals(0xFFA89B8C.toInt(), Makara.Muted.argb())
        assertEquals(0xFFB3261E.toInt(), Makara.Error.argb())
        assertEquals(0xFFFFFFFF.toInt(), Makara.OnError.argb())
    }

    @Test
    fun textPairsPassWcagAA() {
        val pairs = listOf(
            "OnAmber/Amber" to contrast(Makara.OnAmber, Makara.Amber),
            "Cream/Black" to contrast(Makara.Cream, Makara.Black),
            "Cream/Surface" to contrast(Makara.Cream, Makara.Surface),
            "CreamSoft/Black" to contrast(Makara.CreamSoft, Makara.Black),
            "Muted/Black" to contrast(Makara.Muted, Makara.Black),
            "Muted/Surface" to contrast(Makara.Muted, Makara.Surface),
            "OnError/Error" to contrast(Makara.OnError, Makara.Error),
        )
        pairs.forEach { (name, ratio) -> assertTrue("$name = $ratio", ratio >= 4.5) }
    }

    @Test
    fun legacyAliasesPointToMakara() {
        assertEquals(Makara.Amber, InstaBlue)
        assertEquals(Makara.Amber, LikeRed)
        assertEquals(Makara.Error, DeleteRed)
        assertEquals(Makara.Black, MakaraAppColors.background)
        assertEquals(Makara.Muted, MakaraAppColors.textMuted)
    }
}
