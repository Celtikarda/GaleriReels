package com.celtikarda.galerireels

import com.celtikarda.galerireels.ui.formatCount
import androidx.annotation.StringRes
import androidx.annotation.PluralsRes
import android.net.Uri

data class VideoItem(
    val id: Long,
    /**
     * Beğeni ve izlenmelerin kaydedildiği kimlik: dosya adı + boyutu. Galerinin verdiği [id]
     * telefon sıfırlanınca ya da video başka klasöre taşınınca değişebiliyor, bu değişmez.
     */
    val key: String,
    /** Dosya adı, ör. "VID-20251015-WA0005.mp4" */
    val name: String,
    val sizeBytes: Long,
    /** Galeri açıldığında telefonun favorilerinde miydi (sonraki değişiklikler GalleryViewModel'de) */
    val favorite: Boolean,
    val uri: Uri,
    val dateMillis: Long,
    val durationMs: Long,
    val albumId: String,
    val albumName: String?,
    /** Örn. "Download/YTDLnis/Video" — aynı adlı klasörleri ayırt etmek için */
    val albumPath: String,
    val mimeType: String?,
    /** Ekranda görüneceği haliyle (döndürülmüş) boyut; galeri bilmiyorsa 0 */
    val width: Int,
    val height: Int,
    /** Fotoğraf ise true, video ise false */
    val isPhoto: Boolean = false,
) {
    /** En / boy; bilinmiyorsa 0 */
    val aspectRatio: Float get() = if (width > 0 && height > 0) width.toFloat() / height else 0f
}

/** Uygulamada hangi medya türünün gösterileceği */
enum class MediaMode(@StringRes private val titleRes: Int, @PluralsRes private val countRes: Int) {
    VIDEOS(R.string.mode_videos, R.plurals.n_videos),
    PHOTOS(R.string.mode_photos, R.plurals.n_photos),
    ALL(R.string.mode_all, R.plurals.n_items);

    val title: String get() = str(titleRes)

    /** "12 video" / "12 videos", "3 fotoğraf" / "3 photos" */
    fun count(n: Int): String = plural(countRes, n, formatCount(n))
}

/** Uygulama arayüzünün renk teması (Reels oynatıcı ekranı her zaman sinema siyahı kalır) */
enum class ThemeMode(@StringRes private val titleRes: Int) {
    DARK(R.string.theme_dark),
    LIGHT(R.string.theme_light),
    SYSTEM(R.string.theme_system);

    val title: String get() = str(titleRes)
}

/** Reels ekranında sol veya sağ kenara (%25) basılı tutulunca yapılacak işlem */
enum class EdgeHoldAction(@StringRes private val titleRes: Int) {
    FAST_FORWARD(R.string.edge_ff),
    REWIND(R.string.edge_rew),
    CLEAR_VIEW(R.string.edge_clear),
    NONE(R.string.off);

    val title: String get() = str(titleRes)
}

data class Album(
    val id: String,
    val name: String,
    val count: Int,
    /** Klasördeki en yeni video; kapak resmi olarak kullanılır */
    val cover: VideoItem,
) {
    companion object {
        const val ALL = "__all__"
        const val LIKED = "__liked__"
        const val FAVORITES = "__favorites__"
        const val CLEANUP = "__cleanup__"
        const val RECENT = "__recent__"
        const val MONTHLY = "__monthly__"
        const val MEMORIES = "__memories__"
    }
}
