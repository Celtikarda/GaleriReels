package com.celtikarda.galerireels

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.content.FileProvider
import com.celtikarda.galerireels.ui.formatCount
import com.celtikarda.galerireels.ui.formatMonth
import com.celtikarda.galerireels.ui.formatSize
import com.celtikarda.galerireels.ui.formatWatchTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.YearMonth

/** Bir ayda en çok izlenen videolardan biri */
data class RankedVideo(
    val video: VideoItem,
    val views: Int,
)

/** Bir ayda en çok izlenen klasör */
data class RankedAlbum(
    val id: String,
    val name: String,
    val cover: VideoItem,
    val views: Int,
    /** Bu klasörden o ay kaç farklı video izlendi */
    val watchedVideos: Int,
)

/** Bir ayın izleme ve temizlik özeti (Spotify Wrapped benzeri kart için) */
data class MonthlyRecap(
    val month: YearMonth,
    val totalViews: Int,
    val uniqueVideos: Int,
    val watchTimeMs: Long,
    val activeDays: Int,
    val cleanedBytes: Long,
    val newLikes: Int,
    val topVideos: List<RankedVideo>,
    val topAlbum: RankedAlbum?,
) {
    val hasActivity: Boolean get() = totalViews > 0 || cleanedBytes > 0 || newLikes > 0
}

/** Verilen ayın verilerinden [MonthlyRecap] hesaplar. */
internal fun buildMonthlyRecap(
    month: YearMonth,
    allVideos: List<VideoItem>,
    folders: List<Album>,
    viewsByKey: Map<String, Int>,
    activeDays: Int,
    cleanedBytes: Long,
    newLikes: Int,
    lastViewedAtOf: (String) -> Long,
    albumNameOf: (VideoItem) -> String,
): MonthlyRecap {
    val byKey = allVideos.associateBy { it.key }
    val folderById = folders.associateBy { it.id }

    val totalViews = viewsByKey.values.sum()
    val uniqueVideos = viewsByKey.size

    var watchTimeMs = 0L
    val rankedVideos = ArrayList<RankedVideo>()
    // albumId → (toplam izlenme, izlenen farklı video sayısı, en çok izlenen video)
    val albumTotals = HashMap<String, Triple<Int, Int, RankedVideo>>()

    for ((key, count) in viewsByKey) {
        val video = byKey[key] ?: continue
        watchTimeMs += video.durationMs.coerceAtLeast(5_000L) * count
        val ranked = RankedVideo(video, count)
        rankedVideos += ranked

        val prev = albumTotals[video.albumId]
        val bestVideo = if (prev == null || count > prev.third.views) ranked else prev.third
        albumTotals[video.albumId] = Triple((prev?.first ?: 0) + count, (prev?.second ?: 0) + 1, bestVideo)
    }

    val topVideos = rankedVideos.sortedWith(
        compareByDescending<RankedVideo> { it.views }
            .thenByDescending { lastViewedAtOf(it.video.key) },
    ).take(5)

    val topAlbum = albumTotals.entries
        .maxWithOrNull(compareBy<Map.Entry<String, Triple<Int, Int, RankedVideo>>> { it.value.first }.thenBy { it.value.second })
        ?.let { (albumId, triple) ->
            val folder = folderById[albumId]
            val fallback = triple.third.video
            RankedAlbum(
                id = albumId,
                name = folder?.name ?: albumNameOf(fallback),
                cover = folder?.cover ?: fallback,
                views = triple.first,
                watchedVideos = triple.second,
            )
        }

    return MonthlyRecap(
        month = month,
        totalViews = totalViews,
        uniqueVideos = uniqueVideos,
        watchTimeMs = watchTimeMs,
        activeDays = activeDays,
        cleanedBytes = cleanedBytes,
        newLikes = newLikes,
        topVideos = topVideos,
        topAlbum = topAlbum,
    )
}

/**
 * Aylık özeti 1080×1920 boyutunda (hikâye / durum paylaşımına uygun) bir görsel olarak çizer
 * ve Android'in paylaşım penceresini açar.
 */
object RecapCardShare {

    private const val W = 1080
    private const val H = 1920

