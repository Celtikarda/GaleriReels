package com.celtikarda.galerireels.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.pow

class VideoZoomTest {
    private val width = 2392f
    private val height = 1080f

    @Test
    fun fillScale_landscapeVideo_growsToScreenWidth() {
        assertEquals(width / (height * 1.5f), fillScale(1.5f, width, height), 0.001f)
    }

    @Test
    fun fillScale_portraitVideo_growsToScreenWidth() {
        val ratio = 9f / 16f
        assertEquals(width / (height * ratio), fillScale(ratio, width, height), 0.001f)
    }

    @Test
    fun fillScale_unknownRatio_isOne() {
        assertEquals(1f, fillScale(0f, width, height), 0f)
    }

    @Test
    fun zoomDelta_followsFingersExactly() {
        val ratio = 9f / 16f
        val maxScale = fillScale(ratio, width, height)
        val zoom = zoomDelta(1.5f, ratio, width, height)
        assertEquals(1.5f, maxScale.pow(zoom), 0.001f)
    }

    @Test
    fun zoomDelta_videoAlreadyFillsPage_doesNothing() {
        assertEquals(0f, zoomDelta(1.5f, width / height, width, height), 0f)
    }

    @Test
    fun zoomDelta_unknownRatio_doesNothing() {
        assertEquals(0f, zoomDelta(1.5f, 0f, width, height), 0f)
    }

    @Test
    fun snapZoom_snapsOnlyNearEnds() {
        assertEquals(0f, snapZoom(0.03f), 0f)
        assertEquals(1f, snapZoom(0.97f), 0f)
        assertEquals(0.5f, snapZoom(0.5f), 0f)
    }
}
