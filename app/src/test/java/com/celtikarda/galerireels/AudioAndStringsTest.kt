package com.celtikarda.galerireels

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AudioAndStringsTest {

    @Test
    fun `ses yukseltme kazanci kademeli artar`() {
        assertEquals(0, boostGainMb(0f))
        assertEquals(352, boostGainMb(0.5f)) // %150 ≈ +3.5 dB
        assertEquals(602, boostGainMb(1f)) // %200 ≈ +6 dB
        assertEquals(602, boostGainMb(3f)) // üst sınır %200
        assertTrue(boostGainMb(0.25f) < boostGainMb(0.75f))
    }

    @Test
    fun `yazilarda bozuk kacis dizisi yok`() {
        for (dir in listOf("values", "values-tr")) {
            val text = File("src/main/res/$dir/strings.xml").readText()
            // v1.7'de üretilen Türkçe yazılarda kesme işareti "\x27" olarak kalmıştı ve ekranda öyle görünüyordu
            assertFalse("$dir içinde bozuk kesme işareti var", text.contains("""\x27"""))
        }
    }
}
