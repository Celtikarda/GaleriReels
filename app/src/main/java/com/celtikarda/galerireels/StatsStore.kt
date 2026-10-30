package com.celtikarda.galerireels

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.AtomicFile
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
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
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Bir videonun kayıtlı bilgileri */
data class VideoStat(
    /** En az bir kez beğenildi (Beğendiklerin klasörü, temizlikte koruma) */
    val liked: Boolean = false,
    /** Kaç kez beğenildi; kalp her açılışta yeniden basılabilir, her basış bir beğeni ekler */
    val likes: Int = 0,
    /** Beğeninin en son değiştiği an; iki kayıt birleşirken yenisi geçerli olur */
    val likeChangedAt: Long = 0,
    val views: Int = 0,
    val lastViewedAt: Long = 0,
)

/** Uygulamanın Android çöp kutusuna taşıdığı video veya fotoğraf (30 gün sonra Android kendisi siler) */
data class TrashedVideo(val id: Long, val key: String, val name: String, val bytes: Long, val at: Long, val isPhoto: Boolean = false)

/** Dosyadan okunan ya da dosyaya yazılacak her şey */
internal class SavedData(
    val videos: Map<String, VideoStat>,
    /** gün ("2026-10-20") → video anahtarı → o gün kaç kez izlendi */
    val days: Map<String, Map<String, Int>>,
    /** Uygulamanın açıldığı günler */
    val activeDays: Set<String>,
    /** gün → o gün temizlenen (çöp kutusuna taşınan) bayt */
    val cleaned: Map<String, Long>,
    val trash: List<TrashedVideo>,
    /** başarım → açıldığı an */
    val achievements: Map<String, Long>,
    /** Ana ekrandan, Tüm videolar'dan ve önerilerden gizlenen klasör kimlikleri */
    val hiddenAlbums: Set<String> = emptySet(),
    /** Kullanıcının seçtiği medya modu (yedekten geri yüklenince tekrar sormasın) */
    val mediaMode: MediaMode? = null,
    /** Kullanıcının seçtiği görünüm teması */
    val themeMode: ThemeMode? = null,
    /** Sol kenara basılı tutma işlemi */
    val leftHoldAction: EdgeHoldAction? = null,
    /** Sağ kenara basılı tutma işlemi */
    val rightHoldAction: EdgeHoldAction? = null,
)

/**
 * Beğeniler, izlenme sayıları ve günlük izleme geçmişi. Aynı JSON dosyası iki yerde tutulur:
 * uygulamanın kendi klasöründe (hızlı açılış için) ve kullanıcının seçtiği klasörde (uygulama
 * silinse de kalsın diye). Videolar dosya adı + boyutuyla tanınır ([VideoItem.key]).
 * Günlük geçmiş aylık özet ve başarımlar ("7 gün üst üste") için tutulur.
 */
class StatsStore(context: Context, private val scope: CoroutineScope) {

    private val appContext = context.applicationContext
    private val localFile = AtomicFile(File(appContext.filesDir, DataFolder.DATA_FILE))
    private val prefs = appContext.getSharedPreferences("data_folder", Context.MODE_PRIVATE)

    /** video anahtarı → bilgiler */
    private val stats = mutableStateMapOf<String, VideoStat>()
    /** gün → video anahtarı → o gün kaç kez izlendi (sadece ana iş parçacığında) */
    private val days = HashMap<String, HashMap<String, Int>>()
    private val activeDays = sortedSetOf<String>()
    /** gün → o gün temizlenen bayt */
    private val cleaned = mutableStateMapOf<String, Long>()

    /** Günlük geçmiş ya da aktif günler değişince ekranların (aylık özet, başarımlar) yenilenmesi için */
    var historyRevision by mutableIntStateOf(0)
        private set

    /** Çöp kutusuna taşıdıklarımız; geri getirilebilir ya da kalıcı silinebilir */
    val trashed = mutableStateListOf<TrashedVideo>()
    val cleanedToday: Long get() = cleaned[today()] ?: 0L
    val cleanedTotal: Long get() = cleaned.values.sum()

