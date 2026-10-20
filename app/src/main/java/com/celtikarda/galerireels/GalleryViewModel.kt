package com.celtikarda.galerireels

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

class GalleryViewModel(app: Application) : AndroidViewModel(app) {

    /** Beğeniler ve izlenmeler; uygulama silinse de kalsın diye kullanıcının seçtiği klasöre de yazılır */
    val stats = StatsStore(app, viewModelScope).also { it.load() }
    // Temizlikte silinince değişir; ekranlar yenilensin diye gözlenebilir
    private var all by mutableStateOf<List<VideoItem>>(emptyList())
    private var folders by mutableStateOf<List<Album>>(emptyList())
    private var lastToken: Any? = null
    private var pendingVideoId: Long? = null

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

    val videoCount: Int get() = all.size
    val folderCount: Int get() = folders.size

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
            pendingVideoId?.let { openVideo(it) }
        }
    }

    /** Ekrandaki kutucuklar: Tüm videolar, Favoriler, Beğendiklerin, sonra klasörler */
    fun albumTiles(): List<Album> {
        if (all.isEmpty()) return emptyList()
        val liked = all.filter { isLiked(it) }
        val favorites = all.filter { isFavorite(it) }
        return buildList {
            add(Album(Album.ALL, "Tüm videolar", all.size, all.first()))
            if (favorites.isNotEmpty()) add(Album(Album.FAVORITES, "Favoriler", favorites.size, favorites.first()))
            if (liked.isNotEmpty()) add(Album(Album.LIKED, "Beğendiklerin", liked.size, liked.first()))
            addAll(folders)
        }
    }

    fun isFavorite(item: VideoItem): Boolean = item.id in favoriteIds

    /** Galeriyi baştan okur (ör. çöp kutusundan geri getirilenler görünsün diye) */
    fun reloadVideos() {
        if (!loaded) return
        viewModelScope.launch {
            all = MediaRepository.loadVideos(getApplication())
            folders = buildFolders(all)
            favoriteIds = all.filter { it.favorite }.mapTo(HashSet()) { it.id }
        }
    }

    // --- Temizlik

    /** Temizlikte "silinecek" diye işaretlenenler (henüz silinmedi) */
    var cleanupMarked by mutableStateOf<List<VideoItem>>(emptyList())
        private set

    /** Silinmeye aday olanlar: beğenilen ve favori olanlar temizlikte hiç gösterilmez */
    private fun cleanupCandidates(): List<VideoItem> = all.filter { !isLiked(it) && !isFavorite(it) }

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
        folders = buildFolders(all)
        cleanupMarked = emptyList()
    }

    /** Favori isteği telefon tarafından onaylandı (ya da onay gerekmedi) */
    fun setFavorite(item: VideoItem, favorite: Boolean) {
        favoriteIds = if (favorite) favoriteIds + item.id else favoriteIds - item.id
    }

    /** Uygulamaya dönülünce: favoriler galeri uygulamasından değişmiş olabilir */
    fun refreshFavorites() {
        if (!loaded) return
        viewModelScope.launch { favoriteIds = MediaRepository.favoriteIds(getApplication()) }
    }

    fun coverOf(albumId: String): VideoItem? = folders.firstOrNull { it.id == albumId }?.cover

    fun albumNameOf(item: VideoItem): String = folders.firstOrNull { it.id == item.albumId }?.name ?: item.albumName ?: "Video"

    fun open(album: Album) {
        source = when (album.id) {
            Album.ALL -> all
            Album.LIKED -> all.filter { isLiked(it) }
            Album.FAVORITES -> all.filter { isFavorite(it) }
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
    }

    /** Çift dokunma: Instagram'daki gibi sadece beğenir, beğeniyi geri almaz */
    fun like(item: VideoItem) {
        if (!isLiked(item)) stats.setLiked(item.key, true)
    }

    fun addView(item: VideoItem) {
        stats.addView(item.key)
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
