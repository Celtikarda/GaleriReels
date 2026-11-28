package com.celtikarda.galerireels

import android.content.Context
import androidx.core.content.edit

/**
 * Uzun videolarda kaldığın yer. Kısa videolar (1 dakikadan kısa) her seferinde baştan başlar;
 * başında ya da sonunda bırakılan videonun kaydı silinir. Anahtar videonun [VideoItem.key]'i
 * (dosya adı + boyut), değer "konum:kaydedildiği an"; en eski kayıtlar [MAX_ENTRIES]'i geçince silinir.
 */
class ResumeStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("resume_positions", Context.MODE_PRIVATE)

    /** Videonun kaldığı yer (ms); kayıt yoksa ya da video kısaysa null */
    fun get(item: VideoItem): Long? {
        if (!resumable(item)) return null
        return prefs.getString(item.key, null)?.substringBefore(':')?.toLongOrNull()
    }

    fun save(item: VideoItem, positionMs: Long) {
        if (!resumable(item)) return
        if (positionMs < EDGE_MS || positionMs > item.durationMs - EDGE_MS) {
            if (prefs.contains(item.key)) prefs.edit { remove(item.key) }
            return
        }
        prefs.edit {
            putString(item.key, "$positionMs:${System.currentTimeMillis()}")
            val all = prefs.all
            if (all.size >= MAX_ENTRIES) {
                all.entries
                    .filter { it.key != item.key }
                    .sortedBy { (it.value as? String)?.substringAfter(':')?.toLongOrNull() ?: 0L }
                    .take(all.size - MAX_ENTRIES + 1)
                    .forEach { remove(it.key) }
            }
        }
    }

    /** Bütün kayıtlar: anahtar → (konum ms, kaydedildiği an). Bozuk değerler atlanır. */
    fun entries(): Map<String, Pair<Long, Long>> = prefs.all.mapNotNull { (key, value) ->
        val text = value as? String ?: return@mapNotNull null
        val pos = text.substringBefore(':').toLongOrNull() ?: return@mapNotNull null
        val at = text.substringAfter(':', "").toLongOrNull() ?: return@mapNotNull null
        key to (pos to at)
    }.toMap()

    private fun resumable(item: VideoItem) = !item.isPhoto && item.durationMs >= MIN_DURATION_MS

    private companion object {
        const val MIN_DURATION_MS = 60_000L
        /** İlk ve son 5 saniyede bırakılan video baştan başlar */
        const val EDGE_MS = 5_000L
        const val MAX_ENTRIES = 300
    }
}

/**
 * "Kaldığın yer" kartının adayı: en son kaydedilen ve hâlâ görünür olan video ile konumu.
 * Kaydı olan video silinmiş ya da klasörü gizlenmişse bir öncekine bakılır; hiçbiri yoksa null.
 */
internal fun latestResume(entries: Map<String, Pair<Long, Long>>, byKey: Map<String, VideoItem>): Pair<VideoItem, Long>? =
    entries.entries
        .sortedByDescending { it.value.second }
        .firstNotNullOfOrNull { (key, value) -> byKey[key]?.let { it to value.first } }