    /** Açılmış başarımlar → açıldığı an */
    val unlocked = mutableStateMapOf<String, Long>()

    /** Gizlenen klasör kimlikleri (Tüm videolar, Tarihte bugün ve Temizlik'ten hariç tutulur) */
    val hiddenAlbums = mutableStateListOf<String>()

    fun hideAlbum(albumId: String) {
        if (albumId !in hiddenAlbums) {
            hiddenAlbums += albumId
            historyRevision++
            scheduleSave()
        }
    }

    fun unhideAlbum(albumId: String) {
        if (hiddenAlbums.remove(albumId)) {
            historyRevision++
            scheduleSave()
        }
    }

    fun unhideAllAlbums() {
        if (hiddenAlbums.isNotEmpty()) {
            hiddenAlbums.clear()
            historyRevision++
            scheduleSave()
        }
    }

    /** En son izlenen videoların anahtarları, yeniden eskiye */
    fun recentKeys(limit: Int): List<String> =
        stats.entries.filter { it.value.views > 0 }.sortedByDescending { it.value.lastViewedAt }.take(limit).map { it.key }

    /** En az bir kez izlenmiş videoların anahtarları */
    fun watchedKeys(): Set<String> = stats.filterValues { it.views > 0 }.keys

    val maxViewsOfOne: Int get() = stats.values.maxOfOrNull { it.views } ?: 0

    /** Uygulamanın üst üste açıldığı en uzun gün sayısı */
    fun bestStreak(): Int {
        historyRevision // Compose gözlemi için
        return computeBestStreak(activeDays)
    }

    /** Bugün (ya da dün) itibarıyla devam eden üst üste açılma serisi */
    fun currentStreak(todayDate: LocalDate = LocalDate.now()): Int {
        historyRevision
        return computeCurrentStreak(activeDays, todayDate)
    }

    // --- Aylık özet sorguları

    /** Kayıtta verisi olan aylar (en yeniden eskiye); içinde bulunduğumuz ay her zaman listededir */
    fun availableMonths(): List<YearMonth> {
        historyRevision
        val months = HashSet<YearMonth>()
        months += YearMonth.now()
        (days.keys + activeDays + cleaned.keys).forEach { day ->
            if (day.length >= 7) runCatching { YearMonth.parse(day.substring(0, 7)) }.getOrNull()?.let { months += it }
        }
        return months.sortedDescending()
    }

    /** Verilen ayda hangi video kaç kez izlendi (video anahtarı → izlenme sayısı) */
    fun monthViews(month: YearMonth): Map<String, Int> {
        historyRevision
        val prefix = month.toString() // "2026-10"
        val totals = HashMap<String, Int>()
        for ((date, dayCounts) in days) {
            if (!date.startsWith(prefix)) continue
            for ((key, count) in dayCounts) {
                if (count > 0) totals[key] = (totals[key] ?: 0) + count
            }
        }
        return totals
    }

    /** Verilen ayda uygulamanın kullanıldığı gün sayısı */
    fun monthActiveDays(month: YearMonth): Int {
        historyRevision
        val prefix = month.toString()
        val seen = HashSet<String>()
        activeDays.filterTo(seen) { it.startsWith(prefix) }
        days.keys.filterTo(seen) { it.startsWith(prefix) }
        cleaned.entries.filter { it.key.startsWith(prefix) && it.value > 0 }.mapTo(seen) { it.key }
        return seen.size
    }

    /** Verilen ayda temizlenen toplam bayt */
    fun monthCleanedBytes(month: YearMonth): Long {
        historyRevision
        val prefix = month.toString()
        return cleaned.entries.filter { it.key.startsWith(prefix) }.sumOf { it.value }
    }

    /** Verilen ayda beğenilen video sayısı */
    fun monthLikedCount(month: YearMonth): Int =
        stats.values.count { s ->
            s.liked && s.likeChangedAt > 0 &&
                YearMonth.from(Instant.ofEpochMilli(s.likeChangedAt).atZone(ZoneId.systemDefault())) == month
        }

