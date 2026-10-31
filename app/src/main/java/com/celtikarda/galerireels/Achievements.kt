package com.celtikarda.galerireels

/** Başarımların hesaplandığı sayılar (hepsi kayıtlı verilerden çıkar) */
class AchievementInput(
    /** En az bir kez izlenmiş farklı video sayısı */
    val watchedVideos: Long,
    val likes: Long,
    val favorites: Long,
    /** En az bir videosu izlenmiş klasör sayısı */
    val foldersVisited: Long,
    val totalFolders: Long,
    val cleanedBytes: Long,
)

class Achievement(
    val id: String,
    val emoji: String,
    val title: String,
    val description: String,
    /** Açılması için gereken değer */
    val target: Long,
    val progress: (AchievementInput) -> Long,
    /** İlerleme yazısı, ör. "340 MB / 1 GB" (yoksa "73 / 100") */
    val format: ((Long) -> String)? = null,
) {
    fun isUnlocked(input: AchievementInput): Boolean = progress(input) >= target
}

private const val MB = 1024L * 1024
private const val GB = 1024L * MB

private fun megabytes(bytes: Long): String =
    if (bytes >= GB) String.format(java.util.Locale.forLanguageTag("tr-TR"), "%.1f GB", bytes.toDouble() / GB)
    else "${bytes / MB} MB"

/**
 * Bütün başarımlar, ekranda bu sırayla görünür. Sadece arşivi keşfetmeyi ve yer açmayı ödüllendirir;
 * izlenme sayısı ve günlük seri rozetleri 1.4'te çıkarıldı (eski kayıtlarda kalan id'ler sayılmaz).
 */
val achievements: List<Achievement> = listOf(
    Achievement("watched_50", "🧭", "Kaşif", "50 farklı video izle", 50, { it.watchedVideos }),
    Achievement("watched_250", "🗺️", "Arşiv uzmanı", "250 farklı video izle", 250, { it.watchedVideos }),
    Achievement(
        "folders_all", "🌍", "Her köşe", "Bütün klasörlerden en az bir video izle", 1,
        { if (it.totalFolders > 0 && it.foldersVisited >= it.totalFolders) 1 else 0 },
        format = { "" },
    ),
    Achievement("likes_10", "❤️", "Beğeni yağmuru", "10 video beğen", 10, { it.likes }),
    Achievement("favorites_10", "⭐", "Koleksiyoncu", "10 videoyu favorilere ekle", 10, { it.favorites }),
    Achievement("clean_100mb", "🧹", "Temizlikçi", "100 MB yer aç", 100 * MB, { it.cleanedBytes }, ::megabytes),
    Achievement("clean_1gb", "🧽", "Yer açıcı", "1 GB yer aç", GB, { it.cleanedBytes }, ::megabytes),
    Achievement("clean_10gb", "🚛", "Büyük temizlik", "10 GB yer aç", 10 * GB, { it.cleanedBytes }, ::megabytes),
)
