package com.celtikarda.galerireels

import org.junit.Assert.assertEquals
import org.junit.Test

class AchievementCountTest {
    @Test
    fun unlockedCount_ignoresRemovedIds() {
        // 1.4'te kaldırılan rozetlerin id'leri eski kayıtlarda kalır; sayılmamalı ("13 / 8 açıldı" hatası)
        val saved = mapOf(achievements[0].id to 1L, achievements[1].id to 2L, "streak_7" to 3L, "views_100" to 4L)
        assertEquals(2, unlockedCount(saved))
    }
}
