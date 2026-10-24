package com.celtikarda.galerireels

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.celtikarda.galerireels.ui.formatMonth
import kotlinx.coroutines.launch
import java.time.YearMonth

class GalleryViewModel(app: Application) : AndroidViewModel(app) {

    /** Beğeniler ve izlenmeler; uygulama silinse de kalsın diye kullanıcının seçtiği klasöre de yazılır */
    val stats = StatsStore(app, viewModelScope).also { it.load() }
    // Temizlikte silinince değişir; ekranlar yenilensin diye gözlenebilir
    private var all by mutableStateOf<List<VideoItem>>(emptyList())
    private var folders by mutableStateOf<List<Album>>(emptyList())
    private var lastToken: Any? = null
    private var pendingVideoId: Long? = null

    /** Gizlenen klasörler dışındaki görünür videolar */
    private val visibleVideos: List<VideoItem>
        get() {
            val hidden = stats.hiddenAlbums.toSet()
            return if (hidden.isEmpty()) all else all.filter { it.albumId !in hidden }
        }

    /** Gizlenen klasörler dışındaki görünür klasörler */
    private val visibleFolders: List<Album>
        get() {
            val hidden = stats.hiddenAlbums.toSet()
            return if (hidden.isEmpty()) folders else folders.filter { it.id !in hidden }
        }

    /** Ayarlar ekranında listelenecek gizlenmiş klasörler */
    val hiddenFolders: List<Album>
        get() {
            val hidden = stats.hiddenAlbums.toSet()
            return if (hidden.isEmpty()) emptyList() else folders.filter { it.id in hidden }
        }

    /** Açık klasörün videoları, açıldığı andaki haliyle (beğeni kaldırınca liste kaymasın) */
    private var source: List<VideoItem> = emptyList()

    var loaded by mutableStateOf(false)
        private set
    var shuffled by mutableStateOf(true)
        private set
    var openAlbum by mutableStateOf<Album?>(null)
        private set

    /** Reels ekranında gösterilen sıra (karışık ya da yeniden eskiye) */
    var items by mutableStateOf<List<VideoItem>>(emptyList())
        private set

    /** Telefonun galerisinde favori olan videolar (galeri uygulamasıyla ortak liste) */
    var favoriteIds by mutableStateOf<Set<Long>>(emptySet())
        private set

    /** Galerideki toplam video sayısı (gizlenenler dahil; izin ekranı kontrolü için) */
    val totalVideoCount: Int get() = all.size
    val videoCount: Int get() = visibleVideos.size
    val folderCount: Int get() = visibleFolders.size

    /** Aynı token ile tekrar çağrılırsa (ör. ekran döndüğünde) yeniden yüklemez. */
    fun load(token: Any) {
        if (token == lastToken) return
        lastToken = token
        viewModelScope.launch {
            all = MediaRepository.loadVideos(getApplication())
            folders = buildFolders(all)
            favoriteIds = all.filter { it.favorite }.mapTo(HashSet()) { it.id }
            stats.awaitLoaded()
            migrateOldLikes()
            loaded = true
            checkAchievements()
            pendingVideoId?.let { openVideo(it) }
            scanDuplicates()
        }
    }

    /** Ekrandaki kutucuklar: Tüm videolar, Favoriler, Beğendiklerin, sonra klasörler */
    fun albumTiles(): List<Album> {
        val visible = visibleVideos
        if (visible.isEmpty()) return emptyList()
        val liked = visible.filter { isLiked(it) }
        val favorites = visible.filter { isFavorite(it) }
        return buildList {
            add(Album(Album.ALL, "Tüm videolar", visible.size, visible.first()))
            if (favorites.isNotEmpty()) add(Album(Album.FAVORITES, "Favoriler", favorites.size, favorites.first()))
            if (liked.isNotEmpty()) add(Album(Album.LIKED, "Beğendiklerin", liked.size, liked.first()))
            addAll(visibleFolders)
        }
    }

    /** Klasörü ana ekrandan, Tüm videolar'dan, Tarihte bugün'den ve Temizlik'ten gizler */
    fun hideAlbum(albumId: String) {
        stats.hideAlbum(albumId)
        scanDuplicates()
    }

