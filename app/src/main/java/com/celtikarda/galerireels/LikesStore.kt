package com.celtikarda.galerireels

import android.content.Context
import androidx.core.content.edit

/**
 * 0.2–0.3 sürümlerinin beğenileri (galeri numaralarıyla, sadece uygulamanın içinde). 0.4'ten
 * itibaren beğeniler [StatsStore]'da; bu sadece eski beğenileri bir kez oraya taşımak için.
 */
class LikesStore(context: Context) {
    private val prefs = context.getSharedPreferences("likes", Context.MODE_PRIVATE)

    fun load(): Set<Long> =
        prefs.getStringSet(KEY, emptySet()).orEmpty().mapNotNull { it.toLongOrNull() }.toSet()

    fun clear() {
        prefs.edit { remove(KEY) }
    }

    private companion object {
        const val KEY = "liked_ids"
    }
}
