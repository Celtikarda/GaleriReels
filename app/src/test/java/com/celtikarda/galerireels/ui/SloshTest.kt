package com.celtikarda.galerireels.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SloshTest {
    private fun run(s: Slosh, seconds: Float, down: Pair<Float, Float>? = null) {
        var t = 0f
        while (t < seconds) {
            down?.let { s.onGravity(it.first, it.second, 0L) }
            s.step(0.016f)
            t += 0.016f
        }
    }

    @Test
    fun upsideDown_liquidFlowsToOtherEnd_andSettles() {
        val s = Slosh()
        run(s, 1f, 0f to 9.8f)
        assertEquals(0f, s.pos, 0.01f)
        run(s, 0.15f, 0f to -9.8f)
        assertTrue("akmaya başlamalı", s.pos > 0.05f)
        assertFalse(s.settled)
        run(s, 4f, 0f to -9.8f)
        assertEquals(1f, s.pos, 0.01f)
        assertTrue(s.settled)
    }

    @Test
    fun tilt_makesSurfaceLevelWithGround() {
        val s = Slosh()
        // Sağ kenar aşağı eğik: yüzey ekranda sağa doğru yükselir (eğim negatif)
        run(s, 3f, 4f to 8f)
        assertEquals(-0.5f, s.slope, 0.05f)
    }

    @Test
    fun lyingFlat_keepsLastDirection() {
        val s = Slosh()
        run(s, 3f, 0f to -9.8f)
        run(s, 3f, 0.3f to 0.5f)
        assertEquals(1f, s.pos, 0.01f)
    }

    @Test
    fun pour_raisesLevel_withSplash() {
        val s = Slosh()
        s.pour(0.4f)
        assertTrue(s.wave > 0.3f)
        run(s, 3f)
        assertEquals(0.4f, s.level, 0.01f)
        assertTrue(s.settled)
    }
}
