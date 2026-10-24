package com.celtikarda.galerireels

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
enum class MediaMode(val title: String, val itemWord: String, val pluralWord: String) {
    VIDEOS("Videolar", "video", "videolar"),
    PHOTOS("Fotoğraflar", "fotoğraf", "fotoğraflar"),
    ALL("Tümü", "içerik", "içerikler"),
}

/** Uygulama arayüzünün renk teması (Reels oynatıcı ekranı her zaman sinema siyahı kalır) */
enum class ThemeMode(val title: String) {
    DARK("Gece"),
    LIGHT("Aydınlık"),
    SYSTEM("Sistem"),
}

/** Reels ekranında sol veya sağ kenara (%25) basılı tutulunca yapılacak işlem */
enum class EdgeHoldAction(val title: String) {
    FAST_FORWARD("2x İleri"),
    CLEAR_VIEW("Net görünüm"),
    NONE("Kapalı"),
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