    /** Gizlenen klasörü tekrar görünür yapar */
    fun unhideAlbum(albumId: String) {
        stats.unhideAlbum(albumId)
        scanDuplicates()
    }

    /** Tüm gizlenen klasörleri tekrar görünür yapar */
    fun unhideAllAlbums() {
        stats.unhideAllAlbums()
        scanDuplicates()
    }

    fun isFavorite(item: VideoItem): Boolean = item.id in favoriteIds

    /** Galeriyi baştan okur (ör. çöp kutusundan geri getirilenler görünsün diye) */
    fun reloadVideos() {
        if (!loaded) return
        viewModelScope.launch {
            all = MediaRepository.loadVideos(getApplication())
            folders = buildFolders(all)
            favoriteIds = all.filter { it.favorite }.mapTo(HashSet()) { it.id }
            scanDuplicates()
        }
    }

    // --- Temizlik

    /** Temizlikte "silinecek" diye işaretlenenler (henüz silinmedi) */
    var cleanupMarked by mutableStateOf<List<VideoItem>>(emptyList())
        private set

    /** Algısal görüntü özeti (dHash) ile tespit edilen fazlalık kopya videolar */
    var duplicateCopies by mutableStateOf<List<VideoItem>>(emptyList())
        private set

    private fun scanDuplicates() {
        val snapshot = visibleVideos
        val keepKeys = snapshot.filter { isLiked(it) || isFavorite(it) }.mapTo(HashSet()) { it.key }
        viewModelScope.launch {
            val found = DuplicateFinder.findExtraCopies(getApplication(), snapshot, keepKeys)
            val currentIds = visibleVideos.mapTo(HashSet()) { it.id }
            duplicateCopies = found.filter { it.id in currentIds && !isLiked(it) && !isFavorite(it) }
        }
    }

    /** Sadece tespit edilen kopya/benzer videolarla temizlik modunu başlatır */
    fun openDuplicateCleanup() {
        val copies = duplicateCopies.filter { !isLiked(it) && !isFavorite(it) }
        if (copies.isEmpty()) return
        source = copies
        items = copies
        cleanupMarked = emptyList()
        openAlbum = Album(Album.CLEANUP, "Kopya videolar", copies.size, copies.first())
    }

    /** Silinmeye aday olanlar: beğenilen, favori ve gizlenen klasörlerdeki videolar temizlikte gösterilmez */
    private fun cleanupCandidates(): List<VideoItem> = visibleVideos.filter { !isLiked(it) && !isFavorite(it) }

    /** Hiç izlenmemiş (beğenilmemiş, favori olmayan) videolar */
    fun unwatched(): List<VideoItem> = cleanupCandidates().filter { viewsOf(it) == 0 }

    /** [firstIds]: sadece test için, bu videolar en başa alınır */
    fun openCleanup(firstIds: List<Long> = emptyList()) {
        // Önce hiç izlemediklerin, sonra izlediklerin; ikisi de en çok yer kaplayandan başlar
        val sorted = cleanupCandidates().sortedWith(compareBy<VideoItem> { viewsOf(it) > 0 }.thenByDescending { it.sizeBytes })
        source = sorted.filter { it.id in firstIds } + sorted.filter { it.id !in firstIds }
        if (source.isEmpty()) return
        items = source
        cleanupMarked = emptyList()
        openAlbum = Album(Album.CLEANUP, "Temizlik", source.size, source.first())
    }

    /** Sola kaydırıldı: silinecekler listesine girer, sıradaki video gelir */
    fun markForCleanup(item: VideoItem) {
        if (item in cleanupMarked) return
        cleanupMarked = cleanupMarked + item
        items = items - item
    }

    /** Son işareti kaldırır; video [index] sırasına (ekrandaki yere) geri konur */
    fun undoLastMark(index: Int) {
        val last = cleanupMarked.lastOrNull() ?: return
        cleanupMarked = cleanupMarked.dropLast(1)
        items = items.toMutableList().apply { add(index.coerceIn(0, size), last) }
    }

    fun clearCleanupMarks() {
        cleanupMarked = emptyList()
    }

