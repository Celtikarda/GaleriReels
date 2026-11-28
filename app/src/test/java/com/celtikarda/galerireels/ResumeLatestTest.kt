package com.celtikarda.galerireels

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ResumeLatestTest {

    private val dummyUri: Uri by lazy {
        val field = sun.misc.Unsafe::class.java.getDeclaredField("theUnsafe").apply { isAccessible = true }
        (field.get(null) as sun.misc.Unsafe).allocateInstance(android.net.DummyUri::class.java) as Uri
    }

    private fun item(key: String) = VideoItem(
        id = key.hashCode().toLong(), key = key, name = "$key.mp4", sizeBytes = 1_000, favorite = false, uri = dummyUri,
        dateMillis = 0, durationMs = 600_000, albumId = "DCIM/Camera", albumName = "Camera", albumPath = "DCIM/Camera",
        mimeType = null, width = 0, height = 0,
    )

    @Test
    fun latestResume_picksNewestSaved() {
        val a = item("a"); val b = item("b")
        val entries = mapOf("a" to (30_000L to 100L), "b" to (90_000L to 200L))
        assertEquals(b to 90_000L, latestResume(entries, mapOf("a" to a, "b" to b)))
    }

    @Test
    fun latestResume_skipsMissingVideo() {
        val a = item("a")
        val entries = mapOf("a" to (30_000L to 100L), "gone" to (90_000L to 200L))
        assertEquals(a to 30_000L, latestResume(entries, mapOf("a" to a)))
        assertNull(latestResume(entries, emptyMap()))
    }
}
