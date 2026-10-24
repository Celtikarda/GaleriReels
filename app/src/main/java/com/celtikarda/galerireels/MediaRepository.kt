package com.celtikarda.galerireels

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object MediaRepository {

    /** Galerideki tüm videolar, yeniden eskiye. */
    suspend fun loadVideos(context: Context): List<VideoItem> = withContext(Dispatchers.IO) {
        runCatching { query(context, isPhoto = false) }.getOrDefault(emptyList()).sortedByDescending { it.dateMillis }
    }

    /** Galerideki tüm fotoğraflar, yeniden eskiye. */
    suspend fun loadPhotos(context: Context): List<VideoItem> = withContext(Dispatchers.IO) {
        runCatching { query(context, isPhoto = true) }.getOrDefault(emptyList()).sortedByDescending { it.dateMillis }
    }

    // Android 10+ VOLUME_EXTERNAL, SD karttakileri de kapsar
    private fun collection(): Uri =
        if (Build.VERSION.SDK_INT >= 29) MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        else MediaStore.Video.Media.EXTERNAL_CONTENT_URI

    private fun imageCollection(): Uri =
        if (Build.VERSION.SDK_INT >= 29) MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        else MediaStore.Images.Media.EXTERNAL_CONTENT_URI

    @Suppress("DEPRECATION") // DATA sütunu sadece Android 9 ve öncesinde, klasör yolu için
    private fun query(context: Context, isPhoto: Boolean = false): List<VideoItem> {
        val pathColumn = if (Build.VERSION.SDK_INT >= 29) MediaStore.MediaColumns.RELATIVE_PATH else MediaStore.MediaColumns.DATA
        val projection = listOfNotNull(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_TAKEN,
            MediaStore.MediaColumns.DATE_ADDED,
            if (!isPhoto) MediaStore.MediaColumns.DURATION else null,
            MediaStore.MediaColumns.BUCKET_ID,
            MediaStore.MediaColumns.BUCKET_DISPLAY_NAME,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.WIDTH,
            MediaStore.MediaColumns.HEIGHT,
            // Videonun/fotoğrafın dönüklüğü (dik çekilmişse 90) Android 10'dan önce videolarda yok
            if (isPhoto || Build.VERSION.SDK_INT >= 29) MediaStore.MediaColumns.ORIENTATION else null,
            // Telefonun galerisindeki favoriler Android 11'den önce yok
            if (Build.VERSION.SDK_INT >= 30) MediaStore.MediaColumns.IS_FAVORITE else null,
            pathColumn,
        ).toTypedArray()
        // 0 baytlık (yarım kalmış indirme) dosyaları hiç gösterme
        val selection = "${MediaStore.MediaColumns.SIZE} > 0"
        val collection = if (isPhoto) imageCollection() else collection()
        val result = ArrayList<VideoItem>()

        context.contentResolver.query(collection, projection, selection, null, null)?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val sizeCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            val takenCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_TAKEN)
            val addedCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
            val durationCol = c.getColumnIndex(MediaStore.MediaColumns.DURATION)
            val bucketIdCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_ID)
            val bucketNameCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
            val mimeCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
            val widthCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.WIDTH)
            val heightCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.HEIGHT)
            val orientationCol = c.getColumnIndex(MediaStore.MediaColumns.ORIENTATION)
            val favoriteCol = c.getColumnIndex(MediaStore.MediaColumns.IS_FAVORITE)
            val pathCol = c.getColumnIndexOrThrow(pathColumn)

            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                // DATE_TAKEN milisaniye, DATE_ADDED saniye; çekim tarihi yoksa eklenme tarihini kullan
                val taken = if (c.isNull(takenCol)) 0L else c.getLong(takenCol)
                val path = folderPath(c.getString(pathCol), isFullPath = Build.VERSION.SDK_INT < 29)
                // Galeri boyutu dosyadaki haliyle (döndürülmeden) yazıyor; dik çekimlerde en ve boy yer değiştirir
                val rawWidth = if (c.isNull(widthCol)) 0 else c.getInt(widthCol)
                val rawHeight = if (c.isNull(heightCol)) 0 else c.getInt(heightCol)
                val orientation = if (orientationCol < 0 || c.isNull(orientationCol)) 0 else c.getInt(orientationCol)
                val sideways = orientation == 90 || orientation == 270
                val name = c.getString(nameCol) ?: id.toString()
                val size = c.getLong(sizeCol)
                result += VideoItem(
                    id = id,
                    key = "$name|$size",
                    name = name,
                    sizeBytes = size,
                    favorite = favoriteCol >= 0 && c.getInt(favoriteCol) == 1,
                    uri = ContentUris.withAppendedId(collection, id),
                    dateMillis = if (taken > 0) taken else c.getLong(addedCol) * 1000,
                    durationMs = if (durationCol >= 0 && !c.isNull(durationCol)) c.getLong(durationCol) else 0L,
                    albumId = c.getString(bucketIdCol) ?: path,
                    albumName = c.getString(bucketNameCol),
                    albumPath = path,
                    mimeType = c.getString(mimeCol),
                    width = if (sideways) rawHeight else rawWidth,
                    height = if (sideways) rawWidth else rawHeight,
                    isPhoto = isPhoto,
                )
            }
        }
        return result
    }

    fun uriOf(id: Long, isPhoto: Boolean = false): Uri =
        ContentUris.withAppendedId(if (isPhoto) imageCollection() else collection(), id)

    /** Verilen video/fotoğraflardan hâlâ çöp kutusunda duranlar (30 gün dolunca Android siliyor) */
    suspend fun trashedIds(context: Context, ids: Collection<Long>): Set<Long> = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < 30 || ids.isEmpty()) return@withContext emptySet()
        val args = Bundle().apply {
            putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_ONLY)
            putString(ContentResolver.QUERY_ARG_SQL_SELECTION, "${MediaStore.MediaColumns._ID} IN (${ids.joinToString(",")})")
        }
        buildSet {
            for (col in listOf(collection(), imageCollection())) {
                runCatching {
                    context.contentResolver.query(col, arrayOf(MediaStore.MediaColumns._ID), args, null)
                        ?.use { c -> while (c.moveToNext()) add(c.getLong(0)) }
                }
            }
        }
    }

    /** Galerideki favori video ve fotoğrafların numaraları (başka uygulamalardan, ör. galeriden değişmiş olabilir) */
    suspend fun favoriteIds(context: Context): Set<Long> = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < 30) return@withContext emptySet()
        buildSet {
            for (col in listOf(collection(), imageCollection())) {
                runCatching {
                    context.contentResolver.query(
                        col,
                        arrayOf(MediaStore.MediaColumns._ID),
                        "${MediaStore.MediaColumns.IS_FAVORITE} = 1",
                        null,
                        null,
                    )?.use { c -> while (c.moveToNext()) add(c.getLong(0)) }
                }
            }
        }
    }

    /** "Download/YTDLnis/Video/" ya da "/storage/emulated/0/Download/x.mp4" → "Download/YTDLnis/Video" */
    private fun folderPath(raw: String?, isFullPath: Boolean): String {
        if (raw == null) return ""
        val dir = if (isFullPath) raw.substringBeforeLast('/').removePrefix("/storage/emulated/0/") else raw
        return dir.trim('/')
    }
}
