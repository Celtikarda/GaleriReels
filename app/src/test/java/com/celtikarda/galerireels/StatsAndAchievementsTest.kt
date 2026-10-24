package com.celtikarda.galerireels

import android.net.Uri
import com.celtikarda.galerireels.ui.formatDuration
import com.celtikarda.galerireels.ui.formatMonth
import com.celtikarda.galerireels.ui.formatWatchTime
import com.celtikarda.galerireels.ui.relativeDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

class StatsAndAchievementsTest {

    @Test
    fun `merge en yuksek izlenmeyi ve en son degisen begeniyi korur`() {
        val older = VideoStat(liked = true, likeChangedAt = 1000L, views = 8, lastViewedAt = 2000L)
        val newer = VideoStat(liked = false, likeChangedAt = 1500L, views = 3, lastViewedAt = 5000L)

        val merged = StatsStore.merge(older, newer)
        assertFalse(merged.liked)
        assertEquals(1500L, merged.likeChangedAt)
        assertEquals(8, merged.views)
        assertEquals(5000L, merged.lastViewedAt)
    }

    @Test
    fun `bestStreak ve currentStreak ust uste gunleri dogru hesaplar`() {
        val days = setOf(
            "2026-10-01",
            "2026-10-02",
            "2026-10-03",
            "2026-10-04",
            "2026-10-10",
            "2026-10-22",
            "2026-10-23",
            "2026-10-24",
        )
        assertEquals(4, StatsStore.computeBestStreak(days))
        assertEquals(3, StatsStore.computeCurrentStreak(days, LocalDate.of(2026, 10, 24)))
        assertEquals(3, StatsStore.computeCurrentStreak(days, LocalDate.of(2026, 10, 25)))
        assertEquals(0, StatsStore.computeCurrentStreak(days, LocalDate.of(2026, 10, 26)))
    }

    @Test
    fun `json yazma ve okuma tum alanlari korur`() {
        val original = SavedData(
            videos = mapOf(
                "vid1.mp4|1024" to VideoStat(liked = true, likeChangedAt = 1700000000L, views = 12, lastViewedAt = 1700000500L),
            ),
            days = mapOf(
                "2026-10-20" to mapOf("vid1.mp4|1024" to 7),
                "2026-10-21" to mapOf("vid1.mp4|1024" to 5),
            ),
            activeDays = setOf("2026-10-20", "2026-10-21"),
            cleaned = mapOf("2026-10-21" to 52_428_800L),
            trash = listOf(TrashedVideo(11L, "old.mp4|500", "old.mp4", 500L, 1700000100L)),
            achievements = mapOf("first_view" to 1700000050L),
        )

        val json = StatsStore.toJson(original)
        val parsed = StatsStore.parse(json)

        assertEquals(original.videos, parsed.videos)
        assertEquals(original.days, parsed.days)
        assertEquals(original.activeDays, parsed.activeDays)
        assertEquals(original.cleaned, parsed.cleaned)
        assertEquals(original.trash, parsed.trash)
        assertEquals(original.achievements, parsed.achievements)
    }

    @Test
    fun `aylik ozet en cok izlenen videolari ve klasoru dogru hesaplar`() {
        val v1 = sampleVideo(1L, "kedi.mp4", durationMs = 30_000L, albumId = "cam", albumName = "Camera")
        val v2 = sampleVideo(2L, "konser.mp4", durationMs = 60_000L, albumId = "wa", albumName = "WhatsApp Video")
        val v3 = sampleVideo(3L, "deniz.mp4", durationMs = 20_000L, albumId = "cam", albumName = "Camera")
        val folders = listOf(
            Album("cam", "Camera", 2, v1),
            Album("wa", "WhatsApp Video", 1, v2),
        )
        val views = mapOf(
            v1.key to 6,
            v2.key to 9,
            v3.key to 5,
        )

        val recap = buildMonthlyRecap(
            month = YearMonth.of(2026, 10),
            allVideos = listOf(v1, v2, v3),
            folders = folders,
            viewsByKey = views,
            activeDays = 8,
            cleanedBytes = 150L * 1024 * 1024,
            newLikes = 4,
            lastViewedAtOf = { 1000L },
            albumNameOf = { it.albumName ?: "Video" },
        )

        assertTrue(recap.hasActivity)
        assertEquals(20, recap.totalViews)
        assertEquals(3, recap.uniqueVideos)
        // 6*30sn + 9*60sn + 5*20sn = 180 + 540 + 100 = 820 sn
        assertEquals(820_000L, recap.watchTimeMs)
        assertEquals(v2.id, recap.topVideos.first().video.id)
        assertEquals(9, recap.topVideos.first().views)
        // Camera klasörü toplam 6 + 5 = 11 izlenme ile WhatsApp Video'yu (9) geçer
        assertNotNull(recap.topAlbum)
        assertEquals("Camera", recap.topAlbum?.name)
        assertEquals(11, recap.topAlbum?.views)
        assertEquals(2, recap.topAlbum?.watchedVideos)
    }

