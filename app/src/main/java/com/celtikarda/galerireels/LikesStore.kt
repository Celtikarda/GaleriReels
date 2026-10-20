package com.celtikarda.galerireels

import android.content.Context
import androidx.core.content.edit

/** Beğenilen videoların id'leri; sadece bu telefonda, uygulamanın kendi ayar dosyasında durur. */
class LikesStore(context: Context) {
    private val prefs = context.getSharedPreferences("likes", Context.MODE_PRIVATE)

    fun load(): Set<Long> =
        prefs.getStringSet(KEY, emptySet()).orEmpty().mapNotNull { it.toLongOrNull() }.toSet()

    fun save(ids: Set<Long>) {
        prefs.edit { putStringSet(KEY, ids.map { it.toString() }.toSet()) }
    }

    private companion object {
        const val KEY = "liked_ids"
    }
}
