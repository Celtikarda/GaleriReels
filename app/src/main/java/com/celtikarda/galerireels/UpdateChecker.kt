package com.celtikarda.galerireels

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** GitHub'daki en son sürüm */
data class AppUpdate(
    val version: String,
    val title: String,
    val notes: String,
    /** APK'nın doğrudan indirme adresi; release'e APK eklenmediyse sürüm sayfası */
    val downloadUrl: String,
    val pageUrl: String,
)

/**
 * Yeni sürüm çıkınca kullanıcıya haber verir. Uygulamanın internete çıktığı tek yer burası:
 * sadece GitHub'daki son sürümün numarasını okur, telefondan hiçbir bilgi göndermez.
 * Ayarlar'dan kapatılabilir; açıkken en fazla 12 saatte bir bakar.
 */
class UpdateChecker(context: Context) {
    private val prefs = context.getSharedPreferences("updates", Context.MODE_PRIVATE)
    private val currentVersion: String =
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "0"

    var enabled by mutableStateOf(prefs.getBoolean(KEY_ENABLED, true))
        private set
    /** Bulunan yeni sürüm; güncel sürüm kullanılıyorsa null */
    var available by mutableStateOf<AppUpdate?>(null)
        private set
    /** Yeni sürüm penceresi gösterilsin mi (kullanıcı bu sürüm için "Sonra" dediyse gösterilmez) */
    var showPrompt by mutableStateOf(false)
        private set
    var checking by mutableStateOf(false)
        private set

    fun updateEnabled(enabled: Boolean) {
        this.enabled = enabled
        prefs.edit { putBoolean(KEY_ENABLED, enabled) }
        if (!enabled) {
            available = null
            showPrompt = false
        }
    }

    /** Pencere kapatıldı; bu sürüm için bir daha açılmaz (Ayarlar'da görünmeye devam eder) */
    fun dismissPrompt() {
        showPrompt = false
        available?.let { prefs.edit { putString(KEY_DISMISSED, it.version) } }
    }

    /**
     * [force] = Ayarlar'daki "Şimdi kontrol et": kapalı olsa ve 12 saat dolmasa da bakar.
     * Sonuç: true = yeni sürüm var, false = güncel, null = bağlanılamadı.
     */
    suspend fun check(force: Boolean = false): Boolean? {
        if (!force && !enabled) return false
        val now = System.currentTimeMillis()
        if (!force && now - prefs.getLong(KEY_LAST_CHECK, 0L) < CHECK_INTERVAL_MS) {
            // Yakın zamanda bakıldı; bulunan sürümü internete çıkmadan hatırla
            restoreCached()
            return available != null
        }
        if (checking) return available != null
        checking = true
        val latest = try {
            withContext(Dispatchers.IO) { fetchLatest() }
        } catch (e: Exception) {
            Log.w(TAG, "Son sürüm okunamadı", e)
            null
        } finally {
            checking = false
        }
        if (latest == null) return null
        prefs.edit {
            putLong(KEY_LAST_CHECK, now)
            putString(KEY_CACHED, latest.toJson())
        }
        apply(latest, prompt = !force)
        return available != null
    }

    private fun restoreCached() {
        if (available != null) return
        val cached = prefs.getString(KEY_CACHED, null) ?: return
        runCatching { fromJson(JSONObject(cached)) }.getOrNull()?.let { apply(it, prompt = true) }
    }

    private fun apply(latest: AppUpdate, prompt: Boolean) {
        available = latest.takeIf { isNewer(it.version, currentVersion) }
        val update = available
        // Elle bakınca sonuç Ayarlar'da görünüyor; pencere sadece kendiliğinden bulunca açılır
        showPrompt = prompt && update != null && prefs.getString(KEY_DISMISSED, null) != update.version
    }

    private fun fetchLatest(): AppUpdate {
        val connection = URL(LATEST_URL).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                error("GitHub yanıtı ${connection.responseCode}")
            }
            val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            val tag = json.getString("tag_name")
            val pageUrl = json.optString("html_url", RELEASES_PAGE)
            val assets = json.optJSONArray("assets")
            val apkUrl = (0 until (assets?.length() ?: 0))
                .map { assets!!.getJSONObject(it) }
                .firstOrNull { it.optString("name").endsWith(".apk") }
                ?.optString("browser_download_url")
            return AppUpdate(
                version = tag.removePrefix("v"),
                title = json.optString("name").ifBlank { tag },
                notes = json.optString("body"),
                downloadUrl = apkUrl ?: pageUrl,
                pageUrl = pageUrl,
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun AppUpdate.toJson(): String = JSONObject()
        .put("version", version)
        .put("title", title)
        .put("notes", notes)
        .put("downloadUrl", downloadUrl)
        .put("pageUrl", pageUrl)
        .toString()

    private fun fromJson(json: JSONObject) = AppUpdate(
        version = json.getString("version"),
        title = json.getString("title"),
        notes = json.getString("notes"),
        downloadUrl = json.getString("downloadUrl"),
        pageUrl = json.getString("pageUrl"),
    )

    companion object {
        private const val TAG = "UpdateChecker"
        private const val LATEST_URL = "https://api.github.com/repos/Celtikarda/GaleriReels/releases/latest"
        const val RELEASES_PAGE = "https://github.com/Celtikarda/GaleriReels/releases"
        private const val CHECK_INTERVAL_MS = 12 * 60 * 60 * 1000L
        private const val KEY_ENABLED = "enabled"
        private const val KEY_LAST_CHECK = "last_check"
        private const val KEY_CACHED = "cached_latest"
        private const val KEY_DISMISSED = "dismissed_version"

        /** "1.10" > "1.9", "1.2.1" > "1.2"; sayı olmayan parçalar 0 sayılır */
        fun isNewer(candidate: String, current: String): Boolean {
            val a = candidate.split('.').map { it.toIntOrNull() ?: 0 }
            val b = current.split('.').map { it.toIntOrNull() ?: 0 }
            for (i in 0 until maxOf(a.size, b.size)) {
                val x = a.getOrElse(i) { 0 }
                val y = b.getOrElse(i) { 0 }
                if (x != y) return x > y
            }
            return false
        }
    }
}
