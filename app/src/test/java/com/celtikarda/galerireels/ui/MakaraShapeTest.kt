package com.celtikarda.galerireels.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class MakaraShapeTest {
    @Test
    fun cornerRadiiFollowDesignMd() {
        assertEquals(2, MakaraShape.FRAME_DP)
        assertEquals(4, MakaraShape.SM_DP)
        assertEquals(6, MakaraShape.DEFAULT_DP)
        assertEquals(8, MakaraShape.TILE_DP)
        assertEquals(18, MakaraShape.CONTROL_DP)
        assertEquals(24, MakaraShape.BAR_DP)
    }
}
