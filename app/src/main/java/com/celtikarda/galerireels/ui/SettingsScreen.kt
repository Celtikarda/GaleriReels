package com.celtikarda.galerireels.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.celtikarda.galerireels.DataFolder
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.celtikarda.galerireels.DecoderCheck
import com.celtikarda.galerireels.EdgeHoldAction
import com.celtikarda.galerireels.GalleryViewModel
import com.celtikarda.galerireels.MediaActions
import com.celtikarda.galerireels.MediaMode
import com.celtikarda.galerireels.MediaRepository
import com.celtikarda.galerireels.R
import com.celtikarda.galerireels.ThemeMode
import com.celtikarda.galerireels.TrashedVideo
import com.celtikarda.galerireels.VideoItem

private val WarningOrange = Color(0xFFFFB020)

@Composable
fun SettingsScreen(vm: GalleryViewModel, onPickFolder: () -> Unit, onBack: () -> Unit) {
    val stats = vm.stats
    val folder = stats.folder
    val context = LocalContext.current
    val version = remember { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
    // Ayarlardan izin verip dönünce durumu yeniden oku
    var resumeTick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { resumeTick++ }

    // Çöp kutusu: 30 günü dolup telefonun sildiklerini listeden düş
    LaunchedEffect(Unit) {
        val ids = stats.trashed.map { it.id }
        if (ids.isNotEmpty()) {
            val still = MediaRepository.trashedIds(context, ids)
            stats.recordDeleted(ids.filterNot { it in still }.toSet())
        }
    }
    // Geri getirme / kalıcı silme: Android önce kullanıcıya sorar
    var pendingTrash by remember { mutableStateOf<Pair<List<Long>, Boolean>?>(null) }
    val trashLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        val (ids, restore) = pendingTrash ?: return@rememberLauncherForActivityResult
        pendingTrash = null
        if (result.resultCode == Activity.RESULT_OK) {
            if (restore) {
                stats.recordRestored(ids.toSet())
                vm.reloadVideos()
            } else {
                stats.recordDeleted(ids.toSet())
            }
        }
    }
    val trashAction = { items: List<TrashedVideo>, restore: Boolean ->
        val uris = items.map { MediaRepository.uriOf(it.id, it.isPhoto) }
        val ids = items.map { it.id }
        val request = if (restore) MediaActions.trashRequest(context, uris, trash = false) else MediaActions.deleteRequest(context, uris)
        if (request != null) {
            pendingTrash = ids to restore
            trashLauncher.launch(IntentSenderRequest.Builder(request.intentSender).build())
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)) {
            IconButton(onClick = onBack) {
                Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Geri", tint = appColors.textPrimary)
            }
            Text("Ayarlar", color = appColors.textPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }

        SectionTitle("Görünüm", if (appColors.isDark) R.drawable.ic_dark_mode else R.drawable.ic_light_mode)
        Card {
            val currentTheme = stats.themeMode
            Text(
                "Ana ekran, ayarlar, başarımlar ve aylık özet sayfalarında gece (koyu), aydınlık (açık) ya da " +
                    "telefonunun sistem temasını kullanabilirsin. Reels oynatıcı ekranı videoların net görünmesi " +
                    "için her zaman sinema siyahı kalır.",
                color = appColors.textSecondary,
                fontSize = 14.sp,
            )
            Spacer(Modifier.height(10.dp))
            InfoLine("Seçili tema", currentTheme.title)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeMode.entries.forEach { entry ->
                    if (entry == currentTheme) {
                        Button(onClick = { vm.setThemeMode(entry) }) { Text(entry.title) }
                    } else {
                        OutlinedButton(onClick = { vm.setThemeMode(entry) }) { Text(entry.title) }
                    }
                }
            }
        }

        SectionTitle("Medya modu", R.drawable.ic_play)
        Card {
            val currentMode = stats.mediaMode
            Text(
                "Uygulamada sadece videoları, sadece fotoğrafları ya da ikisini birden Reels gibi " +
                    "dikey kaydırarak gezebilir ve sola kaydırarak temizleyebilirsin.",
                color = appColors.textSecondary,
                fontSize = 14.sp,
            )
            Spacer(Modifier.height(10.dp))
            InfoLine("Seçili mod", currentMode.title)
            InfoLine(
                "Galeride",
                "${formatCount(vm.allVideosCount)} video · ${formatCount(vm.allPhotosCount)} fotoğraf",
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MediaMode.entries.forEach { entry ->
                    if (entry == currentMode) {
                        Button(onClick = { vm.setMediaMode(entry) }) { Text(entry.title) }
                    } else {
                        OutlinedButton(onClick = { vm.setMediaMode(entry) }) { Text(entry.title) }
                    }
                }
            }
        }

        SectionTitle("Beğeniler ve izlenmeler", R.drawable.ic_eye)
        Card {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painterResource(R.drawable.ic_folder),
                    contentDescription = null,
                    tint = if (folder != null) InstaBlue else WarningOrange,
                    modifier = Modifier.size(28.dp),
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Kayıt klasörü", color = appColors.textSecondary, fontSize = 13.sp)
                    Text(
                        folder?.displayPath ?: "Seçilmedi",
                        color = appColors.textPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            if (folder != null) {
                if (stats.lastFolderSave > 0) InfoLine("Son kayıt", formatDateTime(stats.lastFolderSave))
                InfoLine("Dosyalar", "${DataFolder.DATA_FILE}, BENİ OKU.txt")
            }
            InfoLine(
                "Kayıtlı",
                "${formatCount(stats.totalLikes)} beğeni · ${formatCount(stats.totalViews)} izlenme " +
                    "(${formatCount(stats.watchedVideos)} video)",
            )
            val currentStreak = stats.currentStreak()
            val bestStreak = stats.bestStreak()
            if (bestStreak > 0) {
                InfoLine("Seri", "$currentStreak gün üst üste (en uzun $bestStreak gün)")
            }

            val warning = stats.folderError
                ?: if (folder == null) "Klasör seçilmedi: uygulamayı silersen beğenilerin ve izlenmelerin kaybolur." else null
            if (warning != null) {
                Spacer(Modifier.height(10.dp))
                Text(warning, color = WarningOrange, fontSize = 14.sp)
            }

            Spacer(Modifier.height(14.dp))
            if (folder == null) {
                Button(onClick = onPickFolder) { Text("Klasör seç") }
            } else {
                OutlinedButton(onClick = onPickFolder) { Text("Klasörü değiştir") }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "Uygulamayı silip yeniden kurarsan aynı klasörü (Documents › GaleriReels) seçmen " +
                    "yeterli; beğenilerin ve izlenmelerin geri gelir.",
                color = appColors.textMuted,
                fontSize = 13.sp,
            )
        }

        if (MediaActions.trashSupported) {
            SectionTitle("Temizlik", R.drawable.ic_delete_sweep)
            Card {
                InfoLine("Bugün", formatSize(stats.cleanedToday))
                InfoLine("Toplam", formatSize(stats.cleanedTotal))
                Spacer(Modifier.height(10.dp))
                val trash = stats.trashed.toList()
                if (trash.isEmpty()) {
                    Text("Çöp kutusunda uygulamanın sildiği içerik yok.", color = appColors.textSecondary, fontSize = 14.sp)
                } else {
                    Text(
                        "Çöp kutusunda ${formatCount(trash.size)} öğe (${formatSize(trash.sumOf { it.bytes })}). " +
                            "Telefon bunları silindikten 30 gün sonra kendiliğinden siler; o zamana kadar geri getirebilirsin. " +
                            "Yerin hemen açılması için kalıcı olarak silebilirsin.",
                        color = appColors.textPrimary,
                        fontSize = 14.sp,
                    )
                    Spacer(Modifier.height(12.dp))
                    Row {
                        OutlinedButton(onClick = { trashAction(trash, true) }) { Text("Geri getir") }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = { trashAction(trash, false) },
                            colors = ButtonDefaults.buttonColors(containerColor = DeleteRed, contentColor = Color.White),
                        ) { Text("Kalıcı olarak sil") }
                    }
                }
            }
        }

        if (MediaActions.favoritesSupported) {
            SectionTitle("Favoriler", R.drawable.ic_star)
            Card {
                val canManage = remember(resumeTick) { MediaActions.canManageMedia(context) }
                Text(
                    "Favoriler telefonunun galerisindeki favorilerle aynı listedir; burada eklediğin " +
                        "galeride de favori olur.",
                    color = appColors.textSecondary,
                    fontSize = 14.sp,
                )
                Spacer(Modifier.height(10.dp))
                if (canManage) {
                    InfoLine("Onay", "Sorulmuyor (Medya yönetimi izni var)")
                } else {
                    Text(
                        "Videoları çöp kutusuna taşırken (ve bazı telefonlarda favori eklerken) telefon " +
                            "her seferinde onay sorar. \"Medya yönetimi\" izni verirsen sormaz.",
                        color = appColors.textPrimary,
                        fontSize = 14.sp,
                    )
                    MediaActions.manageMediaSettings(context)?.let { intent ->
                        Spacer(Modifier.height(10.dp))
                        OutlinedButton(onClick = { context.startActivity(intent) }) { Text("İzni aç") }
                    }
                }
            }
        }

        SectionTitle("Gizlenen klasörler", R.drawable.ic_folder)
        Card {
            val hidden = vm.hiddenFolders
            Text(
                "Ana ekranda bir klasöre basılı tutarak gizleyebilirsin. Gizlenen klasörler " +
                    "Tüm videolar, Tarihte bugün ve Temizlik önerilerine girmez.",
                color = appColors.textSecondary,
                fontSize = 14.sp,
            )
            Spacer(Modifier.height(10.dp))
            if (hidden.isEmpty()) {
                Text("Gizlenmiş klasör yok.", color = appColors.textSecondary, fontSize = 14.sp)
            } else {
                hidden.forEach { album ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(
                            Modifier
                                .weight(1f)
                                .clickable { vm.open(album) }
                        ) {
                            Text(album.name, color = appColors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            Text("${formatCount(album.count)} video · İzlemek için dokun", color = appColors.textSecondary, fontSize = 12.sp)
                        }
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton(onClick = { vm.unhideAlbum(album.id) }) {
                            Text("Göster")
                        }
                    }
                }
                if (hidden.size > 1) {
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { vm.unhideAllAlbums() }) {
                        Text("Tümünü göster (${hidden.size})")
                    }
                }
            }
        }

        SectionTitle("Oynatma", R.drawable.ic_play)
        Card {
            val bgPlay = stats.backgroundPlay
            Text(
                "Arka planda oynatma açıkken uygulamadan çıksan ya da ekranı kapatsan bile videonun " +
                    "sesi devam eder ve bildirim çubuğunda oynatıcı görünür.",
                color = appColors.textSecondary,
                fontSize = 14.sp,
            )
            Spacer(Modifier.height(10.dp))
            InfoLine("Durum", if (bgPlay) "Açık" else "Kapalı")
            Spacer(Modifier.height(10.dp))
            if (bgPlay) {
                OutlinedButton(onClick = { stats.updateBackgroundPlay(false) }) { Text("Kapat") }
            } else {
                Button(onClick = { stats.updateBackgroundPlay(true) }) { Text("Aç") }
            }
        }
        Spacer(Modifier.height(10.dp))
        Card {
            val leftAction = stats.leftHoldAction
            val rightAction = stats.rightHoldAction
            Text("Kenarlara basılı tutma", color = appColors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(
                "Videoları izlerken ekranın sol veya sağ kenarına (%25) basılı tuttuğunda yapılacak işlemi " +
                    "seçebilirsin. Ekranın ortasına basılı tutmak her zaman arayüzü gizler (Net görünüm).",
                color = appColors.textSecondary,
                fontSize = 14.sp,
            )
            Spacer(Modifier.height(12.dp))
            Text("Sol kenara basılı tutunca", color = appColors.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            EdgeActionSelector(
                selected = leftAction,
                onSelect = { stats.updateLeftHoldAction(it) },
            )
            Spacer(Modifier.height(12.dp))
            Text("Sağ kenara basılı tutunca", color = appColors.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            EdgeActionSelector(
                selected = rightAction,
                onSelect = { stats.updateRightHoldAction(it) },
            )
        }

        SectionTitle("Uygulama", R.drawable.ic_info)
        Card {
            InfoLine("Sürüm", version ?: "")
            Row(
                Modifier
                    .clickable {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Celtikarda/GaleriReels")))
                    }
                    .padding(vertical = 3.dp)
            ) {
                Text("Kaynak kodu", color = appColors.textSecondary, fontSize = 14.sp, modifier = Modifier.width(96.dp))
                Text("github.com/Celtikarda/GaleriReels", color = InstaBlue, fontSize = 14.sp)
            }
            Row(
                Modifier
                    .clickable { openIssueReport(context) }
                    .padding(vertical = 3.dp)
            ) {
                Text("Geri bildirim", color = appColors.textSecondary, fontSize = 14.sp, modifier = Modifier.width(96.dp))
                Text("Sorun bildir / Öneri gönder", color = InstaBlue, fontSize = 14.sp)
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = { openIssueReport(context) }) {
                Text("Sorun bildir")
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

/**
 * GitHub Issues sayfasını telefon modeli, Android sürümü ve varsa video hata bilgileriyle
 * önceden doldurulmuş olarak açar (internet izni gerekmez, tarayıcıya yönlendirir).
 */
fun openIssueReport(context: Context, item: VideoItem? = null, errorText: String? = null) {
    val version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "—"
    val title = when {
        item != null && item.isPhoto -> "Fotoğraf görüntüleme sorunu (${item.width}x${item.height})"
        item != null -> "Video oynatma sorunu (${item.width}x${item.height})"
        else -> "Sorun bildirimi / Öneri"
    }
    val body = buildString {
        appendLine("### Açıklama")
        if (errorText != null) {
            appendLine("Karşılaşılan hata: $errorText")
        } else {
            appendLine("(Yaşadığın sorunu ya da önerini buraya yazabilirsin)")
        }
        appendLine()
        appendLine("### Cihaz ve Uygulama Bilgisi")
        appendLine("- **Uygulama sürümü:** $version")
        appendLine("- **Telefon:** ${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine("- **Android:** ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
        if (item != null) {
            appendLine("- **Format:** ${item.mimeType ?: "—"}")
            appendLine("- **Çözünürlük:** ${item.width} × ${item.height}")
            if (item.isPhoto) {
                appendLine("- **Boyut:** ${formatSize(item.sizeBytes)}")
            } else {
                val engine = when (DecoderCheck.decision(item.id)) {
                    true -> "VLC (yazılım)"
                    false -> "ExoPlayer (donanım)"
                    null -> "—"
                }
                appendLine("- **Boyut / Süre:** ${formatSize(item.sizeBytes)} · ${formatDuration(item.durationMs)}")
                appendLine("- **Oynatıcı:** $engine")
            }
        }
    }
    val url = "https://github.com/Celtikarda/GaleriReels/issues/new?title=${Uri.encode(title)}&body=${Uri.encode(body)}"
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EdgeActionSelector(selected: EdgeHoldAction, onSelect: (EdgeHoldAction) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        EdgeHoldAction.entries.forEach { entry ->
            if (entry == selected) {
                Button(onClick = { onSelect(entry) }) { Text(entry.title) }
            } else {
                OutlinedButton(onClick = { onSelect(entry) }) { Text(entry.title) }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String, icon: Int) {
    Row(
        Modifier.padding(start = 4.dp, top = 18.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = appColors.textSecondary, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, color = appColors.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun Card(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(appColors.card)
            .padding(16.dp),
        content = content,
    )
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(Modifier.padding(vertical = 3.dp)) {
        Text(label, color = appColors.textSecondary, fontSize = 14.sp, modifier = Modifier.width(96.dp))
        Text(value, color = appColors.textPrimary, fontSize = 14.sp)
    }
}