    @Test
    fun `basarimlar kosullar saglaninca acilir`() {
        val input = AchievementInput(
            totalViews = 120,
            watchedVideos = 55,
            maxViewsOfOne = 10,
            likes = 12,
            favorites = 3,
            foldersVisited = 5,
            totalFolders = 5,
            cleanedBytes = 200L * 1024 * 1024,
            bestStreak = 7,
        )
        val unlockedIds = achievements.filter { it.isUnlocked(input) }.map { it.id }.toSet()

        assertTrue("first_view" in unlockedIds)
        assertTrue("views_100" in unlockedIds)
        assertFalse("views_1000" in unlockedIds)
        assertTrue("watched_50" in unlockedIds)
        assertTrue("repeat_10" in unlockedIds)
        assertTrue("likes_10" in unlockedIds)
        assertFalse("favorites_10" in unlockedIds)
        assertTrue("folders_all" in unlockedIds)
        assertTrue("clean_100mb" in unlockedIds)
        assertTrue("streak_7" in unlockedIds)
    }

    @Test
    fun `sure ve tarih bicimlendirme dogru calisir`() {
        assertEquals("0:45", formatDuration(45_000L))
        assertEquals("1:05:09", formatDuration(3_909_000L))
        assertEquals("45 sn", formatWatchTime(45_000L))
        assertEquals("14 dk", formatWatchTime(14 * 60_000L))
        assertEquals("2 sa 15 dk", formatWatchTime((2 * 3600 + 15 * 60) * 1000L))
        assertEquals("Ekim 2026", formatMonth(YearMonth.of(2026, 10)))

        val today = LocalDate.of(2026, 10, 24)
        val fiveYearsAgo = today.minusYears(5).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        assertEquals("tam 5 yıl önce bugün", relativeDate(fiveYearsAgo, today))
    }

    @Test
    fun `tarihte bugun gecmis yillardaki ayni hafta videolarini bulur`() {
        val today = LocalDate.of(2026, 10, 24)
        fun millisOf(y: Int, m: Int, d: Int) =
            LocalDate.of(y, m, d).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

        val exactOneYear = sampleVideo(1L, "a.mp4", 10_000L, "cam", "Camera").copy(dateMillis = millisOf(2025, 10, 24))
        val twoDaysOffThreeYears = sampleVideo(2L, "b.mp4", 10_000L, "cam", "Camera").copy(dateMillis = millisOf(2023, 10, 22))
        val sameYearIgnored = sampleVideo(3L, "c.mp4", 10_000L, "cam", "Camera").copy(dateMillis = millisOf(2026, 10, 23))
        val farMonthIgnored = sampleVideo(4L, "d.mp4", 10_000L, "cam", "Camera").copy(dateMillis = millisOf(2024, 5, 10))

        val found = Memories.find(listOf(twoDaysOffThreeYears, sameYearIgnored, exactOneYear, farMonthIgnored), today)
        // Önce tam yıl dönümü olan (1L), sonra 2 gün fark olan (2L)
        assertEquals(listOf(1L, 2L), found.map { it.id })
        assertEquals("1 ve 3 yıl önce bu hafta · 2 video", Memories.summaryText(found, today))
    }

    private val dummyUri: Uri by lazy {
        val field = sun.misc.Unsafe::class.java.getDeclaredField("theUnsafe").apply { isAccessible = true }
        (field.get(null) as sun.misc.Unsafe).allocateInstance(android.net.DummyUri::class.java) as Uri
    }

    private fun sampleVideo(id: Long, name: String, durationMs: Long, albumId: String, albumName: String) = VideoItem(
        id = id,
        key = "$name|1000",
        name = name,
        sizeBytes = 1000L,
        favorite = false,
        uri = dummyUri,
        dateMillis = 1700000000000L,
        durationMs = durationMs,
        albumId = albumId,
        albumName = albumName,
        albumPath = "Movies/$albumName",
        mimeType = "video/mp4",
        width = 1080,
        height = 1920,
    )
}
