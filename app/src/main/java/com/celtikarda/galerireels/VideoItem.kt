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
)

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
