package com.celtikarda.galerireels.ui

import androidx.compose.runtime.derivedStateOf

import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.unit.Dp
import com.celtikarda.galerireels.Strings
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay

import com.celtikarda.galerireels.plural
import com.celtikarda.galerireels.str
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.celtikarda.galerireels.Album
import com.celtikarda.galerireels.GalleryViewModel
import com.celtikarda.galerireels.MediaActions
import com.celtikarda.galerireels.MediaMode
import com.celtikarda.galerireels.Memories
import com.celtikarda.galerireels.MonthlyRecap
import com.celtikarda.galerireels.R
import com.celtikarda.galerireels.SmartCategory
import com.celtikarda.galerireels.VideoItem
import com.celtikarda.galerireels.VideoThumb

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumsScreen(
    vm: GalleryViewModel,
    partialAccess: Boolean,
    onSelectMore: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenRecap: () -> Unit,
    contentBottomPadding: Dp,
) {
    val tiles by remember { derivedStateOf { vm.albumTiles() } }
    val mode = vm.stats.mediaMode
    val context = LocalContext.current
    val version = remember { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
    var pendingHide by remember { mutableStateOf<Album?>(null) }
    var cleanupOpen by rememberSaveable { mutableStateOf(false) }

    // Tüm videolar / Favoriler / Beğendiklerin önce, sonra klasörler
    val collections = tiles.filter { it.id in setOf(Album.ALL, Album.FAVORITES, Album.LIKED) }
    val folders = tiles.filterNot { it in collections }
    val hiddenCount = vm.hiddenFolders.size
    val revision = vm.stats.historyRevision
    val currentRecap = remember(revision, vm.videoCount) { vm.monthlyRecap() }
    val memories = remember(vm.videoCount, mode) { vm.memories() }
    val resume = remember(revision, vm.videoCount, mode) { vm.resumeCandidate() }
    val reels = remember(vm.videoCount, mode) { vm.monthReels() }
    val mosaic = remember(revision, vm.videoCount, mode) { vm.mostWatched() }
    val years = remember(vm.videoCount, mode) { vm.years() }
    val recent by remember { derivedStateOf { vm.recentVideos(15) } }

    // Temizlik kutusu: boşaltılabilecek yer (bir öğe birden çok listede olabilir, bir kez sayılır)
    val freeable by remember {
        derivedStateOf {
            if (MediaActions.trashSupported) {
                (vm.unwatched() + vm.duplicateCopies + vm.smartGroups().values.flatten()).distinctBy { it.id }.sumOf { it.sizeBytes }
            } else 0L
        }
    }

    // Ay makarası: varsa en başta "BUGÜN" (tarihte bugün), sonra aylar
    val todayCount = memories.size
    val chipCount = reels.size + if (todayCount > 0) 1 else 0
    var selectedChip by rememberSaveable { mutableIntStateOf(0) }
    if (selectedChip >= chipCount) selectedChip = 0
    val showingToday = todayCount > 0 && selectedChip == 0
    val selectedReel = if (showingToday) null else reels.getOrNull(selectedChip - if (todayCount > 0) 1 else 0)
    val fullMonth = remember(Strings.locale) { DateTimeFormatter.ofPattern("LLLL", Strings.locale) }

    val gridState = rememberLazyGridState()

    // Normal ayarda 3 sütun; yazı büyütülünce kutu adları sığsın diye 2 sütuna iner
    val tileMin = (96 * LocalDensity.current.fontScale.coerceIn(1f, 1.6f)).dp
    val full: (LazyGridItemSpanScope) -> GridItemSpan = { GridItemSpan(it.maxLineSpan) }
    Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(tileMin),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(span = full) {
                Column(Modifier.statusBarsPadding().padding(top = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "GaleriReels".upper(),
                            style = MakaraType.Display,
                            color = Makara.Cream,
                            maxLines = 1,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = onOpenSettings, modifier = Modifier.size(44.dp)) {
                            Icon(painterResource(R.drawable.ic_settings), contentDescription = str(R.string.settings), tint = Makara.Cream)
                        }
                    }
                    Text(
                        "${mode.count(vm.videoCount)} · ${plural(R.plurals.n_folders, vm.folderCount, formatCount(vm.folderCount))}",
                        style = MakaraType.BodySmall,
                        color = Makara.Muted,
                    )
                    Spacer(Modifier.height(10.dp))
                    MediaModeSelector(current = mode, onSelect = vm::setMediaMode)
                    if (partialAccess) {
                        Spacer(Modifier.height(8.dp))
                        WarningStrip(str(R.string.limited_access), onSelectMore)
                    }
                    if (vm.stats.loaded && vm.stats.folder == null) {
                        Spacer(Modifier.height(8.dp))
                        WarningStrip(str(R.string.not_backed_up), onOpenSettings)
                    }
                }
            }

            resume?.let { (video, position) ->
                item(span = full) {
                    ResumeCard(video, position, onClick = vm::openResume, modifier = Modifier.padding(top = 8.dp))
                }
            }

            if (recent.isNotEmpty()) {
                item(span = full) {
                    Column {
                        SectionTitle(str(R.string.recent))
                        Spacer(Modifier.height(8.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(recent, key = { it.id }) { video ->
                                SmallFrame(video, views = vm.viewsOf(video), onClick = { vm.openRecent(video) })
                            }
                        }
                    }
                }
            }

            val showMonth = currentRecap.hasActivity
            val showCleanup = freeable > 0
            if (showMonth || showCleanup) {
                item(span = full) {
                    Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (showMonth) {
                            ThisMonthTile(
                                watchTimeMs = currentRecap.watchTimeMs,
                                videoCount = currentRecap.uniqueVideos,
                                mode = mode,
                                onClick = onOpenRecap,
                                modifier = Modifier.weight(2f).fillMaxHeight(),
                            )
                        }
                        if (showCleanup) {
                            CleanupTile(
                                bytes = freeable,
                                onClick = { cleanupOpen = true },
                                modifier = Modifier.weight(if (showMonth) 1.2f else 1f).fillMaxHeight(),
                            )
                        }
                    }
                }
            }

            if (chipCount > 0) {
                item(span = full) {
                    Column {
                        MonthChips(reels = reels, selected = selectedChip, onSelect = { selectedChip = it }, todayCount = todayCount)
                        Spacer(Modifier.height(14.dp))
                        if (showingToday) {
                            FilmStrip(
                                title = str(R.string.memories_reel),
                                items = memories,
                                onPlay = { vm.openMemories(memories.first()) },
                                onItem = vm::openMemories,
                            )
                        } else if (selectedReel != null) {
                            FilmStrip(
                                title = str(R.string.month_reel, fullMonth.format(selectedReel.month)),
                                items = selectedReel.items,
                                onPlay = { vm.openMonth(selectedReel, selectedReel.items.first()) },
                                onItem = { vm.openMonth(selectedReel, it) },
                            )
                        }
                    }
                }
            }

            if (mosaic.isNotEmpty()) {
                item(span = full) {
                    Column {
                        SectionTitle(str(R.string.most_watched))
                        Text(str(R.string.most_watched_hint), style = MakaraType.BodySmall, color = Makara.Muted)
                        Spacer(Modifier.height(8.dp))
                        MostWatchedMosaic(mosaic, onItem = vm::openMostWatched)
                    }
                }
            }

            item(span = full) {
                // Gizleme bir klasöre basılı tutunca açılan menüde; gizlenenler varsa başlığın ⋮ menüsünden açılır
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                    SectionTitle(str(R.string.canisters), modifier = Modifier.weight(1f))
                    if (hiddenCount > 0) {
                        var headerMenuOpen by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { headerMenuOpen = true }, modifier = Modifier.size(44.dp)) {
                                Icon(
                                    painterResource(R.drawable.ic_more_vert),
                                    contentDescription = str(R.string.folder_options),
                                    tint = Makara.Muted,
                                )
                            }
                            DropdownMenu(expanded = headerMenuOpen, onDismissRequest = { headerMenuOpen = false }) {
                                DropdownMenuItem(
                                    // PIN varken kaç klasör gizlendiği de görünmez
                                    text = { Text(if (vm.hiddenLock.hasPin) str(R.string.hidden_folders) else str(R.string.hidden_folders_count, formatCount(hiddenCount))) },
                                    leadingIcon = {
                                        Icon(painterResource(R.drawable.ic_visibility_off), contentDescription = null, tint = Makara.Cream)
                                    },
                                    onClick = {
                                        headerMenuOpen = false
                                        onOpenSettings()
                                    },
                                )
                            }
                        }
                    }
                }
            }

            items(collections, key = { it.id }) { album ->
                Canister(album, mode, onClick = { vm.open(album) }, onLongClick = null)
            }
            items(folders, key = { it.id }) { album ->
                FolderCanister(album, mode, onClick = { vm.open(album) }, onHide = { pendingHide = album })
            }

            item(span = full) {
                Text(
                    "GaleriReels $version",
                    style = MakaraType.BodySmall,
                    color = Makara.Muted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = contentBottomPadding),
                )
            }
        }

        YearRail(
            years = years,
            activeYear = selectedReel?.month?.year,
            scrolling = { gridState.isScrollInProgress },
            onPick = vm::openYear,
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 6.dp),
        )
    }

    if (cleanupOpen) {
        ModalBottomSheet(onDismissRequest = { cleanupOpen = false }, containerColor = Makara.Surface) {
            val unwatched = vm.unwatched()
            val duplicates = vm.duplicateCopies
            CleanupCard(
                mode = mode,
                unwatchedCount = unwatched.size,
                unwatchedBytes = unwatched.sumOf { it.sizeBytes },
                cleanedToday = vm.stats.cleanedToday,
                duplicateCount = duplicates.size,
                duplicateBytes = duplicates.sumOf { it.sizeBytes },
                onStart = { cleanupOpen = false; vm.openCleanup() },
                onStartDuplicates = { cleanupOpen = false; vm.openDuplicateCleanup() },
                smartGroups = vm.smartGroups(),
                onStartSmart = { cleanupOpen = false; vm.openSmartCleanup(it) },
            )
            Spacer(Modifier.navigationBarsPadding().height(16.dp))
        }
    }

    pendingHide?.let { folder ->
        AlertDialog(
            onDismissRequest = { pendingHide = null },
            containerColor = appColors.card,
            titleContentColor = appColors.textPrimary,
            textContentColor = appColors.textSecondary,
            title = { Text(str(R.string.hide_folder_q, folder.name), fontWeight = FontWeight.SemiBold) },
            text = {
                Text(
                    str(R.string.hide_folder_body, plural(R.plurals.n_videos, folder.count, formatCount(folder.count)))
                )
            },
            dismissButton = {
                TextButton(onClick = { pendingHide = null }) {
                    Text(str(R.string.cancel), color = appColors.textSecondary)
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.hideAlbum(folder.id)
                        pendingHide = null
                    }
                ) {
                    Text(str(R.string.hide), color = Makara.Amber, fontWeight = FontWeight.SemiBold)
                }
            },
        )
    }
}

