package com.celtikarda.galerireels

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.AtomicFile
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.core.content.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

/** Bir videonun kayıtlı bilgileri */
data class VideoStat(
    val liked: Boolean = false,
    /** Beğeninin en son değiştiği an; iki kayıt birleşirken yenisi geçerli olur */
    val likeChangedAt: Long = 0,
    val views: Int = 0,
    val lastViewedAt: Long = 0,
)

/**
 * Beğeniler ve izlenme sayıları. Aynı JSON dosyası iki yerde tutulur: uygulamanın kendi
 * klasöründe (hızlı açılış için) ve kullanıcının seçtiği klasörde (uygulama silinse de kalsın
 * diye). Videolar dosya adı + boyutuyla tanınır ([VideoItem.key]).
 */
class StatsStore(context: Context, private val scope: CoroutineScope) {

    private val appContext = context.applicationContext
    private val localFile = AtomicFile(File(appContext.filesDir, DataFolder.DATA_FILE))
    private val prefs = appContext.getSharedPreferences("data_folder", Context.MODE_PRIVATE)

    /** video anahtarı → bilgiler */
    private val stats = mutableStateMapOf<String, VideoStat>()

    var loaded by mutableStateOf(false)
        private set
    /** Seçilen klasör; seçilmediyse ya da izni kalmadıysa null */
    var folder by mutableStateOf<DataFolder?>(null)
        private set
    /** Kullanıcı klasör seçmeyi "Şimdi değil" diye geçti; ayarlardan seçebilir */
    var folderPromptDismissed by mutableStateOf(prefs.getBoolean(KEY_DISMISSED, false))
        private set
    /** Klasöre en son başarıyla yazılan an */
    var lastFolderSave by mutableStateOf(prefs.getLong(KEY_LAST_SAVE, 0L))
        private set
    /** Klasöre yazılamadıysa kullanıcıya gösterilecek açıklama */
    var folderError by mutableStateOf<String?>(null)
        private set

    private var saveJob: Job? = null
    private var readmeChecked = false

    fun stat(key: String): VideoStat = stats[key] ?: VideoStat()

    val totalLikes: Int get() = stats.values.count { it.liked }
    val totalViews: Int get() = stats.values.sumOf { it.views }
    val watchedVideos: Int get() = stats.values.count { it.views > 0 }

    fun load() {
        scope.launch {
            val (saved, savedFolder) = withContext(Dispatchers.IO) {
                val folder = restoreFolder()
                // Uygulamanın kendi kopyası yoksa (veriler silindiyse) klasördekinden aç
                val saved = readLocal() ?: folder?.let { f -> runCatching { f.readData(appContext)?.let(::parse) }.getOrNull() }
                saved to folder
            }
            saved?.let { stats.putAll(it) }
            folder = savedFolder
            loaded = true
        }
    }

    suspend fun awaitLoaded() {
        snapshotFlow { loaded }.first { it }
    }

    fun setLiked(key: String, liked: Boolean) {
        val now = System.currentTimeMillis()
        stats[key] = stat(key).copy(liked = liked, likeChangedAt = now)
        scheduleSave()
    }

    fun addView(key: String) {
        val current = stat(key)
        stats[key] = current.copy(views = current.views + 1, lastViewedAt = System.currentTimeMillis())
        scheduleSave()
    }

    /**
     * Klasör seçme penceresinden dönen klasörü kullanmaya başlar. İçinde önceden kalmış veri varsa
     * (ör. uygulama silinip yeniden kurulduysa) şimdikilerle birleştirir; geri gelen video sayısını döner.
     */
    suspend fun useFolder(treeUri: Uri): Result<Int> = runCatching {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        appContext.contentResolver.takePersistableUriPermission(treeUri, flags)
        val (newFolder, existing) = withContext(Dispatchers.IO) {
            val opened = DataFolder.open(appContext, treeUri)
            opened to opened.readData(appContext)?.let { runCatching { parse(it) }.getOrNull() }
        }
        existing?.forEach { (key, other) -> stats[key] = merge(stat(key), other) }

        val old = folder
        if (old != null && old.treeUri != treeUri) {
            runCatching { appContext.contentResolver.releasePersistableUriPermission(old.treeUri, flags) }
        }
        folder = newFolder
        readmeChecked = false
        prefs.edit {
            putString(KEY_TREE, treeUri.toString())
            putString(KEY_DIR, newFolder.dirId)
        }
        save()
        folderError?.let { error(it) }
        existing?.size ?: 0
    }

    fun dismissFolderPrompt() {
        folderPromptDismissed = true
        prefs.edit { putBoolean(KEY_DISMISSED, true) }
    }

