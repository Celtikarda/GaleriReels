package com.celtikarda.galerireels.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class FolderStatusTintTest {
    @Test
    fun folderSetAndMissingLookDifferent() {
        val ratio = contrast(folderStatusTint(hasFolder = true, palette = MakaraAppColors), folderStatusTint(hasFolder = false, palette = MakaraAppColors))
        org.junit.Assert.assertTrue("set vs missing = $ratio", ratio >= 1.4)
    }

    @Test
    fun missingFolderIsTheAmberWarning() {
        assertEquals(Makara.Amber, folderStatusTint(hasFolder = false, palette = MakaraAppColors))
    }
}
