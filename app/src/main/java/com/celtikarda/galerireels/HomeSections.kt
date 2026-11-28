package com.celtikarda.galerireels

import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

/*
 * Makara ana ekranının hesapları. Android'e dokunmaz, JVM testleriyle denenir (HomeSectionsTest).
 */

/** Ay makarası: bir aydaki videolar, en yeni önce */
data class MonthReel(val month: YearMonth, val items: List<VideoItem>)

/** Videoları aylara ayırır: en yeni ay önce, ay içinde en yeni video önce; boş ay olmaz. */
fun monthReels(videos: List<VideoItem>, zone: ZoneId = ZoneId.systemDefault(), limit: Int = 12): List<MonthReel> =
    videos.sortedByDescending { it.dateMillis }
        .groupBy { YearMonth.from(Instant.ofEpochMilli(it.dateMillis).atZone(zone)) }
        .entries
        .sortedByDescending { it.key }
        .take(limit)
        .map { MonthReel(it.key, it.value) }

/** Mozaikte bir kutunun kapladığı sütun × satır */
enum class TileSize(val cols: Int, val rows: Int) { BIG(2, 2), TALL(1, 2), WIDE(2, 1), SMALL(1, 1) }

/** "En çok sarılanlar" mozaiğinde bir kutu */
data class MosaicTile(val video: VideoItem, val views: Int, val size: TileSize) {
    /** Çok izlenen kutunun sayacı kehribar renkle "bağırır" */
    val loud: Boolean get() = views >= 10
}

/** İzlenen videolar çoktan aza; 1. büyük, 2. uzun, 3. geniş, gerisi küçük kutu. Hiç izlenen yoksa boş. */
fun mostWatched(videos: List<VideoItem>, viewsOf: (VideoItem) -> Int, limit: Int = 8): List<MosaicTile> =
    videos.map { it to viewsOf(it) }
        .filter { it.second > 0 }
        .sortedByDescending { it.second }
        .take(limit)
        .mapIndexed { i, (video, views) ->
            val size = when (i) {
                0 -> TileSize.BIG
                1 -> TileSize.TALL
                2 -> TileSize.WIDE
                else -> TileSize.SMALL
            }
            MosaicTile(video, views, size)
        }

/** Kütüphanedeki yıllar, yeniden eskiye. Tek yıl varsa yıl şeridi gereksiz: boş liste. */
fun yearsOf(videos: List<VideoItem>, zone: ZoneId = ZoneId.systemDefault()): List<Int> {
    val years = videos.map { Instant.ofEpochMilli(it.dateMillis).atZone(zone).year }.distinct().sortedDescending()
    return if (years.size < 2) emptyList() else years
}

/** Film karesi numarası: "0007", "0214"; 9999'u geçen sayı dolgusuz */
fun frameNumber(n: Int): String = String.format(java.util.Locale.ROOT, "%04d", n)

/** Mozaikte bir kutunun sol üst hücresi */
data class MosaicCell(val col: Int, val row: Int)

/** Kutuları [cols] sütunlu ızgaraya sırayla, boş kalan ilk yere yerleştirir (yoğun dizilim). */
fun packMosaic(sizes: List<TileSize>, cols: Int = 3): List<MosaicCell> {
    val taken = HashSet<Pair<Int, Int>>()
    fun fits(c: Int, r: Int, s: TileSize) =
        c + s.cols <= cols && (0 until s.cols).all { dc -> (0 until s.rows).all { dr -> (c + dc to r + dr) !in taken } }
    return sizes.map { s ->
        var cell: MosaicCell? = null
        var r = 0
        while (cell == null) {
            for (c in 0 until cols) if (fits(c, r, s)) { cell = MosaicCell(c, r); break }
            if (cell == null) r++
        }
        for (dc in 0 until s.cols) for (dr in 0 until s.rows) taken += (cell.col + dc to cell.row + dr)
        cell
    }
}

/** Yerleştirilmiş mozaiğin toplam satır sayısı */
fun mosaicRows(sizes: List<TileSize>, cells: List<MosaicCell>): Int =
    sizes.zip(cells).maxOfOrNull { (s, c) -> c.row + s.rows } ?: 0

/** Beğenilen videolar, en çok beğenilen önce (Sevdiklerim şeridi) */
fun mostLiked(videos: List<VideoItem>, likesOf: (VideoItem) -> Int, limit: Int = 20): List<VideoItem> =
    videos.map { it to likesOf(it) }.filter { it.second > 0 }.sortedByDescending { it.second }.take(limit).map { it.first }
