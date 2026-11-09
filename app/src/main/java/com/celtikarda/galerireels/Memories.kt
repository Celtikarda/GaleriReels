package com.celtikarda.galerireels

import com.celtikarda.galerireels.ui.localDate
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/**
 * Geçmiş yıllarda bugün (ya da bu hafta, ±3 gün içinde) çekilmiş videoları bulur.
 */
object Memories {

    private const val WINDOW_DAYS = 3L

    /** Verilen tarihin [today] ile yıl dönümü farkı (gün olarak, 0 = tam bugün); önceki yıl değilse null */
    internal fun anniversaryDistanceDays(date: LocalDate, today: LocalDate): Long? {
        if (date.year >= today.year) return null
        // 29 Şubat'ı artık olmayan yıllarda 28 Şubat'a uyarlar
        val thisYear = date.withYear(today.year)
        val candidates = listOf(
            ChronoUnit.DAYS.between(thisYear, today),
            ChronoUnit.DAYS.between(thisYear.minusYears(1), today),
            ChronoUnit.DAYS.between(thisYear.plusYears(1), today),
        )
        return candidates.minByOrNull { abs(it) }?.let { abs(it) }?.takeIf { it <= WINDOW_DAYS }
    }

    /** Geçmiş yıllarda bu hafta çekilmiş videolar (önce tam bugün olanlar, sonra yeniden eskiye) */
    fun find(videos: List<VideoItem>, today: LocalDate = LocalDate.now()): List<VideoItem> =
        videos
            .mapNotNull { video ->
                val dist = anniversaryDistanceDays(localDate(video.dateMillis), today) ?: return@mapNotNull null
                video to dist
            }
            .sortedWith(compareBy<Pair<VideoItem, Long>> { it.second }.thenByDescending { it.first.dateMillis })
            .map { it.first }

    /** Videonun kaç yıl önce çekildiği */
    fun yearsAgo(video: VideoItem, today: LocalDate = LocalDate.now()): Int =
        (today.year - localDate(video.dateMillis).year).coerceAtLeast(1)

    /** Ana ekranda başlığın altında gösterilecek özet yazı: ör. "1 ve 3 yıl önce bu hafta" */
    fun summaryText(memories: List<VideoItem>, today: LocalDate = LocalDate.now()): String {
        if (memories.isEmpty()) return ""
        val years = memories.map { yearsAgo(it, today) }.distinct().sorted()
        val exactToday = memories.any { anniversaryDistanceDays(localDate(it.dateMillis), today) == 0L }
        val yearsText = when (years.size) {
            1 -> "${years[0]}"
            else -> str(R.string.list_two, years.dropLast(1).joinToString(", "), years.last())
        }
        val whenText = plural(if (exactToday && years.size == 1) R.plurals.memories_today else R.plurals.memories_week, years.last(), yearsText)
        return "$whenText · " + plural(R.plurals.n_videos, memories.size, memories.size)
    }
}
