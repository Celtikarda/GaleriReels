package com.celtikarda.galerireels

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

class HomeSectionsTest {

    private val dummyUri: Uri by lazy {
        val field = sun.misc.Unsafe::class.java.getDeclaredField("theUnsafe").apply { isAccessible = true }
        (field.get(null) as sun.misc.Unsafe).allocateInstance(android.net.DummyUri::class.java) as Uri
    }

    private fun millis(date: String): Long =
        LocalDate.parse(date).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    private fun item(id: Long, date: String = "2026-11-01", durationMs: Long = 60_000, isPhoto: Boolean = false) =
        VideoItem(
            id = id, key = "VID_$id|1000", name = "VID_$id.mp4", sizeBytes = 1_000, favorite = false, uri = dummyUri,
            dateMillis = millis(date), durationMs = durationMs, albumId = "DCIM/Camera", albumName = "Camera",
            albumPath = "DCIM/Camera", mimeType = null, width = 0, height = 0, isPhoto = isPhoto,
        )

    @Test
    fun monthReels_groupsNewestFirst() {
        val a = item(1, "2026-11-03"); val b = item(2, "2026-11-20"); val c = item(3, "2026-09-01")
        val reels = monthReels(listOf(a, c, b), ZoneOffset.UTC)
        assertEquals(listOf(YearMonth.of(2026, 11), YearMonth.of(2026, 9)), reels.map { it.month })
        assertEquals(listOf(b, a), reels[0].items)
        assertEquals(listOf(c), reels[1].items)
    }

    @Test
    fun monthReels_respectsLimit() {
        val videos = (0 until 15).map { i -> item(i.toLong(), YearMonth.of(2025, 1).plusMonths(i.toLong()).atDay(5).toString()) }
        val reels = monthReels(videos, ZoneOffset.UTC, limit = 12)
        assertEquals(12, reels.size)
        assertEquals(YearMonth.of(2026, 3), reels.first().month)
    }

    @Test
    fun monthReels_photosHaveNoDuration() {
        val photo = item(1, "2026-10-10", durationMs = 0, isPhoto = true)
        val reels = monthReels(listOf(photo), ZoneOffset.UTC)
        assertEquals(listOf(photo), reels.single().items)
    }

    @Test
    fun mostWatched_sizesByRank() {
        val views = listOf(7, 24, 9, 15, 11)
        val videos = views.indices.map { item(it.toLong()) }
        val tiles = mostWatched(videos, { views[it.id.toInt()] })
        assertEquals(listOf(24, 15, 11, 9, 7), tiles.map { it.views })
        assertEquals(listOf(TileSize.BIG, TileSize.TALL, TileSize.WIDE, TileSize.SMALL, TileSize.SMALL), tiles.map { it.size })
        assertTrue(tiles[0].loud)
        assertFalse(tiles[3].loud)
    }

    @Test
    fun mostWatched_emptyWhenNoViews() {
        val videos = (1L..3L).map { item(it) }
        assertEquals(emptyList<MosaicTile>(), mostWatched(videos, { 0 }))
    }

    @Test
    fun yearsOf_singleYearHidden() {
        assertEquals(emptyList<Int>(), yearsOf(listOf(item(1, "2026-01-01"), item(2, "2026-05-01")), ZoneOffset.UTC))
        assertEquals(listOf(2026, 2024), yearsOf(listOf(item(1, "2024-03-01"), item(2, "2026-05-01")), ZoneOffset.UTC))
    }

    @Test
    fun frameNumber_pads() {
        assertEquals("0007", frameNumber(7))
        assertEquals("0214", frameNumber(214))
        assertEquals("12345", frameNumber(12345))
    }

    @Test
    fun packMosaic_fillsThreeColumnsDensely() {
        val cells = packMosaic(listOf(TileSize.BIG, TileSize.TALL, TileSize.WIDE, TileSize.SMALL, TileSize.SMALL))
        assertEquals(listOf(MosaicCell(0, 0), MosaicCell(2, 0), MosaicCell(0, 2), MosaicCell(2, 2), MosaicCell(0, 3)), cells)
        assertEquals(4, mosaicRows(listOf(TileSize.BIG, TileSize.TALL, TileSize.WIDE, TileSize.SMALL, TileSize.SMALL), cells))
    }

    @Test
    fun mostLiked_ordersByLikesAndDropsUnliked() {
        val likes = listOf(0, 3, 9, 1)
        val videos = likes.indices.map { item(it.toLong()) }
        assertEquals(listOf(2L, 1L, 3L), mostLiked(videos, { likes[it.id.toInt()] }).map { it.id })
        assertEquals(listOf(2L), mostLiked(videos, { likes[it.id.toInt()] }, limit = 1).map { it.id })
    }
}