    suspend fun share(context: Context, recap: MonthlyRecap, albumNameOf: (VideoItem) -> String) {
        val file = withContext(Dispatchers.IO) {
            val bitmap = render(context, recap, albumNameOf)
            val dir = File(context.cacheDir, "paylasim").apply { mkdirs() }
            File(dir, "galerireels-ozet-${recap.month}.png").also { out ->
                out.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri(null, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Özeti paylaş"))
    }

    private suspend fun render(context: Context, recap: MonthlyRecap, albumNameOf: (VideoItem) -> String): Bitmap {
        val bitmap = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Arka plan: koyu mor-kırmızı-siyah çapraz geçiş
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, W.toFloat(), H.toFloat(),
                intArrayOf(0xFF2B1055.toInt(), 0xFF5E1742.toInt(), 0xFF140D1E.toInt(), 0xFF0B0B10.toInt()),
                floatArrayOf(0f, 0.32f, 0.7f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, 0f, W.toFloat(), H.toFloat(), bgPaint)

        // Üstte hafif parlama halkası
        val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x22FF6B4A
        }
        canvas.drawCircle(W * 0.85f, 180f, 340f, glowPaint)

        val bold = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        val medium = Typeface.create("sans-serif-medium", Typeface.NORMAL)

        // Üst başlık: rozet + ay adı
        val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x33FFFFFF }
        canvas.drawRoundRect(RectF(72f, 84f, 410f, 144f), 30f, 30f, pillPaint)
        val pillText = textPaint(26f, 0xFFFFC83D.toInt(), bold)
        canvas.drawText("GALERİ REELS ÖZETİ", 102f, 123f, pillText)

        val titlePaint = textPaint(74f, 0xFFFFFFFF.toInt(), bold)
        canvas.drawText(formatMonth(recap.month), 72f, 240f, titlePaint)

        // 4'lü istatistik kutuları (2x2)
        val stats = listOf(
            "TOPLAM İZLENME" to "${formatCount(recap.totalViews)} kez",
            "İZLEME SÜRESİ" to formatWatchTime(recap.watchTimeMs),
            "FARKLI VİDEO" to "${formatCount(recap.uniqueVideos)} video",
            if (recap.cleanedBytes > 0) "AÇILAN YER" to formatSize(recap.cleanedBytes)
            else "AKTİF GÜN" to "${recap.activeDays} gün",
        )
        val boxW = (W - 72 * 2 - 28) / 2f
        val boxH = 176f
        val cardFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x24FFFFFF }
        val cardStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x2EFFFFFF
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        val labelPaint = textPaint(24f, 0xB3FFFFFF.toInt(), medium)
        val valuePaint = textPaint(48f, 0xFFFFFFFF.toInt(), bold)

        stats.forEachIndexed { i, (label, value) ->
            val col = i % 2
            val row = i / 2
            val left = 72f + col * (boxW + 28f)
            val top = 290f + row * (boxH + 24f)
            val rect = RectF(left, top, left + boxW, top + boxH)
            canvas.drawRoundRect(rect, 32f, 32f, cardFill)
            canvas.drawRoundRect(rect, 32f, 32f, cardStroke)
            canvas.drawText(label, left + 32f, top + 64f, labelPaint)
            canvas.drawText(value, left + 32f, top + 132f, valuePaint)
        }

        var y = 716f

        // En sevdiğin klasör
        recap.topAlbum?.let { album ->
            val sectionPaint = textPaint(28f, 0xCCFFFFFF.toInt(), bold)
            canvas.drawText("EN SEVDİĞİN KLASÖR", 72f, y, sectionPaint)
            y += 24f

            val rect = RectF(72f, y, W - 72f, y + 172f)
            canvas.drawRoundRect(rect, 32f, 32f, cardFill)
            canvas.drawRoundRect(rect, 32f, 32f, cardStroke)

            val thumb = loadThumbBitmap(context, album.cover.uri, 256)
            val thumbRect = RectF(96f, y + 24f, 96f + 124f, y + 24f + 124f)
            drawThumb(canvas, thumb, thumbRect, 24f)

            val namePaint = textPaint(38f, 0xFFFFFFFF.toInt(), bold)
            val subPaint = textPaint(28f, 0xB3FFFFFF.toInt(), medium)
            val maxTextW = W - 72f - 252f - 32f
            val name = TextUtils.ellipsize(album.name, namePaint, maxTextW, TextUtils.TruncateAt.END).toString()
            canvas.drawText(name, 248f, y + 78f, namePaint)
            canvas.drawText(
                "${formatCount(album.views)} izlenme · ${formatCount(album.watchedVideos)} farklı video",
                248f,
                y + 124f,
                subPaint,
            )
            y += 224f
        }

        // En çok izlenen videolar (ilk 4)
        if (recap.topVideos.isNotEmpty()) {
            val sectionPaint = textPaint(28f, 0xCCFFFFFF.toInt(), bold)
            canvas.drawText("BU AY EN ÇOK İZLEDİKLERİN", 72f, y, sectionPaint)
            y += 24f

            val shown = recap.topVideos.take(4)
            val rowH = 160f
            for ((index, ranked) in shown.withIndex()) {
                val rect = RectF(72f, y, W - 72f, y + rowH)
                canvas.drawRoundRect(rect, 28f, 28f, cardFill)

                // Sıra numarası
                val rankPaint = textPaint(36f, 0xFFFFC83D.toInt(), bold)
                canvas.drawText("#${index + 1}", 102f, y + 94f, rankPaint)

                // Küçük resim
                val thumb = loadThumbBitmap(context, ranked.video.uri, 240)
                val thumbRect = RectF(172f, y + 20f, 172f + 120f, y + 20f + 120f)
                drawThumb(canvas, thumb, thumbRect, 20f)

                // Video adı ve klasörü
                val vNamePaint = textPaint(32f, 0xFFFFFFFF.toInt(), bold)
                val vSubPaint = textPaint(25f, 0xB3FFFFFF.toInt(), medium)
                val badgeText = "${formatCount(ranked.views)} kez"
                val maxW = W - 72f - 320f - 190f
                val vName = TextUtils.ellipsize(ranked.video.name.substringBeforeLast('.'), vNamePaint, maxW, TextUtils.TruncateAt.END).toString()
                val folderName = TextUtils.ellipsize(albumNameOf(ranked.video), vSubPaint, maxW, TextUtils.TruncateAt.END).toString()

                canvas.drawText(vName, 316f, y + 72f, vNamePaint)
                canvas.drawText(folderName, 316f, y + 114f, vSubPaint)

                // Sağda izlenme rozeti
                val badgePaint = textPaint(28f, 0xFFFFC83D.toInt(), bold).apply { textAlign = Paint.Align.RIGHT }
                canvas.drawText(badgeText, W - 104f, y + 92f, badgePaint)

                y += rowH + 18f
            }
        }

        // Alt bilgi
        val footerPaint = textPaint(26f, 0x80FFFFFF.toInt(), medium).apply { textAlign = Paint.Align.CENTER }
        canvas.drawText("Galeri Reels · Telefonundaki videolar, reels gibi", W / 2f, H - 64f, footerPaint)

        return bitmap
    }

    private fun drawThumb(canvas: Canvas, bitmap: Bitmap?, rect: RectF, radius: Float) {
        val placeholder = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF2C2C2E.toInt() }
        canvas.drawRoundRect(rect, radius, radius, placeholder)
        if (bitmap == null || bitmap.width <= 0 || bitmap.height <= 0) return

        val scale = maxOf(rect.width() / bitmap.width, rect.height() / bitmap.height)
        val dx = rect.left + (rect.width() - bitmap.width * scale) * 0.5f
        val dy = rect.top + (rect.height() - bitmap.height * scale) * 0.5f
        val shader = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
            setLocalMatrix(Matrix().apply {
                setScale(scale, scale)
                postTranslate(dx, dy)
            })
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.shader = shader }
        canvas.drawRoundRect(rect, radius, radius, paint)
    }

    private fun textPaint(sizePx: Float, color: Int, typeface: Typeface): TextPaint =
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = sizePx
            this.color = color
            this.typeface = typeface
        }
}
