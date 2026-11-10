package com.celtikarda.galerireels

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class SmartCleanupTest {

    private val dummyUri: Uri by lazy {
        val field = sun.misc.Unsafe::class.java.getDeclaredField("theUnsafe").apply { isAccessible = true }
        (field.get(null) as sun.misc.Unsafe).allocateInstance(android.net.DummyUri::class.java) as Uri
    }

    private fun item(
        id: Long,
        name: String = "VID_$id.mp4",
        path: String = "DCIM/Camera",
        album: String? = "Camera",
        durationMs: Long = 60_000,
        size: Long = 1_000,
        isPhoto: Boolean = false,
    ) = VideoItem(
        id = id, key = "$name|$size", name = name, sizeBytes = size, favorite = false, uri = dummyUri,
        dateMillis = 0, durationMs = durationMs, albumId = path, albumName = album, albumPath = path,
        mimeType = null, width = 0, height = 0, isPhoto = isPhoto,
    )

    @Test
    fun `cok kisa video 3 saniyenin altidir, suresi bilinmeyen ve fotograf degildir`() {
        assertTrue(SmartCleanup.isShortVideo(item(1, durationMs = 1_200)))
        assertFalse(SmartCleanup.isShortVideo(item(2, durationMs = 3_000)))
        assertFalse(SmartCleanup.isShortVideo(item(3, durationMs = 0)))
        assertFalse(SmartCleanup.isShortVideo(item(4, durationMs = 500, isPhoto = true)))
    }

    @Test
    fun `ekran goruntusu ve kaydi klasor ya da dosya adindan anlasilir`() {
        assertTrue(SmartCleanup.isScreenCapture(item(1, path = "DCIM/Screenshots", album = "Screenshots", isPhoto = true)))
        assertTrue(SmartCleanup.isScreenCapture(item(2, path = "DCIM/ScreenRecord", album = "ScreenRecord")))
        assertTrue(SmartCleanup.isScreenCapture(item(3, name = "Screen_Recording_20251101.mp4", path = "Movies", album = "Movies")))
        assertFalse(SmartCleanup.isScreenCapture(item(4)))
    }

    @Test
    fun `whatsapp klasoru ya da WA dosya adi`() {
        assertTrue(SmartCleanup.isWhatsApp(item(1, path = "Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Video", album = "WhatsApp Video")))
        assertTrue(SmartCleanup.isWhatsApp(item(2, name = "VID-20251015-WA0005.mp4", path = "Download", album = "Download")))
        assertFalse(SmartCleanup.isWhatsApp(item(3, name = "WAVES.mp4")))
    }

    @Test
    fun `kopyalar once, sonra buyukten kucuge`() {
        val a = item(1, durationMs = 1_000, size = 100)
        val b = item(2, durationMs = 1_000, size = 900)
        val c = item(3, durationMs = 1_000, size = 50)
        val groups = SmartCleanup.quickGroups(listOf(a, b, c), duplicateIds = setOf(3))
        assertEquals(listOf(3L, 2L, 1L), groups.getValue(SmartCategory.SHORT).map { it.id })
    }

    @Test
    fun `keskin desen yuksek, bulanik ve duz goruntu dusuk laplace verir`() {
        val w = 64
        val h = 64
        val sharp = IntArray(w * h) { i -> if (((i % w) / 4 + (i / w) / 4) % 2 == 0) 30 else 220 }
        // Yumuşak yatay geçiş: kontrast var ama kenar yok
        val blurry = IntArray(w * h) { i -> 30 + (i % w) * 3 }
        val flat = IntArray(w * h) { 128 + Random(it).nextInt(-1, 2) }

        val s = SmartCleanup.sharpnessOf(sharp, w, h)
        val b = SmartCleanup.sharpnessOf(blurry, w, h)
        val f = SmartCleanup.sharpnessOf(flat, w, h)
        assertFalse(SmartCleanup.isBlurry(s))
        assertTrue(SmartCleanup.isBlurry(b))
        // Düz görüntü bulanık sayılmaz (gökyüzü, duvar)
        assertFalse(SmartCleanup.isBlurry(f))
        assertFalse(SmartCleanup.isBlurry(null))
    }
}
