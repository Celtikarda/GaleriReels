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
        assertTrue("akmaya başlamalı (kaynak boşalıyor)", s.bottom < 0.95f)
        assertFalse(s.settled)
        run(s, 4f, 0f to -9.8f)
        assertEquals(1f, s.pos, 0.01f)
        assertTrue(s.settled)
    }

    @Test
    fun upsideDown_pourDoesNotTeleport() {
        val s = Slosh()
        run(s, 1f, 0f to 9.8f)
        var last = s.pos
        var maxJump = 0f
        var t = 0f
        var sawSplit = false
        while (t < 2f) {
            s.onGravity(0f, -9.8f, 0L)
            s.step(0.016f)
            maxJump = maxOf(maxJump, kotlin.math.abs(s.pos - last))
            if (s.pos in 0.3f..0.7f && s.flow > 0.5f) sawSplit = true
            last = s.pos
            t += 0.016f
        }
        assertTrue("tek karede en fazla %4 akmalı (oldu ${maxJump})", maxJump < 0.04f)
        assertTrue("ortada iki uçta birden sıvı + akış görülmeli", sawSplit)
        assertEquals(1f, s.pos, 0.001f)
        assertEquals(0f, s.bottom, 0.001f)
    }

    @Test
    fun pouredWater_fallsFirst_thenLands_andIsConserved() {
        val s = Slosh()
        s.level = 1f
        s.targetLevel = 1f
        run(s, 1f, 0f to 9.8f)
        var t = 0f
        var firstLand = -1f
        var thinTail = false
        while (t < 3f) {
            s.onGravity(0f, -9.8f, 0L)
            s.step(0.016f)
            t += 0.016f
            assertEquals("su kaybolmamalı", 1f, s.top + s.bottom + s.airShare(), 0.002f)
            if (firstLand < 0f && s.top > 0f) firstLand = t
            // Kaynak azaldıkça akış incelir (Torricelli)
            if (s.bottom in 0.01f..0.1f && s.flow in 0.01f..1f) thinTail = true
        }
        assertTrue("ilk damla düşerek varmalı, anında değil (vardı: $firstLand sn)", firstLand > 0.12f)
        assertTrue("sonda akış incelmeli", thinTail)
        assertTrue(s.drops.isEmpty())
        assertEquals(1f, s.top, 0.001f)
    }

    @Test
    fun pour_takesAboutASecond_notAFlash() {
        val s = Slosh()
        s.level = 1f
        s.targetLevel = 1f
        run(s, 1f, 0f to 9.8f)
        var t = 0f
        while (s.top < 0.999f && t < 5f) {
            s.onGravity(0f, -9.8f, 0L)
            s.step(0.016f)
            t += 0.016f
        }
        assertTrue("dökülme göz takip edebilecek kadar sürmeli (sürdü $t sn)", t in 0.8f..1.8f)
    }

    @Test
    fun boostPoursFirst_thenWhite_andStaysOnTop() {
        val s = Slosh()
        s.level = 1.4f
        s.pour(1.4f)
        run(s, 2f, 0f to 9.8f)
        assertTrue(s.boostPresent)
        var t = 0f
        var sawBoostOnlyArrive = false
        var sawWhiteStream = false
        while (t < 3f) {
            s.onGravity(0f, -9.8f, 0L)
            s.step(0.016f)
            t += 0.016f
            assertEquals("su kaybolmamalı", 1f, s.top + s.bottom + s.airShare(), 0.003f)
            if (s.boostTop > 0f && s.whiteTop == 0f) sawBoostOnlyArrive = true
            if (s.drops.any { it[5] == 0f }) sawWhiteStream = true
        }
        assertTrue("önce yüzeydeki sarı varmalı", sawBoostOnlyArrive)
        assertTrue("sonra beyaz akmalı", sawWhiteStream)
        assertEquals(1f, s.boostTop, 0.002f)
        assertEquals(1f, s.whiteTop, 0.002f)
    }

    @Test
    fun flipBackMidPour_returnsEverything() {
        val s = Slosh()
        s.level = 1f
        s.targetLevel = 1f
        run(s, 1f, 0f to 9.8f)
        run(s, 0.4f, 0f to -9.8f)
        assertTrue(s.top > 0.05f && s.bottom > 0.05f)
        run(s, 4f, 0f to 9.8f)
        assertEquals(1f, s.bottom, 0.002f)
        assertTrue(s.drops.isEmpty())
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
    fun pourBelow100_neverShowsYellow_evenWhenSpringOvershoots() {
        val s = Slosh()
        s.level = 0.8f
        s.pour(1f)
        var maxLevel = 0f
        var t = 0f
        while (t < 2f) {
            s.step(0.016f)
            maxLevel = maxOf(maxLevel, s.level)
            assertFalse("sarı görünmemeli (seviye ${s.level})", s.boostPresent)
            t += 0.016f
        }
        assertTrue("yay biraz taşmalı ki test anlamlı olsun", maxLevel > 1f)
    }

    @Test
    fun boost_appearsAbove100_andDrainsAway() {
        val s = Slosh()
        s.level = 1f
        s.pour(1.4f)
        run(s, 1f)
        assertTrue(s.boostPresent)
        s.pour(1f)
        run(s, 2f)
        assertFalse(s.boostPresent)
    }

    @Test
    fun shake_makesWaves_thatSettle() {
        val s = Slosh()
        s.level = 0.6f
        s.targetLevel = 0.6f
        s.onShake(6f, 0f)
        run(s, 0.2f)
        assertTrue("yüzey dalgalanmalı", s.heights.any { kotlin.math.abs(it) > 0.02f })
        assertFalse(s.settled)
        run(s, 5f)
        assertTrue(s.settled)
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