    fun recordUnlocked(ids: List<String>) {
        val now = System.currentTimeMillis()
        ids.forEach { if (it !in unlocked) unlocked[it] = now }
        scheduleSave()
    }

    var loaded by mutableStateOf(false)
        private set
    /** Seçilen klasör; seçilmediyse ya da izni kalmadıysa null */
    var folder by mutableStateOf<DataFolder?>(null)
        private set
    /** Kullanıcı klasör seçmeyi "Şimdi değil" diye geçti; ayarlardan seçebilir */
    var folderPromptDismissed by mutableStateOf(prefs.getBoolean(KEY_DISMISSED, false))
        private set
    /** Uygulamadan çıkınca ya da ekran kapanınca videonun sesi arka planda devam etsin mi */
    var backgroundPlay by mutableStateOf(prefs.getBoolean(KEY_BG_PLAY, false))
        private set
    /** Gösterilecek içerik türü: Videolar, Fotoğraflar ya da Tümü */
    var mediaMode by mutableStateOf(
        MediaMode.entries.firstOrNull { it.name == prefs.getString(KEY_MEDIA_MODE, null) } ?: MediaMode.VIDEOS,
    )
        private set
    /** İlk açılışta hangi tür içerik gösterileceği soruldu mu */
    var mediaModeAsked by mutableStateOf(prefs.getBoolean(KEY_MEDIA_MODE_ASKED, false))
        private set
    /** Uygulama arayüzünün renk teması: Gece, Aydınlık ya da Sistem */
    var themeMode by mutableStateOf(
        ThemeMode.entries.firstOrNull { it.name == prefs.getString(KEY_THEME_MODE, null) } ?: ThemeMode.DARK,
    )
        private set
    /** Reels ekranında sol kenara (%25) basılı tutulunca yapılacak işlem */
    var leftHoldAction by mutableStateOf(
        EdgeHoldAction.entries.firstOrNull { it.name == prefs.getString(KEY_LEFT_HOLD, null) } ?: EdgeHoldAction.REWIND,
    )
        private set
    /** Reels ekranında sağ kenara (%25) basılı tutulunca yapılacak işlem */
    var rightHoldAction by mutableStateOf(
        EdgeHoldAction.entries.firstOrNull { it.name == prefs.getString(KEY_RIGHT_HOLD, null) } ?: EdgeHoldAction.FAST_FORWARD,
    )
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
            saved?.let { absorb(it) }
            // Android çöp kutusundakileri 30 gün sonra siliyor
            val expired = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
            trashed.removeAll { it.at < expired }
            folder = savedFolder
            loaded = true
            markActiveToday()
        }
    }

    suspend fun awaitLoaded() {
        snapshotFlow { loaded }.first { it }
    }

    fun addLike(key: String) = changeLikes(key, +1)

    fun removeLike(key: String) = changeLikes(key, -1)

    fun clearLikes(key: String) = changeLikes(key, -stat(key).likes)

    private fun changeLikes(key: String, delta: Int) {
        val likes = (stat(key).likes + delta).coerceAtLeast(0)
        stats[key] = stat(key).copy(liked = likes > 0, likes = likes, likeChangedAt = System.currentTimeMillis())
        scheduleSave()
    }

    fun addView(key: String) {
        val current = stat(key)
        stats[key] = current.copy(views = current.views + 1, lastViewedAt = System.currentTimeMillis())
        val day = days.getOrPut(today()) { HashMap() }
        day[key] = (day[key] ?: 0) + 1
        activeDays.add(today())
        historyRevision++
        scheduleSave()
    }

    /** Temizlikte çöp kutusuna taşındılar; bugün temizlenene eklenir */
    fun recordTrashed(videos: List<VideoItem>) {
        val now = System.currentTimeMillis()
        videos.forEach { trashed += TrashedVideo(it.id, it.key, it.name, it.sizeBytes, now, it.isPhoto) }
        cleaned[today()] = cleanedToday + videos.sumOf { it.sizeBytes }
        activeDays.add(today())
        historyRevision++
        scheduleSave()
    }

    /** Çöp kutusundan geri getirildiler: temizlenmiş sayılmazlar */
    fun recordRestored(ids: Set<Long>) {
        val restored = trashed.filter { it.id in ids }
        trashed.removeAll(restored)
        restored.forEach { t ->
            val day = dayOf(t.at)
            cleaned[day] = ((cleaned[day] ?: 0L) - t.bytes).coerceAtLeast(0L)
        }
        historyRevision++
        scheduleSave()
    }

    /** Kalıcı silindiler (ya da 30 gün doldu): temizlenmiş sayılmaya devam ederler */
    fun recordDeleted(ids: Set<Long>) {
        if (trashed.removeAll { it.id in ids }) scheduleSave()
    }

    /** Uygulamanın bugün açıldığını kaydeder (başarımlarda "üst üste gün" için) */
    fun markActiveToday() {
        if (activeDays.add(today())) {
            historyRevision++
            scheduleSave()
        }
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
        existing?.let { absorb(it) }

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
        existing?.videos?.size ?: 0
    }

    fun dismissFolderPrompt() {
        folderPromptDismissed = true
        prefs.edit { putBoolean(KEY_DISMISSED, true) }
    }

    fun updateBackgroundPlay(enabled: Boolean) {
        backgroundPlay = enabled
        prefs.edit { putBoolean(KEY_BG_PLAY, enabled) }
    }

    fun updateMediaMode(mode: MediaMode) {
        mediaMode = mode
        mediaModeAsked = true
        prefs.edit {
            putString(KEY_MEDIA_MODE, mode.name)
            putBoolean(KEY_MEDIA_MODE_ASKED, true)
        }
        scheduleSave()
    }

    fun updateThemeMode(mode: ThemeMode) {
        themeMode = mode
        prefs.edit { putString(KEY_THEME_MODE, mode.name) }
        scheduleSave()
    }

    fun updateLeftHoldAction(action: EdgeHoldAction) {
        leftHoldAction = action
        prefs.edit { putString(KEY_LEFT_HOLD, action.name) }
        scheduleSave()
    }

    fun updateRightHoldAction(action: EdgeHoldAction) {
        rightHoldAction = action
        prefs.edit { putString(KEY_RIGHT_HOLD, action.name) }
        scheduleSave()
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

    /** Okunan veriyi şimdikilerle birleştirir: en çok izlenme, en son değişen beğeni */
    private fun absorb(saved: SavedData) {
        saved.videos.forEach { (key, other) -> stats[key] = merge(stat(key), other) }
        saved.days.forEach { (date, counts) ->
            val day = days.getOrPut(date) { HashMap() }
            counts.forEach { (key, n) -> day[key] = maxOf(day[key] ?: 0, n) }
        }
        activeDays += saved.activeDays
        saved.cleaned.forEach { (date, bytes) -> cleaned[date] = maxOf(cleaned[date] ?: 0L, bytes) }
        saved.trash.forEach { t -> if (trashed.none { it.id == t.id }) trashed += t }
        // Aynı başarım iki kayıtta da açılmışsa ilk açıldığı an geçerli
        saved.achievements.forEach { (id, at) -> unlocked[id] = minOf(unlocked[id] ?: at, at) }
        saved.hiddenAlbums.forEach { id -> if (id !in hiddenAlbums) hiddenAlbums += id }
        if (!mediaModeAsked && saved.mediaMode != null) {
            mediaMode = saved.mediaMode
            mediaModeAsked = true
            prefs.edit {
                putString(KEY_MEDIA_MODE, saved.mediaMode.name)
                putBoolean(KEY_MEDIA_MODE_ASKED, true)
            }
        }
        if (prefs.getString(KEY_THEME_MODE, null) == null && saved.themeMode != null) {
            themeMode = saved.themeMode
            prefs.edit { putString(KEY_THEME_MODE, saved.themeMode.name) }
        }
        if (prefs.getString(KEY_LEFT_HOLD, null) == null && saved.leftHoldAction != null) {
            leftHoldAction = saved.leftHoldAction
            prefs.edit { putString(KEY_LEFT_HOLD, saved.leftHoldAction.name) }
        }
        if (prefs.getString(KEY_RIGHT_HOLD, null) == null && saved.rightHoldAction != null) {
            rightHoldAction = saved.rightHoldAction
            prefs.edit { putString(KEY_RIGHT_HOLD, saved.rightHoldAction.name) }
        }
        historyRevision++
    }

    private suspend fun save() {
        val json = toJson(
            SavedData(
                stats.toMap(), days.mapValues { it.value.toMap() }, activeDays.toSet(), cleaned.toMap(), trashed.toList(),
                unlocked.toMap(), hiddenAlbums.toSet(),
                mediaMode = if (mediaModeAsked) mediaMode else null,
                themeMode = themeMode,
                leftHoldAction = leftHoldAction,
                rightHoldAction = rightHoldAction,
            ),
        )
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

    private fun readLocal(): SavedData? {
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

    internal companion object {
        const val KEY_TREE = "tree_uri"
        const val KEY_DIR = "dir_id"
        const val KEY_DISMISSED = "prompt_dismissed"
        const val KEY_LAST_SAVE = "last_folder_save"
        const val KEY_BG_PLAY = "background_play"
        const val KEY_MEDIA_MODE = "media_mode"
        const val KEY_MEDIA_MODE_ASKED = "media_mode_asked"
        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_LEFT_HOLD = "left_hold_action"
        const val KEY_RIGHT_HOLD = "right_hold_action"

        private val dayFormat = DateTimeFormatter.ISO_LOCAL_DATE

        fun today(): String = LocalDate.now().format(dayFormat)

        fun dayOf(millis: Long): String =
            Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate().format(dayFormat)

        fun computeBestStreak(activeDays: Set<String>): Int {
            var best = 0
            var run = 0
            var previous: LocalDate? = null
            for (day in activeDays.mapNotNull { runCatching { LocalDate.parse(it, dayFormat) }.getOrNull() }.sorted()) {
                run = if (previous != null && previous.plusDays(1) == day) run + 1 else 1
                best = maxOf(best, run)
                previous = day
            }
            return best
        }

        fun computeCurrentStreak(activeDays: Set<String>, today: LocalDate = LocalDate.now()): Int {
            val parsed = activeDays.mapNotNullTo(HashSet()) { runCatching { LocalDate.parse(it, dayFormat) }.getOrNull() }
            var cursor = when {
                today in parsed -> today
                today.minusDays(1) in parsed -> today.minusDays(1)
                else -> return 0
            }
            var streak = 0
            while (cursor in parsed) {
                streak++
                cursor = cursor.minusDays(1)
            }
            return streak
        }

        fun toJson(data: SavedData): String {
            val videos = JSONObject()
            for ((key, s) in data.videos) {
                if (!s.liked && s.views == 0 && s.likeChangedAt == 0L) continue
                videos.put(
                    key,
                    JSONObject()
                        .put("liked", s.liked)
                        .put("likes", s.likes)
                        .put("likeChangedAt", s.likeChangedAt)
                        .put("views", s.views)
                        .put("lastViewedAt", s.lastViewedAt),
                )
            }
            val days = JSONObject()
            for ((date, counts) in data.days.toSortedMap()) {
                days.put(date, JSONObject(counts as Map<*, *>))
            }
            return JSONObject()
                .put("app", "Galeri Reels")
                .put("format", 2)
                .put("updatedAt", System.currentTimeMillis())
                .put("videos", videos)
                .put("days", days)
                .put("activeDays", JSONArray(data.activeDays.sorted()))
                .put("cleaned", JSONObject(data.cleaned.toSortedMap() as Map<*, *>))
                .put("achievements", JSONObject(data.achievements.toSortedMap() as Map<*, *>))
                .put("hiddenAlbums", JSONArray(data.hiddenAlbums.sorted()))
                .apply {
                    data.mediaMode?.let { put("mediaMode", it.name) }
                    data.themeMode?.let { put("themeMode", it.name) }
                    data.leftHoldAction?.let { put("leftHoldAction", it.name) }
                    data.rightHoldAction?.let { put("rightHoldAction", it.name) }
                }
                .put(
                    "trash",
                    JSONArray(
                        data.trash.map {
                            JSONObject()
                                .put("id", it.id)
                                .put("key", it.key)
                                .put("name", it.name)
                                .put("bytes", it.bytes)
                                .put("at", it.at)
                                .put("isPhoto", it.isPhoto)
                        },
                    ),
                )
                .toString(2)
        }

        fun parse(text: String): SavedData {
            val root = JSONObject(text)
            val videos = root.optJSONObject("videos")
            val videoStats = videos?.keys()?.asSequence()?.associateWith { key ->
                val o = videos.getJSONObject(key)
                val liked = o.optBoolean("liked")
                VideoStat(
                    liked = liked,
                    // 1.3 öncesi dosyalarda sayı yok; beğenilmişse bir beğeni
                    likes = o.optInt("likes", if (liked) 1 else 0),
                    likeChangedAt = o.optLong("likeChangedAt"),
                    views = o.optInt("views"),
                    lastViewedAt = o.optLong("lastViewedAt"),
                )
            }.orEmpty()
            // 0.4'ün dosyalarında (format 1) günlük geçmiş yok
            val days = root.optJSONObject("days")
            val dayCounts = days?.keys()?.asSequence()?.associateWith { date ->
                val o = days.getJSONObject(date)
                o.keys().asSequence().associateWith { key -> o.optInt(key) }
            }.orEmpty()
            val active = root.optJSONArray("activeDays")
            val activeDays = (0 until (active?.length() ?: 0)).map { active!!.getString(it) }.toSet()
            val cleanedJson = root.optJSONObject("cleaned")
            val cleaned = cleanedJson?.keys()?.asSequence()?.associateWith { cleanedJson.optLong(it) }.orEmpty()
            val trashJson = root.optJSONArray("trash")
            val trash = (0 until (trashJson?.length() ?: 0)).map { i ->
                val o = trashJson!!.getJSONObject(i)
                TrashedVideo(
                    o.optLong("id"),
                    o.optString("key"),
                    o.optString("name"),
                    o.optLong("bytes"),
                    o.optLong("at"),
                    o.optBoolean("isPhoto"),
                )
            }
            val achievementsJson = root.optJSONObject("achievements")
            val achievements = achievementsJson?.keys()?.asSequence()?.associateWith { achievementsJson.optLong(it) }.orEmpty()
            val hiddenJson = root.optJSONArray("hiddenAlbums")
            val hiddenAlbums = (0 until (hiddenJson?.length() ?: 0)).map { hiddenJson!!.getString(it) }.toSet()
            val mediaMode = root.optString("mediaMode", "").let { name -> MediaMode.entries.firstOrNull { it.name == name } }
            val themeMode = root.optString("themeMode", "").let { name -> ThemeMode.entries.firstOrNull { it.name == name } }
            val leftHoldAction = root.optString("leftHoldAction", "").let { name -> EdgeHoldAction.entries.firstOrNull { it.name == name } }
            val rightHoldAction = root.optString("rightHoldAction", "").let { name -> EdgeHoldAction.entries.firstOrNull { it.name == name } }
            return SavedData(
                videoStats, dayCounts, activeDays, cleaned, trash, achievements, hiddenAlbums,
                mediaMode, themeMode, leftHoldAction, rightHoldAction,
            )
        }

        /** İki kaydı birleştirir: en çok izlenme, en son değişen beğeni */
        fun merge(a: VideoStat, b: VideoStat) = VideoStat(
            liked = if (b.likeChangedAt > a.likeChangedAt) b.liked else a.liked,
            likes = if (b.likeChangedAt > a.likeChangedAt) b.likes else a.likes,
            likeChangedAt = maxOf(a.likeChangedAt, b.likeChangedAt),
            views = maxOf(a.views, b.views),
            lastViewedAt = maxOf(a.lastViewedAt, b.lastViewedAt),
        )
    }
}
