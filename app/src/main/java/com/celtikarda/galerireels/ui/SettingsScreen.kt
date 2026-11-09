package com.celtikarda.galerireels.ui

import com.celtikarda.galerireels.AppLanguage
import com.celtikarda.galerireels.plural
import com.celtikarda.galerireels.str
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.celtikarda.galerireels.DataFolder
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
import kotlinx.coroutines.launch

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
                Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = str(R.string.back), tint = appColors.textPrimary)
            }
            Text(str(R.string.settings), color = appColors.textPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }

        SectionTitle(str(R.string.appearance), if (appColors.isDark) R.drawable.ic_dark_mode else R.drawable.ic_light_mode)
        Card {
            val currentTheme = stats.themeMode
            Text(
                str(R.string.theme_explain),
                color = appColors.textSecondary,
                fontSize = 14.sp,
            )
            Spacer(Modifier.height(10.dp))
            InfoLine(str(R.string.selected_theme), currentTheme.title)
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

        SectionTitle(str(R.string.language), R.drawable.ic_language)
        Card {
            Text(str(R.string.language_explain), color = appColors.textSecondary, fontSize = 14.sp)
            Spacer(Modifier.height(12.dp))
            LanguageSelector()
        }

        SectionTitle(str(R.string.media_mode), R.drawable.ic_play)
        Card {
            val currentMode = stats.mediaMode
            Text(
                str(R.string.media_mode_explain),
                color = appColors.textSecondary,
                fontSize = 14.sp,
            )
            Spacer(Modifier.height(10.dp))
            InfoLine(str(R.string.selected_mode), currentMode.title)
            InfoLine(
                str(R.string.in_gallery),
                MediaMode.VIDEOS.count(vm.allVideosCount) + " · " + MediaMode.PHOTOS.count(vm.allPhotosCount),
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

        SectionTitle(str(R.string.likes_and_views), R.drawable.ic_eye)
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
                    Text(str(R.string.save_folder), color = appColors.textSecondary, fontSize = 13.sp)
                    Text(
                        folder?.displayPath ?: str(R.string.not_selected),
                        color = appColors.textPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            if (folder != null) {
                if (stats.lastFolderSave > 0) InfoLine(str(R.string.last_saved), formatDateTime(stats.lastFolderSave))
                InfoLine(str(R.string.files), "${DataFolder.DATA_FILE}, BENİ OKU.txt")
            }
            InfoLine(
                str(R.string.saved_label),
                plural(R.plurals.n_likes, stats.totalLikes, formatCount(stats.totalLikes)) + " · " + plural(R.plurals.n_views, stats.totalViews, formatCount(stats.totalViews)) +
                    " (" + plural(R.plurals.n_videos, stats.watchedVideos, formatCount(stats.watchedVideos)) + ")",
            )
            val currentStreak = stats.currentStreak()
            val bestStreak = stats.bestStreak()
            if (bestStreak > 0) {
                InfoLine(str(R.string.streak), str(R.string.streak_value, currentStreak, bestStreak))
            }

            val warning = stats.folderError
                ?: if (folder == null) str(R.string.no_folder_warning) else null
            if (warning != null) {
                Spacer(Modifier.height(10.dp))
                Text(warning, color = WarningOrange, fontSize = 14.sp)
            }

            Spacer(Modifier.height(14.dp))
            if (folder == null) {
                Button(onClick = onPickFolder) { Text(str(R.string.choose_folder)) }
            } else {
                OutlinedButton(onClick = onPickFolder) { Text(str(R.string.change_folder)) }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                str(R.string.reinstall_hint),
                color = appColors.textMuted,
                fontSize = 13.sp,
            )
        }

        if (MediaActions.trashSupported) {
            SectionTitle(str(R.string.cleanup), R.drawable.ic_delete_sweep)
            Card {
                InfoLine(str(R.string.today), formatSize(stats.cleanedToday))
                InfoLine(str(R.string.total), formatSize(stats.cleanedTotal))
                Spacer(Modifier.height(10.dp))
                val trash = stats.trashed.toList()
                if (trash.isEmpty()) {
                    Text(str(R.string.trash_empty), color = appColors.textSecondary, fontSize = 14.sp)
                } else {
                    Text(
                        str(R.string.trash_info, plural(R.plurals.n_items, trash.size, formatCount(trash.size)), formatSize(trash.sumOf { it.bytes })),
                        color = appColors.textPrimary,
                        fontSize = 14.sp,
                    )
                    Spacer(Modifier.height(12.dp))
                    Row {
                        OutlinedButton(onClick = { trashAction(trash, true) }) { Text(str(R.string.restore)) }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = { trashAction(trash, false) },
                            colors = ButtonDefaults.buttonColors(containerColor = DeleteRed, contentColor = Color.White),
                        ) { Text(str(R.string.delete_permanently)) }
                    }
                }
            }
        }

        if (MediaActions.favoritesSupported) {
            SectionTitle(str(R.string.favorites), R.drawable.ic_star)
            Card {
                val canManage = remember(resumeTick) { MediaActions.canManageMedia(context) }
                Text(
                    str(R.string.favorites_explain),
                    color = appColors.textSecondary,
                    fontSize = 14.sp,
                )
                Spacer(Modifier.height(10.dp))
                if (canManage) {
                    InfoLine(str(R.string.confirmation), str(R.string.not_asked_media_mgmt))
                } else {
                    Text(
                        str(R.string.media_mgmt_explain),
                        color = appColors.textPrimary,
                        fontSize = 14.sp,
                    )
                    MediaActions.manageMediaSettings(context)?.let { intent ->
                        Spacer(Modifier.height(10.dp))
                        OutlinedButton(onClick = { context.startActivity(intent) }) { Text(str(R.string.grant_permission)) }
                    }
                }
            }
        }

        SectionTitle(str(R.string.hidden_folders), R.drawable.ic_folder)
        Card {
            val hidden = vm.hiddenFolders
            val lock = vm.hiddenLock
            var pinMode by remember { mutableStateOf<PinMode?>(null) }
            pinMode?.let { mode ->
                PinDialog(
                    lock = lock,
                    mode = mode,
                    onDone = {
                        pinMode = null
                        if (mode == PinMode.SET) Toast.makeText(context, str(R.string.pin_saved), Toast.LENGTH_SHORT).show()
                    },
                    onDismiss = { pinMode = null },
                )
            }
            Text(
                str(R.string.hidden_explain),
                color = appColors.textSecondary,
                fontSize = 14.sp,
            )
            Spacer(Modifier.height(10.dp))
            if (lock.hasPin && !lock.unlocked) {
                // Kilitliyken ne kaç klasör olduğu ne de adları görünür
                Text(str(R.string.pin_locked), color = appColors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Button(onClick = { pinMode = PinMode.UNLOCK }) {
                    Icon(painterResource(R.drawable.ic_lock_open), contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(str(R.string.unlock))
                }
            } else if (hidden.isEmpty()) {
                Text(str(R.string.no_hidden), color = appColors.textSecondary, fontSize = 14.sp)
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
                            Text(str(R.string.hidden_album_line, plural(R.plurals.n_videos, album.count, formatCount(album.count))), color = appColors.textSecondary, fontSize = 12.sp)
                        }
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton(onClick = { vm.unhideAlbum(album.id) }) {
                            Text(str(R.string.show))
                        }
                    }
                }
                if (hidden.size > 1) {
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { vm.unhideAllAlbums() }) {
                        Text(str(R.string.show_all_n, hidden.size))
                    }
                }
            }
            if (!lock.hasPin || lock.unlocked) {
                Spacer(Modifier.height(14.dp))
                Text(
                    if (lock.hasPin) {
                        str(R.string.pin_on_explain)
                    } else {
                        str(R.string.pin_off_explain)
                    },
                    color = appColors.textSecondary,
                    fontSize = 13.sp,
                )
                Spacer(Modifier.height(8.dp))
                if (lock.hasPin) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { pinMode = PinMode.SET }) { Text(str(R.string.change_pin)) }
                        OutlinedButton(onClick = {
                            lock.removePin()
                            Toast.makeText(context, str(R.string.pin_removed), Toast.LENGTH_SHORT).show()
                        }) { Text(str(R.string.remove_pin)) }
                    }
                } else {
                    OutlinedButton(onClick = { pinMode = PinMode.SET }) {
                        Icon(painterResource(R.drawable.ic_lock), contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(str(R.string.set_pin))
                    }
                }
            }
        }

        SectionTitle(str(R.string.playback), R.drawable.ic_play)
        Card {
            val bgPlay = stats.backgroundPlay
            Text(
                str(R.string.bg_play_explain),
                color = appColors.textSecondary,
                fontSize = 14.sp,
            )
            Spacer(Modifier.height(10.dp))
            InfoLine(str(R.string.status), str(if (bgPlay) R.string.on else R.string.off))
            Spacer(Modifier.height(10.dp))
            if (bgPlay) {
                OutlinedButton(onClick = { stats.updateBackgroundPlay(false) }) { Text(str(R.string.turn_off)) }
            } else {
                Button(onClick = { stats.updateBackgroundPlay(true) }) { Text(str(R.string.turn_on)) }
            }
        }
        Spacer(Modifier.height(10.dp))
        Card {
            val leftAction = stats.leftHoldAction
            val rightAction = stats.rightHoldAction
            Text(str(R.string.edge_hold), color = appColors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(
                str(R.string.edge_hold_explain),
                color = appColors.textSecondary,
                fontSize = 14.sp,
            )
            Spacer(Modifier.height(12.dp))
            Text(str(R.string.hold_left), color = appColors.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            EdgeActionSelector(
                selected = leftAction,
                onSelect = { stats.updateLeftHoldAction(it) },
            )
            Spacer(Modifier.height(12.dp))
            Text(str(R.string.hold_right), color = appColors.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            EdgeActionSelector(
                selected = rightAction,
                onSelect = { stats.updateRightHoldAction(it) },
            )
        }

        SectionTitle(str(R.string.app_section), R.drawable.ic_info)
        Card {
            InfoLine(str(R.string.version_label), version ?: "")
            Row(
                Modifier
                    .clickable {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Celtikarda/GaleriReels")))
                    }
                    .padding(vertical = 3.dp)
            ) {
                Text(str(R.string.source_code), color = appColors.textSecondary, fontSize = 14.sp, modifier = Modifier.width(96.dp))
                Text("github.com/Celtikarda/GaleriReels", color = InstaBlue, fontSize = 14.sp)
            }
            Row(
                Modifier
                    .clickable { openIssueReport(context) }
                    .padding(vertical = 3.dp)
            ) {
                Text(str(R.string.feedback), color = appColors.textSecondary, fontSize = 14.sp, modifier = Modifier.width(96.dp))
                Text(str(R.string.report_or_suggest), color = InstaBlue, fontSize = 14.sp)
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = { openIssueReport(context) }) {
                Text(str(R.string.report_problem))
            }
        }
        Spacer(Modifier.height(10.dp))
        Card {
            val updates = vm.updates
            val update = updates.available
            val scope = rememberCoroutineScope()
            var showDialog by remember { mutableStateOf(false) }
            Text(str(R.string.updates), color = appColors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(
                str(R.string.updates_explain),
                color = appColors.textSecondary,
                fontSize = 14.sp,
            )
            Spacer(Modifier.height(10.dp))
            InfoLine(str(R.string.notification), str(if (updates.enabled) R.string.on else R.string.off))
            if (update != null) {
                Row(
                    Modifier
                        .clickable { showDialog = true }
                        .padding(vertical = 3.dp)
                ) {
                    Text(str(R.string.new_version_label), color = appColors.textSecondary, fontSize = 14.sp, modifier = Modifier.width(96.dp))
                    Text(str(R.string.version_ready, update.version), color = InstaBlue, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (updates.enabled) {
                    OutlinedButton(onClick = { updates.updateEnabled(false) }) { Text(str(R.string.turn_off)) }
                } else {
                    Button(onClick = { updates.updateEnabled(true) }) { Text(str(R.string.turn_on)) }
                }
                OutlinedButton(
                    enabled = !updates.checking,
                    onClick = {
                        scope.launch {
                            val text = when (updates.check(force = true)) {
                                true -> null
                                false -> str(R.string.up_to_date, "$version")
                                null -> str(R.string.no_connection)
                            }
                            if (text == null) showDialog = true
                            else Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
                        }
                    },
                ) {
                    Text(str(if (updates.checking) R.string.checking else R.string.check_now))
                }
            }
            if (showDialog && update != null) {
                UpdateDialog(update = update, onDismiss = { showDialog = false })
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
        item != null && item.isPhoto -> str(R.string.issue_photo, "${item.width}x${item.height}")
        item != null -> str(R.string.issue_video, "${item.width}x${item.height}")
        else -> str(R.string.issue_general)
    }
    val body = buildString {
        appendLine("### ${str(R.string.issue_description)}")
        if (errorText != null) {
            appendLine(str(R.string.issue_error, "$errorText"))
        } else {
            appendLine(str(R.string.issue_placeholder))
        }
        appendLine()
        appendLine("### ${str(R.string.issue_device_info)}")
        appendLine("- **${str(R.string.issue_app_version)}:** $version")
        appendLine("- **${str(R.string.issue_phone)}:** ${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine("- **Android:** ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
        if (item != null) {
            appendLine("- **Format:** ${item.mimeType ?: "—"}")
            appendLine("- **${str(R.string.resolution)}:** ${item.width} × ${item.height}")
            if (item.isPhoto) {
                appendLine("- **${str(R.string.size)}:** ${formatSize(item.sizeBytes)}")
            } else {
                val engine = when (DecoderCheck.decision(item.id)) {
                    true -> str(R.string.vlc_software)
                    false -> str(R.string.exo_hardware)
                    null -> "—"
                }
                appendLine("- **${str(R.string.size_duration)}:** ${formatSize(item.sizeBytes)} · ${formatDuration(item.durationMs)}")
                appendLine("- **${str(R.string.player)}:** $engine")
            }
        }
    }
    val url = "https://github.com/Celtikarda/GaleriReels/issues/new?title=${Uri.encode(title)}&body=${Uri.encode(body)}"
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}

/** Telefonun dili / Türkçe / English; seçince Activity yeniden oluşur ve her şey o dilde açılır */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LanguageSelector() {
    val context = LocalContext.current
    val current = remember { AppLanguage.current(context) }
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        AppLanguage.options.forEach { tag ->
            // Dil adları her dilde kendi dilinde yazılır ki yanlış dilde kalan kullanıcı da bulabilsin
            val label = when (tag) {
                "tr" -> "Türkçe"
                "en" -> "English"
                else -> str(R.string.language_system)
            }
            val onClick = { (context as? Activity)?.let { AppLanguage.set(it, tag) }; Unit }
            if (tag == current) {
                Button(onClick = onClick) { Text(label) }
            } else {
                OutlinedButton(onClick = onClick) { Text(label) }
            }
        }
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