/** Sınırlı erişim / yedeklenmiyor uyarısı: ince kehribar kenarlı şerit */
@Composable
private fun WarningStrip(text: String, onClick: () -> Unit) {
    Text(
        text,
        style = MakaraType.Label,
        color = Makara.Cream,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .clip(MakaraShape.Default)
            .border(1.dp, Makara.Amber, MakaraShape.Default)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
    )
}

/** Klasör kutusu: basılı tutunca "Klasörü gizle" menüsü */
@Composable
private fun FolderCanister(album: Album, mode: MediaMode, onClick: () -> Unit, onHide: () -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    Box {
        Canister(album, mode, onClick = onClick, onLongClick = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            menuOpen = true
        })
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text(str(R.string.hide_folder)) },
                leadingIcon = {
                    Icon(painterResource(R.drawable.ic_visibility_off), contentDescription = null, tint = Makara.Cream)
                },
                onClick = {
                    menuOpen = false
                    onHide()
                },
            )
        }
    }
}

/** Medya seçici sekmesinin zemin ve yazı rengi */
internal data class TabColors(val background: Color, val text: Color)

internal fun mediaModeTabColors(selected: Boolean, palette: AppColors): TabColors =
    if (selected) TabColors(Makara.Amber, Makara.OnAmber) else TabColors(Color.Transparent, palette.textSecondary)