    /** İşaretlenenler çöp kutusuna taşındı */
    fun onTrashed(videos: List<VideoItem>) {
        stats.recordTrashed(videos)
        val ids = videos.mapTo(HashSet()) { it.id }
        all = all.filter { it.id !in ids }
        duplicateCopies = duplicateCopies.filter { it.id !in ids }
        folders = buildFolders(all)
        cleanupMarked = emptyList()
        checkAchievements()
    }

    /** Favori isteği telefon tarafından onaylandı (ya da onay gerekmedi) */
    fun setFavorite(item: VideoItem, favorite: Boolean) {
        favoriteIds = if (favorite) favoriteIds + item.id else favoriteIds - item.id
        checkAchievements()
    }

    /** Uygulamaya dönülünce: favoriler galeri uygulamasından değişmiş olabilir */
    fun refreshFavorites() {
        if (!loaded) return
        viewModelScope.launch {
            favoriteIds = MediaRepository.favoriteIds(getApplication())
            checkAchievements()
        }
    }

    // --- Son izlenenler

    /** En son izlenen videolar, yeniden eskiye (gizli klasörler hariç) */
    fun recentVideos(limit: Int): List<VideoItem> {
        val byKey = visibleVideos.associateBy { it.key }
        return stats.recentKeys(limit).mapNotNull { byKey[it] }
    }

    /** "Son izlediklerin"i dokunulan videodan başlatır */
    fun openRecent(first: VideoItem) {
        val recent = recentVideos(200)
        if (recent.isEmpty()) return
        source = recent
        items = listOf(first) + recent.filter { it.id != first.id }
        openAlbum = Album(Album.RECENT, "Son izlediklerin", recent.size, first)
    }

    // --- Tarihte bugün (Anılar)

    /** Geçmiş yıllarda bugün / bu hafta çekilmiş videolar (gizli klasörler hariç) */
    fun memories(): List<VideoItem> = Memories.find(visibleVideos)

    /** "Tarihte bugün" videolarını dokunulan videodan başlatır */
    fun openMemories(first: VideoItem) {
        val list = memories()
        if (list.isEmpty()) return
        source = list
        items = listOf(first) + list.filter { it.id != first.id }
        openAlbum = Album(Album.MEMORIES, "Tarihte bugün", list.size, first)
    }

    // --- Aylık özet

    /** Özet ekranında seçilebilecek aylar (en yeniden eskiye) */
    fun availableMonths(): List<YearMonth> = stats.availableMonths()

    /** Verilen ayın izleme ve temizlik özeti */
    fun monthlyRecap(month: YearMonth = YearMonth.now()): MonthlyRecap = buildMonthlyRecap(
        month = month,
        allVideos = all,
        folders = folders,
        viewsByKey = stats.monthViews(month),
        activeDays = stats.monthActiveDays(month),
        cleanedBytes = stats.monthCleanedBytes(month),
        newLikes = stats.monthLikedCount(month),
        lastViewedAtOf = { stats.stat(it).lastViewedAt },
        albumNameOf = ::albumNameOf,
    )

    /** Aylık özetteki videoları dokunulan videodan başlayarak Reels ekranında açar */
    fun openMonthly(month: YearMonth, first: VideoItem) {
        val viewsByKey = stats.monthViews(month)
        val byKey = all.associateBy { it.key }
        val ordered = viewsByKey.entries
            .sortedByDescending { it.value }
            .mapNotNull { byKey[it.key] }
        if (ordered.isEmpty()) return
        source = ordered
        items = listOf(first) + ordered.filter { it.id != first.id }
        openAlbum = Album(Album.MONTHLY, formatMonth(month), ordered.size, first)
    }

    // --- Başarımlar

    /** Yeni açılan başarımlar; ekranda kutlama gösterilince boşaltılır */
    var newlyUnlocked by mutableStateOf<List<Achievement>>(emptyList())
        private set

    fun achievementInput(): AchievementInput {
        val watched = stats.watchedKeys()
        val albumOfKey = all.associate { it.key to it.albumId }
        return AchievementInput(
            totalViews = stats.totalViews.toLong(),
            watchedVideos = watched.size.toLong(),
            maxViewsOfOne = stats.maxViewsOfOne.toLong(),
            likes = stats.totalLikes.toLong(),
            favorites = favoriteIds.size.toLong(),
            foldersVisited = watched.mapNotNullTo(HashSet()) { albumOfKey[it] }.size.toLong(),
            totalFolders = folders.size.toLong(),
            cleanedBytes = stats.cleanedTotal,
            bestStreak = stats.bestStreak().toLong(),
        )
    }

