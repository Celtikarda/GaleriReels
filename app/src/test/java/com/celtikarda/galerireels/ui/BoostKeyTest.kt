package com.celtikarda.galerireels.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class BoostKeyTest {
    @Test
    fun up_goesIn20PercentSteps_andStopsAt200() {
        var boost = 0f
        val seen = mutableListOf<Int>()
        repeat(7) {
            boost = boostKeyUp(boost)
            seen += (boost * 100).toInt()
        }
        assertEquals(listOf(20, 40, 60, 80, 100, 100, 100), seen)
    }

    @Test
    fun down_goesBackTo100_andStaysThere() {
        var boost = 1f
        val seen = mutableListOf<Int>()
        repeat(6) {
            boost = boostKeyDown(boost)
            seen += Math.round(boost * 100)
        }
        assertEquals(listOf(80, 60, 40, 20, 0, 0), seen)
    }

    @Test
    fun swipeLevelInBetween_snapsToNextStep() {
        // Kaydırmayla %130 olduysa: açma %140, kısma %120
        assertEquals(0.4f, boostKeyUp(0.3f), 0.001f)
        assertEquals(0.2f, boostKeyDown(0.3f), 0.001f)
        // Basamağa çok yakın kayan sayı bir basamak atlamasın
        assertEquals(0.6f, boostKeyUp(0.39999f), 0.001f)
        assertEquals(0.2f, boostKeyDown(0.40001f), 0.001f)
    }
}
