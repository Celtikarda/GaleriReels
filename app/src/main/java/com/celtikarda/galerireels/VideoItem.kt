package com.celtikarda.galerireels

import android.net.Uri

data class VideoItem(
    val id: Long,
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
) {
    /** En / boy; bilinmiyorsa 0 */
    val aspectRatio: Float get() = if (width > 0 && height > 0) width.toFloat() / height else 0f
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
    }
}
