package com.celtikarda.galerireels

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.Settings

/**
 * Galerideki videoları değiştiren istekler (favori, çöp kutusu). Android 11+ bunları kullanıcıya
 * sorarak yapar: dönen PendingIntent bir onay penceresi açar. Kullanıcı Ayarlar'dan "Medya
 * yönetimi" izni verdiyse pencere çıkmadan hemen onaylanır.
 */
object MediaActions {

    /** Telefonun galerisindeki favoriler Android 11'den itibaren var */
    val favoritesSupported: Boolean get() = Build.VERSION.SDK_INT >= 30

    fun favoriteRequest(context: Context, items: List<VideoItem>, favorite: Boolean): PendingIntent? =
        if (Build.VERSION.SDK_INT >= 30) {
            MediaStore.createFavoriteRequest(context.contentResolver, items.map { it.uri }, favorite)
        } else {
            null
        }

    /** Android'in çöp kutusu (30 gün sonra kendiliğinden siler) Android 11'den itibaren var */
    val trashSupported: Boolean get() = Build.VERSION.SDK_INT >= 30

    /** [trash] true: çöp kutusuna taşır, false: çöp kutusundan geri getirir */
    fun trashRequest(context: Context, uris: List<Uri>, trash: Boolean): PendingIntent? =
        if (Build.VERSION.SDK_INT >= 30 && uris.isNotEmpty()) {
            MediaStore.createTrashRequest(context.contentResolver, uris, trash)
        } else {
            null
        }

    /** Kalıcı olarak siler (geri getirilemez) */
    fun deleteRequest(context: Context, uris: List<Uri>): PendingIntent? =
        if (Build.VERSION.SDK_INT >= 30 && uris.isNotEmpty()) {
            MediaStore.createDeleteRequest(context.contentResolver, uris)
        } else {
            null
        }

    /** Android 12+: "Medya yönetimi" izni verilmiş mi (verildiyse onay sorulmaz) */
    fun canManageMedia(context: Context): Boolean =
        Build.VERSION.SDK_INT >= 31 && MediaStore.canManageMedia(context)

    /** "Medya yönetimi" izninin açıldığı ayar sayfası; Android 12'den eskiyse null */
    fun manageMediaSettings(context: Context): Intent? =
        if (Build.VERSION.SDK_INT >= 31) {
            Intent(Settings.ACTION_REQUEST_MANAGE_MEDIA, Uri.fromParts("package", context.packageName, null))
        } else {
            null
        }
}
