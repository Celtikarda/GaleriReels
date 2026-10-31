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
    fun `begeni sayisi kaydedilir ve eski kayitlardaki begeni bir sayilir`() {
        val counted = VideoStat(liked = true, likes = 5, likeChangedAt = 1000L)
        val parsed = StatsStore.parse(StatsStore.toJson(SavedData(
            videos = mapOf("a.mp4|1" to counted),
            days = emptyMap(), activeDays = emptySet(), cleaned = emptyMap(), trash = emptyList(),
            achievements = emptyMap(), hiddenAlbums = emptySet(),
            mediaMode = null, themeMode = null, leftHoldAction = null, rightHoldAction = null,
        )))
        assertEquals(5, parsed.videos.getValue("a.mp4|1").likes)

        // 1.3 öncesi dosya: "likes" alanı yok
        val old = StatsStore.parse("""{"videos":{"b.mp4|2":{"liked":true,"likeChangedAt":1000,"views":3}}}""")
        assertEquals(1, old.videos.getValue("b.mp4|2").likes)

        val merged = StatsStore.merge(counted, VideoStat(liked = true, likes = 7, likeChangedAt = 2000L))
        assertEquals(7, merged.likes)
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
            trash = listOf(
                TrashedVideo(11L, "old.mp4|500", "old.mp4", 500L, 1700000100L, isPhoto = false),
                TrashedVideo(12L, "img.jpg|250", "img.jpg", 250L, 1700000200L, isPhoto = true),
            ),
            achievements = mapOf("first_view" to 1700000050L),
            hiddenAlbums = setOf("whatsapp_bucket", "screen_recorder_bucket"),
            mediaMode = MediaMode.ALL,
            themeMode = ThemeMode.LIGHT,
            leftHoldAction = EdgeHoldAction.REWIND,
            rightHoldAction = EdgeHoldAction.FAST_FORWARD,
        )

        val json = StatsStore.toJson(original)
        val parsed = StatsStore.parse(json)

        assertEquals(original.videos, parsed.videos)
        assertEquals(original.days, parsed.days)
        assertEquals(original.activeDays, parsed.activeDays)
        assertEquals(original.cleaned, parsed.cleaned)
        assertEquals(original.trash, parsed.trash)
        assertEquals(original.achievements, parsed.achievements)
        assertEquals(original.hiddenAlbums, parsed.hiddenAlbums)
        assertEquals(MediaMode.ALL, parsed.mediaMode)
        assertEquals(ThemeMode.LIGHT, parsed.themeMode)
        assertEquals(EdgeHoldAction.REWIND, parsed.leftHoldAction)
        assertEquals(EdgeHoldAction.FAST_FORWARD, parsed.rightHoldAction)
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
            watchedVideos = 55,
            likes = 12,
            favorites = 3,
            foldersVisited = 5,
            totalFolders = 5,
            cleanedBytes = 200L * 1024 * 1024,
        )
        val unlockedIds = achievements.filter { it.isUnlocked(input) }.map { it.id }.toSet()

        assertEquals(8, achievements.size)
        assertTrue("watched_50" in unlockedIds)
        assertFalse("watched_250" in unlockedIds)
        assertTrue("likes_10" in unlockedIds)
        assertFalse("favorites_10" in unlockedIds)
        assertTrue("folders_all" in unlockedIds)
        assertTrue("clean_100mb" in unlockedIds)
        assertFalse("clean_1gb" in unlockedIds)
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

    @Test
    fun `dHash algisal ozet benzer kareleri eslestirir ve kopyalari ayirir`() {
        // Soldan sağa aydınlanan 9x8 kare
        val frameA = IntArray(72) { idx -> (idx % 9) * 25 }
        // Hafif sıkıştırma gürültüsü eklenmiş benzer kare
        val frameB = IntArray(72) { idx -> (idx % 9) * 25 + if (idx % 7 == 0) 2 else -1 }
        // Tam tersi (soldan sağa kararan) farklı kare
        val frameDifferent = IntArray(72) { idx -> (8 - (idx % 9)) * 25 }

        val hashA = DuplicateFinder.computeDHashFromLuminance(frameA)
        val hashB = DuplicateFinder.computeDHashFromLuminance(frameB)
        val hashDiff = DuplicateFinder.computeDHashFromLuminance(frameDifferent)

        assertTrue(DuplicateFinder.hammingDistance(hashA, hashB) <= DuplicateFinder.MAX_HAMMING_DISTANCE)
        assertTrue(DuplicateFinder.hammingDistance(hashA, hashDiff) > DuplicateFinder.MAX_HAMMING_DISTANCE)

        val original = sampleVideo(10L, "orijinal.mp4", 15_000L, "cam", "Camera").copy(sizeBytes = 8_000_000L)
        val whatsappCopy = sampleVideo(11L, "VID-WA001.mp4", 15_100L, "wa", "WhatsApp").copy(sizeBytes = 2_500_000L)
        val unrelated = sampleVideo(12L, "baska.mp4", 15_200L, "cam", "Camera").copy(sizeBytes = 5_000_000L)

        val extras = DuplicateFinder.selectExtraCopies(
            candidates = listOf(original, whatsappCopy, unrelated),
            hashes = mapOf(
                original.key to hashA,
                whatsappCopy.key to hashB,
                unrelated.key to hashDiff,
            ),
            keepKeys = emptySet(),
        )
        // Yüksek boyutlu/orijinal olan korunur, düşük boyutlu WhatsApp kopyası fazlalık olarak döner
        assertEquals(listOf(11L), extras.map { it.id })

        // Seri çekim / benzer fotoğraflar için selectExtraPhotoCopies testi
        val photo1 = original.copy(id = 20L, key = "IMG_001.jpg|4000000", name = "IMG_001.jpg", sizeBytes = 4_000_000L, durationMs = 0L, isPhoto = true, dateMillis = 1700000000000L)
        val photo2Burst = original.copy(id = 21L, key = "IMG_002.jpg|3800000", name = "IMG_002.jpg", sizeBytes = 3_800_000L, durationMs = 0L, isPhoto = true, dateMillis = 1700000002000L)
        val photoDifferent = original.copy(id = 22L, key = "IMG_003.jpg|3900000", name = "IMG_003.jpg", sizeBytes = 3_900_000L, durationMs = 0L, isPhoto = true, dateMillis = 1700000003000L)

        val photoExtras = DuplicateFinder.selectExtraPhotoCopies(
            candidates = listOf(photo1, photo2Burst, photoDifferent),
            hashes = mapOf(
                photo1.key to hashA,
                photo2Burst.key to hashB,
                photoDifferent.key to hashDiff,
            ),
            keepKeys = emptySet(),
        )
        assertEquals(listOf(21L), photoExtras.map { it.id })
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
