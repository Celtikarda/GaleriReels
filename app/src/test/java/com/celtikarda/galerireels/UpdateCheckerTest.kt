package com.celtikarda.galerireels

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {

    @Test
    fun yeniSurumTanınır() {
        assertTrue(UpdateChecker.isNewer("1.3", "1.2"))
        assertTrue(UpdateChecker.isNewer("1.10", "1.9"))
        assertTrue(UpdateChecker.isNewer("1.2.1", "1.2"))
        assertTrue(UpdateChecker.isNewer("2.0", "1.9.9"))
    }

    @Test
    fun ayniYaDaEskiSurumYeniSayilmaz() {
        assertFalse(UpdateChecker.isNewer("1.2", "1.2"))
        assertFalse(UpdateChecker.isNewer("1.2", "1.2.0"))
        assertFalse(UpdateChecker.isNewer("1.1", "1.2"))
        assertFalse(UpdateChecker.isNewer("0.9", "1.0"))
    }
}
