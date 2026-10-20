package com.celtikarda.galerireels

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object MediaRepository {

    /** Galerideki tüm videolar, yeniden eskiye. */
    suspend fun loadVideos(context: Context): List<VideoItem> = withContext(Dispatchers.IO) {
        runCatching { query(context) }.getOrDefault(emptyList()).sortedByDescending { it.dateMillis }
    }

    // Android 10+ VOLUME_EXTERNAL, SD karttakileri de kapsar
    private fun collection(): Uri =
        if (Build.VERSION.SDK_INT >= 29) MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        else MediaStore.Video.Media.EXTERNAL_CONTENT_URI

    @Suppress("DEPRECATION") // DATA sütunu sadece Android 9 ve öncesinde, klasör yolu için
    private fun query(context: Context): List<VideoItem> {
        val pathColumn = if (Build.VERSION.SDK_INT >= 29) MediaStore.MediaColumns.RELATIVE_PATH else MediaStore.MediaColumns.DATA
        val projection = listOfNotNull(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DATE_TAKEN,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.DURATION,
            MediaStore.MediaColumns.BUCKET_ID,
            MediaStore.MediaColumns.BUCKET_DISPLAY_NAME,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.WIDTH,
            MediaStore.MediaColumns.HEIGHT,
            // Videonun dönüklüğü (dik çekilmişse 90) Android 10'dan önce yok
            if (Build.VERSION.SDK_INT >= 29) MediaStore.MediaColumns.ORIENTATION else null,
            pathColumn,
        ).toTypedArray()
        // 0 baytlık (yarım kalmış indirme) dosyaları hiç gösterme
        val selection = "${MediaStore.MediaColumns.SIZE} > 0"
        val collection = collection()
        val result = ArrayList<VideoItem>()

        context.contentResolver.query(collection, projection, selection, null, null)?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val takenCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_TAKEN)
            val addedCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
            val durationCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DURATION)
            val bucketIdCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_ID)
            val bucketNameCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
            val mimeCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
            val widthCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.WIDTH)
            val heightCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.HEIGHT)
            val orientationCol = c.getColumnIndex(MediaStore.MediaColumns.ORIENTATION)
            val pathCol = c.getColumnIndexOrThrow(pathColumn)

            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                // DATE_TAKEN milisaniye, DATE_ADDED saniye; çekim tarihi yoksa eklenme tarihini kullan
                val taken = if (c.isNull(takenCol)) 0L else c.getLong(takenCol)
                val path = folderPath(c.getString(pathCol), isFullPath = Build.VERSION.SDK_INT < 29)
                // Galeri boyutu dosyadaki haliyle (döndürülmeden) yazıyor; dik videolarda en ve boy yer değiştirir
                val rawWidth = if (c.isNull(widthCol)) 0 else c.getInt(widthCol)
                val rawHeight = if (c.isNull(heightCol)) 0 else c.getInt(heightCol)
                val orientation = if (orientationCol < 0 || c.isNull(orientationCol)) 0 else c.getInt(orientationCol)
                val sideways = orientation == 90 || orientation == 270
                result += VideoItem(
                    id = id,
                    uri = ContentUris.withAppendedId(collection, id),
                    dateMillis = if (taken > 0) taken else c.getLong(addedCol) * 1000,
                    durationMs = c.getLong(durationCol),
                    albumId = c.getString(bucketIdCol) ?: path,
                    albumName = c.getString(bucketNameCol),
                    albumPath = path,
                    mimeType = c.getString(mimeCol),
                    width = if (sideways) rawHeight else rawWidth,
                    height = if (sideways) rawWidth else rawHeight,
                )
            }
        }
        return result
    }

    /** "Download/YTDLnis/Video/" ya da "/storage/emulated/0/Download/x.mp4" → "Download/YTDLnis/Video" */
    private fun folderPath(raw: String?, isFullPath: Boolean): String {
        if (raw == null) return ""
        val dir = if (isFullPath) raw.substringBeforeLast('/').removePrefix("/storage/emulated/0/") else raw
        return dir.trim('/')
    }
}