/** Ana ekranda Videolar / Fotoğraflar / Tümü arasında hızlı geçiş sekmesi */
@Composable
private fun MediaModeSelector(current: MediaMode, onSelect: (MediaMode) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(appColors.card)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        MediaMode.entries.forEach { entry ->
            val selected = entry == current
            val colors = mediaModeTabColors(selected, appColors)
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(9.dp))
                    .background(colors.background)
                    .clickable { onSelect(entry) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    entry.title,
                    color = colors.text,
                    fontSize = 13.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                )
            }
        }
    }
}

/** Temizlik modunun girişi: hiç izlenmeyenlerin kapladığı yer, bugün temizlenen ve kopya içerikler */
@Composable
private fun CleanupCard(
    mode: MediaMode,
    unwatchedCount: Int,
    unwatchedBytes: Long,
    cleanedToday: Long,
    duplicateCount: Int,
    duplicateBytes: Long,
    onStart: () -> Unit,
    onStartDuplicates: () -> Unit,
    smartGroups: Map<SmartCategory, List<VideoItem>>,
    onStartSmart: (SmartCategory) -> Unit,
) {
    val unwatchedRes = if (mode == MediaMode.PHOTOS) R.string.cleanup_unviewed else R.string.cleanup_unwatched
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(appColors.card)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onStart)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(CleanupGreen.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(R.drawable.ic_delete_sweep), contentDescription = null, tint = CleanupGreen, modifier = Modifier.size(26.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(str(R.string.cleanup), color = appColors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    if (unwatchedCount > 0) {
                        str(unwatchedRes, mode.count(unwatchedCount), formatSize(unwatchedBytes))
                    } else {
                        str(R.string.cleanup_hint)
                    },
                    color = appColors.textSecondary,
                    fontSize = 13.sp,
                )
                if (cleanedToday > 0) {
                    Text(
                        str(R.string.cleaned_today, formatSize(cleanedToday)),
                        color = CleanupGreen,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Text(str(R.string.start), color = InstaBlue, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
        if (duplicateCount > 0) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(appColors.divider)
                    .clickable(onClick = onStartDuplicates)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    str(R.string.duplicates_found, mode.count(duplicateCount), formatSize(duplicateBytes)),
                    color = appColors.accentGold,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(12.dp))
                Text(str(R.string.review_duplicates), color = InstaBlue, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        // Akıllı öneriler kapalı başlar: tek satırda özet, dokununca kategoriler açılır
        val smart = SmartCategory.entries.mapNotNull { c -> smartGroups[c]?.takeIf { it.isNotEmpty() }?.let { c to it } }
        if (smart.isNotEmpty()) {
            var expanded by rememberSaveable { mutableStateOf(false) }
            val arrow by animateFloatAsState(if (expanded) 180f else 0f, label = "smartArrow")
            // Bir öğe birden çok kategoride olabilir (WhatsApp'tan gelen kısa video): boyutu bir kez say
            val totalBytes = smart.flatMap { it.second }.distinctBy { it.id }.sumOf { it.sizeBytes }
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(appColors.divider)
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    str(R.string.smart_header),
                    color = appColors.textPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "  ·  " + plural(R.plurals.smart_summary, smart.size, smart.size, formatSize(totalBytes)),
                    color = appColors.textSecondary,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    painterResource(R.drawable.ic_expand_more),
                    contentDescription = str(R.string.smart_header),
                    tint = appColors.textSecondary,
                    modifier = Modifier.size(20.dp).graphicsLayer { rotationZ = arrow },
                )
            }
            AnimatedVisibility(visible = expanded, enter = expandVertically(), exit = shrinkVertically()) {
                Column {
                    for ((category, list) in smart) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .background(appColors.divider)
                                .clickable { onStartSmart(category) }
                                .padding(start = 28.dp, end = 16.dp, top = 9.dp, bottom = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                category.row(category.countText(mode, list.size), formatSize(list.sumOf { it.sizeBytes })),
                                color = appColors.textPrimary,
                                fontSize = 13.sp,
                                modifier = Modifier.weight(1f),
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(str(R.string.review), color = InstaBlue, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}
