package com.celtikarda.galerireels

import android.content.pm.ApplicationInfo
import android.graphics.Color
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import com.celtikarda.galerireels.ui.GaleriReelsApp

class MainActivity : ComponentActivity() {
    /**
     * Reels ekranı açıkken ses tuşlarını önce o görür: telefonun sesi max iken ses açma tuşu
     * videoyu %200'e kadar yükseltir. true dönerse tuş telefona (sistem ses çubuğuna) gitmez.
     */
    var volumeKeyHandler: ((KeyEvent) -> Boolean)? = null

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val code = event.keyCode
        if ((code == KeyEvent.KEYCODE_VOLUME_UP || code == KeyEvent.KEYCODE_VOLUME_DOWN) &&
            volumeKeyHandler?.invoke(event) == true
        ) {
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(AppLanguage.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Siyah arka plan üstünde açık renkli durum çubuğu simgeleri
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        // Ekrandaki metinler bu Activity'nin (seçili dildeki) kaynaklarından okunur
        Strings.use(resources)

        // Sadece debug sürümde: adb ile belirli bir videoyu doğrudan açıp test etmek için
        // adb shell am start -S -n com.celtikarda.galerireels/.MainActivity --el open_video_id <id>
        // Temizliği verilen videolarla başlatmak için: --ela open_cleanup_ids <id>,<id>
        // Telefonun sesine dokunmadan ses tuşu yükseltmesini denemek için: --ez fake_max_volume true
        val debuggable = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        val openVideoId = if (debuggable) intent.getLongExtra("open_video_id", -1L).takeIf { it > 0 } else null
        val openCleanupIds = if (debuggable) intent.getLongArrayExtra("open_cleanup_ids")?.toList() else null
        if (debuggable && intent.getBooleanExtra("fake_max_volume", false)) fakeMaxVolume = true

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                GaleriReelsApp(openVideoId = openVideoId, openCleanupIds = openCleanupIds)
            }
        }
    }

    companion object {
        /** Sadece debug testi: telefonun sesi max değilken de max'mış gibi davran */
        var fakeMaxVolume = false
    }
}
