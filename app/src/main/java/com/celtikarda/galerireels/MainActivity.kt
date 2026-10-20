package com.celtikarda.galerireels

import android.content.pm.ApplicationInfo
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import com.celtikarda.galerireels.ui.GaleriReelsApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Siyah arka plan üstünde açık renkli durum çubuğu simgeleri
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)

        // Sadece debug sürümde: adb ile belirli bir videoyu doğrudan açıp test etmek için
        // adb shell am start -S -n com.celtikarda.galerireels/.MainActivity --el open_video_id <id>
        // Temizliği verilen videolarla başlatmak için: --ela open_cleanup_ids <id>,<id>
        val debuggable = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        val openVideoId = if (debuggable) intent.getLongExtra("open_video_id", -1L).takeIf { it > 0 } else null
        val openCleanupIds = if (debuggable) intent.getLongArrayExtra("open_cleanup_ids")?.toList() else null

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                GaleriReelsApp(openVideoId = openVideoId, openCleanupIds = openCleanupIds)
            }
        }
    }
}
