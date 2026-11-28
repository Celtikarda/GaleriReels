package com.celtikarda.galerireels.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeTabTest {
    @Test
    fun onlyFlowOpensThePlayer() {
        assertEquals(4, HomeTab.entries.size)
        assertTrue(HomeTab.FLOW.opensPlayer())
        assertFalse(HomeTab.REEL.opensPlayer())
        assertFalse(HomeTab.LOVED.opensPlayer())
        assertFalse(HomeTab.YOU.opensPlayer())
    }
}