    /** Koşulu sağlanıp henüz açılmamış başarımları açar */
    fun checkAchievements() {
        if (!loaded) return
        val input = achievementInput()
        val fresh = achievements.filter { it.id !in stats.unlocked && it.isUnlocked(input) }
        if (fresh.isEmpty()) return
        stats.recordUnlocked(fresh.map { it.id })
        newlyUnlocked = newlyUnlocked + fresh
    }

    fun consumeUnlocked() {
        newlyUnlocked = emptyList()
    }

    fun coverOf(albumId: String): VideoItem? = folders.firstOrNull { it.id == albumId }?.cover

    fun albumNameOf(item: VideoItem): String = folders.firstOrNull { it.id == item.albumId }?.name ?: item.albumName ?: "Video"

    fun open(album: Album) {
        val visible = visibleVideos
        source = when (album.id) {
            Album.ALL -> visible
            Album.LIKED -> visible.filter { isLiked(it) }
            Album.FAVORITES -> visible.filter { isFavorite(it) }
            else -> all.filter { it.albumId == album.id }
        }
        items = if (shuffled) source.shuffled() else source
        openAlbum = album
    }

    fun close() {
        openAlbum = null
    }

    /** Test için: verilen videoyu "Tüm videolar" listesinin başında açar. */
    fun openVideo(id: Long) {
        if (!loaded) {
            pendingVideoId = id
            return
        }
        pendingVideoId = null
        val target = all.firstOrNull { it.id == id } ?: return
        source = all
        items = listOf(target) + all.filter { it.id != id }
        openAlbum = Album(Album.ALL, "Tüm videolar", all.size, all.first())
    }

    /** Sırayı değiştirir; o an bakılan video yerinde kalsın diye yeni indeksini döndürür. */
    fun toggleShuffle(currentId: Long?): Int {
        shuffled = !shuffled
        val current = source.firstOrNull { it.id == currentId }
        items = when {
            !shuffled -> source
            current != null -> listOf(current) + source.filter { it.id != currentId }.shuffled()
            else -> source.shuffled()
        }
        return items.indexOfFirst { it.id == currentId }.coerceAtLeast(0)
    }

    fun isLiked(item: VideoItem): Boolean = stats.stat(item.key).liked

    fun viewsOf(item: VideoItem): Int = stats.stat(item.key).views

    fun toggleLike(item: VideoItem) {
        stats.setLiked(item.key, !isLiked(item))
        checkAchievements()
    }

    /** Çift dokunma: Instagram'daki gibi sadece beğenir, beğeniyi geri almaz */
    fun like(item: VideoItem) {
        if (!isLiked(item)) stats.setLiked(item.key, true)
        checkAchievements()
    }

    fun addView(item: VideoItem) {
        stats.addView(item.key)
        checkAchievements()
    }

    /** 0.2–0.3'te beğenilenleri (galeri numarasıyla tutuluyordu) bir kez yeni kayda taşır */
    private fun migrateOldLikes() {
        val oldLikes = LikesStore(getApplication())
        val ids = oldLikes.load()
        if (ids.isEmpty()) return
        all.filter { it.id in ids }.forEach { stats.setLiked(it.key, true) }
        oldLikes.clear()
    }

    private fun buildFolders(videos: List<VideoItem>): List<Album> {
        // videolar yeniden eskiye sıralı, yani her grubun ilki en yenisi
        val groups = videos.groupBy { it.albumId }.map { (id, list) ->
            val first = list.first()
            Album(id, first.albumName ?: first.albumPath.substringAfterLast('/').ifEmpty { "Adsız" }, list.size, first)
        }
        // "Video" gibi aynı adlı klasörler varsa üst klasörü de yaz: "YTDLnis/Video"
        val duplicates = groups.groupingBy { it.name.lowercase() }.eachCount().filterValues { it > 1 }.keys
        return groups
            .map { album ->
                if (album.name.lowercase() !in duplicates) album
                else album.copy(name = album.cover.albumPath.split('/').takeLast(2).joinToString("/"))
            }
            .sortedByDescending { it.cover.dateMillis }
    }
}
