package com.celtikarda.galerireels

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

enum class MediaAccess {
    NONE,
    /** Android 14+: kullanıcı sadece bazı fotoğrafları seçti */
    PARTIAL,
    FULL;

    companion object {
        val permissions: Array<String> = when {
            Build.VERSION.SDK_INT >= 34 -> arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
            )
            Build.VERSION.SDK_INT >= 33 -> arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
            )
            else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        fun current(context: Context): MediaAccess {
            fun granted(p: String) =
                ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED

            return when {
                Build.VERSION.SDK_INT >= 33 -> when {
                    granted(Manifest.permission.READ_MEDIA_IMAGES) || granted(Manifest.permission.READ_MEDIA_VIDEO) -> FULL
                    Build.VERSION.SDK_INT >= 34 && granted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) -> PARTIAL
                    else -> NONE
                }
                granted(Manifest.permission.READ_EXTERNAL_STORAGE) -> FULL
                else -> NONE
            }
        }
    }
}