    /** Uygulama arka plana geçerken bekleyen kaydı hemen yapar. */
    fun flush() {
        if (saveJob?.isActive != true) return
        saveJob?.cancel()
        scope.launch(NonCancellable) { save() }
    }

    // Art arda gelen değişiklikler (ör. hızlı beğeniler) tek kayıtta yazılsın
    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(1500)
            save()
        }
    }

    private suspend fun save() {
        val json = toJson(stats.toMap())
        val target = folder
        val written: Result<DataFolder>? = withContext(Dispatchers.IO) {
            writeLocal(json)
            target?.let { writeToFolder(it, json) }
        }
        written?.onSuccess { usedFolder ->
            if (usedFolder.dirId != target?.dirId) {
                folder = usedFolder
                prefs.edit { putString(KEY_DIR, usedFolder.dirId) }
            }
            lastFolderSave = System.currentTimeMillis()
            prefs.edit { putLong(KEY_LAST_SAVE, lastFolderSave) }
            folderError = null
        }?.onFailure {
            folderError = "Seçtiğin klasöre yazılamadı. Klasör silinmiş ya da izni kaldırılmış olabilir; Ayarlar'dan yeniden seç."
        }
    }

    /** Klasöre yazar; GaleriReels klasörü silinmişse bir kez yeniden açıp dener. */
    private fun writeToFolder(target: DataFolder, json: String): Result<DataFolder> =
        runCatching {
            target.writeData(appContext, json)
            target
        }.recoverCatching {
            DataFolder.open(appContext, target.treeUri).also { it.writeData(appContext, json) }
        }.onSuccess {
            if (!readmeChecked) {
                runCatching { it.writeReadmeIfMissing(appContext) }
                readmeChecked = true
            }
        }

    private fun restoreFolder(): DataFolder? {
        val tree = prefs.getString(KEY_TREE, null)?.let(Uri::parse) ?: return null
        val dirId = prefs.getString(KEY_DIR, null) ?: return null
        // Kullanıcı izni telefon ayarlarından kaldırmış olabilir
        val granted = appContext.contentResolver.persistedUriPermissions.any { it.uri == tree && it.isWritePermission }
        return if (granted) DataFolder.restore(tree, dirId) else null
    }

    private fun readLocal(): Map<String, VideoStat>? {
        if (!localFile.baseFile.exists()) return null
        return runCatching { parse(localFile.readFully().toString(Charsets.UTF_8)) }.getOrNull()
    }

    private fun writeLocal(json: String) {
        val out = localFile.startWrite()
        try {
            out.write(json.toByteArray(Charsets.UTF_8))
            localFile.finishWrite(out)
        } catch (e: Exception) {
            localFile.failWrite(out)
        }
    }

    private companion object {
        const val KEY_TREE = "tree_uri"
        const val KEY_DIR = "dir_id"
        const val KEY_DISMISSED = "prompt_dismissed"
        const val KEY_LAST_SAVE = "last_folder_save"

        fun toJson(stats: Map<String, VideoStat>): String {
            val videos = JSONObject()
            for ((key, s) in stats) {
                if (!s.liked && s.views == 0 && s.likeChangedAt == 0L) continue
                videos.put(
                    key,
                    JSONObject()
                        .put("liked", s.liked)
                        .put("likeChangedAt", s.likeChangedAt)
                        .put("views", s.views)
                        .put("lastViewedAt", s.lastViewedAt),
                )
            }
            return JSONObject()
                .put("app", "Galeri Reels")
                .put("format", 1)
                .put("updatedAt", System.currentTimeMillis())
                .put("videos", videos)
                .toString(2)
        }

        fun parse(text: String): Map<String, VideoStat> {
            val videos = JSONObject(text).optJSONObject("videos") ?: return emptyMap()
            return videos.keys().asSequence().associateWith { key ->
                val o = videos.getJSONObject(key)
                VideoStat(
                    liked = o.optBoolean("liked"),
                    likeChangedAt = o.optLong("likeChangedAt"),
                    views = o.optInt("views"),
                    lastViewedAt = o.optLong("lastViewedAt"),
                )
            }
        }

        /** İki kaydı birleştirir: en çok izlenme, en son değişen beğeni */
        fun merge(a: VideoStat, b: VideoStat) = VideoStat(
            liked = if (b.likeChangedAt > a.likeChangedAt) b.liked else a.liked,
            likeChangedAt = maxOf(a.likeChangedAt, b.likeChangedAt),
            views = maxOf(a.views, b.views),
            lastViewedAt = maxOf(a.lastViewedAt, b.lastViewedAt),
        )
    }
}
